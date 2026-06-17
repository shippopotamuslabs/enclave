package com.browntowndev.enclave.domain.port.media

import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities

interface LocalImageGenerationCapabilityPort {
    fun getCapabilities(): LocalImageGenerationCapabilities
}
