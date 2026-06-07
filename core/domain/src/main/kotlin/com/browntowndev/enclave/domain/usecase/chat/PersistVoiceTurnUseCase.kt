package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import java.util.UUID
import javax.inject.Inject

class PersistVoiceTurnUseCase
    @Inject
    constructor(
        private val createUserMessageUseCase: CreateUserMessageUseCase,
        private val messageRepository: MessageRepository,
    ) {
        suspend operator fun invoke(
            chatId: ChatId?,
            userText: String,
            assistantText: String,
        ): ChatId? {
            val hasUserText = userText.isNotBlank()
            val hasAssistantText = assistantText.isNotBlank()
            if (!hasUserText && !hasAssistantText) {
                return chatId
            }

            if (hasUserText) {
                val userPromptResult =
                    createUserMessageUseCase(
                        Message(
                            id = MessageId(UUID.randomUUID().toString()),
                            role = Role.USER,
                            chatId = chatId,
                            messageState = MessageState.COMPLETE,
                            content = Content(text = userText),
                        ),
                    )
                val resolvedChatId = userPromptResult.chatId
                if (hasAssistantText) {
                    saveAssistantMessage(
                        chatId = resolvedChatId,
                        assistantText = assistantText,
                        createdAtMillis = userPromptResult.userMessageCreatedAtMillis + ASSISTANT_CREATED_AT_OFFSET_MS,
                    )
                }
                return resolvedChatId
            }

            if (chatId == null) {
                val userPromptResult =
                    createUserMessageUseCase(
                        Message(
                            id = MessageId(UUID.randomUUID().toString()),
                            role = Role.USER,
                            chatId = null,
                            messageState = MessageState.COMPLETE,
                            content = Content(text = assistantText),
                        ),
                    )
                saveAssistantMessage(
                    chatId = userPromptResult.chatId,
                    assistantText = assistantText,
                    createdAtMillis = userPromptResult.userMessageCreatedAtMillis + ASSISTANT_CREATED_AT_OFFSET_MS,
                )
                return userPromptResult.chatId
            }

            saveAssistantMessage(
                chatId = chatId,
                assistantText = assistantText,
                createdAtMillis = System.currentTimeMillis(),
            )
            return chatId
        }

        private suspend fun saveAssistantMessage(
            chatId: ChatId,
            assistantText: String,
            createdAtMillis: Long,
        ) {
            messageRepository.saveMessage(
                Message(
                    id = MessageId(UUID.randomUUID().toString()),
                    role = Role.ASSISTANT,
                    chatId = chatId,
                    messageState = MessageState.COMPLETE,
                    modelType = ModelType.LIVE_VOICE,
                    content = Content(text = assistantText),
                    createdAt = createdAtMillis,
                ),
            )
        }

        private companion object {
            /** Keeps assistant replies immediately after their user turn in transcript order. */
            const val ASSISTANT_CREATED_AT_OFFSET_MS = 1L
        }
    }
