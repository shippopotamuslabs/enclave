package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolStreamGatePort
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
