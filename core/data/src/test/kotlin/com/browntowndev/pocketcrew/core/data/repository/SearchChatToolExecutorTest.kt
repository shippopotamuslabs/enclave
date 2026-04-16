package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchChatToolExecutorTest {

    private lateinit var loggingPort: LoggingPort
    private lateinit var messageRepository: MessageRepository
    private lateinit var executor: SearchChatToolExecutor

    @Before
    fun setup() {
        loggingPort = mockk(relaxed = true)
        messageRepository = mockk()
        executor = SearchChatToolExecutor(
            loggingPort = loggingPort,
            messageRepository = messageRepository,
        )
    }

    private fun baseRequest(chatId: String = "chat-1") = ToolCallRequest(
        toolName = ToolDefinition.SEARCH_CHAT.name,
        argumentsJson = """{"chat_id":"$chatId","query":"database migration"}""",
        provider = "OPENAI",
        modelType = ModelType.FAST,
        chatId = ChatId(chatId),
        userMessageId = null,
    )

    @Test
    fun `execute returns messages matching query in specified chat`() = runTest {
        val messages = listOf(
            Message(id = MessageId("m1"), chatId = ChatId("chat-1"), content = Content(text = "We need a database migration"), role = Role.USER, createdAt = 1000L),
            Message(id = MessageId("m2"), chatId = ChatId("chat-1"), content = Content(text = "The migration script is ready"), role = Role.ASSISTANT, createdAt = 2000L),
        )
        coEvery { messageRepository.searchMessagesInChat(ChatId("chat-1"), "database migration") } returns messages

        val result = executor.execute(baseRequest())

        assertEquals(ToolDefinition.SEARCH_CHAT.name, result.toolName)
        val json = JSONObject(result.resultJson)
        assertEquals("chat-1", json.getString("chat_id"))
        assertEquals("database migration", json.getString("query"))
        assertEquals(2, json.getInt("total_results"))
        val msgsArray = json.getJSONArray("messages")
        assertEquals(2, msgsArray.length())
        assertEquals("user", msgsArray.getJSONObject(0).getString("role"))
    }

    @Test
    fun `execute throws on unsupported tool name`() = runTest {
        val request = baseRequest().copy(toolName = "unsupported_tool")
        var caught = false
        try {
            executor.execute(request)
        } catch (e: IllegalArgumentException) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `execute throws when query is missing`() = runTest {
        val request = baseRequest().copy(argumentsJson = """{"chat_id":"chat-1"}""")
        var caught = false
        try {
            executor.execute(request)
        } catch (e: IllegalArgumentException) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `execute throws when chat_id is missing`() = runTest {
        val request = baseRequest().copy(argumentsJson = """{"query":"test"}""")
        var caught = false
        try {
            executor.execute(request)
        } catch (e: IllegalArgumentException) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `execute truncates long message content`() = runTest {
        val longContent = "x".repeat(600)
        val messages = listOf(
            Message(id = MessageId("m1"), chatId = ChatId("chat-1"), content = Content(text = longContent), role = Role.USER, createdAt = 1000L),
        )
        coEvery { messageRepository.searchMessagesInChat(any(), any()) } returns messages

        val result = executor.execute(baseRequest())
        val json = JSONObject(result.resultJson)
        val msgContent = json.getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(msgContent.endsWith("..."))
        assertTrue(msgContent.length <= 503) // 500 + "..."
    }

    @Test
    fun `execute returns empty results when no messages match`() = runTest {
        coEvery { messageRepository.searchMessagesInChat(any(), any()) } returns emptyList()

        val result = executor.execute(baseRequest())
        val json = JSONObject(result.resultJson)
        assertEquals(0, json.getInt("total_results"))
        assertEquals(0, json.getJSONArray("messages").length())
    }
}
