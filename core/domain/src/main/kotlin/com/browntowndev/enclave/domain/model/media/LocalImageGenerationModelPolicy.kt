package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.config.LocalModelAsset

object LocalImageGenerationModelPolicy {
    const val DEFAULT_IMG2IMG_DENOISE_STRENGTH = 0.75f
    const val JUGGERNAUT_DEFAULT_IMG2IMG_DENOISE_STRENGTH = 0.35f
    const val MIN_IMG2IMG_DENOISE_STRENGTH = 0.25f
    const val MAX_IMG2IMG_DENOISE_STRENGTH = 1.0f
    private const val LOW_RAM_MEMORY_CLASS_MB = 384

    fun memoryPolicyFor(modelId: LocalImageGenerationModelId, memoryClassMb: Int): LocalImageGenerationMemoryPolicy {
        if (modelId == LocalImageGenerationModelId.SDXL_QNN ||
            modelId == LocalImageGenerationModelId.JUGGERNAUT_XL_QNN
        ) {
            return LocalImageGenerationMemoryPolicy.LOW_RAM
        }
        return if (memoryClassMb <= LOW_RAM_MEMORY_CLASS_MB) {
            LocalImageGenerationMemoryPolicy.LOW_RAM
        } else {
            LocalImageGenerationMemoryPolicy.FULL
        }
    }

    private val policies: Map<LocalImageGenerationModelId, LocalImageGenerationPolicy> =
        mapOf(
            LocalImageGenerationModelId.SD15_QNN to
                sd15Policy(),
            LocalImageGenerationModelId.SD15_CPU to
                sd15Policy(),
            LocalImageGenerationModelId.SDXL_QNN to
                LocalImageGenerationPolicy(
                    supportedImageResolutions = listOf("1024x1024"),
                    defaultImageResolution = "1024x1024",
                    supportedDiffusionSteps = listOf(8, 12, 16, 20, 24, 30),
                    // Matches enclave_diffusion_engine SDXL LCM default (DMD2 pack is tuned for ~8).
                    defaultDiffusionSteps = 8,
                    fixedDiffusionSteps = null,
                    cfgScale = 1.5f,
                    scheduler = "lcm",
                ),
            LocalImageGenerationModelId.JUGGERNAUT_XL_QNN to
                LocalImageGenerationPolicy(
                    supportedImageResolutions = listOf("1024x1024"),
                    defaultImageResolution = "1024x1024",
                    supportedDiffusionSteps = listOf(20, 22, 24, 26, 28, 30),
                    defaultDiffusionSteps = 24,
                    fixedDiffusionSteps = null,
                    cfgScale = 4.0f,
                    scheduler = "dpm",
                    defaultImg2ImgDenoiseStrength = JUGGERNAUT_DEFAULT_IMG2IMG_DENOISE_STRENGTH,
                ),
        )

    fun supports(asset: LocalModelAsset): Boolean = LocalImageGenerationModelRegistry.descriptorFor(asset) != null

    fun policyFor(id: LocalImageGenerationModelId): LocalImageGenerationPolicy =
        policies[id] ?: error("No local image generation policy for $id")

    fun providerCapabilitiesFor(id: LocalImageGenerationModelId): ProviderCapabilities {
        val policy = policyFor(id)
        return ProviderCapabilities(
            supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
            // Local packs are fixed square resolutions; Studio shows Resolution only (not Quality).
            supportedImageQualities = emptyList(),
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
                        clampToSupportedDiffusionSteps(requested, policy)
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

    private fun clampToSupportedDiffusionSteps(
        requested: Int,
        policy: LocalImageGenerationPolicy,
    ): Int {
        val supported = policy.supportedDiffusionSteps
        if (supported.isEmpty()) {
            return policy.defaultDiffusionSteps
        }
        return supported.filter { it >= requested }.minOrNull()
            ?: supported.max()
    }

    fun resolveImg2ImgDenoiseStrength(
        modelId: LocalImageGenerationModelId,
        settings: ImageGenerationSettings,
    ): Float {
        val policy = policyFor(modelId)
        val requested = settings.resolvedImg2ImgDenoiseStrength()
        return if (settings.img2ImgDenoiseStrength == DEFAULT_IMG2IMG_DENOISE_STRENGTH &&
            policy.defaultImg2ImgDenoiseStrength != DEFAULT_IMG2IMG_DENOISE_STRENGTH
        ) {
            policy.defaultImg2ImgDenoiseStrength
        } else {
            requested
        }
    }

    private fun sd15Policy(): LocalImageGenerationPolicy =
        LocalImageGenerationPolicy(
            supportedImageResolutions = listOf("512x512"),
            defaultImageResolution = "512x512",
            supportedDiffusionSteps = emptyList(),
            defaultDiffusionSteps = 20,
            fixedDiffusionSteps = 20,
            cfgScale = 7.5f,
            scheduler = "dpm",
        )

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
    val defaultImg2ImgDenoiseStrength: Float = LocalImageGenerationModelPolicy.DEFAULT_IMG2IMG_DENOISE_STRENGTH,
)

enum class LocalImageGenerationMemoryPolicy {
    FULL,
    LOW_RAM,
}

data class NormalizedLocalImageGenerationRequest(
    val imageResolution: String,
    val diffusionSteps: Int,
    val width: Int,
    val height: Int,
    val cfgScale: Float,
    val scheduler: String,
)
