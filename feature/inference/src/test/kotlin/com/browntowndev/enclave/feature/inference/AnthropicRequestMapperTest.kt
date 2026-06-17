package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnthropicRequestMapperTest {
    @Test
    fun `toAnthropicTool includes schema definitions for ref resolution`() {
        val tool =
            ToolDefinition.WEB_SEARCH.toAnthropicTool()

        val serialized = tool.toString()
        assertTrue(serialized.contains("web_search"))
        assertTrue(serialized.contains("query"))
    }
}