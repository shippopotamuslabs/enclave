package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.port.repository.ActiveStudioGeneration
import org.json.JSONObject

internal object ActiveStudioGenerationCodec {
    private const val KEY_ID = "id"
    private const val KEY_PROMPT = "prompt"
    private const val KEY_MEDIA_TYPE = "mediaType"
    private const val KEY_SETTINGS_KIND = "settingsKind"
    private const val KIND_IMAGE = "image"
    private const val KIND_VIDEO = "video"
    private const val KIND_MUSIC = "music"

    fun encode(generation: ActiveStudioGeneration): String {
        val json =
            JSONObject()
                .put(KEY_ID, generation.id)
                .put(KEY_PROMPT, generation.prompt)
                .put(KEY_MEDIA_TYPE, generation.mediaType.name)
        when (val settings = generation.settings) {
            is ImageGenerationSettings -> {
                json.put(KEY_SETTINGS_KIND, KIND_IMAGE)
                json.put("aspectRatio", settings.aspectRatio.name)
                json.put("quality", settings.quality.name)
                settings.referenceImageUri?.let { json.put("referenceImageUri", it) }
                json.put("seed", settings.seed)
                json.put("generationCount", settings.generationCount)
                settings.imageResolution?.let { json.put("imageResolution", it) }
                settings.diffusionSteps?.let { json.put("diffusionSteps", it) }
                json.put("img2ImgDenoiseStrength", settings.img2ImgDenoiseStrength.toDouble())
            }
            is VideoGenerationSettings -> {
                json.put(KEY_SETTINGS_KIND, KIND_VIDEO)
                json.put("aspectRatio", settings.aspectRatio.name)
                json.put("quality", settings.quality.name)
                settings.referenceImageUri?.let { json.put("referenceImageUri", it) }
                json.put("seed", settings.seed)
                json.put("videoDuration", settings.videoDuration)
                json.put("videoResolution", settings.videoResolution)
                json.put("autoGenerateVideo", settings.autoGenerateVideo)
            }
            is MusicGenerationSettings -> {
                json.put(KEY_SETTINGS_KIND, KIND_MUSIC)
                json.put("duration", settings.duration)
                json.put("tempo", settings.tempo)
            }
        }
        return json.toString()
    }

    fun decode(raw: String): ActiveStudioGeneration? =
        runCatching {
            val json = JSONObject(raw)
            val id = json.getString(KEY_ID)
            val prompt = json.getString(KEY_PROMPT)
            val mediaType = MediaCapability.valueOf(json.getString(KEY_MEDIA_TYPE))
            val settings =
                when (json.optString(KEY_SETTINGS_KIND)) {
                    KIND_IMAGE ->
                        ImageGenerationSettings(
                            aspectRatio = json.optString("aspectRatio").toAspectRatio(),
                            quality = json.optString("quality").toGenerationQuality(),
                            referenceImageUri = json.optString("referenceImageUri").takeIf { it.isNotBlank() },
                            seed = json.optString("seed"),
                            generationCount = json.optInt("generationCount", 1),
                            imageResolution = json.optString("imageResolution").takeIf { it.isNotBlank() },
                            diffusionSteps =
                                json
                                    .optInt("diffusionSteps", Int.MIN_VALUE)
                                    .takeIf { it != Int.MIN_VALUE },
                            img2ImgDenoiseStrength = json.optDouble("img2ImgDenoiseStrength").toFloat(),
                        )
                    KIND_VIDEO ->
                        VideoGenerationSettings(
                            aspectRatio = json.optString("aspectRatio").toAspectRatio(),
                            quality = json.optString("quality").toGenerationQuality(),
                            referenceImageUri = json.optString("referenceImageUri").takeIf { it.isNotBlank() },
                            seed = json.optString("seed"),
                            videoDuration = json.optInt("videoDuration", 6),
                            videoResolution = json.optString("videoResolution", "480p"),
                            autoGenerateVideo = json.optBoolean("autoGenerateVideo", false),
                        )
                    KIND_MUSIC ->
                        MusicGenerationSettings(
                            duration = json.optInt("duration", DEFAULT_MUSIC_DURATION_SECONDS),
                            tempo = json.optInt("tempo", DEFAULT_MUSIC_TEMPO_BPM),
                        )
                    else -> ImageGenerationSettings()
                }
            ActiveStudioGeneration(
                id = id,
                prompt = prompt,
                mediaType = mediaType,
                settings = settings,
            )
        }.getOrNull()

    private fun String.toAspectRatio(): AspectRatio = runCatching { AspectRatio.valueOf(this) }.getOrDefault(AspectRatio.ONE_ONE)

    private fun String.toGenerationQuality(): GenerationQuality =
        runCatching { GenerationQuality.valueOf(this) }.getOrDefault(GenerationQuality.SPEED)

    private const val DEFAULT_MUSIC_DURATION_SECONDS = 30
    private const val DEFAULT_MUSIC_TEMPO_BPM = 120
}
