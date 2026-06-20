package com.browntowndev.enclave.feature.inference.appendonly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SessionIndexVolatilePlacementTest {
    @Test
    @DisplayName("S-SIP-01 live marker emitted only at tailInsertIndex")
    fun S_SIP_01_live_marker_emitted_only_at_tailInsertIndex() {
        val liveSidecar = ResolvedSidecar(text = "live-body", markerId = LIVE)

        val atTail =
            SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(
                sessionIndex = 2,
                frozenSidecars = emptyList(),
                preRetrievalSidecar = null,
                preRetrievalInsertIndex = null,
                liveSidecar = liveSidecar,
                tailInsertIndex = 2,
            )
        assertEquals(listOf(SidecarMarker(markerId = LIVE, body = "live-body")), atTail)
        assertEquals("99999999-8888-7777-6666-555555555555", atTail[0].markerId)
        assertEquals("live-body", atTail[0].body)

        val offTail =
            SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(
                sessionIndex = 1,
                frozenSidecars = emptyList(),
                preRetrievalSidecar = null,
                preRetrievalInsertIndex = null,
                liveSidecar = liveSidecar,
                tailInsertIndex = 2,
            )
        assertTrue(offTail.isEmpty())
    }

    @Test
    @DisplayName("S-SIP-02 frozen markers emitted at mappedInsertIndex order preserved")
    fun S_SIP_02_frozen_markers_emitted_at_mappedInsertIndex_order_preserved() {
        val frozenSidecars =
            listOf(
                FrozenSidecarSnapshot(text = "frozen-A", markerId = FROZEN_A, mappedInsertIndex = 0),
                FrozenSidecarSnapshot(text = "frozen-B", markerId = FROZEN_B, mappedInsertIndex = 0),
            )

        val result =
            SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(
                sessionIndex = 0,
                frozenSidecars = frozenSidecars,
                preRetrievalSidecar = null,
                preRetrievalInsertIndex = null,
                liveSidecar = null,
                tailInsertIndex = 5,
            )

        assertEquals(
            listOf(
                SidecarMarker(FROZEN_A, "frozen-A"),
                SidecarMarker(FROZEN_B, "frozen-B"),
            ),
            result,
        )
        assertEquals(2, result.size)
        assertEquals("11111111-2222-3333-4444-555555555555", result[0].markerId)
        assertEquals("66666666-7777-8888-9999-aaaaaaaaaaaa", result[1].markerId)
    }

    @Test
    @DisplayName("S-SIP-03 null pre-retrieval emits nothing even when index matches")
    fun S_SIP_03_null_pre_retrieval_emits_nothing_when_index_matches() {
        val result =
            SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(
                sessionIndex = 1,
                frozenSidecars = emptyList(),
                preRetrievalSidecar = null,
                preRetrievalInsertIndex = 1,
                liveSidecar = null,
                tailInsertIndex = 3,
            )

        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("S-SIP-04 non-matching session index returns empty list")
    fun S_SIP_04_non_matching_session_index_returns_empty_list() {
        val result =
            SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(
                sessionIndex = 2,
                frozenSidecars =
                    listOf(
                        FrozenSidecarSnapshot(text = "frozen-A", markerId = FROZEN_A, mappedInsertIndex = 0),
                    ),
                preRetrievalSidecar = null,
                preRetrievalInsertIndex = null,
                liveSidecar = ResolvedSidecar("live-body", LIVE),
                tailInsertIndex = 3,
            )

        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("S-SIP-05 frozen-then-live selection order at the same index")
    fun S_SIP_05_frozen_then_live_selection_order_at_same_index() {
        val result =
            SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(
                sessionIndex = 2,
                frozenSidecars =
                    listOf(
                        FrozenSidecarSnapshot(text = "frozen-A", markerId = FROZEN_A, mappedInsertIndex = 2),
                    ),
                preRetrievalSidecar = null,
                preRetrievalInsertIndex = null,
                liveSidecar = ResolvedSidecar("live-body", LIVE),
                tailInsertIndex = 2,
            )

        assertEquals(
            listOf(
                SidecarMarker(FROZEN_A, "frozen-A"),
                SidecarMarker(LIVE, "live-body"),
            ),
            result,
        )
        assertEquals(2, result.size)
        assertEquals(FROZEN_A, result[0].markerId)
        assertEquals(LIVE, result[1].markerId)
    }

    @Test
    @DisplayName("S-SIP-06 mutation-defense emitted markers carry input markerId not reminted")
    fun S_SIP_06_mutation_defense_emitted_markers_carry_input_markerId() {
        val result =
            SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(
                sessionIndex = 0,
                frozenSidecars =
                    listOf(
                        FrozenSidecarSnapshot(text = "frozen-A", markerId = FROZEN_A, mappedInsertIndex = 0),
                    ),
                preRetrievalSidecar = null,
                preRetrievalInsertIndex = null,
                liveSidecar = ResolvedSidecar("live-body", LIVE),
                tailInsertIndex = 0,
            )

        assertEquals("11111111-2222-3333-4444-555555555555", result[0].markerId)
        assertEquals("99999999-8888-7777-6666-555555555555", result[1].markerId)
    }

    @Test
    @DisplayName("S-SIP-07 pre-retrieval marker emitted at matching index when non-null")
    fun S_SIP_07_pre_retrieval_marker_emitted_at_matching_index_when_non_null() {
        val result =
            SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(
                sessionIndex = 1,
                frozenSidecars = emptyList(),
                preRetrievalSidecar = ResolvedSidecar(text = "preretr-body", markerId = PRERETR),
                preRetrievalInsertIndex = 1,
                liveSidecar = null,
                tailInsertIndex = 3,
            )

        assertEquals(listOf(SidecarMarker(markerId = PRERETR, body = "preretr-body")), result)
        assertEquals(1, result.size)
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", result[0].markerId)
        assertEquals("preretr-body", result[0].body)
    }

    private companion object {
        private const val FROZEN_A = "11111111-2222-3333-4444-555555555555"
        private const val FROZEN_B = "66666666-7777-8888-9999-aaaaaaaaaaaa"
        private const val LIVE = "99999999-8888-7777-6666-555555555555"
        private const val PRERETR = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    }
}
