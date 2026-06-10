package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.openai.core.JsonValue
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputImage
import com.browntowndev.enclave.feature.inference.JsonSchemaWireSupport.toWireMap
import com.openai.models.responses.ResponseInputItem
import kotlinx.serialization.json.JsonObject

object XaiRequestMapper {

    private data class SanitizedXaiRequestOptions(
        val reasoningEffort: ApiReasoningEffort?,
        val maxTokens: Int?,
    )

    internal fun mapSessionToResponseParams(
        modelId: String,
        wire: ResponseWireSegmentSupport.ResolvedResponseWire,
        options: GenerationOptions,
        availableTools: List<ToolDefinition>,
    ): ResponseCreateParams =
        buildResponseParams(
            modelId = modelId,
            input = wire.input,
            options = options,
            availableTools = availableTools,
        )

    fun mapToResponseParams(
        modelId: String,
        prompt: String,
        history: List<ChatMessage>,
        options: GenerationOptions,
    ): ResponseCreateParams {
        val messages = mutableListOf<ResponseInputItem>()

        history.filterNot(::isSyntheticAssistantError).forEach { msg ->
            val sanitizedContent = ImagePayloads.stripBase64DataUris(msg.content)
            messages.add(ResponsesInputMessageSupport.textInputItem(msg.role, sanitizedContent))
        }

        messages.add(ResponseInputItem.ofMessage(buildResponseUserMessage(prompt, options.imageUris)))

        return buildResponseParams(
            modelId = modelId,
            input = messages,
            options = options,
            availableTools = if (options.toolingEnabled) options.activeTools else emptyList(),
        )
    }

    private fun buildResponseParams(
        modelId: String,
        input: List<ResponseInputItem>,
        options: GenerationOptions,
        availableTools: List<ToolDefinition>,
    ): ResponseCreateParams {
        val sanitizedOptions = sanitizeResponseOptions(modelId, options)
        val builder =
            ResponseCreateParams
                .builder()
                .model(modelId)
                .store(false)
                .inputOfResponse(input)

        val provider = options.apiProvider
        val optModelId = options.modelId
        val parameterSupport =
            if (provider != null && optModelId != null) {
                ApiProviderModelPolicy.parameterSupport(provider, optModelId)
            } else {
                null
            }

        sanitizedOptions.reasoningEffort?.let { builder.reasoning(ReasoningMapper.toSdkReasoning(it)) }
        if (!options.temperatureDisabled && parameterSupport?.supportsTemperature != false) {
            options.temperature?.let { builder.temperature(it.toDouble()) }
        }
        if (!options.topPDisabled && parameterSupport?.supportsTopP != false) {
            options.topP?.let { builder.topP(it.toDouble()) }
        }
        if (!options.maxTokensDisabled && parameterSupport?.supportsMaxTokens != false) {
            sanitizedOptions.maxTokens?.let { builder.maxOutputTokens(it.toLong()) }
        }

        val toolsPayload = mutableListOf<Map<String, Any>>()

        if (modelId.startsWith("grok-4.20")) {
            toolsPayload.add(mapOf("type" to "web_search"))
            toolsPayload.add(mapOf("type" to "x_search"))
            toolsPayload.add(mapOf("type" to "code_execution"))
        }

        if (availableTools.isNotEmpty() && !isMultiAgentModel(modelId)) {
            toolsPayload.addAll(availableTools.map { it.toResponseToolPayloadMap() })
        }

        if (toolsPayload.isNotEmpty()) {
            builder.putAdditionalBodyProperty("tools", JsonValue.from(toolsPayload))
        }

        options.chatId
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?.let { conversationId ->
                builder.putAdditionalHeader(ProviderInferenceConstants.XAI_CONVERSATION_ID_HEADER, conversationId)
            }
        return builder.build()
    }

    fun isMultiAgentModel(modelId: String): Boolean = ApiProviderModelPolicy.isXaiMultiAgentModel(modelId)

    fun shouldAllowChatCompletionsFallback(modelId: String): Boolean =
        !modelId.startsWith("grok-4.20") && !ApiProviderModelPolicy.isXaiMultiAgentModel(modelId)

    fun isChatReasoningContentModel(modelId: String): Boolean = ApiProviderModelPolicy.isXaiChatReasoningContentModel(modelId)

    private fun sanitizeResponseOptions(
        modelId: String,
        options: GenerationOptions,
    ): SanitizedXaiRequestOptions =
        SanitizedXaiRequestOptions(
            reasoningEffort = sanitizeReasoningEffort(modelId, options.reasoningEffort),
            maxTokens = if (ApiProviderModelPolicy.isXaiMultiAgentModel(modelId)) null else options.maxTokens,
        )

    private fun sanitizeReasoningEffort(
        modelId: String,
        reasoningEffort: ApiReasoningEffort?,
    ): ApiReasoningEffort? {
        if (reasoningEffort == null) {
            return null
        }
        return when {
            ApiProviderModelPolicy.isXaiGrok420Family(modelId) -> {
                if (ApiProviderModelPolicy.isXaiMultiAgentModel(modelId)) reasoningEffort else null
            }
            ApiProviderModelPolicy.isXaiGrok3MiniModel(modelId) || modelId.startsWith("grok-4.3") -> {
                reasoningEffort
            }
            ApiProviderModelPolicy.isXaiGrok3Model(modelId) ||
                ApiProviderModelPolicy.isXaiGrok4ReasoningFamily(modelId) ||
                ApiProviderModelPolicy.isXaiGrok4NonReasoningFamily(modelId) -> {
                null
            }
            else -> {
                reasoningEffort
            }
        }
    }

    private fun isSyntheticAssistantError(message: ChatMessage): Boolean =
        message.role == Role.ASSISTANT &&
            message.content.startsWith(ProviderInferenceConstants.SYNTHETIC_API_ERROR_PREFIX)

    private fun ToolDefinition.toResponseToolPayloadMap(): Map<String, Any> {
        val parametersMap =
            mutableMapOf<String, Any?>(
                "type" to "object",
                "properties" to schema.properties.toWireMap(),
            )
        schema.fullSchemaOverride?.get("definitions")?.let { defs ->
            if (defs is JsonObject) parametersMap["definitions"] = defs.toWireMap()
        }
        schema.fullSchemaOverride?.get("\$defs")?.let { defs ->
            if (defs is JsonObject) parametersMap["\$defs"] = defs.toWireMap()
        }
        if (schema.required.isNotEmpty()) {
            parametersMap["required"] = schema.required
        }

        return mapOf(
            "type" to "function",
            "name" to name,
            "description" to description,
            "parameters" to parametersMap,
            "strict" to false,
        )
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
