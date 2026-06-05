package com.browntowndev.enclave.domain.model.device

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SnapdragonSocClassifierTest {
    @Test
    fun `CAP-001 SM8450 classifies as GEN1`() {
        assertEquals(SnapdragonTier.GEN1, SnapdragonSocClassifier.tier("qcom sm8450"))
    }

    @Test
    fun `CAP-002 SM8550 kalama classifies as GEN2`() {
        assertEquals(SnapdragonTier.GEN2, SnapdragonSocClassifier.tier("qcom sm8550 kalama"))
    }

    @Test
    fun `CAP-003 SM8750 pineapple classifies as GEN3_PLUS`() {
        assertEquals(SnapdragonTier.GEN3_PLUS, SnapdragonSocClassifier.tier("motorola sm8750 pineapple"))
    }

    @Test
    fun `CAP-004 blank hardware classifies as UNKNOWN`() {
        assertEquals(SnapdragonTier.UNKNOWN, SnapdragonSocClassifier.tier(""))
    }
}
