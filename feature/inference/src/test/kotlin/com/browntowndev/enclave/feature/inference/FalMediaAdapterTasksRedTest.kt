package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.feature.inference.fal.FalRequest
import com.browntowndev.enclave.feature.inference.media.FalImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.FalVideoGenerationAdapter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FalMediaAdapterTasksRedTest {
    private val falService = SequencedFalService()
    private val outputSchemaResolver = FakeFalOutputSchemaResolver()

    private fun imageAdapter() =
        FalImageGenerationAdapter(
            falService = falService,
            outputSchemaResolver = outputSchemaResolver,
            pollDelayMillis = 0L,
        )

    private fun videoAdapter() =
        FalVideoGenerationAdapter(
            falService = falService,
            outputSchemaResolver = outputSchemaResolver,
            pollDelayMillis = 0L,
        )

    @Test
    fun `IMGADAPTER-01 edit-capable fal image request includes image fields`() =
        runTest {
            stubCompletedImage()

            imageAdapter().generateImage(
                prompt = "draw",
                apiKey = "key",
                modelId = "fal-ai/flux-pro/kontext",
                baseUrl = null,
                settings = ImageGenerationSettings(),
                referenceImage = byteArrayOf(1, 2, 3),
                supportsEdit = true,
            )

            val body = falService.submittedBodies.single()
            assertTrue("image_url" in body)
            assertTrue("image" !in body)
        }

    @Test
    fun `IMGADAPTER-02 text-to-image-only fal image request omits image fields`() =
        runTest {
            stubCompletedImage()

            imageAdapter().generateImage(
                prompt = "draw",
                apiKey = "key",
                modelId = "fal-ai/recraft/v3",
                baseUrl = null,
                settings = ImageGenerationSettings(),
                referenceImage = byteArrayOf(1, 2, 3),
                supportsEdit = false,
            )

            val body = falService.submittedBodies.single()
            assertTrue("image_url" !in body)
            assertTrue("image" !in body)
            assertTrue("prompt" in body)
        }

    @Test
    fun `VIDADAPTER-01 image-to-video fal request includes image_url and succeeds`() =
        runTest {
            stubCompletedVideo()

            val result =
                videoAdapter().generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/kling-video/v2.1/master/image-to-video",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                    referenceImage = byteArrayOf(1, 2, 3),
                    supportsImageToVideo = true,
                )

            assertTrue("image_url" in falService.submittedBodies.single())
            assertTrue(result.isSuccess)
        }

    @Test
    fun `VIDADAPTER-02 image-to-video without reference fails before submitting`() =
        runTest {
            val result =
                videoAdapter().generateVideo(
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
            assertEquals(
                "Model 'fal-ai/kling-video/v2.1/master/image-to-video' requires a reference image but none was provided.",
                result.exceptionOrNull()?.message,
            )
            assertTrue(falService.submitQueueCalls.isEmpty())
        }

    @Test
    fun `VIDADAPTER-03 text-to-video ignores image-to-video-looking model id when task is unsupported`() =
        runTest {
            stubCompletedVideo()

            val result =
                videoAdapter().generateVideo(
                    prompt = "animate",
                    apiKey = "key",
                    modelId = "fal-ai/some/image-to-video-named-but-t2v",
                    baseUrl = null,
                    settings = VideoGenerationSettings(),
                    referenceImage = byteArrayOf(1, 2, 3),
                    supportsImageToVideo = false,
                )

            assertTrue("image_url" !in falService.submittedBodies.single())
            assertTrue(result.isSuccess)
        }

    @Test
    fun `MUT-01 text-to-image-only request never attaches image fields`() =
        runTest {
            stubCompletedImage()

            imageAdapter().generateImage(
                prompt = "draw",
                apiKey = "key",
                modelId = "fal-ai/recraft/v3",
                baseUrl = null,
                settings = ImageGenerationSettings(),
                referenceImage = byteArrayOf(1, 2, 3),
                supportsEdit = false,
            )

            val body = falService.submittedBodies.single()
            assertTrue("image_url" !in body)
            assertTrue("image" !in body)
        }

    private fun stubCompletedImage() {
        val status =
            buildJsonObject {
                put("status", "COMPLETED")
                put("image_url", "https://cdn.test/image.png")
            }
        falService.submitQueueResult = FalRequest("request-1", "https://queue.test/status", null, null)
        falService.statusResult = status
        falService.resolveMediaBytesResult = byteArrayOf(4, 5, 6)
    }

    private fun stubCompletedVideo() {
        falService.submitQueueResult = FalRequest("request-1", "https://queue.test/status", null, null)
        falService.statusResult = buildJsonObject { put("status", "COMPLETED") }
        falService.resultResult = buildJsonObject { put("video_url", "https://cdn.test/video.mp4") }
        falService.resolveMediaBytesResult = byteArrayOf(4, 5, 6)
    }
}
