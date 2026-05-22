package com.browntowndev.enclave.core.data.repository.message

import com.browntowndev.enclave.core.data.local.ChatSummaryDao
import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.mapper.message.toDomain
import com.browntowndev.enclave.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepair
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatSummary
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.ResolvedImageTarget
import com.browntowndev.enclave.domain.model.chat.Role
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageReadCoordinator
    @Inject
    constructor(
        private val messageDao: MessageDao,
        private val chatSummaryDao: ChatSummaryDao,
        private val encryption: MessageEncryptionCoordinator,
        private val persistence: MessagePersistenceCoordinator,
        private val healer: SensitivePersistenceSelfHealer,
        private val readRepairApplier: SensitiveRoomReadRepairApplier,
    ) {
        suspend fun getMessageById(id: MessageId): Message? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = messageDao.getMessageById(id)?.let { encryption.decryptMessageWithFiles(it, repairs) }?.toDomain()
            readRepairApplier.apply(repairs)
            return out
        }

        suspend fun getMessagesForChat(chatId: ChatId): List<Message> {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = messageDao.getMessagesByChatId(chatId).map { encryption.decryptMessageWithFiles(it, repairs).toDomain() }
            readRepairApplier.apply(repairs)
            return out
        }

        suspend fun resolveLatestImageBearingUserMessage(
            chatId: ChatId,
            currentUserMessageId: MessageId,
        ): ResolvedImageTarget? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val currentUserMessage =
                messageDao
                    .getMessageById(currentUserMessageId)
                    ?.let { encryption.decryptMessageWithFiles(it, repairs) }
                    ?.toDomain()
            if (currentUserMessage != null && currentUserMessage.content.imageUris.isNotEmpty()) {
                readRepairApplier.apply(repairs)
                return ResolvedImageTarget(
                    userMessageId = currentUserMessage.id,
                    imageUris = currentUserMessage.content.imageUris,
                )
            }
            val messages = messageDao.getMessagesByChatId(chatId).map { encryption.decryptMessageWithFiles(it, repairs).toDomain() }
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

        suspend fun searchMessagesInChat(
            chatId: ChatId,
            queryVector: FloatArray,
            limit: Int,
        ): List<Message> {
            val messageIds = persistence.searchSimilarMessages(queryVector, limit)
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = messageDao.getMessagesForChat(chatId, messageIds).map { encryption.decryptMessageWithFiles(it, repairs).toDomain() }
            readRepairApplier.apply(repairs)
            return out
        }

        suspend fun searchMessagesAcrossChats(
            queryVector: FloatArray,
            limit: Int,
        ): List<Message> {
            val messageIds = persistence.searchSimilarMessages(queryVector, limit)
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = messageDao.getMessages(messageIds).first().map { encryption.decryptMessageWithFiles(it, repairs).toDomain() }
            readRepairApplier.apply(repairs)
            return out
        }

        suspend fun getMessagesAround(
            chatId: ChatId,
            timestamp: Long,
            before: Int,
            after: Int,
        ): List<Message> {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val beforeMessages =
                messageDao.getMessagesBefore(chatId, timestamp, before).reversed().map {
                    encryption.decryptMessageWithFiles(it, repairs).toDomain()
                }
            val afterMessages =
                messageDao.getMessagesAfter(chatId, timestamp, after).map {
                    encryption.decryptMessageWithFiles(it, repairs).toDomain()
                }
            readRepairApplier.apply(repairs)
            return beforeMessages + afterMessages
        }

        suspend fun getChatSummary(chatId: ChatId): ChatSummary? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out =
                chatSummaryDao.getSummaryForChatSync(chatId)?.let {
                    SensitiveEntityFieldEncryptors.decrypt(it, healer, repairs).toDomain()
                }
            readRepairApplier.apply(repairs)
            return out
        }
    }

private fun com.browntowndev.enclave.core.data.local.ChatSummaryEntity.toDomain(): ChatSummary =
    ChatSummary(
        chatId = this.chatId,
        content = this.content,
        lastSummarizedMessageId = this.lastSummarizedMessageId,
    )
