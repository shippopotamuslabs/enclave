package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceBusyException
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.CancellationException
import javax.inject.Inject

/**
 * Executes single-model inference directly in the caller's coroutine scope.
 * This is the fallback implementation when background inference is disabled.
 *
 * Pure Kotlin — no Android framework dependencies. Lives in `:domain`.
 */
class DirectChatInferenceExecutor @Inject constructor(
    private val inferenceFactory: InferenceFactoryPort,
    private val activeModelProvider: ActiveModelProviderPort,
    private val messageRepository: MessageRepository,
    private val settingsRepository: SettingsRepository,
    private val memoriesRepository: MemoriesRepository,
    private val embeddingEnginePort: EmbeddingEnginePort,
    private val searchToolPromptComposer: SearchToolPromptComposer,
    private val loggingPort: LoggingPort,
) : ChatInferenceExecutorPort {

    private val historyRehydrator = ChatHistoryRehydrator(
        messageRepository = messageRepository,
        loggingPort = loggingPort,
    )
    private val inferenceRequestPreparer = ChatInferenceRequestPreparer(
        activeModelProvider = activeModelProvider,
        settingsRepository = settingsRepository,
        messageRepository = messageRepository,
        memoriesRepository = memoriesRepository,
        embeddingEnginePort = embeddingEnginePort,
        searchToolPromptComposer = searchToolPromptComposer,
        loggingPort = loggingPort,
    )

    override fun execute(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        userHasImage: Boolean,
        modelType: ModelType,
        backgroundInferenceEnabled: Boolean, // Ignored by direct executor
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
            emit(MessageGenerationState.Failed(error = InferenceBusyException(), modelType = modelType))
        } catch (e: IllegalStateException) {
            emit(MessageGenerationState.Failed(e, modelType))
        } catch (e: java.io.IOException) {
            emit(MessageGenerationState.Failed(e, modelType))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            loggingPort.error(TAG, "Unexpected error in $modelType mode", e)
            emit(MessageGenerationState.Failed(error = e, modelType = modelType))
        }
    }.flowOn(Dispatchers.Default)

    override fun stop() {
        // No-op for direct executor, cancellation is handled by the coroutine scope
    }

    private fun generateWithService(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        service: LlmInferencePort,
        modelType: ModelType,
    ): Flow<MessageGenerationState> = flow {
        val config = activeModelProvider.getActiveConfiguration(modelType)
        val preparedRequest = inferenceRequestPreparer(
            prompt = prompt,
            chatId = chatId,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            modelType = modelType,
        )

        try {
            historyRehydrator(
                chatId = chatId,
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
                service = service,
                contextWindowTokens = config?.contextWindow ?: 4096,
                shouldSummarize = config?.isLocal != true,
                currentPrompt = preparedRequest.prompt,
                options = preparedRequest.options,
            )
        } catch (e: Exception) {
            loggingPort.debug(TAG, "Failed to rehydrate history: ${e.message}")
        }

        service.sendPrompt(
            preparedRequest.prompt,
            preparedRequest.options,
            closeConversation = false,
        ).collect { event ->
            when (event) {
                is InferenceEvent.EngineLoading -> {
                    emit(MessageGenerationState.EngineLoading(event.modelType))
                }

                is InferenceEvent.Processing -> {
                    emit(MessageGenerationState.Processing(event.modelType))
                }

                is InferenceEvent.Thinking -> {
                    emit(MessageGenerationState.ThinkingLive(event.chunk, modelType))
                }

                is InferenceEvent.PartialResponse -> {
                    emit(MessageGenerationState.GeneratingText(event.chunk, event.modelType))
                }

                is InferenceEvent.TavilyResults -> {
                    emit(MessageGenerationState.TavilySourcesAttached(event.sources, event.modelType))
                }

                is InferenceEvent.Artifacts -> {
                    emit(MessageGenerationState.ArtifactsAttached(event.artifacts, event.modelType))
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
    }

    private companion object {
        private const val TAG = "DirectChatInference"
    }
}