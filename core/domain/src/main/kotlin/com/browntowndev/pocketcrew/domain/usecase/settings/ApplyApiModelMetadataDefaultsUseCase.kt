package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.inference.ApiModelParameterSupport
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import javax.inject.Inject

class ApplyApiModelMetadataDefaultsUseCase @Inject constructor() {
    operator fun invoke(
        provider: ApiProvider?,
        modelId: String,
        currentReasoningEffort: ApiReasoningEffort?,
        currentMaxTokens: Int?,
        currentContextWindow: Int?,
        discoveredModel: DiscoveredApiModel?,
    ): ApiModelMetadataDefaults {
        if (provider == null) {
            return ApiModelMetadataDefaults(
                reasoningEffort = currentReasoningEffort,
                maxTokens = currentMaxTokens,
                contextWindow = currentContextWindow,
            )
        }

        val parameterSupport = parameterSupport(provider, modelId)
        val reasoningEffort = when {
            !parameterSupport.supportsReasoningEffort -> null
            currentReasoningEffort != null -> currentReasoningEffort
            else -> parameterSupport.reasoningPolicy.defaultEffort
        }

        val contextWindow = discoveredModel?.contextWindowTokens ?: currentContextWindow
        val maxTokens = discoveredModel
            ?.suggestedDefaultMaxTokens()
            ?.takeIf {
                shouldAdoptSuggestedMaxTokens(
                    currentMaxTokens = currentMaxTokens,
                    currentContextWindow = currentContextWindow,
                    discoveredModel = discoveredModel,
                )
            }
            ?: currentMaxTokens

        return ApiModelMetadataDefaults(
            reasoningEffort = reasoningEffort,
            maxTokens = maxTokens,
            contextWindow = contextWindow,
        )
    }

    fun defaultReasoningEffort(
        provider: ApiProvider,
        modelId: String,
    ): ApiReasoningEffort? = parameterSupport(provider, modelId)
        .takeIf { it.supportsReasoningEffort }
        ?.reasoningPolicy
        ?.defaultEffort

    fun parameterSupport(
        provider: ApiProvider,
        modelId: String,
    ): ApiModelParameterSupport = ApiProviderModelPolicy.parameterSupport(
        provider = provider,
        modelId = modelId,
    )

    private fun shouldAdoptSuggestedMaxTokens(
        currentMaxTokens: Int?,
        currentContextWindow: Int?,
        discoveredModel: DiscoveredApiModel,
    ): Boolean {
        if (currentMaxTokens == null || currentMaxTokens <= 0) {
            return true
        }
        if (currentMaxTokens == 4_096) {
            return true
        }
        if (currentContextWindow != null && currentMaxTokens == currentContextWindow) {
            return true
        }
        if (
            discoveredModel.maxOutputTokens != null &&
            currentMaxTokens == discoveredModel.maxOutputTokens
        ) {
            return true
        }
        if (
            discoveredModel.contextWindowTokens?.let { discoveredContextWindow ->
                currentMaxTokens >= discoveredContextWindow
            } == true
        ) {
            return true
        }
        return false
    }

    private fun DiscoveredApiModel.suggestedDefaultMaxTokens(): Int? {
        val upperBound = listOfNotNull(maxOutputTokens, contextWindowTokens).minOrNull() ?: return null
        return maxOf(1, upperBound / 4)
    }
}
