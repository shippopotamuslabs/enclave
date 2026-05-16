package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopConfig
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopHostCallbacks
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
}

sealed interface AgentLoopEvent {
    data class TurnStarted(val index: Int) : AgentLoopEvent

    data class TurnCompleted(val index: Int, val toolCallCount: Int) : AgentLoopEvent

    data class TextToken(val text: String) : AgentLoopEvent

    data class ThinkingToken(val text: String) : AgentLoopEvent

    data class ToolCallExecuted(val toolName: String, val resultJson: String) : AgentLoopEvent

    data class CompactionApplied(val tier: String, val tokensBefore: Int, val tokensAfter: Int) : AgentLoopEvent

    data class ToolRepaired(val originalName: String, val resolvedName: String, val confidence: Float) :
        AgentLoopEvent

    data class KvCacheInvalidated(val reason: String) : AgentLoopEvent

    data object Finished : AgentLoopEvent

    data class Failed(val message: String) : AgentLoopEvent
}
