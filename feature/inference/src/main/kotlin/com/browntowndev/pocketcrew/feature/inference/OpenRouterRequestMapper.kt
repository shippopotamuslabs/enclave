package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.openai.core.JsonValue
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputItem

object OpenRouterRequestMapper {
    private const val SYNTHETIC_API_ERROR_PREFIX = "Error: API Error"

    fun mapToChatCompletionParams(
        modelId: String,
        prompt: String,
        history: List<ChatMessage>,
        options: GenerationOptions,
        routing: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration()
    ): ChatCompletionCreateParams {
        val builder = ChatCompletionCreateParams.builder()
            .model(modelId)

        val messages = mutableListOf<ChatCompletionMessageParam>()

        history.filterNot(::isSyntheticAssistantError).forEach { msg ->
            when (msg.role) {
                Role.SYSTEM -> {
                    messages.add(ChatCompletionMessageParam.ofSystem(ChatCompletionSystemMessageParam.builder().content(msg.content).build()))
                }
                Role.USER -> {
                    messages.add(ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder().content(msg.content).build()))
                }
                Role.ASSISTANT -> {
                    messages.add(ChatCompletionMessageParam.ofAssistant(ChatCompletionAssistantMessageParam.builder().content(msg.content).build()))
                }
            }
        }

        messages.add(ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder().content(prompt).build()))
        builder.messages(messages)

        options.reasoningEffort?.let { builder.reasoningEffort(ReasoningMapper.toSdkEffort(it)) }
        options.temperature?.let { builder.temperature(it.toDouble()) }
        options.topP?.let { builder.topP(it.toDouble()) }
        options.safeOpenRouterMaxTokens()?.let { builder.maxCompletionTokens(it.toLong()) }

        applyOpenRouterRoutingDefaults(builder, routing)
        return builder.build()
    }

    fun mapToResponseParams(
        modelId: String,
        prompt: String,
        history: List<ChatMessage>,
        options: GenerationOptions,
        routing: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration()
    ): ResponseCreateParams {
        val builder = ResponseCreateParams.builder()
            .model(modelId)

        val messages = mutableListOf<ResponseInputItem>()
        val systemMessages = mutableListOf<String>()

        history.filterNot(::isSyntheticAssistantError).forEach { msg ->
            if (msg.role == Role.SYSTEM) {
                systemMessages += msg.content
                return@forEach
            }

            val role = when (msg.role) {
                Role.USER -> ResponseInputItem.Message.Role.of("user")
                Role.ASSISTANT -> ResponseInputItem.Message.Role.of("assistant")
                Role.SYSTEM -> ResponseInputItem.Message.Role.of("system")
            }
            messages.add(
                ResponseInputItem.ofMessage(
                    ResponseInputItem.Message.builder()
                        .role(role)
                        .addInputTextContent(msg.content)
                        .build()
                )
            )
        }

        messages.add(
            ResponseInputItem.ofMessage(
                ResponseInputItem.Message.builder()
                    .role(ResponseInputItem.Message.Role.of("user"))
                    .addInputTextContent(prompt)
                    .build()
            )
        )

        builder.inputOfResponse(messages)
        systemMessages
            .joinToString(separator = "\n\n")
            .takeIf { it.isNotBlank() }
            ?.let { builder.instructions(it) }

        options.reasoningEffort?.let { builder.reasoning(ReasoningMapper.toSdkReasoning(it)) }
        options.temperature?.let { builder.temperature(it.toDouble()) }
        options.topP?.let { builder.topP(it.toDouble()) }
        options.safeOpenRouterMaxTokens()?.let { builder.maxOutputTokens(it.toLong()) }

        applyOpenRouterRoutingDefaults(builder, routing)
        return builder.build()
    }

    private fun applyOpenRouterRoutingDefaults(
        builder: ChatCompletionCreateParams.Builder,
        routing: OpenRouterRoutingConfiguration
    ) {
        builder.additionalBodyProperties(
            mapOf(
                "provider" to JsonValue.from(
                    mapOf(
                        "sort" to routing.providerSort.wireValue,
                        "allow_fallbacks" to routing.allowFallbacks,
                        "require_parameters" to routing.requireParameters,
                        "data_collection" to routing.dataCollectionPolicy.wireValue,
                        "zdr" to routing.zeroDataRetention,
                    )
                )
            )
        )
    }

    private fun applyOpenRouterRoutingDefaults(
        builder: ResponseCreateParams.Builder,
        routing: OpenRouterRoutingConfiguration
    ) {
        builder.additionalBodyProperties(
            mapOf(
                "provider" to JsonValue.from(
                    mapOf(
                        "sort" to routing.providerSort.wireValue,
                        "allow_fallbacks" to routing.allowFallbacks,
                        "require_parameters" to routing.requireParameters,
                        "data_collection" to routing.dataCollectionPolicy.wireValue,
                        "zdr" to routing.zeroDataRetention,
                    )
                )
            )
        )
    }

    private fun isSyntheticAssistantError(message: ChatMessage): Boolean =
        message.role == Role.ASSISTANT && message.content.startsWith(SYNTHETIC_API_ERROR_PREFIX)

    private fun GenerationOptions.safeOpenRouterMaxTokens(): Int? {
        val configuredMaxTokens = maxTokens ?: return null
        val configuredContextWindow = contextWindow
        if (configuredContextWindow != null && configuredMaxTokens >= configuredContextWindow) {
            return null
        }
        return configuredMaxTokens
    }
}
