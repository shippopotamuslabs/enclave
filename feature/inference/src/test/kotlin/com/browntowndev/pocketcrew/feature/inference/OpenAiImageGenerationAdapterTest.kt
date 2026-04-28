package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.media.OpenAiImageGenerationAdapter
import com.openai.client.OpenAIClient
import com.openai.models.images.Image
import com.openai.models.images.ImageGenerateParams
import com.openai.models.images.ImagesResponse
import com.openai.services.blocking.ImageService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
        assertEquals(2L, paramsSlot.captured.n().orElseThrow())
        assertArrayEquals("first".toByteArray(), result.getOrThrow()[0])
        assertArrayEquals("second".toByteArray(), result.getOrThrow()[1])
    }

    @Test
    fun generateImage_dalle3Batch_usesSingleImageFallbackRequests() = runTest {
        val params = mutableListOf<ImageGenerateParams>()
        every { clientProvider.getClient("key", null) } returns client
        every { client.images() } returns imageService
        every { imageService.generate(capture(params)) } returnsMany listOf(
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
        assertEquals(listOf(1L, 1L), params.map { it.n().orElseThrow() })
        assertEquals(setOf("first", "second"), result.getOrThrow().map { it.decodeToString() }.toSet())
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
