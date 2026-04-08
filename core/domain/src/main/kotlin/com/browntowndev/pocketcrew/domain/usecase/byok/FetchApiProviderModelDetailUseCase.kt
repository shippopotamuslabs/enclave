package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelCatalogPort
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import javax.inject.Inject

interface FetchApiProviderModelDetailUseCase {
    suspend operator fun invoke(
        provider: ApiProvider,
        modelId: String,
        currentApiKey: String,
        credentialAlias: String? = null,
        baseUrl: String? = null
    ): DiscoveredApiModel?
}

class FetchApiProviderModelDetailUseCaseImpl @Inject constructor(
    private val apiModelCatalog: ApiModelCatalogPort,
    private val apiKeyProvider: ApiKeyProviderPort,
) : FetchApiProviderModelDetailUseCase {
    override suspend fun invoke(
        provider: ApiProvider,
        modelId: String,
        currentApiKey: String,
        credentialAlias: String?,
        baseUrl: String?
    ): DiscoveredApiModel? {
        val resolvedApiKey = currentApiKey.ifBlank {
            credentialAlias
                ?.takeIf { it.isNotBlank() }
                ?.let(apiKeyProvider::getApiKey)
                .orEmpty()
        }
        require(resolvedApiKey.isNotBlank()) {
            "An API key is required to fetch provider model details."
        }
        return apiModelCatalog.fetchModel(
            provider = provider,
            apiKey = resolvedApiKey,
            modelId = modelId,
            baseUrl = baseUrl,
        )
    }
}
