package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.config.OpenRouterModelEndpoint
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import javax.inject.Inject

interface FetchOpenRouterModelEndpointsUseCase {
    open suspend operator fun invoke(
        modelId: String,
        currentApiKey: String,
        credentialAlias: String? = null,
        baseUrl: String? = null,
    ): List<OpenRouterModelEndpoint>
}

class FetchOpenRouterModelEndpointsUseCaseImpl
    @Inject
    constructor(
        private val apiKeyProviderPort: ApiKeyProviderPort,
        private val apiModelCatalogPort: ApiModelCatalogPort,
    ) : FetchOpenRouterModelEndpointsUseCase {
        override suspend fun invoke(
            modelId: String,
            currentApiKey: String,
            credentialAlias: String?,
            baseUrl: String?,
        ): List<OpenRouterModelEndpoint> {
            val resolvedApiKey =
                currentApiKey.ifBlank {
                    credentialAlias
                        ?.takeIf { it.isNotBlank() }
                        ?.let { apiKeyProviderPort.getApiKey(it) }
                        .orEmpty()
                }
            require(resolvedApiKey.isNotBlank()) {
                "An API key is required to fetch OpenRouter model endpoints."
            }
            return apiModelCatalogPort.fetchModelEndpoints(
                modelId = modelId,
                apiKey = resolvedApiKey,
                baseUrl = baseUrl,
            )
        }
    }
