package com.browntowndev.pocketcrew.domain.usecase
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
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
    private var nextChatId = 1L
    var shouldThrowOnCreateChat = false
    private val savedAssistantMessages = mutableListOf<Pair<Long, String>>()

    private val messagesFlows = mutableMapOf<Long, MutableStateFlow<List<Message>>>()
    
    // Track incomplete crew messages for testing
    private var incompleteCrewMessages: List<Message> = emptyList()

    // For getAllChats and togglePinStatus tests
    private val _chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
    private val pinnedChats = mutableMapOf<Long, Boolean>()
    
    fun setChats(chats: List<Chat>) {
        _chatsFlow.value = chats
        chats.forEach { pinnedChats[it.id] = it.pinned }
    }
    
    override fun getAllChats(): Flow<List<Chat>> = _chatsFlow
    
    override suspend fun togglePinStatus(chatId: Long) {
        val current = pinnedChats[chatId] ?: return
        pinnedChats[chatId] = !current
        // Update the flow with modified chat
        _chatsFlow.value = _chatsFlow.value.map { chat ->
            if (chat.id == chatId) chat.copy(pinned = !current) else chat
        }
    }
    
    fun setIncompleteCrewMessages(messages: List<Message>) {
        incompleteCrewMessages = messages
    }

    override fun getMessagesForChat(chatId: Long): Flow<List<Message>> {
        return messagesFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }
    }

    fun setMessagesForChat(chatId: Long, messages: List<Message>) {
        messagesFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }.value = messages
    }

    override suspend fun updateMessageState(messageId: Long, messageState: MessageState) {
        // No-op for testing
    }

    override suspend fun updateMessageContent(messageId: Long, content: String) {
        // No-op for testing
    }

    override suspend fun appendMessageContent(messageId: Long, content: String) {
        // No-op for testing
    }

    override suspend fun setThinkingStartTime(messageId: Long) {
        // No-op for testing
    }

    override suspend fun setThinkingEndTime(messageId: Long) {
        // No-op for testing
    }

    override suspend fun appendThinkingRaw(messageId: Long, thinkingText: String) {
        // No-op for testing
    }

    override suspend fun clearThinking(messageId: Long) {
        // No-op for testing
    }

    override suspend fun updateMessageModelType(messageId: Long, modelType: ModelType) {
        // No-op for testing
    }

    override suspend fun createChat(chat: Chat): Long {
        if (shouldThrowOnCreateChat) {
            throw RuntimeException("Simulated error on createChat")
        }
        val chatWithId = chat.copy(id = nextChatId)
        createdChats.add(chatWithId)
        return nextChatId++
    }

    override suspend fun saveAssistantMessage(
        messageId: Long,
        content: String,
        thinkingData: ThinkingData?
    ) {
        savedAssistantMessages.add(messageId to content)
    }

    override suspend fun createAssistantMessage(chatId: Long, userMessageId: Long, modelType: ModelType, pipelineStep: PipelineStep?): Long {
        // Return a fake message ID
        return nextChatId++
    }

    fun getCreatedChats(): List<Chat> = createdChats.toList()

    fun verifyChatCreated(times: Int) {
        Assertions.assertEquals(times, createdChats.size)
    }

    fun verifyChatName(expectedName: String) {
        Assertions.assertTrue(
            createdChats.any { it.name == expectedName },
            "No chat was created with name: $expectedName"
        )
    }

    fun reset() {
        createdChats.clear()
        nextChatId = 1L
        shouldThrowOnCreateChat = false
        savedAssistantMessages.clear()
    }
    
    /**
     * Returns messages that have incomplete state (PROCESSING, THINKING, or GENERATING)
     * for CREW pipeline resume.
     * This is the new behavior being tested.
     */
    override suspend fun getIncompleteCrewMessages(chatId: Long): List<Message> {
        val messages = messagesFlows[chatId]?.value ?: return emptyList()
        return messages.filter { message ->
            message.messageState == MessageState.PROCESSING ||
            message.messageState == MessageState.THINKING ||
            message.messageState == MessageState.GENERATING
        }
    }

    private val deletedChatIds = mutableListOf<Long>()

    override suspend fun deleteChat(chatId: Long) {
        deletedChatIds.add(chatId)
        _chatsFlow.value = _chatsFlow.value.filter { it.id != chatId }
    }

    override suspend fun renameChat(chatId: Long, newName: String) {
        _chatsFlow.value = _chatsFlow.value.map { chat ->
            if (chat.id == chatId) chat.copy(name = newName) else chat
        }
    }

    override fun searchChats(query: String, ftsQuery: String): Flow<List<Chat>> {
        return _chatsFlow.map { list ->
            list.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    override suspend fun persistAllMessageData(
        messageId: Long,
        modelType: ModelType,
        thinkingStartTime: Long,
        thinkingEndTime: Long,
        thinkingDuration: Int?,
        thinkingRaw: String?,
        content: String,
        messageState: MessageState,
        pipelineStep: PipelineStep?
    ) {
        // No-op for testing
    }
}
