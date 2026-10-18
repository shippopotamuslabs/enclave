package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.chat.ThinkingData
import com.browntowndev.enclave.domain.model.chat.WebSearchSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID

/**
 * In-memory [ChatRepository] fake (ENC-28). Chats and messages live in StateFlows so
 * collectors see updates exactly as with the Room-backed implementation. Chat-level
 * operations (pin, delete, rename, search) carry real semantics; message-content
 * mutators update the stored message. Invocation lists are recorded for assertions.
 */
@Suppress("TooManyFunctions")
class FakeChatRepository(
    initialChats: List<Chat> = emptyList(),
) : ChatRepository {
    private val chatsState = MutableStateFlow(initialChats)
    private val messagesState = MutableStateFlow<List<Message>>(emptyList())

    /** When set, [getAllChats] returns a flow that fails with this error (E1-style tests). */
    var getAllChatsFailure: Throwable? = null

    /** When set, [deleteChat] throws this error instead of deleting. */
    var deleteFailure: Throwable? = null

    val togglePinCalls = mutableListOf<ChatId>()
    val deleteCalls = mutableListOf<ChatId>()
    val renameCalls = mutableListOf<Pair<ChatId, String>>()
    val searchQueries = mutableListOf<String>()

    fun seedChats(chats: List<Chat>) {
        chatsState.value = chats
    }

    fun setChats(chats: List<Chat>) {
        chatsState.value = chats
    }

    fun seedMessages(messages: List<Message>) {
        messagesState.value = messages
    }

    fun chats(): List<Chat> = chatsState.value

    fun messages(): List<Message> = messagesState.value

    override fun getAllChats(): Flow<List<Chat>> {
        val failure = getAllChatsFailure
        return if (failure != null) {
            flow { throw failure }
        } else {
            chatsState.asStateFlow()
        }
    }

    override suspend fun togglePinStatus(chatId: ChatId) {
        togglePinCalls += chatId
        val chat = chatsState.value.firstOrNull { it.id == chatId }
            ?: throw IllegalArgumentException("Chat not found")
        chatsState.value = chatsState.value.map {
            if (it.id == chatId) it.copy(pinned = !chat.pinned) else it
        }
    }

    /** Recorded getMessagesForChat chat ids (ENC-28). */
    val getMessagesForChatCalls = mutableListOf<ChatId>()

    override fun getMessagesForChat(chatId: ChatId): Flow<List<Message>> {
        getMessagesForChatCalls += chatId
        return messagesState.map { all -> all.filter { it.chatId == chatId } }
    }

    private fun updateMessage(messageId: MessageId, transform: (Message) -> Message) {
        messagesState.value = messagesState.value.map {
            if (it.id == messageId) transform(it) else it
        }
    }

    /** Recorded updateMessageState calls (ENC-28). */
    val updateMessageStateCalls = mutableListOf<Pair<MessageId, MessageState>>()

    override suspend fun updateMessageState(messageId: MessageId, messageState: MessageState) {
        updateMessageStateCalls += messageId to messageState
        updateMessage(messageId) { it.copy(messageState = messageState) }
    }

    /** Recorded updateMessageContent calls (ENC-28). */
    val updateMessageContentCalls = mutableListOf<Pair<MessageId, String>>()

    override suspend fun updateMessageContent(messageId: MessageId, content: String) {
        updateMessageContentCalls += messageId to content
        updateMessage(messageId) { it.copy(content = it.content.copy(text = content)) }
    }

    override suspend fun appendMessageContent(messageId: MessageId, content: String) {
        updateMessage(messageId) { it.copy(content = it.content.copy(text = it.content.text + content)) }
    }

    override suspend fun setThinkingStartTime(messageId: MessageId) {
        updateMessage(messageId) { it.copy(thinkingStartTime = System.currentTimeMillis()) }
    }

    override suspend fun setThinkingEndTime(messageId: MessageId) {
        updateMessage(messageId) { it.copy(thinkingEndTime = System.currentTimeMillis()) }
    }

    override suspend fun appendThinkingRaw(messageId: MessageId, thinkingText: String) {
        updateMessage(messageId) { it.copy(thinkingRaw = (it.thinkingRaw.orEmpty()) + thinkingText) }
    }

    override suspend fun clearThinking(messageId: MessageId) {
        updateMessage(messageId) {
            it.copy(thinkingRaw = null, thinkingStartTime = null, thinkingEndTime = null, thinkingDurationSeconds = null)
        }
    }

    override suspend fun updateMessageModelType(messageId: MessageId, modelType: ModelType) {
        updateMessage(messageId) { it.copy(modelType = modelType) }
    }

    override suspend fun createChat(chat: Chat, scheduleCloudSync: Boolean): ChatId {
        chatsState.value = chatsState.value + chat
        return chat.id
    }

    override suspend fun saveAssistantMessage(messageId: MessageId, content: String, thinkingData: ThinkingData?) {
        updateMessage(messageId) {
            it.copy(
                content = it.content.copy(text = content),
                thinkingRaw = thinkingData?.rawFullThought ?: it.thinkingRaw,
                messageState = MessageState.COMPLETE,
            )
        }
    }

    override suspend fun createAssistantMessage(
        chatId: ChatId,
        userMessageId: MessageId,
        modelType: ModelType,
        pipelineStep: PipelineStep?,
    ): MessageId {
        val id = MessageId(UUID.randomUUID().toString())
        messagesState.value = messagesState.value +
            Message(
                id = id,
                chatId = chatId,
                content = Content(text = "", pipelineStep = pipelineStep),
                role = Role.ASSISTANT,
                messageState = MessageState.PROCESSING,
                createdAt = System.currentTimeMillis(),
                modelType = modelType,
            )
        return id
    }

    override suspend fun persistWebSearchSources(messageId: MessageId, sources: List<WebSearchSource>) {
        updateMessage(messageId) {
            it.copy(content = it.content.copy(webSearchSources = sources), webSearchSources = sources)
        }
    }

    @Suppress("LongParameterList")
    /** Every persistAllMessageData call, recorded for assertions (ENC-28). */
    data class PersistedMessageData(
        val messageId: MessageId,
        val content: String,
        val messageState: MessageState,
    )

    val persistAllMessageDataCalls = mutableListOf<PersistedMessageData>()

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
        artifacts: List<com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest>,
        generatedMediaUris: List<String>,
        generatedMediaAssetIds: List<String>,
        generatedMediaTypes: List<String>,
        generatedMediaPrompts: List<String>,
        transcriptSegments: List<MessageTranscriptSegment>,
        telemetry: List<TurnTelemetry>,
    ) {
        persistAllMessageDataCalls += PersistedMessageData(messageId, content, messageState)
        updateMessage(messageId) {
            it.copy(
                content = Content(
                    text = content,
                    pipelineStep = pipelineStep,
                    webSearchSources = webSearchSources,
                    artifacts = artifacts,
                    generatedMediaUris = generatedMediaUris,
                    generatedMediaAssetIds = generatedMediaAssetIds,
                    generatedMediaTypes = generatedMediaTypes,
                    generatedMediaPrompts = generatedMediaPrompts,
                ),
                modelType = modelType,
                thinkingStartTime = thinkingStartTime,
                thinkingEndTime = thinkingEndTime,
                thinkingDurationSeconds = thinkingDuration?.toLong(),
                thinkingRaw = thinkingRaw,
                messageState = messageState,
                webSearchSources = webSearchSources,
            )
        }
    }

    override suspend fun updateArtifactExportPreference(artifactId: String, format: MimeType) = Unit

    override suspend fun getIncompleteCouncilMessages(chatId: ChatId): List<Message> =
        messagesState.value.filter { it.chatId == chatId && it.messageState == MessageState.PROCESSING }

    override suspend fun deleteChat(chatId: ChatId) {
        deleteFailure?.let { throw it }
        deleteCalls += chatId
        chatsState.value = chatsState.value.filterNot { it.id == chatId }
        messagesState.value = messagesState.value.filterNot { it.chatId == chatId }
    }

    override suspend fun deleteAllChats() {
        chatsState.value = emptyList()
        messagesState.value = emptyList()
    }

    override suspend fun renameChat(chatId: ChatId, newName: String) {
        renameCalls += chatId to newName
        chatsState.value = chatsState.value.map {
            if (it.id == chatId) it.copy(name = newName, lastModified = Date()) else it
        }
    }

    override fun searchChats(query: String, messageIds: List<MessageId>): Flow<List<Chat>> {
        searchQueries += query
        val matchingChatIds = messagesState.value
            .filter { it.id in messageIds }
            .mapNotNull { it.chatId }
            .toSet()
        return chatsState.map { chats ->
            chats.filter { chat ->
                chat.id in matchingChatIds || chat.name.contains(query, ignoreCase = true)
            }
        }
    }

    override suspend fun getChatsByIds(ids: List<ChatId>): Map<ChatId, Chat> =
        chatsState.value.filter { it.id in ids }.associateBy { it.id }

    override suspend fun markWebSearchSourcesExtracted(urls: List<String>) = Unit

    override suspend fun markWebSearchSourceExtracted(url: String) = Unit
}
