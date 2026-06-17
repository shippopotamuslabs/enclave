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
    fun `CAP-006 Gen3 QNN supports SD15 SDXL and Juggernaut`() {
        val capabilities = LocalImageGenerationCapabilityPolicy.resolve(SnapdragonTier.GEN3_PLUS, LocalImageGenTriState.YES)

        assertEquals(LocalImageGenTriState.YES, capabilities.sd15NpuSupported)
        assertEquals(LocalImageGenTriState.YES, capabilities.sdxlNpuSupported)
        assertEquals(LocalImageGenTriState.YES, capabilities.juggernautXlNpuSupported)
    }

    @Test
    fun `CAP-009 Gen2 QNN blocks Juggernaut with exact reason`() {
        val capabilities = LocalImageGenerationCapabilityPolicy.resolve(SnapdragonTier.GEN2, LocalImageGenTriState.YES)

        assertEquals(LocalImageGenTriState.NO, capabilities.juggernautXlNpuSupported)
        assertEquals(
            "Requires Snapdragon 8 Gen 3+ with QNN.",
            capabilities.disabledReasonsByModelId[LocalImageGenerationModelId.JUGGERNAUT_XL_QNN],
        )
    }

    @Test
    fun `CAP-007 QNN probe failure blocks all local image pack downloads`() {
        val capabilities = LocalImageGenerationCapabilityPolicy.resolve(SnapdragonTier.GEN3_PLUS, LocalImageGenTriState.NO)

        assertFalse(capabilities.isAnyBackendUsable)
        assertEquals(LocalImageGenTriState.NO, capabilities.sd15NpuSupported)
        assertEquals(LocalImageGenTriState.NO, capabilities.sdxlNpuSupported)
    }

    @Test
    fun `CAP-014 Gen1 QNN blocks SD15 NPU because graphs need Hexagon V73`() {
        val capabilities = LocalImageGenerationCapabilityPolicy.resolve(SnapdragonTier.GEN1, LocalImageGenTriState.YES)

        assertEquals(LocalImageGenTriState.NO, capabilities.sd15NpuSupported)
        assertEquals(LocalImageGenTriState.NO, capabilities.sdxlNpuSupported)
        assertEquals(LocalImageGenerationBackendKind.NONE, capabilities.recommendedBackend)
    }

    // CAP-008 ("When LocalImageGenerationCapabilityPortImpl.getCapabilities() runs") is asserted once
    // in feature/inference/.../LocalImageGenerationDevicePolicyTest.kt per its test_spec "When" clause.

    @Test
    fun `MUT-002 QNN only SDXL-family gate fails because Gen2 QNN blocks SDXL and Juggernaut`() {
        val capabilities = LocalImageGenerationCapabilityPolicy.resolve(SnapdragonTier.GEN2, LocalImageGenTriState.YES)

        assertEquals("Requires Snapdragon 8 Gen 3+ with QNN.", capabilities.disabledReasonsByModelId[LocalImageGenerationModelId.SDXL_QNN])
        assertEquals(
            "Requires Snapdragon 8 Gen 3+ with QNN.",
            capabilities.disabledReasonsByModelId[LocalImageGenerationModelId.JUGGERNAUT_XL_QNN],
        )
    }
}
