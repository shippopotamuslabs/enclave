package com.browntowndev.enclave.domain.model.device

/**
 * Point-in-time view of device RAM and swap suitable for gating auxiliary ONNX loads.
 */
data class DeviceMemorySnapshot(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val swapTotalBytes: Long,
    val swapFreeBytes: Long,
    val isLowMemory: Boolean,
) {
    val usedRamBytes: Long
        get() = (totalRamBytes - availableRamBytes).coerceAtLeast(0L)

    val availableRamPlusSwapBytes: Long
        get() = availableRamBytes + swapFreeBytes

    val ramUtilizationRatio: Float
        get() =
            if (totalRamBytes <= 0L) {
                1f
            } else {
                (usedRamBytes.toFloat() / totalRamBytes.toFloat()).coerceIn(0f, 1f)
            }
}
