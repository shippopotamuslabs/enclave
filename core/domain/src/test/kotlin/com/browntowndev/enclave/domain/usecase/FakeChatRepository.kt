package com.browntowndev.enclave.domain.usecase
import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.enclave.domain.model.chat.ThinkingData
import com.browntowndev.enclave.domain.model.chat.WebSearchSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.jupiter.api.Assertions

/**
 * Fake implementation of ChatRepository for testing.
 * Allows controlling chat creation and verifying method calls.
 */
class FakeChatRepository : ChatRepository {
    private val createdChats = mutableListOf<Chat>()
    private var nextChatId = 1
    var shouldThrowOnCreateChat = false
    private val savedAssistantMessages = mutableListOf<Pair<MessageId, String>>()
    val persistedMessageData = mutableListOf<PersistedMessageData>()

    private val messagesFlows = mutableMapOf<ChatId, MutableStateFlow<List<Message>>>()

    // Track incomplete crew messages for testing
    private var incompleteCrewMessages: List<Message> = emptyList()

    // For getAllChats and togglePinStatus tests
    private val _chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
    private val pinnedChats = mutableMapOf<ChatId, Boolean>()

    fun setChats(chats: List<Chat>) {
        _chatsFlow.value = chats
        chats.forEach { pinnedChats[it.id] = it.pinned }
    }

    override fun getAllChats(): Flow<List<Chat>> = _chatsFlow

    override suspend fun togglePinStatus(chatId: ChatId) {
        val current = pinnedChats[chatId] ?: return
        pinnedChats[chatId] = !current
        // Update the flow with modified chat
        _chatsFlow.value =
            _chatsFlow.value.map { chat ->
                if (chat.id == chatId) chat.copy(pinned = !current) else chat
            }
    }

    fun setIncompleteCouncilMessages(messages: List<Message>) {
        incompleteCrewMessages = messages
    }

