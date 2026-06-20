package com.browntowndev.enclave.feature.inference.wire

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentLoopRowClassifierTest {
    private fun classify(
        message: AgentLoopMessage,
        attachImages: Boolean = false,
        pendingToolCallIds: MutableSet<String> = mutableSetOf(),
    ): AgentLoopRowKind? = message.classify(attachImages = attachImages, pendingToolCallIds = pendingToolCallIds)

    @Test
    fun `classifies system row as system prompt`() {
        val msg = AgentLoopMessage(id = "s1", role = Role.SYSTEM, content = "You are helpful")
        assertEquals(AgentLoopRowKind.SystemPrompt("You are helpful"), classify(msg))
    }

    @Test
    fun `classifies user row as user text with attachImages flag`() {
        val msg = AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")
        assertEquals(AgentLoopRowKind.UserText("hi", attachImages = false), classify(msg))
        assertEquals(AgentLoopRowKind.UserText("hi", attachImages = true), classify(msg, attachImages = true))
    }

    @Test
    fun `classifies assistant text row`() {
        val msg = AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "hello back")
        val kind = classify(msg)
        assertTrue(kind is AgentLoopRowKind.AssistantText)
        val assistant = kind as AgentLoopRowKind.AssistantText
        assertEquals("hello back", assistant.content)
        assertTrue(assistant.toolCalls.isEmpty())
        assertNull(assistant.reasoningContent)
    }

    @Test
    fun `classifies assistant row with raw tool calls`() {
        val msg = AgentLoopMessage(
            id = "a1",
            role = Role.ASSISTANT,
            content = "",
            metadataJson = """{"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"q\":\"x\"}"}]}""",
        )
        val kind = classify(msg) as AgentLoopRowKind.AssistantText
        assertEquals(1, kind.toolCalls.size)
        assertEquals("call_1", kind.toolCalls[0].id)
        assertEquals("web_search", kind.toolCalls[0].name)
    }

    @Test
    fun `classifies assistant row with provider reasoning content`() {
        val msg = AgentLoopMessage(
            id = "a1",
            role = Role.ASSISTANT,
            content = "ok",
            metadataJson = """{"provider_reasoning":{"reasoning_content":"hidden"}}""",
        )
        val kind = classify(msg) as AgentLoopRowKind.AssistantText
        assertEquals("hidden", kind.reasoningContent)
    }

    @Test
    fun `drops assistant row with blank content and no tool calls`() {
        val msg = AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "  ")
        assertNull(classify(msg))
    }

    @Test
    fun `classifies compact summary row`() {
        val msg = AgentLoopMessage(id = "compact-summary-001", role = Role.ASSISTANT, content = "the summary")
        assertEquals(AgentLoopRowKind.CompactSummary("the summary"), classify(msg))
    }

    @Test
    fun `classifies tool result row and removes pending id`() {
        val pending = mutableSetOf("call_1")
        val msg = AgentLoopMessage(
            id = "t1",
            role = Role.USER,
            content = "{}",
            metadataJson = """{"call_id":"call_1"}""",
        )
        val kind = classify(msg, pendingToolCallIds = pending) as AgentLoopRowKind.ToolResult
        assertEquals("call_1", kind.toolCallId)
        assertFalse(kind.isOrphan)
        assertTrue(pending.isEmpty(), "classify should remove the matched id from pendingToolCallIds")
    }

    @Test
    fun `marks tool result as orphan when id not pending`() {
        val msg = AgentLoopMessage(
            id = "t1",
            role = Role.USER,
            content = "{}",
            metadataJson = """{"call_id":"call_orphan"}""",
        )
        val kind = classify(msg) as AgentLoopRowKind.ToolResult
        assertTrue(kind.isOrphan)
    }

    @Test
    fun `classifies engine internal row with wire text`() {
        val msg = AgentLoopMessage(
            id = "budget",
            role = Role.ASSISTANT,
            content = "<system_context>\nTool budget: 1/35 used.\n</system_context>",
            metadataJson = """{"agent_tool_budget":true}""",
        )
        val kind = classify(msg) as AgentLoopRowKind.EngineInternal
        assertTrue(kind.wireText.isNotBlank())
    }

    @Test
    fun `drops engine internal row when wire text is blank`() {
        val msg = AgentLoopMessage(
            id = "budget",
            role = Role.ASSISTANT,
            content = """{"_agent":"payload"}""",
            metadataJson = """{"agent_tool_budget":true}""",
        )
        assertNull(classify(msg))
    }

    @Test
    fun `classifies tool result with empty call_id as orphan`() {
        val msg = AgentLoopMessage(
            id = "t1",
            role = Role.USER,
            content = "{}",
            metadataJson = """{"call_id":""}""",
        )
        val kind = classify(msg) as AgentLoopRowKind.ToolResult
        assertEquals("", kind.toolCallId)
        assertTrue(kind.isOrphan)
    }

    @Test
    fun `classifies compact-summary with call_id as tool result due to ordering precedence`() {
        val msg = AgentLoopMessage(
            id = "compact-summary-001",
            role = Role.ASSISTANT,
            content = "the summary",
            metadataJson = """{"call_id":"call_1"}""",
        )
        val kind = classify(msg)
        assertTrue(kind is AgentLoopRowKind.ToolResult, "Should prioritize tool result over compact summary")
        assertEquals("call_1", (kind as AgentLoopRowKind.ToolResult).toolCallId)
    }

    @Test
    fun `pendingToolCallIds double-add safety`() {
        val pending = mutableSetOf<String>()
        // Add duplicate tool call IDs (simulating double-add)
        pending.add("call_1")
        pending.add("call_1")
        assertEquals(1, pending.size, "Set should automatically handle double-add safety")

        val msg = AgentLoopMessage(
            id = "t1",
            role = Role.USER,
            content = "{}",
            metadataJson = """{"call_id":"call_1"}""",
        )

        // First classification removes it from pending
        val kind1 = classify(msg, pendingToolCallIds = pending) as AgentLoopRowKind.ToolResult
        assertFalse(kind1.isOrphan)
        assertTrue(pending.isEmpty())

        // Second classification of the same message makes it an orphan because the ID is no longer pending
        val kind2 = classify(msg, pendingToolCallIds = pending) as AgentLoopRowKind.ToolResult
        assertTrue(kind2.isOrphan)
    }
}
