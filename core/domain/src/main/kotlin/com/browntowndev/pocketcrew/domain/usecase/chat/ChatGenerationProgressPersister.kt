package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.ExtractedUrlTrackerPort
import com.browntowndev.pocketcrew.domain.util.Clock
import javax.inject.Inject

class ChatGenerationProgressPersister @Inject constructor(
    private val chatRepository: ChatRepository,
    private val extractedUrlTracker: ExtractedUrlTrackerPort,
    private val clock: Clock,
) {
    fun startSession(
        mode: Mode,
        chatId: ChatId,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        partialPersistIntervalMs: Long = DEFAULT_PARTIAL_PERSIST_INTERVAL_MS,
    ): ChatGenerationProgressSession {
        val accumulatorManager = ChatGenerationAccumulatorManager(
            mode = mode,
            chatId = chatId,
            userMessageId = userMessageId,
            defaultAssistantMessageId = assistantMessageId,
            chatRepository = chatRepository,
        )
        return ChatGenerationProgressSession(
            accumulatorManager = accumulatorManager,
            chatRepository = chatRepository,
            extractedUrlTracker = extractedUrlTracker,
            clock = clock,
            partialPersistIntervalMs = partialPersistIntervalMs,
        )
    }

    private companion object {
        private const val DEFAULT_PARTIAL_PERSIST_INTERVAL_MS = 500L
    }
}

class ChatGenerationProgressSession internal constructor(
    private val accumulatorManager: ChatGenerationAccumulatorManager,
    private val chatRepository: ChatRepository,
    private val extractedUrlTracker: ExtractedUrlTrackerPort,
    private val clock: Clock,
    private val partialPersistIntervalMs: Long,
) {
    private var lastPersistedAt: Long? = null

    suspend fun applyState(state: MessageGenerationState): AccumulatedMessages {
        markExtractedSources()
        val accumulatedMessages = accumulatorManager.reduce(state)
        val now = clock.currentTimeMillis()
        if (shouldPersist(state, now)) {
            persistAccumulatedMessages()
            lastPersistedAt = now
        }
        if (state.isTerminal) {
            extractedUrlTracker.clear()
        }
        return accumulatedMessages
    }

    suspend fun flush(markIncompleteAsCancelled: Boolean) {
        if (accumulatorManager.messages.isEmpty()) return

        markExtractedSources()
        if (markIncompleteAsCancelled) {
            accumulatorManager.markIncompleteAsCancelled()
        }
        persistAccumulatedMessages()
        extractedUrlTracker.clear()
    }

    private suspend fun persistAccumulatedMessages() {
        PersistAccumulatedChatMessagesUseCase(
            chatRepository = chatRepository,
            extractedUrls = extractedUrlTracker.urls,
        )(accumulatorManager)
    }

    private fun markExtractedSources() {
        val extractedUrls = extractedUrlTracker.urls
        if (extractedUrls.isNotEmpty()) {
            accumulatorManager.markSourcesExtracted(extractedUrls.toList())
        }
    }

    private fun shouldPersist(
        state: MessageGenerationState,
        now: Long,
    ): Boolean {
        val previousPersistAt = lastPersistedAt
        return state.isTerminal ||
            previousPersistAt == null ||
            partialPersistIntervalMs <= 0L ||
            now - previousPersistAt >= partialPersistIntervalMs
    }

}
