package com.browntowndev.enclave.domain.port.media

import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget

/**
 * Port for image-generation services.
 *
 * Generation is expressed against a resolved [ImageGenerationTarget] so the port can fan out to
 * either a remote BYOK provider or the on-device stable-diffusion backend without callers having
 * to stitch together provider credentials or local artifact paths.
 */
interface ImageGenerationPort {
    /**
     * Generates one or more images from [prompt] against the resolved [target] using [settings].
     *
     * @param prompt The text prompt describing the image to generate.
     * @param target The resolved image-generation target (remote provider or local stable diffusion).
     * @param settings The generation settings (aspect ratio, resolution, quality, etc.).
     * @param onProgress Optional sink for local-generation progress events; remote providers ignore it.
     * @return A [Result] containing the raw image bytes for each generated image on success.
     */
    suspend fun generateImage(
        prompt: String,
        target: ImageGenerationTarget,
        settings: GenerationSettings,
        onProgress: (ImageGenerationProgress) -> Unit = {},
    ): Result<List<ByteArray>>
}
