package com.browntowndev.enclave.feature.studio

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.model.media.StudioTemplate
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.port.media.SpeechState

fun MediaCapability.toUi(): StudioMediaTypeUi =
    when (this) {
        MediaCapability.IMAGE -> StudioMediaTypeUi.IMAGE
        MediaCapability.VIDEO -> StudioMediaTypeUi.VIDEO
        MediaCapability.MUSIC -> StudioMediaTypeUi.MUSIC
    }

fun StudioMediaTypeUi.toDomain(): MediaCapability =
    when (this) {
        StudioMediaTypeUi.IMAGE -> MediaCapability.IMAGE
        StudioMediaTypeUi.VIDEO -> MediaCapability.VIDEO
        StudioMediaTypeUi.MUSIC -> MediaCapability.MUSIC
    }

fun AspectRatio.toUi(): StudioAspectRatioUi =
    runCatching { StudioAspectRatioUi.valueOf(name) }.getOrDefault(StudioAspectRatioUi.ONE_ONE)

fun StudioAspectRatioUi.toDomain(): AspectRatio =
    runCatching { AspectRatio.valueOf(name) }.getOrDefault(AspectRatio.ONE_ONE)

fun GenerationQuality.toUi(): StudioQualityUi =
    runCatching { StudioQualityUi.valueOf(name) }.getOrDefault(StudioQualityUi.SPEED)

fun StudioQualityUi.toDomain(): GenerationQuality =
    runCatching { GenerationQuality.valueOf(name) }.getOrDefault(GenerationQuality.SPEED)

fun GenerationSettings.toUi(): StudioGenerationSettingsUi =
    when (this) {
        is ImageGenerationSettings ->
            StudioImageSettingsUi(
                aspectRatio = aspectRatio.toUi(),
                quality = quality.toUi(),
                imageResolution = imageResolution,
                diffusionSteps = diffusionSteps,
                referenceImageUri = referenceImageUri,
                seed = seed,
                generationCount = generationCount,
                img2ImgDenoiseStrength = img2ImgDenoiseStrength,
            )
        is VideoGenerationSettings ->
            StudioVideoSettingsUi(
                aspectRatio = aspectRatio.toUi(),
                quality = quality.toUi(),
                videoDuration = videoDuration,
                apiDuration = apiDuration,
                videoResolution = videoResolution,
                autoGenerateVideo = autoGenerateVideo,
                referenceImageUri = referenceImageUri,
                seed = seed,
            )
        is MusicGenerationSettings ->
            StudioMusicSettingsUi(
                duration = duration,
                tempo = tempo,
            )
    }

fun StudioGenerationSettingsUi.toDomain(): GenerationSettings =
    when (this) {
        is StudioImageSettingsUi ->
            ImageGenerationSettings(
                aspectRatio = aspectRatio.toDomain(),
                quality = quality.toDomain(),
                imageResolution = imageResolution,
                diffusionSteps = diffusionSteps,
                referenceImageUri = referenceImageUri,
                seed = seed,
                generationCount = generationCount,
                img2ImgDenoiseStrength = img2ImgDenoiseStrength,
            )
        is StudioVideoSettingsUi ->
            VideoGenerationSettings(
                aspectRatio = aspectRatio.toDomain(),
                quality = quality.toDomain(),
                videoDuration = videoDuration,
                apiDuration = apiDuration,
                videoResolution = videoResolution,
                autoGenerateVideo = autoGenerateVideo,
                referenceImageUri = referenceImageUri,
                seed = seed,
            )
        is StudioMusicSettingsUi ->
            MusicGenerationSettings(
                duration = duration,
                tempo = tempo,
            )
    }

fun ProviderCapabilities.toUi(): StudioCapabilitiesUi =
    StudioCapabilitiesUi(
        supportedAspectRatios = supportedAspectRatios.map { it.toUi() },
        supportedImageQualities = supportedImageQualities.map { it.toUi() },
        supportedImageResolutions = supportedImageResolutions,
        supportedImageSteps = supportedImageSteps,
        defaultImageStep = defaultImageStep,
        supportedVideoQualities = supportedVideoQualities.map { it.toUi() },
        supportedVideoResolutions = supportedVideoResolutions,
        supportedVideoDurations = supportedVideoDurations,
        supportedVideoDurationTokens = supportedVideoDurationTokens,
        supportsVideo = supportsVideo,
        supportsMusic = supportsMusic,
        musicDurationStart = musicDurationRange.first,
        musicDurationEnd = musicDurationRange.last,
        musicTempoStart = musicTempoRange.first,
        musicTempoEnd = musicTempoRange.last,
    )

fun StudioTemplate.toUi(): StudioTemplateUi =
    StudioTemplateUi(
        id = id,
        name = name,
        exampleUri = exampleUri,
        promptPrefix = promptPrefix,
        promptSuffix = promptSuffix,
    )

fun StudioTemplateUi.toDomain(): StudioTemplate =
    StudioTemplate(
        id = id,
        name = name,
        exampleUri = exampleUri,
        promptPrefix = promptPrefix,
        promptSuffix = promptSuffix,
    )

fun SpeechState.toUi(): StudioSpeechStateUi =
    when (this) {
        is SpeechState.Idle -> StudioSpeechStateUi.Idle
        is SpeechState.ModelLoading -> StudioSpeechStateUi.ModelLoading
        is SpeechState.Listening -> StudioSpeechStateUi.Listening(volume = volume, tick = tick)
        is SpeechState.Transcribing -> StudioSpeechStateUi.Transcribing
        is SpeechState.FinalText -> StudioSpeechStateUi.FinalText(text = text)
        is SpeechState.Error -> StudioSpeechStateUi.Error(message = message)
    }

fun StudioSpeechStateUi.toDomain(): SpeechState =
    when (this) {
        is StudioSpeechStateUi.Idle -> SpeechState.Idle
        is StudioSpeechStateUi.ModelLoading -> SpeechState.ModelLoading
        is StudioSpeechStateUi.Listening -> SpeechState.Listening(volume = volume, tick = tick)
        is StudioSpeechStateUi.Transcribing -> SpeechState.Transcribing
        is StudioSpeechStateUi.FinalText -> SpeechState.FinalText(text = text)
        is StudioSpeechStateUi.Error -> SpeechState.Error(message = message)
    }
