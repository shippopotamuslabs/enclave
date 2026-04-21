package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.Flow

/**
 * Port for single-model chat inference execution.
 * Implementation may run in-process or via a foreground service.
 */
interface ChatInferenceExecutorPort {
    /**
     * Execute single-model inference. Returns Flow of MessageGenerationState.
     * Implementation determines whether to run in a background service based on the flag.
     *
     * @param prompt The user's input text
     * @param userMessageId The ID of the user message being sent
     * @param assistantMessageId The ID of the assistant message to populate
     * @param chatId The chat ID for loading conversation history
     * @param userHasImage Whether an image is attached to the prompt
     * @param modelType The target engine type (FAST or THINKING)
     * @param backgroundInferenceEnabled Whether to use a persistent background service
     * @return Flow of MessageGenerationState for UI updates
     */
    fun execute(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        userHasImage: Boolean,
        modelType: ModelType,
        backgroundInferenceEnabled: Boolean,
    ): Flow<MessageGenerationState>

    /**
     * Stops any running inference associated with this port.
     */
    fun stop()
}
