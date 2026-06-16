package com.browntowndev.enclave.feature.inference.openai

import com.openai.models.chat.completions.ChatCompletionChunk
import com.openai.models.completions.CompletionUsage
import com.openai.models.completions.CompletionUsage.PromptTokensDetails
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseUsage
import io.mockk.every
import io.mockk.mockk
import com.openai.core.JsonValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional

class OpenAiStreamUsageAccumulatorTest {
    @Test
    fun accumulateOpenAiStreamUsage_appliesUsageWhenChoicesEmpty() {
        val state = OpenAiStreamUsageAccumulator()
        val event = mockk<ChatCompletionChunk>()
        val usage = mockk<CompletionUsage>()
        val promptDetails = mockk<PromptTokensDetails>()

        every { event.usage() } returns Optional.of(usage)
        every { usage.promptTokens() } returns 12_000L
        every { usage.completionTokens() } returns 50L
        every { usage.completionTokensDetails() } returns Optional.empty()
        every { usage.promptTokensDetails() } returns Optional.of(promptDetails)
        every { usage._additionalProperties() } returns emptyMap()
        every { promptDetails.cachedTokens() } returns Optional.of(9_500L)
        every { event.choices() } returns emptyList()

        accumulateOpenAiStreamUsage(event, state)

        assertEquals(12_000, state.inputTokens)
        assertEquals(50, state.outputTokens)
        assertEquals(9_500, state.cacheReadInputTokens)
    }

    @Test
    fun accumulateResponseStreamUsage_mapsInputOutputCacheAndReasoning() {
        val state = OpenAiStreamUsageAccumulator()
        val usage = mockk<ResponseUsage>()
        val inputDetails = mockk<ResponseUsage.InputTokensDetails>()
        val outputDetails = mockk<ResponseUsage.OutputTokensDetails>()

        every { usage.inputTokens() } returns 12_000L
        every { usage.outputTokens() } returns 50L
        every { usage.inputTokensDetails() } returns inputDetails
        every { usage.outputTokensDetails() } returns outputDetails
        every { inputDetails.cachedTokens() } returns 9_500L
        every { outputDetails.reasoningTokens() } returns 12L
        every { usage._additionalProperties() } returns emptyMap()

        accumulateResponseStreamUsage(usage, state)

        assertEquals(12_000, state.inputTokens)
        assertEquals(50, state.outputTokens)
        assertEquals(9_500, state.cacheReadInputTokens)
        assertEquals(12, state.reasoningTokens)
    }

    @Test
    fun accumulateResponseStreamUsage_readsUsageFromCompletedResponse() {
        val state = OpenAiStreamUsageAccumulator()
        val response = mockk<Response>()
        val usage = mockk<ResponseUsage>()
        val inputDetails = mockk<ResponseUsage.InputTokensDetails>()
        val outputDetails = mockk<ResponseUsage.OutputTokensDetails>()

        every { response.usage() } returns Optional.of(usage)
        every { usage.inputTokens() } returns 1_000L
        every { usage.outputTokens() } returns 200L
        every { usage.inputTokensDetails() } returns inputDetails
        every { usage.outputTokensDetails() } returns outputDetails
        every { inputDetails.cachedTokens() } returns 400L
        every { outputDetails.reasoningTokens() } returns 0L
        every { usage._additionalProperties() } returns emptyMap()

        accumulateResponseStreamUsage(response, state)

        assertEquals(1_000, state.inputTokens)
        assertEquals(200, state.outputTokens)
        assertEquals(400, state.cacheReadInputTokens)
        assertEquals(null, state.reasoningTokens)
    }

    @Test
    fun accumulateOpenAiStreamUsage_keepsLargerInputTokensAcrossTwoUsageChunks() {
        val state = OpenAiStreamUsageAccumulator()
        val chunk1 = mockCompletionChunk(promptTokens = 1_000L, completionTokens = 400L)
        val chunk2 = mockCompletionChunk(promptTokens = 500L, completionTokens = 0L)

        accumulateOpenAiStreamUsage(chunk1, state)
        accumulateOpenAiStreamUsage(chunk2, state)

        assertEquals(1_000, state.inputTokens)
    }

    @Test
    fun accumulateOpenAiStreamUsage_doesNotClobberOutputTokensWithTrailingZeroChunk() {
        val state = OpenAiStreamUsageAccumulator()
        val chunk1 = mockCompletionChunk(promptTokens = 1_000L, completionTokens = 400L)
        val chunk2 = mockCompletionChunk(promptTokens = 500L, completionTokens = 0L)

        accumulateOpenAiStreamUsage(chunk1, state)
        accumulateOpenAiStreamUsage(chunk2, state)

        assertEquals(400, state.outputTokens)
    }

    @Test
    fun accumulateOpenAiStreamUsage_stillAppliesFinalUsageOnEmptyChoicesChunk() {
        val state = OpenAiStreamUsageAccumulator()
        val event = mockk<ChatCompletionChunk>()
        val usage = mockk<CompletionUsage>()
        val promptDetails = mockk<PromptTokensDetails>()

        every { event.usage() } returns Optional.of(usage)
        every { usage.promptTokens() } returns 12_000L
        every { usage.completionTokens() } returns 50L
        every { usage.completionTokensDetails() } returns Optional.empty()
        every { usage.promptTokensDetails() } returns Optional.of(promptDetails)
        every { usage._additionalProperties() } returns emptyMap()
        every { promptDetails.cachedTokens() } returns Optional.of(9_500L)
        every { event.choices() } returns emptyList()

        accumulateOpenAiStreamUsage(event, state)

        assertEquals(12_000, state.inputTokens)
        assertEquals(50, state.outputTokens)
        assertEquals(9_500, state.cacheReadInputTokens)
    }

    @Test
    fun accumulateOpenAiStreamUsage_parsesOpenRouterExactCost() {
        val state = OpenAiStreamUsageAccumulator()
        val event = mockk<ChatCompletionChunk>()
        val usage = mockk<CompletionUsage>()

        every { event.usage() } returns Optional.of(usage)
        every { usage.promptTokens() } returns 100L
        every { usage.completionTokens() } returns 20L
        every { usage.completionTokensDetails() } returns Optional.empty()
        every { usage.promptTokensDetails() } returns Optional.empty()
        every { usage._additionalProperties() } returns mapOf("cost" to JsonValue.from(0.0012))
        every { event.choices() } returns emptyList()

        accumulateOpenAiStreamUsage(event, state)

        assertEquals(BigDecimal("0.001200"), state.costUsd)
    }

    private fun mockCompletionChunk(
        promptTokens: Long,
        completionTokens: Long,
    ): ChatCompletionChunk {
        val event = mockk<ChatCompletionChunk>()
        val usage = mockk<CompletionUsage>()
        every { event.usage() } returns Optional.of(usage)
        every { usage.promptTokens() } returns promptTokens
        every { usage.completionTokens() } returns completionTokens
        every { usage.completionTokensDetails() } returns Optional.empty()
        every { usage.promptTokensDetails() } returns Optional.empty()
        every { usage._additionalProperties() } returns emptyMap()
        every { event.choices() } returns emptyList()
        return event
    }
}
