package com.browntowndev.pocketcrew.domain.usecase.chat

import kotlinx.coroutines.flow.Flow

interface ChatUseCases {
    val processPrompt: CreateUserMessageUseCase
    val generateChatResponseUseCase: GenerateChatResponseUseCase

    /**
     * Generates a chat response for the given prompt using streaming.
     * @param prompt The user's input text
     * @param messageId The ID of the assistant message to populate
     * @return Flow of MessageGenerationState for UI updates
     */
    fun generateChatResponse(prompt: String, messageId: String): Flow<MessageGenerationState> =
        generateChatResponseUseCase(prompt, messageId)
}
