package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolStreamGatePort
import com.openai.client.OpenAIClient
import com.openai.errors.BadRequestException
import com.openai.errors.NotFoundException

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

    override suspend fun executePrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit
    ) {
        logImagePayloads(options)

        val responseParams = OpenAiRequestMapper.mapToResponseParams(
            modelId = modelId,
            prompt = prompt,
            history = requestHistory,
            options = options
        )

        try {
            loggingPort.info(
                tag,
                @Suppress("MaxLineLength")
                "Using Responses API for provider=$provider model=$modelId reasoningEffort=${options.reasoningEffort} reasoningBudget=${options.reasoningBudget}"
            )
            streamResponses(
                params = responseParams,
                emitEvent = emitEvent,
            )
            emitEvent(InferenceEvent.Finished(modelType))
        } catch (e: BadRequestException) {
            loggingPort.warning(
                tag,
                @Suppress("MaxLineLength")
                "Responses API rejected request. Falling back to chat completions with reasoningStreamAvailable=false. ${describeException(e)}"
            )
        } catch (e: NotFoundException) {
            loggingPort.warning(
                tag,
                @Suppress("MaxLineLength")
                "Responses API rejected request. Falling back to chat completions with reasoningStreamAvailable=false. ${describeException(e)}"
            )
        } catch (e: Exception) {
            val isClientError = (e.message?.contains("400") == true || e.message?.contains("Bad Request") == true) ||
                (e.message?.contains("404") == true || e.message?.contains("Not Found") == true)
            if (isClientError) {
                loggingPort.warning(
                    tag,
                    @Suppress("MaxLineLength")
                    "Responses API rejected request. Falling back to chat completions with reasoningStreamAvailable=false. ${describeException(e)}"
                )
            } else {
                throw e
            }

            val chatParams = OpenAiRequestMapper.mapToChatCompletionParams(
                modelId = modelId,
                prompt = prompt,
                history = requestHistory,
                options = options.copy(reasoningEffort = null, reasoningBudget = 0)
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
    }
}
