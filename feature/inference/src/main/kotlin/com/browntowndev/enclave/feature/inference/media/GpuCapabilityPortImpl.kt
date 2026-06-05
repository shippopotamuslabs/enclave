package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.domain.port.media.GpuCapabilityPort
import com.browntowndev.enclave.domain.port.media.LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import javax.inject.Inject

class GpuCapabilityPortImpl
    @Inject
    constructor(
        private val capabilityPort: LocalImageGenerationCapabilityPort,
    ) : GpuCapabilityPort {
        override fun isLocalImageGenerationSupported(): Boolean {
            if (!LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED) {
                return false
            }
            return capabilityPort.getCapabilities().isAnyBackendUsable
        }
    }
