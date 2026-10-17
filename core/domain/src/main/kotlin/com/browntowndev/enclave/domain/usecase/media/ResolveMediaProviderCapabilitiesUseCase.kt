package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.media.ModelGenerationInputSchema
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.model.media.toProviderCapabilities
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import javax.inject.Inject

open class ResolveMediaProviderCapabilitiesUseCase
    @Inject
    constructor(
        private val getProviderCapabilitiesUseCase: GetProviderCapabilitiesUseCase,
        private val apiModelCatalog: ApiModelCatalogPort,
        private val apiKeyProvider: ApiKeyProviderPort,
    ) {
        open suspend operator fun invoke(
            mediaProvider: MediaProviderAsset?,
            mediaCapability: MediaCapability,
        ): ProviderCapabilities {
            val fallback =
                getProviderCapabilitiesUseCase(
                    mediaProvider?.provider?.name?.lowercase(),
                )
            val schema = loadFalInputSchema(mediaProvider)
            return schema?.toProviderCapabilities(mediaCapability, fallback) ?: fallback
        }

        private suspend fun loadFalInputSchema(mediaProvider: MediaProviderAsset?): ModelGenerationInputSchema? {
            val providerAsset =
                mediaProvider?.takeIf { it.provider == ApiProvider.FAL } ?: return null
            val modelId =
                providerAsset.modelName
                    ?.trim()
                    .orEmpty()
                    .takeIf { it.isNotBlank() } ?: return null
            val apiKey =
                apiKeyProvider
                    .getApiKey(providerAsset.credentialAlias)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return null
            return apiModelCatalog
                .fetchModel(
                    provider = ApiProvider.FAL,
                    apiKey = apiKey,
                    modelId = modelId,
                    baseUrl = providerAsset.baseUrl,
                )?.inputSchema
        }
    }
