@file:Suppress(
    "MaxLineLength",
    "MagicNumber",
    "UnusedPrivateProperty",
    "UnusedParameter",
    "TooManyFunctions",
    "ComplexCondition",
    "LargeClass",
    "CyclomaticComplexMethod",
    "SwallowedException",
    "InstanceOfCheckForException",
    "ThrowsCount",
    "NestedBlockDepth",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
    "FunctionNaming",
    "LongMethod",
    "MatchingDeclarationName",
    "UnusedPrivateMember",
    "LongParameterList",
)

package com.browntowndev.pocketcrew.core.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.browntowndev.pocketcrew.core.data.local.ChatSummaryDao
import com.browntowndev.pocketcrew.core.data.local.ChatSummaryEntity
import com.browntowndev.pocketcrew.core.data.local.EmbeddingDao
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisEntity
import com.browntowndev.pocketcrew.core.data.local.MessageWithFiles
import com.browntowndev.pocketcrew.core.data.mapper.toDomain
import com.browntowndev.pocketcrew.core.data.mapper.toEntity
import com.browntowndev.pocketcrew.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepair
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
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
class MessageRepositoryImpl
    @Inject
    constructor(
        private val syncTrigger: CloudSyncTrigger,
        private val messageDao: MessageDao,
        private val messageVisionAnalysisDao: MessageVisionAnalysisDao,
        private val chatSummaryDao: ChatSummaryDao,
        private val embeddingDao: EmbeddingDao,
        private val transactionProvider: TransactionProvider,
        private val healer: SensitivePersistenceSelfHealer,
        private val readRepairApplier: SensitiveRoomReadRepairApplier,
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
            require(message.chatId != null) {
                "Message(id=${message.id.value}) must have a chatId before persisting — route through CreateUserMessageUseCase"
            }

            val messageId =
                transactionProvider.runInTransaction {
                    val entity = SensitiveEntityFieldEncryptors.encrypt(message.toEntity(), healer)
                    messageDao.upsert(entity)

                    if (message.content.fileAttachments.isNotEmpty()) {
                        val fileEntities =
                            message.content.fileAttachments.map {
                                SensitiveEntityFieldEncryptors.encrypt(
                                    it.toEntity(id = UUID.randomUUID().toString(), messageId = entity.id.value),
                                    healer,
                                )
                            }
                        messageDao.insertFiles(fileEntities)
                    }

                    entity.id
                }

            triggerSync()
            return messageId
        }

        override suspend fun saveEmbedding(
            messageId: MessageId,
            embedding: FloatArray,
        ) {
            val vectorBlob = floatArrayToByteArray(embedding)
            embeddingDao.replaceEmbedding(
                deleteQuery =
                    SimpleSQLiteQuery(
                        "DELETE FROM document_embeddings WHERE id = ?",
                        arrayOf(messageId.value),
                    ),
                insertQuery =
                    SimpleSQLiteQuery(
                        "INSERT INTO document_embeddings (id, vector) VALUES (?, ?)",
                        arrayOf(messageId.value, vectorBlob),
                    ),
            )
        }

        override suspend fun searchSimilarMessages(
            queryVector: FloatArray,
            limit: Int,
        ): List<MessageId> {
            val queryBlob = floatArrayToByteArray(queryVector)
            val sql = "SELECT id FROM document_embeddings WHERE vector MATCH ? ORDER BY distance LIMIT ?"
            return embeddingDao
                .searchSimilarMessages(SimpleSQLiteQuery(sql, arrayOf(queryBlob, limit)))
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
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = messageDao.getMessageById(id)?.let { decryptMessageWithFiles(it, repairs) }?.toDomain()
            readRepairApplier.apply(repairs)
            return out
        }

        /**
         * Retrieves all messages for a specific chat, ordered by ID ascending.
         *
         * @param chatId The chat ID
         * @return List of messages in chronological order
         */
        override suspend fun getMessagesForChat(chatId: ChatId): List<Message> {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = messageDao.getMessagesByChatId(chatId).map { decryptMessageWithFiles(it, repairs).toDomain() }
            readRepairApplier.apply(repairs)
            return out
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
                SensitiveEntityFieldEncryptors.encrypt(
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
                    ),
                    healer,
                ),
            )
            triggerSync()
        }

        override suspend fun getVisionAnalysesForMessages(userMessageIds: List<MessageId>): Map<MessageId, List<MessageVisionAnalysis>> {
            if (userMessageIds.isEmpty()) return emptyMap()
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out =
                messageVisionAnalysisDao
                    .getByUserMessageIds(userMessageIds)
                    .map { SensitiveEntityFieldEncryptors.decrypt(it, healer, repairs).toDomain() }
                    .groupBy { it.userMessageId }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun resolveLatestImageBearingUserMessage(
            chatId: ChatId,
            currentUserMessageId: MessageId,
        ): ResolvedImageTarget? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val currentUserMessage =
                messageDao
                    .getMessageById(currentUserMessageId)
                    ?.let { decryptMessageWithFiles(it, repairs) }
                    ?.toDomain()
            if (currentUserMessage != null && currentUserMessage.content.imageUris.isNotEmpty()) {
                readRepairApplier.apply(repairs)
                return ResolvedImageTarget(
                    userMessageId = currentUserMessage.id,
                    imageUris = currentUserMessage.content.imageUris,
                )
            }
            val messages = messageDao.getMessagesByChatId(chatId).map { decryptMessageWithFiles(it, repairs).toDomain() }
            readRepairApplier.apply(repairs)
            val latestImageMessage =
                messages
                    .filter { it.role == Role.USER && it.content.imageUris.isNotEmpty() && it.id != currentUserMessageId }
                    .maxByOrNull { it.createdAt ?: 0L }
            return latestImageMessage?.let {
                ResolvedImageTarget(
                    userMessageId = it.id,
                    imageUris = it.content.imageUris,
                )
            }
        }

        override suspend fun searchMessagesInChat(
            chatId: ChatId,
            queryVector: FloatArray,
            limit: Int,
        ): List<Message> {
            val messageIds = searchSimilarMessages(queryVector, 100)
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = messageDao.getMessagesForChat(chatId, messageIds).map { decryptMessageWithFiles(it, repairs).toDomain() }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun searchMessagesAcrossChats(
            queryVector: FloatArray,
            limit: Int,
        ): List<Message> {
            val messageIds = searchSimilarMessages(queryVector, limit)
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = messageDao.getMessages(messageIds).first().map { decryptMessageWithFiles(it, repairs).toDomain() }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun getMessagesAround(
            chatId: ChatId,
            timestamp: Long,
            before: Int,
            after: Int,
        ): List<Message> {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val beforeMessages =
                messageDao.getMessagesBefore(chatId, timestamp, before).reversed().map {
                    decryptMessageWithFiles(it, repairs).toDomain()
                }
            val afterMessages =
                messageDao.getMessagesAfter(chatId, timestamp, after).map {
                    decryptMessageWithFiles(it, repairs).toDomain()
                }
            readRepairApplier.apply(repairs)
            return beforeMessages + afterMessages
        }

        override suspend fun getChatSummary(chatId: ChatId): ChatSummary? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out =
                chatSummaryDao.getSummaryForChatSync(chatId)?.let {
                    SensitiveEntityFieldEncryptors.decrypt(it, healer, repairs).toDomain()
                }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun saveChatSummary(summary: ChatSummary) {
            chatSummaryDao.insertOrUpdateSummary(SensitiveEntityFieldEncryptors.encrypt(summary.toEntity(), healer))
        }

        override suspend fun deleteChatSummary(chatId: ChatId) {
            chatSummaryDao.deleteSummaryForChat(chatId)
        }

        private fun decryptMessageWithFiles(
            row: MessageWithFiles,
            readRepairs: MutableList<SensitiveRoomReadRepair>?,
        ): MessageWithFiles =
            row.copy(
                message = SensitiveEntityFieldEncryptors.decrypt(row.message, healer, readRepairs),
                files = row.files.map { SensitiveEntityFieldEncryptors.decrypt(it, healer, readRepairs) },
            )
    }

private fun ChatSummaryEntity.toDomain(): ChatSummary =
    ChatSummary(
        chatId = this.chatId,
        content = this.content,
        lastSummarizedMessageId = this.lastSummarizedMessageId,
    )

private fun ChatSummary.toEntity(): ChatSummaryEntity =
    ChatSummaryEntity(
        chatId = this.chatId,
        content = this.content,
        lastSummarizedMessageId = this.lastSummarizedMessageId,
    )
