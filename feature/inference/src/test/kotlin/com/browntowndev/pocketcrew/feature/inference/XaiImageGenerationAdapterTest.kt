package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationQuality
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.media.XaiImageGenerationAdapter
import com.openai.client.OpenAIClient
import com.openai.models.images.Image
import com.openai.models.images.ImageGenerateParams
import com.openai.models.images.ImagesResponse
import com.openai.services.blocking.ImageService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.test.runTest

class XaiImageGenerationAdapterTest {
    private val clientProvider = mockk<OpenAiClientProviderPort>()
    private val client = mockk<OpenAIClient>()
    private val imageService = mockk<ImageService>()
    private val adapter = XaiImageGenerationAdapter(clientProvider)

    @Test
    fun generateImage_usesXaiSpecificImageFields_withoutOpenAiSize() = runTest {
        val paramsSlot = slot<ImageGenerateParams>()
        every { clientProvider.getClient("key", "https://api.x.ai/v1") } returns client
        every { client.images() } returns imageService
        every { imageService.generate(capture(paramsSlot)) } returns successfulImageResponse()

        val result = adapter.generateImage(
            prompt = "A serene Japanese garden",
            apiKey = "key",
            modelId = "grok-imagine-image",
            baseUrl = null,
            settings = ImageGenerationSettings(
                aspectRatio = AspectRatio.SIXTEEN_NINE,
                quality = GenerationQuality.HD,
            ),
        )

        assertTrue(result.isSuccess)
        assertArrayEquals("image".toByteArray(), result.getOrThrow())
        val params = paramsSlot.captured
        assertEquals("A serene Japanese garden", params.prompt())
        assertEquals("grok-imagine-image", params.model().orElseThrow().asString())
        assertEquals(ImageGenerateParams.ResponseFormat.B64_JSON, params.responseFormat().orElseThrow())
        assertFalse(params.size().isPresent)
        assertEquals("16:9", params._additionalBodyProperties()["aspect_ratio"]?.convert(String::class.java))
        assertEquals("2k", params._additionalBodyProperties()["resolution"]?.convert(String::class.java))
    }

    @Test
    fun generateImage_mapsUnsupportedDomainAspectRatios_toNearestXaiRatio() = runTest {
        val paramsSlot = slot<ImageGenerateParams>()
        every { clientProvider.getClient("key", "https://custom.x.ai/v1") } returns client
        every { client.images() } returns imageService
        every { imageService.generate(capture(paramsSlot)) } returns successfulImageResponse()

        val result = adapter.generateImage(
            prompt = "wide scene",
            apiKey = "key",
            modelId = "grok-imagine-image",
            baseUrl = "https://custom.x.ai/v1",
            settings = ImageGenerationSettings(
                aspectRatio = AspectRatio.TWENTY_ONE_NINE,
                quality = GenerationQuality.SPEED,
            ),
        )

        assertTrue(result.isSuccess)
        assertFalse(paramsSlot.captured.size().isPresent)
        assertEquals("20:9", paramsSlot.captured._additionalBodyProperties()["aspect_ratio"]?.convert(String::class.java))
        assertEquals("1k", paramsSlot.captured._additionalBodyProperties()["resolution"]?.convert(String::class.java))
    }

    private fun successfulImageResponse(): ImagesResponse {
        val encodedImage = Base64.getEncoder().encodeToString("image".toByteArray())
        return ImagesResponse.builder()
            .created(0)
            .addData(Image.builder().b64Json(encodedImage).build())
            .build()
    }
}
