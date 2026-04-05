package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputItem

object XaiRequestMapper {
    private const val SYNTHETIC_API_ERROR_PREFIX = "Error: API Error"

    private data class SanitizedXaiRequestOptions(
        val reasoningEffort: ApiReasoningEffort?,
        val maxTokens: Int?,
    )

    fun mapToChatCompletionParams(
        modelId: String,
        prompt: String,
        history: List<ChatMessage>,
        options: GenerationOptions
    ): ChatCompletionCreateParams {
        val sanitizedOptions = sanitizeChatOptions(modelId, options)
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

        sanitizedOptions.reasoningEffort?.let { builder.reasoningEffort(ReasoningMapper.toSdkEffort(it)) }
        options.temperature?.let { builder.temperature(it.toDouble()) }
        options.topP?.let { builder.topP(it.toDouble()) }
        sanitizedOptions.maxTokens?.let { builder.maxCompletionTokens(it.toLong()) }

        return builder.build()
    }

    fun mapToResponseParams(
        modelId: String,
        prompt: String,
        history: List<ChatMessage>,
        options: GenerationOptions
    ): ResponseCreateParams {
        val sanitizedOptions = sanitizeResponseOptions(modelId, options)
        val builder = ResponseCreateParams.builder()
            .model(modelId)

        val messages = mutableListOf<ResponseInputItem>()

        history.filterNot(::isSyntheticAssistantError).forEach { msg ->
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
        sanitizedOptions.reasoningEffort?.let { builder.reasoning(ReasoningMapper.toSdkReasoning(it)) }
        options.temperature?.let { builder.temperature(it.toDouble()) }
        options.topP?.let { builder.topP(it.toDouble()) }
        sanitizedOptions.maxTokens?.let { builder.maxOutputTokens(it.toLong()) }

        return builder.build()
    }

    fun isMultiAgentModel(modelId: String): Boolean =
        ApiProviderModelPolicy.isXaiMultiAgentModel(modelId)

    fun shouldAllowChatCompletionsFallback(modelId: String): Boolean =
        !ApiProviderModelPolicy.isXaiMultiAgentModel(modelId)

    fun isChatReasoningContentModel(modelId: String): Boolean =
        ApiProviderModelPolicy.isXaiChatReasoningContentModel(modelId)

    private fun sanitizeResponseOptions(
        modelId: String,
        options: GenerationOptions
    ): SanitizedXaiRequestOptions =
        SanitizedXaiRequestOptions(
            reasoningEffort = sanitizeReasoningEffort(modelId, options.reasoningEffort),
            maxTokens = if (ApiProviderModelPolicy.isXaiMultiAgentModel(modelId)) null else options.maxTokens,
        )

    private fun sanitizeChatOptions(
        modelId: String,
        options: GenerationOptions
    ): SanitizedXaiRequestOptions =
        SanitizedXaiRequestOptions(
            reasoningEffort = sanitizeReasoningEffort(modelId, options.reasoningEffort),
            maxTokens = options.maxTokens,
        )

    private fun sanitizeReasoningEffort(
        modelId: String,
        reasoningEffort: ApiReasoningEffort?
    ): ApiReasoningEffort? {
        if (reasoningEffort == null) {
            return null
        }
        if (ApiProviderModelPolicy.isXaiGrok3MiniModel(modelId)) {
            return reasoningEffort
        }
        if (
            ApiProviderModelPolicy.isXaiGrok3Model(modelId) ||
            ApiProviderModelPolicy.isXaiGrok4ReasoningFamily(modelId)
        ) {
            return null
        }
        return reasoningEffort
    }

    private fun isSyntheticAssistantError(message: ChatMessage): Boolean =
        message.role == Role.ASSISTANT && message.content.startsWith(SYNTHETIC_API_ERROR_PREFIX)
}
