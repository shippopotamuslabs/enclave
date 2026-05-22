package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
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
                    val currentSettings = settings.value
                    if (currentSettings is ImageGenerationSettings) {
                        if (caps.supportedImageQualities.isNotEmpty() &&
                            !caps.supportedImageQualities.contains(currentSettings.quality)
                        ) {
                            settings.value = currentSettings.copy(quality = caps.supportedImageQualities.first())
                        }
                    } else if (currentSettings is VideoGenerationSettings) {
                        if (caps.supportedVideoQualities.isNotEmpty() &&
                            !caps.supportedVideoQualities.contains(currentSettings.quality)
                        ) {
                            settings.value = currentSettings.copy(quality = caps.supportedVideoQualities.first())
                        }
                    }
                }.launchIn(scope)
        }
    }
