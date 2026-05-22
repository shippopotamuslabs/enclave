package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.port.inference.LoggingPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Defense-in-depth FFI guard tests. Verifies caps are enforced before the model turn result
 * crosses the UniFFI boundary, preventing OOM during UTF-8 encoding of runaway provider output.
 */
class FfiPayloadGuardTest {
    @Test
    fun `capAssistantText returns null when input is null`() {
        assertNull(FfiPayloadGuard.capAssistantText(null))
    }

    @Test
    fun `capAssistantText returns input unchanged when under limit`() {
        val text = "normal assistant reply"
        assertSame(text, FfiPayloadGuard.capAssistantText(text))
    }

    @Test
    fun `capAssistantText returns input unchanged at exactly the limit`() {
        val text = "a".repeat(FfiPayloadGuard.MAX_ASSISTANT_TEXT_CHARS)
        val result = FfiPayloadGuard.capAssistantText(text)
        assertSame(text, result)
    }

    @Test
    fun `capAssistantText truncates and appends marker when over limit`() {
        val text = "x".repeat(FfiPayloadGuard.MAX_ASSISTANT_TEXT_CHARS + 100)
        val result = FfiPayloadGuard.capAssistantText(text)!!
        assertEquals(
            FfiPayloadGuard.MAX_ASSISTANT_TEXT_CHARS + FfiPayloadGuard.ASSISTANT_TEXT_TRUNCATION_MARKER.length,
            result.length,
        )
        assertTrue(result.endsWith(FfiPayloadGuard.ASSISTANT_TEXT_TRUNCATION_MARKER))
        assertTrue(result.startsWith("xxxx"))
    }

    @Test
    fun `capAssistantText logs a warning when truncating`() {
        val recorder = RecordingLog()
        val text = "y".repeat(FfiPayloadGuard.MAX_ASSISTANT_TEXT_CHARS + 1)
        FfiPayloadGuard.capAssistantText(text, recorder)
        assertEquals(1, recorder.warnings.size)
        val (tag, message) = recorder.warnings[0]
        assertEquals("RustAgentEngineAdapter", tag)
        assertTrue(message.contains("FFI guard truncating assistantText"))
        assertTrue(message.contains("originalChars=${text.length}"))
    }

    @Test
    fun `capAssistantText does not log when under limit`() {
        val recorder = RecordingLog()
        FfiPayloadGuard.capAssistantText("short text", recorder)
        assertTrue(recorder.warnings.isEmpty())
    }

    @Test
    fun `capToolArgs returns input unchanged when under limit`() {
        val args = """{"q":"hello"}"""
        assertSame(args, FfiPayloadGuard.capToolArgs(args, toolName = "web_search"))
    }

    @Test
    fun `capToolArgs returns input unchanged at exactly the limit`() {
        val args = "a".repeat(FfiPayloadGuard.MAX_TOOL_ARGS_CHARS)
        assertSame(args, FfiPayloadGuard.capToolArgs(args, toolName = "append_blocks"))
    }

    @Test
    fun `capToolArgs replaces oversized args with synthetic error payload`() {
        val args = "z".repeat(FfiPayloadGuard.MAX_TOOL_ARGS_CHARS + 1)
        val result = FfiPayloadGuard.capToolArgs(args, toolName = "append_blocks")
        assertNotEquals(args, result)
        assertEquals(FfiPayloadGuard.TOOL_ARGS_TRUNCATED_PAYLOAD, result)
        assertTrue(result.contains("tool_arguments_exceeded_ffi_limit"))
    }

    @Test
    fun `capToolArgs logs a warning including tool name when replacing`() {
        val recorder = RecordingLog()
        val args = "z".repeat(FfiPayloadGuard.MAX_TOOL_ARGS_CHARS + 1)
        FfiPayloadGuard.capToolArgs(args, toolName = "append_blocks", loggingPort = recorder)
        assertEquals(1, recorder.warnings.size)
        val (tag, message) = recorder.warnings[0]
        assertEquals("RustAgentEngineAdapter", tag)
        assertTrue(message.contains("FFI guard replacing oversized tool args"))
        assertTrue(message.contains("toolName=append_blocks"))
        assertTrue(message.contains("originalChars=${args.length}"))
    }

    @Test
    fun `capToolArgs warning falls back to placeholder when tool name is null`() {
        val recorder = RecordingLog()
        val args = "z".repeat(FfiPayloadGuard.MAX_TOOL_ARGS_CHARS + 1)
        FfiPayloadGuard.capToolArgs(args, toolName = null, loggingPort = recorder)
        assertEquals(1, recorder.warnings.size)
        assertTrue(recorder.warnings[0].second.contains("toolName=<unknown>"))
    }

    @Test
    fun `capToolArgs does not log when under limit`() {
        val recorder = RecordingLog()
        FfiPayloadGuard.capToolArgs("""{"ok":true}""", toolName = "x", loggingPort = recorder)
        assertTrue(recorder.warnings.isEmpty())
    }

    @Test
    fun `assistant text marker text references truncation`() {
        assertTrue(
            FfiPayloadGuard.ASSISTANT_TEXT_TRUNCATION_MARKER.contains("truncated", ignoreCase = true),
        )
    }

    @Test
    fun `tool args truncated payload is valid-shaped JSON sentinel`() {
        assertTrue(FfiPayloadGuard.TOOL_ARGS_TRUNCATED_PAYLOAD.startsWith("{"))
        assertTrue(FfiPayloadGuard.TOOL_ARGS_TRUNCATED_PAYLOAD.endsWith("}"))
        assertTrue(FfiPayloadGuard.TOOL_ARGS_TRUNCATED_PAYLOAD.contains("_client_error"))
    }

    @Test
    fun `null loggingPort is tolerated when truncating`() {
        val text = "x".repeat(FfiPayloadGuard.MAX_ASSISTANT_TEXT_CHARS + 1)
        val args = "y".repeat(FfiPayloadGuard.MAX_TOOL_ARGS_CHARS + 1)
        val capped = FfiPayloadGuard.capAssistantText(text, loggingPort = null)
        val cappedArgs = FfiPayloadGuard.capToolArgs(args, toolName = "x", loggingPort = null)
        assertFalse(capped == text)
        assertEquals(FfiPayloadGuard.TOOL_ARGS_TRUNCATED_PAYLOAD, cappedArgs)
    }

    private class RecordingLog : LoggingPort {
        val warnings: MutableList<Pair<String, String>> = mutableListOf()

        override fun debug(
            tag: String,
            message: String,
        ) {}

        override fun info(
            tag: String,
            message: String,
        ) {}

        override fun warning(
            tag: String,
            message: String,
        ) {
            warnings += tag to message
        }

        override fun error(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {}

        override fun recordException(
            tag: String,
            message: String,
            throwable: Throwable,
        ) {}
    }
}
