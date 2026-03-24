package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.inference.PipelineState

/**
 * Port interface for persisting and retrieving PipelineState.
 * Used for CREW pipeline resume after app death/restart.
 */
interface PipelineStateRepository {
    
    /**
     * Persists the current pipeline state for a chat.
     * Called periodically during pipeline execution to enable resume after app death.
     *
     * @param chatId Unique identifier for the chat session
     * @param state The current pipeline state to persist
     */
    suspend fun persistPipelineState(chatId: String, state: PipelineState)
    
    /**
     * Retrieves the saved pipeline state for a chat.
     * Returns null if no state is saved.
     *
     * @param chatId Unique identifier for the chat session
     * @return The saved pipeline state, or null if none exists
     */
    suspend fun getPipelineState(chatId: String): PipelineState?
    
    /**
     * Clears the saved pipeline state for a chat.
     * Called when pipeline completes successfully or is cancelled.
     *
     * @param chatId Unique identifier for the chat session
     */
    suspend fun clearPipelineState(chatId: String)
}
