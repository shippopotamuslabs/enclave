package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.usecase.inference.LlmToolingOrchestrator
import com.browntowndev.pocketcrew.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.errors.BadRequestException
import com.openai.models.responses.ResponseFunctionToolCall
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputItem

class OpenRouterInferenceServiceImpl(
    client: OpenAIClient,
    modelId: String,
    modelType: ModelType,
    private val routing: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration(),
    baseUrl: String? = null,
    loggingPort: LoggingPort,
    orchestrator: LlmToolingOrchestrator,
) : BaseOpenAiSdkInferenceService(
    client = client,
    modelId = modelId,
    provider = "OPENROUTER",
    modelType = modelType,
    baseUrl = baseUrl,
    loggingPort = loggingPort,
    orchestrator = orchestrator,
) {

    override val tag: String = "OpenRouterInferenceService"

    override fun mapToolingResponseParams(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
    ): ResponseCreateParams =
        OpenRouterRequestMapper.mapToResponseParams(
            modelId = modelId,
            prompt = prompt,
            history = requestHistory,
            options = options,
            routing = routing,
        )

    override fun mapToolingFollowUpResponseParams(
        currentParams: ResponseCreateParams,
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        initialResponse: StreamedOpenAiResponse,
        results: List<Pair<ToolCallRequest, String>>,
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
                    throw IllegalStateException("Missing function call id at index $index for OpenRouter follow-up")
                }
            } else {
                "fallback_${java.util.UUID.randomUUID().toString().replace("-", "")}"
            }

            val functionItemId = if (index != -1) {
                initialResponse.providerToolItemIds.getOrElse(index) {
                    "fc_${callId.sanitizeForOpenRouterId()}"
                }
            } else {
                "fc_${callId}"
            }

            inputItems += ResponseInputItem.ofFunctionCall(
                ResponseFunctionToolCall.builder()
                    .id(functionItemId)
                    .callId(callId)
                    .name(toolCall.toolName)
                    .arguments(toolCall.argumentsJson)
                    .build()
            )
            inputItems += ResponseInputItem.ofFunctionCallOutput(
                ResponseInputItem.FunctionCallOutput.builder()
                    .id("fco_${callId.sanitizeForOpenRouterId()}")
                    .callId(callId)
                    .output(resultJson)
                    .build()
            )
        }

        builder.inputOfResponse(inputItems)
        options.safeOpenRouterFollowUpMaxTokens()?.let { builder.maxOutputTokens(it.toLong()) }
        OpenRouterRequestMapper.applyRoutingDefaults(builder, routing)
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
            streamResponses(
                params = responseParams,
                emitEvent = emitEvent,
            )
            emitEvent(InferenceEvent.Finished(modelType))
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
            emitEvent(InferenceEvent.Finished(modelType))
        }
    }

    private fun GenerationOptions.safeOpenRouterFollowUpMaxTokens(): Int? {
        val configuredMaxTokens = maxTokens ?: return null
        val configuredContextWindow = contextWindow
        if (configuredContextWindow != null && configuredMaxTokens >= configuredContextWindow) {
            return null
        }
        return configuredMaxTokens
    }

    private fun String.sanitizeForOpenRouterId(): String =
        replace(Regex("[^A-Za-z0-9_-]"), "_")
}
