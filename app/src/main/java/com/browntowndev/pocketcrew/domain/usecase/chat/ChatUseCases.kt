package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.presentation.screen.chat.Mode
import kotlinx.coroutines.flow.Flow

interface ChatUseCases {
    val processPrompt: CreateUserMessageUseCase
    val generateChatResponseUseCase: GenerateChatResponseUseCase

    /**
     * Generates a chat response for the given prompt using streaming.
     * @param prompt The user's input text
     * @param messageId The ID of the assistant message to populate
     * @param mode The execution mode (FAST, THINKING, or CREW)
     * @return Flow of MessageGenerationState for UI updates
     */
    fun generateChatResponse(prompt: String, messageId: String, mode: Mode): Flow<MessageGenerationState> =
        generateChatResponseUseCase(prompt, messageId, mode)
}
