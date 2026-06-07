package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.device.SnapdragonTier

data class LocalImageGenerationCapabilities(
    val snapdragonTier: SnapdragonTier = SnapdragonTier.UNKNOWN,
    val npuAvailable: LocalImageGenTriState,
    val sd15NpuSupported: LocalImageGenTriState,
    val sd15CpuSupported: LocalImageGenTriState,
    val sdxlNpuSupported: LocalImageGenTriState,
    val juggernautXlNpuSupported: LocalImageGenTriState = LocalImageGenTriState.NO,
    val recommendedBackend: LocalImageGenerationBackendKind,
    val recommendedModelId: LocalImageGenerationModelId?,
    val diagnostics: List<String>,
    val disabledReasonsByModelId: Map<LocalImageGenerationModelId, String> = emptyMap(),
) {
    val isAnyBackendUsable: Boolean =
        sd15NpuSupported == LocalImageGenTriState.YES ||
            sd15CpuSupported == LocalImageGenTriState.YES ||
            sdxlNpuSupported == LocalImageGenTriState.YES ||
            juggernautXlNpuSupported == LocalImageGenTriState.YES
}
