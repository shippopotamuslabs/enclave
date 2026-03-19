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
     * Also creates a CrewPipelineStepEntity to associate the message with its pipeline step.
     *
     * @param chatId The chat ID
     * @param userMessageId The user message ID this response is for
     * @param modelType The model type for this message
     * @param pipelineStep The pipeline step this message belongs to (for Crew mode)
     * @return The ID of the newly created message
     */
    suspend fun createAssistantMessage(chatId: Long, userMessageId: Long, modelType: ModelType, pipelineStep: PipelineStep? = null): Long
}
