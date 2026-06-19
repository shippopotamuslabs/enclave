package com.browntowndev.enclave.feature.inference.telemetry

import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SidecarDiagnosticsReclassificationTest {
    private val uuidA = "11111111-2222-3333-4444-555555555555"
    private val uuidB = "22222222-3333-4444-5555-666666666666"

    private fun systemParam(text: String): ChatCompletionMessageParam =
        ChatCompletionMessageParam.ofSystem(
            ChatCompletionSystemMessageParam.builder().content(text).build(),
        )

    private fun userParam(text: String): ChatCompletionMessageParam =
        ChatCompletionMessageParam.ofUser(
            ChatCompletionUserMessageParam.builder().content(text).build(),
        )

    @Test
    @DisplayName("S-DIAG-01 captureFrom classifies Sidecar spans in user content")
    fun S_DIAG_01_classifies_user_content_spans() {
        val messages = listOf(
            systemParam("stable-system"),
            userParam("Hello\n\n[Sidecar-$uuidA: ledger]"),
        )
        val manifest = PromptCacheSidecarDiagnostics.captureFrom(
            frozenOnWire = emptyList(),
            liveSidecarText = null,
            preRetrievalSidecarText = null,
            tailInsertIndex = 1,
            mappedBodySize = 2,
            frozenAfterFreezeCount = 0,
            stableSystemPrompt = "stable-system",
            messages = messages,
        )
        assertEquals(1, manifest.userContentSpans.size)
        assertEquals(uuidA, manifest.userContentSpans[0].markerId)
        assertEquals(1, manifest.userContentSpans[0].wireIndex)
    }

    @Test
    @DisplayName("S-DIAG-02 wirePlacements contains only stable_system after migration")
    fun S_DIAG_02_wirePlacements_only_stable_system() {
        val messages = listOf(
            systemParam("stable-system"),
            userParam("Hello\n\n[Sidecar-$uuidA: ledger]"),
        )
        val manifest = PromptCacheSidecarDiagnostics.captureFrom(
            frozenOnWire = emptyList(),
            liveSidecarText = null,
            preRetrievalSidecarText = null,
            tailInsertIndex = 1,
            mappedBodySize = 2,
            frozenAfterFreezeCount = 0,
            stableSystemPrompt = "stable-system",
            messages = messages,
        )
        assertEquals(1, manifest.wirePlacements.size)
        assertEquals("stable_system", manifest.wirePlacements[0].kind)
    }

    @Test
    @DisplayName("S-DIAG-03 SidecarDelta userContentSpanDelta reports new unchanged removed")
    fun S_DIAG_03_userContentSpanDelta() {
        val prevManifest = PromptCacheSidecarDiagnostics.captureFrom(
            frozenOnWire = emptyList(),
            liveSidecarText = null,
            preRetrievalSidecarText = null,
            tailInsertIndex = 1,
            mappedBodySize = 2,
            frozenAfterFreezeCount = 0,
            stableSystemPrompt = "stable-system",
            messages = listOf(systemParam("stable-system"), userParam("Hello\n\n[Sidecar-$uuidA: ledger]")),
        )
        val currManifest = PromptCacheSidecarDiagnostics.captureFrom(
            frozenOnWire = emptyList(),
            liveSidecarText = null,
            preRetrievalSidecarText = null,
            tailInsertIndex = 1,
            mappedBodySize = 2,
            frozenAfterFreezeCount = 0,
            stableSystemPrompt = "stable-system",
            messages = listOf(
                systemParam("stable-system"),
                userParam("Hello\n\n[Sidecar-$uuidA: ledger]\n\n[Sidecar-$uuidB: ledger2]"),
            ),
        )
        val delta = PromptCacheSidecarDiagnostics.diff(prevManifest, currManifest)!!
        assertTrue(delta.userContentSpanDelta.unchangedSpans.any { it.markerId == uuidA })
        assertTrue(delta.userContentSpanDelta.newSpans.any { it.markerId == uuidB })
        assertTrue(delta.userContentSpanDelta.removedSpans.isEmpty())
    }

    @Test
    @DisplayName("S-DIAG-04 extractSystemJsonSpans finds exactly one system span")
    fun S_DIAG_04_extractSystemJsonSpans_one_span() {
        val wireJson = """{"messages":[{"role":"system","content":"stable"},{"role":"user","content":"Hi"}]}"""
        val spans = PromptCacheSidecarDiagnostics.extractSystemJsonSpans(wireJson)
        assertEquals(1, spans.size)
    }

    @Test
    @DisplayName("S-DIAG-05 user content spans share sidecar block parsing semantics")
    fun S_DIAG_05_user_content_spans_share_sidecar_block_parsing_semantics() {
        val block = "[Sidecar-$uuidA: {\"items\":[\"a\",\"b\"]} [Sidecar-$uuidB: nested-marker-like text]]"
        val manifest = PromptCacheSidecarDiagnostics.captureFrom(
            frozenOnWire = emptyList(),
            liveSidecarText = null,
            preRetrievalSidecarText = null,
            tailInsertIndex = 1,
            mappedBodySize = 2,
            frozenAfterFreezeCount = 0,
            stableSystemPrompt = "stable-system",
            messages = listOf(systemParam("stable-system"), userParam("Hello\n\n$block")),
        )

        assertEquals(1, manifest.userContentSpans.size)
        assertEquals(uuidA, manifest.userContentSpans[0].markerId)
        assertEquals(block.length, manifest.userContentSpans[0].length)
    }
}