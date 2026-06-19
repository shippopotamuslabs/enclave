package com.browntowndev.enclave.feature.inference.appendonly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProviderAppendOnlyContextTest {
    @Test
    fun AOC_01_stable_prefix_reuse() {
        val context = ProviderAppendOnlyContext()
        val first =
            context.ensureStablePrefix(
                systemPrompt = "You are a helpful assistant.",
                toolsFingerprint = "fp_tools_abc123",
            )
        val second =
            context.ensureStablePrefix(
                systemPrompt = "You are a helpful assistant.",
                toolsFingerprint = "fp_tools_abc123",
            )

        assertEquals(1, first.version)
        assertEquals(1, second.version)
        assertEquals("fp_tools_abc123", second.toolsFingerprint)
        assertEquals("You are a helpful assistant.", second.systemPrompt)
    }

    @Test
    fun AOC_04_two_sidecar_snapshots_in_one_session() {
        val context = ProviderAppendOnlyContext()
        context.appendVolatileSidecar(
            sidecarText = """State ledger JSON: {"v":1}""",
            delivery = VolatileContextDelivery.MergeIntoSystemPrompt,
        )
        context.appendVolatileSidecar(
            sidecarText = """State ledger JSON: {"v":2}""",
            delivery = VolatileContextDelivery.MergeIntoSystemPrompt,
        )

        val texts = context.committedVolatileSidecarTexts()
        assertEquals(2, texts.size)
        assertEquals("""State ledger JSON: {"v":1}""", texts[0])
        assertEquals("""State ledger JSON: {"v":2}""", texts[1])
        assertEquals(false, texts.any { it.contains("\"v\":3") })
    }

    @Test
    fun AOC_05_in_place_content_change_clears_log() {
        val context = ProviderAppendOnlyContext()
        context.syncMessages(
            listOf(
                ProviderNormalizedMessage(role = "user", content = "Hello"),
                ProviderNormalizedMessage(role = "assistant", content = "Hi"),
            ),
        )
        assertEquals(2, context.committedMessageCount())

        context.syncMessages(
            listOf(
                ProviderNormalizedMessage(role = "user", content = "Hello altered"),
                ProviderNormalizedMessage(role = "assistant", content = "Hi"),
            ),
        )

        assertEquals(1, context.clearCount)
        assertEquals(2, context.committedMessageCount())
        assertEquals("Hello altered", context.logMessages()[0].content)
        assertNotEquals("Hello", context.logMessages()[0].content)
    }

    @Test
    fun AOC_05_digest_inequality_triggers_clear() {
        val first =
            ProviderMessageDigest.compute(
                listOf(ProviderNormalizedMessage(role = "user", content = "Hello")),
            )
        val second =
            ProviderMessageDigest.compute(
                listOf(ProviderNormalizedMessage(role = "user", content = "Hello altered")),
            )
        assertNotEquals(first, second)
    }

    @Test
    fun AOC_13_shorter_normalized_array_clears_log() {
        val context = ProviderAppendOnlyContext()
        context.syncMessages(
            List(4) { index ->
                ProviderNormalizedMessage(role = "user", content = "msg$index")
            },
        )
        assertEquals(4, context.committedMessageCount())

        context.syncMessages(
            listOf(
                ProviderNormalizedMessage(role = "user", content = "a"),
                ProviderNormalizedMessage(role = "user", content = "b"),
            ),
        )

        assertEquals(2, context.committedMessageCount())
        assertEquals(2, context.logMessages().size)
    }

    @Test
    fun frozen_sidecars_accumulate_with_mapped_indices() {
        val context = ProviderAppendOnlyContext()
        context.endTurnFreezeSidecar("s1", mappedInsertIndex = 0)
        context.endTurnFreezeSidecar("s2", mappedInsertIndex = 2)
        context.endTurnFreezeSidecar("s3", mappedInsertIndex = 4)

        val snapshots = context.frozenSidecarsForWireReplay()
        assertEquals(3, snapshots.size)
        assertEquals("s1", snapshots[0].text)
        assertEquals(0, snapshots[0].mappedInsertIndex)
        assertEquals("s2", snapshots[1].text)
        assertEquals(2, snapshots[1].mappedInsertIndex)
        assertEquals("s3", snapshots[2].text)
        assertEquals(4, snapshots[2].mappedInsertIndex)
    }

    @Test
    fun cross_turn_reanchor_preserves_frozen_sidecars_on_prefix_digest_drift() {
        val context = ProviderAppendOnlyContext()
        context.endTurnFreezeSidecar("s1", mappedInsertIndex = 1)
        context.syncMessages(
            listOf(ProviderNormalizedMessage(role = "user", content = "Hello")),
        )
        context.markTurnBoundaryForReanchor()
        context.syncMessages(
            listOf(
                ProviderNormalizedMessage(role = "user", content = "Hello"),
                ProviderNormalizedMessage(
                    role = "assistant",
                    content = "",
                    toolCalls = """[{"id":"call_1","name":"web_search"}]""",
                ),
            ),
        )

        assertEquals(0, context.clearCount)
        assertEquals(1, context.frozenSidecarsForWireReplay().size)
        assertEquals(2, context.committedMessageCount())
    }

    @Test
    fun turn_boundary_reanchor_does_not_suppress_within_turn_digest_mismatch() {
        val context = ProviderAppendOnlyContext()
        context.syncMessages(
            listOf(ProviderNormalizedMessage(role = "user", content = "Hello")),
        )
        context.markTurnBoundaryForReanchor()
        context.syncMessages(
            listOf(ProviderNormalizedMessage(role = "user", content = "Hello")),
        )
        context.syncMessages(
            listOf(ProviderNormalizedMessage(role = "user", content = "Hello altered")),
        )

        assertEquals(1, context.clearCount)
        assertEquals("digest_mismatch", context.lastClearReason)
    }

    @Test
    fun turn_boundary_reanchor_still_clears_on_compaction_shorter_array() {
        val context = ProviderAppendOnlyContext()
        context.endTurnFreezeSidecar("frozen-ledger", mappedInsertIndex = 2)
        context.syncMessages(
            listOf(
                ProviderNormalizedMessage(role = "user", content = "prompt"),
                ProviderNormalizedMessage(role = "user", content = "budget"),
                ProviderNormalizedMessage(role = "assistant", content = ""),
                ProviderNormalizedMessage(role = "user", content = """{"results":[]}""", toolCallId = "call_1"),
            ),
        )
        context.markTurnBoundaryForReanchor()
        context.syncMessages(
            listOf(
                ProviderNormalizedMessage(role = "user", content = "prompt"),
                ProviderNormalizedMessage(role = "assistant", content = ""),
                ProviderNormalizedMessage(role = "user", content = """{"results":[]}""", toolCallId = "call_1"),
            ),
        )

        assertEquals(1, context.clearCount)
        assertEquals("compaction_shorter_array", context.lastClearReason)
        assertTrue(context.frozenSidecarsForWireReplay().isEmpty())
    }

    @Test
    fun digest_mismatch_clears_frozen_sidecar_snapshots() {
        val context = ProviderAppendOnlyContext()
        context.endTurnFreezeSidecar("s1", mappedInsertIndex = 1)
        context.syncMessages(
            listOf(ProviderNormalizedMessage(role = "user", content = "Hello")),
        )
        context.syncMessages(
            listOf(ProviderNormalizedMessage(role = "user", content = "Hello altered")),
        )

        assertTrue(context.frozenSidecarsForWireReplay().isEmpty())
    }

    @Test
    fun onLogCleared_fires_on_digest_mismatch_not_session_invalidate() {
        var clearedReason: String? = null
        val context = ProviderAppendOnlyContext(onLogCleared = { clearedReason = it })
        context.syncMessages(
            listOf(ProviderNormalizedMessage(role = "user", content = "Hello")),
        )
        clearedReason = null
        context.syncMessages(
            listOf(ProviderNormalizedMessage(role = "user", content = "Hello altered")),
        )
        assertEquals("digest_mismatch", clearedReason)

        clearedReason = null
        context.invalidate()
        assertNull(clearedReason)
    }

    @Test
    fun AOC_17_toolsFingerprint_change_increments_version() {
        val context = ProviderAppendOnlyContext()
        val first =
            context.ensureStablePrefix(
                systemPrompt = "You are a helpful assistant.",
                toolsFingerprint = "fp_tools_v1",
            )
        val second =
            context.ensureStablePrefix(
                systemPrompt = "You are a helpful assistant.",
                toolsFingerprint = "fp_tools_v2",
            )

        assertEquals(1, first.version)
        assertEquals(2, second.version)
    }

    @Test
    fun rehydrated_shorter_session_clears_frozen_sidecars() {
        val context = ProviderAppendOnlyContext()
        context.endTurnFreezeSidecar("frozen-ledger", mappedInsertIndex = 2)
        context.syncMessages(
            listOf(
                ProviderNormalizedMessage(role = "user", content = "prompt"),
                ProviderNormalizedMessage(role = "user", content = "budget"),
                ProviderNormalizedMessage(role = "assistant", content = ""),
                ProviderNormalizedMessage(role = "user", content = """{"results":[]}""", toolCallId = "call_1"),
            ),
        )
        assertEquals(1, context.committedVolatileSidecarTexts().size)

        context.syncMessages(
            listOf(
                ProviderNormalizedMessage(role = "user", content = "prompt"),
                ProviderNormalizedMessage(role = "assistant", content = ""),
                ProviderNormalizedMessage(role = "user", content = """{"results":[]}""", toolCallId = "call_1"),
            ),
        )

        assertEquals(1, context.clearCount)
        assertEquals("compaction_shorter_array", context.lastClearReason)
        assertTrue(context.frozenSidecarsForWireReplay().isEmpty())
    }

    @Test
    fun append_only_clear_count_stays_zero_when_session_only_grows() {
        val context = ProviderAppendOnlyContext()
        context.endTurnFreezeSidecar("s1", mappedInsertIndex = 0)
        context.syncMessages(
            listOf(ProviderNormalizedMessage(role = "user", content = "Search")),
        )
        context.syncMessages(
            listOf(
                ProviderNormalizedMessage(role = "user", content = "Search"),
                ProviderNormalizedMessage(role = "assistant", content = ""),
                ProviderNormalizedMessage(role = "user", content = """{"results":[]}""", toolCallId = "call_1"),
            ),
        )
        context.syncMessages(
            listOf(
                ProviderNormalizedMessage(role = "user", content = "Search"),
                ProviderNormalizedMessage(role = "assistant", content = ""),
                ProviderNormalizedMessage(role = "user", content = """{"results":[]}""", toolCallId = "call_1"),
                ProviderNormalizedMessage(role = "assistant", content = "Summary"),
            ),
        )

        assertEquals(0, context.clearCount)
        assertEquals(1, context.frozenSidecarsForWireReplay().size)
    }

    @Test
    @DisplayName("SC-DEDUPE-RESOLVE-01 first ship records lastShippedSidecarText")
    fun SC_DEDUPE_RESOLVE_01_firstShipReturnsBuiltAndDedupesSecondCall() {
        val context = ProviderAppendOnlyContext()
        val built = """State ledger JSON: {"turn":1}"""

        assertEquals(built, context.resolveContextSidecarsForWire(built))
        assertNull(context.resolveContextSidecarsForWire(built))
    }

    @Test
    @DisplayName("SC-DEDUPE-RESOLVE-02 semantic change ships again")
    fun SC_DEDUPE_RESOLVE_02_changedStringShipsAgain() {
        val context = ProviderAppendOnlyContext()
        val v1 = """State ledger JSON: {"turn":1}"""
        val v2 = """State ledger JSON: {"turn":2}"""

        context.resolveContextSidecarsForWire(v1)
        assertEquals(v2, context.resolveContextSidecarsForWire(v2))
        assertNull(context.resolveContextSidecarsForWire(v2))
    }

    @Test
    @DisplayName("SC-DEDUPE-RESOLVE-03 post-clear re-ship")
    fun SC_DEDUPE_RESOLVE_03_clearLogStateAndInvalidateResetDedupeMemory() {
        val context = ProviderAppendOnlyContext()
        val sidecar = """State ledger JSON: {"v":1}"""
        context.resolveContextSidecarsForWire(sidecar)
        assertNull(context.resolveContextSidecarsForWire(sidecar))
        context.syncMessages(
            listOf(ProviderNormalizedMessage(role = "user", content = "Hello")),
        )
        context.syncMessages(
            listOf(ProviderNormalizedMessage(role = "user", content = "Hello altered")),
        )
        assertEquals("digest_mismatch", context.lastClearReason)

        assertEquals(sidecar, context.resolveContextSidecarsForWire(sidecar))

        val invalidateContext = ProviderAppendOnlyContext()
        invalidateContext.resolveContextSidecarsForWire(sidecar)
        invalidateContext.invalidate()
        assertEquals(sidecar, invalidateContext.resolveContextSidecarsForWire(sidecar))
    }

    @Test
    @DisplayName("SC-DEDUPE-03 post-invalidate re-ship")
    fun SC_DEDUPE_03_postInvalidateReShip() {
        val context = ProviderAppendOnlyContext()
        val sidecar = """State ledger JSON: {"k":1}"""
        context.endTurnFreezeSidecar(sidecar, mappedInsertIndex = 0)
        assertEquals(1, context.frozenSidecarsForWireReplay().size)
        assertNull(context.resolveContextSidecarsForWire(sidecar))

        context.invalidate()

        assertTrue(context.frozenSidecarsForWireReplay().isEmpty())
        assertEquals(sidecar, context.resolveContextSidecarsForWire(sidecar))
        assertNull(context.resolveContextSidecarsForWire(sidecar))
    }
}