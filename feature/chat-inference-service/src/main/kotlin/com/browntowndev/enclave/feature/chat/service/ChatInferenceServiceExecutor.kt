package com.browntowndev.enclave.feature.chat.service

import android.app.ForegroundServiceStartNotAllowedException
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
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
open class ChatInferenceServiceExecutor
    @Inject
    constructor(
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
        ): Flow<MessageGenerationState> =
            flow {
                loggingPort.info(
                    TAG,
                    "execute() requested chat=${chatId.value} " +
                        "assistantMessageId=${assistantMessageId.value} " +
                        "modelType=${modelType.name} backgroundInferenceEnabled=$backgroundInferenceEnabled",
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    loggingPort.error(TAG, "Failed to start ChatInferenceService for chat=${chatId.value}", e)
                    emit(MessageGenerationState.Failed(e, modelType))
                } catch (e: Throwable) {
                    loggingPort.error(TAG, "Failed to start ChatInferenceService for chat=${chatId.value} due to system error", e)
                    emit(MessageGenerationState.Failed(RuntimeException(e), modelType))
                }
            }

        override fun executeWithOverride(
            prompt: String,
            userMessageId: MessageId,
            assistantMessageId: MessageId,
            chatId: ChatId,
            userHasImage: Boolean,
            modelOverride: ChatModelOverride,
            resolvedConfiguration: ActiveModelConfiguration,
            backgroundInferenceEnabled: Boolean,
        ): Flow<MessageGenerationState> =
            flow {
                loggingPort.info(
                    TAG,
                    "executeWithOverride() requested chat=${chatId.value} " +
                        "assistantMessageId=${assistantMessageId.value} " +
                        "override=${modelOverride.displayName} " +
                        "resolvedConfig=${resolvedConfiguration.name} " +
                        "backgroundInferenceEnabled=$backgroundInferenceEnabled",
                )
                try {
                    loggingPort.debug(
                        TAG,
                        "starting ChatInferenceService override chat=${chatId.value} " +
                            "assistantMessageId=${assistantMessageId.value}",
                    )
                    serviceStarter.startServiceWithOverride(
                        prompt = prompt,
                        userMessageId = userMessageId,
                        assistantMessageId = assistantMessageId,
                        chatId = chatId,
                        userHasImage = userHasImage,
                        modelOverride = modelOverride,
                    )
                    loggingPort.info(
                        TAG,
                        "override service start accepted chat=${chatId.value} " +
                            "assistantMessageId=${assistantMessageId.value}",
                    )
                } catch (e: ForegroundServiceStartNotAllowedException) {
                    loggingPort.error(TAG, "Foreground service start was rejected for chat=${chatId.value}", e)
                    emit(MessageGenerationState.Failed(e, ModelType.FAST))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    loggingPort.error(TAG, "Failed to start ChatInferenceService for chat=${chatId.value}", e)
                    emit(MessageGenerationState.Failed(e, ModelType.FAST))
                } catch (e: Throwable) {
                    loggingPort.error(
                        TAG,
                        "Failed to start ChatInferenceService for chat=${chatId.value} due to system error",
                        e,
                    )
                    emit(MessageGenerationState.Failed(RuntimeException(e), ModelType.FAST))
                }
            }

        override fun stop() {
            serviceStarter.stopInference()
        }
    }
