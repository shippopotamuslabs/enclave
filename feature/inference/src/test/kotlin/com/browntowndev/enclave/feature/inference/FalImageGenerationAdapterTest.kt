package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.feature.inference.fal.FalRequest
import com.browntowndev.enclave.feature.inference.media.FalImageGenerationAdapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64

class FalImageGenerationAdapterTest {
    private val falService = SequencedFalService()
    private val outputSchemaResolver = FakeFalOutputSchemaResolver()
    private val adapter =
        FalImageGenerationAdapter(
            falService = falService,
            outputSchemaResolver = outputSchemaResolver,
            pollDelayMillis = 0L,
        )

    @BeforeEach
    fun setUp() {
        falService.resultResult = buildJsonObject {}
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
            val status =
                buildJsonObject {
                    put("status", "COMPLETED")
                    put("image_url", "https://cdn.test/image.png")
                }
            falService.submitQueueResult = defaultRequest()
            falService.statusResult = status
            falService.resolveMediaBytesResult = "png".toByteArray()

            adapter.generateImage(
                prompt = "draw",
                apiKey = "key",
                modelId = "fal-ai/flux",
                baseUrl = null,
                settings = ImageGenerationSettings(imageResolution = "2K"),
            )

            assertEquals("2K", falService.submittedBodies.single()["resolution"]?.jsonPrimitive?.content)
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
            falService.submitQueueResult = defaultRequest()
            falService.statusResult = status
            falService.resultResult = resultJson
            falService.resolveMediaBytesResult = "png".toByteArray()

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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", null, null)
            falService.statusResult = status
            falService.resolveMediaBytesResult = "png".toByteArray()

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
            assertTrue(falService.resultCalls.isEmpty())
        }

    @Test
    fun generateImage_responseUrlResultEmpty_fallsBackToStatusMediaUrl() =
        runTest {
            val status =
                buildJsonObject {
                    put("status", "COMPLETED")
                    put("image_url", "https://cdn.test/status-image.png")
                }
            falService.submitQueueResult = defaultRequest()
            falService.statusResult = status
            falService.resultResult = buildJsonObject {}
            falService.resolveMediaBytesResult = "png".toByteArray()

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
            falService.submitQueueResult = defaultRequest()
            falService.statusResult = status
            falService.resultResult = previewOnlyResult
            falService.resolveMediaBytesResult = "png".toByteArray()

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
            falService.submitQueueResult = defaultRequest()
            falService.statusResult = status
            falService.resultResult = resultWithBoth
            falService.resolveMediaBytesResult = "png".toByteArray()

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
            assertTrue(falService.resolvedMediaUrls.none { it == "https://cdn.test/preview.png" })
        }

    @Test
    fun generateImage_queueCompleted_decodesDataUriWithoutDownload() =
        runTest {
            val bytes = "png".toByteArray()
            val dataUri = "data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}"
            val status = buildJsonObject { put("status", "COMPLETED") }
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", null, null)
            falService.statusResult = status
            falService.resultResult = buildJsonObject { put("image_url", dataUri) }
            falService.resolveMediaBytesResult = bytes

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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", null, null)
            falService.statusResult = status
            falService.resultResult = buildJsonObject {}

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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", null, null)
            falService.statusResult = status
            falService.resultResult = buildJsonObject { put("image_url", "https://cdn.test/image1.png") }
            falService.resolveMediaBytesResult = "png".toByteArray()

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
            falService.submitQueueResult = defaultRequest()
            falService.statusResult = status

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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", null, null)
            falService.statusResult = status

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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", null, "https://queue.test/cancel")
            falService.statusResult = status

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
            assertEquals(listOf("https://queue.test/cancel"), falService.cancelCalls)
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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", null, null)
            falService.statusResult = status
            falService.onStatusPolled = { statusPolled.complete(Unit) }

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

            assertEquals(listOf("https://queue.test/status"), falService.cancelCalls)
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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", null, null)
            falService.statusSequence.addAll(listOf(inProgressWithPreview, completedStatus))
            falService.resultResult = buildJsonObject { put("image_url", "https://cdn.test/image.png") }
            falService.resolveMediaBytesResult = "png".toByteArray()

            val result =
                adapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux",
                    baseUrl = null,
                    settings = ImageGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertEquals(2, falService.statusCalls.size)
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
