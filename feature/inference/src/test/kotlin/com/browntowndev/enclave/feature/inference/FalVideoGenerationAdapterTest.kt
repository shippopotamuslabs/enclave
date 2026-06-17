package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.feature.inference.fal.FalOutputSchemaResolver
import com.browntowndev.enclave.feature.inference.fal.FalRequest
import com.browntowndev.enclave.feature.inference.fal.FalService
import com.browntowndev.enclave.feature.inference.media.FalVideoGenerationAdapter
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64

class FalVideoGenerationAdapterTest {
    private val outputSchemaResolver =
        mockk<FalOutputSchemaResolver>().also { resolver ->
            coEvery { resolver.resolve(any(), any(), any()) } returns null
        }
    private val falService = mockk<FalService>()
    private val adapter =
        FalVideoGenerationAdapter(
            falService = falService,
            outputSchemaResolver = outputSchemaResolver,
            pollDelayMillis = 0L,
        )

    @BeforeEach
    fun resetFalServiceMocks() {
        clearMocks(falService, answers = false)
        every { falService.awaitStatusStream(any(), any()) } throws IllegalStateException("stream unavailable")
        every { falService.result(any(), any()) } returns buildJsonObject {}
    }

    @Test
    fun generateVideo_happyHorseNestedVideoObject_extractsUrlWithRealFalService() =
        runTest {
            val realFalService = FalService(OkHttpClient())
            val happyHorseResult =
                Json
                    .parseToJsonElement(
                        """
                        {
                          "video": {
                            "url": "https://cdn.test/happy-horse.mp4",
                            "content_type": "video/mp4"
                          },
                          "seed": 42
                        }
                        """.trimIndent(),
                    ).jsonObject
            assertEquals(
                listOf("https://cdn.test/happy-horse.mp4"),
                realFalService.primaryMediaUrls(happyHorseResult),
            )
        }

