package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.repository.message.MessagePersistenceCoordinator
import com.browntowndev.enclave.core.data.repository.message.MessageReadCoordinator
import com.browntowndev.enclave.core.data.repository.message.MessageVisionWriter
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatSummary
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.enclave.domain.model.chat.ResolvedImageTarget
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room implementation of MessageRepository.
 *
 * Transaction management is delegated to the use case layer via TransactionProvider.
 */
@Singleton
class MessageRepositoryImpl
    @Inject
    constructor(
        private val persistence: MessagePersistenceCoordinator,
        private val reads: MessageReadCoordinator,
        private val vision: MessageVisionWriter,
    ) : MessageRepository {
        override suspend fun saveMessage(message: Message): MessageId = persistence.saveMessage(message)

        override suspend fun saveEmbedding(
            messageId: MessageId,
            embedding: FloatArray,
        ) = persistence.saveEmbedding(messageId, embedding)

        override suspend fun searchSimilarMessages(
            queryVector: FloatArray,
            limit: Int,
        ): List<MessageId> = persistence.searchSimilarMessages(queryVector, limit)

        override suspend fun getMessageById(id: MessageId): Message? = reads.getMessageById(id)

        override suspend fun getMessagesForChat(chatId: ChatId): List<Message> = reads.getMessagesForChat(chatId)

        override suspend fun saveVisionAnalysis(
            userMessageId: MessageId,
            imageUri: String,
            promptText: String,
            analysisText: String,
            modelType: ModelType,
        ) = vision.saveVisionAnalysis(userMessageId, imageUri, promptText, analysisText, modelType)

        override suspend fun getVisionAnalysesForMessages(
            userMessageIds: List<MessageId>,
        ): Map<MessageId, List<MessageVisionAnalysis>> = vision.getVisionAnalysesForMessages(userMessageIds)

        override suspend fun resolveLatestImageBearingUserMessage(
            chatId: ChatId,
            currentUserMessageId: MessageId,
        ): ResolvedImageTarget? = reads.resolveLatestImageBearingUserMessage(chatId, currentUserMessageId)

        override suspend fun searchMessagesInChat(
            chatId: ChatId,
            queryVector: FloatArray,
            limit: Int,
        ): List<Message> = reads.searchMessagesInChat(chatId, queryVector, limit)

        override suspend fun searchMessagesAcrossChats(
            queryVector: FloatArray,
            limit: Int,
        ): List<Message> = reads.searchMessagesAcrossChats(queryVector, limit)

        override suspend fun getMessagesAround(
            chatId: ChatId,
            timestamp: Long,
            before: Int,
            after: Int,
        ): List<Message> = reads.getMessagesAround(chatId, timestamp, before, after)

        override suspend fun getChatSummary(chatId: ChatId): ChatSummary? = reads.getChatSummary(chatId)

        override suspend fun saveChatSummary(summary: ChatSummary) = persistence.saveChatSummary(summary)

        override suspend fun deleteChatSummary(chatId: ChatId) = persistence.deleteChatSummary(chatId)
    }
