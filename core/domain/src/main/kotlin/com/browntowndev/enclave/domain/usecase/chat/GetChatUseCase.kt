package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting all messages for a chat as a Flow.
 * Listens to database changes via Room Flow.
 */
open class GetChatUseCase
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
    ) {
        /**
         * Returns all messages for a chat as a Flow.
         *
         * @param chatId The ID of the chat
         * @return Flow of messages for the chat
         */
        open operator fun invoke(chatId: ChatId): Flow<List<Message>> = chatRepository.getMessagesForChat(chatId)
    }
