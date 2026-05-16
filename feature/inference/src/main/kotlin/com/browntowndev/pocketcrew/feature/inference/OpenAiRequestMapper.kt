package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.util.ContextWindowPlanner
import com.browntowndev.pocketcrew.domain.util.JTokkitTokenCounter
import com.browntowndev.pocketcrew.domain.util.NativeToolResultFormatter
import com.browntowndev.pocketcrew.feature.inference.openai.StreamedOpenAiResponse
import com.openai.core.JsonValue
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseFunctionToolCall
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
        val provider = options.apiProvider
        val optModelId = options.modelId
        val parameterSupport = if (provider != null && optModelId != null) {
            ApiProviderModelPolicy.parameterSupport(provider, optModelId)
        } else null

        val builder = ChatCompletionCreateParams.builder()
            .model(optModelId ?: modelId)

        val messages = mutableListOf<ChatCompletionMessageParam>()
        val systemMessages = mutableListOf<String>()

        history.filterNot(::isSyntheticAssistantError).forEach { msg ->
            if (msg.role == Role.SYSTEM) {
                systemMessages += ImagePayloads.stripBase64DataUris(msg.content)
            } else {
                messages.add(toContent(msg))
            }
        }

        options.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            if (!systemMessages.contains(it)) {
                systemMessages.add(0, it)
            }
        }

        val finalSystemPrompt = systemMessages.joinToString("\n\n").takeIf { it.isNotBlank() }
        if (finalSystemPrompt != null) {
            messages.add(
                0,
                ChatCompletionMessageParam.ofSystem(
                    ChatCompletionSystemMessageParam.builder()
                        .content(finalSystemPrompt)
                        .build()
                )
            )
        }

        messages.add(
            ChatCompletionMessageParam.ofUser(
                buildChatCompletionUserMessage(prompt, options.imageUris)
            )
        )

        options.reasoningEffort?.let { builder.reasoningEffort(ReasoningMapper.toSdkEffort(it)) }

        if (!options.temperatureDisabled && parameterSupport?.supportsTemperature != false) {
            options.temperature?.let { builder.temperature(it.toDouble()) }
        }
        if (!options.topPDisabled && parameterSupport?.supportsTopP != false) {
            options.topP?.let { builder.topP(it.toDouble()) }
        }
        if (!options.maxTokensDisabled && parameterSupport?.supportsMaxTokens != false) {
            options.maxTokens?.let { builder.maxCompletionTokens(it.toLong()) }
        }

        if (options.toolingEnabled) {
            options.availableTools.forEach { builder.addTool(it.toChatCompletionTool()) }
            builder.parallelToolCalls(false)
        }

        builder.messages(messages)
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
                systemMessages += ImagePayloads.stripBase64DataUris(msg.content)
                return@forEach
            }

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
        }

        return builder.build()
    }

    fun mapToFollowUpResponseParams(
        currentParams: ResponseCreateParams,
        prompt: String,
        options: GenerationOptions,
        history: List<ChatMessage>,
        initialResponse: StreamedOpenAiResponse,
        results: List<Pair<ToolCallRequest, String>>,
        appendStopToolsWarning: Boolean,
    ): ResponseCreateParams {
        // Truncate large tool results when context window is known
        val truncationAvailableTokens = options.contextWindow?.let { cw ->
            val budget = ContextWindowPlanner.budgetFor(
                contextWindowTokens = cw,
                options = options,
                modelId = initialResponse.responseId ?: "",
                tokenCounter = JTokkitTokenCounter,
            )
            val usedTokens = ContextWindowPlanner.estimatePromptTokens(
                history = history,
                systemPrompt = null,
                currentPrompt = prompt,
                toolResultPayloads = results.map { it.second },
                modelId = initialResponse.responseId ?: "",
                tokenCounter = JTokkitTokenCounter,
            ) ?: 0
            (budget.usablePromptTokens - usedTokens).coerceAtLeast(0)
        }
        val builder = currentParams.toBuilder()
        val inputItems = if (currentParams.input().isPresent && currentParams.input().get().isResponse()) {
            currentParams.input().get().asResponse().toMutableList()
        } else {
            mutableListOf()
        }

        initialResponse.assistantMessageText
            .takeIf { it.isNotBlank() }
            ?.let { assistantText ->
                inputItems += ResponseInputItem.ofMessage(
                    ResponseInputItem.Message.builder()
                        .role(ResponseInputItem.Message.Role.of("assistant"))
                        .addInputTextContent(assistantText)
                        .build()
                )
            }

        // Map results back to their corresponding function calls in the initial response
        results.forEach { (toolCall, resultJson) ->
            val truncatedResult = if (truncationAvailableTokens != null && truncationAvailableTokens > 0) {
                NativeToolResultFormatter.truncateForApiContext(
                    resultJson = resultJson,
                    availableTokens = maxOf(truncationAvailableTokens / results.size.coerceAtLeast(1), 100),
                    tokenCounter = JTokkitTokenCounter,
                    modelId = initialResponse.responseId ?: "",
                )
            } else {
                resultJson
            }
            val index = initialResponse.functionCalls.indexOf(toolCall)

            val callId = if (index != -1) {
                initialResponse.providerToolCallIds.getOrElse(index) {
                    throw IllegalStateException("Missing function call id at index $index for follow-up")
                }
            } else {
                "fallback_${java.util.UUID.randomUUID().toString().replace("-", "")}"
            }

            val functionItemId = if (index != -1) {
                initialResponse.providerToolItemIds.getOrElse(index) {
                    "fc_${callId}"
                }
            } else {
                "fc_${callId}"
            }

            inputItems += ResponseInputItem.ofFunctionCall(
                ResponseFunctionToolCall.builder()
                    .id(functionItemId)
                    .callId(callId)
                    .name(toolCall.toolName)
                    .arguments(toolCall.argumentsJson)
                    .build()
            )
            inputItems += ResponseInputItem.ofFunctionCallOutput(
                ResponseInputItem.FunctionCallOutput.builder()
                    .id("fco_${callId}")
                    .callId(callId)
                    .output(truncatedResult)
                    .build()
            )
        }

        if (appendStopToolsWarning) {
            inputItems += ResponseInputItem.ofMessage(
                ResponseInputItem.Message.builder()
                    .role(ResponseInputItem.Message.Role.of("user"))
                    .addInputTextContent(ContextWindowPlanner.STOP_TOOLS_WARNING)
                    .build()
            )
        }

        builder.inputOfResponse(inputItems)
        return builder.build()
    }

    private fun toContent(message: ChatMessage): ChatCompletionMessageParam {
        return when (message.role) {
            Role.USER -> ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                    .content(ImagePayloads.stripBase64DataUris(message.content))
                    .build()
            )
            Role.ASSISTANT -> ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder()
                    .content(ImagePayloads.stripBase64DataUris(message.content))
                    .build()
            )
            Role.SYSTEM -> ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder()
                    .content(ImagePayloads.stripBase64DataUris(message.content))
                    .build()
            )
        }
    }

    private fun ToolDefinition.toResponsesTool(): FunctionTool {
        val paramsBuilder = FunctionTool.Parameters.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(schema.properties.toJsonMap()))
            .putAdditionalProperty("required", JsonValue.from(schema.required))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
        schema.fullSchemaOverride?.get("definitions")?.let { defs ->
            if (defs is kotlinx.serialization.json.JsonObject) {
                paramsBuilder.putAdditionalProperty("definitions", JsonValue.from(defs.toJsonMap()))
            }
        }
        schema.fullSchemaOverride?.get("\$defs")?.let { defs ->
            if (defs is kotlinx.serialization.json.JsonObject) {
                paramsBuilder.putAdditionalProperty("\$defs", JsonValue.from(defs.toJsonMap()))
            }
        }
        return FunctionTool.builder()
            .name(name)
            .description(description)
            .parameters(paramsBuilder.build())
            .strict(true)
            .build()
    }

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

