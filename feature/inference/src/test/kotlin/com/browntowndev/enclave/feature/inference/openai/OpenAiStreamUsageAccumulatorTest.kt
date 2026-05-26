package com.browntowndev.enclave.feature.inference.openai

import com.openai.models.chat.completions.ChatCompletionChunk
import com.openai.models.completions.CompletionUsage
import com.openai.models.completions.CompletionUsage.PromptTokensDetails
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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
        every { promptDetails.cachedTokens() } returns Optional.of(9_500L)
        every { event.choices() } returns emptyList()

        accumulateOpenAiStreamUsage(event, state)

        assertEquals(12_000, state.inputTokens)
        assertEquals(50, state.outputTokens)
        assertEquals(9_500, state.cacheReadInputTokens)
    }
}
