package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogResult
import com.browntowndev.enclave.domain.usecase.byok.FetchApiProviderModelDetailUseCase
import com.browntowndev.enclave.domain.usecase.byok.FetchApiProviderModelsUseCase
import com.browntowndev.enclave.domain.model.config.MediaTask
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoverApiModelsUseCaseTest {
    private val fetchApiProviderModelsUseCase: FetchApiProviderModelsUseCase = mockk()
    private val fetchApiProviderModelDetailUseCase: FetchApiProviderModelDetailUseCase = mockk()

    private lateinit var useCase: DiscoverApiModelsUseCase

    @BeforeEach
    fun setUp() {
        useCase =
            DiscoverApiModelsUseCase(
                fetchApiProviderModelsUseCase,
                fetchApiProviderModelDetailUseCase,
            )
    }

    @Test
    fun `invoke fetches and returns models`() =
        runTest {
            val request =
                ApiModelDiscoveryRequest(
                    provider = ApiProvider.ANTHROPIC,
                    currentApiKey = "key",
                    credentialAlias = null,
                    baseUrl = null,
                    selectedModelId = null,
                )
            val models = listOf(DiscoveredApiModel(id = "model-1"))

            coEvery {
                fetchApiProviderModelsUseCase(ApiProvider.ANTHROPIC, "key", null, null)
            } returns ApiModelCatalogResult(models)

            val result = useCase(request)

            assertEquals(1, result.models.size)
            assertEquals("model-1", result.models[0].id)
            assertEquals(ApiProvider.ANTHROPIC, result.scope.provider)
            assertEquals(false, result.isTruncated)
        }

    @Test
    fun `invoke fetches model detail for XAI if selected model missing context window`() =
        runTest {
            val request =
                ApiModelDiscoveryRequest(
                    provider = ApiProvider.XAI,
                    currentApiKey = "key",
                    credentialAlias = null,
                    baseUrl = null,
                    selectedModelId = "grok-beta",
                )
            // grok-beta is in the list but missing contextWindowTokens
            val models = listOf(DiscoveredApiModel(id = "grok-beta", contextWindowTokens = null))
            val detailedModel = DiscoveredApiModel(id = "grok-beta", contextWindowTokens = 128000)

            coEvery {
                fetchApiProviderModelsUseCase(ApiProvider.XAI, "key", null, null)
            } returns ApiModelCatalogResult(models)

            coEvery {
                fetchApiProviderModelDetailUseCase(ApiProvider.XAI, "grok-beta", "key", null, null)
            } returns detailedModel

            val result = useCase(request)

            assertEquals(1, result.models.size)
            assertEquals(128000, result.models[0].contextWindowTokens)
        }

    @Test
    fun `invoke fetches model detail for FAL if selected model missing tasks`() =
        runTest {
            val request =
                ApiModelDiscoveryRequest(
                    provider = ApiProvider.FAL,
                    currentApiKey = "key",
                    credentialAlias = null,
                    baseUrl = null,
                    selectedModelId = "fal-ai/flux/dev",
                )
            val models = listOf(DiscoveredApiModel(id = "fal-ai/flux/dev", tasks = emptySet()))
            val detailedModel = DiscoveredApiModel(id = "fal-ai/flux/dev", tasks = setOf(MediaTask.TEXT_TO_IMAGE))

            coEvery {
                fetchApiProviderModelsUseCase(ApiProvider.FAL, "key", null, null)
            } returns ApiModelCatalogResult(models)

            coEvery {
                fetchApiProviderModelDetailUseCase(ApiProvider.FAL, "fal-ai/flux/dev", "key", null, null)
            } returns detailedModel

            val result = useCase(request)

            assertEquals(1, result.models.size)
            assertEquals(setOf(MediaTask.TEXT_TO_IMAGE), result.models[0].tasks)
        }

    @Test
    fun `invoke adds selected model if not in the discovered list`() =
        runTest {
            val request =
                ApiModelDiscoveryRequest(
                    provider = ApiProvider.GOOGLE,
                    currentApiKey = "key",
                    credentialAlias = null,
                    baseUrl = null,
                    selectedModelId = "custom-model",
                )
            coEvery {
                fetchApiProviderModelsUseCase(ApiProvider.GOOGLE, "key", null, null)
            } returns ApiModelCatalogResult(emptyList())

            val result = useCase(request)

            assertEquals(1, result.models.size)
            assertEquals("custom-model", result.models[0].id)
        }

    @Test
    fun `invoke propagates catalog truncation flag`() =
        runTest {
            val request =
                ApiModelDiscoveryRequest(
                    provider = ApiProvider.FAL,
                    currentApiKey = "key",
                    credentialAlias = null,
                    baseUrl = null,
                    selectedModelId = null,
                )
            coEvery {
                fetchApiProviderModelsUseCase(ApiProvider.FAL, "key", null, null)
            } returns ApiModelCatalogResult(models = emptyList(), isTruncated = true)

            val result = useCase(request)

            assertTrue(result.isTruncated)
        }
}
