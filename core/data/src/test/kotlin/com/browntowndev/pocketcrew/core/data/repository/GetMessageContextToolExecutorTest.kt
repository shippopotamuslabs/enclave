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
import kotlinx.serialization.json.boolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetMessageContextToolExecutorTest {

    private lateinit var loggingPort: LoggingPort
    private lateinit var messageRepository: MessageRepository
    private lateinit var executor: GetMessageContextToolExecutor

    @Before
    fun setup() {
        loggingPort = mockk(relaxed = true)
        messageRepository = mockk()
        executor = GetMessageContextToolExecutor(
            loggingPort = loggingPort,
            messageRepository = messageRepository,
        )
    }

    private fun baseRequest(
        argumentsJson: String = """{"message_id":"m3","before":5,"after":5}"""
    ) = ToolCallRequest(
        toolName = ToolDefinition.GET_MESSAGE_CONTEXT.name,
        argumentsJson = argumentsJson,
        provider = "OPENAI",
        modelType = ModelType.FAST,
        chatId = ChatId("chat-1"),
        userMessageId = null,
    )

    @Test
    fun `execute returns context messages around anchor`() = runTest {
        val anchorMessage = Message(
            id = MessageId("m3"),
            chatId = ChatId("c1"),
            content = Content(text = "anchor message"),
            role = Role.USER,
            createdAt = 1700000002L,
        )
        coEvery { messageRepository.getMessageById(MessageId("m3")) } returns anchorMessage
        coEvery { messageRepository.getMessagesAround(ChatId("c1"), 1700000002L, 5, 5) } returns listOf(
            Message(id = MessageId("m1"), chatId = ChatId("c1"), content = Content(text = "msg before 1"), role = Role.USER, createdAt = 1700000000L),
            Message(id = MessageId("m2"), chatId = ChatId("c1"), content = Content(text = "msg before 2"), role = Role.ASSISTANT, createdAt = 1700000001L),
            Message(id = MessageId("m4"), chatId = ChatId("c1"), content = Content(text = "msg after 1"), role = Role.ASSISTANT, createdAt = 1700000003L),
            Message(id = MessageId("m5"), chatId = ChatId("c1"), content = Content(text = "msg after 2"), role = Role.USER, createdAt = 1700000004L),
        )

        val result = executor.execute(baseRequest())

        assertEquals(ToolDefinition.GET_MESSAGE_CONTEXT.name, result.toolName)
        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals("m3", json["anchor_message_id"]!!.jsonPrimitive.content)
        assertEquals("c1", json["chat_id"]!!.jsonPrimitive.content)
        assertEquals(5, json["total_context_messages"]!!.jsonPrimitive.int) // 4 context + 1 anchor
        val messages = json["messages"]!!.jsonArray
        assertEquals(5, messages.size)
    }

    @Test
    fun `execute marks anchor message with is_anchor true`() = runTest {
        val anchorMessage = Message(
            id = MessageId("m3"),
            chatId = ChatId("c1"),
            content = Content(text = "anchor"),
            role = Role.USER,
            createdAt = 1700000002L,
        )
        coEvery { messageRepository.getMessageById(MessageId("m3")) } returns anchorMessage
        coEvery { messageRepository.getMessagesAround(ChatId("c1"), 1700000002L, 5, 5) } returns listOf(
            Message(id = MessageId("m2"), chatId = ChatId("c1"), content = Content(text = "before"), role = Role.USER, createdAt = 1700000001L),
            Message(id = MessageId("m4"), chatId = ChatId("c1"), content = Content(text = "after"), role = Role.ASSISTANT, createdAt = 1700000003L),
        )

        val result = executor.execute(baseRequest())
        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        val messages = json["messages"]!!.jsonArray

        val anchorMsg = messages.first { it.jsonObject["is_anchor"]!!.jsonPrimitive.boolean }.jsonObject
        assertEquals("m3", anchorMsg["message_id"]!!.jsonPrimitive.content)

        val nonAnchorMsg = messages.first { !it.jsonObject["is_anchor"]!!.jsonPrimitive.boolean }.jsonObject
        assertFalse(nonAnchorMsg["is_anchor"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `execute returns error when message not found`() = runTest {
        coEvery { messageRepository.getMessageById(MessageId("m99")) } returns null

        val result = executor.execute(baseRequest(argumentsJson = """{"message_id":"m99"}"""))
        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals("m99", json["anchor_message_id"]!!.jsonPrimitive.content)
        assertEquals("Message not found", json["error"]!!.jsonPrimitive.content)
    }

    @Test
    fun `execute returns error when message has no timestamp`() = runTest {
        coEvery { messageRepository.getMessageById(MessageId("m1")) } returns Message(
            id = MessageId("m1"),
            chatId = ChatId("c1"),
            content = Content(text = "no timestamp"),
            role = Role.USER,
            createdAt = null,
        )

        val result = executor.execute(baseRequest(argumentsJson = """{"message_id":"m1"}"""))
        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals("Message has no timestamp", json["error"]!!.jsonPrimitive.content)
    }

    @Test
    fun `execute uses default before and after when not specified`() = runTest {
        val anchorMessage = Message(
            id = MessageId("m1"),
            chatId = ChatId("c1"),
            content = Content(text = "anchor"),
            role = Role.USER,
            createdAt = 1700000000L,
        )
        coEvery { messageRepository.getMessageById(MessageId("m1")) } returns anchorMessage
        coEvery { messageRepository.getMessagesAround(ChatId("c1"), 1700000000L, 5, 5) } returns emptyList()

        executor.execute(baseRequest(argumentsJson = """{"message_id":"m1"}"""))

        coEvery { messageRepository.getMessagesAround(ChatId("c1"), 1700000000L, 5, 5) } returns emptyList()
    }

    @Test
    fun `execute throws when message_id is missing`() = runTest {
        var caught = false
        try {
            executor.execute(baseRequest(argumentsJson = """{}"""))
        } catch (e: Exception) {
            caught = true
        }
        assertTrue(caught)
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
    fun `execute truncates long message content`() = runTest {
        val longContent = "b".repeat(600)
        val anchorMessage = Message(
            id = MessageId("m1"),
            chatId = ChatId("c1"),
            content = Content(text = "anchor"),
            role = Role.USER,
            createdAt = 1700000000L,
        )
        coEvery { messageRepository.getMessageById(MessageId("m1")) } returns anchorMessage
        coEvery { messageRepository.getMessagesAround(ChatId("c1"), 1700000000L, 5, 5) } returns listOf(
            Message(id = MessageId("m2"), chatId = ChatId("c1"), content = Content(text = longContent), role = Role.ASSISTANT, createdAt = 1700000001L),
        )

        val result = executor.execute(baseRequest(argumentsJson = """{"message_id":"m1"}"""))
        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        val content = json["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(content.endsWith("..."))
        assertEquals(503, content.length)
    }

    @Test
    fun `execute clamps before and after to max`() = runTest {
        val anchorMessage = Message(
            id = MessageId("m1"),
            chatId = ChatId("c1"),
            content = Content(text = "anchor"),
            role = Role.USER,
            createdAt = 1700000000L,
        )
        coEvery { messageRepository.getMessageById(MessageId("m1")) } returns anchorMessage
        coEvery { messageRepository.getMessagesAround(ChatId("c1"), 1700000000L, 20, 20) } returns emptyList()

        executor.execute(baseRequest(argumentsJson = """{"message_id":"m1","before":100,"after":100}"""))
    }
}
