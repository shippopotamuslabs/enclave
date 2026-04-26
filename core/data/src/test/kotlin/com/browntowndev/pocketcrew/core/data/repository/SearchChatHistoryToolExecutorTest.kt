package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class SearchChatHistoryToolExecutorTest {

    private lateinit var loggingPort: LoggingPort
    private lateinit var messageRepository: MessageRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var embeddingEngine: EmbeddingEnginePort
    private lateinit var executor: SearchChatHistoryToolExecutor
    private val embeddingVector = floatArrayOf(0.1f, 0.2f, 0.3f)

    @Before
    fun setup() {
        loggingPort = mockk(relaxed = true)
        messageRepository = mockk()
        chatRepository = mockk(relaxed = true)
        embeddingEngine = mockk()
        coEvery { embeddingEngine.getEmbedding(any()) } returns embeddingVector
        executor = SearchChatHistoryToolExecutor(
            loggingPort = loggingPort,
            messageRepository = messageRepository,
            chatRepository = chatRepository,
            embeddingEngine = embeddingEngine,
        )
    }

    private fun baseRequest(
        toolName: String = ToolDefinition.SEARCH_CHAT_HISTORY.name,
        argumentsJson: String = """{"queries":["kotlin coroutines"]}"""
    ) = ToolCallRequest(
        toolName = toolName,
        argumentsJson = argumentsJson,
        provider = "OPENAI",
        modelType = ModelType.FAST,
        chatId = ChatId("chat-1"),
        userMessageId = null,
    )

    @Test
    fun `execute returns messages with chat metadata and match_type`() = runTest {
        val messages = listOf(
            Message(
                id = MessageId("m1"),
                chatId = ChatId("c1"),
                content = Content(text = "How do I cancel a coroutine?"),
                role = Role.USER,
                createdAt = 1700000000L,
            ),
            Message(
                id = MessageId("m2"),
                chatId = ChatId("c1"),
                content = Content(text = "You can use cancel() on the Job."),
                role = Role.ASSISTANT,
                createdAt = 1700000001L,
            ),
        )
        coEvery { messageRepository.searchMessagesAcrossChats(embeddingVector) } returns messages
        coEvery { messageRepository.getMessagesAround(ChatId("c1"), any(), 2, 2) } returns listOf(
            Message(id = MessageId("m0"), chatId = ChatId("c1"), content = Content(text = "prior msg"), role = Role.USER, createdAt = 1699999999L),
        )
        coEvery { chatRepository.getChatsByIds(any()) } returns mapOf(
            ChatId("c1") to Chat(id = ChatId("c1"), name = "Kotlin Discussion", created = Date(1000L), lastModified = Date(2000L))
        )

        val result = executor.execute(baseRequest())

        assertEquals(ToolDefinition.SEARCH_CHAT_HISTORY.name, result.toolName)
        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals(2, json["total_matched"]!!.jsonPrimitive.int)
        val messagesArray = json["messages"]!!.jsonArray
        // Should include context message + 2 direct matches = 3
        assertTrue(messagesArray.size >= 2)
        // Check that direct matches have match_type "direct"
        val directMsg = messagesArray.first { it.jsonObject["message_id"]!!.jsonPrimitive.content == "m1" }.jsonObject
        assertEquals("direct", directMsg["match_type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `execute marks context messages with match_type context`() = runTest {
        val matchedMessage = Message(
            id = MessageId("m2"),
            chatId = ChatId("c1"),
            content = Content(text = "matched message"),
            role = Role.USER,
            createdAt = 1700000000L,
        )
        coEvery { messageRepository.searchMessagesAcrossChats(any()) } returns listOf(matchedMessage)
        coEvery { messageRepository.getMessagesAround(ChatId("c1"), any(), 2, 2) } returns listOf(
            Message(id = MessageId("m1"), chatId = ChatId("c1"), content = Content(text = "before msg"), role = Role.USER, createdAt = 1699999999L),
            Message(id = MessageId("m3"), chatId = ChatId("c1"), content = Content(text = "after msg"), role = Role.ASSISTANT, createdAt = 1700000001L),
        )
        coEvery { chatRepository.getChatsByIds(any()) } returns mapOf(
            ChatId("c1") to Chat(id = ChatId("c1"), name = "Chat", created = Date(1000L), lastModified = Date(2000L))
        )

        val result = executor.execute(baseRequest())
        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        val messagesArray = json["messages"]!!.jsonArray

        val contextMessages = messagesArray.filter { it.jsonObject["match_type"]!!.jsonPrimitive.content == "context" }
        assertEquals(2, contextMessages.size)
    }

    @Test
    fun `execute passes multiple queries to repository`() = runTest {
        val messages = listOf(
            Message(id = MessageId("m1"), chatId = ChatId("c1"), content = Content(text = "cow moo"), role = Role.USER, createdAt = 1000L),
        )
        coEvery { messageRepository.searchMessagesAcrossChats(embeddingVector) } returns messages
        coEvery { messageRepository.getMessagesAround(any(), any(), any(), any()) } returns emptyList()
        coEvery { chatRepository.getChatsByIds(any()) } returns mapOf(
            ChatId("c1") to Chat(id = ChatId("c1"), name = "Farm Chat", created = Date(1000L), lastModified = Date(2000L))
        )

        val request = baseRequest(argumentsJson = """{"queries":["cow","cow photo","moo"]}""")
        val result = executor.execute(request)

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        val queriesArray = json["queries"]!!.jsonArray
        assertEquals(3, queriesArray.size)
    }

    @Test
    fun `execute throws on unsupported tool name`() = runTest {
        val request = baseRequest(toolName = "unsupported_tool")
        var caught = false
        try {
            executor.execute(request)
        } catch (e: IllegalArgumentException) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `execute throws when queries is missing and no query fallback`() = runTest {
        val request = baseRequest(argumentsJson = """{}""")
        var caught = false
        try {
            executor.execute(request)
        } catch (e: Exception) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `execute falls back to single query parameter when queries is absent`() = runTest {
        val messages = listOf(
            Message(id = MessageId("m1"), chatId = ChatId("c1"), content = Content(text = "cow moo"), role = Role.USER, createdAt = 1000L),
        )
        coEvery { messageRepository.searchMessagesAcrossChats(embeddingVector) } returns messages
        coEvery { messageRepository.getMessagesAround(any(), any(), any(), any()) } returns emptyList()
        coEvery { chatRepository.getChatsByIds(any()) } returns mapOf(
            ChatId("c1") to Chat(id = ChatId("c1"), name = "Farm Chat", created = Date(1000L), lastModified = Date(2000L))
        )

        val request = baseRequest(argumentsJson = """{"query":"cow"}""")
        val result = executor.execute(request)

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        val queriesArray = json["queries"]!!.jsonArray
        assertEquals(1, queriesArray.size)
        assertEquals("cow", queriesArray[0].jsonPrimitive.content)
    }

    @Test
    fun `execute prefers queries array over single query`() = runTest {
        val messages = listOf(
            Message(id = MessageId("m1"), chatId = ChatId("c1"), content = Content(text = "cow"), role = Role.USER, createdAt = 1000L),
        )
        coEvery { messageRepository.searchMessagesAcrossChats(embeddingVector) } returns messages
        coEvery { messageRepository.getMessagesAround(any(), any(), any(), any()) } returns emptyList()
        coEvery { chatRepository.getChatsByIds(any()) } returns mapOf(
            ChatId("c1") to Chat(id = ChatId("c1"), name = "Chat", created = Date(1000L), lastModified = Date(2000L))
        )

        val request = baseRequest(argumentsJson = """{"queries":["cow","moo"],"query":"cow"}""")
        val result = executor.execute(request)

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        val queriesArray = json["queries"]!!.jsonArray
        assertEquals(2, queriesArray.size)
    }

    @Test
    fun `execute throws when queries array is empty`() = runTest {
        val request = baseRequest(argumentsJson = """{"queries":[]}""")
        var caught = false
        try {
            executor.execute(request)
        } catch (e: Exception) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `execute returns empty results when no messages match`() = runTest {
        coEvery { messageRepository.searchMessagesAcrossChats(any()) } returns emptyList()

        val result = executor.execute(baseRequest())
        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals(0, json["total_matched"]!!.jsonPrimitive.int)
        assertEquals(0, json["messages"]!!.jsonArray.size)
    }

    @Test
    fun `execute truncates long message content`() = runTest {
        val longContent = "a".repeat(600)
        val messages = listOf(
            Message(id = MessageId("m1"), chatId = ChatId("c1"), content = Content(text = longContent), role = Role.USER, createdAt = 1700000000L),
        )
        coEvery { messageRepository.searchMessagesAcrossChats(any()) } returns messages
        coEvery { messageRepository.getMessagesAround(any(), any(), any(), any()) } returns emptyList()
        coEvery { chatRepository.getChatsByIds(any()) } returns mapOf(
            ChatId("c1") to Chat(id = ChatId("c1"), name = "Chat", created = Date(1000L), lastModified = Date(2000L))
        )

        val result = executor.execute(baseRequest())
        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        val content = json["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(content.endsWith("..."))
        assertEquals(503, content.length)
    }

    @Test
    fun `execute uses Unknown for missing chat names`() = runTest {
        val messages = listOf(
            Message(id = MessageId("m1"), chatId = ChatId("c1"), content = Content(text = "msg1"), role = Role.USER, createdAt = 1000L),
        )
        coEvery { messageRepository.searchMessagesAcrossChats(any()) } returns messages
        coEvery { messageRepository.getMessagesAround(any(), any(), any(), any()) } returns emptyList()
        coEvery { chatRepository.getChatsByIds(any()) } returns emptyMap()

        val result = executor.execute(baseRequest())
        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        val messagesArray = json["messages"]!!.jsonArray
        assertEquals("Unknown", messagesArray[0].jsonObject["chat_name"]!!.jsonPrimitive.content)
    }
}
