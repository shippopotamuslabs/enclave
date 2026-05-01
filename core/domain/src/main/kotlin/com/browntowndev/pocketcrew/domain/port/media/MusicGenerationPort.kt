package com.browntowndev.pocketcrew.domain.port.media

import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings

/**
 * Port for Music Generation services.
 */
interface MusicGenerationPort {
    /**
     * Generates music from the given [prompt] using the specified [provider] and [settings].
     *
     * @param prompt The text prompt describing the music to generate.
     * @param provider The media provider configuration to use.
     * @param settings The generation settings.
     * @return A [Result] containing the raw audio bytes on success.
     */
    suspend fun generateMusic(
        prompt: String,
        provider: MediaProviderAsset,
        settings: GenerationSettings
    ): Result<ByteArray>
}