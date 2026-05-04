package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.usecase.inference.LlmToolingOrchestrator
import com.browntowndev.pocketcrew.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.errors.BadRequestException
import com.openai.errors.NotFoundException
import com.openai.models.responses.ResponseCreateParams

class ApiInferenceServiceImpl(
    client: OpenAIClient,
    modelId: String,
    provider: String,
    modelType: ModelType,
    baseUrl: String? = null,
    loggingPort: LoggingPort,
    orchestrator: LlmToolingOrchestrator,
) : BaseOpenAiSdkInferenceService(
    client = client,
    modelId = modelId,
    provider = provider,
    modelType = modelType,
    baseUrl = baseUrl,
    loggingPort = loggingPort,
    orchestrator = orchestrator,
) {

    override val tag: String = "ApiInferenceService"

    override fun mapToolingResponseParams(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>
    ): ResponseCreateParams =
        OpenAiRequestMapper.mapToResponseParams(
            modelId = modelId,
            prompt = prompt,
            history = requestHistory,
            options = options
        )

    override fun mapToolingFollowUpResponseParams(
        currentParams: ResponseCreateParams,
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        initialResponse: StreamedOpenAiResponse,
        results: List<Pair<ToolCallRequest, String>>,
        appendStopToolsWarning: Boolean
    ): ResponseCreateParams =
        OpenAiRequestMapper.mapToFollowUpResponseParams(
            currentParams = currentParams,
            prompt = prompt,
            history = requestHistory,
            options = options,
            initialResponse = initialResponse,
            results = results,
            appendStopToolsWarning = appendStopToolsWarning
        )

    override suspend fun executePrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit
    ) {
        if (options.toolingEnabled && options.availableTools.isNotEmpty()) {
            try {
                executeToolingPrompt(prompt, options, requestHistory, emitEvent)
                return
            } catch (e: Exception) {
                if (e is BadRequestException || e.message?.contains("400") == true || e.message?.contains("Bad Request") == true ||
                    e is NotFoundException || e.message?.contains("404") == true || e.message?.contains("Not Found") == true
                ) {
                    loggingPort.warning(
                        tag,
                        "Responses API rejected tooling request. Falling back to chat completions tooling. ${describeException(e)}"
                    )
                } else {
                    throw e
                }

                executeChatCompletionsToolingPrompt(
                    prompt,
                    options.copy(reasoningEffort = null, reasoningBudget = 0),
                    requestHistory,
                    emitEvent
                )
                return
            }
        }

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
                "Using Responses API for provider=$provider model=$modelId reasoningEffort=${options.reasoningEffort} reasoningBudget=${options.reasoningBudget}"
            )
            streamResponses(
                params = responseParams,
                emitEvent = emitEvent,
            )
            emitEvent(InferenceEvent.Finished(modelType))
        } catch (e: Exception) {
            if (e is BadRequestException || e.message?.contains("400") == true || e.message?.contains("Bad Request") == true ||
                e is NotFoundException || e.message?.contains("404") == true || e.message?.contains("Not Found") == true
            ) {
                loggingPort.warning(
                    tag,
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
            streamChatCompletions(chatParams, emitEvent)
            emitEvent(InferenceEvent.Finished(modelType))
        }
    }
}
