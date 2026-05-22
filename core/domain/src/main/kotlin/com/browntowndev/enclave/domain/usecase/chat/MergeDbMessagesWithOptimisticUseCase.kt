package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.Message
import javax.inject.Inject

class MergeDbMessagesWithOptimisticUseCase
    @Inject
    constructor() {
        operator fun invoke(
            dbMessages: List<Message>,
            optimisticUserMessage: Message?,
        ): List<Message> {
            if (optimisticUserMessage == null) {
                return dbMessages
            }
            if (dbMessages.any { it.id == optimisticUserMessage.id }) {
                return dbMessages
            }
            return (dbMessages + optimisticUserMessage).sortedBy { it.createdAt }
        }
    }
