package com.browntowndev.enclave.domain.model.media

sealed interface GenerationSettings

sealed interface VisualGenerationSettings : GenerationSettings {
    val aspectRatio: AspectRatio
    val quality: GenerationQuality
    val referenceImageUri: String?
    val seed: String
}

data class ImageGenerationSettings(
    override val aspectRatio: AspectRatio = AspectRatio.ONE_ONE,
    override val quality: GenerationQuality = GenerationQuality.SPEED,
    /** Provider-native resolution (e.g. fal OpenAPI `resolution` enum). */
    val imageResolution: String? = null,
    val diffusionSteps: Int? = null,
    override val referenceImageUri: String? = null,
    override val seed: String = "",
    val generationCount: Int = 1,
    /** How strongly img2img deviates from the reference image (0.25–1.0). */
    val img2ImgDenoiseStrength: Float = LocalImageGenerationModelPolicy.DEFAULT_IMG2IMG_DENOISE_STRENGTH,
) : VisualGenerationSettings {
    companion object {
        const val MIN_GENERATION_COUNT = 1
        const val MAX_GENERATION_COUNT = 10
    }
}

fun ImageGenerationSettings.withClampedGenerationCount(): ImageGenerationSettings =
    copy(
        generationCount =
            generationCount.coerceIn(
                ImageGenerationSettings.MIN_GENERATION_COUNT,
                ImageGenerationSettings.MAX_GENERATION_COUNT,
            ),
    )

fun ImageGenerationSettings.resolvedImg2ImgDenoiseStrength(): Float =
    img2ImgDenoiseStrength.coerceIn(
        LocalImageGenerationModelPolicy.MIN_IMG2IMG_DENOISE_STRENGTH,
        LocalImageGenerationModelPolicy.MAX_IMG2IMG_DENOISE_STRENGTH,
    )

data class VideoGenerationSettings(
    override val aspectRatio: AspectRatio = AspectRatio.ONE_ONE,
    override val quality: GenerationQuality = GenerationQuality.SPEED,
    val videoDuration: Int = 6,
    /**
     * Provider-native duration token when the API expects a string (e.g. `"8s"`).
     * When set, inference adapters should send this value instead of [videoDuration].
     */
    val apiDuration: String? = null,
    val videoResolution: String = "480p",
    val autoGenerateVideo: Boolean = false,
    override val referenceImageUri: String? = null,
    override val seed: String = "",
) : VisualGenerationSettings

data class MusicGenerationSettings(
    val duration: Int = 30,
    val tempo: Int = 120,
) : GenerationSettings

enum class AspectRatio(
    val ratio: String,
) {
    ONE_ONE("1:1"),
    THREE_FOUR("3:4"),
    FOUR_THREE("4:3"),
    NINE_SIXTEEN("9:16"),
    SIXTEEN_NINE("16:9"),
    TWO_THREE("2:3"),
    THREE_TWO("3:2"),
    TWENTY_ONE_NINE("21:9"),
    FIVE_FOUR("5:4"),
}

enum class GenerationQuality(
    val displayName: String,
) {
    SPEED("Speed"),
    QUALITY("Quality"),
    HD("HD"),
    ULTRA("Ultra"),
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    AUTO("Auto"),
}

private val DefaultMusicDurationRange = 5..120
private val DefaultMusicTempoRange = 60..200

data class ProviderCapabilities(
    val supportedAspectRatios: List<AspectRatio>,
    val supportedImageQualities: List<GenerationQuality>,
    val supportedImageResolutions: List<String> = emptyList(),
    val supportedImageSteps: List<Int> = emptyList(),
    val defaultImageStep: Int? = null,
    val supportedVideoQualities: List<GenerationQuality>,
    val supportedVideoResolutions: List<String>,
    val supportedVideoDurations: List<Int>,
    /** Labels shown in Studio duration pills (e.g. `8s`); falls back to `"${seconds}s"` when empty. */
    val supportedVideoDurationTokens: List<String> = emptyList(),
    val supportsVideo: Boolean,
    val supportsMusic: Boolean = false,
    val musicDurationRange: IntRange = DefaultMusicDurationRange,
    val musicTempoRange: IntRange = DefaultMusicTempoRange,
)
