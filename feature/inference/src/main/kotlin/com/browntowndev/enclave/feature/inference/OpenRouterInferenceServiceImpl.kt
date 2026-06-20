package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.browntowndev.enclave.feature.inference.telemetry.PromptCacheDiagnosticsContext
import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.models.chat.completions.ChatCompletionCreateParams

open class OpenRouterInferenceServiceImpl(
    client: OpenAIClient,
    modelId: String,
    modelType: ModelType,
    openRouterRouting: OpenRouterRoutingConfiguration,
    baseUrl: String? = null,
    loggingPort: LoggingPort,
    toolStreamGatePort: ToolStreamGatePort,
    telemetrySupport: InferenceTelemetrySupport? = null,
    telemetryEventPort: TelemetryEventPort? = null,
) : BaseOpenAiSdkInferenceService(
        client = client,
        modelId = modelId,
        provider = "OPENROUTER",
        modelType = modelType,
        openRouterRouting = openRouterRouting,
        baseUrl = baseUrl,
        loggingPort = loggingPort,
        toolStreamGatePort = toolStreamGatePort,
        telemetrySupport = telemetrySupport,
        telemetryEventPort = telemetryEventPort,
    ) {
    override val tag: String = "OpenRouterInferenceService"

    override fun enablePromptCacheDiagnostics(): Boolean = true

    override fun logPromptCacheProviderExtras(context: PromptCacheDiagnosticsContext) {
        val sessionId = context.chatId?.value?.takeIf { it.isNotBlank() }
        loggingPort.debug(
            tag,
            "PROMPT_CACHE_OPENROUTER sessionId=${sessionId ?: "absent"} " +
                "preferredProvider=${openRouterRouting?.preferredProvider ?: "auto"} " +
                "allowFallbacks=${openRouterRouting?.allowFallbacks}",
        )
    }

    override fun supportsExplicitCacheControl(requestModelId: String): Boolean {
        return OpenRouterPromptCacheSupport.supportsExplicitCacheControl(requestModelId)
    }

    override fun applyPromptCacheRouting(
        builder: ChatCompletionCreateParams.Builder,
        options: GenerationOptions,
    ) {
        val conversationId = options.chatId?.value?.takeIf { it.isNotBlank() } ?: return
        builder.putAdditionalHeader("X-Session-Id", conversationId)
        builder.putAdditionalBodyProperty("session_id", JsonValue.from(conversationId))
    }

    override fun applyProviderChatExtras(
        builder: ChatCompletionCreateParams.Builder,
        requestModelId: String,
        options: GenerationOptions,
    ) {
        if (openRouterRouting != null) {
            OpenRouterRoutingSupport.applyToChatCompletionBuilder(builder, openRouterRouting)
        }
    }
}
