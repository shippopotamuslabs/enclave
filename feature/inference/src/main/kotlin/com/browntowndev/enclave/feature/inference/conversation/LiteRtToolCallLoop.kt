package com.browntowndev.enclave.feature.inference.conversation

import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.AgentPolicyPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.usecase.chat.ToolSchemaDisclosureResolver
import com.browntowndev.enclave.domain.util.JTokkitTokenCounter
import com.browntowndev.enclave.feature.inference.LocalToolResultPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal class LiteRtToolCallLoop(
    private val cache: ConversationSignatureCache,
    private val toolExecutor: ToolExecutorPort,
    val loggingPort: LoggingPort,
    private val agentPolicyPort: AgentPolicyPort,
    private val cancellationGate: ConversationCancellationGate,
) {
    @Volatile
    private var consecutiveSoftFailures: Int = 0

    fun latestAssistantMessageId(): MessageId? = cache.latestAssistantMessageId

    suspend fun executeToolSafely(request: ToolCallRequest): String {
        if (cancellationGate.isCurrentGenerationCancelled) {
            loggingPort.warning(TAG, "Tool execution aborted: generation was cancelled")
            throw CancellationException("Generation was cancelled")
        }

        if (request.toolName == ToolDefinition.DISCLOSE_TOOL_SCHEMAS.name) {
            val parsedNames =
                runCatching {
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(request.argumentsJson)
                        .jsonObject["tool_names"]
                        ?.jsonArray
                        ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        ?: emptyList()
                }.getOrDefault(emptyList())
            return handleDisclosure(parsedNames)
        }

        val effectiveRequest =
            if (request.provider == "LITERT") {
                repairLiteRtToolRequest(request)
            } else {
                request
            }

        return try {
            loggingPort.debug(
                TAG,
                "executeToolSafely entered tool=${effectiveRequest.toolName} provider=${effectiveRequest.provider} " +
                    "modelType=${effectiveRequest.modelType}",
            )

            if (!agentPolicyPort.shouldAllowTool(consecutiveSoftFailures.toUInt(), ConversationSignatureCache.MAX_SOFT_FAILURE_STREAK)) {
                loggingPort.warning(
                    TAG,
                    "soft-failure streak reached limit; refusing tool dispatch tool=${effectiveRequest.toolName}",
                )
                consecutiveSoftFailures += 1
                return LocalToolResultPolicy.buildContextWindowExceededToolError(effectiveRequest.toolName)
            }

            val rawResultJson = toolExecutor.execute(effectiveRequest).resultJson
            val modelId = cache.currentModelPath?.substringAfterLast('/')
            val decision =
                LocalToolResultPolicy.evaluate(
                    rawResultJson = rawResultJson,
                    contextWindowTokens = cache.currentContextWindow,
                    currentSystemPrompt = cache.currentSystemPrompt,
                    history = cache.history,
                    transientToolResultTokens = cache.transientToolResultTokens,
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
                cache.transientToolResultTokens +=
                    JTokkitTokenCounter.countTokens(decision.finalResult, modelId) + TRANSIENT_TOOL_BUFFER_TOKENS
            }

            if (decision.contextFull) {
                loggingPort.warning(
                    TAG,
                    "Context nearly full after tool result. Truncating via Rust bridge and scheduling context recreation. " +
                        "tool=${effectiveRequest.toolName} usedTokens=${decision.totalTokens} window=${cache.currentContextWindow}",
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
     * Intercepts a `disclose_tool_schemas` request locally without invoking [ToolExecutorPort].
     * Updates [ConversationSignatureCache.activeToolNames] in first-seen order and returns
     * the canonical resolver JSON payload.
     */
    fun handleDisclosure(toolNames: List<String>): String {
        val activeNames = cache.activeToolNames
        val activeNameSet = activeNames.toSet()
        val deferredCatalog =
            cache.toolCatalog.filter { entry ->
                entry.name !in activeNameSet
            }
        val activeDefinitions = activeNames.mapNotNull(ToolDefinition::fromName)
        val resolution =
            disclosureResolver.resolve(
                toolNames = toolNames,
                toolCatalog = deferredCatalog,
                activeTools = activeDefinitions,
                strategy = cache.toolCallStrategy,
            )
        if (resolution.activatedToolNames.isNotEmpty()) {
            val seen = activeNames.toMutableList()
            resolution.activatedToolNames.forEach { newName ->
                if (newName !in seen) seen += newName
            }
            cache.activeToolNames = seen
        }
        return resolution.resultJson
    }

    private val disclosureResolver = ToolSchemaDisclosureResolver()

    private fun repairLiteRtToolRequest(request: ToolCallRequest): ToolCallRequest =
        try {
            val repaired = agentPolicyPort.repairTool(request.toolName, request.argumentsJson)
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
        consecutiveSoftFailures =
            if (softFailure) {
                consecutiveSoftFailures + 1
            } else {
                0
            }
    }

    companion object {
        private const val TAG = "ConversationManager"
        private const val TRANSIENT_TOOL_BUFFER_TOKENS = 30
    }
}

internal class ConversationCancellationGate {
    @Volatile
    var isCurrentGenerationCancelled: Boolean = false

    val cancellationEpoch = java.util.concurrent.atomic.AtomicInteger(0)

    fun cancelCurrentGeneration() {
        isCurrentGenerationCancelled = true
    }

    fun cancelProcess() {
        isCurrentGenerationCancelled = true
        cancellationEpoch.incrementAndGet()
    }

    fun resetForNewRequest() {
        isCurrentGenerationCancelled = false
    }
}
