package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.openai.client.OpenAIClient

class OpenRouterInferenceServiceImpl(
    client: OpenAIClient,
    modelId: String,
    modelType: ModelType,
    baseUrl: String? = null,
    loggingPort: LoggingPort,
    toolStreamGatePort: ToolStreamGatePort,
) : BaseOpenAiSdkInferenceService(
        client = client,
        modelId = modelId,
        provider = "OPENROUTER",
        modelType = modelType,
        baseUrl = baseUrl,
        loggingPort = loggingPort,
        toolStreamGatePort = toolStreamGatePort,
    ) {
    override val tag: String = "OpenRouterInferenceService"
}
