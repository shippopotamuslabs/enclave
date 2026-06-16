package com.browntowndev.enclave.feature.studio.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
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
            generationId: String,
            override: StudioModelOverride?,
        ) {
            val intent =
                Intent(context, MultimediaGenerationService::class.java).apply {
                    action = MultimediaGenerationService.ACTION_START
                    putExtra(MultimediaGenerationService.EXTRA_PROMPT, prompt)
                    putExtra(MultimediaGenerationService.EXTRA_MEDIA_TYPE, mediaType.name)
                    putExtra(MultimediaGenerationService.EXTRA_GENERATION_ID, generationId)
                    StudioGenerationIntentCodec.writeSettings(this, settings)
                    if (override != null) {
                        StudioGenerationIntentCodec.writeOverride(this, override)
                    }
                }
            try {
                context.startForegroundService(intent)
            } catch (error: ForegroundServiceStartNotAllowedException) {
                throw IllegalStateException("Generation could not start in the background.", error)
            } catch (error: SecurityException) {
                throw IllegalStateException("Generation could not start due to system restrictions.", error)
            }
        }

        override fun stopGeneration() {
            val intent =
                Intent(context, MultimediaGenerationService::class.java).apply {
                    action = MultimediaGenerationService.ACTION_STOP
                }
            context.startService(intent)
        }
    }
