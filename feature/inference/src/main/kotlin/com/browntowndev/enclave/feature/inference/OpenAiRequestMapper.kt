package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.openai.core.JsonValue
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputImage
import com.browntowndev.enclave.feature.inference.JsonSchemaWireSupport.toWireMap
import com.openai.models.responses.ResponseInputItem
import kotlinx.serialization.json.JsonObject

object OpenAiRequestMapper {

    internal fun mapSessionToResponseParams(
        modelId: String,
        wire: ResponseWireSegmentSupport.ResolvedResponseWire,
        options: GenerationOptions,
        availableTools: List<ToolDefinition>,
    ): ResponseCreateParams =
        buildResponseParams(
            modelId = modelId,
            input = wire.input,
            instructions = wire.instructions,
            options = options,
            availableTools = availableTools,
            store = null,
            additionalHeaders = emptyMap(),
            additionalBodyProperties = emptyMap(),
        )

    fun mapToResponseParams(
        modelId: String,
        prompt: String,
        history: List<ChatMessage>,
        options: GenerationOptions,
    ): ResponseCreateParams {
        val provider = options.apiProvider
        val optModelId = options.modelId
        val parameterSupport =
            if (provider != null && optModelId != null) {
                ApiProviderModelPolicy.parameterSupport(provider, optModelId)
            } else {
                null
            }

        val messages = mutableListOf<ResponseInputItem>()
        val systemMessages = mutableListOf<String>()

        history.filterNot(::isSyntheticAssistantError).forEach { msg ->
            if (msg.role == Role.SYSTEM) {
                systemMessages += ImagePayloads.stripBase64DataUris(msg.content)
                return@forEach
            }

            val sanitizedContent = ImagePayloads.stripBase64DataUris(msg.content)
            messages.add(ResponsesInputMessageSupport.textInputItem(msg.role, sanitizedContent))
        }

        messages.add(ResponseInputItem.ofMessage(buildResponseUserMessage(prompt, options.imageUris)))

        return buildResponseParams(
            modelId = modelId,
            input = messages,
            instructions =
                systemMessages
                    .joinToString(separator = "\n\n")
                    .takeIf { it.isNotBlank() },
            options = options,
            availableTools = if (options.toolingEnabled) options.activeTools else emptyList(),
            store = null,
            additionalHeaders = emptyMap(),
            additionalBodyProperties = emptyMap(),
            parameterSupport = parameterSupport,
        )
    }

    private fun buildResponseParams(
        modelId: String,
        input: List<ResponseInputItem>,
        instructions: String?,
        options: GenerationOptions,
        availableTools: List<ToolDefinition>,
        store: Boolean?,
        additionalHeaders: Map<String, String>,
        additionalBodyProperties: Map<String, Any>,
        parameterSupport: com.browntowndev.enclave.domain.model.inference.ApiModelParameterSupport? =
            run {
                val provider = options.apiProvider
                val optModelId = options.modelId
                if (provider != null && optModelId != null) {
                    ApiProviderModelPolicy.parameterSupport(provider, optModelId)
                } else {
                    null
                }
            },
    ): ResponseCreateParams {
        val builder =
            ResponseCreateParams
                .builder()
                .model(modelId)
                .inputOfResponse(input)

        instructions?.let(builder::instructions)
        store?.let(builder::store)
        additionalHeaders.forEach { (key, value) -> builder.putAdditionalHeader(key, value) }
        additionalBodyProperties.forEach { (key, value) ->
            builder.putAdditionalBodyProperty(key, JsonValue.from(value))
        }

        options.reasoningEffort?.let { builder.reasoning(ReasoningMapper.toSdkReasoning(it)) }
        if (!options.temperatureDisabled && parameterSupport?.supportsTemperature != false) {
            options.temperature?.let { builder.temperature(it.toDouble()) }
        }
        if (!options.topPDisabled && parameterSupport?.supportsTopP != false) {
            options.topP?.let { builder.topP(it.toDouble()) }
        }
        if (!options.maxTokensDisabled && parameterSupport?.supportsMaxTokens != false) {
            options.maxTokens?.let { builder.maxOutputTokens(it.toLong()) }
        }
        if (availableTools.isNotEmpty()) {
            availableTools.forEach { builder.addTool(it.toResponsesTool()) }
        }

        options.chatId
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?.let(builder::promptCacheKey)
        return builder.build()
    }

    private fun isSyntheticAssistantError(message: ChatMessage): Boolean =
        message.role == Role.ASSISTANT &&
            message.content.startsWith(ProviderInferenceConstants.SYNTHETIC_API_ERROR_PREFIX)

    private fun ToolDefinition.toResponsesTool(): FunctionTool {
        val paramsBuilder =
            FunctionTool.Parameters
                .builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(schema.properties.toWireMap()))
                .putAdditionalProperty("required", JsonValue.from(schema.required))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
        schema.fullSchemaOverride?.get("definitions")?.let { defs ->
            if (defs is kotlinx.serialization.json.JsonObject) {
                paramsBuilder.putAdditionalProperty("definitions", JsonValue.from(defs.toWireMap()))
            }
        }
        schema.fullSchemaOverride?.get("\$defs")?.let { defs ->
            if (defs is kotlinx.serialization.json.JsonObject) {
                paramsBuilder.putAdditionalProperty("\$defs", JsonValue.from(defs.toWireMap()))
            }
        }
        return FunctionTool
            .builder()
            .name(name)
            .description(description)
            .parameters(paramsBuilder.build())
            .strict(true)
            .build()
    }

    private fun buildResponseUserMessage(
        prompt: String,
        imageUris: List<String>,
    ): ResponseInputItem.Message {
        val builder =
            ResponseInputItem.Message
                .builder()
                .role(ResponseInputItem.Message.Role.of("user"))
                .addInputTextContent(prompt)

        ImagePayloads.fromUris(imageUris).forEach { payload ->
            ImagePayloads.validate(payload)
            builder.addContent(
                ResponseInputImage
                    .builder()
                    .detail(ResponseInputImage.Detail.AUTO)
                    .imageUrl(payload.dataUrl)
                    .build(),
            )
        }

        return builder.build()
    }
}

internal fun ToolDefinition.toChatCompletionTool(strict: Boolean = false): ChatCompletionFunctionTool {
    val paramsBuilder =
        FunctionParameters
            .builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(schema.properties.toWireMap()))
            .putAdditionalProperty("required", JsonValue.from(schema.required))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
    schema.fullSchemaOverride?.get("definitions")?.let { defs ->
        if (defs is kotlinx.serialization.json.JsonObject) {
            paramsBuilder.putAdditionalProperty("definitions", JsonValue.from(defs.toWireMap()))
        }
    }
    schema.fullSchemaOverride?.get("\$defs")?.let { defs ->
        if (defs is kotlinx.serialization.json.JsonObject) {
            paramsBuilder.putAdditionalProperty("\$defs", JsonValue.from(defs.toWireMap()))
        }
    }
    return ChatCompletionFunctionTool
        .builder()
        .function(
            FunctionDefinition
                .builder()
                .name(name)
                .description(description)
                .parameters(paramsBuilder.build())
                .strict(strict)
                .build(),
        ).build()
}
