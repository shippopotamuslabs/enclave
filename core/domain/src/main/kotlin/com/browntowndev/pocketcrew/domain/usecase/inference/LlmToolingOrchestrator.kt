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
     * @param onToolCallDetected A lambda that extracts a [ToolCallRequest] from the response.
     * @param onToolResultMapped A lambda that updates the parameters with the tool result.
     * @param onNoToolCallOnFirstPass An optional lambda called if the first pass contains no tool calls.
     * @param onFinished A lambda called after the loop finishes.
     */
    suspend fun <TParams, TResponse> execute(
        providerName: String,
        initialParams: TParams,
        tag: String,
        maxToolCalls: Int = 30,
        onInferencePass: suspend (params: TParams, allowToolCall: Boolean) -> TResponse,
        onToolCallDetected: (TResponse) -> ToolCallRequest?,
        onToolResultMapped: suspend (params: TParams, response: TResponse, resultJson: String) -> TParams,
        onNoToolCallOnFirstPass: suspend (TResponse) -> Unit = {},
        onFinished: suspend (params: TParams, toolCallCount: Int, lastResponse: TResponse?) -> Unit
    ) {
        var currentParams = initialParams
        var toolCallCount = 0
        var lastResponse: TResponse? = null

        while (true) {
            val response = onInferencePass(currentParams, toolCallCount < maxToolCalls)
            lastResponse = response
            val toolCall = onToolCallDetected(response)

            if (toolCall == null) {
                if (toolCallCount == 0) {
                    loggingPort.info(
                        tag,
                        "Tool loop complete without tool call provider=$providerName"
                    )
                    onNoToolCallOnFirstPass(response)
                }
                break
            }

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

            currentParams = onToolResultMapped(currentParams, response, toolResult.resultJson)
        }

        onFinished(currentParams, toolCallCount, lastResponse)
    }
}
