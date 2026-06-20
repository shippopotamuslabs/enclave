package com.browntowndev.enclave.feature.inference.wire

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.MessageParam
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnthropicWireMessageMapperTest {
    private val mapper = ObjectMapper()
    private val wireMapper = AnthropicWireMessageMapper(toolInputProperties = ::parseInput)

    private fun parseInput(json: String): Map<String, JsonValue> =
        mapper.readValue(json, object : TypeReference<Map<String, Any?>>() {}).entries
            .associate { (key, value) -> key to JsonValue.from(value) }

    private fun MessageParam.blocks() = content().asBlockParams()

    @Test
    fun `userText emits user role with text block`() {
        val param = wireMapper.userText("hi", imageUris = emptyList(), useCacheControl = false)
        assertEquals(MessageParam.Role.USER, param.role())
        val blocks = param.blocks()
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].isText())
    }

    @Test
    fun `engineInternal returns null for blank wire text`() {
        assertNull(wireMapper.engineInternal("", useCacheControl = false))
    }

    @Test
    fun `engineInternal emits user message for non-blank wire text`() {
        val param = wireMapper.engineInternal("<system_context>budget</system_context>", useCacheControl = false)
        assertNotNull(param)
        assertEquals(MessageParam.Role.USER, param!!.role())
    }

    @Test
    fun `compactSummary emits user role with conversation summary label`() {
        val param = wireMapper.compactSummary("the summary", useCacheControl = false)
        assertEquals(MessageParam.Role.USER, param.role())
        val text = param.blocks()[0].asText().text()
        assertTrue(text.startsWith("[Conversation summary]"))
    }

    @Test
    fun `toolResult emits user role with tool result block`() {
        val param = wireMapper.toolResult("call_1", "{}", isOrphan = false, useCacheControl = false)
        assertEquals(MessageParam.Role.USER, param.role())
        assertTrue(param.blocks()[0].isToolResult())
    }

    @Test
    fun `assistantText emits assistant role with text and tool use blocks`() {
        val param = wireMapper.assistantText(
            content = "answer",
            toolCalls = listOf(RawToolCallRef(id = "call_1", name = "web_search", argumentsJson = """{"q":"x"}""")),
            reasoningContent = null,
            useCacheControl = false,
        )!!
        assertEquals(MessageParam.Role.ASSISTANT, param.role())
        val blocks = param.blocks()
        assertEquals(2, blocks.size)
        assertTrue(blocks[0].isText())
        assertTrue(blocks[1].isToolUse())
    }

    @Test
    fun `assistantText tolerates null tool call id`() {
        val param = wireMapper.assistantText(
            content = "answer",
            toolCalls = listOf(RawToolCallRef(id = null, name = "web_search", argumentsJson = "{}")),
            reasoningContent = null,
            useCacheControl = false,
        )!!
        val toolUse = param.blocks()[1].asToolUse()
        assertEquals("", toolUse.id())
    }

    @Test
    fun `assistantText returns null when content blank and no tool calls`() {
        assertNull(
            wireMapper.assistantText("  ", emptyList(), reasoningContent = null, useCacheControl = false),
        )
    }

    @Test
    fun `systemPrompt errors because Anthropic sets system on the params builder`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            wireMapper.systemPrompt("x", useCacheControl = false)
        }
    }
}
