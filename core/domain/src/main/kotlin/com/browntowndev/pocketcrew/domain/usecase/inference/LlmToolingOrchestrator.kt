package com.browntowndev.pocketcrew.domain.usecase.inference

import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
import javax.inject.Inject

/**
 * Result of checking whether the context window is exceeded during a tool loop.
 * If mid-loop compaction was performed, [rebuiltParams] contains the new params
 * built from the compacted history. If compaction failed or was insufficient,
 * [contextExceeded] remains true and the model will be warned to stop calling tools.
 */
data class ContextExceededResult<TParams>(
    val contextExceeded: Boolean,
    val rebuiltParams: TParams? = null,
)

/**
 * Encapsulates the core tool-calling loop logic shared across all LLM inference services.
 * This ensures consistent logging, recursion limits, and error handling while allowing
 * provider-specific mapping via lambdas.
 *
 * Supports parallel tool calls: when a model emits multiple function calls in a single
 * response, all are detected and executed sequentially within one loop iteration.
 * Each tool result is mapped via [onToolResultMapped] so providers can accumulate
 * multiple call/result pairs into the follow-up request parameters.
 *
 * Context-full enforcement mirrors the recursion limit pattern:
 * - [onContextExceeded] is called when a provider detects the context window is past threshold.
 * - The model receives a warning asking it to stop calling tools.
 * - If the model ignores the warning and calls another tool, a hard error is thrown.
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
     * @param onContextExceeded A lambda called after tool results are mapped to check if context window
     *   is past threshold. Returns a [ContextExceededResult] indicating whether context is exceeded
     *   (after any mid-loop compaction), and optionally containing rebuilt params from compacted history.
     * @param onToolResult A lambda called for each individual tool result (e.g., for source tracking).
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
        onContextExceeded: suspend (params: TParams, results: List<Pair<ToolCallRequest, String>>) -> ContextExceededResult<TParams> = { _, _ -> ContextExceededResult(false) },
        onToolResult: suspend (ToolCallRequest, String) -> Unit = { _, _ -> },
        onNoToolCallOnFirstPass: suspend (TResponse) -> Unit = {},
        onFinished: suspend (params: TParams, toolCallCount: Int, lastResponse: TResponse?) -> Unit
    ) {
        var currentParams = initialParams
        var toolCallCount = 0
        var lastResponse: TResponse? = null
        var contextFullWarned = false

        while (toolCallCount <= maxToolCalls + 1) {
            val response = onInferencePass(currentParams, toolCallCount < maxToolCalls && !contextFullWarned)
            lastResponse = response
            
            val toolCalls = try {
                onToolCallDetected(response)
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Extraction failed"
                loggingPort.error(tag, "Tool call extraction failed provider=$providerName error=$errorMessage", e)
                // If we can't even extract the tool call, we return a synthetic "error tool" 
                // so the LLM knows it messed up the envelope format.
                listOf(
                    ToolCallRequest(
                        toolName = "error_reporter",
                        argumentsJson = """{"error": "Failed to parse tool call envelope: $errorMessage"}""",
                        provider = providerName,
                        modelType = com.browntowndev.pocketcrew.domain.model.inference.ModelType.FAST // Dummy
                    )
                )
            }

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

            // If we are already beyond the limit and the model is STILL calling tools,
            // then we have a loop and must throw a hard error.
            if (toolCallCount > maxToolCalls) {
                loggingPort.error(tag, "Model ignored recursion limit warning provider=$providerName. Termination forced.")
                throw IllegalStateException("Search skill recursion limit exceeded: Model ignored warning to stop calling tools.")
            }

            // If context was already flagged as full and the model is STILL calling tools,
            // hard error — same pattern as recursion limit enforcement.
            if (contextFullWarned) {
                loggingPort.error(tag, "Model ignored context-full warning and called another tool provider=$providerName. Termination forced.")
                throw IllegalStateException("Context window exceeded: Model was warned to stop calling tools but continued. Try simplifying your request.")
            }

            val turnResults = mutableListOf<Pair<ToolCallRequest, String>>()
            for (toolCall in toolCalls) {
                val toolResult = try {
                    if (toolCallCount >= maxToolCalls) {
                        loggingPort.warning(
                            tag,
                            "Recursive tool call limit reached provider=$providerName tool=${toolCall.toolName}"
                        )
                        // Report limit as an error to the model instead of crashing
                        ToolExecutionResult(
                            toolName = toolCall.toolName,
                            resultJson = """{"error": "Recursion limit reached. You have called too many tools. Do not call any more tools. Provide a final response based on the information you already have."}"""
                        )
                    } else if (toolCall.toolName == "error_reporter") {
                        // This is our own synthetic error from extraction failure
                        ToolExecutionResult(toolCall.toolName, toolCall.argumentsJson)
                    } else {
                        ToolEnvelopeParser.requireSupportedTool(toolCall.toolName)
                        toolExecutor.execute(toolCall)
                    }
                } catch (e: Exception) {
                    val errorMessage = e.message ?: "Unknown error"
                    loggingPort.error(
                        tag,
                        "Tool execution failed provider=$providerName tool=${toolCall.toolName} error=$errorMessage",
                        e
                    )
                    // Return the error as the tool result so the LLM can see it and potentially self-correct
                    ToolExecutionResult(
                        toolName = toolCall.toolName,
                        resultJson = """{"error": "Tool execution failed: $errorMessage"}"""
                    )
                }

                // Increment count only for real tool call attempts or reported errors
                toolCallCount++

                loggingPort.info(
                    tag,
                    "Tool call processed provider=$providerName tool=${toolCall.toolName} iteration=$toolCallCount resultChars=${toolResult.resultJson.length}"
                )
                
                onToolResult(toolCall, toolResult.resultJson)
                turnResults.add(toolCall to toolResult.resultJson)
            }
            currentParams = onToolResultsMapped(currentParams, response, turnResults)

            // Check if context window is exceeded after adding tool results
            val contextResult = onContextExceeded(currentParams, turnResults)
            if (contextResult.rebuiltParams != null) {
                currentParams = contextResult.rebuiltParams
                loggingPort.info(tag, "Mid-loop compaction rebuilt params provider=$providerName")
            }
            if (contextResult.contextExceeded) {
                contextFullWarned = true
                loggingPort.warning(tag, "Context window threshold exceeded during tool loop provider=$providerName. Model will be warned to stop calling tools.")
            }
        }

        onFinished(currentParams, toolCallCount, lastResponse)
    }
}
