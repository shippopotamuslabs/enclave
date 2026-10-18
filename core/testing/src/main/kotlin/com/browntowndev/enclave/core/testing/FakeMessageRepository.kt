package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatSummary
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.enclave.domain.model.chat.ResolvedImageTarget
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [MessageRepository] fake (ENC-28). Vector search cannot rank by a raw
 * FloatArray meaningfully in a fake, so [searchSimilarMessages] returns the
 * test-controlled [similarMessageIds]; the text/vector plumbing above it (embedding
 * engine + use case) still runs for real.
 */
@Suppress("TooManyFunctions")
class FakeMessageRepository(
    initial: List<Message> = emptyList(),
) : MessageRepository {
    private val messagesState = MutableStateFlow(initial)
    private val summaries = mutableMapOf<ChatId, ChatSummary>()
    private val visionAnalyses = mutableMapOf<MessageId, List<MessageVisionAnalysis>>()

    /** Returned verbatim by [searchSimilarMessages]; set per test. */
    var similarMessageIds: List<MessageId> = emptyList()

    var searchSimilarCallCount = 0
        private set

    fun seed(messages: List<Message>) {
        messagesState.value = messages
    }

    fun messages(): List<Message> = messagesState.value

    override suspend fun saveMessage(message: Message): MessageId {
        messagesState.value = messagesState.value.filterNot { it.id == message.id } + message
        return message.id
    }

    override suspend fun saveEmbedding(messageId: MessageId, embedding: FloatArray) = Unit

    override suspend fun searchSimilarMessages(queryVector: FloatArray, limit: Int): List<MessageId> {
        searchSimilarCallCount++
        return similarMessageIds.take(limit)
    }

    /** When set, [getMessageById] throws this (ENC-28 failure injection). */
    var getMessageByIdFailure: Throwable? = null

    override suspend fun getMessageById(id: MessageId): Message? {
        getMessageByIdFailure?.let { throw it }
        return messagesState.value.firstOrNull { it.id == id }
    }

    /** When set, [getMessagesForChat] throws this (ENC-28 failure injection). */
    var getMessagesForChatFailure: Throwable? = null

    override suspend fun getMessagesForChat(chatId: ChatId): List<Message> {
        getMessagesForChatFailure?.let { throw it }
        return messagesState.value.filter { it.chatId == chatId }
    }

    override suspend fun saveVisionAnalysis(
        userMessageId: MessageId,
        imageUri: String,
        promptText: String,
        analysisText: String,
        modelType: ModelType,
    ) {
        val now = System.currentTimeMillis()
        val analysis = MessageVisionAnalysis(
            id = java.util.UUID.randomUUID().toString(),
            userMessageId = userMessageId,
            imageUri = imageUri,
            promptText = promptText,
            analysisText = analysisText,
            modelType = modelType,
            createdAt = now,
            updatedAt = now,
        )
        visionAnalyses[userMessageId] = (visionAnalyses[userMessageId] ?: emptyList()) + analysis
    }

    override suspend fun getVisionAnalysesForMessages(
        userMessageIds: List<MessageId>,
    ): Map<MessageId, List<MessageVisionAnalysis>> =
        userMessageIds.mapNotNull { id -> visionAnalyses[id]?.let { id to it } }.toMap()

    /** Value returned by [resolveLatestImageBearingUserMessage] (ENC-28). */
    var resolvedImageTarget: ResolvedImageTarget? = null

    override suspend fun resolveLatestImageBearingUserMessage(
        chatId: ChatId,
        currentUserMessageId: MessageId,
    ): ResolvedImageTarget? = resolvedImageTarget

    /** Recorded [searchMessagesInChat] invocation (ENC-28). */
    data class SearchInChatCall(
        val chatId: ChatId,
        val queryVector: FloatArray,
    )

    /** Every [searchMessagesInChat] invocation, in call order (ENC-28). */
    val searchMessagesInChatCalls = mutableListOf<SearchInChatCall>()

    override suspend fun searchMessagesInChat(
        chatId: ChatId,
        queryVector: FloatArray,
        limit: Int,
    ): List<Message> {
        searchMessagesInChatCalls += SearchInChatCall(chatId, queryVector)
        return messagesState.value.filter { it.chatId == chatId }.take(limit)
    }

    /** When non-null, returned verbatim by [searchMessagesAcrossChats] instead of the seeded state (ENC-28). */
    var searchAcrossChatsResults: List<Message>? = null

    /** When non-null, returned verbatim by [getMessagesAround] instead of the state-derived window (ENC-28). */
    var messagesAroundResults: List<Message>? = null

    override suspend fun searchMessagesAcrossChats(
        queryVector: FloatArray,
        limit: Int,
    ): List<Message> = searchAcrossChatsResults ?: messagesState.value.take(limit)

    /** Recorded [getMessagesAround] invocation (ENC-28, data batch). */
    data class MessagesAroundCall(
        val chatId: ChatId,
        val timestamp: Long,
        val before: Int,
        val after: Int,
    )

    /** Every [getMessagesAround] invocation, in call order (ENC-28, data batch). */
    val getMessagesAroundCalls = mutableListOf<MessagesAroundCall>()

    override suspend fun getMessagesAround(
        chatId: ChatId,
        timestamp: Long,
        before: Int,
        after: Int,
    ): List<Message> {
        getMessagesAroundCalls += MessagesAroundCall(chatId, timestamp, before, after)
        messagesAroundResults?.let { return it }
        val inChat = messagesState.value
            .filter { it.chatId == chatId }
            .sortedBy { it.createdAt ?: 0L }
        if (inChat.isEmpty()) return emptyList()
        val anchor = inChat.indexOfLast { (it.createdAt ?: 0L) <= timestamp }.coerceAtLeast(0)
        val from = (anchor - before).coerceAtLeast(0)
        val to = (anchor + after + 1).coerceAtMost(inChat.size)
        return inChat.subList(from, to)
    }

    override suspend fun getChatSummary(chatId: ChatId): ChatSummary? = summaries[chatId]

    override suspend fun saveChatSummary(summary: ChatSummary) {
        summaries[summary.chatId] = summary
    }

    override suspend fun deleteChatSummary(chatId: ChatId) {
        summaries.remove(chatId)
    }

    override suspend fun softDeleteMessage(messageId: MessageId) {
        messagesState.value = messagesState.value.filterNot { it.id == messageId }
    }
}
