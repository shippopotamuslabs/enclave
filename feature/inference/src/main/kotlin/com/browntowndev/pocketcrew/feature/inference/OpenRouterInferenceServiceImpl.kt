package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.openai.client.OpenAIClient
import com.openai.errors.BadRequestException

class OpenRouterInferenceServiceImpl(
    client: OpenAIClient,
    modelId: String,
    modelType: ModelType,
    private val routing: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration(),
    baseUrl: String? = null,
    loggingPort: LoggingPort
) : BaseOpenAiSdkInferenceService(
    client = client,
    modelId = modelId,
    provider = "OPENROUTER",
    modelType = modelType,
    baseUrl = baseUrl,
    loggingPort = loggingPort
) {

    override val tag: String = "OpenRouterInferenceService"

    override suspend fun executePrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit
    ) {
        val responseParams = OpenRouterRequestMapper.mapToResponseParams(
            modelId = modelId,
            prompt = prompt,
            history = requestHistory,
            options = options,
            routing = routing
        )

        try {
            loggingPort.info(
                tag,
                "Using OpenRouter Responses API. model=$modelId reasoningEffort=${options.reasoningEffort} reasoningBudget=${options.reasoningBudget}"
            )
            streamResponses(responseParams, emitEvent)
        } catch (e: Exception) {
            if (e !is BadRequestException && e.message?.contains("400") != true && e.message?.contains("Bad Request") != true) {
                throw e
            }

            loggingPort.warning(
                tag,
                "OpenRouter Responses API rejected request. Falling back to chat completions. ${describeException(e)}"
            )

            val chatParams = OpenRouterRequestMapper.mapToChatCompletionParams(
                modelId = modelId,
                prompt = prompt,
                history = requestHistory,
                options = options,
                routing = routing
            )
            streamChatCompletions(chatParams, emitEvent)
        }
    }
}
