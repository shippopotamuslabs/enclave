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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
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
        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals("chat-1", json["chat_id"]!!.jsonPrimitive.content)
        assertEquals("database migration", json["query"]!!.jsonPrimitive.content)
        assertEquals(2, json["total_results"]!!.jsonPrimitive.int)
        val msgsArray = json["messages"]!!.jsonArray
        assertEquals(2, msgsArray.size)
        assertEquals("USER", msgsArray[0].jsonObject["role"]!!.jsonPrimitive.content)
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        val msgContent = json["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(msgContent.endsWith("..."))
        assertTrue(msgContent.length <= 503) // 500 + "..."
    }

    @Test
    fun `execute returns empty results when no messages match`() = runTest {
        coEvery { messageRepository.searchMessagesInChat(any(), any()) } returns emptyList()

        val result = executor.execute(baseRequest())
        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals(0, json["total_results"]!!.jsonPrimitive.int)
        assertEquals(0, json["messages"]!!.jsonArray.size)
    }
}
