package com.browntowndev.enclave.domain.port.device

import com.browntowndev.enclave.domain.model.device.DeviceMemorySnapshot

/**
 * Probes live device memory pressure for gating auxiliary ONNX utility models.
 *
 * Implementations must be cheap and safe to call from inference hot paths.
 */
interface DeviceMemoryProbePort {
    fun probe(): DeviceMemorySnapshot

    fun hasHeadroom(
        requiredBytes: Long,
        snapshot: DeviceMemorySnapshot = probe(),
    ): Boolean = snapshot.availableRamPlusSwapBytes >= requiredBytes && !snapshot.isLowMemory

    /** No-op probe that always reports ample headroom. Used in JVM unit tests. */
    object Permissive : DeviceMemoryProbePort {
        private val snapshot =
            DeviceMemorySnapshot(
                totalRamBytes = 16L * 1024 * 1024 * 1024,
                availableRamBytes = 8L * 1024 * 1024 * 1024,
                swapTotalBytes = 4L * 1024 * 1024 * 1024,
                swapFreeBytes = 4L * 1024 * 1024 * 1024,
                isLowMemory = false,
            )

        override fun probe(): DeviceMemorySnapshot = snapshot
    }

    /** Probe that always denies headroom. Useful for skip-path tests. */
    object Constrained : DeviceMemoryProbePort {
        private val snapshot =
            DeviceMemorySnapshot(
                totalRamBytes = 4L * 1024 * 1024 * 1024,
                availableRamBytes = 64L * 1024 * 1024,
                swapTotalBytes = 0L,
                swapFreeBytes = 0L,
                isLowMemory = true,
            )

        override fun probe(): DeviceMemorySnapshot = snapshot

        override fun hasHeadroom(
            requiredBytes: Long,
            snapshot: DeviceMemorySnapshot,
        ): Boolean = false
    }
}
