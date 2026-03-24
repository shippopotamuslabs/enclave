package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
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
     * @return Flow of MessageGenerationState for UI updates
     */
    fun executePipeline(
        chatId: String,
        userMessage: String,
    ): Flow<MessageGenerationState>

    /**
     * Stops the current inference pipeline.
     * This is a best-effort operation and may not stop immediately.
     *
     * @param pipelineId The unique identifier of the pipeline to stop
     */
    suspend fun stopPipeline(pipelineId: String)

    /**
     * Resumes an inference pipeline from a saved state.
     * Used after app restart to continue an interrupted CREW conversation.
     *
     * @param chatId The chat ID to resume inference for
     * @param pipelineId The unique identifier for this pipeline instance
     * @param onComplete Callback invoked when inference completes (success or failure)
     * @param onError Callback invoked on error conditions
     * @return A Flow emitting MessageGenerationState updates
     */
    suspend fun resumeFromState(
        chatId: String,
        pipelineId: String,
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ): Flow<MessageGenerationState>
}
