package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.openai.client.OpenAIClient
import com.openai.errors.BadRequestException

class XaiInferenceServiceImpl(
    client: OpenAIClient,
    modelId: String,
    modelType: ModelType,
    baseUrl: String? = null,
    loggingPort: LoggingPort
) : BaseOpenAiSdkInferenceService(
    client = client,
    modelId = modelId,
    provider = "XAI",
    modelType = modelType,
    baseUrl = baseUrl,
    loggingPort = loggingPort
) {

    override val tag: String = "XaiInferenceService"

    override suspend fun executePrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit
    ) {
        val multiAgentModel = XaiRequestMapper.isMultiAgentModel(modelId)
        val chatFallbackAllowed = XaiRequestMapper.shouldAllowChatCompletionsFallback(modelId)
        val chatReasoningContentModel = XaiRequestMapper.isChatReasoningContentModel(modelId)
        val responseParams = XaiRequestMapper.mapToResponseParams(
            modelId = modelId,
            prompt = prompt,
            history = requestHistory,
            options = options
        )

        try {
            loggingPort.info(
                tag,
                "Using Responses API for xAI model. model=$modelId chatFallbackAllowed=$chatFallbackAllowed chatReasoningContentModel=$chatReasoningContentModel reasoningEffort=${options.reasoningEffort} reasoningBudget=${options.reasoningBudget}"
            )
            streamResponses(responseParams, emitEvent)
        } catch (e: Exception) {
            val badRequest = e is BadRequestException || e.message?.contains("400") == true || e.message?.contains("Bad Request") == true
            if (!badRequest || !chatFallbackAllowed) {
                if (badRequest && multiAgentModel) {
                    loggingPort.error(
                        tag,
                        "Responses API rejected xAI multi-agent request. Chat fallback is disabled for this model. ${describeException(e)}",
                        e
                    )
                }
                throw e
            }

            loggingPort.warning(
                tag,
                "Responses API rejected xAI request. Falling back to chat completions. model=$modelId chatReasoningContentModel=$chatReasoningContentModel ${describeException(e)}"
            )

            val chatParams = XaiRequestMapper.mapToChatCompletionParams(
                modelId = modelId,
                prompt = prompt,
                history = requestHistory,
                options = options
            )
            streamChatCompletions(chatParams, emitEvent)
        }
    }
}
