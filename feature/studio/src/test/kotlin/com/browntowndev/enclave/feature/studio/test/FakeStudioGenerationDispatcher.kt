package com.browntowndev.enclave.feature.studio.test

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.port.media.StudioGenerationDispatcherPort

data class StartedArgs(
    val prompt: String,
    val mediaType: MediaCapability,
    val settings: GenerationSettings,
    val generationId: String,
    val override: StudioModelOverride?,
)

class FakeStudioGenerationDispatcher : StudioGenerationDispatcherPort {
    var lastStarted: StartedArgs? = null
        private set

    override fun startGeneration(
        prompt: String,
        mediaType: MediaCapability,
        settings: GenerationSettings,
        generationId: String,
        override: StudioModelOverride?,
    ) {
        lastStarted =
            StartedArgs(
                prompt = prompt,
                mediaType = mediaType,
                settings = settings,
                generationId = generationId,
                override = override,
            )
    }

    override fun stopGeneration() {
        // no-op
    }
}
