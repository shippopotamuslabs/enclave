package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelCatalogPort
import javax.inject.Inject

interface FetchApiProviderModelsUseCase {
    suspend operator fun invoke(
        provider: ApiProvider,
        currentApiKey: String,
        credentialAlias: String? = null,
        baseUrl: String? = null
    ): List<String>
}

class FetchApiProviderModelsUseCaseImpl @Inject constructor(
    private val apiModelCatalog: ApiModelCatalogPort,
    private val apiKeyProvider: ApiKeyProviderPort,
) : FetchApiProviderModelsUseCase {
    override suspend fun invoke(
        provider: ApiProvider,
        currentApiKey: String,
        credentialAlias: String?,
        baseUrl: String?
    ): List<String> {
        val resolvedApiKey = currentApiKey.ifBlank {
            credentialAlias
                ?.takeIf { it.isNotBlank() }
                ?.let(apiKeyProvider::getApiKey)
                .orEmpty()
        }
        require(resolvedApiKey.isNotBlank()) {
            "An API key is required to fetch provider models."
        }
        return apiModelCatalog.fetchModels(provider, resolvedApiKey, baseUrl)
    }
}
