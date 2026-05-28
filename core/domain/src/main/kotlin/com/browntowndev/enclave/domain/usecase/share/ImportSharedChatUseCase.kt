package com.browntowndev.enclave.domain.usecase.share

import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.share.SharedChat
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class ImportSharedChatUseCase
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
        private val messageRepository: MessageRepository,
    ) {
        suspend operator fun invoke(sharedChat: SharedChat): ChatId {
            val sourceChatId = sharedChat.sourceChatId?.let { ChatId(it) }
            if (sourceChatId != null) {
                val existingMessages = messageRepository.getMessagesForChat(sourceChatId)
                if (existingMessages.isNotEmpty()) {
                    return sourceChatId
                }
            }
            val now = System.currentTimeMillis()
            val newChatId =
                chatRepository.createChat(
                    Chat(
                        id = ChatId(UUID.randomUUID().toString()),
                        name = "Imported Chat",
                        created = Date(now),
                        lastModified = Date(now),
                    ),
                )
            sharedChat.messages.forEach { msg ->
                messageRepository.saveMessage(
                    Message(
                        id = MessageId(UUID.randomUUID().toString()),
                        chatId = newChatId,
                        role = Role.fromApiValue(msg.role),
                        content = Content(text = msg.content),
                        createdAt = msg.timestamp,
                        messageState = MessageState.COMPLETE,
                    ),
                )
            }
            return newChatId
        }
    }
