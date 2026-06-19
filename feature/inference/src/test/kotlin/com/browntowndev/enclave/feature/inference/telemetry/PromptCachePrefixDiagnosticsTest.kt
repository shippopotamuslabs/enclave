package com.browntowndev.enclave.feature.inference.telemetry

import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptCachePrefixDiagnosticsTest {
    @Test
    fun compare_detects_tool_and_wire_changes() {
        val previous =
            PromptCachePrefixDiagnostics.captureShape(
                systemPrompt = "sys",
                toolNames = listOf("web_search"),
                messages = emptyList(),
                frozenSidecarCount = 0,
                liveSidecarPresent = false,
            )
        val current =
            PromptCachePrefixDiagnostics.captureShape(
                systemPrompt = "sys",
                toolNames = listOf("web_search", "append_blocks"),
                messages = emptyList(),
                frozenSidecarCount = 1,
                liveSidecarPresent = true,
            )

        val comparison =
            PromptCachePrefixDiagnostics.compare(
                previous = previous,
                current = current,
                previousInputTokens = 15_712,
                currentInputTokens = 15_452,
            )

        assertTrue(comparison.prefixChanged)
        assertTrue(comparison.changeReasons.contains("tools"))
        assertTrue(comparison.changeReasons.contains("frozen_sidecar_count"))
        assertTrue(comparison.changeReasons.contains("live_sidecar"))
        assertTrue(comparison.changeReasons.contains("input_shrunk"))
        assertEquals(-260, comparison.inputTokenDelta)
    }

    @Test
    fun compare_reports_no_change_when_shape_stable() {
        val shape =
            PromptCachePrefixDiagnostics.captureShape(
                systemPrompt = "stable",
                toolNames = listOf("a", "b"),
                messages = emptyList(),
                frozenSidecarCount = 2,
                liveSidecarPresent = false,
            )

        val comparison =
            PromptCachePrefixDiagnostics.compare(
                previous = shape,
                current = shape,
                previousInputTokens = 1000,
                currentInputTokens = 1200,
            )

        assertFalse(comparison.prefixChanged)
        assertEquals(200, comparison.inputTokenDelta)
    }

    @Test
    fun computePrefixBreak_identical_bodies_match_fully() {
        val body = "TOOLS:web_search\nS:system prompt\nU:hello"

        val report =
            PromptCachePrefixDiagnostics.computePrefixBreak(
                previousBody = body,
                newBody = body,
                windowChars = PromptCachePrefixDiagnostics.PREFIX_BREAK_WINDOW_CHARS,
            )

        assertEquals(body.length, report.matchChars)
        assertEquals(body.length, report.newChars)
        assertEquals(100, report.matchPct)
        assertEquals(body.length, report.divergenceIndex)
    }

    @Test
    fun computePrefixBreak_localizes_first_divergence_with_window() {
        val previousBody = "AAAA-BBBB-CCCC"
        val newBody = "AAAA-XXXX-CCCC"

        val report =
            PromptCachePrefixDiagnostics.computePrefixBreak(
                previousBody = previousBody,
                newBody = newBody,
                windowChars = 4,
            )

        assertEquals(5, report.matchChars)
        assertEquals(5, report.divergenceIndex)
        assertEquals("AAA-", report.contextBefore)
        assertEquals("A-BB", report.oldSnippet)
        assertEquals("A-XX", report.newSnippet)
    }

    @Test
    fun renderWireBody_orders_tools_line_first() {
        val body =
            PromptCachePrefixDiagnostics.renderWireBody(
                systemPrompt = "system text",
                toolNames = listOf("alpha", "beta"),
                messages =
                    listOf(
                        ChatCompletionMessageParam.ofUser(
                            ChatCompletionUserMessageParam
                                .builder()
                                .content("hello")
                                .build(),
                        ),
                    ),
            )

        assertTrue(body.startsWith("TOOLS:alpha,beta\nS:system text\n"))
        assertTrue(body.contains("U:hello"))
    }

    @Test
    fun sanitizeForLog_collapses_control_whitespace_to_one_line() {
        val sanitized =
            PromptCachePrefixDiagnostics.sanitizeForLog(
                "line one\n\tline two\r\n  line three",
            )

        assertEquals("line one line two line three", sanitized)
    }
}
