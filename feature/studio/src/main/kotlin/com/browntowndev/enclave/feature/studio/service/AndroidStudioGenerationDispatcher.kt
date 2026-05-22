package com.browntowndev.enclave.feature.studio.service

import android.content.Context
import android.content.Intent
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.port.media.StudioGenerationDispatcherPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidStudioGenerationDispatcher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : StudioGenerationDispatcherPort {
        override fun startGeneration(
            prompt: String,
            mediaType: MediaCapability,
            settings: GenerationSettings,
        ) {
            val intent =
                Intent(context, MultimediaGenerationService::class.java).apply {
                    action = MultimediaGenerationService.ACTION_START
                    putExtra(MultimediaGenerationService.EXTRA_PROMPT, prompt)
                    putExtra(MultimediaGenerationService.EXTRA_MEDIA_TYPE, mediaType.name)
                    StudioGenerationIntentCodec.writeSettings(this, settings)
                }
            context.startForegroundService(intent)
        }

        override fun stopGeneration() {
            val intent =
                Intent(context, MultimediaGenerationService::class.java).apply {
                    action = MultimediaGenerationService.ACTION_STOP
                }
            context.startService(intent)
        }
    }
