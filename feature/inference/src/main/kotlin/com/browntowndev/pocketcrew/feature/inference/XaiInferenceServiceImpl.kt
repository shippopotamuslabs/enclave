package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.usecase.inference.LlmToolingOrchestrator
import com.browntowndev.pocketcrew.domain.util.ContextWindowPlanner
import com.browntowndev.pocketcrew.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.errors.BadRequestException
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputItem

class XaiInferenceServiceImpl(
    client: OpenAIClient,
    modelId: String,
    modelType: ModelType,
    baseUrl: String? = null,
    loggingPort: LoggingPort,
    orchestrator: LlmToolingOrchestrator,
) : BaseOpenAiSdkInferenceService(
    client = client,
    modelId = modelId,
    provider = "XAI",
    modelType = modelType,
    baseUrl = baseUrl,
    loggingPort = loggingPort,
    orchestrator = orchestrator,
) {

    override val tag: String = "XaiInferenceService"

    override fun mapToolingResponseParams(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
    ): ResponseCreateParams =
        XaiRequestMapper.mapToResponseParams(
            modelId = modelId,
            prompt = prompt,
            history = requestHistory,
            options = options,
        )

    override fun mapToolingFollowUpResponseParams(
        currentParams: ResponseCreateParams,
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        initialResponse: StreamedOpenAiResponse,
        results: List<Pair<ToolCallRequest, String>>,
        appendStopToolsWarning: Boolean,
    ): ResponseCreateParams {
        val builder = currentParams.toBuilder()
        val inputItems = if (currentParams.input().isPresent && currentParams.input().get().isResponse()) {
            currentParams.input().get().asResponse().toMutableList()
        } else {
            mutableListOf()
        }

        initialResponse.assistantMessageText
            .takeIf { it.isNotBlank() }
            ?.let { assistantText ->
                inputItems += ResponseInputItem.ofMessage(
                    ResponseInputItem.Message.builder()
                        .role(ResponseInputItem.Message.Role.of("assistant"))
                        .addInputTextContent(assistantText)
                        .build()
                )
            }

        // Map results back to their corresponding function calls in the initial response
        results.forEach { (toolCall, resultJson) ->
            val index = initialResponse.functionCalls.indexOf(toolCall)

            val callId = if (index != -1) {
                initialResponse.providerToolCallIds.getOrElse(index) {
                    throw IllegalStateException("Missing function call id at index $index for xAI follow-up")
                }
            } else {
                "fallback_${java.util.UUID.randomUUID().toString().replace("-", "")}"
            }

            val functionItemId = if (index != -1) {
                initialResponse.providerToolItemIds.getOrElse(index) {
                    "fc_${callId}"
                }
            } else {
                "fc_${callId}"
            }

            inputItems += ResponseInputItem.ofFunctionCall(
                com.openai.models.responses.ResponseFunctionToolCall.builder()
                    .id(functionItemId)
                    .callId(callId)
                    .name(toolCall.toolName)
                    .arguments(toolCall.argumentsJson)
                    .build()
            )
            inputItems += ResponseInputItem.ofFunctionCallOutput(
                ResponseInputItem.FunctionCallOutput.builder()
                    .id("fco_${callId}")
                    .callId(callId)
                    .output(resultJson)
                    .build()
            )
        }

        if (appendStopToolsWarning) {
            inputItems += ResponseInputItem.ofMessage(
                ResponseInputItem.Message.builder()
                    .role(ResponseInputItem.Message.Role.of("user"))
                    .addInputTextContent(ContextWindowPlanner.STOP_TOOLS_WARNING)
                    .build()
            )
        }

        builder.inputOfResponse(inputItems)
        return builder.build()
    }

    override suspend fun executePrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit
    ) {
        if (options.toolingEnabled && options.availableTools.isNotEmpty()) {
            executeToolingPrompt(prompt, options, requestHistory, emitEvent)
            return
        }

        logImagePayloads(options)

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
            streamResponses(
                params = responseParams,
                emitEvent = emitEvent,
            )
            emitEvent(InferenceEvent.Finished(modelType))
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
            emitEvent(InferenceEvent.Finished(modelType))
        }
    }
}
