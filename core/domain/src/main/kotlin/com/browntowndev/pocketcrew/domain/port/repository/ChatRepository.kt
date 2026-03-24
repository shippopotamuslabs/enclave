package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.model.MessageState
import kotlinx.coroutines.flow.Flow

/**
 * Port (interface) for chat persistence.
 * Implemented by the data layer.
 *
 * Note: This repository handles saving chats to the chat table.
 * Transaction management is delegated to the use case layer to keep
 * this interface agnostic of transaction lifecycle.
 */
interface ChatRepository {
    /**
     * Returns all chats as a Flow.
     * Listens to database changes.
     *
     * @return Flow of all chats sorted by pinned first, then by lastModified descending
     */
    fun getAllChats(): Flow<List<Chat>>

    /**
     * Toggles the pinned status of a chat.
     *
     * @param chatId The ID of the chat to toggle
     */
    suspend fun togglePinStatus(chatId: Long)

    /**
     * Returns all messages for a chat as a Flow.
     * Listens to database changes.
     *
     * @param chatId The ID of the chat
     * @return Flow of messages for the chat
     */
    fun getMessagesForChat(chatId: Long): Flow<List<Message>>

    /**
     * Updates the state of a message during streaming.
     *
     * @param messageId The ID of the message
     * @param messageState The new state of the message
     */
    suspend fun updateMessageState(messageId: Long, messageState: MessageState)

    /**
     * Updates the content of a message during streaming.
     *
     * @param messageId The ID of the message
     * @param content The new content
     */
    suspend fun updateMessageContent(messageId: Long, content: String)

    /**
     * Appends content to an existing message during streaming.
     *
     * @param messageId The ID of the message
     * @param content The content to append
     */
    suspend fun appendMessageContent(messageId: Long, content: String)

    /**
     * Sets the thinking start time for a message.
     * Called when thinking begins.
     *
     * @param messageId The ID of the message
     */
    suspend fun setThinkingStartTime(messageId: Long)

    /**
     * Sets the thinking end time for a message.
     * Called when thinking completes.
     *
     * @param messageId The ID of the message
     */
    suspend fun setThinkingEndTime(messageId: Long)

    /**
     * Appends raw thinking text to a message.
     * Used during streaming to accumulate thinking without chunking.
     *
     * @param messageId The ID of the message
     * @param thinkingText The text to append
     */
    suspend fun appendThinkingRaw(messageId: Long, thinkingText: String)

    /**
     * Clears thinking data from a message (for blocked/failed states).
     *
     * @param messageId The ID of the message
     */
    suspend fun clearThinking(messageId: Long)

    /**
     * Updates the model type used for a message.
     *
     * @param messageId The ID of the message
     * @param modelType The model type
     */
    suspend fun updateMessageModelType(messageId: Long, modelType: ModelType)

    /**
     * Creates a new chat in the database.
     *
     * @param chat The chat to create
     * @return The ID of the newly created chat
     */
    suspend fun createChat(chat: Chat): Long

    /**
     * Saves an assistant message to the database.
     *
     * @param messageId The ID of the message
     * @param content The content of the message
     * @param thinkingData Optional thinking data for reasoning models
     */
    suspend fun saveAssistantMessage(
        messageId: Long,
        content: String,
        thinkingData: ThinkingData? = null
    )

    /**
     * Creates a new assistant message for a new step in Crew mode.
     * The pipeline step is stored directly on the message entity.
     *
     * @param chatId The chat ID
     * @param userMessageId The user message ID this response is for
     * @param modelType The model type for this message
     * @param pipelineStep The pipeline step this message belongs to (for Crew mode)
     * @return The ID of the newly created message
     */
    suspend fun createAssistantMessage(chatId: Long, userMessageId: Long, modelType: ModelType, pipelineStep: PipelineStep? = null): Long

    /**
     * Persists all message data atomically in a single transaction.
     * Updates model type, thinking timestamps, thinking content, content, pipeline step, and state.
     * This is more efficient than calling individual update methods as it minimizes
     * database round trips and ensures atomicity.
     *
     * @param messageId The ID of the message to update
     * @param modelType The model type used
     * @param thinkingStartTime The thinking start timestamp (0 if not applicable)
     * @param thinkingEndTime The thinking end timestamp (0 if not applicable)
     * @param thinkingDuration The thinking duration in seconds (null if not applicable)
     * @param thinkingRaw The raw thinking content (null to clear)
     * @param content The final message content
     * @param messageState The final message state
     * @param pipelineStep The pipeline step (for CREW mode messages)
     */
    suspend fun persistAllMessageData(
        messageId: Long,
        modelType: ModelType,
        thinkingStartTime: Long,
        thinkingEndTime: Long,
        thinkingDuration: Int?,
        thinkingRaw: String?,
        content: String,
        messageState: MessageState,
        pipelineStep: PipelineStep?
    )

    /**
     * Returns messages that have incomplete state (PROCESSING, THINKING, or GENERATING)
     * for CREW pipeline resume. These messages need their pipeline state preserved
     * so the user can resume an interrupted CREW conversation.
     *
     * @param chatId The chat ID to get incomplete messages for
     * @return List of messages in incomplete states
     */
    suspend fun getIncompleteCrewMessages(chatId: Long): List<Message>
}
