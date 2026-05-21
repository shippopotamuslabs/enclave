package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import javax.inject.Inject

class CreateAssistantPlaceholderUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        @Suppress("UnusedPrivateProperty")
        private val chatRepository: ChatRepository,
    ) {
        suspend operator fun invoke(
            chatId: ChatId,
            userMessageCreatedAtMillis: Long,
            assistantMessageId: MessageId,
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