    @Test
    fun generateVideo_queueCompleted_downloadsResultUrl() =
        runTest {
            val status = buildJsonObject { put("status", "COMPLETED") }
            val resultJson = buildJsonObject { put("video_url", "https://cdn.test/video.mp4") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest(
                    requestId = "request-1",
                    statusUrl = "https://queue.test/status",
                    responseUrl = "https://queue.test/result",
                    cancelUrl = null,
                )
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.result("key", "https://queue.test/result") } returns resultJson
            every { falService.primaryVideoUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
            every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
            every { falService.resolveMediaBytes("https://cdn.test/video.mp4", any()) } returns "mp4".toByteArray()

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("mp4".toByteArray(), result.getOrThrow())
        }

    @Test
    fun generateVideo_failedStatus_returnsFailure() =
        runTest {
            val status = buildJsonObject { put("status", "FAILED") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", "https://queue.test/result", null)
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "FAILED"

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("failed") == true)
        }

    @Test
    fun generateVideo_failedStatusWithErrorDetail_includesDetailInMessage() =
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
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("content policy violation") == true)
        }

    @Test
    fun generateVideo_completedStatusWithoutResponseUrl_usesInlineMediaUrl() =
        runTest {
            val status = buildJsonObject { put("status", "COMPLETED") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest(
                    requestId = "request-1",
                    statusUrl = "https://queue.test/requests/request-1",
                    responseUrl = null,
                    cancelUrl = null,
                )
            every { falService.status("key", "https://queue.test/requests/request-1") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.mediaUrls(status) } returns listOf("https://cdn.test/video.mp4")
            every { falService.primaryVideoUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
            every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
            every { falService.resolveMediaBytes("https://cdn.test/video.mp4", any()) } returns "mp4".toByteArray()

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("mp4".toByteArray(), result.getOrThrow())
            verify(exactly = 0) { falService.result(any(), any()) }
        }

    @Test
    fun generateVideo_responseUrlResultEmpty_fallsBackToStatusMediaUrl() =
        runTest {
            val status =
                buildJsonObject {
                    put("status", "COMPLETED")
                    put("video_url", "https://cdn.test/status-video.mp4")
                }
            val emptyResult = buildJsonObject {}
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest(
                    requestId = "request-1",
                    statusUrl = "https://queue.test/status",
                    responseUrl = "https://queue.test/result",
                    cancelUrl = null,
                )
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.result("key", "https://queue.test/result") } returns emptyResult
            every { falService.mediaUrls(status) } returns listOf("https://cdn.test/status-video.mp4")
            every { falService.primaryVideoUrls(any(), any()) } returns listOf("https://cdn.test/status-video.mp4")
            every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/status-video.mp4")
            every { falService.resolveMediaBytes("https://cdn.test/status-video.mp4", any()) } returns "mp4".toByteArray()

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("mp4".toByteArray(), result.getOrThrow())
        }

    @Test
    fun generateVideo_completedStatus_decodesDataUriWithoutDownload() =
        runTest {
            val bytes = "mp4".toByteArray()
            val dataUri = "data:video/mp4;base64,${Base64.getEncoder().encodeToString(bytes)}"
            val status = buildJsonObject { put("status", "COMPLETED") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", null, null)
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.result(any(), any()) } returns buildJsonObject { put("video_url", dataUri) }
            every { falService.primaryVideoUrls(any(), any()) } returns listOf(dataUri)
            every { falService.primaryMediaUrls(any(), any()) } returns listOf(dataUri)
            every { falService.mediaUrls(any(), any()) } returns listOf(dataUri)
            every { falService.resolveMediaBytes(dataUri, any()) } returns bytes

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals(bytes, result.getOrThrow())
        }

    @Test
    fun generateVideo_statusNeverCompletes_returnsTimeoutFailure() =
        runTest {
            val status = buildJsonObject { put("status", "IN_QUEUE") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", "https://queue.test/result", "https://queue.test/cancel")
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "IN_QUEUE"
            every { falService.cancel("key", "https://queue.test/cancel") } returns Unit

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isFailure)
            assertEquals("fal video generation timed out", result.exceptionOrNull()?.message)
            verify { falService.cancel("key", "https://queue.test/cancel") }
        }

    @Test
    fun generateVideo_coroutineCancellation_cancelsQueueAndRethrowsCancellation() =
        runTest {
            val waitingAdapter =
                FalVideoGenerationAdapter(
                    falService = falService,
                    outputSchemaResolver = outputSchemaResolver,
                    pollDelayMillis = 10_000L,
                )
            val status = buildJsonObject { put("status", "IN_QUEUE") }
            val statusPolled = CompletableDeferred<Unit>()
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", "https://queue.test/result", null)
            every { falService.status("key", "https://queue.test/status") } answers {
                statusPolled.complete(Unit)
                status
            }
            every { falService.statusValue(status) } returns "IN_QUEUE"
            every { falService.cancel("key", "https://queue.test/status") } returns Unit

            val job =
                launch {
                    waitingAdapter.generateVideo(
                        prompt = "animate",
                        apiKey = "key",
                        modelId = "fal-ai/video",
                        baseUrl = null,
                        settings = VideoGenerationSettings(),
                    )
                }
            statusPolled.await()

            job.cancelAndJoin()

            verify { falService.cancel("key", "https://queue.test/status") }
        }

    @Test
    fun generateVideo_nullStatusWithInlineVideoUrl_completesSuccessfully() =
        runTest {
            val status = buildJsonObject { put("video_url", "https://cdn.test/video.mp4") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", null, null)
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns null
            every { falService.primaryVideoUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
            every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
            every { falService.resolveMediaBytes("https://cdn.test/video.mp4", any()) } returns "mp4".toByteArray()

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("mp4".toByteArray(), result.getOrThrow())
        }

    @Test
    fun generateVideo_responseUrlResultThrows_fallsBackToStatusMediaUrl() =
        runTest {
            val status =
                buildJsonObject {
                    put("status", "COMPLETED")
                    put("video_url", "https://cdn.test/status-video.mp4")
                }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest(
                    requestId = "request-1",
                    statusUrl = "https://queue.test/status",
                    responseUrl = "https://queue.test/result",
                    cancelUrl = null,
                )
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.result("key", "https://queue.test/result") } throws
                IllegalStateException("fal queue result failed: 503 Service Unavailable")
            every { falService.mediaUrls(status) } returns listOf("https://cdn.test/status-video.mp4")
            every { falService.primaryVideoUrls(any(), any()) } returns listOf("https://cdn.test/status-video.mp4")
            every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/status-video.mp4")
            every { falService.resolveMediaBytes("https://cdn.test/status-video.mp4", any()) } returns "mp4".toByteArray()

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("mp4".toByteArray(), result.getOrThrow())
        }

    @Test
    fun generateVideo_responseUrlResultHasOnlyPreview_fallsBackToStatusVideoUrl() =
        runTest {
            val status =
                buildJsonObject {
                    put("status", "COMPLETED")
                    put("video_url", "https://cdn.test/video.mp4")
                }
            val previewOnlyResult = buildJsonObject { put("preview_url", "https://cdn.test/preview.jpg") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest(
                    requestId = "request-1",
                    statusUrl = "https://queue.test/status",
                    responseUrl = "https://queue.test/result",
                    cancelUrl = null,
                )
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.result("key", "https://queue.test/result") } returns previewOnlyResult
            every { falService.mediaUrls(status) } returns listOf("https://cdn.test/video.mp4")
            every { falService.primaryVideoUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
            every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
            every { falService.resolveMediaBytes("https://cdn.test/video.mp4", any()) } returns "mp4".toByteArray()

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("mp4".toByteArray(), result.getOrThrow())
        }

    @Test
    fun generateVideo_inProgressStatusWithPreviewUrl_doesNotCompleteEarly() =
        runTest {
            val inProgressWithPreview =
                buildJsonObject {
                    put("status", "IN_PROGRESS")
                    put("preview_url", "https://cdn.test/preview.jpg")
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
            every { falService.result(any(), any()) } returns buildJsonObject { put("video_url", "https://cdn.test/video.mp4") }
            every { falService.primaryVideoUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
            every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
            every { falService.resolveMediaBytes("https://cdn.test/video.mp4", any()) } returns "mp4".toByteArray()

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            verify(exactly = 2) { falService.status("key", "https://queue.test/status") }
        }

    @Test
    fun generateVideo_unknownStatusWithPreviewOnly_doesNotCompleteEarly() =
        runTest {
            val unknownWithPreview = buildJsonObject { put("preview_url", "https://cdn.test/preview.jpg") }
            val completedStatus =
                buildJsonObject {
                    put("status", "COMPLETED")
                    put("video_url", "https://cdn.test/video.mp4")
                }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", null, null)
            every { falService.status("key", "https://queue.test/status") } returnsMany
                listOf(unknownWithPreview, completedStatus)
            every { falService.statusValue(unknownWithPreview) } returns null
            every { falService.primaryMediaUrls(unknownWithPreview) } returns emptyList()
            every { falService.mediaUrls(unknownWithPreview) } returns emptyList()
            every { falService.statusValue(completedStatus) } returns "COMPLETED"
            every { falService.mediaUrls(completedStatus) } returns listOf("https://cdn.test/video.mp4")
            every { falService.primaryVideoUrls(completedStatus, any()) } returns listOf("https://cdn.test/video.mp4")
            every { falService.primaryMediaUrls(completedStatus, any()) } returns listOf("https://cdn.test/video.mp4")
            every { falService.resolveMediaBytes("https://cdn.test/video.mp4", any()) } returns "mp4".toByteArray()

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            verify(exactly = 2) { falService.status("key", "https://queue.test/status") }
        }

    @Test
    fun generateVideo_finalUrlPrefersPrimaryOverPreview() =
        runTest {
            val status = buildJsonObject { put("status", "COMPLETED") }
            val resultWithBothUrls =
                buildJsonObject {
                    put("video_url", "https://cdn.test/video.mp4")
                    put("preview_url", "https://cdn.test/preview.jpg")
                }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", "https://queue.test/result", null)
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.result("key", "https://queue.test/result") } returns resultWithBothUrls
            every { falService.primaryVideoUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
            every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
            every { falService.resolveMediaBytes("https://cdn.test/video.mp4", any()) } returns "mp4".toByteArray()

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("mp4".toByteArray(), result.getOrThrow())
            verify(exactly = 0) { falService.resolveMediaBytes("https://cdn.test/preview.jpg", any()) }
        }

    @Test
    fun generateVideo_defaultDuration_sendsStringDurationForFalEnums() =
        runTest {
            val bodySlot = slot<kotlinx.serialization.json.JsonObject>()
            val status = buildJsonObject { put("status", "COMPLETED") }
            val resultJson =
                buildJsonObject {
                    putJsonObject("video") {
                        put("url", "https://cdn.test/seedance.mp4")
                    }
                }
            every { falService.submitQueue(any(), any(), capture(bodySlot), any()) } returns
                FalRequest("request-1", "https://queue.test/status", "https://queue.test/result", null)
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "COMPLETED"
            every { falService.result("key", "https://queue.test/result") } returns resultJson
            every { falService.result("key", "https://queue.test") } returns resultJson
            every { falService.primaryVideoUrls(any(), any()) } returns listOf("https://cdn.test/seedance.mp4")
            every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/seedance.mp4")
            every { falService.resolveMediaBytes("https://cdn.test/seedance.mp4", any()) } returns "mp4".toByteArray()

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "bytedance/seedance-2.0/image-to-video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(videoDuration = 6),
                )

            assertTrue(result.isSuccess)
            assertEquals("6", bodySlot.captured["duration"]?.jsonPrimitive?.content)
        }

    @Test
    fun generateVideo_imageToVideoModelWithoutReferenceImage_returnsValidationFailure() =
        runTest {
            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/kling-video/v2.1/master/image-to-video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                    referenceImage = null,
                    supportsImageToVideo = true,
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(result.exceptionOrNull()?.message?.contains("requires a reference image") == true)
            verify(exactly = 0) { falService.submitQueue(any(), any(), any(), any()) }
        }

    @Test
    fun generateVideo_i2vModelWithoutReferenceImage_returnsValidationFailure() =
        runTest {
            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/wan/i2v",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                    referenceImage = null,
                    supportsImageToVideo = true,
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(result.exceptionOrNull()?.message?.contains("requires a reference image") == true)
            verify(exactly = 0) { falService.submitQueue(any(), any(), any(), any()) }
        }

    @Test
    fun generateVideo_unknownStatusPersists_failsEarly() =
        runTest {
            val status = buildJsonObject { put("status", "SOME_UNKNOWN_STATUS") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns
                FalRequest("request-1", "https://queue.test/status", "https://queue.test/result", "https://queue.test/cancel")
            every { falService.status("key", "https://queue.test/status") } returns status
            every { falService.statusValue(status) } returns "SOME_UNKNOWN_STATUS"
            every { falService.primaryMediaUrls(status) } returns emptyList()
            every { falService.cancel("key", "https://queue.test/cancel") } returns Unit

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("unknown or unexpected status") == true)
            verify(exactly = 5) { falService.status("key", "https://queue.test/status") }
            verify { falService.cancel("key", "https://queue.test/cancel") }
        }
}
