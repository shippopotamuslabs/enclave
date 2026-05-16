package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.AgentModelTurnResult
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort

/**
 * Extended inference service contract for providers that support the Rust agent loop.
 * Implementations complete a single model turn given the full session context managed by Rust.
 */
interface RustCompatibleInferenceService : LlmInferencePort {
    /**
     * Complete one model turn. The Rust engine manages history and compaction; this method
     * is called once per turn with the full session context it assembled.
     *
     * @param sessionMessages full message history from the Rust session (already compacted if needed)
     * @param systemPrompt system prompt to prepend (may differ from [options] if Rust overrides it)
     * @param availableTools tools the model may call this turn; empty when [allowToolCall] is false
     * @param allowToolCall whether the model is permitted to emit a tool call this turn
     * @param onTextToken callback for each streamed text delta
     * @param onThinkingToken callback for each streamed reasoning/thinking delta
     * @param options original [GenerationOptions] from [sendPrompt] (for model params, IDs, etc.)
     */
    suspend fun completeSingleTurn(
        sessionMessages: List<AgentLoopMessage>,
        systemPrompt: String,
        availableTools: List<ToolDefinition>,
        allowToolCall: Boolean,
        onTextToken: suspend (String) -> Unit,
        onThinkingToken: suspend (String) -> Unit,
        options: GenerationOptions,
    ): AgentModelTurnResult
}
