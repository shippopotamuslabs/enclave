package com.browntowndev.pocketcrew.domain.model.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ToolCallRequestTest {

    @Test
    fun `parameters lazy property deserializes valid JSON`() {
        val request = ToolCallRequest(
            toolName = ToolDefinition.TAVILY_WEB_SEARCH.name,
            argumentsJson = """{"query": "test query"}""",
            provider = "OPENAI",
            modelType = ModelType.FAST
        )

        val params = request.parameters as TavilyWebSearchParams
        assertEquals("test query", params.query)
    }

    @Test
    fun `parameters lazy property throws SerializationException for invalid JSON`() {
        val request = ToolCallRequest(
            toolName = ToolDefinition.TAVILY_WEB_SEARCH.name,
            argumentsJson = """{"invalid": "json"}""", // Missing 'query'
            provider = "OPENAI",
            modelType = ModelType.FAST
        )

        assertThrows(SerializationException::class.java) {
            request.parameters
        }
    }

    @Test
    fun `parameters lazy property handles unknown tool name gracefully via exception`() {
        val request = ToolCallRequest(
            toolName = "unknown_tool",
            argumentsJson = "{}",
            provider = "OPENAI",
            modelType = ModelType.FAST
        )

        assertThrows(IllegalArgumentException::class.java) {
            request.parameters
        }
    }

    @Test
    fun `parameters lazy property handles partial or malformed JSON via exception`() {
        val request = ToolCallRequest(
            toolName = ToolDefinition.TAVILY_WEB_SEARCH.name,
            argumentsJson = """{"query": "truncated...""",
            provider = "OPENAI",
            modelType = ModelType.FAST
        )

        assertThrows(SerializationException::class.java) {
            request.parameters
        }
    }
}
