package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class SearchChatsUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val embeddingEngine: EmbeddingEnginePort,
) {
    operator fun invoke(query: String): Flow<List<Chat>> = flow {
        if (query.isBlank()) {
            emitAll(chatRepository.getAllChats())
            return@flow
        }

        val queryVector = embeddingEngine.getEmbedding(query)
        val messageIds = messageRepository.searchSimilarMessages(queryVector)
        emitAll(chatRepository.searchChats(query, messageIds))
    }
}
