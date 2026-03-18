package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.CrewPipelineStepEntity
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.ThinkingStepsEntity
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
     * Saves thinking steps for a message during generation.
     */
    override suspend fun saveThinkingSteps(messageId: Long, thinkingSteps: List<String>) {
        // Delete old thinking steps first
        messageDao.deleteThinkingStepsForMessage(messageId)

        // Upsert new thinking steps
        val stepsToUpsert = thinkingSteps.map { step ->
            ThinkingStepsEntity(
                messageId = messageId,
                thinkingChunk = step
            )
        }
        messageDao.upsertManyThinkingSteps(stepsToUpsert)

        // Update message state to THINKING
        messageDao.updateMessageState(messageId, MessageState.THINKING)
    }

    /**
     * Clears thinking steps from a message (for blocked/failed states).
     */
    override suspend fun clearThinkingSteps(messageId: Long) {
        messageDao.deleteThinkingStepsForMessage(messageId)
        messageDao.updateMessageState(messageId, MessageState.COMPLETE)
    }

    /**
     * Updates the model type used for a message.
     */
    override suspend fun updateMessageModelType(messageId: Long, modelType: ModelType) {
        messageDao.updateMessageModelType(messageId, modelType)
    }

    /**
     * Updates the thinking duration for a message.
     */
    override suspend fun updateThinkingDuration(messageId: Long, thinkingDurationSeconds: Int) {
        messageDao.updateThinkingDuration(messageId, thinkingDurationSeconds)
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
        messageDao.updateMessageContent(
            id = messageId,
            content = content,
            thinkingDuration = thinkingData?.thinkingDurationSeconds,
            thinkingRaw = thinkingData?.rawFullThought
        )
        // Save thinking steps to separate table
        if (thinkingData?.steps?.isNotEmpty() == true) {
            // Delete old thinking steps first
            messageDao.deleteThinkingStepsForMessage(messageId)
            // Insert new thinking steps
            val stepsToUpsert = thinkingData.steps.map { step ->
                ThinkingStepsEntity(
                    messageId = messageId,
                    thinkingChunk = step
                )
            }
            messageDao.upsertManyThinkingSteps(stepsToUpsert)
        }
    }
}
