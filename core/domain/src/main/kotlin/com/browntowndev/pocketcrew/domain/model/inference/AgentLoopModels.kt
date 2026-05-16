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
    val maxTurns: UInt = 20u,
    val thresholdRatio: Float = 0.80f,
    val microKeepRecent: UInt = 2u,
    val toolRetryLimit: UInt = 3u,
    val greedyAppendBlocks: Boolean = true,
    val toolResultBudgetCloudBytes: ULong = 4096uL,
)

data class AgentLoopSessionCapabilities(
    val summarizationAllowed: Boolean = true,
    val kvFixed: Boolean = false,
    val toolResultBudgetTokens: UInt? = null,
)

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

data class AgentModelTurnResult(
    val assistantText: String?,
    val toolCalls: List<AgentRawToolCall>,
    val finishReason: AgentFinishReason,
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

    data class Other(val reason: String) : AgentFinishReason()
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
)

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
        onTextToken: suspend (String) -> Unit = {},
        onThinkingToken: suspend (String) -> Unit = {},
    ): AgentModelTurnResult

    suspend fun executeTools(calls: List<AgentToolCall>): List<AgentToolResult>

    // Invoked after each tool result for UI side effects (web search sources, artifacts, media).
    suspend fun onToolResult(toolName: String, argumentsJson: String, resultJson: String) {}

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
