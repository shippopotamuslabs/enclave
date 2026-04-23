package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.pocketcrew.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceBusyException
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository

import com.browntowndev.pocketcrew.domain.port.repository.ExtractedUrlTrackerPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
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
    private val pipelineExecutor: PipelineExecutorPort,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val loggingPort: LoggingPort,
    private val activeModelProvider: ActiveModelProviderPort,
    private val extractedUrlTracker: ExtractedUrlTrackerPort,
    private val chatInferenceExecutor: ChatInferenceExecutorPort,
    private val embeddingEngine: EmbeddingEnginePort,
    private val activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort = NoOpActiveChatTurnSnapshotPort,
) {
    private val persistAccumulatedMessages = PersistAccumulatedChatMessagesUseCase(
        chatRepository = chatRepository,
        messageRepository = messageRepository,
        embeddingEngine = embeddingEngine,
        extractedUrls = extractedUrlTracker.urls
    )

    operator fun invoke(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        mode: Mode,
        backgroundInferenceEnabled: Boolean,
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
                backgroundInferenceEnabled = backgroundInferenceEnabled,
            )
            val accumulatorManager = ChatGenerationAccumulatorManager(
                mode = mode,
                chatId = chatId,
                userMessageId = userMessageId,
                defaultAssistantMessageId = assistantMessageId,
                chatRepository = chatRepository,
            )
            var terminalSeen = false

            // Observe extraction events so the accumulator marks sources as extracted
            // in real time, ensuring the UI shows the “read” indicator during generation
            // and the persisted snapshots carry the correct flag.
            try {
                baseFlow.collect { state ->
                    // Apply any URLs that have been extracted since the last emission.
                    // The ExtractedUrlTracker is updated by the ExtractToolExecutor when a URL
                    // is read, but the accumulator's sources still have extracted=false. We
                    // reconcile this here so that snapshots emitted to the ViewModel carry the
                    // correct extracted flag in real time.
                    val extractedUrls = extractedUrlTracker.urls
                    if (extractedUrls.isNotEmpty()) {
                        accumulatorManager.markSourcesExtracted(extractedUrls.toList())
                    }
                    if (state.isTerminal) {
                        terminalSeen = true
                    }
                    val accumulatedMessages = accumulatorManager.reduce(state)
                    if (!backgroundInferenceEnabled) {
                        activeChatTurnSnapshotPort.publish(
                            key = ActiveChatTurnKey(
                                chatId = chatId,
                                assistantMessageId = assistantMessageId,
                            ),
                            snapshot = accumulatedMessages,
                        )
                    }
                    emit(accumulatedMessages)
                }
            } finally {
                try {
                    withContext(NonCancellable + Dispatchers.IO) {
                        if (!shouldSkipFinalPersistenceForBackgroundCancellation(
                                mode = mode,
                                backgroundInferenceEnabled = backgroundInferenceEnabled,
                                terminalSeen = terminalSeen,
                            )
                        ) {
                            accumulatorManager.markIncompleteAsCancelled()
                            persistAccumulatedMessages(accumulatorManager)
                            
                            // Generate and save embedding for the user message
                            val userMessage = messageRepository.getMessageById(userMessageId)
                            if (userMessage != null && userMessage.content.text.isNotBlank()) {
                                val embedding = embeddingEngine.getEmbedding(userMessage.content.text)
                                messageRepository.saveEmbedding(userMessageId, embedding)
                            }
                            
                            extractedUrlTracker.clear()
                        }
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
        backgroundInferenceEnabled: Boolean,
    ): Flow<MessageGenerationState> = when (mode) {
        Mode.FAST -> executeSingleModelMode(
            prompt = prompt,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            chatId = chatId,
            userHasImage = userHasImage,
            modelType = ModelType.FAST,
            backgroundInferenceEnabled = backgroundInferenceEnabled,
        )

        Mode.THINKING -> executeSingleModelMode(
            prompt = prompt,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            chatId = chatId,
            userHasImage = userHasImage,
            modelType = ModelType.THINKING,
            backgroundInferenceEnabled = backgroundInferenceEnabled,
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
        backgroundInferenceEnabled: Boolean,
    ): Flow<MessageGenerationState> {
        return chatInferenceExecutor.execute(
            prompt = prompt,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            chatId = chatId,
            userHasImage = userHasImage,
            modelType = modelType,
            backgroundInferenceEnabled = backgroundInferenceEnabled,
        )
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
                ?.let { config -> config.isLocal == false && config.isMultimodal }
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

    private suspend fun kotlinx.coroutines.flow.FlowCollector<MessageGenerationState>.emitBusyState(modelType: ModelType) {
        emit(MessageGenerationState.Failed(InferenceBusyException(), modelType))
    }

    private fun shouldSkipFinalPersistenceForBackgroundCancellation(
        mode: Mode,
        backgroundInferenceEnabled: Boolean,
        terminalSeen: Boolean,
    ): Boolean {
        return backgroundInferenceEnabled && mode != Mode.CREW && !terminalSeen
    }

    private companion object {
        private const val TAG = "GenerateChatResponse"
        private const val FLOW_BUFFER_SIZE = 64
    }
}

private object NoOpActiveChatTurnSnapshotPort : ActiveChatTurnSnapshotPort {
    override fun observe(key: ActiveChatTurnKey): Flow<AccumulatedMessages?> {
        return flowOf(null)
    }

    override suspend fun publish(
        key: ActiveChatTurnKey,
        snapshot: AccumulatedMessages,
    ) = Unit

    override suspend fun markSourcesExtracted(
        key: ActiveChatTurnKey,
        urls: List<String>,
    ) = Unit

    override suspend fun acknowledgeHandoff(key: ActiveChatTurnKey) = Unit

    override suspend fun clear(key: ActiveChatTurnKey) = Unit
}
