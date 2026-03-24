package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.chat.Message
import kotlinx.coroutines.flow.Flow

interface ChatUseCases {
    /**
     * Creates the initial user prompt.
     */
    val processPromptUseCase: CreateUserMessageUseCase

    /**
     * Generates a chat response for the given prompt.
     */
    val generateChatResponseUseCase: GenerateChatResponseUseCase

    /**
     * Gets the chat messages for the given chat ID.
     */
    val getChatUseCase: GetChatUseCase

    /**
     * Merges database messages with in-flight messages.
     */
    val mergeMessagesUseCase: MergeMessagesUseCase

    /**
     * Creates the initial user prompt.
     * @param message The user's input text
     */
    suspend fun processPrompt(message: Message) = processPromptUseCase(message)

    /**
     * Generates a chat response for the given prompt using streaming.
     * @param prompt The user's input text
     * @param userMessageId The Long ID of the user message being sent
     * @param assistantMessageId The Long ID of the assistant message to populate
     * @param chatId The chat ID for loading conversation history
     * @param mode The execution mode (FAST, THINKING, or CREW)
     * @return Flow of MessageGenerationState for UI updates
     */
    suspend fun generateChatResponse(prompt: String, userMessageId: Long, assistantMessageId: Long, chatId: Long, mode: Mode) =
        generateChatResponseUseCase(prompt, userMessageId, assistantMessageId, chatId, mode)

    /**
     * Gets the chat messages for the given chat ID.
     * @param chatId The chat ID to get messages for
     * @return Flow of messages for the chat
     */
    suspend fun getChat(chatId: Long): Flow<List<Message>> = getChatUseCase(chatId)
}
