package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.port.media.MediaSaverPort
import javax.inject.Inject

class SaveMediaToGalleryUseCase
    @Inject
    constructor(
        private val mediaSaverPort: MediaSaverPort,
    ) {
        suspend operator fun invoke(
            localUri: String,
            mediaType: MediaCapability,
        ): Result<String> = mediaSaverPort.saveToGallery(localUri, mediaType)
    }
