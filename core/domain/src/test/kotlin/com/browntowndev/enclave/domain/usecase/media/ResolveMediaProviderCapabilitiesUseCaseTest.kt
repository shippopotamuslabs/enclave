package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeApiModelCatalogPort
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.ModelGenerationInputSchema
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResolveMediaProviderCapabilitiesUseCaseTest {
    private val getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase()
    private val apiModelCatalog = FakeApiModelCatalogPort()
    private val apiKeyProvider = FakeApiKeyProvider()
    private val useCase =
        ResolveMediaProviderCapabilitiesUseCase(
            getProviderCapabilitiesUseCase = getProviderCapabilitiesUseCase,
            apiModelCatalog = apiModelCatalog,
            apiKeyProvider = apiKeyProvider,
        )

    @Test
    fun invoke_falModelWithOpenApiSchema_overridesVideoResolutions() =
        runTest {
            val provider =
                MediaProviderAsset(
                    id = MediaProviderId("fal-1"),
                    displayName = "fal",
                    provider = ApiProvider.FAL,
                    capability = MediaCapability.VIDEO,
                    credentialAlias = "fal-key",
                    modelName = "fal-ai/happy-horse-1.0",
                )
            apiKeyProvider.saveApiKey("fal-key", "test-key")
            apiModelCatalog.modelResult =
                DiscoveredApiModel(
                    id = "fal-ai/happy-horse-1.0",
                    name = "Happy Horse",
                    isVideoGenerationCapable = true,
                    inputSchema =
                        ModelGenerationInputSchema(
                            resolutionOptions = listOf("720p", "1080p"),
                            videoDurationTokens = listOf("5s", "10s"),
                            aspectRatioOptions = listOf("16:9"),
                        ),
                )

            val capabilities =
                useCase(
                    mediaProvider = provider,
                    mediaCapability = MediaCapability.VIDEO,
                )

            assertEquals(listOf("720p", "1080p"), capabilities.supportedVideoResolutions)
            assertEquals(listOf(5, 10), capabilities.supportedVideoDurations)
            assertEquals(listOf("5s", "10s"), capabilities.supportedVideoDurationTokens)
            assertEquals(listOf(AspectRatio.SIXTEEN_NINE), capabilities.supportedAspectRatios)
        }

    @Test
    fun invoke_falImageModelWithResolutionEnum_exposesImageResolutionsWithoutQuality() =
        runTest {
            val provider =
                MediaProviderAsset(
                    id = MediaProviderId("fal-img"),
                    displayName = "fal",
                    provider = ApiProvider.FAL,
                    capability = MediaCapability.IMAGE,
                    credentialAlias = "fal-key",
                    modelName = "fal-ai/flux/dev",
                )
            apiKeyProvider.saveApiKey("fal-key", "test-key")
            apiModelCatalog.modelResult =
                DiscoveredApiModel(
                    id = "fal-ai/flux/dev",
                    name = "Flux",
                    isImageGenerationCapable = true,
                    inputSchema =
                        ModelGenerationInputSchema(
                            resolutionOptions = listOf("512", "1K", "2K"),
                            aspectRatioOptions = listOf("1:1"),
                        ),
                )

            val capabilities =
                useCase(
                    mediaProvider = provider,
                    mediaCapability = MediaCapability.IMAGE,
                )

            assertEquals(emptyList<com.browntowndev.enclave.domain.model.media.GenerationQuality>(), capabilities.supportedImageQualities)
            assertEquals(listOf("512", "1K", "2K"), capabilities.supportedImageResolutions)
        }

    @Test
    fun invoke_nonFalProvider_returnsStaticCapabilities() =
        runTest {
            val provider =
                MediaProviderAsset(
                    id = MediaProviderId("openai-1"),
                    displayName = "OpenAI",
                    provider = ApiProvider.OPENAI,
                    capability = MediaCapability.VIDEO,
                    credentialAlias = "openai-key",
                    modelName = "sora",
                )

            val capabilities =
                useCase(
                    mediaProvider = provider,
                    mediaCapability = MediaCapability.VIDEO,
                )

            assertEquals(listOf(4, 8, 12), capabilities.supportedVideoDurations)
        }
}
