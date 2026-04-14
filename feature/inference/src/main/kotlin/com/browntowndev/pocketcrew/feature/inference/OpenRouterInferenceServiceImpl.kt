package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
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
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        initialResponse: StreamedOpenAiResponse,
        toolResultJson: String,
    ): ResponseCreateParams {
        val functionCall = initialResponse.functionCall
            ?: throw IllegalStateException("Missing function call payload for OpenRouter follow-up")
        val callId = initialResponse.providerToolCallId
            ?: throw IllegalStateException("Missing function call id for OpenRouter follow-up")
        val functionItemId = initialResponse.providerToolItemId ?: "fc_${callId.sanitizeForOpenRouterId()}"
        val builder = ResponseCreateParams.builder()
            .model(modelId)
        val inputItems = mutableListOf<ResponseInputItem>()
        val systemMessages = mutableListOf<String>()

        requestHistory.forEach { message ->
            if (message.role == Role.SYSTEM) {
                systemMessages += message.content
            } else {
                inputItems += ResponseInputItem.ofMessage(
                    ResponseInputItem.Message.builder()
                        .role(ResponseInputItem.Message.Role.of(message.role.name.lowercase()))
                        .addInputTextContent(message.content)
                        .build()
                )
            }
        }

        inputItems += ResponseInputItem.ofMessage(
            ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.of("user"))
                .addInputTextContent(prompt)
                .build()
        )

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

        inputItems += ResponseInputItem.ofFunctionCall(
            ResponseFunctionToolCall.builder()
                .id(functionItemId)
                .callId(callId)
                .name(functionCall.toolName)
                .arguments(functionCall.argumentsJson)
                .build()
        )
        inputItems += ResponseInputItem.ofFunctionCallOutput(
            ResponseInputItem.FunctionCallOutput.builder()
                .id("fco_${callId.sanitizeForOpenRouterId()}")
                .callId(callId)
                .output(toolResultJson)
                .build()
        )

        builder.inputOfResponse(inputItems)
        systemMessages.joinToString(separator = "\n\n")
            .takeIf { it.isNotBlank() }
            ?.let { builder.instructions(it) }
        options.reasoningEffort?.let { builder.reasoning(ReasoningMapper.toSdkReasoning(it)) }
        options.temperature?.let { builder.temperature(it.toDouble()) }
        options.topP?.let { builder.topP(it.toDouble()) }
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
