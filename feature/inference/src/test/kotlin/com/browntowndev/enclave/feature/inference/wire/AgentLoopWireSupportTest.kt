package com.browntowndev.enclave.feature.inference.wire

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentLoopWireSupportTest {
    @Test
    fun `metadata parses null and blank json as empty`() {
        assertNull("null".toMetadataJsonObject())
        assertNull("".toMetadataJsonObject())
        assertNull("   ".toMetadataJsonObject())
    }

    @Test
    fun `metadata parses malformed json as null`() {
        assertNull("{not json".toMetadataJsonObject())
    }

    @Test
    fun `metadata extracts tool call id`() {
        val msg = AgentLoopMessage(
            id = "t1",
            role = Role.USER,
            content = "{}",
            metadataJson = """{"call_id":"call_xyz"}""",
        )
        assertEquals("call_xyz", msg.metadata().toolCallId)
        assertTrue(msg.hasToolCallMetadata())
    }

    @Test
    fun `metadata parses raw_tool_calls with id name and arguments`() {
        val msg = AgentLoopMessage(
            id = "a1",
            role = Role.ASSISTANT,
            content = "",
            metadataJson = """{"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"q\":\"test\"}"}]}""",
        )
        val meta = msg.metadata()
        assertEquals(1, meta.rawToolCalls.size)
        assertEquals("call_1", meta.rawToolCalls[0].id)
        assertEquals("web_search", meta.rawToolCalls[0].name)
        assertEquals("""{"q":"test"}""", meta.rawToolCalls[0].argumentsJson)
    }

    @Test
    fun `raw_tool_calls drops entries without a name`() {
        val msg = AgentLoopMessage(
            id = "a1",
            role = Role.ASSISTANT,
            content = "",
            metadataJson = """{"raw_tool_calls":[{"id":"call_1","arguments_json":"{}"}]}""",
        )
        assertTrue(msg.metadata().rawToolCalls.isEmpty())
    }

    @Test
    fun `raw_tool_calls treats blank id as null but keeps the entry`() {
        val msg = AgentLoopMessage(
            id = "a1",
            role = Role.ASSISTANT,
            content = "",
            metadataJson = """{"raw_tool_calls":[{"id":"","name":"web_search","arguments_json":"{}"}]}""",
        )
        val calls = msg.metadata().rawToolCalls
        assertEquals(1, calls.size)
        assertEquals("web_search", calls[0].name)
        assertNull(calls[0].id)
    }

    @Test
    fun `raw_tool_calls defaults missing arguments_json to empty object`() {
        val msg = AgentLoopMessage(
            id = "a1",
            role = Role.ASSISTANT,
            content = "",
            metadataJson = """{"raw_tool_calls":[{"id":"call_1","name":"web_search"}]}""",
        )
        assertEquals("{}", msg.metadata().rawToolCalls[0].argumentsJson)
    }

    @Test
    fun `metadata extracts provider_reasoning content`() {
        val msg = AgentLoopMessage(
            id = "a1",
            role = Role.ASSISTANT,
            content = "ok",
            metadataJson = """{"provider_reasoning":{"reasoning_content":"hidden chain"}}""",
        )
        assertEquals("hidden chain", msg.metadata().providerReasoningContent)
    }

    @Test
    fun `provider_reasoning_content extension on parsed object`() {
        val obj = """{"provider_reasoning":{"reasoning_content":"need tool output first"}}""".toMetadataJsonObject()
        assertEquals("need tool output first", obj.providerReasoningContent())
    }

    @Test
    fun `provider_reasoning_content returns null when blank`() {
        val obj = """{"provider_reasoning":{"reasoning_content":"  "}}""".toMetadataJsonObject()
        assertNull(obj.providerReasoningContent())
    }

    @Test
    fun `metadata flags engine internal rows`() {
        val msg = AgentLoopMessage(
            id = "budget",
            role = Role.ASSISTANT,
            content = "<system_context>\nTool budget: 1/35 used.\n</system_context>",
            metadataJson = """{"agent_tool_budget":true}""",
        )
        assertTrue(msg.metadata().isEngineInternal)
    }

    @Test
    fun `metadata is not engine internal for ordinary rows`() {
        val msg = AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")
        assertFalse(msg.metadata().isEngineInternal)
    }

    @Test
    fun `lastNonToolResultUserIndex skips tool result rows`() {
        val messages = listOf(
            AgentLoopMessage(id = "u1", role = Role.USER, content = "Search"),
            AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = ""),
            AgentLoopMessage(
                id = "t1",
                role = Role.USER,
                content = "{}",
                metadataJson = """{"call_id":"call_1"}""",
            ),
        )
        assertEquals(0, messages.lastNonToolResultUserIndex())
    }

    @Test
    fun `lastNonToolResultUserIndex returns last real user index`() {
        val messages = listOf(
            AgentLoopMessage(id = "u1", role = Role.USER, content = "first"),
            AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "reply"),
            AgentLoopMessage(id = "u2", role = Role.USER, content = "second"),
        )
        assertEquals(2, messages.lastNonToolResultUserIndex())
    }

    @Test
    fun `lastNonToolResultUserIndex returns minus one when no user rows`() {
        val messages = listOf(
            AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "reply"),
        )
        assertEquals(-1, messages.lastNonToolResultUserIndex())
    }

    @Test
    fun `lastNonEngineInternalIndex skips engine internal rows`() {
        val messages = listOf(
            AgentLoopMessage(id = "u1", role = Role.USER, content = "hi"),
            AgentLoopMessage(
                id = "budget",
                role = Role.ASSISTANT,
                content = "<system_context>\nTool budget: 1/35 used.\n</system_context>",
                metadataJson = """{"agent_tool_budget":true}""",
            ),
        )
        assertEquals(0, messages.lastNonEngineInternalIndex())
    }

    @Test
    fun `lastNonEngineInternalIndex returns zero for empty transcript`() {
        assertEquals(0, emptyList<AgentLoopMessage>().lastNonEngineInternalIndex())
    }

    @Test
    fun `isCompactSummary detects prefix`() {
        assertTrue(
            AgentLoopMessage(id = "compact-summary-001", role = Role.ASSISTANT, content = "summary")
                .isCompactSummary(),
        )
        assertFalse(
            AgentLoopMessage(id = "chat-001", role = Role.ASSISTANT, content = "summary")
                .isCompactSummary(),
        )
    }

    @Test
    fun `formatCompactSummary prepends wire label`() {
        assertEquals("[Conversation summary]\nthe summary", formatCompactSummary("the summary"))
    }

    @Test
    fun `truncateForLogs keeps short values unchanged`() {
        assertEquals("short", truncateForLogs("short"))
    }

    @Test
    fun `truncateForLogs truncates long values with sentinel`() {
        val long = "x".repeat(MAX_LOG_BODY_CHARS + 100)
        val truncated = truncateForLogs(long)
        assertEquals(MAX_LOG_BODY_CHARS + "...<truncated>".length, truncated.length)
        assertTrue(truncated.endsWith("...<truncated>"))
    }

    @Test
    fun `describeInferenceException renders class and message`() {
        val described = describeInferenceException(IllegalStateException("boom"))
        assertTrue(described.startsWith("exception=IllegalStateException"))
        assertTrue(described.contains("message=boom"))
    }

    @Test
    fun `describeInferenceException omits message when blank`() {
        val described = describeInferenceException(IllegalStateException(""))
        assertEquals("exception=IllegalStateException", described)
    }
}
