package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeApiModelCatalogPort
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FetchApiProviderModelsUseCaseTest {
    private val apiModelCatalog = FakeApiModelCatalogPort()
    private val apiKeyProvider = FakeApiKeyProvider()
    private val useCase =
        FetchApiProviderModelsUseCaseImpl(
            apiModelCatalog = apiModelCatalog,
            apiKeyProvider = apiKeyProvider,
        )

    @Test
    fun `uses in-memory api key when present`() =
        runTest {
            apiModelCatalog.modelsResult =
                ApiModelCatalogResult(listOf(DiscoveredApiModel(id = "gpt-5")))

            val catalog =
                useCase(
                    provider = ApiProvider.OPENAI,
                    currentApiKey = "live-key",
                    credentialAlias = "saved-alias",
                    baseUrl = null,
                )

            assertEquals(listOf(DiscoveredApiModel(id = "gpt-5")), catalog.models)
            assertEquals(
                listOf(FakeApiModelCatalogPort.FetchModelsCall(ApiProvider.OPENAI, "live-key", null)),
                apiModelCatalog.fetchModelsCalls,
            )
        }

    @Test
    fun `falls back to stored api key when current key is blank`() =
        runTest {
            apiKeyProvider.saveApiKey("saved-alias", "stored-key")
            apiModelCatalog.modelsResult =
                ApiModelCatalogResult(listOf(DiscoveredApiModel(id = "grok-4.20-multi-agent")))

            val catalog =
                useCase(
                    provider = ApiProvider.XAI,
                    currentApiKey = "",
                    credentialAlias = "saved-alias",
                    baseUrl = "https://api.x.ai/v1",
                )

            assertEquals(listOf(DiscoveredApiModel(id = "grok-4.20-multi-agent")), catalog.models)
            assertEquals(
                listOf(
                    FakeApiModelCatalogPort.FetchModelsCall(
                        ApiProvider.XAI,
                        "stored-key",
                        "https://api.x.ai/v1",
                    ),
                ),
                apiModelCatalog.fetchModelsCalls,
            )
        }

    @Test
    fun `supports openrouter discovery with default base url`() =
        runTest {
            apiModelCatalog.modelsResult =
                ApiModelCatalogResult(listOf(DiscoveredApiModel(id = "openai/gpt-5.2")))

            val catalog =
                useCase(
                    provider = ApiProvider.OPENROUTER,
                    currentApiKey = "router-key",
                    credentialAlias = "router-alias",
                    baseUrl = "https://openrouter.ai/api/v1",
                )

            assertEquals(listOf(DiscoveredApiModel(id = "openai/gpt-5.2")), catalog.models)
            assertEquals(
                listOf(
                    FakeApiModelCatalogPort.FetchModelsCall(
                        ApiProvider.OPENROUTER,
                        "router-key",
                        "https://openrouter.ai/api/v1",
                    ),
                ),
                apiModelCatalog.fetchModelsCalls,
            )
        }

    @Test
    fun `supports anthropic discovery with default base url`() =
        runTest {
            apiModelCatalog.modelsResult =
                ApiModelCatalogResult(listOf(DiscoveredApiModel(id = "claude-sonnet-4-20250514")))

            val catalog =
                useCase(
                    provider = ApiProvider.ANTHROPIC,
                    currentApiKey = "anthropic-key",
                    credentialAlias = "anthropic-alias",
                    baseUrl = null,
                )

            assertEquals(listOf(DiscoveredApiModel(id = "claude-sonnet-4-20250514")), catalog.models)
            assertEquals(
                listOf(FakeApiModelCatalogPort.FetchModelsCall(ApiProvider.ANTHROPIC, "anthropic-key", null)),
                apiModelCatalog.fetchModelsCalls,
            )
        }

    @Test
    fun `fails when no api key is available`() =
        runTest {
            // No key stored for the alias, so getApiKey returns null.

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
