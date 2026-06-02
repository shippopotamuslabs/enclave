package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import com.browntowndev.enclave.feature.inference.media.FalImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.GoogleImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.ImageGenerationPortImpl
import com.browntowndev.enclave.feature.inference.media.OpenAiImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.XaiImageGenerationAdapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImageGenerationPortImplTest {
    private val apiKeyProvider = mockk<ApiKeyProviderPort>()
    private val studioRepository = mockk<StudioRepositoryPort>()
    private val openAiAdapter = mockk<OpenAiImageGenerationAdapter>()
    private val googleAdapter = mockk<GoogleImageGenerationAdapter>()
    private val xaiAdapter = mockk<XaiImageGenerationAdapter>()
    private val falAdapter = mockk<FalImageGenerationAdapter>()
    private lateinit var port: ImageGenerationPortImpl

    @BeforeEach
    fun setup() {
        port =
            ImageGenerationPortImpl(
                apiKeyProvider = apiKeyProvider,
                studioRepository = studioRepository,
                openAiAdapter = openAiAdapter,
                googleAdapter = googleAdapter,
                xaiAdapter = xaiAdapter,
                falAdapter = falAdapter,
            )
        coEvery { apiKeyProvider.getApiKey("alias") } returns "key"
        coEvery { studioRepository.readMediaBytes("cache://reference.png") } returns "png".toByteArray()
    }

    @Test
    fun generateImage_missingApiKey_returnsFailure() =
        runTest {
            coEvery { apiKeyProvider.getApiKey("alias") } returns null

            val result = port.generateImage("prompt", provider(ApiProvider.OPENAI), ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("API key not found") == true)
        }

    @Test
    fun generateImage_falProvider_loadsReferenceAndUsesDefaultModel() =
        runTest {
            coEvery {
                falAdapter.generateImage(any(), any(), any(), any(), any(), any())
            } returns Result.success(listOf("fal".toByteArray()))

            val settings = ImageGenerationSettings(referenceImageUri = "cache://reference.png")

            val result =
                port.generateImage(
                    prompt = "draw",
                    provider = provider(ApiProvider.FAL, modelName = null),
                    settings = settings,
                )

            assertTrue(result.isSuccess)
            assertArrayEquals("fal".toByteArray(), result.getOrThrow().single())
            coVerify {
                falAdapter.generateImage(
                    prompt = "draw",
                    apiKey = "key",
                    modelId = "fal-ai/flux-pro/kontext",
                    baseUrl = null,
                    settings = settings,
                    referenceImage = match { it.contentEquals("png".toByteArray()) },
                )
            }
        }

    @Test
    fun generateImage_unsupportedProvider_returnsFailure() =
        runTest {
            val result = port.generateImage("prompt", provider(ApiProvider.ANTHROPIC), ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
        }

    private fun provider(
        provider: ApiProvider,
        modelName: String? = "custom-model",
    ): MediaProviderAsset =
        MediaProviderAsset(
            id = MediaProviderId("provider"),
            displayName = provider.displayName,
            provider = provider,
            capability = MediaCapability.IMAGE,
            modelName = modelName,
            credentialAlias = "alias",
        )
}
