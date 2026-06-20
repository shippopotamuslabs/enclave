package com.browntowndev.enclave.feature.inference.appendonly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
            markerId = "11111111-2222-3333-4444-555555555555",
            delivery = VolatileContextDelivery.MergeIntoSystemPrompt,
        )
        context.appendVolatileSidecar(
            sidecarText = """State ledger JSON: {"v":2}""",
            markerId = "66666666-7777-8888-9999-aaaaaaaaaaaa",
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
        context.endTurnFreezeSidecar("s1", markerId = "11111111-2222-3333-4444-555555555555", mappedInsertIndex = 0)
        context.endTurnFreezeSidecar("s2", markerId = "22222222-3333-4444-5555-666666666666", mappedInsertIndex = 2)
        context.endTurnFreezeSidecar("s3", markerId = "33333333-4444-5555-6666-777777777777", mappedInsertIndex = 4)

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
        context.endTurnFreezeSidecar("s1", markerId = "11111111-2222-3333-4444-555555555555", mappedInsertIndex = 1)
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
        context.endTurnFreezeSidecar("frozen-ledger", markerId = "11111111-2222-3333-4444-555555555555", mappedInsertIndex = 2)
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
        context.endTurnFreezeSidecar("s1", markerId = "11111111-2222-3333-4444-555555555555", mappedInsertIndex = 1)
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
        context.endTurnFreezeSidecar("frozen-ledger", markerId = "11111111-2222-3333-4444-555555555555", mappedInsertIndex = 2)
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
        context.endTurnFreezeSidecar("s1", markerId = "11111111-2222-3333-4444-555555555555", mappedInsertIndex = 0)
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

        assertEquals(built, context.resolveContextSidecarsForWire(built)?.text)
        assertNull(context.resolveContextSidecarsForWire(built))
    }

    @Test
    @DisplayName("SC-DEDUPE-RESOLVE-02 semantic change ships again")
    fun SC_DEDUPE_RESOLVE_02_changedStringShipsAgain() {
        val context = ProviderAppendOnlyContext()
        val v1 = """State ledger JSON: {"turn":1}"""
        val v2 = """State ledger JSON: {"turn":2}"""

        context.resolveContextSidecarsForWire(v1)
        assertEquals(v2, context.resolveContextSidecarsForWire(v2)?.text)
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

        assertEquals(sidecar, context.resolveContextSidecarsForWire(sidecar)?.text)

        val invalidateContext = ProviderAppendOnlyContext()
        invalidateContext.resolveContextSidecarsForWire(sidecar)
        invalidateContext.invalidate()
        assertEquals(sidecar, invalidateContext.resolveContextSidecarsForWire(sidecar)?.text)
    }

    @Test
    @DisplayName("SC-DEDUPE-03 post-invalidate re-ship")
    fun SC_DEDUPE_03_postInvalidateReShip() {
        val context = ProviderAppendOnlyContext()
        val sidecar = """State ledger JSON: {"k":1}"""
        context.endTurnFreezeSidecar(sidecar, markerId = "11111111-2222-3333-4444-555555555555", mappedInsertIndex = 0)
        assertEquals(1, context.frozenSidecarsForWireReplay().size)
        assertNull(context.resolveContextSidecarsForWire(sidecar))

        context.invalidate()

        assertTrue(context.frozenSidecarsForWireReplay().isEmpty())
        assertEquals(sidecar, context.resolveContextSidecarsForWire(sidecar)?.text)
        assertNull(context.resolveContextSidecarsForWire(sidecar))
    }

    @Test
    @DisplayName("S-FREEZE-01 FrozenSidecarSnapshot carries markerId")
    fun S_FREEZE_01_snapshot_carries_markerId() {
        val context = ProviderAppendOnlyContext()
        context.endTurnFreezeSidecar(
            sidecarText = """State ledger JSON: {"v":1}""",
            markerId = "11111111-2222-3333-4444-555555555555",
            mappedInsertIndex = 2,
        )
        val snapshots = context.frozenSidecarsForWireReplay()
        assertEquals(1, snapshots.size)
        assertEquals("""State ledger JSON: {"v":1}""", snapshots[0].text)
        assertEquals("11111111-2222-3333-4444-555555555555", snapshots[0].markerId)
        assertEquals(2, snapshots[0].mappedInsertIndex)
    }

    @Test
    @DisplayName("S-FREEZE-02 resolveContextSidecarsForWire returns ResolvedSidecar with fresh markerId")
    fun S_FREEZE_02_resolve_returns_ResolvedSidecar_with_fresh_markerId() {
        val context = ProviderAppendOnlyContext()
        val resolved = context.resolveContextSidecarsForWire("""State ledger JSON: {"v":1}""")
        assertNotNull(resolved)
        assertEquals("""State ledger JSON: {"v":1}""", resolved!!.text)
        assertTrue(resolved.markerId.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")))
    }

    @Test
    @DisplayName("S-FREEZE-03 dedupe path returns null and allocates no markerId")
    fun S_FREEZE_03_dedupe_returns_null() {
        val context = ProviderAppendOnlyContext()
        context.resolveContextSidecarsForWire("""State ledger JSON: {"v":1}""")
        assertNull(context.resolveContextSidecarsForWire("""State ledger JSON: {"v":1}"""))
    }

    @Test
    @DisplayName("S-FREEZE-04 live to frozen transition reuses same markerId")
    fun S_FREEZE_04_live_to_frozen_reuses_markerId() {
        val context = ProviderAppendOnlyContext()
        val resolved = context.resolveContextSidecarsForWire("""State ledger JSON: {"v":1}""")
        assertNotNull(resolved)
        val markerId = resolved!!.markerId
        context.endTurnFreezeSidecar(
            sidecarText = """State ledger JSON: {"v":1}""",
            markerId = markerId,
            mappedInsertIndex = 1,
        )
        val snapshots = context.frozenSidecarsForWireReplay()
        assertEquals(1, snapshots.size)
        assertEquals(markerId, snapshots[0].markerId)
        assertEquals("""State ledger JSON: {"v":1}""", snapshots[0].text)
    }

    @Test
    @DisplayName("S-FREEZE-05 appendVolatileSidecar stores markerId")
    fun S_FREEZE_05_appendVolatileSidecar_stores_markerId() {
        val context = ProviderAppendOnlyContext()
        context.appendVolatileSidecar(
            sidecarText = "ledger",
            markerId = "11111111-2222-3333-4444-555555555555",
            delivery = VolatileContextDelivery.OpenAiTailBeforeLastSessionMessage(sessionLastIndex = 3),
        )
        val snapshots = context.frozenSidecarsForWireReplay()
        assertEquals(1, snapshots.size)
        assertEquals("11111111-2222-3333-4444-555555555555", snapshots[0].markerId)
        assertEquals(3, snapshots[0].mappedInsertIndex)
    }

    @Test
    @DisplayName("AOC-DCR-01 compaction boundary clear with revision does not double-clear on subsequent sync")
    fun AOC_DCR_01_compactionBoundaryWithRevisionDoesNotDoubleClear() {
        val context = ProviderAppendOnlyContext()
        // Seed append-only state: a frozen sidecar + committed messages at revision 10.
        context.endTurnFreezeSidecar(
            sidecarText = """State ledger JSON: {"v":1}""",
            markerId = "11111111-2222-3333-4444-555555555555",
            mappedInsertIndex = 0,
        )
        context.syncMessages(
            listOf(
                ProviderNormalizedMessage(role = "user", content = "prompt"),
                ProviderNormalizedMessage(role = "assistant", content = "reply"),
            ),
            transcriptRevision = 10L,
        )
        assertEquals(1, context.frozenSidecarsForWireReplay().size)
        assertEquals(0, context.clearCount)

        // Rust emits CompactionBoundaryApplied with revisionAfter=11; the event handler
        // calls clearOnRustCompactionBoundary(11) which must set lastSeenRevision=11.
        context.clearOnRustCompactionBoundary(revisionAfter = 11L)
        assertEquals(1, context.clearCount)
        assertEquals("rust_compaction_boundary", context.lastClearReason)
        assertTrue(context.frozenSidecarsForWireReplay().isEmpty())

        // completeSingleTurn then delivers the compacted messages at the same revision.
        // syncMessages' proactive compaction clear must NOT fire again — lastSeenRevision
        // is already 11, so transcriptRevision(11) > lastSeenRevision(11) is false.
        context.syncMessages(
            listOf(
                ProviderNormalizedMessage(role = "user", content = "compacted prompt"),
            ),
            transcriptRevision = 11L,
        )

        assertEquals(1, context.clearCount, "syncMessages must not double-clear after compaction boundary")
    }

    @Test
    @DisplayName("AOC-DCR-02 compaction boundary clear without revision still allows proactive sync clear")
    fun AOC_DCR_02_compactionBoundaryWithoutRevisionStillProactivelyClears() {
        val context = ProviderAppendOnlyContext()
        context.endTurnFreezeSidecar(
            sidecarText = """State ledger JSON: {"v":1}""",
            markerId = "11111111-2222-3333-4444-555555555555",
            mappedInsertIndex = 0,
        )
        context.syncMessages(
            listOf(ProviderNormalizedMessage(role = "user", content = "prompt")),
            transcriptRevision = 10L,
        )
        assertEquals(0, context.clearCount)

        // KvCacheInvalidated path: no revision available.
        context.clearOnRustCompactionBoundary(revisionAfter = null)
        assertEquals(1, context.clearCount)

        // syncMessages with a revision bump must still proactively clear since
        // lastSeenRevision was not advanced by the boundary clear.
        context.syncMessages(
            listOf(ProviderNormalizedMessage(role = "user", content = "compacted")),
            transcriptRevision = 11L,
        )

        assertEquals(2, context.clearCount, "proactive clear still fires when boundary clear had no revision")
    }
}