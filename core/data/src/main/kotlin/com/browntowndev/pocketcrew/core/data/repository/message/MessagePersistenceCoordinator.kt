package com.browntowndev.pocketcrew.core.data.repository.message

import androidx.sqlite.db.SimpleSQLiteQuery
import com.browntowndev.pocketcrew.core.data.local.ChatSummaryDao
import com.browntowndev.pocketcrew.core.data.local.ChatSummaryEntity
import com.browntowndev.pocketcrew.core.data.local.EmbeddingDao
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.mapper.message.toEntity
import com.browntowndev.pocketcrew.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.ChatSummary
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagePersistenceCoordinator
    @Inject
    constructor(
        private val syncTrigger: CloudSyncTrigger,
        private val messageDao: MessageDao,
        private val embeddingDao: EmbeddingDao,
        private val chatSummaryDao: ChatSummaryDao,
        private val transactionProvider: TransactionProvider,
        private val healer: SensitivePersistenceSelfHealer,
    ) {
        suspend fun saveMessage(message: Message): MessageId {
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

            syncTrigger.trigger()
            return messageId
        }

        suspend fun saveEmbedding(
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

        suspend fun searchSimilarMessages(
            queryVector: FloatArray,
            limit: Int,
        ): List<MessageId> {
            val queryBlob = floatArrayToByteArray(queryVector)
            val sql = "SELECT id FROM document_embeddings WHERE vector MATCH ? ORDER BY distance LIMIT ?"
            return embeddingDao
                .searchSimilarMessages(SimpleSQLiteQuery(sql, arrayOf(queryBlob, limit)))
                .map { MessageId(it) }
        }

        suspend fun saveChatSummary(summary: ChatSummary) {
            chatSummaryDao.insertOrUpdateSummary(SensitiveEntityFieldEncryptors.encrypt(summary.toChatSummaryEntity(), healer))
        }

        suspend fun deleteChatSummary(chatId: ChatId) {
            chatSummaryDao.deleteSummaryForChat(chatId)
        }

        private fun floatArrayToByteArray(floatArray: FloatArray): ByteArray {
            val buffer = ByteBuffer.allocate(floatArray.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (f in floatArray) buffer.putFloat(f)
            return buffer.array()
        }
    }

private fun ChatSummary.toChatSummaryEntity(): ChatSummaryEntity =
    ChatSummaryEntity(
        chatId = this.chatId,
        content = this.content,
        lastSummarizedMessageId = this.lastSummarizedMessageId,
    )
