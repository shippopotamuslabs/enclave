package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlinx.serialization.json.JsonObject

/**
 * DeepSeek inference service using the official OpenAI-compatible Chat Completions endpoint.
 */
open class DeepSeekInferenceServiceImpl(
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
        provider = "DEEPSEEK",
        modelType = modelType,
        baseUrl = baseUrl,
        loggingPort = loggingPort,
        toolStreamGatePort = toolStreamGatePort,
        telemetrySupport = telemetrySupport,
        telemetryEventPort = telemetryEventPort,
    ) {
    override val tag: String = "DeepSeekInferenceService"

    override fun enablePromptCacheDiagnostics(): Boolean = true

    override fun applyMaxOutputTokens(
        builder: ChatCompletionCreateParams.Builder,
        tokenLimit: Int,
        requestModelId: String,
    ) {
        if (DeepSeekRequestPolicy.usesMaxTokensField(requestModelId)) {
            builder.putAdditionalBodyProperty("max_tokens", JsonValue.from(tokenLimit.toLong()))
        } else {
            super.applyMaxOutputTokens(builder, tokenLimit, requestModelId)
        }
    }

    override fun shouldIncludeToolChoice(
        requestModelId: String,
        toolsAvailable: Boolean,
        options: GenerationOptions,
    ): Boolean =
        toolsAvailable &&
            !DeepSeekRequestPolicy.shouldOmitToolChoice(
                modelId = requestModelId,
                reasoningBudget = options.reasoningBudget,
                toolsAvailable = toolsAvailable,
            )

    override fun applyProviderChatExtras(
        builder: ChatCompletionCreateParams.Builder,
        requestModelId: String,
        options: GenerationOptions,
    ) {
        if (DeepSeekRequestPolicy.thinkingExtraBody(requestModelId, options.reasoningBudget) != null) {
            builder.putAdditionalBodyProperty("thinking", DeepSeekRequestPolicy.thinkingJsonValue())
        }
        DeepSeekRequestPolicy
            .mapReasoningEffort(
                modelId = requestModelId,
                reasoningBudget = options.reasoningBudget,
                effort = options.reasoningEffort,
            )?.let { effort ->
                builder.putAdditionalBodyProperty("reasoning_effort", JsonValue.from(effort))
            }
    }

    override fun shouldAttachProviderReasoningContent(
        requestModelId: String,
        options: GenerationOptions,
        metadata: JsonObject?,
    ): Boolean {
        if (ApiProviderModelPolicy.shouldStripReasoningContentOnAssistantReplay(requestModelId)) {
            return false
        }
        return super.shouldAttachProviderReasoningContent(requestModelId, options, metadata)
    }

    override fun supportsClientToolsForModel(requestModelId: String): Boolean =
        ApiProviderModelPolicy.supportsDeepSeekClientTools(requestModelId)
}
