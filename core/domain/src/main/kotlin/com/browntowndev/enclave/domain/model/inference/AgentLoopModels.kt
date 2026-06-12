package com.browntowndev.enclave.domain.model.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role

/**
 * Configuration for a Rust-backed agent session (maps to `FfiAgentConfig` + capabilities + IO).
 */
data class AgentLoopConfig(
    val agent: AgentLoopAgentConfig = AgentLoopAgentConfig(),
    val capabilities: AgentLoopSessionCapabilities = AgentLoopSessionCapabilities(),
    val io: AgentLoopSessionIoParams,
)

data class AgentLoopAgentConfig(
    val maxTurns: UInt = 20u,
    val thresholdRatio: Float = 0.80f,
    val microKeepRecent: UInt = 4u,
    val toolRetryLimit: UInt = 3u,
    val modelApiRetryLimit: UInt = 3u,
    val modelApiRetryBaseDelayMs: ULong = 1_000uL,
    val toolResultBudgetCloudBytes: ULong = 16384uL,
    val toolResultBudgetRelaxedBytes: ULong = 131072uL,
    val toolResultTruncationPressureRatio: Float = 0.85f,
    val midTurnThresholdRatio: Float = 0.93f,
    val postToolResultThresholdRatio: Float = 0.92f,
    val manualCompactThresholdRatio: Float = 0.80f,
    val allowFifoOnPreflight: Boolean = true,
    val allowFifoOnMidTurn: Boolean = false,
    val allowFifoOnPostToolResult: Boolean = false,
    val requireSnapshotBeforeFifo: Boolean = true,
    val enableContextStore: Boolean = true,
    val enableStateLedger: Boolean = true,
    val enableToolSchemaDeferral: Boolean = true,
    val enableFileCondensation: Boolean = true,
    val enableManualCompact: Boolean = true,
    val toolSpillThresholdBytes: ULong = 8192uL,
    val terminalSpillThresholdBytes: ULong = 8192uL,
    val historySnapshotThresholdTokens: UInt = 1024u,
    val fileOutlineThresholdTokens: UInt = 2048u,
    val promptCacheAnchorThresholdTokens: UInt = 0u,
    val outputReserveRatio: Float = 0.05f,
    val outputReserveFloorTokens: UInt = 4096u,
    val outputReserveMaxTokens: UInt = 16384u,
    val preflightTokenThreshold: UInt? = null,
    val midTurnTokenThreshold: UInt? = null,
    val postToolResultTokenThreshold: UInt? = null,
    val messageThreshold: UInt? = null,
    val retentionWindowTokens: UInt = 0u,
    val maxModelRequestsPerTurn: UInt = 1u,
    val enableLoopHooks: Boolean = false,
    val enablePendingWorkGuard: Boolean = false,
    val longLoop: AgentLoopLongLoopPolicy = AgentLoopLongLoopPolicy(),
    /** Full [`ToolSemanticsConfig`] JSON; catalog tool specs are merged from [AgentLoopSessionIoParams.toolCatalogJson]. */
    val toolSemanticsJson: String = AgentLoopToolSemanticsResources.enclaveDefaultJson,
)

data class AgentLoopToolSemanticsConfig(
    val discovery: AgentLoopToolDiscoverySemantics?,
    val artifact: AgentLoopArtifactToolSemantics?,
    val greedyExtensionTools: List<AgentLoopToolGreedyExtensionSemantics>,
) {
    companion object {
        fun agnostic(): AgentLoopToolSemanticsConfig =
            AgentLoopToolSemanticsConfig(
                discovery = null,
                artifact = null,
                greedyExtensionTools = emptyList(),
            )

        fun enclaveDefault(): AgentLoopToolSemanticsConfig =
            AgentLoopToolSemanticsConfig(
                discovery =
                    AgentLoopToolDiscoverySemantics(
                        toolName = "tool_discovery_tool",
                        requestFieldName = "capability",
                        alwaysCallable = true,
                        companionCallableTools = listOf("read_context_reference"),
                    ),
                artifact =
                    AgentLoopArtifactToolSemantics(
                        startToolName = "start_artifact",
                        appendToolName = "append_blocks",
                        idField = "artifactId",
                        appendBlocksField = "blocks",
                        completionField = "isComplete",
                    ),
                greedyExtensionTools =
                    listOf(
                        AgentLoopToolGreedyExtensionSemantics(
                            toolName = "append_blocks",
                            extraTurns = 20u,
                        ),
                    ),
            )
    }
}

data class AgentLoopToolDiscoverySemantics(
    val toolName: String,
    val requestFieldName: String,
    val alwaysCallable: Boolean,
    val companionCallableTools: List<String> = emptyList(),
)

data class AgentLoopArtifactToolSemantics(
    val startToolName: String,
    val appendToolName: String,
    val idField: String,
    val appendBlocksField: String,
    val completionField: String,
)

data class AgentLoopToolGreedyExtensionSemantics(
    val toolName: String,
    val extraTurns: UInt,
)

