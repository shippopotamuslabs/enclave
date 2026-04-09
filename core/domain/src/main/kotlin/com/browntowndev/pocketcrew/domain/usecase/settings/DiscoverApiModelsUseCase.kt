package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.usecase.byok.FetchApiProviderModelDetailUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.FetchApiProviderModelsUseCase
import javax.inject.Inject

class DiscoverApiModelsUseCase @Inject constructor(
    private val fetchApiProviderModelsUseCase: FetchApiProviderModelsUseCase,
    private val fetchApiProviderModelDetailUseCase: FetchApiProviderModelDetailUseCase,
) {
    suspend operator fun invoke(request: ApiModelDiscoveryRequest): ApiModelDiscoveryResult {
        val models = fetchApiProviderModelsUseCase(
            provider = request.provider,
            currentApiKey = request.currentApiKey,
            credentialAlias = request.credentialAlias,
            baseUrl = request.baseUrl,
        )
        val selectedModelDetail = if (
            request.provider == ApiProvider.XAI &&
            request.selectedModelId != null &&
            models.none { it.id == request.selectedModelId && it.contextWindowTokens != null }
        ) {
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

        val mergedModels = buildList {
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
            scope = ApiModelDiscoveryScope(
                provider = request.provider,
                baseUrl = request.baseUrl.normalizedBaseUrl(),
                credentialAlias = request.credentialAlias?.takeIf(String::isNotBlank),
            ),
        )
    }

    private fun String?.normalizedBaseUrl(): String = this?.trim().orEmpty()
}
