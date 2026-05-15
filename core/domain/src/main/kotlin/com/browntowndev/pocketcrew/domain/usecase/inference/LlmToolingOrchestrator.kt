@file:Suppress("MaxLineLength", "MagicNumber", "UnusedPrivateProperty", "UnusedParameter", "TooManyFunctions", "ComplexCondition", "LargeClass", "CyclomaticComplexMethod", "SwallowedException", "InstanceOfCheckForException", "ThrowsCount", "NestedBlockDepth", "ReturnCount", "LoopWithTooManyJumpStatements", "FunctionNaming", "LongMethod", "MatchingDeclarationName", "UnusedPrivateMember", "LongParameterList")
package com.browntowndev.pocketcrew.domain.usecase.inference

import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * Result of checking whether the context window is exceeded during a tool loop.
 * If mid-loop history rebuilding was performed, [rebuiltParams] contains the new params
 * built from the updated history. If rebuilding failed or was insufficient,
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

    private companion object {
        private val toolOutcomeJson = Json { ignoreUnknownKeys = true }
    }

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
     *   The second argument [allowToolCall] is false when the model has hit the recursion/cap budget,
     *   the context-full warning is active, or three consecutive turns returned only soft tool failures
     *   (executor returned JSON with an `error` field without throwing).
     * @param onToolCallDetected A lambda that extracts [ToolCallRequest]s from the response.
     *   Returns a list of tool call requests (empty if no tool calls were detected).
     * @param onToolResultsMapped A lambda that updates the parameters with all tool results from the turn.
     *   Called once per turn if tool calls were detected, receiving the current params, the response,
     *   and a list of pairs containing each [ToolCallRequest] and its corresponding result JSON.
     * @param onContextExceeded A lambda called after tool results are mapped to check if context window
     *   is past threshold. Returns a [ContextExceededResult] indicating whether context is exceeded
     *   (after any mid-loop history rebuilding), and optionally containing rebuilt params from the updated history.
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
        onContextExceeded: suspend (params: TParams, results: List<Pair<ToolCallRequest, String>>) -> ContextExceededResult<TParams> = { _, _ ->
            ContextExceededResult(false)
        },
        onToolResult: suspend (ToolCallRequest, String) -> Unit = { _, _ -> },
        onNoToolCallOnFirstPass: suspend (TResponse) -> Unit = {},
        onFinished: suspend (params: TParams, toolCallCount: Int, lastResponse: TResponse?) -> Unit
    ) {
        val pendingArtifactIds = mutableSetOf<String>()
        var lastArtifactToolCall: ToolCallRequest? = null
        val json = Json { ignoreUnknownKeys = true }

        var currentParams = initialParams
        var toolCallCount = 0
        var lastResponse: TResponse? = null
        var contextFullWarned = false
        var consecutiveSoftFailureTurns = 0

        // Effective limit that can expand for greedy tools
        var effectiveMaxToolCalls = maxToolCalls

        while (toolCallCount <= effectiveMaxToolCalls + 1) {
            val allowToolCall =
                toolCallCount < effectiveMaxToolCalls &&
                    !contextFullWarned &&
                    consecutiveSoftFailureTurns < 3
            val response = onInferencePass(currentParams, allowToolCall)
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

            // Expand limit for incremental generation tools
            if (toolCalls.any { it.toolName == ToolDefinition.APPEND_BLOCKS.name }) {
                effectiveMaxToolCalls = maxOf(effectiveMaxToolCalls, toolCallCount + 20)
            }

            // If we are already beyond the limit and the model is STILL calling tools,
            // then we have a loop and must throw a hard error.
            if (toolCallCount > effectiveMaxToolCalls) {
                loggingPort.error(
                    tag,
                    "Model ignored recursion limit warning provider=$providerName. Termination forced."
                )
                throw IllegalStateException(
                    "Search skill recursion limit exceeded: Model ignored warning to stop calling tools."
                )
            }

            // If context was already flagged as full and the model is STILL calling tools,
            // hard error — same pattern as recursion limit enforcement.
            if (contextFullWarned) {
                loggingPort.error(
                    tag,
                    "Model ignored context-full warning and called another tool " +
                        "provider=$providerName. Termination forced."
                )
                throw IllegalStateException(
                    "Context window exceeded: Model was warned to stop calling tools " +
                        "but continued. Try simplifying your request."
                )
            }

            val turnResults = mutableListOf<Pair<ToolCallRequest, String>>()
            var turnHadHardFailure = false
            var turnHadNonSoftOutcome = false
            for (toolCall in toolCalls) {
                val normalizedCall = toolCall.copy(toolName = toolCall.toolName.trim())
                var toolResult: ToolExecutionResult
                try {
                    toolResult =
                        if (toolCallCount >= effectiveMaxToolCalls) {
                            loggingPort.warning(
                                tag,
                                "Recursive tool call limit reached provider=$providerName tool=${normalizedCall.toolName}"
                            )
                            // Report limit as an error to the model instead of crashing
                            ToolExecutionResult(
                                toolName = normalizedCall.toolName,
                                resultJson = """{"error": "Recursion limit reached. You have called too many tools. """ +
                                    """Do not call any more tools. Provide a final response based on the """ +
                                    """information you already have."}"""
                            )
                        } else if (normalizedCall.toolName == "error_reporter") {
                            // This is our own synthetic error from extraction failure
                            ToolExecutionResult(normalizedCall.toolName, normalizedCall.argumentsJson)
                        } else {
                            ToolEnvelopeParser.requireSupportedTool(normalizedCall.toolName)
                            toolExecutor.execute(normalizedCall)
                        }
                } catch (e: Exception) {
                    turnHadHardFailure = true
                    val errorMessage = e.message ?: "Unknown error"
                    loggingPort.error(
                        tag,
                        "Tool execution failed provider=$providerName tool=${normalizedCall.toolName} error=$errorMessage",
                        e
                    )
                    // Return the error as the tool result so the LLM can see it and potentially self-correct
                    toolResult =
                        ToolExecutionResult(
                            toolName = normalizedCall.toolName,
                            resultJson = """{"error": "Tool execution failed: $errorMessage"}"""
                        )
                }

                if (!isSoftFailureResultJson(toolResult.resultJson)) {
                    turnHadNonSoftOutcome = true
                }

                // Increment count only for real tool call attempts or reported errors
                toolCallCount++

                loggingPort.info(
                    tag,
                    "Tool call processed provider=$providerName tool=${normalizedCall.toolName} " +
                        "iteration=$toolCallCount resultChars=${toolResult.resultJson.length}"
                )

                recordArtifactLoopProgress(
                    json = json,
                    pendingArtifactIds = pendingArtifactIds,
                    updateLastArtifact = { lastArtifactToolCall = it },
                    toolCall = normalizedCall,
                    resultJson = toolResult.resultJson,
                )

                onToolResult(normalizedCall, toolResult.resultJson)
                turnResults.add(normalizedCall to toolResult.resultJson)
            }

            when {
                turnHadHardFailure || turnHadNonSoftOutcome -> {
                    consecutiveSoftFailureTurns = 0
                }
                toolCalls.isNotEmpty() -> {
                    consecutiveSoftFailureTurns++
                    if (toolCalls.size >= 2 && consecutiveSoftFailureTurns >= 3) {
                        loggingPort.warning(
                            tag,
                            "validation retry exhausted provider=$providerName " +
                                "(repeated parallel tool calls returned only soft errors)",
                        )
                    }
                }
            }

            currentParams = onToolResultsMapped(currentParams, response, turnResults)

            // Check if context window is exceeded after adding tool results
            val contextResult = onContextExceeded(currentParams, turnResults)
            if (contextResult.rebuiltParams != null) {
                currentParams = contextResult.rebuiltParams
                loggingPort.info(tag, "Mid-loop history rebuilt params provider=$providerName")
            }
            if (contextResult.contextExceeded) {
                contextFullWarned = true
                loggingPort.warning(
                    tag,
                    "Context window threshold exceeded during tool loop " +
                        "provider=$providerName. Model will be warned to stop calling tools."
                )
            }
        }

        lastArtifactToolCall?.let { ctx ->
            if (pendingArtifactIds.isEmpty()) return@let
            for (artifactId in pendingArtifactIds.toList()) {
                loggingPort.warning(
                    tag,
                    "Auto-finalizing artifactId=$artifactId (model ended tool loop without " +
                        "append_blocks isComplete=true) provider=$providerName",
                )
                val argsJson =
                    buildJsonObject {
                        put("artifactId", JsonPrimitive(artifactId))
                        put("blocks", JsonArray(emptyList()))
                        put("isComplete", JsonPrimitive(true))
                    }.toString()
                val synth =
                    ToolCallRequest(
                        toolName = ToolDefinition.APPEND_BLOCKS.name,
                        argumentsJson = argsJson,
                        provider = ctx.provider,
                        modelType = ctx.modelType,
                        chatId = ctx.chatId,
                        userMessageId = ctx.userMessageId,
                        assistantMessageId = ctx.assistantMessageId,
                    )
                val synthResult =
                    try {
                        toolExecutor.execute(synth)
                    } catch (e: Exception) {
                        loggingPort.error(
                            tag,
                            "Auto-finalize append_blocks failed artifactId=$artifactId provider=$providerName",
                            e,
                        )
                        continue
                    }
                loggingPort.info(
                    tag,
                    "Auto-finalize processed artifactId=$artifactId resultChars=${synthResult.resultJson.length}",
                )
                onToolResult(synth, synthResult.resultJson)
            }
            pendingArtifactIds.clear()
        }

        onFinished(currentParams, toolCallCount, lastResponse)
    }

    private fun recordArtifactLoopProgress(
        json: Json,
        pendingArtifactIds: MutableSet<String>,
        updateLastArtifact: (ToolCallRequest) -> Unit,
        toolCall: ToolCallRequest,
        resultJson: String,
    ) {
        when (toolCall.toolName) {
            ToolDefinition.START_ARTIFACT.name -> {
                updateLastArtifact(toolCall)
                val id = extractArtifactIdFromStartSuccess(json, resultJson) ?: return
                pendingArtifactIds.add(id)
            }
            ToolDefinition.APPEND_BLOCKS.name -> {
                updateLastArtifact(toolCall)
                val complete = parseAppendBlocksSuccessIsComplete(json, resultJson) ?: return
                val artifactId = extractArtifactIdFromAppendArgs(json, toolCall.argumentsJson) ?: return
                if (complete) {
                    pendingArtifactIds.remove(artifactId)
                } else {
                    pendingArtifactIds.add(artifactId)
                }
            }
        }
    }

    private fun extractArtifactIdFromStartSuccess(json: Json, resultJson: String): String? =
        try {
            val o = json.parseToJsonElement(resultJson).jsonObject
            if (o["status"]?.jsonPrimitive?.content != "success") return null
            (o["artifactId"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

    private fun parseAppendBlocksSuccessIsComplete(json: Json, resultJson: String): Boolean? =
        try {
            val o = json.parseToJsonElement(resultJson).jsonObject
            if (o["status"]?.jsonPrimitive?.content != "success") return null
            when (val e = o["isComplete"]) {
                is JsonPrimitive ->
                    e.booleanOrNull == true ||
                        e.content.equals("true", ignoreCase = true) ||
                        e.content == "1"
                else -> false
            }
        } catch (_: Exception) {
            null
        }

    private fun extractArtifactIdFromAppendArgs(json: Json, argumentsJson: String): String? =
        try {
            val o = json.parseToJsonElement(argumentsJson).jsonObject
            (o["artifactId"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

    private fun isSoftFailureResultJson(resultJson: String): Boolean =
        try {
            when (val element = toolOutcomeJson.parseToJsonElement(resultJson)) {
                is JsonObject -> element.containsKey("error")
                else -> false
            }
        } catch (_: Exception) {
            false
        }
}
