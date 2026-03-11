package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.presentation.screen.chat.Mode
import kotlinx.coroutines.flow.Flow

interface ChatUseCases {
    val processPrompt: CreateUserMessageUseCase
    val generateChatResponseUseCase: GenerateChatResponseUseCase

    /**
     * Generates a chat response for the given prompt using streaming.
     * @param prompt The user's input text
     * @param userMessageId The Long ID of the user message being sent
     * @param assistantMessageId The Long ID of the assistant message to populate
     * @param chatId The chat ID for loading conversation history
     * @param mode The execution mode (FAST, THINKING, or CREW)
     * @return Flow of MessageGenerationState for UI updates
     */
    fun generateChatResponse(prompt: String, userMessageId: Long, assistantMessageId: Long, chatId: Long, mode: Mode): Flow<MessageGenerationState> =
        generateChatResponseUseCase(prompt, userMessageId, assistantMessageId, chatId, mode)
}
