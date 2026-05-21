package com.browntowndev.pocketcrew.domain.model.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role

/**
 * Configuration for a Rust-backed agent session (maps to `FfiAgentConfig` + capabilities + IO).
 */
data class AgentLoopConfig(
    val agent: AgentLoopAgentConfig = AgentLoopAgentConfig(),
    val capabilities: AgentLoopSessionCapabilities = AgentLoopSessionCapabilities(),
    val io: AgentLoopSessionIoParams,
)

data class AgentLoopAgentConfig(
    val maxTurns: UInt = 50u,
    val thresholdRatio: Float = 0.80f,
    val microKeepRecent: UInt = 2u,
    val toolRetryLimit: UInt = 3u,
    val greedyAppendBlocks: Boolean = true,
    val toolResultBudgetCloudBytes: ULong = 4096uL,
    val midTurnThresholdRatio: Float = 0.90f,
    val postToolResultThresholdRatio: Float = 0.85f,
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
    val promptCacheAnchorThresholdTokens: UInt = 4096u,
    val maxModelRequestsPerTurn: UInt = 1u,
    val enableLoopHooks: Boolean = false,
    val enablePendingWorkGuard: Boolean = false,
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
            metadataJson = "null",
        )
    }
