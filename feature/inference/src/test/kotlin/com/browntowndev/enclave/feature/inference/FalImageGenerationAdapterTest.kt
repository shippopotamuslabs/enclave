package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.feature.inference.fal.FalOutputSchemaResolver
import com.browntowndev.enclave.feature.inference.fal.FalRequest
import com.browntowndev.enclave.feature.inference.fal.FalService
import com.browntowndev.enclave.feature.inference.media.FalImageGenerationAdapter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import io.mockk.clearMocks
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64

class FalImageGenerationAdapterTest {
    private val falService = mockk<FalService>()
    private val outputSchemaResolver =
        mockk<FalOutputSchemaResolver>().also { resolver ->
            coEvery { resolver.resolve(any(), any(), any()) } returns null
        }
    private val adapter =
        FalImageGenerationAdapter(
            falService = falService,
            outputSchemaResolver = outputSchemaResolver,
            pollDelayMillis = 0L,
        )

    @BeforeEach
    fun resetFalServiceMocks() {
        clearMocks(falService, answers = false)
        every { falService.result(any(), any()) } returns buildJsonObject {}
    }

    private fun defaultRequest(cancelUrl: String? = null) =
        FalRequest(
            requestId = "request-1",
            statusUrl = "https://queue.test/status",
            responseUrl = "https://queue.test/result",
            cancelUrl = cancelUrl,
        )

    @Test
    fun generateImage_withImageResolution_includesResolutionInRequestBody() =
        runTest {
            val bodySlot = slot<JsonObject>()
            val status = buildJsonObject { put("status", "COMPLETED") }
            every { falService.submitQueue(any(), any(), capture(bodySlot), any()) } returns defaultRequest()
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.primaryMediaUrls(status) } returns emptyList()
            every { falService.mediaUrls(status) } returns listOf("https://cdn.test/image.png")
            every { falService.resolveMediaBytes(any(), any()) } returns "png".toByteArray()

            adapter.generateImage(
                prompt = "draw",
                apiKey = "key",
                modelId = "fal-ai/flux",
                baseUrl = null,
                settings = ImageGenerationSettings(imageResolution = "2K"),
            )

