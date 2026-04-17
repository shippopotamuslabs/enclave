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
            val sanitizedContent = ImagePayloads.stripBase64DataUris(msg.content)
            when (msg.role) {
                Role.SYSTEM -> {
                    messages.add(ChatCompletionMessageParam.ofSystem(ChatCompletionSystemMessageParam.builder().content(sanitizedContent).build()))
                }
                Role.USER -> {
                    messages.add(ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder().content(sanitizedContent).build()))
                }
                Role.ASSISTANT -> {
                    messages.add(ChatCompletionMessageParam.ofAssistant(ChatCompletionAssistantMessageParam.builder().content(sanitizedContent).build()))
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
            val sanitizedContent = ImagePayloads.stripBase64DataUris(msg.content)
            val role = when (msg.role) {
                Role.USER -> ResponseInputItem.Message.Role.of("user")
                Role.ASSISTANT -> ResponseInputItem.Message.Role.of("assistant")
                Role.SYSTEM -> ResponseInputItem.Message.Role.of("system")
            }

            messages.add(
                ResponseInputItem.ofMessage(
                    ResponseInputItem.Message.builder()
                        .role(role)
                        .addInputTextContent(sanitizedContent)
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
                            .putAdditionalProperty("properties", JsonValue.from(schema.properties.toMap()))
                            .putAdditionalProperty("required", JsonValue.from(schema.required))
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
                    .putAdditionalProperty("properties", JsonValue.from(schema.properties.toMap()))
                    .putAdditionalProperty("required", JsonValue.from(schema.required))
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                    .build()
            )
            .strict(true)
            .build()

    private fun kotlinx.serialization.json.JsonObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        forEach { (k, v) ->
            map[k] = v.toPrimitiveOrMap()
        }
        return map
    }

    private fun kotlinx.serialization.json.JsonElement.toPrimitiveOrMap(): Any {
        return when (this) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                if (isString) content
                else {
                    content.toBooleanStrictOrNull()
                        ?: content.toLongOrNull()
                        ?: content.toDoubleOrNull()
                        ?: content
                }
            }
            is kotlinx.serialization.json.JsonObject -> toMap()
            is kotlinx.serialization.json.JsonArray -> map { it.toPrimitiveOrMap() }
            else -> Any()
        }
    }

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
