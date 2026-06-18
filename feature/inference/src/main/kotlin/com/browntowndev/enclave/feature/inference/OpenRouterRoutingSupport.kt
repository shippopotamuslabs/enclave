package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.enclave.domain.model.config.OpenRouterProviderSort
import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import com.openai.core.JsonValue
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.ResponseCreateParams

/**
 * Applies OpenRouter provider routing preferences to live OpenAI-SDK request builders.
 *
 * Uses [ChatCompletionCreateParams.Builder.putAdditionalBodyProperty] (merge) rather than
 * [ChatCompletionCreateParams.Builder.additionalBodyProperties] (replaces entire body map).
 */
internal object OpenRouterRoutingSupport {
    fun applyToChatCompletionBuilder(
        builder: ChatCompletionCreateParams.Builder,
        routing: OpenRouterRoutingConfiguration,
    ) {
        builder.putAdditionalBodyProperty("provider", providerRoutingJsonValue(routing))
    }

    fun applyToResponseBuilder(
        builder: ResponseCreateParams.Builder,
        routing: OpenRouterRoutingConfiguration,
    ) {
        builder.putAdditionalBodyProperty("provider", providerRoutingJsonValue(routing))
    }

    private fun providerRoutingJsonValue(routing: OpenRouterRoutingConfiguration): JsonValue {
        val hasPreferred = !routing.preferredProvider.isNullOrBlank()
        val allowFallbacks = routing.allowFallbacks

        val sort = if (hasPreferred && !allowFallbacks) {
            OpenRouterProviderSort.THROUGHPUT.wireValue
        } else {
            routing.providerSort.wireValue
        }
        val requireParameters = if (hasPreferred && !allowFallbacks) {
            false
        } else {
            routing.requireParameters
        }
        val dataCollection = if (hasPreferred && !allowFallbacks) {
            OpenRouterDataCollectionPolicy.DENY.wireValue
        } else {
            routing.dataCollectionPolicy.wireValue
        }
        val zdr = if (hasPreferred && !allowFallbacks) false else routing.zeroDataRetention

        val providerMap =
            buildMap {
                put("sort", sort)
                put("allow_fallbacks", allowFallbacks)
                put("require_parameters", requireParameters)
                put("data_collection", dataCollection)
                put("zdr", zdr)
                routing.preferredProvider?.takeIf { it.isNotBlank() }?.let { preferred ->
                    put("order", listOf(preferred))
                }
            }
        return JsonValue.from(providerMap)
    }
}
