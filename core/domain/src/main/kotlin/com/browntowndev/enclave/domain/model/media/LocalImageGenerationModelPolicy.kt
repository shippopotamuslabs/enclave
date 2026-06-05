package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.config.LocalModelAsset

object LocalImageGenerationModelPolicy {
    fun supports(asset: LocalModelAsset): Boolean = LocalImageGenerationModelRegistry.descriptorFor(asset) != null

    fun policyFor(id: LocalImageGenerationModelId): LocalImageGenerationPolicy =
        throw NotImplementedError("TDD Red")

    fun normalizeRequest(
        modelId: LocalImageGenerationModelId,
        settings: ImageGenerationSettings,
    ): NormalizedLocalImageGenerationRequest =
        throw NotImplementedError("TDD Red")
}

data class LocalImageGenerationPolicy(
    val supportedImageResolutions: List<String>,
    val defaultImageResolution: String,
    val supportedDiffusionSteps: List<Int>,
    val defaultDiffusionSteps: Int,
    val fixedDiffusionSteps: Int?,
    val cfgScale: Float,
    val scheduler: String,
)

data class NormalizedLocalImageGenerationRequest(
    val imageResolution: String,
    val diffusionSteps: Int,
    val width: Int,
    val height: Int,
    val cfgScale: Float,
    val scheduler: String,
)
