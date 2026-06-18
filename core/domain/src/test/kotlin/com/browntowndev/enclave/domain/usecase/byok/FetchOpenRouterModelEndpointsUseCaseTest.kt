package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.config.OpenRouterModelEndpoint
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FetchOpenRouterModelEndpointsUseCaseTest {
    private val apiKeyProviderPort = mockk<ApiKeyProviderPort>()
    private val apiModelCatalogPort = mockk<ApiModelCatalogPort>()
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

            coEvery { apiKeyProviderPort.getApiKey("alias-or") } returns "secret-api-key"
            coEvery {
                apiModelCatalogPort.fetchModelEndpoints(
                    modelId = "meta-llama/llama-3.3-70b-instruct",
                    apiKey = "secret-api-key",
                    baseUrl = null,
                )
            } returns mockEndpoints

            val result =
                useCase(
                    modelId = "meta-llama/llama-3.3-70b-instruct",
                    currentApiKey = "",
                    credentialAlias = "alias-or",
                    baseUrl = null,
                )

            assertEquals(mockEndpoints, result)
            coVerify(exactly = 1) { apiKeyProviderPort.getApiKey("alias-or") }
            coVerify(exactly = 1) {
                apiModelCatalogPort.fetchModelEndpoints(
                    modelId = "meta-llama/llama-3.3-70b-instruct",
                    apiKey = "secret-api-key",
                    baseUrl = null,
                )
            }
        }
}
