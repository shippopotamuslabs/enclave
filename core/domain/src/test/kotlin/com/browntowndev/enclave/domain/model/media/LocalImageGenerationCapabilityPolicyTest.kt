package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.device.SnapdragonTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalImageGenerationCapabilityPolicyTest {
    @Test
    fun `CAP-005 Gen2 QNN supports SD15 and blocks SDXL with exact reason`() {
        val capabilities = LocalImageGenerationCapabilityPolicy.resolve(SnapdragonTier.GEN2, LocalImageGenTriState.YES)

        assertEquals(LocalImageGenTriState.YES, capabilities.sd15NpuSupported)
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
    }

    @Test
    fun `CAP-008 insufficient RAM blocks local image packs with diagnostic`() {
        val capabilities =
            LocalImageGenerationCapabilityPolicy.resolve(
                snapdragonTier = SnapdragonTier.GEN3_PLUS,
                npuAvailable = LocalImageGenTriState.YES,
                totalMemoryBytes = 8_589_934_591L,
            )

        assertTrue(capabilities.diagnostics.contains("Insufficient RAM for local diffusion (need >= 8 GiB)."))
        assertFalse(capabilities.isAnyBackendUsable)
    }

    @Test
    fun `MUT-002 QNN only SDXL gate fails because Gen2 QNN blocks SDXL`() {
        val capabilities = LocalImageGenerationCapabilityPolicy.resolve(SnapdragonTier.GEN2, LocalImageGenTriState.YES)

        assertEquals("Requires Snapdragon 8 Gen 3+ with QNN.", capabilities.disabledReasonsByModelId[LocalImageGenerationModelId.SDXL_QNN])
    }
}
