package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.ExtractedUrlTrackerPort
import javax.inject.Inject

class ChatGenerationProgressPersister @Inject constructor(
    private val chatRepository: ChatRepository,
    private val extractedUrlTracker: ExtractedUrlTrackerPort,
) {
    fun startSession(
        mode: Mode,
        chatId: ChatId,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
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
        )
    }
}

class ChatGenerationProgressSession internal constructor(
    private val accumulatorManager: ChatGenerationAccumulatorManager,
    private val chatRepository: ChatRepository,
    private val extractedUrlTracker: ExtractedUrlTrackerPort,
) {
    suspend fun applyState(state: MessageGenerationState): AccumulatedMessages {
        markExtractedSources()
        val accumulatedMessages = accumulatorManager.reduce(state)
        if (state.isTerminal) {
            persistAccumulatedMessages()
            extractedUrlTracker.clear()
        }
        return accumulatedMessages
    }

    suspend fun flush(markIncompleteAsCancelled: Boolean) {
        if (markIncompleteAsCancelled) {
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
            extractedUrls = extractedUrlTracker.urls,
        )(accumulatorManager)
    }

    private fun markExtractedSources() {
        val extractedUrls = extractedUrlTracker.urls
        if (extractedUrls.isNotEmpty()) {
            accumulatorManager.markSourcesExtracted(extractedUrls.toList())
        }
    }
}
