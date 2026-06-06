package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class StudioCapabilitiesCoordinator
    @Inject
    constructor() {
        fun observe(
            scope: CoroutineScope,
            settings: MutableStateFlow<GenerationSettings>,
            capabilitiesFlow: Flow<ProviderCapabilities?>,
        ) {
            capabilitiesFlow
                .onEach { caps ->
                    caps ?: return@onEach
                    applyCapabilitiesToSettings(settings, caps)
                }.launchIn(scope)
        }

        private fun applyCapabilitiesToSettings(
            settings: MutableStateFlow<GenerationSettings>,
            caps: ProviderCapabilities,
        ) {
            val currentSettings = settings.value
            val updated =
                when (currentSettings) {
                    is ImageGenerationSettings -> alignImageSettings(currentSettings, caps)
                    is VideoGenerationSettings -> alignVideoSettings(currentSettings, caps)
                    is MusicGenerationSettings -> alignMusicSettings(currentSettings, caps)
                    else -> return
                }
            if (updated != currentSettings) {
                settings.value = updated
            }
        }

        private fun alignImageSettings(
            settings: ImageGenerationSettings,
            caps: ProviderCapabilities,
        ): ImageGenerationSettings {
            var updated = settings
            if (caps.supportedImageQualities.isNotEmpty() &&
                !caps.supportedImageQualities.contains(updated.quality)
            ) {
                updated = updated.copy(quality = caps.supportedImageQualities.first())
            }
            if (caps.supportedAspectRatios.isNotEmpty() &&
                !caps.supportedAspectRatios.contains(updated.aspectRatio)
            ) {
                updated = updated.copy(aspectRatio = caps.supportedAspectRatios.first())
            }
            if (caps.supportedImageResolutions.isNotEmpty()) {
                val resolution =
                    updated.imageResolution?.takeIf { it in caps.supportedImageResolutions }
                        ?: caps.supportedImageResolutions.first()
                if (resolution != updated.imageResolution) {
                    updated = updated.copy(imageResolution = resolution)
                }
            }
            updated =
                when {
                    caps.supportedImageSteps.isEmpty() ->
                        if (updated.diffusionSteps != null) {
                            updated.copy(diffusionSteps = null)
                        } else {
                            updated
                        }
                    else -> {
                        val defaultStep =
                            caps.defaultImageStep ?: caps.supportedImageSteps.firstOrNull()
                        val step =
                            updated.diffusionSteps?.takeIf { it in caps.supportedImageSteps }
                                ?: defaultStep
                        if (step != null && step != updated.diffusionSteps) {
                            updated.copy(diffusionSteps = step)
                        } else {
                            updated
                        }
                    }
                }
            return updated
        }

        private fun alignVideoSettings(
            settings: VideoGenerationSettings,
            caps: ProviderCapabilities,
        ): VideoGenerationSettings {
            var updated = settings
            if (caps.supportedVideoQualities.isNotEmpty() &&
                !caps.supportedVideoQualities.contains(updated.quality)
            ) {
                updated = updated.copy(quality = caps.supportedVideoQualities.first())
            }
            if (caps.supportedAspectRatios.isNotEmpty() &&
                !caps.supportedAspectRatios.contains(updated.aspectRatio)
            ) {
                updated = updated.copy(aspectRatio = caps.supportedAspectRatios.first())
            }
            if (caps.supportedVideoResolutions.isNotEmpty() &&
                !caps.supportedVideoResolutions.contains(updated.videoResolution)
            ) {
                updated = updated.copy(videoResolution = caps.supportedVideoResolutions.first())
            }
            updated = alignVideoDuration(updated, caps)
            return updated
        }

        private fun alignVideoDuration(
            settings: VideoGenerationSettings,
            caps: ProviderCapabilities,
        ): VideoGenerationSettings {
            val durationTokens = caps.supportedVideoDurationTokens
            if (durationTokens.isNotEmpty()) {
                val selectedToken =
                    settings.apiDuration?.takeIf { it in durationTokens }
                        ?: durationTokens.first()
                val seconds = selectedToken.removeSuffix("s").toIntOrNull() ?: settings.videoDuration
                if (selectedToken == settings.apiDuration && seconds == settings.videoDuration) {
                    return settings
                }
                return settings.copy(
                    apiDuration = selectedToken,
                    videoDuration = seconds,
                )
            }
            if (caps.supportedVideoDurations.isEmpty() ||
                caps.supportedVideoDurations.contains(settings.videoDuration)
            ) {
                return settings
            }
            return settings.copy(
                videoDuration = caps.supportedVideoDurations.first(),
                apiDuration = null,
            )
        }

        private fun alignMusicSettings(
            settings: MusicGenerationSettings,
            caps: ProviderCapabilities,
        ): MusicGenerationSettings {
            var updated = settings
            if (updated.duration !in caps.musicDurationRange) {
                updated =
                    updated.copy(
                        duration = updated.duration.coerceIn(caps.musicDurationRange),
                    )
            }
            if (updated.tempo !in caps.musicTempoRange) {
                updated =
                    updated.copy(
                        tempo = updated.tempo.coerceIn(caps.musicTempoRange),
                    )
            }
            return updated
        }
    }
