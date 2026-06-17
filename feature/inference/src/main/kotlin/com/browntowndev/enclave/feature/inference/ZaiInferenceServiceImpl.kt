package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.openai.client.OpenAIClient

/**
 * Z.ai inference service using the OpenAI-compatible Chat Completions endpoint
 * (`https://api.z.ai/api/paas/v4` or `.../coding/paas/v4`).
 *
 * Z.ai's OpenAI endpoint returns richer, standard-compliant usage telemetry —
 * `prompt_tokens_details.cached_tokens` and `completion_tokens_details.reasoning_tokens` —
 * which [com.browntowndev.enclave.feature.inference.openai.OpenAiStreamUsageAccumulator] parses
 * natively. This avoids the Anthropic endpoint's non-standard reasoning-token escape hatches.
 *
 * Functionally identical to [ApiInferenceServiceImpl]; the dedicated subclass provides a distinct
 * log tag and an extension point for future Z.ai-specific hooks.
 */
class ZaiInferenceServiceImpl(
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
        provider = "ZAI",
        modelType = modelType,
        baseUrl = baseUrl,
        loggingPort = loggingPort,
        toolStreamGatePort = toolStreamGatePort,
        telemetrySupport = telemetrySupport,
        telemetryEventPort = telemetryEventPort,
    ) {
    override val tag: String = "ZaiInferenceService"

    override fun toApiTemperature(value: Float): Double = ZaiSamplingParams.toApiDouble(value)

    override fun toApiTopP(value: Float): Double = ZaiSamplingParams.toApiDouble(value)
}
