package com.browntowndev.enclave.feature.chat.service

import android.content.Context
import android.content.Intent
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Responsible only for starting/stopping the [ChatInferenceService] foreground service.
 * State observation is handled by [ChatInferenceServiceExecutor].
 */
@Singleton
class ChatInferenceServiceStarter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val loggingPort: LoggingPort,
    ) {
        private companion object {
            private const val TAG = "ChatInferenceStarter"
        }

        fun startService(
            prompt: String,
            userMessageId: MessageId,
            assistantMessageId: MessageId,
            chatId: ChatId,
            userHasImage: Boolean,
            modelType: ModelType,
        ) {
            loggingPort.info(
                TAG,
                "startService requested chat=${chatId.value} " +
                    "assistantMessageId=${assistantMessageId.value} " +
                    "modelType=${modelType.name} userHasImage=$userHasImage",
            )
            val intent =
                Intent(context, ChatInferenceService::class.java).apply {
                    action = ChatInferenceService.ACTION_START
                    putExtra(ChatInferenceService.EXTRA_PROMPT, prompt)
                    putExtra(ChatInferenceService.EXTRA_USER_MESSAGE_ID, userMessageId.value)
                    putExtra(ChatInferenceService.EXTRA_ASSISTANT_MESSAGE_ID, assistantMessageId.value)
                    putExtra(ChatInferenceService.EXTRA_CHAT_ID, chatId.value)
                    putExtra(ChatInferenceService.EXTRA_USER_HAS_IMAGE, userHasImage)
                    putExtra(ChatInferenceService.EXTRA_MODEL_TYPE, modelType.name)
                }
            loggingPort.debug(
                TAG,
                "startForegroundService intent prepared " +
                    "action=${intent.action} chat=${chatId.value} " +
                    "assistantMessageId=${assistantMessageId.value}",
            )
            context.startForegroundService(intent)
            loggingPort.info(
                TAG,
                "startForegroundService invoked chat=${chatId.value} assistantMessageId=${assistantMessageId.value}",
            )
        }

        fun startServiceWithOverride(
            prompt: String,
            userMessageId: MessageId,
            assistantMessageId: MessageId,
            chatId: ChatId,
            userHasImage: Boolean,
            modelOverride: ChatModelOverride,
        ) {
            loggingPort.info(
                TAG,
                "startServiceWithOverride requested chat=${chatId.value} " +
                    "assistantMessageId=${assistantMessageId.value} " +
                    "override=${modelOverride.displayName} userHasImage=$userHasImage",
            )
            val intent =
                Intent(context, ChatInferenceService::class.java).apply {
                    action = ChatInferenceService.ACTION_START
                    putExtra(ChatInferenceService.EXTRA_PROMPT, prompt)
                    putExtra(ChatInferenceService.EXTRA_USER_MESSAGE_ID, userMessageId.value)
                    putExtra(ChatInferenceService.EXTRA_ASSISTANT_MESSAGE_ID, assistantMessageId.value)
                    putExtra(ChatInferenceService.EXTRA_CHAT_ID, chatId.value)
                    putExtra(ChatInferenceService.EXTRA_USER_HAS_IMAGE, userHasImage)
                    putExtra(ChatInferenceService.EXTRA_MODEL_TYPE, ModelType.FAST.name)
                    ChatInferenceServiceIntentCodec.putModelOverride(this, modelOverride)
                }
            loggingPort.debug(
                TAG,
                "startForegroundService override intent prepared " +
                    "action=${intent.action} chat=${chatId.value} " +
                    "assistantMessageId=${assistantMessageId.value}",
            )
            context.startForegroundService(intent)
            loggingPort.info(
                TAG,
                "startForegroundService override invoked chat=${chatId.value} " +
                    "assistantMessageId=${assistantMessageId.value}",
            )
        }

        fun stopInference() {
            loggingPort.info(TAG, "stopInference requested")
            val intent =
                Intent(context, ChatInferenceService::class.java).apply {
                    action = ChatInferenceService.ACTION_STOP
                }
            try {
                context.startService(intent)
                loggingPort.info(TAG, "stopService intent sent")
            } catch (e: IllegalStateException) {
                loggingPort.warning(TAG, "stopService ignored; service not running in background: ${e.message}")
            }
        }
    }
