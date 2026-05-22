package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Mode
import kotlinx.coroutines.flow.Flow

interface ChatUseCases {
    /**
     * Creates the initial user prompt.
     */
    val processPromptUseCase: CreateUserMessageUseCase

    /**
     * Persists the assistant placeholder after the user message is saved.
     */
    val createAssistantPlaceholderUseCase: CreateAssistantPlaceholderUseCase

    /**
     * Merges database messages with an optimistic user row for UI projection.
     */
    val mergeDbMessagesWithOptimisticUseCase: MergeDbMessagesWithOptimisticUseCase

    /**
     * Generates a chat response for the given prompt.
     */
    val generateChatResponseUseCase: GenerateChatResponseUseCase

    /**
     * Gets the chat messages for the given chat ID.
     */
    val getChatUseCase: GetChatUseCase

    /**
     * Listens to local microphone audio and transcribes it on-device.
     */
    val listenToSpeechUseCase: ListenToSpeechUseCase

    /**
     * Merges database messages with in-flight messages.
     */
    val mergeMessagesUseCase: MergeMessagesUseCase

    /**
     * Processes a file attachment.
     */
    val processFileAttachmentUseCase: ProcessFileAttachmentUseCase

    /**
     * Creates the initial user prompt.
     * @param message The user's input text
     */
    suspend fun processPrompt(message: Message): UserPromptResult = processPromptUseCase(message)

    /**
     * Persists an empty assistant placeholder row after the user message.
     */
    suspend fun createAssistantPlaceholder(
        chatId: ChatId,
        userMessageCreatedAtMillis: Long,
        assistantMessageId: MessageId,
    ): AssistantPlaceholderResult =
        createAssistantPlaceholderUseCase(
            chatId = chatId,
            userMessageCreatedAtMillis = userMessageCreatedAtMillis,
            assistantMessageId = assistantMessageId,
        )

    /**
     * Generates a chat response for the given prompt using streaming.
     * @param prompt The user's input text
     * @param userMessageId The ID of the user message being sent
     * @param assistantMessageId The ID of the assistant message to populate
     * @param chatId The chat ID for loading conversation history
     * @param mode The execution mode (FAST, THINKING, or COUNCIL)
     * @return Flow of MessageGenerationState for UI updates
     */
    suspend fun generateChatResponse(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        mode: Mode,
        backgroundInferenceEnabled: Boolean,
    ) = generateChatResponseUseCase(prompt, userMessageId, assistantMessageId, chatId, mode, backgroundInferenceEnabled)

    /**
     * Gets the chat messages for the given chat ID.
     * @param chatId The chat ID to get messages for
     * @return Flow of messages for the chat
     */
    suspend fun getChat(chatId: ChatId): Flow<List<Message>> = getChatUseCase(chatId)
}
