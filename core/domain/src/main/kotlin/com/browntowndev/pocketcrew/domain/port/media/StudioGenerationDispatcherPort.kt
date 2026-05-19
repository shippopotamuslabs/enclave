package com.browntowndev.pocketcrew.domain.port.media

import com.browntowndev.pocketcrew.domain.model.config.MediaCapability

interface StudioGenerationDispatcherPort {
    fun startGeneration(
        prompt: String,
        mediaType: MediaCapability,
    )

    fun stopGeneration()
}
