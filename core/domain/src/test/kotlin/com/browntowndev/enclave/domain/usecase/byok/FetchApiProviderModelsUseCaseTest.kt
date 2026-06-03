package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogPort
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogResult
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FetchApiProviderModelsUseCaseTest {
    private val apiModelCatalog = mockk<ApiModelCatalogPort>()
    private val apiKeyProvider = mockk<ApiKeyProviderPort>()
    private val useCase =
        FetchApiProviderModelsUseCaseImpl(
            apiModelCatalog = apiModelCatalog,
            apiKeyProvider = apiKeyProvider,
        )

    @Test
    fun `uses in-memory api key when present`() =
        runTest {
            coEvery {
                apiModelCatalog.fetchModels(ApiProvider.OPENAI, "live-key", null)
            } returns ApiModelCatalogResult(listOf(DiscoveredApiModel(id = "gpt-5")))

            val catalog =
                useCase(
                    provider = ApiProvider.OPENAI,
                    currentApiKey = "live-key",
                    credentialAlias = "saved-alias",
                    baseUrl = null,
                )

            assertEquals(listOf(DiscoveredApiModel(id = "gpt-5")), catalog.models)
            coVerify(exactly = 1) {
                apiModelCatalog.fetchModels(ApiProvider.OPENAI, "live-key", null)
            }
        }

    @Test
    fun `falls back to stored api key when current key is blank`() =
        runTest {
            coEvery { apiKeyProvider.getApiKey("saved-alias") } returns "stored-key"
            coEvery {
                apiModelCatalog.fetchModels(ApiProvider.XAI, "stored-key", "https://api.x.ai/v1")
            } returns ApiModelCatalogResult(listOf(DiscoveredApiModel(id = "grok-4.20-multi-agent")))

            val catalog =
                useCase(
                    provider = ApiProvider.XAI,
                    currentApiKey = "",
                    credentialAlias = "saved-alias",
                    baseUrl = "https://api.x.ai/v1",
                )

            assertEquals(listOf(DiscoveredApiModel(id = "grok-4.20-multi-agent")), catalog.models)
            coVerify(exactly = 1) {
                apiModelCatalog.fetchModels(ApiProvider.XAI, "stored-key", "https://api.x.ai/v1")
            }
        }

    @Test
    fun `supports openrouter discovery with default base url`() =
        runTest {
            coEvery {
                apiModelCatalog.fetchModels(ApiProvider.OPENROUTER, "router-key", "https://openrouter.ai/api/v1")
            } returns ApiModelCatalogResult(listOf(DiscoveredApiModel(id = "openai/gpt-5.2")))

            val catalog =
                useCase(
                    provider = ApiProvider.OPENROUTER,
                    currentApiKey = "router-key",
                    credentialAlias = "router-alias",
                    baseUrl = "https://openrouter.ai/api/v1",
                )

            assertEquals(listOf(DiscoveredApiModel(id = "openai/gpt-5.2")), catalog.models)
            coVerify(exactly = 1) {
                apiModelCatalog.fetchModels(ApiProvider.OPENROUTER, "router-key", "https://openrouter.ai/api/v1")
            }
        }

    @Test
    fun `supports anthropic discovery with default base url`() =
        runTest {
            coEvery {
                apiModelCatalog.fetchModels(ApiProvider.ANTHROPIC, "anthropic-key", null)
            } returns ApiModelCatalogResult(listOf(DiscoveredApiModel(id = "claude-sonnet-4-20250514")))

            val catalog =
                useCase(
                    provider = ApiProvider.ANTHROPIC,
                    currentApiKey = "anthropic-key",
                    credentialAlias = "anthropic-alias",
                    baseUrl = null,
                )

            assertEquals(listOf(DiscoveredApiModel(id = "claude-sonnet-4-20250514")), catalog.models)
            coVerify(exactly = 1) {
                apiModelCatalog.fetchModels(ApiProvider.ANTHROPIC, "anthropic-key", null)
            }
        }

    @Test
    fun `fails when no api key is available`() =
        runTest {
            coEvery { apiKeyProvider.getApiKey(any()) } returns null

            assertFailsWith<IllegalArgumentException> {
                useCase(
                    provider = ApiProvider.OPENAI,
                    currentApiKey = "",
                    credentialAlias = "saved-alias",
                    baseUrl = null,
                )
            }
        }
}
