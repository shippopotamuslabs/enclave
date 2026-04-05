package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputItem

object OpenAiRequestMapper {
    private const val XAI_MULTI_AGENT_MODEL = "grok-4.20-multi-agent"
    private const val SYNTHETIC_API_ERROR_PREFIX = "Error: API Error"

    fun mapToChatCompletionParams(
        modelId: String,
        prompt: String,
        history: List<ChatMessage>,
        options: GenerationOptions
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

        // Add the current prompt
        messages.add(ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder().content(prompt).build()))

        builder.messages(messages)

        options.temperature?.let { builder.temperature(it.toDouble()) }
        options.topP?.let { builder.topP(it.toDouble()) }
        options.maxTokens?.let { builder.maxCompletionTokens(it.toLong()) }
        
        return builder.build()
    }

    fun mapToResponseParams(
        modelId: String,
        prompt: String,
        history: List<ChatMessage>,
        options: GenerationOptions
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

        options.temperature?.let { builder.temperature(it.toDouble()) }
        options.topP?.let { builder.topP(it.toDouble()) }
        if (!isXaiMultiAgentModel(modelId)) {
            options.maxTokens?.let { builder.maxOutputTokens(it.toLong()) }
        }
        
        return builder.build()
    }

    fun isXaiMultiAgentModel(modelId: String): Boolean =
        modelId == XAI_MULTI_AGENT_MODEL

    private fun isSyntheticAssistantError(message: ChatMessage): Boolean =
        message.role == Role.ASSISTANT && message.content.startsWith(SYNTHETIC_API_ERROR_PREFIX)
}
