package com.browntowndev.pocketcrew.domain.usecase.inference

import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
import javax.inject.Inject

/**
 * Encapsulates the core tool-calling loop logic shared across all LLM inference services.
 * This ensures consistent logging, recursion limits, and error handling while allowing
 * provider-specific mapping via lambdas.
 *
 * Supports parallel tool calls: when a model emits multiple function calls in a single
 * response, all are detected and executed sequentially within one loop iteration.
 * Each tool result is mapped via [onToolResultMapped] so providers can accumulate
 * multiple call/result pairs into the follow-up request parameters.
 */
class LlmToolingOrchestrator @Inject constructor(
    val toolExecutor: ToolExecutorPort,
    private val loggingPort: LoggingPort,
) {

    /**
     * Executes the tool-calling loop.
     *
     * @param TParams The type of parameters used to send the request (e.g., ResponseCreateParams, MessageCreateParams).
     * @param TResponse The type of response returned from the provider.
     * @param providerName The name of the provider (e.g., OPENAI, ANTHROPIC).
     * @param initialParams The initial request parameters.
     * @param tag The logging tag for the service.
     * @param maxToolCalls The maximum number of tool calls allowed (recursion limit).
     * @param onInferencePass A lambda that performs the actual API or Local call.
     * @param onToolCallDetected A lambda that extracts [ToolCallRequest]s from the response.
     *   Returns a list of tool call requests (empty if no tool calls were detected).
     * @param onToolResultsMapped A lambda that updates the parameters with all tool results from the turn.
     *   Called once per turn if tool calls were detected, receiving the current params, the response,
     *   and a list of pairs containing each [ToolCallRequest] and its corresponding result JSON.
     * @param onNoToolCallOnFirstPass An optional lambda called if the first pass contains no tool calls.
     * @param onFinished A lambda called after the loop finishes.
     */
    suspend fun <TParams, TResponse> execute(
        providerName: String,
        initialParams: TParams,
        tag: String,
        maxToolCalls: Int = 20,
        onInferencePass: suspend (params: TParams, allowToolCall: Boolean) -> TResponse,
        onToolCallDetected: (TResponse) -> List<ToolCallRequest>,
        onToolResultsMapped: suspend (params: TParams, response: TResponse, results: List<Pair<ToolCallRequest, String>>) -> TParams,
        onToolResult: suspend (ToolCallRequest, String) -> Unit = { _, _ -> },
        onNoToolCallOnFirstPass: suspend (TResponse) -> Unit = {},
        onFinished: suspend (params: TParams, toolCallCount: Int, lastResponse: TResponse?) -> Unit
    ) {
        var currentParams = initialParams
        var toolCallCount = 0
        var lastResponse: TResponse? = null

        while (true) {
            val response = onInferencePass(currentParams, toolCallCount < maxToolCalls)
            lastResponse = response
            val toolCalls = onToolCallDetected(response)

            if (toolCalls.isEmpty()) {
                if (toolCallCount == 0) {
                    loggingPort.info(
                        tag,
                        "Tool loop complete without tool call provider=$providerName"
                    )
                    onNoToolCallOnFirstPass(response)
                }
                break
            }

            val turnResults = mutableListOf<Pair<ToolCallRequest, String>>()
            for (toolCall in toolCalls) {
                if (toolCallCount >= maxToolCalls) {
                    loggingPort.warning(
                        tag,
                        "Recursive tool call limit reached provider=$providerName tool=${toolCall.toolName}"
                    )
                    throw IllegalStateException("Search skill recursion limit exceeded")
                }

                toolCallCount++
                ToolEnvelopeParser.requireSupportedTool(toolCall.toolName)
                val toolArg = runCatching {
                    toolCall.argumentsJson.take(100).replace("\n", " ")
                }.getOrDefault("<unknown>")

                loggingPort.info(
                    tag,
                    "Tool call detected provider=$providerName tool=${toolCall.toolName} arg=$toolArg iteration=$toolCallCount"
                )
                val toolResult = toolExecutor.execute(toolCall)
                loggingPort.info(
                    tag,
                    "Tool call completed provider=$providerName tool=${toolCall.toolName} resultChars=${toolResult.resultJson.length}"
                )
                
                onToolResult(toolCall, toolResult.resultJson)
                turnResults.add(toolCall to toolResult.resultJson)
            }
            currentParams = onToolResultsMapped(currentParams, response, turnResults)
        }

        onFinished(currentParams, toolCallCount, lastResponse)
    }
}