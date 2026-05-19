package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import javax.inject.Inject

/** Deletes every chat tracked by [chatRepository]. */
class DeleteAllChatsUseCase
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
    ) {
        suspend operator fun invoke() {
            chatRepository.deleteAllChats()
        }
    }
