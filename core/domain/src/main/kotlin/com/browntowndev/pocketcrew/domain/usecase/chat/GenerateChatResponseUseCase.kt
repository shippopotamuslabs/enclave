package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceBusyException
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

/**
 * Use case for generating chat responses.
 *
 * ARCHITECTURE (Real-Time Flow Refactor):
 * 1. Accumulates state internally using ChatGenerationAccumulatorManager.
 * 2. Emits AccumulatedMessages on every state change for real-time UI updates.
 * 3. Persists all accumulated state to DB in a single transaction on completion.
 * 4. Uses buffer(64) for backpressure handling.
 */
class GenerateChatResponseUseCase @Inject constructor(
    private val inferenceFactory: InferenceFactoryPort,
    private val pipelineExecutor: PipelineExecutorPort,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val loggingPort: LoggingPort,
    private val activeModelProvider: ActiveModelProviderPort,
    private val settingsRepository: SettingsRepository,
    private val searchToolPromptComposer: SearchToolPromptComposer,
) {
    private val historyRehydrator = ChatHistoryRehydrator(
        messageRepository = messageRepository,
        loggingPort = loggingPort,
    )
    private val inferenceRequestPreparer = ChatInferenceRequestPreparer(
        activeModelProvider = activeModelProvider,
        settingsRepository = settingsRepository,
        messageRepository = messageRepository,
        searchToolPromptComposer = searchToolPromptComposer,
        loggingPort = loggingPort,
    )
    private val persistAccumulatedMessages = PersistAccumulatedChatMessagesUseCase(chatRepository)

    operator fun invoke(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        mode: Mode,
    ): Flow<AccumulatedMessages> {
        return flow {
            val userMessage = messageRepository.getMessageById(userMessageId)
                ?: throw IllegalStateException("User message $userMessageId was not found")

            val baseFlow = executeMode(
                mode = mode,
                prompt = prompt,
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                userHasImage = userMessage.content.imageUri != null,
            )
            val accumulatorManager = ChatGenerationAccumulatorManager(
                mode = mode,
                chatId = chatId,
                userMessageId = userMessageId,
                defaultAssistantMessageId = assistantMessageId,
                chatRepository = chatRepository,
            )

            try {
                baseFlow.collect { state ->
                    emit(accumulatorManager.reduce(state))
                }
            } finally {
                try {
                    withContext(Dispatchers.IO) {
                        persistAccumulatedMessages(accumulatorManager)
                    }
                } catch (e: Exception) {
                    loggingPort.error(TAG, "Failed to persist messages", e)
                }
            }
        }.buffer(FLOW_BUFFER_SIZE).flowOn(Dispatchers.Default)
    }

    private fun executeMode(
        mode: Mode,
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        userHasImage: Boolean,
    ): Flow<MessageGenerationState> = when (mode) {
        Mode.FAST -> executeSingleModelMode(
            prompt = prompt,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            chatId = chatId,
            userHasImage = userHasImage,
            modelType = ModelType.FAST,
        )

        Mode.THINKING -> executeSingleModelMode(
            prompt = prompt,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            chatId = chatId,
            userHasImage = userHasImage,
            modelType = ModelType.THINKING,
        )

        Mode.CREW -> executeCrewMode(
            prompt = prompt,
            chatId = chatId,
            userHasImage = userHasImage,
        )
    }

    private fun executeSingleModelMode(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        userHasImage: Boolean,
        modelType: ModelType,
    ): Flow<MessageGenerationState> = flow {
        try {
            if (userHasImage) {
                emit(MessageGenerationState.Processing(modelType))
            }
            inferenceFactory.withInferenceService(modelType) { service ->
                emitAll(
                    generateWithService(
                        prompt = prompt,
                        userMessageId = userMessageId,
                        assistantMessageId = assistantMessageId,
                        chatId = chatId,
                        service = service,
                        modelType = modelType,
                    )
                )
            }
        } catch (e: InferenceBusyException) {
            emitBusyState(modelType)
        } catch (e: IllegalStateException) {
            emit(MessageGenerationState.Failed(e, modelType))
        } catch (e: java.io.IOException) {
            emit(MessageGenerationState.Failed(e, modelType))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            loggingPort.error(TAG, "Unexpected error in $modelType mode", e)
            emit(MessageGenerationState.Failed(error = e, modelType = modelType))
        }
    }

    private fun executeCrewMode(
        prompt: String,
        chatId: ChatId,
        userHasImage: Boolean,
    ): Flow<MessageGenerationState> = flow {
        try {
            if (userHasImage) {
                emit(MessageGenerationState.Processing(ModelType.MAIN))
            }
            val apiVisionConfigured = activeModelProvider.getActiveConfiguration(ModelType.VISION)
                ?.let { config -> config.isLocal == false && config.visionCapable }
                ?: false
            val crewPrompt = if (userHasImage && apiVisionConfigured) {
                prepareChatPrompt(
                    prompt = prompt,
                    hasImageContext = true,
                    imageHandling = ChatImageHandling.TOOL,
                )
            } else {
                prompt
            }

            emitAll(
                pipelineExecutor.executePipeline(
                    chatId = chatId.value,
                    userMessage = crewPrompt,
                )
            )
        } catch (e: InferenceBusyException) {
            emitBusyState(ModelType.MAIN)
        } catch (e: IllegalStateException) {
            emit(MessageGenerationState.Failed(e, ModelType.MAIN))
        } catch (e: java.io.IOException) {
            emit(MessageGenerationState.Failed(e, ModelType.MAIN))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            loggingPort.error(TAG, "Unexpected error in CREW mode", e)
            emit(MessageGenerationState.Failed(e, ModelType.MAIN))
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<MessageGenerationState>.emitBusyState(
        modelType: ModelType,
    ) {
        emit(
            MessageGenerationState.Failed(
                error = InferenceBusyException(),
                modelType = modelType,
            )
        )
    }

    private fun generateWithService(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        service: LlmInferencePort,
        modelType: ModelType,
    ): Flow<MessageGenerationState> = flow {
        try {
            historyRehydrator(chatId, userMessageId, assistantMessageId, service)
        } catch (e: Exception) {
            loggingPort.debug(TAG, "Failed to rehydrate history: ${e.message}")
        }

        val preparedRequest = inferenceRequestPreparer(
            prompt = prompt,
            chatId = chatId,
            userMessageId = userMessageId,
            modelType = modelType,
        )

        service.sendPrompt(
            preparedRequest.prompt,
            preparedRequest.options,
            closeConversation = false,
        ).collect { event ->
            when (event) {
                is InferenceEvent.Thinking -> {
                    emit(MessageGenerationState.ThinkingLive(event.chunk, modelType))
                }

                is InferenceEvent.PartialResponse -> {
                    emit(MessageGenerationState.GeneratingText(event.chunk, event.modelType))
                }

                is InferenceEvent.Finished -> {
                    emit(MessageGenerationState.Finished(event.modelType))
                }

                is InferenceEvent.SafetyBlocked -> {
                    loggingPort.warning(
                        TAG,
                        "InferenceEvent.SafetyBlocked modelType=${event.modelType} reason=${event.reason}",
                    )
                    emit(MessageGenerationState.Blocked(event.reason, event.modelType))
                }

                is InferenceEvent.Error -> {
                    loggingPort.error(
                        TAG,
                        "InferenceEvent.Error modelType=${event.modelType} message=${event.cause.message}",
                        event.cause,
                    )
                    emit(MessageGenerationState.Failed(event.cause, event.modelType))
                }
            }
        }
    }.flowOn(Dispatchers.Default)

    private companion object {
        private const val TAG = "GenerateChatResponse"
        private const val FLOW_BUFFER_SIZE = 64
    }
}
