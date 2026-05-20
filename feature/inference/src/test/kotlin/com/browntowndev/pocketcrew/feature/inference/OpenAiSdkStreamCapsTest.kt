package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Stream-time cap tests. Verifies that oversized provider deltas, runaway accumulated assistant
 * text, and oversized tool-call argument streams are all bounded before they hit the FFI boundary.
 */
class OpenAiSdkStreamCapsTest {
    // ── isDeltaChunkOversize ─────────────────────────────────────────────────

    @Test
    fun `isDeltaChunkOversize is false for empty chunk`() {
        assertFalse(OpenAiSdkStreamCaps.isDeltaChunkOversize(0))
    }

    @Test
    fun `isDeltaChunkOversize is false at exactly the limit`() {
        assertFalse(OpenAiSdkStreamCaps.isDeltaChunkOversize(OpenAiSdkStreamCaps.MAX_DELTA_CHUNK_CHARS))
    }

    @Test
    fun `isDeltaChunkOversize is true one over the limit`() {
        assertTrue(OpenAiSdkStreamCaps.isDeltaChunkOversize(OpenAiSdkStreamCaps.MAX_DELTA_CHUNK_CHARS + 1))
    }

    // ── applyAssistantTextCap ────────────────────────────────────────────────

    @Test
    fun `applyAssistantTextCap leaves builder unchanged when under limit`() {
        val sb = StringBuilder("hello world")
        val capped = OpenAiSdkStreamCaps.applyAssistantTextCap(sb)
        assertFalse(capped)
        assertEquals("hello world", sb.toString())
    }

    @Test
    fun `applyAssistantTextCap leaves builder unchanged at exactly the limit`() {
        val sb = StringBuilder("a".repeat(OpenAiSdkStreamCaps.MAX_ASSISTANT_TEXT_CHARS))
        val capped = OpenAiSdkStreamCaps.applyAssistantTextCap(sb)
        assertFalse(capped)
        assertEquals(OpenAiSdkStreamCaps.MAX_ASSISTANT_TEXT_CHARS, sb.length)
    }

    @Test
    fun `applyAssistantTextCap truncates and appends marker when over limit`() {
        val sb = StringBuilder("x".repeat(OpenAiSdkStreamCaps.MAX_ASSISTANT_TEXT_CHARS + 500))
        val capped = OpenAiSdkStreamCaps.applyAssistantTextCap(sb)
        assertTrue(capped)
        assertEquals(
            OpenAiSdkStreamCaps.MAX_ASSISTANT_TEXT_CHARS +
                OpenAiSdkStreamCaps.ASSISTANT_TEXT_TRUNCATION_MARKER.length,
            sb.length,
        )
        assertTrue(sb.toString().endsWith(OpenAiSdkStreamCaps.ASSISTANT_TEXT_TRUNCATION_MARKER))
    }

    @Test
    fun `applyAssistantTextCap logs warning with model id when truncating`() {
        val recorder = RecordingLog()
        val sb = StringBuilder("y".repeat(OpenAiSdkStreamCaps.MAX_ASSISTANT_TEXT_CHARS + 1))
        OpenAiSdkStreamCaps.applyAssistantTextCap(
            sb,
            loggingPort = recorder,
            tag = "TestTag",
            modelId = "gpt-mini",
        )
        assertEquals(1, recorder.warnings.size)
        val (tag, message) = recorder.warnings[0]
        assertEquals("TestTag", tag)
        assertTrue(message.contains("Capping assistantText"))
        assertTrue(message.contains("model=gpt-mini"))
        assertTrue(message.contains("ending stream early"))
    }

    @Test
    fun `applyAssistantTextCap tolerates null loggingPort`() {
        val sb = StringBuilder("z".repeat(OpenAiSdkStreamCaps.MAX_ASSISTANT_TEXT_CHARS + 1))
        val capped = OpenAiSdkStreamCaps.applyAssistantTextCap(sb)
        assertTrue(capped)
    }

    @Test
    fun `applyAssistantTextCap reports original length in log even after truncating`() {
        val recorder = RecordingLog()
        val original = OpenAiSdkStreamCaps.MAX_ASSISTANT_TEXT_CHARS + 42
        val sb = StringBuilder("a".repeat(original))
        OpenAiSdkStreamCaps.applyAssistantTextCap(sb, loggingPort = recorder, tag = "T")
        assertTrue(recorder.warnings[0].second.contains("actual=$original"))
    }

    // ── appendToolArgsChunkOrReject ──────────────────────────────────────────

