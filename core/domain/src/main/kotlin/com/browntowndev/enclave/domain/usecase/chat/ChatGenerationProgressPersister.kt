package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.ExtractedUrlTrackerPort
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import javax.inject.Inject

class ChatGenerationProgressPersister
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
        private val messageRepository: MessageRepository,
        private val embeddingEngine: EmbeddingEnginePort,
        private val extractedUrlTracker: ExtractedUrlTrackerPort,
    ) {
        fun startSession(
            mode: Mode,
            chatId: ChatId,
            userMessageId: MessageId,
            assistantMessageId: MessageId,
        ): ChatGenerationProgressSession {
            val accumulatorManager =
                ChatGenerationAccumulatorManager(
                    mode = mode,
                    chatId = chatId,
                    userMessageId = userMessageId,
                    defaultAssistantMessageId = assistantMessageId,
                    chatRepository = chatRepository,
                )
            return ChatGenerationProgressSession(
                accumulatorManager = accumulatorManager,
                chatRepository = chatRepository,
                messageRepository = messageRepository,
                embeddingEngine = embeddingEngine,
                extractedUrlTracker = extractedUrlTracker,
            )
        }
    }

class ChatGenerationProgressSession internal constructor(
    private val accumulatorManager: ChatGenerationAccumulatorManager,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val embeddingEngine: EmbeddingEnginePort,
    private val extractedUrlTracker: ExtractedUrlTrackerPort,
) {
    suspend fun applyState(state: MessageGenerationState): AccumulatedMessages {
        markExtractedSources()
        val accumulatedMessages = accumulatorManager.reduce(state)
        if (state is MessageGenerationState.WebSearchSourcesAttached && state.sources.isNotEmpty()) {
            chatRepository.persistWebSearchSources(
                messageId = state.sources.first().messageId,
                sources = state.sources,
            )
        }
        if (state.isTerminal) {
            persistAccumulatedMessages()
            extractedUrlTracker.clear()
        }
        return accumulatedMessages
    }

    fun accumulatedMessages(): AccumulatedMessages = accumulatorManager.toMessagesState()

    suspend fun flush(markIncompleteAsCancelled: Boolean) {
        if (markIncompleteAsCancelled) {
            accumulatorManager.markIncompleteAsCancelled()
            persistAccumulatedMessages()
            extractedUrlTracker.clear()
            return
        }

        if (accumulatorManager.messages.isEmpty()) return
        if (accumulatorManager.messages.values.any { accumulator -> !accumulator.isComplete }) return

        markExtractedSources()
        persistAccumulatedMessages()
        extractedUrlTracker.clear()
    }

    private suspend fun persistAccumulatedMessages() {
        PersistAccumulatedChatMessagesUseCase(
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            embeddingEngine = embeddingEngine,
            extractedUrls = extractedUrlTracker.urls,
        )(accumulatorManager)
    }

    private fun markExtractedSources() {
        val extractedUrls = extractedUrlTracker.urls
        if (extractedUrls.isNotEmpty()) {
            accumulatorManager.markWebSearchSourcesExtracted(extractedUrls.toList())
        }
    }
}
