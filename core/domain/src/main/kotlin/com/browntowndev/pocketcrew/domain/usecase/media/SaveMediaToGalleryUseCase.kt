package com.browntowndev.pocketcrew.domain.usecase.media

import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.port.media.MediaSaverPort
import javax.inject.Inject

class SaveMediaToGalleryUseCase @Inject constructor(
    private val mediaSaverPort: MediaSaverPort
) {
    suspend operator fun invoke(localUri: String, mediaType: MediaCapability): Result<String> {
        return mediaSaverPort.saveToGallery(localUri, mediaType)
    }
}
