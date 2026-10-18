@file:Suppress("ForbiddenMockKInTests")

// MockK is retained only for the third-party OpenAI SDK surface (OpenAIClient/ImageService); the owned client-provider port uses a fake.
package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.feature.inference.media.OpenAiImageGenerationAdapter
import com.openai.client.OpenAIClient
import com.openai.models.images.*
import com.openai.services.blocking.ImageService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenAiImageGenerationAdapterTest {
    private val client = mockk<OpenAIClient>()
    private val clientProvider = FakeOpenAiClientProviderPort().also { it.client = client }
    private val imageService = mockk<ImageService>()
    private val adapter = OpenAiImageGenerationAdapter(clientProvider)

    @Test
    fun generateImage_passesNativeCount() =
        runTest {
            val paramsSlot = slot<ImageGenerateParams>()
            every { client.images() } returns imageService
            every { imageService.generate(capture(paramsSlot)) } returns
                successfulImageResponse(
                    "first".toByteArray(),
                    "second".toByteArray(),
                )

            val result =
                adapter.generateImage(
                    prompt = "prompt",
                    apiKey = "key",
                    modelId = "gpt-image-2",
                    baseUrl = null,
                    settings = ImageGenerationSettings(generationCount = 2),
                )

            assertTrue(result.isSuccess)
            assertEquals(2L, paramsSlot.captured.n().get())
            assertArrayEquals("first".toByteArray(), result.getOrThrow()[0])
            assertArrayEquals("second".toByteArray(), result.getOrThrow()[1])
        }

    @Test
    fun generateImage_usesCorrectModernMappings() =
        runTest {
            val paramsSlot = slot<ImageGenerateParams>()
            every { client.images() } returns imageService
            every { imageService.generate(capture(paramsSlot)) } returns successfulImageResponse("image".toByteArray())

            // Test High
            adapter.generateImage(
                prompt = "prompt",
                apiKey = "key",
                modelId = "gpt-image-2",
                baseUrl = null,
                settings =
                    ImageGenerationSettings(
                        aspectRatio = AspectRatio.SIXTEEN_NINE,
                        quality = GenerationQuality.HIGH,
                    ),
            )
            assertEquals(
                "high",
                paramsSlot.captured
                    .quality()
                    .get()
                    .toString(),
            )
            assertEquals(
                "1536x1024",
                paramsSlot.captured
                    .size()
                    .get()
                    .toString(),
            )

            // Test Low
            adapter.generateImage(
                prompt = "prompt",
                apiKey = "key",
                modelId = "gpt-image-2",
                baseUrl = null,
                settings =
                    ImageGenerationSettings(
                        aspectRatio = AspectRatio.NINE_SIXTEEN,
                        quality = GenerationQuality.LOW,
                    ),
            )
            assertEquals(
                "low",
                paramsSlot.captured
                    .quality()
                    .get()
                    .toString(),
            )
            assertEquals(
                "1024x1536",
                paramsSlot.captured
                    .size()
                    .get()
                    .toString(),
            )

            // Test Medium
            adapter.generateImage(
                prompt = "prompt",
                apiKey = "key",
                modelId = "gpt-image-2",
                baseUrl = null,
                settings =
                    ImageGenerationSettings(
                        aspectRatio = AspectRatio.ONE_ONE,
                        quality = GenerationQuality.MEDIUM,
                    ),
            )
            assertEquals(
                "medium",
                paramsSlot.captured
                    .quality()
                    .get()
                    .toString(),
            )
            assertEquals(
                "1024x1024",
                paramsSlot.captured
                    .size()
                    .get()
                    .toString(),
            )

            // Test Auto
            adapter.generateImage(
                prompt = "prompt",
                apiKey = "key",
                modelId = "gpt-image-2",
                baseUrl = null,
                settings =
                    ImageGenerationSettings(
                        aspectRatio = AspectRatio.ONE_ONE,
                        quality = GenerationQuality.AUTO,
                    ),
            )
            assertEquals(
                "auto",
                paramsSlot.captured
                    .quality()
                    .get()
                    .toString(),
            )
        }

    @Test
    fun generateImage_withReferenceImage_throwsUnsupported() =
        runTest {
            val result =
                adapter.generateImage(
                    prompt = "prompt",
                    apiKey = "key",
                    modelId = "gpt-image-2",
                    baseUrl = null,
                    settings = ImageGenerationSettings(generationCount = 1),
                    referenceImage = "fake-bytes".toByteArray(),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
            assertEquals("OpenAI does not support image-to-image generation with text prompts.", result.exceptionOrNull()?.message)
        }

    private fun successfulImageResponse(vararg images: ByteArray): ImagesResponse =
        ImagesResponse
            .builder()
            .created(0)
            .also { responseBuilder ->
                images.forEach { image ->
                    val encodedImage = Base64.getEncoder().encodeToString(image)
                    responseBuilder.addData(Image.builder().b64Json(encodedImage).build())
                }
            }.build()
}
