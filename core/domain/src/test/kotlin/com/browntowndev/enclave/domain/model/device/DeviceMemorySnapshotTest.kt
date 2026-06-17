package com.browntowndev.enclave.domain.model.device

import com.browntowndev.enclave.domain.port.device.DeviceMemoryProbePort
import com.browntowndev.enclave.domain.model.inference.UtilityOnnxRuntimeRequirements
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceMemorySnapshotTest {
    @Test
    fun derivedFieldsComputeRamPlusSwapHeadroom() {
        val snapshot =
            DeviceMemorySnapshot(
                totalRamBytes = 8L * 1024 * 1024 * 1024,
                availableRamBytes = 2L * 1024 * 1024 * 1024,
                swapTotalBytes = 2L * 1024 * 1024 * 1024,
                swapFreeBytes = 1L * 1024 * 1024 * 1024,
                isLowMemory = false,
            )

        assertEquals(6L * 1024 * 1024 * 1024, snapshot.usedRamBytes)
        assertEquals(3L * 1024 * 1024 * 1024, snapshot.availableRamPlusSwapBytes)
        assertEquals(0.75f, snapshot.ramUtilizationRatio, 0.001f)
    }

    @Test
    fun permissiveProbeGrantsHeadroom() {
        val probe = DeviceMemoryProbePort.Permissive
        assertTrue(probe.hasHeadroom(UtilityOnnxRuntimeRequirements.LFM2_SESSION_BYTES))
    }

    @Test
    fun constrainedProbeDeniesHeadroom() {
        val probe = DeviceMemoryProbePort.Constrained
        assertFalse(probe.hasHeadroom(UtilityOnnxRuntimeRequirements.LFM2_SESSION_BYTES))
    }
}