package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.media.OpenAiImageGenerationAdapter
import com.openai.client.OpenAIClient
import com.openai.models.images.*
import com.openai.services.blocking.ImageService
import io.mockk.*
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenAiImageGenerationAdapterTest {
    private val clientProvider = mockk<OpenAiClientProviderPort>()
    private val client = mockk<OpenAIClient>()
    private val imageService = mockk<ImageService>()
    private val adapter = OpenAiImageGenerationAdapter(clientProvider)

    @Test
    fun generateImage_batchCapableModel_passesNativeCount() = runTest {
        val paramsSlot = slot<ImageGenerateParams>()
        every { clientProvider.getClient("key", null) } returns client
        every { client.images() } returns imageService
        every { imageService.generate(capture(paramsSlot)) } returns successfulImageResponse(
            "first".toByteArray(),
            "second".toByteArray(),
        )

        val result = adapter.generateImage(
            prompt = "prompt",
            apiKey = "key",
            modelId = "gpt-image-1",
            baseUrl = null,
            settings = ImageGenerationSettings(generationCount = 2),
        )

        assertTrue(result.isSuccess)
        assertEquals(2L, paramsSlot.captured.n().get())
        assertArrayEquals("first".toByteArray(), result.getOrThrow()[0])
        assertArrayEquals("second".toByteArray(), result.getOrThrow()[1])
    }

    @Test
    fun generateImage_dalle3Batch_usesSingleImageFallbackRequests() = runTest {
        every { clientProvider.getClient("key", null) } returns client
        every { client.images() } returns imageService
        every { imageService.generate(any()) } returnsMany listOf(
            successfulImageResponse("first".toByteArray()),
            successfulImageResponse("second".toByteArray()),
        )

        val result = adapter.generateImage(
            prompt = "prompt",
            apiKey = "key",
            modelId = "dall-e-3",
            baseUrl = null,
            settings = ImageGenerationSettings(generationCount = 2),
        )

        assertTrue(result.isSuccess)
        val capturedParams = mutableListOf<ImageGenerateParams>()
        verify(exactly = 2) { imageService.generate(capture(capturedParams)) }
        assertEquals(listOf(1L, 1L), capturedParams.map { it.n().get() })
        assertEquals(setOf("first", "second"), result.getOrThrow().map { it.decodeToString() }.toSet())
    }

    @Test
    fun generateImage_withReferenceImage_throwsUnsupported() = runTest {
        // Prove that OpenAI does not support image-to-image with text prompts natively without
        // hallucinatory mask behavior, leading us to fail fast.
        
        val result = adapter.generateImage(
            prompt = "prompt",
            apiKey = "key",
            modelId = "dall-e-3",
            baseUrl = null,
            settings = ImageGenerationSettings(generationCount = 1),
            referenceImage = "fake-bytes".toByteArray()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
        assertEquals("OpenAI does not support image-to-image generation with text prompts.", result.exceptionOrNull()?.message)
    }

    private fun successfulImageResponse(vararg images: ByteArray): ImagesResponse =
        ImagesResponse.builder()
            .created(0)
            .also { responseBuilder ->
                images.forEach { image ->
                    val encodedImage = Base64.getEncoder().encodeToString(image)
                    responseBuilder.addData(Image.builder().b64Json(encodedImage).build())
                }
            }
            .build()
}
