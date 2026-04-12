package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting all messages for a chat as a Flow.
 * Listens to database changes via Room Flow.
 */
class GetChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    /**
     * Returns all messages for a chat as a Flow.
     *
     * @param chatId The ID of the chat
     * @return Flow of messages for the chat
     */
    operator fun invoke(chatId: ChatId): Flow<List<Message>> {
        return chatRepository.getMessagesForChat(chatId)
    }
}
