package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.media.GoogleImageGenerationAdapter
import com.google.genai.Client
import com.google.genai.types.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
        assertEquals(2, capturedConfig.numberOfImages().get())
        assertArrayEquals("first".toByteArray(), result.getOrThrow()[0])
        assertArrayEquals("second".toByteArray(), result.getOrThrow()[1])
    }

    @Test
    fun generateImage_withReferenceImage_callsEditImage() = runTest {
        lateinit var capturedConfig: EditImageConfig
        lateinit var capturedReferenceImage: RawReferenceImage
        lateinit var capturedPrompt: String
        val adapter = object : GoogleImageGenerationAdapter(clientProvider) {
            override fun editImage(
                client: Client,
                modelId: String,
                prompt: String,
                referenceImage: RawReferenceImage,
                config: EditImageConfig,
            ): EditImageResponse {
                capturedConfig = config
                capturedReferenceImage = referenceImage
                capturedPrompt = prompt
                val genResponse = successfulImageResponse("edited".toByteArray())
                return EditImageResponse.builder()
                    .generatedImages(genResponse.generatedImages().orElse(emptyList()))
                    .build()
            }
        }
        every { clientProvider.getClient("key", null) } returns client
        val referenceBytes = "ref".toByteArray()

        val result = adapter.generateImage(
            prompt = "prompt",
            apiKey = "key",
            modelId = "imagen-3",
            baseUrl = null,
            settings = ImageGenerationSettings(generationCount = 1, referenceImageUri = "file://test.jpg"),
            referenceImage = referenceBytes
        )

        assertTrue(result.isSuccess)
        assertEquals("edited", String(result.getOrThrow()[0]))
        assertEquals("prompt [1]", capturedPrompt)
        assertEquals("raw", capturedReferenceImage.referenceType().get())
        assertArrayEquals(referenceBytes, capturedReferenceImage.referenceImage().get().imageBytes().get())
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