            assertEquals("2K", bodySlot.captured["resolution"]?.jsonPrimitive?.content)
        }

    @Test
    fun generateImage_queueCompleted_downloadsImageUrls() =
        runTest {
            val status = buildJsonObject { put("status", "COMPLETED") }
            val resultJson =
                buildJsonObject {
                    put(
                        "images",
                        buildJsonArray {
                            add(buildJsonObject { put("url", "https://cdn.test/image.png") })
                        },
                    )
                }
            every { falService.submitQueue(any(), any(), any(), any()) } returns defaultRequest()
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.result("key", "https://queue.test/result") } returns resultJson
            every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/image.png")
            every { falService.resolveMediaBytes("https://cdn.test/image.png", any()) } returns "png".toByteArray()

            val result =
                adapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux",
                    baseUrl = null,
                    settings = ImageGenerationSettings(aspectRatio = AspectRatio.SIXTEEN_NINE),
                    referenceImage = "ref".toByteArray(),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("png".toByteArray(), result.getOrThrow().single())
        }

    @Test
    fun generateImage_completedWithoutResponseUrl_usesInlineMediaUrl() =
        runTest {
            val status =
                buildJsonObject {
                    put("status", "COMPLETED")
                    put("image_url", "https://cdn.test/image.png")
                }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", null, null)
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/image.png")
            every { falService.mediaUrls(any(), any()) } returns listOf("https://cdn.test/image.png")
            every { falService.resolveMediaBytes("https://cdn.test/image.png", any()) } returns "png".toByteArray()

            val result =
                adapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux",
                    baseUrl = null,
                    settings = ImageGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("png".toByteArray(), result.getOrThrow().single())
            verify(exactly = 0) { falService.result(any(), any()) }
        }

    @Test
    fun generateImage_responseUrlResultEmpty_fallsBackToStatusMediaUrl() =
        runTest {
            val status =
                buildJsonObject {
                    put("status", "COMPLETED")
                    put("image_url", "https://cdn.test/status-image.png")
                }
            val emptyResult = buildJsonObject {}
            every { falService.submitQueue(any(), any(), any(), any()) } returns defaultRequest()
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.result("key", "https://queue.test/result") } returns emptyResult
            every { falService.primaryMediaUrls(emptyResult) } returns emptyList()
            every { falService.primaryMediaUrls(status) } returns listOf("https://cdn.test/status-image.png")
            every { falService.resolveMediaBytes("https://cdn.test/status-image.png", any()) } returns "png".toByteArray()

            val result =
                adapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux",
                    baseUrl = null,
                    settings = ImageGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("png".toByteArray(), result.getOrThrow().single())
        }

    @Test
    fun generateImage_responseUrlResultHasOnlyPreview_fallsBackToStatusPrimaryUrl() =
        runTest {
            val status =
                buildJsonObject {
                    put("status", "COMPLETED")
                    put("image_url", "https://cdn.test/image.png")
                }
            val previewOnlyResult = buildJsonObject { put("preview_url", "https://cdn.test/preview.png") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns defaultRequest()
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.result("key", "https://queue.test/result") } returns previewOnlyResult
            every { falService.primaryMediaUrls(previewOnlyResult) } returns emptyList()
            every { falService.primaryMediaUrls(status) } returns listOf("https://cdn.test/image.png")
            every { falService.resolveMediaBytes("https://cdn.test/image.png", any()) } returns "png".toByteArray()

            val result =
                adapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux",
                    baseUrl = null,
                    settings = ImageGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("png".toByteArray(), result.getOrThrow().single())
        }

    @Test
    fun generateImage_finalUrlPrefersPrimaryOverPreview() =
        runTest {
            val status = buildJsonObject { put("status", "COMPLETED") }
            val resultWithBoth =
                buildJsonObject {
                    put("image_url", "https://cdn.test/image.png")
                    put("preview_url", "https://cdn.test/preview.png")
                }
            every { falService.submitQueue(any(), any(), any(), any()) } returns defaultRequest()
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.result("key", "https://queue.test/result") } returns resultWithBoth
            every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/image.png")
            every { falService.resolveMediaBytes("https://cdn.test/image.png", any()) } returns "png".toByteArray()

            val result =
                adapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux",
                    baseUrl = null,
                    settings = ImageGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("png".toByteArray(), result.getOrThrow().single())
            verify(exactly = 0) { falService.resolveMediaBytes("https://cdn.test/preview.png", any()) }
        }

    @Test
    fun generateImage_queueCompleted_decodesDataUriWithoutDownload() =
        runTest {
            val bytes = "png".toByteArray()
            val dataUri = "data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}"
            val status = buildJsonObject { put("status", "COMPLETED") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", null, null)
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.result(any(), any()) } returns buildJsonObject { put("image_url", dataUri) }
            every { falService.primaryMediaUrls(any(), any()) } returns listOf(dataUri)
            every { falService.mediaUrls(any(), any()) } returns listOf(dataUri)
            every { falService.resolveMediaBytes(dataUri, any()) } returns bytes

            val result =
                adapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux",
                    baseUrl = null,
                    settings = ImageGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals(bytes, result.getOrThrow().single())
        }

    @Test
    fun generateImage_noUrls_returnsFailure() =
        runTest {
            val status = buildJsonObject { put("status", "COMPLETED") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", null, null)
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.result(any(), any()) } returns buildJsonObject {}
            every { falService.primaryMediaUrls(any(), any()) } returns emptyList()
            every { falService.mediaUrls(any(), any()) } returns emptyList()

            val result =
                adapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux",
                    baseUrl = null,
                    settings = ImageGenerationSettings(),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("No fal image URLs returned") == true)
        }

    @Test
    fun generateImage_fewerUrlsThanGenerationCount_returnsFailure() =
        runTest {
            val status = buildJsonObject { put("status", "COMPLETED") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", null, null)
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.result(any(), any()) } returns buildJsonObject { put("image_url", "https://cdn.test/image1.png") }
            every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/image1.png")
            every { falService.mediaUrls(any(), any()) } returns listOf("https://cdn.test/image1.png")
            every { falService.resolveMediaBytes(any(), any()) } returns "png".toByteArray()

            val result =
                adapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux",
                    baseUrl = null,
                    settings = ImageGenerationSettings(generationCount = 2),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Expected 2 images from fal, but received 1") == true)
        }

    @Test
    fun generateImage_failedStatus_returnsFailure() =
        runTest {
            val status = buildJsonObject { put("status", "FAILED") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns defaultRequest()
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "FAILED"

            val result =
                adapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux",
                    baseUrl = null,
                    settings = ImageGenerationSettings(),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("failed") == true)
        }

    @Test
    fun generateImage_failedStatusWithErrorDetail_includesDetailInMessage() =
        runTest {
            val status =
                buildJsonObject {
                    put("status", "FAILED")
                    put("error", "content policy violation")
                }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", null, null)
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "FAILED"

            val result =
                adapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux",
                    baseUrl = null,
                    settings = ImageGenerationSettings(),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("content policy violation") == true)
        }

    @Test
    fun generateImage_statusNeverCompletes_returnsTimeoutFailure() =
        runTest {
            val status = buildJsonObject { put("status", "IN_QUEUE") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", null, "https://queue.test/cancel")
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "IN_QUEUE"
            every { falService.cancel("key", "https://queue.test/cancel") } returns Unit

            val result =
                adapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux",
                    baseUrl = null,
                    settings = ImageGenerationSettings(),
                )

            assertTrue(result.isFailure)
            assertEquals("fal image generation timed out", result.exceptionOrNull()?.message)
            verify { falService.cancel("key", "https://queue.test/cancel") }
        }

    @Test
    fun generateImage_coroutineCancellation_cancelsQueueAndRethrowsCancellation() =
        runTest {
            val waitingAdapter =
                FalImageGenerationAdapter(
                    falService = falService,
                    outputSchemaResolver = outputSchemaResolver,
                    pollDelayMillis = 10_000L,
                )
            val status = buildJsonObject { put("status", "IN_QUEUE") }
            val statusPolled = CompletableDeferred<Unit>()
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", null, null)
            every { falService.status("key", "https://queue.test/status") } answers {
                statusPolled.complete(Unit)
                status
            }
            every { falService.statusValue(status) } returns "IN_QUEUE"
            every { falService.cancel("key", "https://queue.test/status") } returns Unit

            val job =
                launch {
                    waitingAdapter.generateImage(
                        prompt = "draw",
                        apiKey = "key",
                        modelId = "fal-ai/flux",
                        baseUrl = null,
                        settings = ImageGenerationSettings(),
                    )
                }
            statusPolled.await()

            job.cancelAndJoin()

            verify { falService.cancel("key", "https://queue.test/status") }
        }

    @Test
    fun generateImage_inProgressStatusWithPreviewUrl_doesNotCompleteEarly() =
        runTest {
            val inProgressWithPreview =
                buildJsonObject {
                    put("status", "IN_PROGRESS")
                    put("preview_url", "https://cdn.test/preview.png")
                }
            val completedStatus = buildJsonObject { put("status", "COMPLETED") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", null, null)
            every { falService.status("key", "https://queue.test/status") } returnsMany
                listOf(inProgressWithPreview, completedStatus)
            every { falService.statusValue(inProgressWithPreview) } returns "IN_PROGRESS"
            every { falService.statusValue(completedStatus) } returns "COMPLETED"
            every { falService.mediaUrls(completedStatus) } returns emptyList()
            every { falService.mediaUrls(completedStatus) } returns emptyList()
            every { falService.result(any(), any()) } returns buildJsonObject { put("image_url", "https://cdn.test/image.png") }
            every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/image.png")
            every { falService.resolveMediaBytes("https://cdn.test/image.png", any()) } returns "png".toByteArray()

            val result =
                adapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux",
                    baseUrl = null,
                    settings = ImageGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            verify(exactly = 2) { falService.status("key", "https://queue.test/status") }
        }

    @Test
    fun generateImage_wrongSettingsType_returnsFailure() =
        runTest {
            val result =
                adapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux",
                    baseUrl = null,
                    settings = MusicGenerationSettings(),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }
}
