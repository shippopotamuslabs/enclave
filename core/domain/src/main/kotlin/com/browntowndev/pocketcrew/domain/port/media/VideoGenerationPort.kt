package com.browntowndev.pocketcrew.domain.port.media

import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings

/**
 * Port for Video Generation services.
 */
interface VideoGenerationPort {
    /**
     * Generates a video from the given [prompt] using the specified [provider] and [settings].
     *
     * @param prompt The text prompt describing the video to generate.
     * @param provider The media provider configuration to use.
     * @param settings The generation settings (aspect ratio, duration, resolution, etc.).
     * @return A [Result] containing the raw video bytes on success.
     */
    suspend fun generateVideo(
        prompt: String,
        provider: MediaProviderAsset,
        settings: GenerationSettings
    ): Result<ByteArray>
}
