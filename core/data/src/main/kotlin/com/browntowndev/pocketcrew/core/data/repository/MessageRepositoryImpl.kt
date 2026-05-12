@file:Suppress("MaxLineLength", "MagicNumber", "UnusedPrivateProperty", "UnusedParameter", "TooManyFunctions", "ComplexCondition", "LargeClass", "CyclomaticComplexMethod", "SwallowedException", "InstanceOfCheckForException", "ThrowsCount", "NestedBlockDepth", "ReturnCount", "LoopWithTooManyJumpStatements", "FunctionNaming", "LongMethod", "MatchingDeclarationName", "UnusedPrivateMember", "LongParameterList")
package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ChatSummaryDao
import com.browntowndev.pocketcrew.core.data.local.ChatSummaryEntity
import com.browntowndev.pocketcrew.core.data.local.EmbeddingDao
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisEntity
import com.browntowndev.pocketcrew.core.data.mapper.toDomain
import com.browntowndev.pocketcrew.core.data.mapper.toEntity
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.ChatSummary
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.pocketcrew.domain.model.chat.ResolvedImageTarget
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import androidx.sqlite.db.SimpleSQLiteQuery
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room implementation of MessageRepository.
 *
 * This implementation handles saving messages to both the message table
 * and the FTS search index. Transaction management is intentionally
 * NOT handled here - it is delegated to the use case layer via
 * TransactionProvider to keep this class agnostic of transaction lifecycle.
 *
 * @param messageDao The DAO for message operations
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val syncTrigger: CloudSyncTrigger,
    private val messageDao: MessageDao,
    private val messageVisionAnalysisDao: MessageVisionAnalysisDao,
    private val chatSummaryDao: ChatSummaryDao,
    private val embeddingDao: EmbeddingDao,
    private val transactionProvider: TransactionProvider,
) : MessageRepository {

    private fun triggerSync() = syncTrigger.trigger()

    /**
     * Saves a message to the database.
     * Uses the DAO's transaction-capable method to ensure atomicity of
     * both the message and search index writes.
     *
     * Note: Transaction handling is delegated to the use case layer.
     * If multiple repository calls need to be atomic, the use case should
     * wrap them in runInTransaction.
     *
     * @param message The message to save
     * @return The ID of the saved message
     */
    override suspend fun saveMessage(message: Message): MessageId {
        require(message.chatId != null) { "Message(id=${message.id.value}) must have a chatId before persisting — route through CreateUserMessageUseCase" }
        
        val messageId = transactionProvider.runInTransaction {
            val entity = message.toEntity()
            messageDao.upsert(entity)

            if (message.content.fileAttachments.isNotEmpty()) {
                val fileEntities = message.content.fileAttachments.map { 
                    it.toEntity(id = UUID.randomUUID().toString(), messageId = entity.id.value)
                }
                messageDao.insertFiles(fileEntities)
            }

            entity.id
        }
        
        triggerSync()
        return messageId
    }

    override suspend fun saveEmbedding(messageId: MessageId, embedding: FloatArray) {
        val vectorBlob = floatArrayToByteArray(embedding)
        embeddingDao.replaceEmbedding(
            deleteQuery = SimpleSQLiteQuery(
                "DELETE FROM document_embeddings WHERE id = ?",
                arrayOf(messageId.value),
            ),
            insertQuery = SimpleSQLiteQuery(
                "INSERT INTO document_embeddings (id, vector) VALUES (?, ?)",
                arrayOf(messageId.value, vectorBlob),
            ),
        )
    }

    override suspend fun searchSimilarMessages(queryVector: FloatArray, limit: Int): List<MessageId> {
        val queryBlob = floatArrayToByteArray(queryVector)
        val sql = "SELECT id FROM document_embeddings WHERE vector MATCH ? ORDER BY distance LIMIT ?"
        return embeddingDao.searchSimilarMessages(SimpleSQLiteQuery(sql, arrayOf(queryBlob, limit)))
            .map { MessageId(it) }
    }

    private fun floatArrayToByteArray(floatArray: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floatArray.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floatArray) buffer.putFloat(f)
        return buffer.array()
    }

    /**
     * Retrieves a message by its ID.
     *
     * @param id The message ID
     * @return The message if found, null otherwise
     */
    override suspend fun getMessageById(id: MessageId): Message? {
        return messageDao.getMessageById(id)?.toDomain()
    }

    /**
     * Retrieves all messages for a specific chat, ordered by ID ascending.
     *
     * @param chatId The chat ID
     * @return List of messages in chronological order
     */
    override suspend fun getMessagesForChat(chatId: ChatId): List<Message> {
        return messageDao.getMessagesByChatId(chatId).map { it.toDomain() }
    }

    override suspend fun saveVisionAnalysis(
        userMessageId: MessageId,
        imageUri: String,
        promptText: String,
        analysisText: String,
        modelType: ModelType,
    ) {
        val now = System.currentTimeMillis()
        messageVisionAnalysisDao.upsert(
            MessageVisionAnalysisEntity(
                id = UUID.randomUUID().toString(),
                userMessageId = userMessageId,
                imageUri = imageUri,
                promptText = promptText,
                analysisText = analysisText,
                modelType = modelType,
                createdAt = now,
                updatedAt = now,
                pendingSync = 1,
            )
        )
        triggerSync()
    }

    override suspend fun getVisionAnalysesForMessages(
        userMessageIds: List<MessageId>
    ): Map<MessageId, List<MessageVisionAnalysis>> {
        if (userMessageIds.isEmpty()) return emptyMap()
        return messageVisionAnalysisDao.getByUserMessageIds(userMessageIds)
            .map { it.toDomain() }
            .groupBy { it.userMessageId }
    }

    override suspend fun resolveLatestImageBearingUserMessage(
        chatId: ChatId,
        currentUserMessageId: MessageId,
    ): ResolvedImageTarget? {
        val currentUserMessage = messageDao.getMessageById(currentUserMessageId)?.toDomain()
        if (currentUserMessage != null && currentUserMessage.content.imageUris.isNotEmpty()) {
            return ResolvedImageTarget(
                userMessageId = currentUserMessage.id,
                imageUris = currentUserMessage.content.imageUris,
            )
        }
        val messages = messageDao.getMessagesByChatId(chatId).map { it.toDomain() }
        val latestImageMessage = messages
            .filter { it.role == Role.USER && it.content.imageUris.isNotEmpty() && it.id != currentUserMessageId }
            .maxByOrNull { it.createdAt ?: 0L }
        return latestImageMessage?.let {
            ResolvedImageTarget(
                userMessageId = it.id,
                imageUris = it.content.imageUris,
            )
        }
    }

    override suspend fun searchMessagesInChat(chatId: ChatId, queryVector: FloatArray, limit: Int): List<Message> {
        val messageIds = searchSimilarMessages(queryVector, 100)
        return messageDao.getMessagesForChat(chatId, messageIds).map { it.toDomain() }
    }

    override suspend fun searchMessagesAcrossChats(queryVector: FloatArray, limit: Int): List<Message> {
        val messageIds = searchSimilarMessages(queryVector, limit)
        return messageDao.getMessages(messageIds).first().map { it.toDomain() }
    }

    override suspend fun getMessagesAround(chatId: ChatId, timestamp: Long, before: Int, after: Int): List<Message> {
        val beforeMessages = messageDao.getMessagesBefore(chatId, timestamp, before).reversed().map { it.toDomain() }
        val afterMessages = messageDao.getMessagesAfter(chatId, timestamp, after).map { it.toDomain() }
        return beforeMessages + afterMessages
    }

    override suspend fun getChatSummary(chatId: ChatId): ChatSummary? {
        return chatSummaryDao.getSummaryForChatSync(chatId)?.toDomain()
    }

    override suspend fun saveChatSummary(summary: ChatSummary) {
        chatSummaryDao.insertOrUpdateSummary(summary.toEntity())
    }

    override suspend fun deleteChatSummary(chatId: ChatId) {
        chatSummaryDao.deleteSummaryForChat(chatId)
    }
}

private fun ChatSummaryEntity.toDomain(): ChatSummary {
    return ChatSummary(
        chatId = this.chatId,
        content = this.content,
        lastSummarizedMessageId = this.lastSummarizedMessageId
    )
}

private fun ChatSummary.toEntity(): ChatSummaryEntity {
    return ChatSummaryEntity(
        chatId = this.chatId,
        content = this.content,
        lastSummarizedMessageId = this.lastSummarizedMessageId
    )
}
