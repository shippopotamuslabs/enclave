package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Deletes every chat tracked by [chatRepository]. */
class DeleteAllChatsUseCase
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
    ) {
        suspend operator fun invoke() {
            chatRepository.getAllChats().first().forEach { chat ->
                chatRepository.deleteChat(chat.id)
            }
        }
    }
