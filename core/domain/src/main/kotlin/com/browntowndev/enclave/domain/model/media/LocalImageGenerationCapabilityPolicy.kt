package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.device.SnapdragonTier

object LocalImageGenerationCapabilityPolicy {
    fun resolve(
        snapdragonTier: SnapdragonTier,
        npuAvailable: LocalImageGenTriState,
        totalMemoryBytes: Long = Long.MAX_VALUE,
        isArm64: Boolean = true,
    ): LocalImageGenerationCapabilities = throw NotImplementedError("TDD Red")
}
