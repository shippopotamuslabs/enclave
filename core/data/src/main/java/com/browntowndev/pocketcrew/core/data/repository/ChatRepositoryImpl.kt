package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.CrewPipelineStepEntity
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.mapper.toDomain
import com.browntowndev.pocketcrew.core.data.mapper.toEntity
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room implementation of ChatRepository.
 *
 * This implementation handles saving chats to the chat table.
 * Transaction management is intentionally NOT handled here - it is
 * delegated to the use case layer via TransactionProvider.
 *
 * @param chatDao The DAO for chat operations
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) : ChatRepository {

    /**
     * Returns all messages for a chat as a Flow.
     */
    override fun getMessagesForChat(chatId: Long): Flow<List<Message>> {
        return messageDao.getMessagesWithAllRelations(chatId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Updates the state of a message during streaming.
     */
    override suspend fun updateMessageState(messageId: Long, messageState: MessageState) {
        messageDao.updateMessageState(messageId, messageState)
    }

    /**
     * Updates the content of a message during streaming.
     */
    override suspend fun updateMessageContent(messageId: Long, content: String) {
        messageDao.updateMessageContentText(messageId, content)
    }

    /**
     * Appends content to an existing message during streaming.
     */
    override suspend fun appendMessageContent(messageId: Long, content: String) {
        val existing = messageDao.getMessageById(messageId)
        if (existing != null) {
            val newContent = existing.content + content
            messageDao.updateMessageContentText(messageId, newContent)
        }
    }

    /**
     * Sets the thinking start time for a message.
     */
    override suspend fun setThinkingStartTime(messageId: Long) {
        messageDao.updateThinkingStartTime(messageId, System.currentTimeMillis())
        messageDao.updateMessageState(messageId, MessageState.THINKING)
    }

    /**
     * Sets the thinking end time for a message.
     */
    override suspend fun setThinkingEndTime(messageId: Long) {
        messageDao.updateThinkingEndTime(messageId, System.currentTimeMillis())
    }

    /**
     * Appends raw thinking text to a message.
     */
    override suspend fun appendThinkingRaw(messageId: Long, thinkingText: String) {
        val existing = messageDao.getMessageById(messageId)
        val currentRaw = existing?.thinkingRaw ?: ""
        messageDao.updateThinkingRaw(messageId, currentRaw + thinkingText)
    }

    /**
     * Clears thinking data from a message (for blocked/failed states).
     */
    override suspend fun clearThinking(messageId: Long) {
        messageDao.updateThinkingRaw(messageId, null)
        messageDao.updateThinkingStartTime(messageId, 0)
        messageDao.updateThinkingEndTime(messageId, 0)
        messageDao.updateMessageState(messageId, MessageState.COMPLETE)
    }

    /**
     * Updates the model type used for a message.
     */
    override suspend fun updateMessageModelType(messageId: Long, modelType: ModelType) {
        messageDao.updateMessageModelType(messageId, modelType)
    }

    /**
     * Creates a new assistant message for a new step in Crew mode.
     * Also creates a CrewPipelineStepEntity to associate the message with its pipeline step.
     */
    override suspend fun createAssistantMessage(chatId: Long, userMessageId: Long, modelType: ModelType, pipelineStep: PipelineStep?): Long {
        val entity = com.browntowndev.pocketcrew.core.data.local.MessageEntity(
            chatId = chatId,
            content = "",
            role = Role.ASSISTANT,
            userMessageId = userMessageId,
            messageState = MessageState.PROCESSING,
            modelType = modelType
        )
        val messageId = messageDao.insert(entity)

        // Create CrewPipelineStepEntity if pipelineStep is provided (Crew mode)
        if (pipelineStep != null) {
            messageDao.insertCrewPipelineStep(
                CrewPipelineStepEntity(
                    messageId = messageId,
                    pipelineStep = pipelineStep
                )
            )
        }

        return messageId
    }

    /**
     * Creates a new chat in the database.
     *
     * @param chat The chat to create
     * @return The ID of the newly created chat
     */
    override suspend fun createChat(chat: Chat): Long {
        val entity = chat.toEntity()
        return chatDao.insert(entity)
    }

    /**
     * Saves an assistant message to the database.
     *
     * @param messageId The ID of the message
     * @param content The content of the message
     * @param thinkingData Optional thinking data for reasoning models
     */
    override suspend fun saveAssistantMessage(
        messageId: Long,
        content: String,
        thinkingData: ThinkingData?
    ) {
        // Calculate duration if we have thinking data with start/end times
        val duration = thinkingData?.thinkingDurationSeconds?.toInt()
        
        messageDao.updateMessageContent(
            id = messageId,
            content = content,
            thinkingDuration = duration,
            thinkingRaw = thinkingData?.rawFullThought
        )
    }
}
