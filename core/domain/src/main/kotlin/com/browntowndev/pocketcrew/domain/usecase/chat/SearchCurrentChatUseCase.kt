package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import javax.inject.Inject

class SearchCurrentChatUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val embeddingEngine: EmbeddingEnginePort,
) {
    suspend operator fun invoke(chatId: ChatId, query: String): List<Message> {
        if (query.isBlank()) return emptyList()
        val queryVector = embeddingEngine.getEmbedding(query)
        return messageRepository.searchMessagesInChat(chatId, queryVector)
    }
}
