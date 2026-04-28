package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.media.GoogleImageGenerationAdapter
import com.google.genai.Client
import com.google.genai.types.GenerateImagesConfig
import com.google.genai.types.GenerateImagesResponse
import com.google.genai.types.GeneratedImage
import com.google.genai.types.Image
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleImageGenerationAdapterTest {
    private val clientProvider = mockk<GoogleGenAiClientProviderPort>()
    private val client = mockk<Client>()

    @Test
    fun generateImage_passesNumberOfImages_andDecodesAllReturnedImages() = runTest {
        lateinit var capturedConfig: GenerateImagesConfig
        val adapter = object : GoogleImageGenerationAdapter(clientProvider) {
            override fun generateImages(
                client: Client,
                modelId: String,
                prompt: String,
                config: GenerateImagesConfig,
            ): GenerateImagesResponse {
                capturedConfig = config
                return successfulImageResponse(
                    "first".toByteArray(),
                    "second".toByteArray(),
                )
            }
        }
        every { clientProvider.getClient("key", null) } returns client

        val result = adapter.generateImage(
            prompt = "prompt",
            apiKey = "key",
            modelId = "imagen-3",
            baseUrl = null,
            settings = ImageGenerationSettings(generationCount = 2),
        )

        assertTrue(result.isSuccess)
        assertEquals(2, capturedConfig.numberOfImages().orElseThrow())
        assertArrayEquals("first".toByteArray(), result.getOrThrow()[0])
        assertArrayEquals("second".toByteArray(), result.getOrThrow()[1])
    }

    private fun successfulImageResponse(vararg images: ByteArray): GenerateImagesResponse =
        GenerateImagesResponse.builder()
            .generatedImages(
                images.map { imageBytes ->
                    GeneratedImage.builder()
                        .image(Image.builder().imageBytes(imageBytes).build())
                        .build()
                },
            )
            .build()
}
