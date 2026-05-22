package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import javax.inject.Inject

interface FetchApiProviderModelsUseCase {
    suspend operator fun invoke(
        provider: ApiProvider,
        currentApiKey: String,
        credentialAlias: String? = null,
        baseUrl: String? = null,
    ): List<DiscoveredApiModel>
}

class FetchApiProviderModelsUseCaseImpl
    @Inject
    constructor(
        private val apiModelCatalog: ApiModelCatalogPort,
        private val apiKeyProvider: ApiKeyProviderPort,
    ) : FetchApiProviderModelsUseCase {
        override suspend fun invoke(
            provider: ApiProvider,
            currentApiKey: String,
            credentialAlias: String?,
            baseUrl: String?,
        ): List<DiscoveredApiModel> {
            val resolvedApiKey =
                currentApiKey.ifBlank {
                    credentialAlias
                        ?.takeIf { it.isNotBlank() }
                        ?.let { apiKeyProvider.getApiKey(it) }
                        .orEmpty()
                }
            require(resolvedApiKey.isNotBlank()) {
                "An API key is required to fetch provider models."
            }
            return apiModelCatalog.fetchModels(provider, resolvedApiKey, baseUrl)
        }
    }
