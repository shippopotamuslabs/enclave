package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.config.MediaCapability

enum class ImageInputRequirement {
    NONE,
    OPTIONAL,
    REQUIRED,
}

/**
 * Normalized input constraints extracted from a provider model schema (e.g. fal OpenAPI).
 */
data class ModelGenerationInputSchema(
    val aspectRatioOptions: List<String> = emptyList(),
    val resolutionOptions: List<String> = emptyList(),
    /** Integer second choices when the API uses numeric duration. */
    val videoDurationSeconds: List<Int> = emptyList(),
    /** Raw duration tokens when the API uses string enums (e.g. "8s"). */
    val videoDurationTokens: List<String> = emptyList(),
    val numImagesRange: IntRange? = null,
    val musicDurationRange: IntRange? = null,
    val musicTempoRange: IntRange? = null,
    val imageInput: ImageInputRequirement = ImageInputRequirement.NONE,
)

fun ModelGenerationInputSchema.toProviderCapabilities(
    mediaCapability: MediaCapability,
    fallback: ProviderCapabilities,
): ProviderCapabilities {
    val aspectRatios =
        aspectRatioOptions
            .mapNotNull { option -> AspectRatio.entries.find { it.ratio == option } }
            .ifEmpty { fallback.supportedAspectRatios }

    return when (mediaCapability) {
        MediaCapability.IMAGE ->
            fallback.copy(
                supportedAspectRatios = aspectRatios,
                supportedImageQualities = emptyList(),
                supportedImageResolutions =
                    resolutionOptions.ifEmpty { fallback.supportedImageResolutions },
            )
        MediaCapability.VIDEO ->
            fallback.copy(
                supportedAspectRatios = aspectRatios,
                supportedVideoResolutions =
                    resolutionOptions.ifEmpty { fallback.supportedVideoResolutions },
                supportedVideoDurations =
                    videoDurationSeconds
                        .ifEmpty {
                            videoDurationTokens.mapNotNull { token ->
                                token.removeSuffix("s").toIntOrNull()
                            }
                        }.ifEmpty { fallback.supportedVideoDurations },
                supportedVideoDurationTokens =
                    videoDurationTokens.ifEmpty {
                        videoDurationSeconds.map { "${it}s" }
                    },
                supportsVideo = true,
            )
        MediaCapability.MUSIC ->
            fallback.copy(
                supportsMusic = true,
                musicDurationRange = musicDurationRange ?: fallback.musicDurationRange,
                musicTempoRange = musicTempoRange ?: fallback.musicTempoRange,
            )
    }
}
