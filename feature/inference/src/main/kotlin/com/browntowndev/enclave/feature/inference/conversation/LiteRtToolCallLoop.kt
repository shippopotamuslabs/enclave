package com.browntowndev.enclave.feature.inference.conversation

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.inference.AgentLoopToolCatalogJson
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.AgentPolicyPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.usecase.chat.SearchToolsHostCoordinator
import com.browntowndev.enclave.domain.util.JTokkitTokenCounter
import com.browntowndev.enclave.feature.inference.LocalToolResultPolicy
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicInteger

internal class LiteRtToolCallLoop(
    private val cache: ConversationSignatureCache,
    private val toolExecutor: ToolExecutorPort,
    val loggingPort: LoggingPort,
    private val agentPolicyPort: AgentPolicyPort,
    private val cancellationGate: ConversationCancellationGate,
    private val searchToolsHostCoordinator: SearchToolsHostCoordinator,
) {
    private val consecutiveSoftFailures = AtomicInteger(0)

    fun resetForNewTurn() {
        consecutiveSoftFailures.set(0)
    }

    suspend fun executeToolSafely(
        request: ToolCallRequest,
        turnContext: LiteRtToolContext,
    ): String {
        if (!cancellationGate.isRequestActive(turnContext.turn.requestId)) {
            loggingPort.warning(TAG, "Tool execution aborted: generation was cancelled")
            cache.markConversationDirtyForDanglingToolCall()
            throw CancellationException("Generation was cancelled")
        }

        if (request.toolName == ToolDefinition.SEARCH_TOOLS.name) {
            return handleToolSearch(
                argumentsJson = request.argumentsJson,
                chatId = request.chatId,
                turnContext = turnContext,
            )
        }

        val effectiveRequest =
            if (request.provider == "LITERT") {
                repairLiteRtToolRequest(request, turnContext.turn.toolCatalog)
            } else {
                request
            }

        return try {
            loggingPort.debug(
                TAG,
                "executeToolSafely entered tool=${effectiveRequest.toolName} provider=${effectiveRequest.provider} " +
                    "modelType=${effectiveRequest.modelType}",
            )

            if (
                !agentPolicyPort.shouldAllowTool(
                    consecutiveSoftFailures.get().toUInt(),
                    ConversationSignatureCache.MAX_SOFT_FAILURE_STREAK,
                )
            ) {
                loggingPort.warning(
                    TAG,
                    "soft-failure streak reached limit; refusing tool dispatch tool=${effectiveRequest.toolName}",
                )
                return LocalToolResultPolicy.buildContextWindowExceededToolError(effectiveRequest.toolName)
            }

            val rawResultJson = toolExecutor.execute(effectiveRequest).resultJson
            val turn = turnContext.turn
            val modelId = turn.modelPath?.substringAfterLast('/')
            val decision =
                LocalToolResultPolicy.evaluate(
                    rawResultJson = rawResultJson,
                    contextWindowTokens = turn.contextWindow,
                    currentSystemPrompt = turn.systemPrompt,
                    history = turn.history,
                    transientToolResultTokens = turnContext.turnTransientToolResultTokens.get(),
                    modelId = modelId,
                    resultJsonLooksSoftFailure = agentPolicyPort::resultJsonLooksSoftFailure,
                )
            updateSoftFailureStreak(decision.hasErrorPayload)

            loggingPort.debug(
                TAG,
                "Native tool call completed tool=${effectiveRequest.toolName} finalPayload=${decision.finalResult.take(
                    ConversationSignatureCache.TOOL_RESULT_TRUNCATE_LENGTH,
                )}",
            )

            if (decision.shouldTrackTransientToolResult) {
                cache.hasTransientToolResults = true
                turnContext.turnTransientToolResultTokens.addAndGet(
                    JTokkitTokenCounter.countTokens(decision.finalResult, modelId) + TRANSIENT_TOOL_BUFFER_TOKENS,
                )
            }

            if (decision.contextFull) {
                loggingPort.warning(
                    TAG,
                    "Context nearly full after tool result. Truncating via Rust bridge and scheduling context recreation. " +
                        "tool=${effectiveRequest.toolName} usedTokens=${decision.totalTokens} window=${turn.contextWindow}",
                )
                cache.hasTransientToolResults = true
                return agentPolicyPort.truncateResult(
                    decision.finalResult,
                    ConversationSignatureCache.CONTEXT_FULL_RESULT_BUDGET_BYTES,
                )
            }

            if (decision.hasErrorPayload) {
                loggingPort.error(
                    TAG,
                    "Native tool call returned error payload tool=${effectiveRequest.toolName} " +
                        "provider=${effectiveRequest.provider} modelType=${effectiveRequest.modelType} " +
                        "payload=${decision.finalResult.take(ConversationSignatureCache.TOOL_RESULT_TRUNCATE_LENGTH)}",
                )
            }
            decision.finalResult
        } catch (e: CancellationException) {
            cache.markConversationDirtyForDanglingToolCall()
            throw e
        } catch (t: Throwable) {
            val rootCause = ConversationErrorMapper.rootCause(t)
            loggingPort.error(
                TAG,
                "Native tool call failed tool=${effectiveRequest.toolName} " +
                    "cause=${rootCause::class.java.simpleName}: ${rootCause.message}",
                rootCause,
            )
            updateSoftFailureStreak(softFailure = true)
            ConversationErrorMapper.executionFailed(effectiveRequest.toolName, rootCause)
        }
    }

    /**
     * Intercepts `search_tools` locally without invoking [ToolExecutorPort].
     * Updates [ConversationSignatureCache.toolActivationRegistry] (single activation source).
     */
    suspend fun handleToolSearch(
        argumentsJson: String,
        chatId: ChatId? = null,
        turnContext: LiteRtToolContext,
    ): String =
        try {
            val turn = turnContext.turn
            val input =
                SearchToolsHostCoordinator.inputFrom(
                    argumentsJson = argumentsJson,
                    toolCatalog = turn.toolCatalog,
                    activationRegistry = cache.toolActivationRegistry,
                    providedIndex = turn.toolSearchIndex,
                    strategy = turn.toolCallStrategy,
                    chatId = chatId,
                )
            searchToolsHostCoordinator.execute(input).resultJson
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val rootCause = ConversationErrorMapper.rootCause(t)
            loggingPort.error(
                TAG,
                "search_tools host failed cause=${rootCause::class.java.simpleName}: ${rootCause.message}",
                rootCause,
            )
            ConversationErrorMapper.executionFailed(ToolDefinition.SEARCH_TOOLS.name, rootCause)
        }

    private fun repairLiteRtToolRequest(
        request: ToolCallRequest,
        toolCatalog: List<com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry>,
    ): ToolCallRequest =
        try {
            val repaired =
                agentPolicyPort.repairTool(
                    name = request.toolName,
                    argsJson = request.argumentsJson,
                    toolCatalogJson = AgentLoopToolCatalogJson.jsonForCatalog(toolCatalog),
                )
            request.copy(toolName = repaired.resolvedName, argumentsJson = repaired.argumentsJson)
        } catch (t: Throwable) {
            val rootCause = ConversationErrorMapper.rootCause(t)
            loggingPort.warning(
                TAG,
                "LiteRT tool repair unavailable; using raw tool call tool=${request.toolName} " +
                    "cause=${rootCause::class.java.simpleName}: ${rootCause.message}",
            )
            request
        }

    private fun updateSoftFailureStreak(softFailure: Boolean) {
        if (softFailure) {
            consecutiveSoftFailures.incrementAndGet()
        } else {
            consecutiveSoftFailures.set(0)
        }
    }

    companion object {
        private const val TAG = "ConversationManager"
        private const val TRANSIENT_TOOL_BUFFER_TOKENS = 30
    }
}

internal class ConversationCancellationGate {
    private val currentRequestId = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    var isCurrentGenerationCancelled: Boolean = false

    val cancellationEpoch =
        java.util.concurrent.atomic
            .AtomicInteger(0)

    fun beginRequest(): Int {
        val requestId = currentRequestId.incrementAndGet()
        isCurrentGenerationCancelled = false
        return requestId
    }

    fun currentRequestId(): Int = currentRequestId.get()

    fun prepareForGetConversation() {
        isCurrentGenerationCancelled = false
    }

    fun cancelCurrentGeneration() {
        isCurrentGenerationCancelled = true
    }

    fun cancelCurrentGenerationIfOwner(requestId: Int) {
        if (currentRequestId.get() == requestId) {
            isCurrentGenerationCancelled = true
        }
    }

    fun cancelProcess() {
        isCurrentGenerationCancelled = true
        cancellationEpoch.incrementAndGet()
    }

    fun isRequestActive(requestId: Int): Boolean =
        currentRequestId.get() == requestId && !isCurrentGenerationCancelled
}
