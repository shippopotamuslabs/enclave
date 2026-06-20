package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.domain.port.inference.ToolEnvelopeParserPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport

internal object ApiInferenceServiceFactory {
    @Suppress("LongParameterList")
    fun buildUncachedService(
        apiConfig: ApiModelConfiguration,
        apiCreds: ApiCredentials,
        apiKey: String,
        requestHeaders: Map<String, String>,
        resolvedBaseUrl: String?,
        modelType: ModelType,
        openAiClientProvider: OpenAiClientProviderPort,
        anthropicClientProvider: AnthropicClientProviderPort,
        googleGenAiClientProvider: GoogleGenAiClientProviderPort,
        loggingPort: LoggingPort,
        toolEnvelopeParserPort: ToolEnvelopeParserPort,
        toolStreamGatePort: ToolStreamGatePort,
        telemetrySupport: InferenceTelemetrySupport,
        telemetryEventPort: TelemetryEventPort,
    ): LlmInferencePort =
        when (apiCreds.provider) {
            ApiProvider.ANTHROPIC ->
                AnthropicInferenceServiceImpl(
                    client =
                        anthropicClientProvider.getClient(
                            apiKey = apiKey,
                            baseUrl = resolvedBaseUrl,
                            headers = requestHeaders,
                        ),
                    modelId = apiConfig.modelId,
                    modelType = modelType,
                    baseUrl = resolvedBaseUrl,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = toolEnvelopeParserPort,
                    toolStreamGatePort = toolStreamGatePort,
                    telemetrySupport = telemetrySupport,
                    telemetryEventPort = telemetryEventPort,
                    providerLabel = telemetrySupport.providerLabel(ApiProvider.ANTHROPIC.name),
                )
            ApiProvider.DEEPSEEK ->
                DeepSeekInferenceServiceImpl(
                    client =
                        openAiClientProvider.getClient(
                            apiKey = apiKey,
                            baseUrl = resolvedBaseUrl,
                            headers = requestHeaders,
                        ),
                    modelId = apiConfig.modelId,
                    modelType = modelType,
                    baseUrl = resolvedBaseUrl,
                    loggingPort = loggingPort,
                    toolStreamGatePort = toolStreamGatePort,
                    telemetrySupport = telemetrySupport,
                    telemetryEventPort = telemetryEventPort,
                )
            ApiProvider.ZAI ->
                ZaiInferenceServiceImpl(
                    client =
                        openAiClientProvider.getClient(
                            apiKey = apiKey,
                            baseUrl = resolvedBaseUrl,
                            headers = requestHeaders,
                        ),
                    modelId = apiConfig.modelId,
                    modelType = modelType,
                    baseUrl = resolvedBaseUrl,
                    loggingPort = loggingPort,
                    toolStreamGatePort = toolStreamGatePort,
                    telemetrySupport = telemetrySupport,
                    telemetryEventPort = telemetryEventPort,
                )
            ApiProvider.GOOGLE -> {
                val client =
                    googleGenAiClientProvider.getClient(
                        apiKey = apiKey,
                        baseUrl = resolvedBaseUrl,
                        headers = requestHeaders,
                    )
                val sdkClient =
                    GoogleGenAiSdkClientImpl(
                        clientProvider = { client },
                        modelType = modelType,
                        loggingPort = loggingPort,
                    )
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = apiConfig.modelId,
                    modelType = modelType,
                    baseUrl = resolvedBaseUrl,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = toolEnvelopeParserPort,
                    toolStreamGatePort = toolStreamGatePort,
                    telemetrySupport = telemetrySupport,
                    telemetryEventPort = telemetryEventPort,
                )
            }
            ApiProvider.OPENROUTER ->
                OpenRouterInferenceServiceImpl(
                    client =
                        openAiClientProvider.getClient(
                            apiKey = apiKey,
                            baseUrl = resolvedBaseUrl,
                            headers = requestHeaders,
                        ),
                    modelId = apiConfig.modelId,
                    modelType = modelType,
                    openRouterRouting = apiConfig.openRouterRouting,
                    baseUrl = resolvedBaseUrl,
                    loggingPort = loggingPort,
                    toolStreamGatePort = toolStreamGatePort,
                    telemetrySupport = telemetrySupport,
                    telemetryEventPort = telemetryEventPort,
                )
            ApiProvider.XAI ->
                XaiInferenceServiceImpl(
                    client =
                        openAiClientProvider.getClient(
                            apiKey = apiKey,
                            baseUrl = resolvedBaseUrl,
                            headers = requestHeaders,
                        ),
                    modelId = apiConfig.modelId,
                    modelType = modelType,
                    baseUrl = resolvedBaseUrl,
                    loggingPort = loggingPort,
                    toolStreamGatePort = toolStreamGatePort,
                    telemetrySupport = telemetrySupport,
                    telemetryEventPort = telemetryEventPort,
                )
            ApiProvider.FAL ->
                throw IllegalStateException("fal.ai is media-only and cannot be used for chat inference.")
            else ->
                ApiInferenceServiceImpl(
                    client =
                        openAiClientProvider.getClient(
                            apiKey = apiKey,
                            baseUrl = resolvedBaseUrl,
                            headers = requestHeaders,
                        ),
                    modelId = apiConfig.modelId,
                    provider = apiCreds.provider.name,
                    modelType = modelType,
                    baseUrl = resolvedBaseUrl,
                    loggingPort = loggingPort,
                    toolStreamGatePort = toolStreamGatePort,
                    telemetrySupport = telemetrySupport,
                    telemetryEventPort = telemetryEventPort,
                )
        }
}
