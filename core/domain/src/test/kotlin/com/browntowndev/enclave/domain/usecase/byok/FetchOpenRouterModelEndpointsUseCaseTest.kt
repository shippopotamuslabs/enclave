package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeApiModelCatalogPort
import com.browntowndev.enclave.domain.model.config.OpenRouterModelEndpoint
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FetchOpenRouterModelEndpointsUseCaseTest {
    private val apiKeyProviderPort = FakeApiKeyProvider()
    private val apiModelCatalogPort = FakeApiModelCatalogPort()
    private lateinit var useCase: FetchOpenRouterModelEndpointsUseCase

    @BeforeEach
    fun setup() {
        useCase =
            FetchOpenRouterModelEndpointsUseCaseImpl(
                apiKeyProviderPort = apiKeyProviderPort,
                apiModelCatalogPort = apiModelCatalogPort,
            )
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SCN-DOMAIN-001")
    fun scnDomain001_fetchOpenRouterModelEndpointsUseCaseCallsCatalogPortWithResolvedApiKey() =
        runTest {
            val mockEndpoints =
                listOf(
                    OpenRouterModelEndpoint(
                        providerName = "Together",
                        providerSlug = "together",
                        contextLength = 131072,
                        promptPriceUsdPerMillion = 0.7,
                        completionPriceUsdPerMillion = 0.9,
                        quantization = "fp8",
                        isOperational = true,
                        uptime1d = 99.95,
                        latency30m = 0.25,
                        throughput30m = 45.0,
                    ),
                )

            apiKeyProviderPort.saveApiKey("alias-or", "secret-api-key")
            apiModelCatalogPort.endpointsResult = mockEndpoints

            val result =
                useCase(
                    modelId = "meta-llama/llama-3.3-70b-instruct",
                    currentApiKey = "",
                    credentialAlias = "alias-or",
                    baseUrl = null,
                )

            assertEquals(mockEndpoints, result)
            assertEquals(listOf("alias-or"), apiKeyProviderPort.getApiKeyCalls)
            assertEquals(
                listOf(
                    FakeApiModelCatalogPort.FetchModelEndpointsCall(
                        modelId = "meta-llama/llama-3.3-70b-instruct",
                        apiKey = "secret-api-key",
                        baseUrl = null,
                    ),
                ),
                apiModelCatalogPort.fetchModelEndpointsCalls,
            )
        }
}
