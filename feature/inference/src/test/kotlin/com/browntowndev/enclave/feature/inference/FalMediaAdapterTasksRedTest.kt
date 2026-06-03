package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.feature.inference.fal.FalOutputSchemaResolver
import com.browntowndev.enclave.feature.inference.fal.FalRequest
import com.browntowndev.enclave.feature.inference.fal.FalService
import com.browntowndev.enclave.feature.inference.media.FalImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.FalVideoGenerationAdapter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FalMediaAdapterTasksRedTest {
    private val falService = mockk<FalService>()
    private val outputSchemaResolver =
        mockk<FalOutputSchemaResolver>().also { resolver ->
            coEvery { resolver.resolve(any(), any(), any()) } returns null
        }

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
            val bodySlot = slot<JsonObject>()
            stubCompletedImage(bodySlot)

            imageAdapter().generateImage(
                prompt = "draw",
                apiKey = "key",
                modelId = "fal-ai/flux-pro/kontext",
                baseUrl = null,
                settings = ImageGenerationSettings(),
                referenceImage = byteArrayOf(1, 2, 3),
                supportsEdit = true,
            )

            assertTrue("image_url" in bodySlot.captured)
            assertTrue("image" in bodySlot.captured)
        }

    @Test
    fun `IMGADAPTER-02 text-to-image-only fal image request omits image fields`() =
        runTest {
            val bodySlot = slot<JsonObject>()
            stubCompletedImage(bodySlot)

            imageAdapter().generateImage(
                prompt = "draw",
                apiKey = "key",
                modelId = "fal-ai/recraft/v3",
                baseUrl = null,
                settings = ImageGenerationSettings(),
                referenceImage = byteArrayOf(1, 2, 3),
                supportsEdit = false,
            )

            assertTrue("image_url" !in bodySlot.captured)
            assertTrue("image" !in bodySlot.captured)
            assertTrue("prompt" in bodySlot.captured)
        }

    @Test
    fun `VIDADAPTER-01 image-to-video fal request includes image_url and succeeds`() =
        runTest {
            val bodySlot = slot<JsonObject>()
            stubCompletedVideo(bodySlot)

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

            assertTrue("image_url" in bodySlot.captured)
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
            verify(exactly = 0) { falService.submitQueue(any(), any(), any(), any()) }
        }

    @Test
    fun `VIDADAPTER-03 text-to-video ignores image-to-video-looking model id when task is unsupported`() =
        runTest {
            val bodySlot = slot<JsonObject>()
            stubCompletedVideo(bodySlot)

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

            assertTrue("image_url" !in bodySlot.captured)
            assertTrue(result.isSuccess)
        }

    @Test
    fun `MUT-01 text-to-image-only request never attaches image fields`() =
        runTest {
            val bodySlot = slot<JsonObject>()
            stubCompletedImage(bodySlot)

            imageAdapter().generateImage(
                prompt = "draw",
                apiKey = "key",
                modelId = "fal-ai/recraft/v3",
                baseUrl = null,
                settings = ImageGenerationSettings(),
                referenceImage = byteArrayOf(1, 2, 3),
                supportsEdit = false,
            )

            assertTrue("image_url" !in bodySlot.captured)
            assertTrue("image" !in bodySlot.captured)
        }

    private fun stubCompletedImage(bodySlot: io.mockk.CapturingSlot<JsonObject>) {
        val status = kotlinx.serialization.json.buildJsonObject { put("status", "COMPLETED") }
        every { falService.submitQueue(any(), any(), capture(bodySlot), any()) } returns
            FalRequest("request-1", "https://queue.test/status", null, null)
        every { falService.status("key", "https://queue.test/status") } returns status
        every { falService.statusValue(status) } returns "COMPLETED"
        every { falService.primaryMediaUrls(status) } returns emptyList()
        every { falService.mediaUrls(status) } returns listOf("https://cdn.test/image.png")
        every { falService.resolveMediaBytes("https://cdn.test/image.png", "key") } returns byteArrayOf(4, 5, 6)
    }

    private fun stubCompletedVideo(bodySlot: io.mockk.CapturingSlot<JsonObject>) {
        val status = kotlinx.serialization.json.buildJsonObject { put("status", "COMPLETED") }
        val result = kotlinx.serialization.json.buildJsonObject { put("video_url", "https://cdn.test/video.mp4") }
        every { falService.submitQueue(any(), any(), capture(bodySlot), any()) } returns
            FalRequest("request-1", "https://queue.test/status", null, null)
        every { falService.status("key", "https://queue.test/status") } returns status
        every { falService.statusValue(status) } returns "COMPLETED"
        every { falService.result("key", "https://queue.test") } returns result
        every { falService.primaryVideoUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
        every { falService.primaryMediaUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
        every { falService.mediaUrls(any(), any()) } returns listOf("https://cdn.test/video.mp4")
        every { falService.resolveMediaBytes("https://cdn.test/video.mp4", "key") } returns byteArrayOf(4, 5, 6)
    }
}
