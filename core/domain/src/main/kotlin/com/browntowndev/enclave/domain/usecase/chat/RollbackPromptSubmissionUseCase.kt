package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import javax.inject.Inject

class RollbackPromptSubmissionUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val chatRepository: ChatRepository,
        private val transactionProvider: TransactionProvider,
    ) {
        suspend operator fun invoke(
            chatId: ChatId,
            userMessageId: MessageId,
            assistantMessageId: MessageId,
            wasNewChat: Boolean,
        ) {
            if (wasNewChat) {
                transactionProvider.runInTransaction {
                    messageRepository.softDeleteMessage(userMessageId)
                    messageRepository.softDeleteMessage(assistantMessageId)
                }
                chatRepository.deleteChat(chatId)
            } else {
                transactionProvider.runInTransaction {
                    messageRepository.softDeleteMessage(userMessageId)
                    messageRepository.softDeleteMessage(assistantMessageId)
                }
            }
        }
    }