    override fun getMessagesForChat(chatId: ChatId): Flow<List<Message>> = messagesFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }

    fun setMessagesForChat(
        chatId: ChatId,
        messages: List<Message>,
    ) {
        messagesFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }.value = messages
    }

    override suspend fun updateMessageState(
        messageId: MessageId,
        messageState: MessageState,
    ) {
        // No-op for testing
    }

    override suspend fun updateMessageContent(
        messageId: MessageId,
        content: String,
    ) {
        // No-op for testing
    }

    override suspend fun appendMessageContent(
        messageId: MessageId,
        content: String,
    ) {
        // No-op for testing
    }

    override suspend fun setThinkingStartTime(messageId: MessageId) {
        // No-op for testing
    }

    override suspend fun setThinkingEndTime(messageId: MessageId) {
        // No-op for testing
    }

    override suspend fun appendThinkingRaw(
        messageId: MessageId,
        thinkingText: String,
    ) {
        // No-op for testing
    }

    override suspend fun clearThinking(messageId: MessageId) {
        // No-op for testing
    }

    override suspend fun updateMessageModelType(
        messageId: MessageId,
        modelType: ModelType,
    ) {
        // No-op for testing
    }

    override suspend fun createChat(chat: Chat): ChatId {
        if (shouldThrowOnCreateChat) {
            throw RuntimeException("Simulated error on createChat")
        }
        val id = ChatId((nextChatId++).toString())
        val chatWithId = chat.copy(id = id)
        createdChats.add(chatWithId)
        return id
    }

    override suspend fun saveAssistantMessage(
        messageId: MessageId,
        content: String,
        thinkingData: ThinkingData?,
    ) {
        savedAssistantMessages.add(messageId to content)
    }

    override suspend fun createAssistantMessage(
        chatId: ChatId,
        userMessageId: MessageId,
        modelType: ModelType,
        pipelineStep: PipelineStep?,
    ): MessageId {
        // Return a fake message ID
        return MessageId((nextChatId++).toString())
    }

    fun getCreatedChats(): List<Chat> = createdChats.toList()

    fun verifyChatCreated(times: Int) {
        Assertions.assertEquals(times, createdChats.size)
    }

    fun verifyChatName(expectedName: String) {
        Assertions.assertTrue(
            createdChats.any { it.name == expectedName },
            "No chat was created with name: $expectedName",
        )
    }

    fun reset() {
        createdChats.clear()
        nextChatId = 1
        shouldThrowOnCreateChat = false
        savedAssistantMessages.clear()
        persistedMessageData.clear()
        deletedChatIdStorage.clear()
        deleteChatFailure = null
    }

    /**
     * Returns messages that have incomplete state (PROCESSING, THINKING, or GENERATING)
     * for COUNCIL pipeline resume.
     * This is the new behavior being tested.
     */
    override suspend fun getIncompleteCouncilMessages(chatId: ChatId): List<Message> {
        val messages = messagesFlows[chatId]?.value ?: return emptyList()
        return messages.filter { message ->
            message.messageState == MessageState.PROCESSING ||
                message.messageState == MessageState.THINKING ||
                message.messageState == MessageState.GENERATING
        }
    }

    private val deletedChatIdStorage = mutableListOf<ChatId>()

    /** String IDs passed to [deleteChat], for use-case tests (see test_spec.md). */
    val deletedChatIds: Set<String>
        get() = deletedChatIdStorage.mapTo(mutableSetOf()) { it.value }

    /** When non-null, [deleteChat] throws this exception before mutating state. */
    var deleteChatFailure: RuntimeException? = null

    override suspend fun deleteChat(chatId: ChatId) {
        deleteChatFailure?.let { throw it }
        deletedChatIdStorage.add(chatId)
        _chatsFlow.value = _chatsFlow.value.filter { it.id != chatId }
    }

    override suspend fun deleteAllChats() {
        deleteChatFailure?.let { throw it }
        deletedChatIdStorage.addAll(_chatsFlow.value.map { it.id })
        _chatsFlow.value = emptyList()
        messagesFlows.clear()
    }

    override suspend fun renameChat(
        chatId: ChatId,
        newName: String,
    ) {
        _chatsFlow.value =
            _chatsFlow.value.map { chat ->
                if (chat.id == chatId) chat.copy(name = newName) else chat
            }
    }

    override fun searchChats(
        query: String,
        messageIds: List<MessageId>,
    ): Flow<List<Chat>> =
        _chatsFlow.map { list ->
            list.filter { it.name.contains(query, ignoreCase = true) }
        }

    override suspend fun getChatsByIds(ids: List<ChatId>): Map<ChatId, Chat> =
        _chatsFlow.value.filter { it.id in ids }.associateBy { it.id }

    override suspend fun persistWebSearchSources(
        messageId: MessageId,
        sources: List<WebSearchSource>,
    ) {
        if (sources.isEmpty()) return
        val latest =
            persistedMessageData.lastOrNull { it.messageId == messageId }
                ?: PersistedMessageData(
                    messageId = messageId,
                    modelType = ModelType.FAST,
                    thinkingStartTime = 0L,
                    thinkingEndTime = 0L,
                    thinkingDuration = null,
                    thinkingRaw = null,
                    content = "",
                    messageState = MessageState.PROCESSING,
                    pipelineStep = null,
                    webSearchSources = emptyList(),
                    artifacts = emptyList(),
                    generatedMediaUris = emptyList(),
                    generatedMediaAssetIds = emptyList(),
                    generatedMediaTypes = emptyList(),
                    generatedMediaPrompts = emptyList(),
                    transcriptSegments = emptyList(),
                )
        persistedMessageData.removeAll { it.messageId == messageId }
        persistedMessageData.add(latest.copy(webSearchSources = sources))
    }

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
        artifacts: List<ArtifactGenerationRequest>,
        generatedMediaUris: List<String>,
        generatedMediaAssetIds: List<String>,
        generatedMediaTypes: List<String>,
        generatedMediaPrompts: List<String>,
        transcriptSegments: List<MessageTranscriptSegment>,
    ) {
        persistedMessageData.add(
            PersistedMessageData(
                messageId = messageId,
                modelType = modelType,
                thinkingStartTime = thinkingStartTime,
                thinkingEndTime = thinkingEndTime,
                thinkingDuration = thinkingDuration,
                thinkingRaw = thinkingRaw,
                content = content,
                messageState = messageState,
                pipelineStep = pipelineStep,
                webSearchSources = webSearchSources,
                artifacts = artifacts,
                generatedMediaUris = generatedMediaUris,
                generatedMediaAssetIds = generatedMediaAssetIds,
                generatedMediaTypes = generatedMediaTypes,
                generatedMediaPrompts = generatedMediaPrompts,
                transcriptSegments = transcriptSegments,
            ),
        )
    }

    override suspend fun updateArtifactExportPreference(
        artifactId: String,
        format: MimeType,
    ) {
        // No-op for tests that do not assert on export preference
    }

    data class PersistedMessageData(
        val messageId: MessageId,
        val modelType: ModelType,
        val thinkingStartTime: Long,
        val thinkingEndTime: Long,
        val thinkingDuration: Int?,
        val thinkingRaw: String?,
        val content: String,
        val messageState: MessageState,
        val pipelineStep: PipelineStep?,
        val webSearchSources: List<WebSearchSource>,
        val artifacts: List<ArtifactGenerationRequest>,
        val generatedMediaUris: List<String> = emptyList(),
        val generatedMediaAssetIds: List<String> = emptyList(),
        val generatedMediaTypes: List<String> = emptyList(),
        val generatedMediaPrompts: List<String> = emptyList(),
        val transcriptSegments: List<com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment> = emptyList(),
    )

    override suspend fun markWebSearchSourcesExtracted(urls: List<String>) {
        // No-op for fake
    }

    override suspend fun markWebSearchSourceExtracted(url: String) {
        // No-op for fake
    }
}