    @Test
    fun `appendToolArgsChunkOrReject appends when both buffer and chunk are small`() {
        val sb = StringBuilder("{")
        val rejected = OpenAiSdkStreamCaps.appendToolArgsChunkOrReject(sb, "\"a\":1}", toolName = "x")
        assertFalse(rejected)
        assertEquals("{\"a\":1}", sb.toString())
    }

    @Test
    fun `appendToolArgsChunkOrReject appends when sum equals limit`() {
        val sb = StringBuilder("a".repeat(OpenAiSdkStreamCaps.MAX_TOOL_ARGS_CHARS - 5))
        val rejected = OpenAiSdkStreamCaps.appendToolArgsChunkOrReject(sb, "bbbbb", toolName = "x")
        assertFalse(rejected)
        assertEquals(OpenAiSdkStreamCaps.MAX_TOOL_ARGS_CHARS, sb.length)
    }

    @Test
    fun `appendToolArgsChunkOrReject rejects when sum exceeds limit by one`() {
        val sb = StringBuilder("a".repeat(OpenAiSdkStreamCaps.MAX_TOOL_ARGS_CHARS - 1))
        val before = sb.length
        val rejected = OpenAiSdkStreamCaps.appendToolArgsChunkOrReject(sb, "bb", toolName = "x")
        assertTrue(rejected)
        assertEquals(before, sb.length)
    }

    @Test
    fun `appendToolArgsChunkOrReject rejects when chunk alone exceeds limit`() {
        val sb = StringBuilder()
        val chunk = "c".repeat(OpenAiSdkStreamCaps.MAX_TOOL_ARGS_CHARS + 1)
        val rejected = OpenAiSdkStreamCaps.appendToolArgsChunkOrReject(sb, chunk, toolName = "x")
        assertTrue(rejected)
        assertEquals(0, sb.length)
    }

    @Test
    fun `appendToolArgsChunkOrReject logs warning with accumulated and chunk size on reject`() {
        val recorder = RecordingLog()
        val sb = StringBuilder("a".repeat(OpenAiSdkStreamCaps.MAX_TOOL_ARGS_CHARS - 1))
        val accumulated = sb.length
        val rejected =
            OpenAiSdkStreamCaps.appendToolArgsChunkOrReject(
                sb,
                "bb",
                toolName = "append_blocks",
                loggingPort = recorder,
                tag = "TestTag",
                modelId = "gpt-mini",
            )
        assertTrue(rejected)
        assertEquals(1, recorder.warnings.size)
        val (tag, msg) = recorder.warnings[0]
        assertEquals("TestTag", tag)
        assertTrue(msg.contains("Capping tool call args"))
        assertTrue(msg.contains("toolName=append_blocks"))
        assertTrue(msg.contains("model=gpt-mini"))
        assertTrue(msg.contains("accumulated=$accumulated"))
        assertTrue(msg.contains("attemptedChunk=2"))
    }

    @Test
    fun `appendToolArgsChunkOrReject does not log when chunk fits`() {
        val recorder = RecordingLog()
        val sb = StringBuilder()
        OpenAiSdkStreamCaps.appendToolArgsChunkOrReject(
            sb,
            "small",
            toolName = "x",
            loggingPort = recorder,
            tag = "T",
        )
        assertTrue(recorder.warnings.isEmpty())
    }

    @Test
    fun `appendToolArgsChunkOrReject falls back to placeholder for null tool name`() {
        val recorder = RecordingLog()
        val sb = StringBuilder("a".repeat(OpenAiSdkStreamCaps.MAX_TOOL_ARGS_CHARS))
        OpenAiSdkStreamCaps.appendToolArgsChunkOrReject(
            sb,
            "x",
            toolName = null,
            loggingPort = recorder,
            tag = "T",
        )
        assertTrue(recorder.warnings[0].second.contains("toolName=<unknown>"))
    }

    @Test
    fun `tool args truncated payload sentinel is well-formed and identifiable`() {
        assertTrue(OpenAiSdkStreamCaps.TOOL_ARGS_TRUNCATED_PAYLOAD.startsWith("{"))
        assertTrue(OpenAiSdkStreamCaps.TOOL_ARGS_TRUNCATED_PAYLOAD.endsWith("}"))
        assertTrue(OpenAiSdkStreamCaps.TOOL_ARGS_TRUNCATED_PAYLOAD.contains("_client_error"))
        assertTrue(
            OpenAiSdkStreamCaps.TOOL_ARGS_TRUNCATED_PAYLOAD.contains("tool_arguments_exceeded_max_bytes"),
        )
    }

    @Test
    fun `assistant text truncation marker mentions truncation`() {
        assertTrue(
            OpenAiSdkStreamCaps.ASSISTANT_TEXT_TRUNCATION_MARKER.contains("truncated", ignoreCase = true),
        )
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
