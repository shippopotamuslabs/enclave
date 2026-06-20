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
        every { promptDetails._additionalProperties() } returns emptyMap()
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
        every { inputDetails._additionalProperties() } returns emptyMap()
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
        every { inputDetails._additionalProperties() } returns emptyMap()
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
        every { promptDetails._additionalProperties() } returns emptyMap()
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

    @Test
    fun testRealJsonDeserialization() {
        val json = """
            {
              "prompt_tokens": 12000,
              "completion_tokens": 50,
              "total_tokens": 12050,
              "prompt_tokens_details": {
                "cached_tokens": 9500
              }
            }
        """.trimIndent()
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        val usage = mapper.readValue(json, CompletionUsage::class.java)
        assertEquals(12000L, usage.promptTokens())
        assertEquals(50L, usage.completionTokens())
        val detailsOpt = usage.promptTokensDetails()
        org.junit.jupiter.api.Assertions.assertTrue(detailsOpt.isPresent)
        val details = detailsOpt.get()
        val cachedOpt = details.cachedTokens()
        org.junit.jupiter.api.Assertions.assertTrue(cachedOpt.isPresent)
        assertEquals(9500L, cachedOpt.get())
    }

    @Test
    fun testRealChunkDeserialization() {
        val json = """
            {
              "id": "gen-123",
              "choices": [],
              "usage": {
                "prompt_tokens": 12000,
                "completion_tokens": 50,
                "total_tokens": 12050,
                "prompt_tokens_details": {
                  "cached_tokens": 9500
                }
              }
            }
        """.trimIndent()
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        val chunk = mapper.readValue(json, ChatCompletionChunk::class.java)
        val state = OpenAiStreamUsageAccumulator()
        accumulateOpenAiStreamUsage(chunk, state)
        assertEquals(12000, state.inputTokens)
        assertEquals(50, state.outputTokens)
        assertEquals(9500, state.cacheReadInputTokens)
    }

    @Test
    fun testRealChunkDeserializationWithCacheWriteTokens() {
        val json = """
            {
              "id": "gen-123",
              "choices": [],
              "usage": {
                "prompt_tokens": 12000,
                "completion_tokens": 50,
                "total_tokens": 12050,
                "prompt_tokens_details": {
                  "cached_tokens": 9500,
                  "cache_write_tokens": 1000
                }
              }
            }
        """.trimIndent()
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        val chunk = mapper.readValue(json, ChatCompletionChunk::class.java)
        val state = OpenAiStreamUsageAccumulator()
        accumulateOpenAiStreamUsage(chunk, state)
        assertEquals(12000, state.inputTokens)
        assertEquals(50, state.outputTokens)
        assertEquals(9500, state.cacheReadInputTokens)
        assertEquals(1000, state.cacheWriteInputTokens)
    }

    @Test
    fun accumulateResponseStreamUsage_mapsCacheWriteTokens() {
        val state = OpenAiStreamUsageAccumulator()
        val usage = mockk<ResponseUsage>()
        val inputDetails = mockk<ResponseUsage.InputTokensDetails>()
        val outputDetails = mockk<ResponseUsage.OutputTokensDetails>()

        every { usage.inputTokens() } returns 12_000L
        every { usage.outputTokens() } returns 50L
        every { usage.inputTokensDetails() } returns inputDetails
        every { usage.outputTokensDetails() } returns outputDetails
        every { inputDetails.cachedTokens() } returns 9_500L
        every { outputDetails.reasoningTokens() } returns 0L
        every { usage._additionalProperties() } returns emptyMap()
        
        val cacheWriteVal = JsonValue.from(1000)
        every { inputDetails._additionalProperties() } returns mapOf("cache_write_tokens" to cacheWriteVal)

        accumulateResponseStreamUsage(usage, state)

        assertEquals(12_000, state.inputTokens)
        assertEquals(50, state.outputTokens)
        assertEquals(9_500, state.cacheReadInputTokens)
        assertEquals(1000, state.cacheWriteInputTokens)
    }

    @Test
    fun accumulateOpenAiStreamUsage_accumulatesCacheWriteTokensFromDetailsAdditionalProperties() {
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
        
        val cacheWriteVal = JsonValue.from(1000)
        every { promptDetails._additionalProperties() } returns mapOf("cache_write_tokens" to cacheWriteVal)
        every { event.choices() } returns emptyList()

        accumulateOpenAiStreamUsage(event, state)

        assertEquals(12_000, state.inputTokens)
        assertEquals(50, state.outputTokens)
        assertEquals(9_500, state.cacheReadInputTokens)
        assertEquals(1000, state.cacheWriteInputTokens)
    }

    @Test
    fun testRealChunkDeserializationWithOpenRouterFlatCachingKeys() {
        val json = """
            {
              "id": "gen-123",
              "choices": [],
              "usage": {
                "prompt_tokens": 12000,
                "completion_tokens": 50,
                "total_tokens": 12050,
                "cache_read_tokens": 9500,
                "cache_write_tokens": 1000
              }
            }
        """.trimIndent()
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        val chunk = mapper.readValue(json, ChatCompletionChunk::class.java)
        val state = OpenAiStreamUsageAccumulator()
        accumulateOpenAiStreamUsage(chunk, state)
        assertEquals(12000, state.inputTokens)
        assertEquals(50, state.outputTokens)
        assertEquals(9500, state.cacheReadInputTokens)
        assertEquals(1000, state.cacheWriteInputTokens)
    }

    @Test
    fun accumulateResponseStreamUsage_mapsOpenRouterFlatCachingKeys() {
        val state = OpenAiStreamUsageAccumulator()
        val usage = mockk<ResponseUsage>()
        val inputDetails = mockk<ResponseUsage.InputTokensDetails>()
        val outputDetails = mockk<ResponseUsage.OutputTokensDetails>()

        every { usage.inputTokens() } returns 12_000L
        every { usage.outputTokens() } returns 50L
        every { usage.inputTokensDetails() } returns inputDetails
        every { usage.outputTokensDetails() } returns outputDetails
        every { inputDetails.cachedTokens() } returns 0L
        every { inputDetails._additionalProperties() } returns emptyMap()
        every { outputDetails.reasoningTokens() } returns 0L
        
        every { usage._additionalProperties() } returns mapOf(
            "cache_read_tokens" to JsonValue.from(9500),
            "cache_write_tokens" to JsonValue.from(1000)
        )

        accumulateResponseStreamUsage(usage, state)

        assertEquals(12_000, state.inputTokens)
        assertEquals(50, state.outputTokens)
        assertEquals(9500, state.cacheReadInputTokens)
        assertEquals(1000, state.cacheWriteInputTokens)
    }
}

