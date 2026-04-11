package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.openai.core.JsonValue
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputImage
import com.openai.models.responses.ResponseInputItem

object OpenAiRequestMapper {
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
        messages.add(
            ChatCompletionMessageParam.ofUser(
                buildChatCompletionUserMessage(prompt, options.imageUris)
            )
        )

        builder.messages(messages)

        options.reasoningEffort?.let { builder.reasoningEffort(ReasoningMapper.toSdkEffort(it)) }
        options.temperature?.let { builder.temperature(it.toDouble()) }
        options.topP?.let { builder.topP(it.toDouble()) }
        options.maxTokens?.let { builder.maxCompletionTokens(it.toLong()) }
        if (options.toolingEnabled) {
            options.availableTools.forEach { builder.addTool(it.toChatCompletionTool()) }
            builder.parallelToolCalls(false)
        }
        
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

        messages.add(ResponseInputItem.ofMessage(buildResponseUserMessage(prompt, options.imageUris)))

        builder.inputOfResponse(messages)
        systemMessages
            .joinToString(separator = "\n\n")
            .takeIf { it.isNotBlank() }
            ?.let { builder.instructions(it) }

        options.reasoningEffort?.let { builder.reasoning(ReasoningMapper.toSdkReasoning(it)) }
        options.temperature?.let { builder.temperature(it.toDouble()) }
        options.topP?.let { builder.topP(it.toDouble()) }
        options.maxTokens?.let { builder.maxOutputTokens(it.toLong()) }
        if (options.toolingEnabled) {
            options.availableTools.forEach { builder.addTool(it.toResponsesTool()) }
            builder.parallelToolCalls(false)
            builder.maxToolCalls(1)
        }
        
        return builder.build()
    }

    private fun ToolDefinition.toChatCompletionTool(): ChatCompletionFunctionTool =
        ChatCompletionFunctionTool.builder()
            .function(
                FunctionDefinition.builder()
                    .name(name)
                    .description(description)
                    .parameters(
                        FunctionParameters.builder()
                            .putAdditionalProperty("type", JsonValue.from("object"))
                            .putAdditionalProperty("properties", JsonValue.from(toolProperties()))
                            .putAdditionalProperty("required", JsonValue.from(listOf("query")))
                            .build()
                    )
                    .strict(true)
                    .build()
            )
            .build()

    private fun ToolDefinition.toResponsesTool(): FunctionTool =
        FunctionTool.builder()
            .name(name)
            .description(description)
            .parameters(
                FunctionTool.Parameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty("properties", JsonValue.from(toolProperties()))
                    .putAdditionalProperty("required", JsonValue.from(listOf("query")))
                    .build()
            )
            .strict(true)
            .build()

    private fun toolProperties(): Map<String, Any> =
        mapOf(
            "query" to mapOf(
                "type" to "string"
            )
        )

    private fun isSyntheticAssistantError(message: ChatMessage): Boolean =
        message.role == Role.ASSISTANT && message.content.startsWith(SYNTHETIC_API_ERROR_PREFIX)

    private fun buildChatCompletionUserMessage(
        prompt: String,
        imageUris: List<String>,
    ): ChatCompletionUserMessageParam {
        if (imageUris.isEmpty()) {
            return ChatCompletionUserMessageParam.builder()
                .content(prompt)
                .build()
        }

        val parts = buildList {
            add(
                ChatCompletionContentPart.ofText(
                    ChatCompletionContentPartText.builder()
                        .text(prompt)
                        .build()
                )
            )
            ImagePayloads.fromUris(imageUris).forEach { payload ->
                add(
                    ChatCompletionContentPart.ofImageUrl(
                        ChatCompletionContentPartImage.builder()
                            .imageUrl(
                                ChatCompletionContentPartImage.ImageUrl.builder()
                                    .url(payload.dataUrl)
                                    .build()
                            )
                            .build()
                    )
                )
            }
        }

        return ChatCompletionUserMessageParam.builder()
            .contentOfArrayOfContentParts(parts)
            .build()
    }

    private fun buildResponseUserMessage(
        prompt: String,
        imageUris: List<String>,
    ): ResponseInputItem.Message {
        val builder = ResponseInputItem.Message.builder()
            .role(ResponseInputItem.Message.Role.of("user"))
            .addInputTextContent(prompt)

        ImagePayloads.fromUris(imageUris).forEach { payload ->
            builder.addContent(
                ResponseInputImage.builder()
                    .detail(ResponseInputImage.Detail.AUTO)
                    .imageUrl(payload.dataUrl)
                    .build()
            )
        }

        return builder.build()
    }
}
