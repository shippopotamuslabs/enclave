package com.browntowndev.pocketcrew.feature.chat.service

import android.app.ForegroundServiceStartNotAllowedException
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.feature.inference.InferenceEventBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ChatInferenceExecutorPort] that delegates to the Android
 * [ChatInferenceService] foreground service for background-safe execution.
 *
 * Starts the service via [ChatInferenceServiceStarter] and collects the resulting
 * state emissions from [ChatInferenceService]'s SharedFlow. Handles
 * [ForegroundServiceStartNotAllowedException] on Android 12+ by emitting a
 * [MessageGenerationState.Failed] so the UI can fall back to direct execution.
 */
@Singleton
class ChatInferenceServiceExecutor @Inject constructor(
    private val serviceStarter: ChatInferenceServiceStarter,
    private val loggingPort: LoggingPort,
    private val inferenceEventBus: InferenceEventBus,
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
            val requestKey = InferenceEventBus.ChatRequestKey(chatId, assistantMessageId)
            loggingPort.info(
                TAG,
                "execute() requested chat=${chatId.value} assistantMessageId=${assistantMessageId.value} modelType=${modelType.name} backgroundInferenceEnabled=$backgroundInferenceEnabled",
            )
            val stateFlow = inferenceEventBus.openChatRequest(requestKey)
            var terminalSeen = false

            try {
                loggingPort.debug(
                    TAG,
                    "opening request stream chat=${chatId.value} assistantMessageId=${assistantMessageId.value}",
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
                    "service start returned chat=${chatId.value} assistantMessageId=${assistantMessageId.value}; collecting states",
                )
                emitAll(
                    stateFlow.transformWhile { state ->
                        loggingPort.debug(
                            TAG,
                            "state received chat=${chatId.value} assistantMessageId=${assistantMessageId.value} state=${state::class.simpleName}",
                        )
                        emit(state)
                        val terminal = state.isTerminal
                        if (terminal) {
                            terminalSeen = true
                            loggingPort.info(
                                TAG,
                                "terminal state received chat=${chatId.value} assistantMessageId=${assistantMessageId.value} state=${state::class.simpleName}",
                            )
                        }
                        !terminal
                    }
                )
            } catch (e: ForegroundServiceStartNotAllowedException) {
                terminalSeen = true
                loggingPort.error(TAG, "ForegroundServiceStartNotAllowedException for chat $chatId", e)
                emit(MessageGenerationState.Failed(e, modelType))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                terminalSeen = true
                loggingPort.error(TAG, "Failed to start or collect ChatInferenceService for chat $chatId", e)
                emit(MessageGenerationState.Failed(e, modelType))
            } finally {
                loggingPort.debug(
                    TAG,
                    "clearing request stream chat=${chatId.value} assistantMessageId=${assistantMessageId.value}",
                )
                inferenceEventBus.clearChatRequest(requestKey)
            }
        }
    }

    override fun stop() {
        serviceStarter.stopInference()
    }


}
