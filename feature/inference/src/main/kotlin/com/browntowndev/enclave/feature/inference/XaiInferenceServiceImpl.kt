package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.AgentRawToolCall
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.responses.ResponseCreateParams

open class XaiInferenceServiceImpl(
    client: OpenAIClient,
    modelId: String,
    modelType: ModelType,
    baseUrl: String? = null,
    loggingPort: LoggingPort,
    toolStreamGatePort: ToolStreamGatePort,
    telemetrySupport: InferenceTelemetrySupport? = null,
    telemetryEventPort: TelemetryEventPort? = null,
) : BaseOpenAiSdkInferenceService(
        client = client,
        modelId = modelId,
        provider = "XAI",
        modelType = modelType,
        baseUrl = baseUrl,
        loggingPort = loggingPort,
        toolStreamGatePort = toolStreamGatePort,
        telemetrySupport = telemetrySupport,
        telemetryEventPort = telemetryEventPort,
    ) {
    override val tag: String = "XaiInferenceService"

    override val maxImages: Int = 20

    override fun supportsClientToolsForModel(requestModelId: String): Boolean =
        !ApiProviderModelPolicy.isXaiMultiAgentModel(requestModelId)

    override fun shouldAllowChatCompletionsFallback(requestModelId: String): Boolean =
        XaiRequestMapper.shouldAllowChatCompletionsFallback(requestModelId)

    override fun applyPromptCacheRouting(
        builder: ChatCompletionCreateParams.Builder,
        options: GenerationOptions,
    ) {
        val conversationId = options.chatId?.value?.takeIf { it.isNotBlank() } ?: return
        builder.putAdditionalHeader(ProviderInferenceConstants.XAI_CONVERSATION_ID_HEADER, conversationId)
    }

    internal override fun buildResponseParams(
        responseWireSegments: List<ResponseWireSegment>,
        requestModelId: String,
        availableTools: List<ToolDefinition>,
        options: GenerationOptions,
    ): ResponseCreateParams? {
        val responseOptions = options.copy(
            apiProvider = ApiProvider.XAI,
            modelId = requestModelId,
        )
        val wire = ResponseWireSegmentSupport.resolveForXai(responseWireSegments)
        return XaiRequestMapper.mapSessionToResponseParams(
            modelId = requestModelId,
            wire = wire,
            options = responseOptions,
            availableTools = availableTools,
        )
    }

    override fun logTurnTelemetry(
        row: TurnTelemetry,
        messages: List<ChatCompletionMessageParam>,
        allowToolCall: Boolean,
        toolCalls: List<AgentRawToolCall>,
        appendOnlyClears: Int,
    ) {
        val wireHead =
            messages
                .take(XAI_WIRE_LOG_HEAD_MESSAGE_COUNT)
                .joinToString(" | ") { param ->
                    when {
                        param.isSystem() ->
                            "sys:${
                                param.asSystem().content().asText()
                                    .take(XAI_WIRE_LOG_SYSTEM_PREVIEW_CHARS)
                            }"
                        param.isUser() ->
                            "user:${
                                param.asUser().content().asText()
                                    .take(XAI_WIRE_LOG_USER_PREVIEW_CHARS)
                            }"
                        param.isAssistant() -> "asst"
                        param.isTool() -> "tool"
                        else -> "other"
                    }
                }
        loggingPort.debug(
            tag,
            "xAI telemetry turn seq=${row.sequenceIndex} allowToolCall=$allowToolCall " +
                "toolCalls=${toolCalls.size} wireMsgs=${messages.size} " +
                "appendOnlyClears=$appendOnlyClears wireHead=[$wireHead] " +
                "input=${row.inputTokens} cacheRead=${row.cacheReadInputTokens} " +
                "output=${row.outputTokens}",
        )
    }

    companion object {
        private const val XAI_WIRE_LOG_HEAD_MESSAGE_COUNT = 3
        private const val XAI_WIRE_LOG_SYSTEM_PREVIEW_CHARS = 48
        private const val XAI_WIRE_LOG_USER_PREVIEW_CHARS = 24
    }
}
