package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.config.LocalModelAsset

object LocalImageGenerationModelPolicy {
    private val policies: Map<LocalImageGenerationModelId, LocalImageGenerationPolicy> =
        mapOf(
            LocalImageGenerationModelId.SD15_QNN to
                LocalImageGenerationPolicy(
                    supportedImageResolutions = listOf("512x512"),
                    defaultImageResolution = "512x512",
                    supportedDiffusionSteps = emptyList(),
                    defaultDiffusionSteps = 20,
                    fixedDiffusionSteps = 20,
                    cfgScale = 7.5f,
                    scheduler = "dpm",
                ),
            LocalImageGenerationModelId.SDXL_QNN to
                LocalImageGenerationPolicy(
                    supportedImageResolutions = listOf("1024x1024"),
                    defaultImageResolution = "1024x1024",
                    supportedDiffusionSteps = listOf(8, 12, 16, 20, 24, 30),
                    defaultDiffusionSteps = 16,
                    fixedDiffusionSteps = null,
                    cfgScale = 1.5f,
                    scheduler = "lcm",
                ),
        )

    fun supports(asset: LocalModelAsset): Boolean = LocalImageGenerationModelRegistry.descriptorFor(asset) != null

    fun policyFor(id: LocalImageGenerationModelId): LocalImageGenerationPolicy =
        policies[id] ?: error("No local image generation policy for $id")

    fun providerCapabilitiesFor(id: LocalImageGenerationModelId): ProviderCapabilities {
        val policy = policyFor(id)
        return ProviderCapabilities(
            supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
            supportedImageQualities = listOf(GenerationQuality.SPEED),
            supportedImageResolutions = policy.supportedImageResolutions,
            supportedImageSteps = policy.supportedDiffusionSteps,
            defaultImageStep = policy.defaultDiffusionSteps,
            supportedVideoQualities = emptyList(),
            supportedVideoResolutions = emptyList(),
            supportedVideoDurations = emptyList(),
            supportsVideo = false,
            supportsMusic = false,
        )
    }

    fun normalizeRequest(
        modelId: LocalImageGenerationModelId,
        settings: ImageGenerationSettings,
    ): NormalizedLocalImageGenerationRequest {
        val policy = policyFor(modelId)
        val normalizedResolution =
            settings.imageResolution
                ?.trim()
                ?.lowercase()
                ?: policy.defaultImageResolution
        val supportedResolutions = policy.supportedImageResolutions.map { it.lowercase() }
        if (normalizedResolution !in supportedResolutions) {
            throw IllegalArgumentException(
                "Unsupported local image generation resolution '$normalizedResolution' for model $modelId. " +
                    "Supported resolutions: ${policy.supportedImageResolutions}.",
            )
        }

        val steps =
            when (val fixed = policy.fixedDiffusionSteps) {
                null -> {
                    val requested = settings.diffusionSteps ?: policy.defaultDiffusionSteps
                    if (requested in policy.supportedDiffusionSteps) {
                        requested
                    } else {
                        policy.defaultDiffusionSteps
                    }
                }
                else -> fixed
            }

        val (width, height) = parseSquareResolution(normalizedResolution)

        return NormalizedLocalImageGenerationRequest(
            imageResolution = normalizedResolution,
            diffusionSteps = steps,
            width = width,
            height = height,
            cfgScale = policy.cfgScale,
            scheduler = policy.scheduler,
        )
    }

    private fun parseSquareResolution(resolution: String): Pair<Int, Int> {
        val parts =
            resolution
                .split('x')
                .mapNotNull(String::toIntOrNull)
                .filter { it > 0 }
        require(parts.size == 2) { "Invalid image resolution '$resolution'." }
        return parts[0] to parts[1]
    }
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
