package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.device.SnapdragonTier

object LocalImageGenerationCapabilityPolicy {
    private const val MIN_RAM_BYTES = 8L * 1024 * 1024 * 1024
    const val SDXL_BLOCKED_REASON = "Requires Snapdragon 8 Gen 3+ with QNN."

    fun resolve(
        snapdragonTier: SnapdragonTier,
        npuAvailable: LocalImageGenTriState,
        totalMemoryBytes: Long = Long.MAX_VALUE,
        isArm64: Boolean = true,
    ): LocalImageGenerationCapabilities {
        if (!isArm64 || totalMemoryBytes < MIN_RAM_BYTES || snapdragonTier == SnapdragonTier.UNKNOWN) {
            return blockedCapabilities(
                snapdragonTier = snapdragonTier,
                npuAvailable = LocalImageGenTriState.NO,
            )
        }

        if (npuAvailable == LocalImageGenTriState.NO) {
            return LocalImageGenerationCapabilities(
                snapdragonTier = snapdragonTier,
                npuAvailable = LocalImageGenTriState.NO,
                sd15NpuSupported = LocalImageGenTriState.NO,
                sd15CpuSupported = LocalImageGenTriState.NO,
                sdxlNpuSupported = LocalImageGenTriState.NO,
                recommendedBackend = LocalImageGenerationBackendKind.NONE,
                recommendedModelId = null,
                diagnostics = emptyList(),
            )
        }

        val sd15Npu =
            when (npuAvailable) {
                LocalImageGenTriState.YES -> LocalImageGenTriState.YES
                LocalImageGenTriState.UNKNOWN -> LocalImageGenTriState.UNKNOWN
                LocalImageGenTriState.NO -> LocalImageGenTriState.NO
            }
        val sdxlFamilyNpu =
            when {
                npuAvailable != LocalImageGenTriState.YES -> LocalImageGenTriState.NO
                snapdragonTier == SnapdragonTier.GEN3_PLUS -> LocalImageGenTriState.YES
                npuAvailable == LocalImageGenTriState.UNKNOWN -> LocalImageGenTriState.UNKNOWN
                else -> LocalImageGenTriState.NO
            }
        val disabledReasons =
            buildMap {
                if (sdxlFamilyNpu == LocalImageGenTriState.NO &&
                    npuAvailable == LocalImageGenTriState.YES &&
                    snapdragonTier != SnapdragonTier.GEN3_PLUS
                ) {
                    put(LocalImageGenerationModelId.SDXL_QNN, SDXL_BLOCKED_REASON)
                    put(LocalImageGenerationModelId.JUGGERNAUT_XL_QNN, SDXL_BLOCKED_REASON)
                }
            }

        return LocalImageGenerationCapabilities(
            snapdragonTier = snapdragonTier,
            npuAvailable = npuAvailable,
            sd15NpuSupported = sd15Npu,
            sd15CpuSupported = LocalImageGenTriState.NO,
            sdxlNpuSupported = sdxlFamilyNpu,
            juggernautXlNpuSupported = sdxlFamilyNpu,
            recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
            recommendedModelId = LocalImageGenerationModelId.SD15_QNN,
            diagnostics = emptyList(),
            disabledReasonsByModelId = disabledReasons,
        )
    }

    private fun blockedCapabilities(
        snapdragonTier: SnapdragonTier,
        npuAvailable: LocalImageGenTriState,
    ): LocalImageGenerationCapabilities =
        LocalImageGenerationCapabilities(
            snapdragonTier = snapdragonTier,
            npuAvailable = npuAvailable,
            sd15NpuSupported = LocalImageGenTriState.NO,
            sd15CpuSupported = LocalImageGenTriState.NO,
            sdxlNpuSupported = LocalImageGenTriState.NO,
            juggernautXlNpuSupported = LocalImageGenTriState.NO,
            recommendedBackend = LocalImageGenerationBackendKind.NONE,
            recommendedModelId = null,
            diagnostics = emptyList(),
        )
}