internal fun ToolDefinition.toChatCompletionTool(): ChatCompletionFunctionTool {
    val paramsBuilder = FunctionParameters.builder()
        .putAdditionalProperty("type", JsonValue.from("object"))
        .putAdditionalProperty("properties", JsonValue.from(schema.properties.toJsonMap()))
        .putAdditionalProperty("required", JsonValue.from(schema.required))
        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
    schema.fullSchemaOverride?.get("definitions")?.let { defs ->
        if (defs is kotlinx.serialization.json.JsonObject) {
            paramsBuilder.putAdditionalProperty("definitions", JsonValue.from(defs.toJsonMap()))
        }
    }
    schema.fullSchemaOverride?.get("\$defs")?.let { defs ->
        if (defs is kotlinx.serialization.json.JsonObject) {
            paramsBuilder.putAdditionalProperty("\$defs", JsonValue.from(defs.toJsonMap()))
        }
    }
    return ChatCompletionFunctionTool.builder()
        .function(
            FunctionDefinition.builder()
                .name(name)
                .description(description)
                .parameters(paramsBuilder.build())
                .strict(true)
                .build()
        )
        .build()
}

private fun kotlinx.serialization.json.JsonObject.toJsonMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    forEach { (k, v) -> map[k] = v.toJsonPrimitiveOrMap() }
    return map
}

private fun kotlinx.serialization.json.JsonElement.toJsonPrimitiveOrMap(): Any =
    when (this) {
        is kotlinx.serialization.json.JsonPrimitive -> {
            if (isString) content
            else content.toBooleanStrictOrNull() ?: content.toLongOrNull() ?: content.toDoubleOrNull() ?: content
        }
        is kotlinx.serialization.json.JsonObject -> toJsonMap()
        is kotlinx.serialization.json.JsonArray -> map { it.toJsonPrimitiveOrMap() }
        else -> Any()
    }
