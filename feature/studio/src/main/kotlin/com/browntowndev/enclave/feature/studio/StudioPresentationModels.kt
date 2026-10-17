package com.browntowndev.enclave.feature.studio

import androidx.compose.runtime.Immutable

enum class StudioMediaTypeUi(
    val displayName: String,
) {
    IMAGE("Image"),
    VIDEO("Video"),
    MUSIC("Music"),
}

enum class StudioAspectRatioUi(
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

enum class StudioQualityUi(
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

sealed interface StudioGenerationSettingsUi

@Immutable
data class StudioImageSettingsUi(
    val aspectRatio: StudioAspectRatioUi = StudioAspectRatioUi.ONE_ONE,
    val quality: StudioQualityUi = StudioQualityUi.SPEED,
    val imageResolution: String? = null,
    val diffusionSteps: Int? = null,
    val referenceImageUri: String? = null,
    val seed: String = "",
    val generationCount: Int = 1,
    val img2ImgDenoiseStrength: Float = DEFAULT_IMG2IMG_DENOISE_STRENGTH,
) : StudioGenerationSettingsUi {
    companion object {
        const val MIN_GENERATION_COUNT = 1
        const val MAX_GENERATION_COUNT = 10
        const val MIN_IMG2IMG_DENOISE_STRENGTH = 0.25f
        const val MAX_IMG2IMG_DENOISE_STRENGTH = 1.0f
        const val DEFAULT_IMG2IMG_DENOISE_STRENGTH = 0.75f
    }
}

@Immutable
data class StudioVideoSettingsUi(
    val aspectRatio: StudioAspectRatioUi = StudioAspectRatioUi.ONE_ONE,
    val quality: StudioQualityUi = StudioQualityUi.SPEED,
    val videoDuration: Int = 6,
    val apiDuration: String? = null,
    val videoResolution: String = "480p",
    val autoGenerateVideo: Boolean = false,
    val referenceImageUri: String? = null,
    val seed: String = "",
) : StudioGenerationSettingsUi

@Immutable
data class StudioMusicSettingsUi(
    val duration: Int = 30,
    val tempo: Int = 120,
) : StudioGenerationSettingsUi

val StudioGenerationSettingsUi.referenceImageUriOrNull: String?
    get() =
        when (this) {
            is StudioImageSettingsUi -> referenceImageUri
            is StudioVideoSettingsUi -> referenceImageUri
            is StudioMusicSettingsUi -> null
        }

@Immutable
data class StudioCapabilitiesUi(
    val supportedAspectRatios: List<StudioAspectRatioUi>,
    val supportedImageQualities: List<StudioQualityUi>,
    val supportedImageResolutions: List<String> = emptyList(),
    val supportedImageSteps: List<Int> = emptyList(),
    val defaultImageStep: Int? = null,
    val supportedVideoQualities: List<StudioQualityUi>,
    val supportedVideoResolutions: List<String>,
    val supportedVideoDurations: List<Int>,
    val supportedVideoDurationTokens: List<String> = emptyList(),
    val supportsVideo: Boolean,
    val supportsMusic: Boolean = false,
    val musicDurationStart: Int = 5,
    val musicDurationEnd: Int = 120,
    val musicTempoStart: Int = 60,
    val musicTempoEnd: Int = 200,
)

@Immutable
data class StudioTemplateUi(
    val id: String,
    val name: String,
    val exampleUri: String,
    val promptPrefix: String = "",
    val promptSuffix: String = "",
)

sealed class StudioSpeechStateUi {
    data object Idle : StudioSpeechStateUi()

    data object ModelLoading : StudioSpeechStateUi()

    data class Listening(
        val volume: Float,
        val tick: Long = 0L,
    ) : StudioSpeechStateUi()

    data object Transcribing : StudioSpeechStateUi()

    data class FinalText(
        val text: String,
    ) : StudioSpeechStateUi()

    data class Error(
        val message: String,
    ) : StudioSpeechStateUi()
}

val StudioSpeechStateUi.isActiveVoiceInput: Boolean
    get() =
        this is StudioSpeechStateUi.Listening ||
            this is StudioSpeechStateUi.ModelLoading ||
            this is StudioSpeechStateUi.Transcribing
