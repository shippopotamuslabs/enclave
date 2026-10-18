package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.profile.ProfileAnalysisInferencePort

/**
 * [ProfileAnalysisInferencePort] fake (ENC-28): records every turn and answers
 * via [completeTurnHandler] (receiving the zero-based call index), falling back
 * to [defaultTurnResult] when no handler is programmed.
 */
class FakeProfileAnalysisInferencePort : ProfileAnalysisInferencePort {
    data class CompleteTurnCall(
        val configId: ApiModelConfigurationId,
        val systemPrompt: String,
    )

    val completeTurnCalls = mutableListOf<CompleteTurnCall>()

    /** Value returned by [getContextWindowTokens]. */
    var contextWindowTokensResult: Int = 200_000

    /** Scripted turn behavior; receives the zero-based index of the current call. */
    var completeTurnHandler: (suspend (callIndex: Int) -> AgentModelTurnResult)? = null

    /** Turn result used when [completeTurnHandler] is not set. */
    var defaultTurnResult: AgentModelTurnResult? = null

    override suspend fun completeTurn(
        configId: ApiModelConfigurationId,
        messages: List<AgentLoopMessage>,
        systemPrompt: String,
        tools: List<ToolDefinition>,
        onTextToken: suspend (String) -> Unit,
    ): AgentModelTurnResult {
        completeTurnCalls += CompleteTurnCall(configId, systemPrompt)
        val callIndex = completeTurnCalls.size - 1
        completeTurnHandler?.let { return it(callIndex) }
        return defaultTurnResult
            ?: error("FakeProfileAnalysisInferencePort: no completeTurnHandler or defaultTurnResult programmed")
    }

    override suspend fun getContextWindowTokens(configId: ApiModelConfigurationId): Int = contextWindowTokensResult
}
