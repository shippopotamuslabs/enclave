package com.browntowndev.pocketcrew.domain.port.media

import com.browntowndev.pocketcrew.domain.model.config.MediaCapability

/**
 * Port for saving media assets to the device's public gallery.
 */
interface MediaSaverPort {
    /**
     * Saves the media file at [localUri] to the device's public gallery.
     * @return the URI of the saved media in the public gallery.
     */
    suspend fun saveToGallery(localUri: String, mediaType: MediaCapability): Result<String>
}
