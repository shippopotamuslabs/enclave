package com.browntowndev.enclave.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HarnessOutputSanitizerTest {
    @Test
    fun sanitize_strips_standalone_agent_json_block() {
        val input =
            """Here is the answer {"_agent":{"tool_calls_used":1,"allow_tool_call":true}} for you."""
        assertEquals("Here is the answer  for you.", HarnessOutputSanitizer.sanitize(input))
    }

    @Test
    fun sanitize_strips_system_context_tags() {
        val input = "Visible text <system_context>sidecar dump</system_context> still visible."
        assertEquals("Visible text  still visible.", HarnessOutputSanitizer.sanitize(input))
    }

    @Test
    fun sanitize_strips_legacy_agent_prefix_block() {
        val input = "Before [Agent]\n{\"_agent\":{\"tool_calls_used\":2}} after."
        assertEquals("Before  after.", HarnessOutputSanitizer.sanitize(input))
    }

    @Test
    fun sanitize_preserves_ordinary_user_visible_text() {
        val input = "The weather is sunny and the answer is 42."
        assertEquals(input, HarnessOutputSanitizer.sanitize(input))
    }

    @Test
    fun sanitize_preserves_leading_spaces_in_streamed_tokens() {
        assertEquals(" world", HarnessOutputSanitizer.sanitize(" world"))
    }

    @Test
    fun sanitize_preserves_code_indentation() {
        val input = "line1\n    indented\n  less"
        assertEquals(input, HarnessOutputSanitizer.sanitize(input))
    }

    @Test
    fun streamBuffer_preserves_spacing_across_chunks() {
        val buffer = HarnessOutputSanitizer.StreamBuffer()
        assertEquals("Hello", buffer.append("Hello"))
        assertEquals(" world", buffer.append(" world"))
    }

    @Test
    fun streamBuffer_strips_agent_json_split_across_chunks() {
        val buffer = HarnessOutputSanitizer.StreamBuffer()
        assertEquals("Visible ", buffer.append("Visible {\"_agen"))
        assertEquals(" text", buffer.append("t\":{\"tool_calls_used\":1}} text"))
        assertEquals("", buffer.flush())
    }
}