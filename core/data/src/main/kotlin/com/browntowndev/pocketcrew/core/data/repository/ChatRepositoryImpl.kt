package com.browntowndev.pocketcrew.core.data.repository
import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceDao
import com.browntowndev.pocketcrew.core.data.mapper.toDomain
import com.browntowndev.pocketcrew.core.data.mapper.toEntity
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.chat.WebSearchSource
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
import kotlinx.serialization.json.Json


@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val syncTrigger: CloudSyncTrigger,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val webSearchSourceDao: WebSearchSourceDao,
    private val messageFileDao: com.browntowndev.pocketcrew.core.data.local.MessageFileDao,
    private val transactionProvider: com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider,
) : ChatRepository {

    private fun triggerSync() = syncTrigger.trigger()

    override fun getAllChats(): Flow<List<Chat>> {
        return chatDao.getAllChats().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun togglePinStatus(chatId: ChatId) {
        val rowsAffected = chatDao.updatePinStatus(chatId)
        require(rowsAffected > 0) { "Chat not found with id: $chatId" }
        triggerSync()
    }

    override fun getMessagesForChat(chatId: ChatId): Flow<List<Message>> {
        // Combine the message table Flow with a web_search_source table Flow.
        // Without this, markExtracted on web_search_source would not cause the
        // Flow to emit, and the UI wouldn't know when a source was extracted.
        return combine(
            messageDao.getMessagesByChatIdFlow(chatId),
            webSearchSourceDao.getByChatIdFlow(chatId),
        ) { messages, webSearchSources ->
            val sourcesByMessageId = webSearchSources.groupBy { it.messageId }
            messages.map { it.toDomain(sourcesByMessageId[it.message.id] ?: emptyList()) }
        }
    }

    override suspend fun updateMessageState(messageId: MessageId, messageState: MessageState) {
        messageDao.updateMessageState(messageId, messageState)
        if (messageState == MessageState.COMPLETE) {
            messageDao.updateMessageCreatedAt(messageId, System.currentTimeMillis())
        }
        triggerSync()
    }

    override suspend fun updateMessageContent(messageId: MessageId, content: String) {
        messageDao.updateMessageContentText(messageId, content)
        triggerSync()
    }

    override suspend fun appendMessageContent(messageId: MessageId, content: String) {
        messageDao.appendMessageContentAtomic(messageId, content)
        triggerSync()
    }

    override suspend fun setThinkingStartTime(messageId: MessageId) {
        messageDao.updateThinkingStartTime(messageId, System.currentTimeMillis())
        messageDao.updateMessageState(messageId, MessageState.THINKING)
        triggerSync()
    }

    override suspend fun setThinkingEndTime(messageId: MessageId) {
        messageDao.updateThinkingEndTime(messageId, System.currentTimeMillis())
        triggerSync()
    }

    override suspend fun appendThinkingRaw(messageId: MessageId, thinkingText: String) {
        messageDao.appendThinkingRawAtomic(messageId, thinkingText)
        triggerSync()
    }

    override suspend fun clearThinking(messageId: MessageId) {
        messageDao.updateThinkingRaw(messageId, null)
        messageDao.updateThinkingStartTime(messageId, 0)
        messageDao.updateThinkingEndTime(messageId, 0)
        messageDao.updateMessageState(messageId, MessageState.COMPLETE)
        messageDao.updateMessageCreatedAt(messageId, System.currentTimeMillis())
        triggerSync()
    }

    override suspend fun updateMessageModelType(messageId: MessageId, modelType: ModelType) {
        messageDao.updateMessageModelType(messageId, modelType)
        triggerSync()
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
            pipelineStep = pipelineStep,
            pendingSync = 1
        )
        messageDao.upsert(entity)
        triggerSync()
        return entity.id
    }

    override suspend fun createChat(chat: Chat): ChatId {
        val entity = chat.toEntity()
        chatDao.upsert(entity)
        triggerSync()
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
        triggerSync()
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
        webSearchSources: List<WebSearchSource>,
        artifacts: List<com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest>,
        generatedMediaUris: List<String>,
        generatedMediaAssetIds: List<String>,
        generatedMediaTypes: List<String>,
        generatedMediaPrompts: List<String>
    ) {
        val artifactEntities = artifacts.map { artifact ->
            com.browntowndev.pocketcrew.core.data.local.MessageFileEntity(
                id = UUID.randomUUID().toString(),
                messageId = messageId.value,
                name = artifact.title,
                content = artifact.content,
                fileType = "ARTIFACT",
                syncStatus = "PENDING_UPLOAD",
                pendingSync = 0
            )
        }

        val mediaUrisJson = if (generatedMediaUris.isNotEmpty()) {
            Json.encodeToString(generatedMediaUris)
        } else null

        val mediaAssetIdsJson = if (generatedMediaAssetIds.isNotEmpty()) {
            Json.encodeToString(generatedMediaAssetIds)
        } else null

        val mediaTypesJson = if (generatedMediaTypes.isNotEmpty()) {
            Json.encodeToString(generatedMediaTypes)
        } else null

        val mediaPromptsJson = if (generatedMediaPrompts.isNotEmpty()) {
            Json.encodeToString(generatedMediaPrompts)
        } else null

        transactionProvider.runInTransaction {
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
                webSearchSources = webSearchSources.map { it.toEntity() },
                mediaUrisJson = mediaUrisJson,
                mediaAssetIdsJson = mediaAssetIdsJson,
                mediaTypesJson = mediaTypesJson,
                mediaPromptsJson = mediaPromptsJson
            )

            if (artifactEntities.isNotEmpty()) {
                messageFileDao.deleteArtifactsByMessageId(messageId.value)
                artifactEntities.forEach { messageFileDao.insert(it) }
            }
        }
        triggerSync()
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
        val rowsAffected = chatDao.softDeleteById(chatId)
        require(rowsAffected > 0) { "Chat not found with id: $chatId" }
        triggerSync()
    }

    override suspend fun renameChat(chatId: ChatId, newName: String) {
        val rowsAffected = chatDao.updateName(chatId, newName)
        require(rowsAffected > 0) { "Chat not found with id: $chatId" }
        triggerSync()
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

    override suspend fun markWebSearchSourceExtracted(url: String) {
        webSearchSourceDao.markExtracted(url)
        triggerSync()
    }

    override suspend fun markWebSearchSourcesExtracted(urls: List<String>) {
        urls.forEach { webSearchSourceDao.markExtracted(it) }
        triggerSync()
    }
}
