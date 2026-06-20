package com.browntowndev.enclave.feature.inference.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptCacheSidecarDiagnosticsTest {
    @Test
    fun diff_detects_new_frozen_slot_and_tail_shift() {
        val previous =
            PromptCacheSidecarDiagnostics.captureFrom(
                frozenOnWire =
                    listOf(
                        PromptCacheSidecarDiagnostics.slotFrom(
                            mappedInsertIndex = 2,
                            text = "ledger-v1",
                        ),
                    ),
                liveSidecarText = "ledger-v2",
                preRetrievalSidecarText = null,
                tailInsertIndex = 4,
                mappedBodySize = 5,
                frozenAfterFreezeCount = 1,
                stableSystemPrompt = "stable-system",
                messages = emptyList(),
            )
        val current =
            PromptCacheSidecarDiagnostics.captureFrom(
                frozenOnWire =
                    listOf(
                        PromptCacheSidecarDiagnostics.slotFrom(
                            mappedInsertIndex = 2,
                            text = "ledger-v1",
                        ),
                        PromptCacheSidecarDiagnostics.slotFrom(
                            mappedInsertIndex = 4,
                            text = "ledger-v2",
                        ),
                    ),
                liveSidecarText = "ledger-v3",
                preRetrievalSidecarText = null,
                tailInsertIndex = 6,
                mappedBodySize = 7,
                frozenAfterFreezeCount = 2,
                stableSystemPrompt = "stable-system",
                messages = emptyList(),
            )

        val delta = PromptCacheSidecarDiagnostics.diff(previous, current)

        requireNotNull(delta)
        assertEquals(1, delta.newFrozenSlots.size)
        assertEquals(4, delta.newFrozenSlots.first().mappedInsertIndex)
        assertTrue(delta.liveTextChanged)
        assertEquals(2, delta.tailInsertIndexDelta)
        assertEquals(1, delta.frozenAfterFreezeDelta)
    }

    @Test
    fun extractSystemJsonSpans_finds_system_roles_in_wire_json() {
        val json =
            """
            {"messages":[
            {"role":"system","content":"stable"},
            {"role":"user","content":"hi"},
            {"role":"system","content":"sidecar-state"}
            ],"model":"deepseek-v4-flash"}
            """.trimIndent()

        val spans = PromptCacheSidecarDiagnostics.extractSystemJsonSpans(json)

        assertEquals(2, spans.size)
        assertTrue(spans[0].roleCharOffset < spans[1].roleCharOffset)
    }

    @Test
    fun diffSystemJsonSpans_reports_inserted_system_message() {
        val previousJson =
            """{"messages":[{"role":"system","content":"a"},{"role":"user","content":"hi"}]}"""
        val currentJson =
            """{"messages":[{"role":"system","content":"a"},{"role":"user","content":"hi"},{"role":"system","content":"sidecar"}]}"""
        val previousSpans = PromptCacheSidecarDiagnostics.extractSystemJsonSpans(previousJson)
        val currentSpans = PromptCacheSidecarDiagnostics.extractSystemJsonSpans(currentJson)

        val delta = PromptCacheSidecarDiagnostics.diffSystemJsonSpans(previousSpans, currentSpans)

        assertTrue(delta.contains("count=1->2"))
        assertTrue(delta.contains("insert@"))
    }

    @Test
    fun formatEarlyJsonBreak_reports_block0_divergence() {
        val previous = "A".repeat(600)
        val current = "A".repeat(500) + "B".repeat(100)

        val formatted = PromptCacheSidecarDiagnostics.formatEarlyJsonBreak(previous, current)

        assertTrue(formatted.contains("block0Match="))
        assertFalse(formatted.contains("block0Match=600/600(100%)"))
    }
}
