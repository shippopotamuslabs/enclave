package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import javax.inject.Inject

open class CreateAssistantPlaceholderUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        @Suppress("UnusedPrivateProperty")
        private val chatRepository: ChatRepository,
    ) {
        open suspend operator fun invoke(
            chatId: ChatId,
            userMessageCreatedAtMillis: Long,
            assistantMessageId: MessageId,
            scheduleCloudSync: Boolean = true,
        ): AssistantPlaceholderResult {
            val assistantMessage =
                Message(
                    id = assistantMessageId,
                    chatId = chatId,
                    content = Content(text = ""),
                    role = Role.ASSISTANT,
                    createdAt = userMessageCreatedAtMillis + 1,
                )
            messageRepository.saveMessage(assistantMessage)
            return AssistantPlaceholderResult(assistantMessageId = assistantMessageId)
        }
    }
