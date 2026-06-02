package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.usecase.byok.FetchApiProviderModelDetailUseCase
import com.browntowndev.enclave.domain.usecase.byok.FetchApiProviderModelsUseCase
import javax.inject.Inject

class DiscoverApiModelsUseCase
    @Inject
    constructor(
        private val fetchApiProviderModelsUseCase: FetchApiProviderModelsUseCase,
        private val fetchApiProviderModelDetailUseCase: FetchApiProviderModelDetailUseCase,
    ) {
        suspend operator fun invoke(request: ApiModelDiscoveryRequest): ApiModelDiscoveryResult {
            val catalog =
                fetchApiProviderModelsUseCase(
                    provider = request.provider,
                    currentApiKey = request.currentApiKey,
                    credentialAlias = request.credentialAlias,
                    baseUrl = request.baseUrl,
                )
            val models = catalog.models
            val needsDetailFetch =
                request.provider == ApiProvider.XAI &&
                    request.selectedModelId != null &&
                    models.none { it.id == request.selectedModelId && it.contextWindowTokens != null }
            val selectedModelDetail =
                if (needsDetailFetch) {
                    fetchApiProviderModelDetailUseCase(
                        provider = request.provider,
                        modelId = request.selectedModelId,
                        currentApiKey = request.currentApiKey,
                        credentialAlias = request.credentialAlias,
                        baseUrl = request.baseUrl,
                    )
                } else {
                    null
                }

            val mergedModels =
                buildList {
                    selectedModelDetail?.let(::add)
                    if (
                        !request.selectedModelId.isNullOrBlank() &&
                        models.none { it.id == request.selectedModelId } &&
                        selectedModelDetail?.id != request.selectedModelId
                    ) {
                        add(DiscoveredApiModel(id = request.selectedModelId))
                    }
                    addAll(models)
                }.distinctBy(DiscoveredApiModel::id)

            return ApiModelDiscoveryResult(
                models = mergedModels,
                scope =
                    ApiModelDiscoveryScope(
                        provider = request.provider,
                        baseUrl = request.baseUrl.normalizedBaseUrl(),
                        credentialAlias = request.credentialAlias?.takeIf(String::isNotBlank),
                    ),
                isTruncated = catalog.isTruncated,
            )
        }

        private fun String?.normalizedBaseUrl(): String = this?.trim().orEmpty()
    }
