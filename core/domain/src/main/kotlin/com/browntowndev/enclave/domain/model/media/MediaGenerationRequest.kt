package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.config.MediaCapability

data class MediaGenerationRequest(
    val prompt: String,
    val capability: MediaCapability,
    val settings: GenerationSettings,
    val origin: MediaGenerationOrigin,
    val override: StudioModelOverride? = null,
)
