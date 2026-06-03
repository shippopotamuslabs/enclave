package com.browntowndev.enclave.domain.usecase

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatSummary
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.enclave.domain.model.chat.ResolvedImageTarget
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import org.junit.jupiter.api.Assertions

/**
 * Fake implementation of MessageRepository for testing.
 * Allows controlling message saving and verifying method calls.
 */
class FakeMessageRepository : MessageRepository {
    private val savedMessages = mutableListOf<Message>()
    private val softDeletedMessageIds = mutableSetOf<MessageId>()
    private var getMessageByIdResult: Message? = null
    private var getMessagesForChatResult: List<Message> = emptyList()
    private val messages = mutableMapOf<ChatId, MutableList<Message>>()
    private val summaries = mutableMapOf<ChatId, ChatSummary>()
    private val embeddings = mutableMapOf<MessageId, FloatArray>()
    private val visionAnalyses = mutableListOf<MessageVisionAnalysis>()
    private var nextMessageId = 1

    private var resolvedImageTarget: ResolvedImageTarget? = null

    // Methods to simulate errors
    var shouldThrowOnSaveMessage = false

    override suspend fun saveMessage(
        message: Message,
    ): MessageId {
        if (shouldThrowOnSaveMessage) throw RuntimeException("Simulated error on saveMessage")
        val persisted = message.copy(id = message.id.takeIf { it.value.isNotBlank() } ?: MessageId(nextMessageId++.toString()))
        savedMessages.add(persisted)
        message.chatId?.let { chatId ->
            messages.getOrPut(chatId) { mutableListOf() }.add(persisted)
        }
        return persisted.id
    }

    override suspend fun saveEmbedding(
        messageId: MessageId,
        embedding: FloatArray,
    ) {
        embeddings[messageId] = embedding
    }

    override suspend fun searchSimilarMessages(
        queryVector: FloatArray,
        limit: Int,
    ): List<MessageId> = emptyList()

    override suspend fun getMessageById(id: MessageId): Message? = getMessageByIdResult

    override suspend fun getMessagesForChat(chatId: ChatId): List<Message> = getMessagesForChatResult

    override suspend fun saveVisionAnalysis(
        userMessageId: MessageId,
        imageUri: String,
        promptText: String,
        analysisText: String,
        modelType: ModelType,
    ) {
        visionAnalyses.removeIf { it.userMessageId == userMessageId && it.imageUri == imageUri }
        visionAnalyses.add(
            MessageVisionAnalysis(
                id = "${userMessageId.value}:$imageUri",
                userMessageId = userMessageId,
                imageUri = imageUri,
                promptText = promptText,
                analysisText = analysisText,
                modelType = modelType,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
    }

    override suspend fun getVisionAnalysesForMessages(userMessageIds: List<MessageId>): Map<MessageId, List<MessageVisionAnalysis>> =
        visionAnalyses.filter { it.userMessageId in userMessageIds }.groupBy { it.userMessageId }

    override suspend fun resolveLatestImageBearingUserMessage(
        chatId: ChatId,
        currentUserMessageId: MessageId,
    ): ResolvedImageTarget? = resolvedImageTarget

    override suspend fun searchMessagesInChat(
        chatId: ChatId,
        queryVector: FloatArray,
        limit: Int,
    ): List<Message> = emptyList()

    override suspend fun searchMessagesAcrossChats(
        queryVector: FloatArray,
        limit: Int,
    ): List<Message> = emptyList()

    override suspend fun getMessagesAround(
        chatId: ChatId,
        timestamp: Long,
        before: Int,
        after: Int,
    ): List<Message> = emptyList()

    override suspend fun getChatSummary(chatId: ChatId): ChatSummary? = summaries[chatId]

    override suspend fun saveChatSummary(summary: ChatSummary) {
        // no-op for testing
    }

    override suspend fun deleteChatSummary(chatId: ChatId) {
        // no-op for testing
    }

    override suspend fun softDeleteMessage(messageId: MessageId) {
        softDeletedMessageIds.add(messageId)
        savedMessages.removeAll { it.id == messageId }
        messages.values.forEach { chatMessages ->
            chatMessages.removeAll { it.id == messageId }
        }
    }

    fun activeMessagesForChat(chatId: ChatId): List<Message> =
        messages[chatId]?.filter { it.id !in softDeletedMessageIds }.orEmpty()

    fun setMessagesForChat(messages: List<Message>) {
        getMessagesForChatResult = messages
    }

    fun getSavedMessages(): List<Message> = savedMessages.toList()

    fun setVisionAnalyses(analyses: List<MessageVisionAnalysis>) {
        visionAnalyses.clear()
        visionAnalyses.addAll(analyses)
    }

    fun verifySaveMessageCalled(times: Int) {
        Assertions.assertEquals(times, savedMessages.size)
    }

    fun verifyMessageSaved(message: Message) {
        Assertions.assertTrue(
            savedMessages.any { it.id == message.id && it.content == message.content && it.role == message.role },
            "Message was not saved: $message",
        )
    }

    fun setResolvedImageTarget(target: ResolvedImageTarget?) {
        resolvedImageTarget = target
    }

    fun reset() {
        savedMessages.clear()
        softDeletedMessageIds.clear()
        messages.clear()
        shouldThrowOnSaveMessage = false
        getMessageByIdResult = null
        getMessagesForChatResult = emptyList()
        visionAnalyses.clear()
        resolvedImageTarget = null
    }
}
