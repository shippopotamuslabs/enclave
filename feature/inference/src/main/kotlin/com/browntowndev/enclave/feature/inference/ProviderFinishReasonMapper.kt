package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult

internal object ProviderFinishReasonMapper {
    fun fromOpenAiChat(
        finishReason: String,
        text: String,
    ): AgentModelTurnResult = textOnlyTurn(text, finishReason.toOpenAiChatReason())

    fun fromOpenAiResponses(
        status: String,
        incompleteReason: String?,
        responseId: String,
        text: String,
    ): AgentModelTurnResult = textOnlyTurn(text, status.toOpenAiResponsesReason(incompleteReason, responseId))

    fun fromAnthropic(
        stopReason: String,
        text: String,
    ): AgentModelTurnResult = textOnlyTurn(text, stopReason.toAnthropicReason())

    fun fromGoogle(
        finishReason: String,
        text: String,
    ): AgentModelTurnResult = textOnlyTurn(text, finishReason.toGoogleReason())

    fun fromOpenAiChatStream(
        finishReason: String,
        text: String,
    ): AgentModelTurnResult = fromOpenAiChat(finishReason = finishReason, text = text)

    private fun textOnlyTurn(
        text: String,
        finishReason: AgentFinishReason,
    ): AgentModelTurnResult =
        AgentModelTurnResult(
            assistantText = text,
            toolCalls = emptyList(),
            finishReason = finishReason,
        )

    private fun String.toOpenAiChatReason(): AgentFinishReason =
        when (lowercase()) {
            "stop" -> AgentFinishReason.Stop
            "length" -> AgentFinishReason.MaxTokens
            else -> AgentFinishReason.Other(this)
        }

    private fun String.toOpenAiResponsesReason(
        incompleteReason: String?,
        responseId: String,
    ): AgentFinishReason =
        when (lowercase()) {
            "completed" -> AgentFinishReason.Stop
            "incomplete" ->
                when (incompleteReason?.lowercase()) {
                    "max_output_tokens" -> AgentFinishReason.MaxTokens
                    null -> AgentFinishReason.Other("incomplete:$responseId")
                    else -> AgentFinishReason.Other(incompleteReason)
                }
            else -> AgentFinishReason.Other(this)
        }

    private fun String.toAnthropicReason(): AgentFinishReason =
        when (lowercase()) {
            "end_turn", "stop_sequence" -> AgentFinishReason.Stop
            "max_tokens" -> AgentFinishReason.MaxTokens
            else -> AgentFinishReason.Other(this)
        }

    private fun String.toGoogleReason(): AgentFinishReason =
        when (uppercase()) {
            "STOP" -> AgentFinishReason.Stop
            "MAX_TOKENS" -> AgentFinishReason.MaxTokens
            else -> AgentFinishReason.Other(this)
        }
}
