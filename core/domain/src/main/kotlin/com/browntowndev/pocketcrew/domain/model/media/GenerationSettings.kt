package com.browntowndev.pocketcrew.domain.model.media

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
    override val referenceImageUri: String? = null,
    override val seed: String = ""
) : VisualGenerationSettings

data class VideoGenerationSettings(
    override val aspectRatio: AspectRatio = AspectRatio.ONE_ONE,
    override val quality: GenerationQuality = GenerationQuality.SPEED,
    val videoDuration: Int = 6,
    val videoResolution: String = "480p",
    val autoGenerateVideo: Boolean = false,
    override val referenceImageUri: String? = null,
    override val seed: String = ""
) : VisualGenerationSettings

data class MusicGenerationSettings(
    val duration: Int = 30,
    val tempo: Int = 120
) : GenerationSettings

enum class AspectRatio(val ratio: String) {
    ONE_ONE("1:1"), THREE_FOUR("3:4"), FOUR_THREE("4:3"), 
    NINE_SIXTEEN("9:16"), SIXTEEN_NINE("16:9"), 
    TWO_THREE("2:3"), THREE_TWO("3:2")
}

enum class GenerationQuality { SPEED, QUALITY, HD, ULTRA }

data class ProviderCapabilities(
    val supportedAspectRatios: List<AspectRatio>,
    val supportedQualities: List<GenerationQuality>,
    val supportsReferenceImage: Boolean,
    val supportsVideo: Boolean,
    val supportsMusic: Boolean = false
)
