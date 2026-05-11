package com.browntowndev.pocketcrew.feature.studio.service

import android.content.Context
import android.content.Intent
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.port.media.StudioGenerationDispatcherPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidStudioGenerationDispatcher @Inject constructor(
    @ApplicationContext private val context: Context
) : StudioGenerationDispatcherPort {

    override fun startGeneration(prompt: String, mediaType: MediaCapability) {
        val intent = Intent(context, MultimediaGenerationService::class.java).apply {
            action = MultimediaGenerationService.ACTION_START
            putExtra(MultimediaGenerationService.EXTRA_PROMPT, prompt)
            putExtra(MultimediaGenerationService.EXTRA_MEDIA_TYPE, mediaType.name)
        }
        context.startForegroundService(intent)
    }

    override fun stopGeneration() {
        val intent = Intent(context, MultimediaGenerationService::class.java).apply {
            action = MultimediaGenerationService.ACTION_STOP
        }
        context.startService(intent)
    }
}
