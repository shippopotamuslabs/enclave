package com.browntowndev.pocketcrew.feature.moa.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles starting and stopping the InferenceService with proper intents.
 * Used by PipelineExecutorPort to control the foreground service.
 */
@Singleton
class InferenceServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Creates an intent to start the inference pipeline service.
     *
     * @param chatId Unique identifier for the chat session
     * @param userMessage The user's input text
     * @param stateJson Optional serialized pipeline state for resuming
     * @return Intent ready for startForegroundService()
     */
    fun createStartIntent(
        chatId: String,
        userMessage: String,
        stateJson: String? = null
    ): Intent {
        return Intent(context, InferenceService::class.java).apply {
            action = InferenceService.ACTION_START
            putExtra(InferenceService.EXTRA_CHAT_ID, chatId)
            putExtra(InferenceService.EXTRA_USER_MESSAGE, userMessage)
            stateJson?.let { putExtra(InferenceService.EXTRA_STATE_JSON, it) }
        }
    }

    /**
     * Creates an intent to stop the inference service.
     *
     * @return Intent ready for startService() or sendBroadcast()
     */
    fun createStopIntent(): Intent {
        return Intent(context, InferenceService::class.java).apply {
            action = InferenceService.ACTION_STOP
        }
    }

    /**
     * Starts the inference service as a foreground service.
     * Uses startForegroundService() which requires the service to call startForeground()
     * within 5 seconds.
     *
     * @param chatId Unique identifier for the chat session
     * @param userMessage The user's input text
     * @param stateJson Optional serialized pipeline state for resuming
     * @throws SecurityException if foreground service permission not granted
     */
    fun startService(
        chatId: String,
        userMessage: String,
        stateJson: String? = null
    ) {
        val intent = createStartIntent(chatId, userMessage, stateJson)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Stops the inference service gracefully.
     * Sends a stop action intent to the service.
     */
    fun stopService() {
        val intent = createStopIntent()
        context.startService(intent)
    }
}
