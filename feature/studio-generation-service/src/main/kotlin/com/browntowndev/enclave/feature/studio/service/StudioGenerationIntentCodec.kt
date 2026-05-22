package com.browntowndev.enclave.feature.studio.service

import android.content.Intent
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings

object StudioGenerationIntentCodec {
    const val EXTRA_SETTINGS_KIND = "SETTINGS_KIND"

    private const val KIND_IMAGE = "image"
    private const val KIND_VIDEO = "video"
    private const val KIND_MUSIC = "music"

    private const val EXTRA_ASPECT_RATIO = "ASPECT_RATIO"
    private const val EXTRA_QUALITY = "QUALITY"
    private const val EXTRA_REFERENCE_IMAGE_URI = "REFERENCE_IMAGE_URI"
    private const val EXTRA_SEED = "SEED"
    private const val EXTRA_GENERATION_COUNT = "GENERATION_COUNT"
    private const val EXTRA_VIDEO_DURATION = "VIDEO_DURATION"
    private const val EXTRA_VIDEO_RESOLUTION = "VIDEO_RESOLUTION"
    private const val EXTRA_AUTO_GENERATE_VIDEO = "AUTO_GENERATE_VIDEO"
    private const val EXTRA_MUSIC_DURATION = "MUSIC_DURATION"
    private const val EXTRA_MUSIC_TEMPO = "MUSIC_TEMPO"

    fun writeSettings(
        intent: Intent,
        settings: GenerationSettings,
    ) {
        when (settings) {
            is ImageGenerationSettings -> {
                intent.putExtra(EXTRA_SETTINGS_KIND, KIND_IMAGE)
                intent.putExtra(EXTRA_ASPECT_RATIO, settings.aspectRatio.name)
                intent.putExtra(EXTRA_QUALITY, settings.quality.name)
                intent.putExtra(EXTRA_REFERENCE_IMAGE_URI, settings.referenceImageUri)
                intent.putExtra(EXTRA_SEED, settings.seed)
                intent.putExtra(EXTRA_GENERATION_COUNT, settings.generationCount)
            }
            is VideoGenerationSettings -> {
                intent.putExtra(EXTRA_SETTINGS_KIND, KIND_VIDEO)
                intent.putExtra(EXTRA_ASPECT_RATIO, settings.aspectRatio.name)
                intent.putExtra(EXTRA_QUALITY, settings.quality.name)
                intent.putExtra(EXTRA_REFERENCE_IMAGE_URI, settings.referenceImageUri)
                intent.putExtra(EXTRA_SEED, settings.seed)
                intent.putExtra(EXTRA_VIDEO_DURATION, settings.videoDuration)
                intent.putExtra(EXTRA_VIDEO_RESOLUTION, settings.videoResolution)
                intent.putExtra(EXTRA_AUTO_GENERATE_VIDEO, settings.autoGenerateVideo)
            }
            is MusicGenerationSettings -> {
                intent.putExtra(EXTRA_SETTINGS_KIND, KIND_MUSIC)
                intent.putExtra(EXTRA_MUSIC_DURATION, settings.duration)
                intent.putExtra(EXTRA_MUSIC_TEMPO, settings.tempo)
            }
        }
    }

    fun readSettings(intent: Intent): GenerationSettings? {
        return when (intent.getStringExtra(EXTRA_SETTINGS_KIND)) {
            KIND_IMAGE ->
                ImageGenerationSettings(
                    aspectRatio = intent.getStringExtra(EXTRA_ASPECT_RATIO)?.toAspectRatio() ?: AspectRatio.ONE_ONE,
                    quality = intent.getStringExtra(EXTRA_QUALITY)?.toGenerationQuality() ?: GenerationQuality.SPEED,
                    referenceImageUri = intent.getStringExtra(EXTRA_REFERENCE_IMAGE_URI),
                    seed = intent.getStringExtra(EXTRA_SEED).orEmpty(),
                    generationCount = intent.getIntExtra(EXTRA_GENERATION_COUNT, 1),
                )
            KIND_VIDEO ->
                VideoGenerationSettings(
                    aspectRatio = intent.getStringExtra(EXTRA_ASPECT_RATIO)?.toAspectRatio() ?: AspectRatio.ONE_ONE,
                    quality = intent.getStringExtra(EXTRA_QUALITY)?.toGenerationQuality() ?: GenerationQuality.SPEED,
                    referenceImageUri = intent.getStringExtra(EXTRA_REFERENCE_IMAGE_URI),
                    seed = intent.getStringExtra(EXTRA_SEED).orEmpty(),
                    videoDuration = intent.getIntExtra(EXTRA_VIDEO_DURATION, 6),
                    videoResolution = intent.getStringExtra(EXTRA_VIDEO_RESOLUTION) ?: "480p",
                    autoGenerateVideo = intent.getBooleanExtra(EXTRA_AUTO_GENERATE_VIDEO, false),
                )
            KIND_MUSIC ->
                MusicGenerationSettings(
                    duration = intent.getIntExtra(EXTRA_MUSIC_DURATION, 30),
                    tempo = intent.getIntExtra(EXTRA_MUSIC_TEMPO, 120),
                )
            else -> null
        }
    }

    private fun String.toAspectRatio(): AspectRatio =
        runCatching { AspectRatio.valueOf(this) }.getOrDefault(AspectRatio.ONE_ONE)

    private fun String.toGenerationQuality(): GenerationQuality =
        runCatching { GenerationQuality.valueOf(this) }.getOrDefault(GenerationQuality.SPEED)
}
