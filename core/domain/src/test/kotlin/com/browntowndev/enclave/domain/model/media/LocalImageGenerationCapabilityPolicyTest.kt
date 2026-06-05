package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.device.SnapdragonTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class LocalImageGenerationCapabilityPolicyTest {
    @Test
    fun `CAP-005 Gen2 QNN supports SD15 and blocks SDXL with exact reason`() {
        val capabilities = LocalImageGenerationCapabilityPolicy.resolve(SnapdragonTier.GEN2, LocalImageGenTriState.YES)

        assertEquals(LocalImageGenTriState.YES, capabilities.sd15NpuSupported)
        assertEquals(LocalImageGenTriState.NO, capabilities.sdxlNpuSupported)
        assertEquals("Requires Snapdragon 8 Gen 3+ with QNN.", capabilities.disabledReasonsByModelId[LocalImageGenerationModelId.SDXL_QNN])
    }

    @Test
    fun `CAP-006 Gen3 QNN supports SD15 and SDXL`() {
        val capabilities = LocalImageGenerationCapabilityPolicy.resolve(SnapdragonTier.GEN3_PLUS, LocalImageGenTriState.YES)

        assertEquals(LocalImageGenTriState.YES, capabilities.sd15NpuSupported)
        assertEquals(LocalImageGenTriState.YES, capabilities.sdxlNpuSupported)
    }

    @Test
    fun `CAP-007 QNN probe failure blocks all local image pack downloads`() {
        val capabilities = LocalImageGenerationCapabilityPolicy.resolve(SnapdragonTier.GEN3_PLUS, LocalImageGenTriState.NO)

        assertFalse(capabilities.isAnyBackendUsable)
        assertEquals(LocalImageGenTriState.NO, capabilities.sd15NpuSupported)
        assertEquals(LocalImageGenTriState.NO, capabilities.sdxlNpuSupported)
    }

    // CAP-008 ("When LocalImageGenerationCapabilityPortImpl.getCapabilities() runs") is asserted once
    // in feature/inference/.../LocalImageGenerationDevicePolicyTest.kt per its test_spec "When" clause.

    @Test
    fun `MUT-002 QNN only SDXL gate fails because Gen2 QNN blocks SDXL`() {
        val capabilities = LocalImageGenerationCapabilityPolicy.resolve(SnapdragonTier.GEN2, LocalImageGenTriState.YES)

        assertEquals("Requires Snapdragon 8 Gen 3+ with QNN.", capabilities.disabledReasonsByModelId[LocalImageGenerationModelId.SDXL_QNN])
    }
}
