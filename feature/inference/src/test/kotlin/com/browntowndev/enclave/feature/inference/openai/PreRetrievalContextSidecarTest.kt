package com.browntowndev.enclave.feature.inference.openai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreRetrievalContextSidecarTest {
    @Test
    fun format_adds_marker_once() {
        val formatted = PreRetrievalContextSidecar.format("# RELEVANT WEB SEARCH RESULTS:\nbody")
        assertTrue(formatted.startsWith(PreRetrievalContextSidecar.MARKER))
        assertTrue(formatted.contains("RELEVANT WEB SEARCH RESULTS"))
    }

    @Test
    fun isSidecarText_matches_marker_prefix() {
        assertTrue(PreRetrievalContextSidecar.isSidecarText(PreRetrievalContextSidecar.format("payload")))
        assertEquals(false, PreRetrievalContextSidecar.isSidecarText("State ledger JSON: {}"))
    }
}
