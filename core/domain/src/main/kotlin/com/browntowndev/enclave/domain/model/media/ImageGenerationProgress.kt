package com.browntowndev.enclave.domain.model.media

/**
 * Progress events emitted by the on-device image-generation pipeline.
 *
 * The transport stays out of Room and off the main thread; remote providers do not emit these.
 */
sealed interface ImageGenerationProgress {
    data object PreparingModel : ImageGenerationProgress

    data object LoadingModel : ImageGenerationProgress

    data class Generating(
        val step: Int,
        val totalSteps: Int,
    ) : ImageGenerationProgress

    data object SavingOutput : ImageGenerationProgress
}
