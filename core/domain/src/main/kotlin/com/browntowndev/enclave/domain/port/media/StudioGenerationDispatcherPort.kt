package com.browntowndev.enclave.domain.port.media

import com.browntowndev.enclave.domain.model.config.MediaCapability

interface StudioGenerationDispatcherPort {
    fun startGeneration(
        prompt: String,
        mediaType: MediaCapability,
    )

    fun stopGeneration()
}
