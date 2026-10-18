package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.feature.inference.fal.FalRequest
import com.browntowndev.enclave.feature.inference.fal.FalService
import com.browntowndev.enclave.feature.inference.media.FalVideoGenerationAdapter
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
    private val outputSchemaResolver = FakeFalOutputSchemaResolver()
    private val falService = SequencedFalService()
    private val adapter =
        FalVideoGenerationAdapter(
            falService = falService,
            outputSchemaResolver = outputSchemaResolver,
            pollDelayMillis = 0L,
        )

    @BeforeEach
    fun setUp() {
        falService.resultResult = buildJsonObject {}
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
            falService.submitQueueResult =
                FalRequest(
                    requestId = "request-1",
                    statusUrl = "https://queue.test/status",
                    responseUrl = "https://queue.test/result",
                    cancelUrl = null,
                )
            falService.statusResult = status
            falService.resultResult = resultJson
            falService.resolveMediaBytesResult = "mp4".toByteArray()

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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", "https://queue.test/result", null)
            falService.statusResult = status

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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", null, null)
            falService.statusResult = status

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
            val status =
                buildJsonObject {
                    put("status", "COMPLETED")
                    put("video_url", "https://cdn.test/video.mp4")
                }
            falService.submitQueueResult =
                FalRequest(
                    requestId = "request-1",
                    statusUrl = "https://queue.test/requests/request-1",
                    responseUrl = null,
                    cancelUrl = null,
                )
            falService.statusResult = status
            falService.resolveMediaBytesResult = "mp4".toByteArray()

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
            assertTrue(falService.resultCalls.isEmpty())
        }

    @Test
    fun generateVideo_responseUrlResultEmpty_fallsBackToStatusMediaUrl() =
        runTest {
            val status =
                buildJsonObject {
                    put("status", "COMPLETED")
                    put("video_url", "https://cdn.test/status-video.mp4")
                }
            falService.submitQueueResult =
                FalRequest(
                    requestId = "request-1",
                    statusUrl = "https://queue.test/status",
                    responseUrl = "https://queue.test/result",
                    cancelUrl = null,
                )
            falService.statusResult = status
            falService.resultResult = buildJsonObject {}
            falService.resolveMediaBytesResult = "mp4".toByteArray()

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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", null, null)
            falService.statusResult = status
            falService.resultResult = buildJsonObject { put("video_url", dataUri) }
            falService.resolveMediaBytesResult = bytes

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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", "https://queue.test/result", "https://queue.test/cancel")
            falService.statusResult = status

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
            assertEquals(listOf("https://queue.test/cancel"), falService.cancelCalls)
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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", "https://queue.test/result", null)
            falService.statusResult = status
            falService.onStatusPolled = { statusPolled.complete(Unit) }

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

            assertEquals(listOf("https://queue.test/status"), falService.cancelCalls)
        }

    @Test
    fun generateVideo_nullStatusWithInlineVideoUrl_completesSuccessfully() =
        runTest {
            val status = buildJsonObject { put("video_url", "https://cdn.test/video.mp4") }
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", null, null)
            falService.statusResult = status
            falService.resolveMediaBytesResult = "mp4".toByteArray()

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
            falService.submitQueueResult =
                FalRequest(
                    requestId = "request-1",
                    statusUrl = "https://queue.test/status",
                    responseUrl = "https://queue.test/result",
                    cancelUrl = null,
                )
            falService.statusResult = status
            falService.resultFailure =
                IllegalStateException("fal queue result failed: 503 Service Unavailable")
            falService.resolveMediaBytesResult = "mp4".toByteArray()

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
            falService.submitQueueResult =
                FalRequest(
                    requestId = "request-1",
                    statusUrl = "https://queue.test/status",
                    responseUrl = "https://queue.test/result",
                    cancelUrl = null,
                )
            falService.statusResult = status
            falService.resultResult = previewOnlyResult
            falService.resolveMediaBytesResult = "mp4".toByteArray()

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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", null, null)
            falService.statusSequence.addAll(listOf(inProgressWithPreview, completedStatus))
            falService.resultResult = buildJsonObject { put("video_url", "https://cdn.test/video.mp4") }
            falService.resolveMediaBytesResult = "mp4".toByteArray()

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertEquals(2, falService.statusCalls.size)
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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", null, null)
            falService.statusSequence.addAll(listOf(unknownWithPreview, completedStatus))
            falService.resolveMediaBytesResult = "mp4".toByteArray()

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                )

            assertTrue(result.isSuccess)
            assertEquals(2, falService.statusCalls.size)
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
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", "https://queue.test/result", null)
            falService.statusResult = status
            falService.resultResult = resultWithBothUrls
            falService.resolveMediaBytesResult = "mp4".toByteArray()

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
            assertTrue(falService.resolvedMediaUrls.none { it == "https://cdn.test/preview.jpg" })
        }

    @Test
    fun generateVideo_defaultDuration_sendsStringDurationForFalEnums() =
        runTest {
            val status = buildJsonObject { put("status", "COMPLETED") }
            val resultJson =
                buildJsonObject {
                    putJsonObject("video") {
                        put("url", "https://cdn.test/seedance.mp4")
                    }
                }
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", "https://queue.test/result", null)
            falService.statusResult = status
            falService.resultResult = resultJson
            falService.resolveMediaBytesResult = "mp4".toByteArray()

            val result =
                adapter.generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "bytedance/seedance-2.0/image-to-video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(videoDuration = 6),
                )

            assertTrue(result.isSuccess)
            assertEquals("6", falService.submittedBodies.single()["duration"]?.jsonPrimitive?.content)
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
            assertTrue(falService.submitQueueCalls.isEmpty())
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
            assertTrue(falService.submitQueueCalls.isEmpty())
        }

    @Test
    fun generateVideo_unknownStatusPersists_failsEarly() =
        runTest {
            val status = buildJsonObject { put("status", "SOME_UNKNOWN_STATUS") }
            falService.submitQueueResult =
                FalRequest("request-1", "https://queue.test/status", "https://queue.test/result", "https://queue.test/cancel")
            falService.statusResult = status

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
            assertEquals(5, falService.statusCalls.size)
            assertEquals(listOf("https://queue.test/cancel"), falService.cancelCalls)
        }
}