data class AgentLoopLongLoopPolicy(
    val maxToolTurns: UInt = 40u,
    val sidecarByteBudgetPerCall: ULong = 8192uL,
    val sidecarTotalByteBudget: ULong = 16384uL,
    val summarizeAfterToolTurns: UInt = 0u,
    val compactEveryNToolTurns: UInt = 0u,
)

data class AgentLoopSessionCapabilities(
    val summarizationAllowed: Boolean = true,
    val kvFixed: Boolean = false,
    val toolResultBudgetTokens: UInt? = null,
    val contextPolicyTier: AgentLoopContextPolicyTier = AgentLoopContextPolicyTier.HOSTED_MUTABLE,
    val toolSchemaDeferralSupported: Boolean = false,
    val fileBackedContextSupported: Boolean = false,
    val promptCacheSupported: Boolean = false,
    val historyRewriteSupported: Boolean = true,
    val lifecycleHooksSupported: Boolean = false,
)

enum class AgentLoopContextPolicyTier {
    HOSTED_MUTABLE,
    HOSTED_PROXY,
    LOCAL_MUTABLE,
    FIXED_KV,
    HIGH_CONTEXT,
    SMALL_CONTEXT,
}

data class AgentLoopSessionIoParams(
    val provider: String,
    val contextWindowTokens: UInt,
    val toolsSchemaJson: String,
    val toolCatalogJson: String = "[]",
    val toolSchemaRegistryJson: String = "[]",
    val systemPrompt: String,
)

data class AgentLoopMessage(
    val id: String,
    val role: Role,
    val content: String,
    val metadataJson: String = "null",
)

data class AgentContextReference(
    val id: String,
    val kind: String,
    val title: String,
    val byteLen: ULong,
    val tokenEstimate: UInt,
    val preview: String,
    val retrievalHint: String,
)

data class AgentModelTurnResult(
    val assistantText: String?,
    val toolCalls: List<AgentRawToolCall>,
    val finishReason: AgentFinishReason,
    val providerReasoningContent: String? = null,
    val telemetry: com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry? = null,
)

data class AgentRawToolCall(
    val id: String?,
    val name: String,
    val argumentsJson: String,
)

sealed class AgentFinishReason {
    data object ToolUse : AgentFinishReason()

    data object Stop : AgentFinishReason()

    data object MaxTokens : AgentFinishReason()

    data class Other(
        val reason: String,
    ) : AgentFinishReason()
}

data class AgentToolCall(
    val callId: String,
    val name: String,
    val argumentsJson: String,
)

data class AgentToolResult(
    val callId: String,
    val resultJson: String,
    val truncated: Boolean,
    val softFailure: Boolean,
    val activatedToolNames: List<String> = emptyList(),
)

data class AgentSummaryRequest(
    val messagesToSummarize: List<AgentLoopMessage>,
    val instruction: String,
    val lastMessageId: String,
    val trigger: AgentLoopCompactionTrigger = AgentLoopCompactionTrigger.PREFLIGHT,
    val focusInstruction: String? = null,
    val historyRef: String? = null,
    val stateLedgerSnapshotJson: String? = null,
)

enum class AgentLoopCompactionTrigger {
    PREFLIGHT,
    MID_TURN,
    POST_TOOL_RESULT,
    MANUAL,
}

data class AgentSummaryResult(
    val summaryText: String,
    val lastMessageId: String,
)

/**
 * Host-side hooks for model, tool, and optional summary tiers.
 * Implementations must not block the Android main thread.
 */
interface AgentLoopHostCallbacks {
    suspend fun completeModelTurn(
        sessionMessages: List<AgentLoopMessage>,
        systemPrompt: String,
        toolsSchemaJson: String,
        allowToolCall: Boolean,
        contextReferences: List<AgentContextReference> = emptyList(),
        stateLedgerJson: String? = null,
        toolContextIndexJson: String? = null,
        fileContextIndexJson: String? = null,
        cacheHints: List<String> = emptyList(),
        transcriptRevision: Long = 0L,
        onTextToken: suspend (String) -> Unit = {},
        onThinkingToken: suspend (String) -> Unit = {},
    ): AgentModelTurnResult

    suspend fun executeTools(calls: List<AgentToolCall>): List<AgentToolResult>

    // Invoked after each tool result for UI side effects (web search sources, artifacts, media).
    suspend fun onToolResult(
        toolName: String,
        argumentsJson: String,
        resultJson: String,
    ) {}

    suspend fun summarize(request: AgentSummaryRequest): AgentSummaryResult {
        error("Summarization requested but host did not implement summarize()")
    }
}

/**
 * Maps [ChatMessage] to engine messages with stable synthetic ids.
 */
fun List<ChatMessage>.toAgentLoopMessages(): List<AgentLoopMessage> =
    mapIndexed { index, message ->
        AgentLoopMessage(
            id = "chat-$index",
            role = message.role,
            content = message.content,
            metadataJson = message.metadataJson,
        )
    }
