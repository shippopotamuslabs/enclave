package com.browntowndev.pocketcrew.feature.inference.conversation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException

class ConversationErrorMapperTest {
    @Test
    fun executionFailed_includesToolNameAndMessage() {
        val json =
            ConversationErrorMapper.executionFailed(
                toolName = "web_search",
                throwable = IllegalStateException("boom"),
            )

        assertTrue(json.contains("\"tool\":\"web_search\""))
        assertTrue(json.contains("\"exception\":\"IllegalStateException\""))
        assertTrue(json.contains("\"message\":\"boom\""))
    }

    @Test
    fun toJson_contextWindowExceeded_delegatesToPolicy() {
        val json =
            ConversationErrorMapper.toJson(
                ConversationToolError.ContextWindowExceeded(toolName = "append_blocks"),
            )

        assertTrue(json.contains("tool_execution_failed"))
        assertTrue(json.contains("append_blocks"))
        assertTrue(json.contains("Context window exceeded"))
    }

    @Test
    fun rootCause_unwrapsInvocationTargetException() {
        val root = IllegalArgumentException("inner")
        val wrapped = InvocationTargetException(root)

        assertEquals(root, ConversationErrorMapper.rootCause(wrapped))
    }

    @Test
    fun escapeJson_escapesQuotesAndBackslashes() {
        assertEquals("line\\\"break\\\\", ConversationErrorMapper.escapeJson("line\"break\\"))
    }
}
