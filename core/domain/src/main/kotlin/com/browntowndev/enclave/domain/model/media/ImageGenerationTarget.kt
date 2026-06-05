package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId

sealed interface ImageGenerationTarget {
    data class Remote(
        val providerAssetId: MediaProviderId,
        val providerDisplayName: String,
        val providerCapabilities: ProviderCapabilities? = null,
    ) : ImageGenerationTarget

    /**
     * On-device diffusion (QNN NPU or MNN CPU). [modelDir] holds the installed package directory.
     */
    data class LocalStableDiffusion(
        val localConfigId: LocalModelConfigurationId,
        val displayName: String,
        val modelId: LocalImageGenerationModelId,
        val modelDir: String,
        val catalogSha256: String,
        val selectedBackend: LocalImageGenerationBackendKind,
        val capabilities: ProviderCapabilities,
        val deviceCapabilities: LocalImageGenerationCapabilities,
    ) : ImageGenerationTarget
}

object LocalStableDiffusionModelPolicy {
    fun supports(asset: LocalModelAsset): Boolean = LocalImageGenerationModelRegistry.descriptorFor(asset) != null
}

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

val LocalStableDiffusionCapabilities: ProviderCapabilities =
    ProviderCapabilities(
        supportedAspectRatios = listOf(AspectRatio.ONE_ONE, AspectRatio.SIXTEEN_NINE, AspectRatio.NINE_SIXTEEN),
        supportedImageQualities = listOf(GenerationQuality.SPEED),
        supportedImageResolutions = listOf("512x512", "1024x1024"),
        supportedVideoQualities = emptyList(),
        supportedVideoResolutions = emptyList(),
        supportedVideoDurations = emptyList(),
        supportsVideo = false,
        supportsMusic = false,
    )
