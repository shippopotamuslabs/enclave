package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import javax.inject.Inject

class SearchCurrentChatUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
) {
    suspend operator fun invoke(chatId: ChatId, query: String): List<Message> {
        return messageRepository.searchMessagesInChat(chatId, query)
    }
}
