package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.AgentFinishReason
import com.browntowndev.pocketcrew.domain.model.inference.AgentModelTurnResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderFinishReasonMapperTest {
    @Test
    fun `FB-P-001 OpenAI chat stop maps to Stop`() {
        assertTurn(
            result = ProviderFinishReasonMapper.fromOpenAiChat(finishReason = "stop", text = "Hello"),
            expectedReason = AgentFinishReason.Stop,
            expectedText = "Hello",
        )
    }

    @Test
    fun `FB-P-002 OpenAI chat length maps to MaxTokens`() {
        assertTurn(
            result = ProviderFinishReasonMapper.fromOpenAiChat(finishReason = "length", text = "Partial"),
            expectedReason = AgentFinishReason.MaxTokens,
            expectedText = "Partial",
        )
    }

    @Test
    fun `FB-P-003 OpenAI chat content filter maps to Other`() {
        assertTurn(
            result = ProviderFinishReasonMapper.fromOpenAiChat(finishReason = "content_filter", text = "Blocked"),
            expectedReason = AgentFinishReason.Other("content_filter"),
            expectedText = "Blocked",
        )
    }

    @Test
    fun `FB-P-004 OpenAI Responses completed maps to Stop`() {
        assertTurn(
            result = ProviderFinishReasonMapper.fromOpenAiResponses(
                status = "completed",
                incompleteReason = null,
                responseId = "resp_done",
                text = "Done",
            ),
            expectedReason = AgentFinishReason.Stop,
            expectedText = "Done",
        )
    }

    @Test
    fun `FB-P-005 OpenAI Responses max output tokens maps to MaxTokens`() {
        assertTurn(
            result = ProviderFinishReasonMapper.fromOpenAiResponses(
                status = "incomplete",
                incompleteReason = "max_output_tokens",
                responseId = "resp_partial",
                text = "Partial",
            ),
            expectedReason = AgentFinishReason.MaxTokens,
            expectedText = "Partial",
        )
    }

    @Test
    fun `FB-P-006 Anthropic end turn maps to Stop`() {
        assertTurn(
            result = ProviderFinishReasonMapper.fromAnthropic(stopReason = "end_turn", text = "Done"),
            expectedReason = AgentFinishReason.Stop,
            expectedText = "Done",
        )
    }

    @Test
    fun `FB-P-007 Anthropic max tokens maps to MaxTokens`() {
        assertTurn(
            result = ProviderFinishReasonMapper.fromAnthropic(stopReason = "max_tokens", text = "Partial"),
            expectedReason = AgentFinishReason.MaxTokens,
            expectedText = "Partial",
        )
    }

    @Test
    fun `FB-P-008 Anthropic refusal maps to Other`() {
        assertTurn(
            result = ProviderFinishReasonMapper.fromAnthropic(stopReason = "refusal", text = "Blocked"),
            expectedReason = AgentFinishReason.Other("refusal"),
            expectedText = "Blocked",
        )
    }

    @Test
    fun `FB-P-009 Google STOP maps to Stop`() {
        assertTurn(
            result = ProviderFinishReasonMapper.fromGoogle(finishReason = "STOP", text = "Done"),
            expectedReason = AgentFinishReason.Stop,
            expectedText = "Done",
        )
    }

    @Test
    fun `FB-P-010 Google MAX_TOKENS maps to MaxTokens`() {
        assertTurn(
            result = ProviderFinishReasonMapper.fromGoogle(finishReason = "MAX_TOKENS", text = "Partial"),
            expectedReason = AgentFinishReason.MaxTokens,
            expectedText = "Partial",
        )
    }

    @Test
    fun `FB-P-011 Google SAFETY maps to Other`() {
        assertTurn(
            result = ProviderFinishReasonMapper.fromGoogle(finishReason = "SAFETY", text = "Blocked"),
            expectedReason = AgentFinishReason.Other("SAFETY"),
            expectedText = "Blocked",
        )
    }

    @Test
    fun `FB-M-001 OpenAI chat stream length maps to MaxTokens`() {
        assertTurn(
            result = ProviderFinishReasonMapper.fromOpenAiChatStream(finishReason = "length", text = "Partial answer"),
            expectedReason = AgentFinishReason.MaxTokens,
            expectedText = "Partial answer",
        )
    }

    private fun assertTurn(
        result: AgentModelTurnResult,
        expectedReason: AgentFinishReason,
        expectedText: String,
    ) {
        assertEquals(expectedReason, result.finishReason)
        assertEquals(expectedText, result.assistantText)
        assertTrue(result.toolCalls.isEmpty())
    }
}
