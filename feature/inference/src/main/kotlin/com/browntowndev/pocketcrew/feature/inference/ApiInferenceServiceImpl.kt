package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolStreamGatePort
import com.openai.client.OpenAIClient

class ApiInferenceServiceImpl(
    client: OpenAIClient,
    modelId: String,
    provider: String,
    modelType: ModelType,
    baseUrl: String? = null,
    loggingPort: LoggingPort,
    toolStreamGatePort: ToolStreamGatePort,
) : BaseOpenAiSdkInferenceService(
        client = client,
        modelId = modelId,
        provider = provider,
        modelType = modelType,
        baseUrl = baseUrl,
        loggingPort = loggingPort,
        toolStreamGatePort = toolStreamGatePort,
    ) {
    override val tag: String = "ApiInferenceService"
}
