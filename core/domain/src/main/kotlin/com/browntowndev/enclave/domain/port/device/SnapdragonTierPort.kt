package com.browntowndev.enclave.domain.port.device

import com.browntowndev.enclave.domain.model.device.SnapdragonTier

interface SnapdragonTierPort {
    fun currentTier(): SnapdragonTier
}
