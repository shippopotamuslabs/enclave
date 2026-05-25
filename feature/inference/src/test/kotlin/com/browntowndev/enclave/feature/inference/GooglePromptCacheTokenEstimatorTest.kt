package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.google.genai.types.Content
import com.google.genai.types.Part
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GooglePromptCacheTokenEstimatorTest {
    @Test
    fun countStablePrefixTokens_includesToolSchemasNotOnlyMessageContent() {
        val shortContents =
            listOf(
                Content
                    .builder()
                    .role("user")
                    .parts(listOf(Part.fromText("hello")))
                    .build(),
            )
        val contentOnly =
            GooglePromptCacheTokenEstimator.countStablePrefixTokens(
                modelId = "gemini-2.5-flash",
                systemPrompt = "system",
                contents = shortContents,
                tools = emptyList(),
            )
        val withTools =
            GooglePromptCacheTokenEstimator.countStablePrefixTokens(
                modelId = "gemini-2.5-flash",
                systemPrompt = "system",
                contents = shortContents,
                tools = listOf(ToolDefinition.WEB_SEARCH, ToolDefinition.APPEND_BLOCKS),
            )
        assertTrue(withTools > contentOnly)
    }
}
