package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.VisualGenerationSettings
import javax.inject.Inject

/**
 * Maps the current studio media mode and generation settings to the default-model slot
 * used for provider assignment (text-to-image vs edit, text-to-video vs image-to-video, etc.).
 */
class ResolveMediaModelTypeUseCase
    @Inject
    constructor() {
        operator fun invoke(
            mediaCapability: MediaCapability,
            settings: GenerationSettings,
        ): ModelType = resolveMediaModelType(mediaCapability, settings)
    }

internal fun resolveMediaModelType(
    mediaCapability: MediaCapability,
    settings: GenerationSettings,
): ModelType {
    val referenceAttached = (settings as? VisualGenerationSettings)?.referenceImageUri != null
    return when (mediaCapability) {
        MediaCapability.IMAGE ->
            if (referenceAttached) {
                ModelType.IMAGE_EDIT
            } else {
                ModelType.IMAGE_GENERATION
            }
        MediaCapability.VIDEO ->
            if (referenceAttached) {
                ModelType.IMAGE_TO_VIDEO
            } else {
                ModelType.VIDEO_GENERATION
            }
        MediaCapability.MUSIC -> ModelType.MUSIC_GENERATION
    }
}
