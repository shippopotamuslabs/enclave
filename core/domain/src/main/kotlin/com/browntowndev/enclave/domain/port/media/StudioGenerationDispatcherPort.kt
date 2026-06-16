package com.browntowndev.enclave.domain.port.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.StudioModelOverride

interface StudioGenerationDispatcherPort {
    fun startGeneration(
        prompt: String,
        mediaType: MediaCapability,
        settings: GenerationSettings,
        generationId: String,
        override: StudioModelOverride? = null,
    )

    fun stopGeneration()
}
