package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.inference.AgentContextReference
import com.browntowndev.enclave.domain.model.inference.AgentLoopConfig
import com.browntowndev.enclave.domain.model.inference.AgentLoopHostCallbacks
import kotlinx.coroutines.flow.Flow

/**
 * Portable agent loop API (Rust engine behind `:core:agent`).
 */
interface AgentLoopPort {
    fun execute(
        initialMessages: List<ChatMessage>,
        config: AgentLoopConfig,
        sessionContext: Map<String, String>,
        host: AgentLoopHostCallbacks,
    ): Flow<AgentLoopEvent>

    suspend fun requestManualCompact(request: AgentManualCompactRequest): AgentManualCompactResult =
        error("Manual compact requires a stateful agent loop session")

    suspend fun readContextReference(referenceId: String): String =
        error("Context reference retrieval requires a stateful agent loop session")

    suspend fun searchContextReferences(
        query: String,
        limit: Int,
    ): List<AgentContextReference> = emptyList()
}

data class AgentManualCompactRequest(
    val focusInstruction: String? = null,
    val forceSummary: Boolean = false,
)

data class AgentManualCompactResult(
    val summaryRef: String?,
    val historyRef: String?,
    val tokensBefore: Int,
    val tokensAfter: Int,
    val events: List<AgentLoopEvent> = emptyList(),
)

sealed interface AgentLoopEvent {
    data class TurnStarted(
        val index: Int,
    ) : AgentLoopEvent

    data class TurnCompleted(
        val index: Int,
        val toolCallCount: Int,
        val terminal: Boolean = false,
    ) : AgentLoopEvent

    data class TextToken(
        val text: String,
    ) : AgentLoopEvent

    data class ThinkingToken(
        val text: String,
    ) : AgentLoopEvent

    data class ToolCallExecuted(
        val toolName: String,
        val resultJson: String,
    ) : AgentLoopEvent

    data class CompactionApplied(
        val tier: String,
        val tokensBefore: Int,
        val tokensAfter: Int,
        val trigger: String,
    ) : AgentLoopEvent

    data class ToolRepaired(
        val originalName: String,
        val resolvedName: String,
        val confidence: Float,
    ) : AgentLoopEvent

    data class KvCacheInvalidated(
        val reason: String,
    ) : AgentLoopEvent

    data class PreCompact(
        val trigger: String,
    ) : AgentLoopEvent

    data class PostCompact(
        val trigger: String,
        val summaryRef: String?,
        val historyRef: String?,
        val transcriptRevisionAfter: Long,
    ) : AgentLoopEvent

    /** Emitted when Rust records a destructive compaction rewrite (transcript revision bumped). */
    data class CompactionBoundaryApplied(
        val revisionAfter: Long,
    ) : AgentLoopEvent

    data object Finished : AgentLoopEvent

    data class Failed(
        val message: String,
    ) : AgentLoopEvent

    /** Rust scheduled a transient model API retry for the in-flight model turn. */
    data class ModelApiRetryScheduled(
        val attempt: Int,
        val maxAttempts: Int,
        val delayMs: Long,
        val reason: String,
    ) : AgentLoopEvent
}
