package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.openai.core.JsonValue
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputImage
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
            .store(false)

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

        messages.add(
            ChatCompletionMessageParam.ofUser(
                buildChatCompletionUserMessage(prompt, options.imageUris)
            )
        )
        builder.messages(messages)

        sanitizedOptions.reasoningEffort?.let { builder.reasoningEffort(ReasoningMapper.toSdkEffort(it)) }
        options.temperature?.let { builder.temperature(it.toDouble()) }
        options.topP?.let { builder.topP(it.toDouble()) }
        sanitizedOptions.maxTokens?.let { builder.maxCompletionTokens(it.toLong()) }
        if (options.toolingEnabled && options.availableTools.isNotEmpty()) {
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
        val sanitizedOptions = sanitizeResponseOptions(modelId, options)
        val builder = ResponseCreateParams.builder()
            .model(modelId)
            .store(false)

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

        messages.add(ResponseInputItem.ofMessage(buildResponseUserMessage(prompt, options.imageUris)))

        builder.inputOfResponse(messages)
        sanitizedOptions.reasoningEffort?.let { builder.reasoning(ReasoningMapper.toSdkReasoning(it)) }
        options.temperature?.let { builder.temperature(it.toDouble()) }
        options.topP?.let { builder.topP(it.toDouble()) }
        sanitizedOptions.maxTokens?.let { builder.maxOutputTokens(it.toLong()) }
        if (options.toolingEnabled && options.availableTools.isNotEmpty()) {
            options.availableTools.forEach { builder.addTool(it.toResponsesTool()) }
            builder.parallelToolCalls(false)
            builder.maxToolCalls(1)
        }

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
            ApiProviderModelPolicy.isXaiGrok4ReasoningFamily(modelId) ||
            ApiProviderModelPolicy.isXaiGrok4NonReasoningFamily(modelId)
        ) {
            return null
        }
        return reasoningEffort
    }

    private fun isSyntheticAssistantError(message: ChatMessage): Boolean =
        message.role == Role.ASSISTANT && message.content.startsWith(SYNTHETIC_API_ERROR_PREFIX)

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
                            .putAdditionalProperty("required", JsonValue.from(requiredArguments()))
                            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
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
                    .putAdditionalProperty("required", JsonValue.from(requiredArguments()))
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                    .build()
            )
            .strict(true)
            .build()

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
                ImagePayloads.validate(payload)
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
            ImagePayloads.validate(payload)
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


private fun ToolDefinition.toolProperties(): Map<String, Any> =
        when (this) {
            ToolDefinition.TAVILY_WEB_SEARCH -> mapOf(
                "query" to mapOf(
                    "type" to "string"
                )
            )
            ToolDefinition.TAVILY_EXTRACT -> mapOf(
                "urls" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string")
                ),
                "extract_depth" to mapOf(
                    "type" to "string",
                    "enum" to listOf("basic", "advanced")
                ),
                "format" to mapOf(
                    "type" to "string",
                    "enum" to listOf("markdown", "text")
                )
            )
            ToolDefinition.ATTACHED_IMAGE_INSPECT -> mapOf(
                "question" to mapOf(
                    "type" to "string"
                )
            )
            ToolDefinition.SEARCH_CHAT_HISTORY -> mapOf(
                "query" to mapOf(
                    "type" to "string"
                )
            )
            ToolDefinition.SEARCH_CHAT -> mapOf(
                "chat_id" to mapOf(
                    "type" to "string",
                    "description" to "The ID of the chat to search."
                ),
                "query" to mapOf(
                    "type" to "string"
                )
            )
            else -> error("Unsupported tool: $name")
        }

private fun ToolDefinition.requiredArguments(): List<String> =
        when (this) {
            ToolDefinition.TAVILY_WEB_SEARCH -> listOf("query")
            ToolDefinition.TAVILY_EXTRACT -> listOf("urls", "extract_depth", "format")
            ToolDefinition.ATTACHED_IMAGE_INSPECT -> listOf("question")
            ToolDefinition.SEARCH_CHAT_HISTORY -> listOf("query")
            ToolDefinition.SEARCH_CHAT -> listOf("chat_id", "query")
            else -> error("Unsupported tool: $name")
        }
