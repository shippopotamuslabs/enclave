package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.repository.ChatRepository
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
