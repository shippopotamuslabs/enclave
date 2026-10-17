package com.browntowndev.enclave.feature.moa.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles starting and stopping the InferenceService with proper intents.
 * Used by PipelineExecutorPort to control the foreground service.
 */
@Singleton
open class InferenceServiceStarter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
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
            stateJson: String? = null,
            userMessageId: String? = null,
            assistantMessageId: String? = null,
        ): Intent =
            Intent(context, InferenceService::class.java).apply {
                action = InferenceService.ACTION_START
                putExtra(InferenceService.EXTRA_CHAT_ID, chatId)
                putExtra(InferenceService.EXTRA_USER_MESSAGE, userMessage)
                stateJson?.let { putExtra(InferenceService.EXTRA_STATE_JSON, it) }
                userMessageId?.let { putExtra(InferenceService.EXTRA_USER_MESSAGE_ID, it) }
                assistantMessageId?.let { putExtra(InferenceService.EXTRA_ASSISTANT_MESSAGE_ID, it) }
            }

        /**
         * Creates an intent to stop the inference service.
         *
         * @return Intent ready for startService()
         */
        fun createStopIntent(): Intent =
            Intent(context, InferenceService::class.java).apply {
                action = InferenceService.ACTION_STOP
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
        open fun startService(
            chatId: String,
            userMessage: String,
            stateJson: String? = null,
            userMessageId: String? = null,
            assistantMessageId: String? = null,
        ) {
            val intent =
                createStartIntent(
                    chatId = chatId,
                    userMessage = userMessage,
                    stateJson = stateJson,
                    userMessageId = userMessageId,
                    assistantMessageId = assistantMessageId,
                )
            context.startForegroundService(intent)
        }

        /**
         * Stops the inference service gracefully.
         * Sends a stop action intent to the service.
         */
        open fun stopService() {
            val intent = createStopIntent()
            try {
                context.startService(intent)
            } catch (_: IllegalStateException) {
                // Service already stopped while app is backgrounded.
            }
        }

        /**
         * Creates an intent to resume the inference pipeline from a saved state.
         *
         * @param chatId Unique identifier for the chat session
         * @param stateJson The serialized pipeline state to resume from
         * @return Intent ready for startForegroundService()
         */
        fun createResumeIntent(
            chatId: String,
            stateJson: String,
        ): Intent =
            Intent(context, InferenceService::class.java).apply {
                action = InferenceService.ACTION_RESUME
                putExtra(InferenceService.EXTRA_CHAT_ID, chatId)
                putExtra(InferenceService.EXTRA_STATE_JSON, stateJson)
            }

        /**
         * Starts the inference service to resume from a saved state.
         *
         * @param chatId Unique identifier for the chat session
         * @param stateJson The serialized pipeline state to resume from
         */
        fun startServiceResume(
            chatId: String,
            stateJson: String,
        ) {
            val intent = createResumeIntent(chatId, stateJson)
            context.startForegroundService(intent)
        }
    }
