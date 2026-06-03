package com.browntowndev.enclave.domain.port.profile

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.ToolDefinition

/**
 * Single-turn inference port for the profile analysis session.
 *
 * Abstracts away the provider-specific machinery so the orchestrator in core:data can
 * drive a manual tool-call loop without knowing about RustCompatibleInferenceService.
 */
interface ProfileAnalysisInferencePort {
    /**
     * Complete one model turn. Caller manages the message history and tool-call loop.
     *
     * Tool results are passed back as USER messages formatted as:
     * "[Tool result: tool_name]\n{json}" — a simplified format that works conversationally
     * for high-capability models even without strict tool-use API formatting.
     */
    suspend fun completeTurn(
        configId: ApiModelConfigurationId,
        messages: List<AgentLoopMessage>,
        systemPrompt: String,
        tools: List<ToolDefinition>,
        onTextToken: suspend (String) -> Unit = {},
    ): AgentModelTurnResult

    /**
     * Returns the context-window size in tokens for [configId].
     * Used by the extraction pass to size conversation batches so a single turn never
     * overflows the model's context. Falls back to a conservative default if the
     * configuration cannot be resolved.
     */
    suspend fun getContextWindowTokens(configId: ApiModelConfigurationId): Int
}
