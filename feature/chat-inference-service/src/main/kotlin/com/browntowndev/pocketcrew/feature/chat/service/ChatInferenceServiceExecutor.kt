package com.browntowndev.pocketcrew.feature.chat.service

import android.app.ForegroundServiceStartNotAllowedException
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ChatInferenceExecutorPort] that starts the Android
 * [ChatInferenceService] foreground service for background-safe execution.
 *
 * Successful service start returns no state stream. The service owns live
 * token publication and Room persistence. Only service-start failures emit a
 * terminal [MessageGenerationState.Failed] so the caller can persist the error.
 */
@Singleton
class ChatInferenceServiceExecutor @Inject constructor(
    private val serviceStarter: ChatInferenceServiceStarter,
    private val loggingPort: LoggingPort,
) : ChatInferenceExecutorPort {

    companion object {
        private const val TAG = "ChatInferenceServiceExec"
    }

    override fun execute(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        userHasImage: Boolean,
        modelType: ModelType,
        backgroundInferenceEnabled: Boolean, // Ignored — this executor IS the background path
    ): Flow<MessageGenerationState> {
        return flow {
            loggingPort.info(
                TAG,
                "execute() requested chat=${chatId.value} assistantMessageId=${assistantMessageId.value} modelType=${modelType.name} backgroundInferenceEnabled=$backgroundInferenceEnabled",
            )
            try {
                loggingPort.debug(
                    TAG,
                    "starting ChatInferenceService chat=${chatId.value} assistantMessageId=${assistantMessageId.value}",
                )
                serviceStarter.startService(
                    prompt = prompt,
                    userMessageId = userMessageId,
                    assistantMessageId = assistantMessageId,
                    chatId = chatId,
                    userHasImage = userHasImage,
                    modelType = modelType,
                )
                loggingPort.info(
                    TAG,
                    "service start accepted chat=${chatId.value} assistantMessageId=${assistantMessageId.value}",
                )
            } catch (e: ForegroundServiceStartNotAllowedException) {
                loggingPort.error(TAG, "Foreground service start was rejected for chat=${chatId.value}", e)
                emit(MessageGenerationState.Failed(e, modelType))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                loggingPort.error(TAG, "Failed to start ChatInferenceService for chat=${chatId.value}", e)
                emit(MessageGenerationState.Failed(e, modelType))
            }
        }
    }

    override fun stop() {
        serviceStarter.stopInference()
    }
}
