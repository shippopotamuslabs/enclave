package com.browntowndev.enclave.feature.inference.telemetry

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
}
