package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ChatDao
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

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) : ChatRepository {

    override fun getAllChats(): Flow<List<Chat>> {
        throw NotImplementedError("getAllChats not yet implemented - TDD Red Phase")
    }

    override suspend fun togglePinStatus(chatId: Long) {
        throw NotImplementedError("togglePinStatus not yet implemented - TDD Red Phase")
    }

    override fun getMessagesForChat(chatId: Long): Flow<List<Message>> {
        return messageDao.getMessagesByChatIdFlow(chatId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun updateMessageState(messageId: Long, messageState: MessageState) {
        messageDao.updateMessageState(messageId, messageState)
    }

    override suspend fun updateMessageContent(messageId: Long, content: String) {
        messageDao.updateMessageContentText(messageId, content)
    }

    override suspend fun appendMessageContent(messageId: Long, content: String) {
        val existing = messageDao.getMessageById(messageId)
        if (existing != null) {
            val newContent = existing.content + content
            messageDao.updateMessageContentText(messageId, newContent)
        }
    }

    override suspend fun setThinkingStartTime(messageId: Long) {
        messageDao.updateThinkingStartTime(messageId, System.currentTimeMillis())
        messageDao.updateMessageState(messageId, MessageState.THINKING)
    }

    override suspend fun setThinkingEndTime(messageId: Long) {
        messageDao.updateThinkingEndTime(messageId, System.currentTimeMillis())
    }

    override suspend fun appendThinkingRaw(messageId: Long, thinkingText: String) {
        val existing = messageDao.getMessageById(messageId)
        val currentRaw = existing?.thinkingRaw ?: ""
        messageDao.updateThinkingRaw(messageId, currentRaw + thinkingText)
    }

    override suspend fun clearThinking(messageId: Long) {
        messageDao.updateThinkingRaw(messageId, null)
        messageDao.updateThinkingStartTime(messageId, 0)
        messageDao.updateThinkingEndTime(messageId, 0)
        messageDao.updateMessageState(messageId, MessageState.COMPLETE)
    }

    override suspend fun updateMessageModelType(messageId: Long, modelType: ModelType) {
        messageDao.updateMessageModelType(messageId, modelType)
    }

    override suspend fun createAssistantMessage(
        chatId: Long,
        userMessageId: Long,
        modelType: ModelType,
        pipelineStep: PipelineStep?
    ): Long {
        val entity = com.browntowndev.pocketcrew.core.data.local.MessageEntity(
            chatId = chatId,
            content = "",
            role = Role.ASSISTANT,
            userMessageId = userMessageId,
            messageState = MessageState.PROCESSING,
            modelType = modelType,
            pipelineStep = pipelineStep
        )
        return messageDao.insert(entity)
    }

    override suspend fun createChat(chat: Chat): Long {
        val entity = chat.toEntity()
        return chatDao.insert(entity)
    }

    override suspend fun saveAssistantMessage(
        messageId: Long,
        content: String,
        thinkingData: ThinkingData?
    ) {
        val duration = thinkingData?.thinkingDurationSeconds?.toInt()

        messageDao.updateMessageContent(
            id = messageId,
            content = content,
            thinkingDuration = duration,
            thinkingRaw = thinkingData?.rawFullThought
        )
    }

    /**
     * Persists all message data atomically in a single transaction.
     * Updates model type, thinking timestamps, thinking content, content, pipeline step, and state.
     */
    override suspend fun persistAllMessageData(
        messageId: Long,
        modelType: ModelType,
        thinkingStartTime: Long,
        thinkingEndTime: Long,
        thinkingDuration: Int?,
        thinkingRaw: String?,
        content: String,
        messageState: MessageState,
        pipelineStep: PipelineStep?
    ) {
        messageDao.persistAllMessageData(
            messageId = messageId,
            modelType = modelType,
            thinkingStartTime = thinkingStartTime,
            thinkingEndTime = thinkingEndTime,
            thinkingDuration = thinkingDuration,
            thinkingRaw = thinkingRaw,
            content = content,
            messageState = messageState,
            pipelineStep = pipelineStep
        )
    }

    /**
     * Returns messages that have incomplete state for CREW pipeline resume.
     * Filters for PROCESSING, THINKING, or GENERATING state messages.
     */
    override suspend fun getIncompleteCrewMessages(chatId: Long): List<Message> {
        val incompleteStates = listOf(
            MessageState.PROCESSING,
            MessageState.THINKING,
            MessageState.GENERATING
        )
        val entities = messageDao.getMessagesByStates(chatId, incompleteStates)
        return entities.map { it.toDomain() }
    }
}
