package com.browntowndev.enclave.domain.port.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings

interface StudioGenerationDispatcherPort {
    fun startGeneration(
        prompt: String,
        mediaType: MediaCapability,
        settings: GenerationSettings,
    )

    fun stopGeneration()
}
