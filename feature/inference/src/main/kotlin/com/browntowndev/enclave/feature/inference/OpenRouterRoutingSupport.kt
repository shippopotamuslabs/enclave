package com.browntowndev.enclave.feature.inference

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

    private fun providerRoutingJsonValue(routing: OpenRouterRoutingConfiguration): JsonValue =
        JsonValue.from(
            mapOf(
                "sort" to routing.providerSort.wireValue,
                "allow_fallbacks" to routing.allowFallbacks,
                "require_parameters" to routing.requireParameters,
                "data_collection" to routing.dataCollectionPolicy.wireValue,
                "zdr" to routing.zeroDataRetention,
            ),
        )
}
