package com.browntowndev.pocketcrew.core.data.repository
import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.TavilySourceDao
import com.browntowndev.pocketcrew.core.data.mapper.toDomain
import com.browntowndev.pocketcrew.core.data.mapper.toEntity
import com.browntowndev.pocketcrew.core.data.util.FtsSanitizer
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.chat.TavilySource
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map


@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val tavilySourceDao: TavilySourceDao,
) : ChatRepository {

    override fun getAllChats(): Flow<List<Chat>> {
        return chatDao.getAllChats().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun togglePinStatus(chatId: ChatId) {
        val rowsAffected = chatDao.updatePinStatus(chatId)
        if (rowsAffected == 0) {
            throw IllegalArgumentException("Chat not found with id: $chatId")
        }
    }

    override fun getMessagesForChat(chatId: ChatId): Flow<List<Message>> {
        // Combine the message table Flow with a tavily_source table Flow.
        // This ensures that source-level changes (e.g. extracted flag updates)
        // trigger a re-read, even though the message table itself hasn't changed.
        // Without this, markExtracted on tavily_source would not cause the
        // messages Flow to re-emit, and the UI would show stale extracted=false.
        return combine(
            messageDao.getMessagesByChatIdFlow(chatId),
            tavilySourceDao.getByChatIdFlow(chatId),
        ) { entities, sources ->
            val sourcesByMessage = sources.groupBy { it.messageId }
            entities.map { entity ->
                val messageSources = sourcesByMessage[entity.id].orEmpty()
                entity.toDomain(messageSources)
            }
        }
    }

    override suspend fun updateMessageState(messageId: MessageId, messageState: MessageState) {
        messageDao.updateMessageState(messageId, messageState)
        if (messageState == MessageState.COMPLETE) {
            messageDao.updateMessageCreatedAt(messageId, System.currentTimeMillis())
        }
    }

    override suspend fun updateMessageContent(messageId: MessageId, content: String) {
        messageDao.updateMessageContentText(messageId, content)
    }

    override suspend fun appendMessageContent(messageId: MessageId, content: String) {
        val existing = messageDao.getMessageById(messageId)
        if (existing != null) {
            val newContent = existing.content + content
            messageDao.updateMessageContentText(messageId, newContent)
        }
    }

    override suspend fun setThinkingStartTime(messageId: MessageId) {
        messageDao.updateThinkingStartTime(messageId, System.currentTimeMillis())
        messageDao.updateMessageState(messageId, MessageState.THINKING)
    }

    override suspend fun setThinkingEndTime(messageId: MessageId) {
        messageDao.updateThinkingEndTime(messageId, System.currentTimeMillis())
    }

    override suspend fun appendThinkingRaw(messageId: MessageId, thinkingText: String) {
        val existing = messageDao.getMessageById(messageId)
        val currentRaw = existing?.thinkingRaw ?: ""
        messageDao.updateThinkingRaw(messageId, currentRaw + thinkingText)
    }

    override suspend fun clearThinking(messageId: MessageId) {
        messageDao.updateThinkingRaw(messageId, null)
        messageDao.updateThinkingStartTime(messageId, 0)
        messageDao.updateThinkingEndTime(messageId, 0)
        messageDao.updateMessageState(messageId, MessageState.COMPLETE)
        messageDao.updateMessageCreatedAt(messageId, System.currentTimeMillis())
    }

    override suspend fun updateMessageModelType(messageId: MessageId, modelType: ModelType) {
        messageDao.updateMessageModelType(messageId, modelType)
    }

    override suspend fun createAssistantMessage(
        chatId: ChatId,
        userMessageId: MessageId,
        modelType: ModelType,
        pipelineStep: PipelineStep?
    ): MessageId {
        val entity = MessageEntity(
            id = MessageId(UUID.randomUUID().toString()),
            chatId = chatId,
            content = "",
            role = Role.ASSISTANT,
            userMessageId = userMessageId,
            messageState = MessageState.PROCESSING,
            modelType = modelType,
            pipelineStep = pipelineStep
        )
        messageDao.insert(entity)
        return entity.id
    }

    override suspend fun createChat(chat: Chat): ChatId {
        val entity = chat.toEntity()
        chatDao.insert(entity)
        return entity.id
    }

    override suspend fun saveAssistantMessage(
        messageId: MessageId,
        content: String,
        thinkingData: ThinkingData?
    ) {
        val duration = thinkingData?.thinkingDurationSeconds

        messageDao.updateMessageContent(
            id = messageId,
            content = content,
            thinkingDuration = duration?.toInt(),
            thinkingRaw = thinkingData?.rawFullThought
        )
    }

    /**
     * Persists all message data atomically in a single transaction.
     * Updates model type, thinking timestamps, thinking content, content, pipeline step, and state.
     */
    override suspend fun persistAllMessageData(
        messageId: MessageId,
        modelType: ModelType,
        thinkingStartTime: Long,
        thinkingEndTime: Long,
        thinkingDuration: Int?,
        thinkingRaw: String?,
        content: String,
        messageState: MessageState,
        pipelineStep: PipelineStep?,
        tavilySources: List<TavilySource>
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
            pipelineStep = pipelineStep,
            tavilySources = tavilySources.map { it.toEntity() }
        )
    }

    /**
     * Returns messages that have incomplete state for CREW pipeline resume.
     * Filters for PROCESSING, THINKING, or GENERATING state messages.
     */
    override suspend fun getIncompleteCrewMessages(chatId: ChatId): List<Message> {
        val incompleteStates = listOf(
            MessageState.PROCESSING,
            MessageState.THINKING,
            MessageState.GENERATING
        )
        val entities = messageDao.getMessagesByStates(chatId, incompleteStates)
        return entities.map { it.toDomain() }
    }

    override suspend fun deleteChat(chatId: ChatId) {
        val rowsAffected = chatDao.deleteById(chatId)
        if (rowsAffected == 0) {
            throw IllegalArgumentException("Chat not found with id: $chatId")
        }
    }

    override suspend fun renameChat(chatId: ChatId, newName: String) {
        val rowsAffected = chatDao.updateName(chatId, newName)
        if (rowsAffected == 0) {
            throw IllegalArgumentException("Chat not found with id: $chatId")
        }
    }

    override fun searchChats(query: String, messageIds: List<MessageId>): Flow<List<Chat>> {
        return chatDao.searchChats(query, messageIds).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getChatsByIds(ids: List<ChatId>): Map<ChatId, Chat> {
        if (ids.isEmpty()) return emptyMap()
        return chatDao.getChatsByIds(ids).associate { it.id to it.toDomain() }
    }

    override suspend fun markSourcesExtracted(urls: List<String>) {
        for (url in urls) {
            tavilySourceDao.markExtracted(url)
        }
    }
}
