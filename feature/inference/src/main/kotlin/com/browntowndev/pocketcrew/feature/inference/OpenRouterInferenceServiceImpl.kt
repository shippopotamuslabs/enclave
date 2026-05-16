package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.openai.client.OpenAIClient
import com.openai.errors.BadRequestException
import com.openai.errors.NotFoundException

class OpenRouterInferenceServiceImpl(
    client: OpenAIClient,
    modelId: String,
    modelType: ModelType,
    private val routing: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration(),
    baseUrl: String? = null,
    loggingPort: LoggingPort,
) : BaseOpenAiSdkInferenceService(
    client = client,
    modelId = modelId,
    provider = "OPENROUTER",
    modelType = modelType,
    baseUrl = baseUrl,
    loggingPort = loggingPort,
) {

    override val tag: String = "OpenRouterInferenceService"

    override suspend fun executePrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit
    ) {
        logImagePayloads(options)

        val responseParams = OpenRouterRequestMapper.mapToResponseParams(
            modelId = modelId,
            prompt = prompt,
            history = requestHistory,
            options = options,
            routing = routing
        )

        suspend fun fallbackToChatCompletions() {
            val chatParams = OpenRouterRequestMapper.mapToChatCompletionParams(
                modelId = modelId,
                prompt = prompt,
                history = requestHistory,
                options = options,
                routing = routing
            )
            streamChatCompletions(
                params = chatParams,
                chatId = options.chatId,
                userMessageId = options.userMessageId,
                assistantMessageId = options.assistantMessageId,
                emitEvent = emitEvent,
            )
            emitEvent(InferenceEvent.Finished(modelType))
        }

        try {
            loggingPort.info(
                tag,
                @Suppress("MaxLineLength")
                "Using OpenRouter Responses API. model=$modelId reasoningEffort=${options.reasoningEffort} reasoningBudget=${options.reasoningBudget}"
            )
            streamResponses(
                params = responseParams,
                emitEvent = emitEvent,
            )
            emitEvent(InferenceEvent.Finished(modelType))
        } catch (e: BadRequestException) {
            loggingPort.warning(
                tag,
                "OpenRouter Responses API rejected request. Falling back to chat completions. ${describeException(e)}"
            )
            fallbackToChatCompletions()
        } catch (e: NotFoundException) {
            loggingPort.warning(
                tag,
                "OpenRouter Responses API rejected request. Falling back to chat completions. ${describeException(e)}"
            )
            fallbackToChatCompletions()
        } catch (e: Exception) {
            val isClientError = (e.message?.contains("400") == true || e.message?.contains("Bad Request") == true) ||
                (e.message?.contains("404") == true || e.message?.contains("Not Found") == true)
            if (isClientError) {
                loggingPort.warning(
                    tag,
                    "OpenRouter Responses API rejected request. Falling back to chat completions. ${describeException(e)}"
                )
            } else {
                throw e
            }
            fallbackToChatCompletions()
        }
    }

}
