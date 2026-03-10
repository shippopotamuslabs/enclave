package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.usecase.chat.MessageGenerationState
import kotlinx.coroutines.flow.Flow

/**
 * Port interface for executing the Crew pipeline (multi-model inference pipeline).
 * This abstracts the WorkManager implementation from the domain layer.
 */
interface PipelineExecutorPort {

    /**
     * Executes the Crew pipeline for generating a chat response.
     * @param chatId Unique identifier for the chat session
     * @param userMessage The user's input text
     * @param assistantMessageId The ID of the assistant message to populate
     * @return Flow of MessageGenerationState for UI updates
     */
    fun executePipeline(
        chatId: String,
        userMessage: String,
        assistantMessageId: String
    ): Flow<MessageGenerationState>
}