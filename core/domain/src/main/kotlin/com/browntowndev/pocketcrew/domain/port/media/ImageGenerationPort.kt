package com.browntowndev.pocketcrew.domain.port.media

import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings

/**
 * Port for Image Generation services.
 */
interface ImageGenerationPort {
    /**
     * Generates an image from the given [prompt] using the specified [provider] and [settings].
     *
     * @param prompt The text prompt describing the image to generate.
     * @param provider The media provider configuration to use.
     * @param settings The generation settings (aspect ratio, quality, etc.).
     * @return A [Result] containing the raw image bytes for each generated image on success.
     */
    suspend fun generateImage(
        prompt: String,
        provider: MediaProviderAsset,
        settings: GenerationSettings
    ): Result<List<ByteArray>>
}
