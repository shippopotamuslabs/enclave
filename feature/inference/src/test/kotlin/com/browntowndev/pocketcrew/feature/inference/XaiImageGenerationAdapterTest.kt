package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationQuality
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.media.XaiImageGenerationAdapter
import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.errors.BadRequestException
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
import okhttp3.MediaType.Companion.toMediaType

class XaiImageGenerationAdapterTest {
    private val clientProvider = mockk<OpenAiClientProviderPort>()
    private val client = mockk<OpenAIClient>()
    private val imageService = mockk<ImageService>()
    private val okHttpClient = mockk<okhttp3.OkHttpClient>()
    private val adapter = XaiImageGenerationAdapter(clientProvider, okHttpClient)

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
                quality = GenerationQuality.QUALITY,
                generationCount = 3,
            ),
        )

        assertTrue(result.isSuccess)
        assertArrayEquals("image".toByteArray(), result.getOrThrow().single())
        val params = paramsSlot.captured
        assertEquals("A serene Japanese garden", params.prompt())
        assertEquals("grok-imagine-image", params.model().orElseThrow().asString())
        assertEquals(3L, params.n().orElseThrow())
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

    @Test
    fun generateImage_decodesAllReturnedImages() = runTest {
        every { clientProvider.getClient("key", "https://api.x.ai/v1") } returns client
        every { client.images() } returns imageService
        every { imageService.generate(any<ImageGenerateParams>()) } returns successfulImageResponse(
            "first".toByteArray(),
            "second".toByteArray(),
        )

        val result = adapter.generateImage(
            prompt = "batch",
            apiKey = "key",
            modelId = "grok-imagine-image",
            baseUrl = null,
            settings = ImageGenerationSettings(generationCount = 2),
        )

        assertTrue(result.isSuccess)
        assertArrayEquals("first".toByteArray(), result.getOrThrow()[0])
        assertArrayEquals("second".toByteArray(), result.getOrThrow()[1])
    }

    @Test
    fun generateImage_passedModerationFalse_returnsModerationFailure() = runTest {
        every { clientProvider.getClient("key", "https://api.x.ai/v1") } returns client
        every { client.images() } returns imageService
        every { imageService.generate(any<ImageGenerateParams>()) } returns ImagesResponse.builder()
            .created(0)
            .putAdditionalProperty("passed_moderation", JsonValue.from(false))
            .build()

        val result = adapter.generateImage(
            prompt = "rejected prompt",
            apiKey = "key",
            modelId = "grok-imagine-image",
            baseUrl = null,
            settings = ImageGenerationSettings(),
        )

        assertTrue(result.isFailure)
        assertEquals("Prompt rejected due to moderation.", result.exceptionOrNull()?.message)
    }

    @Test
    fun generateImage_badRequest_returnsModerationFailure() = runTest {
        val badRequest = mockk<BadRequestException>()
        every { badRequest.statusCode() } returns 400
        every { clientProvider.getClient("key", "https://api.x.ai/v1") } returns client
        every { client.images() } returns imageService
        every { imageService.generate(any<ImageGenerateParams>()) } throws badRequest

        val result = adapter.generateImage(
            prompt = "rejected prompt",
            apiKey = "key",
            modelId = "grok-imagine-image",
            baseUrl = null,
            settings = ImageGenerationSettings(),
        )

        assertTrue(result.isFailure)
        assertEquals("Prompt rejected due to moderation.", result.exceptionOrNull()?.message)
    }

    @Test
    fun generateImage_withReferenceImage_hitsImagesEditsEndpoint() = runTest {
        // A failing test to prove we are hitting OkHttpClient instead of OpenAI Java SDK
        // This will fail because the current code uses the OpenAI SDK's client.images().generate()
        
        val requestSlot = slot<okhttp3.Request>()
        val call = mockk<okhttp3.Call>()
        every { okHttpClient.newCall(capture(requestSlot)) } returns call
        every { call.execute() } returns okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("https://api.x.ai/v1/images/edits").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(
                okhttp3.ResponseBody.create(
                    "application/json".toMediaType(),
                    """
                    {
                        "data": [
                            {
                                "b64_json": "${Base64.getEncoder().encodeToString("fake-edited-image".toByteArray())}"
                            }
                        ]
                    }
                    """.trimIndent()
                )
            )
            .build()

        val result = adapter.generateImage(
            prompt = "Edit this",
            apiKey = "key",
            modelId = "grok-imagine-image",
            baseUrl = null,
            settings = ImageGenerationSettings(referenceImageUri = "file:///fake.png"),
            referenceImage = "fake-image".toByteArray()
        )

        assertTrue(result.isSuccess)
        val request = requestSlot.captured
        assertTrue(request.url.toString().endsWith("/images/edits"))
        assertEquals("POST", request.method)
        assertArrayEquals("fake-edited-image".toByteArray(), result.getOrThrow().single())
    }

    private fun successfulImageResponse(vararg images: ByteArray = arrayOf("image".toByteArray())): ImagesResponse {
        return ImagesResponse.builder()
            .created(0)
            .also { responseBuilder ->
                images.forEach { image ->
                    val encodedImage = Base64.getEncoder().encodeToString(image)
                    responseBuilder.addData(Image.builder().b64Json(encodedImage).build())
                }
            }
            .build()
    }
}
