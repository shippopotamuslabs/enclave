package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetMessageContextToolExecutorTest {
    private lateinit var messageRepository: FakeMessageRepository
    private lateinit var executor: GetMessageContextToolExecutor

    @BeforeEach
    fun setup() {
        messageRepository = FakeMessageRepository()
        executor =
            GetMessageContextToolExecutor(
                loggingPort = FakeLoggingPort(),
                messageRepository = messageRepository,
            )
    }

    private fun baseRequest(argumentsJson: String = """{"message_id":"m3","before":5,"after":5}""") =
        ToolCallRequest(
            toolName = ToolDefinition.GET_MESSAGE_CONTEXT.name,
            argumentsJson = argumentsJson,
            provider = "OPENAI",
            modelType = ModelType.FAST,
            chatId = ChatId("chat-1"),
            userMessageId = null,
        )

    private fun message(
        id: String,
        text: String,
        role: Role,
        createdAt: Long?,
    ) = Message(
        id = MessageId(id),
        chatId = ChatId("c1"),
        content = Content(text = text),
        role = role,
        createdAt = createdAt,
    )

    @Test
    fun `execute returns context messages around anchor`() =
        runTest {
            messageRepository.seed(
                listOf(
                    message("m1", "msg before 1", Role.USER, 1700000000L),
                    message("m2", "msg before 2", Role.ASSISTANT, 1700000001L),
                    message("m3", "anchor message", Role.USER, 1700000002L),
                    message("m4", "msg after 1", Role.ASSISTANT, 1700000003L),
                    message("m5", "msg after 2", Role.USER, 1700000004L),
                ),
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
    fun `execute marks anchor message with is_anchor true`() =
        runTest {
            messageRepository.seed(
                listOf(
                    message("m2", "before", Role.USER, 1700000001L),
                    message("m3", "anchor", Role.USER, 1700000002L),
                    message("m4", "after", Role.ASSISTANT, 1700000003L),
                ),
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
    fun `execute returns error when message not found`() =
        runTest {
            val result = executor.execute(baseRequest(argumentsJson = """{"message_id":"m99"}"""))
            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            assertEquals("m99", json["anchor_message_id"]!!.jsonPrimitive.content)
            assertEquals("Message not found", json["error"]!!.jsonPrimitive.content)
        }

    @Test
    fun `execute returns error when message has no timestamp`() =
        runTest {
            messageRepository.seed(
                listOf(
                    message("m1", "no timestamp", Role.USER, null),
                ),
            )

            val result = executor.execute(baseRequest(argumentsJson = """{"message_id":"m1"}"""))
            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            assertEquals("Message has no timestamp", json["error"]!!.jsonPrimitive.content)
        }

    @Test
    fun `execute uses default before and after when not specified`() =
        runTest {
            messageRepository.seed(
                listOf(
                    message("m1", "anchor", Role.USER, 1700000000L),
                ),
            )

            executor.execute(baseRequest(argumentsJson = """{"message_id":"m1"}"""))

            assertEquals(
                listOf(
                    FakeMessageRepository.MessagesAroundCall(
                        chatId = ChatId("c1"),
                        timestamp = 1700000000L,
                        before = 5,
                        after = 5,
                    ),
                ),
                messageRepository.getMessagesAroundCalls,
            )
        }

    @Test
    fun `execute throws when message_id is missing`() =
        runTest {
            var caught = false
            try {
                executor.execute(baseRequest(argumentsJson = """{}"""))
            } catch (e: Exception) {
                caught = true
            }
            assertTrue(caught)
        }

    @Test
    fun `execute throws on unsupported tool name`() =
        runTest {
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
    fun `execute truncates long message content`() =
        runTest {
            val longContent = "b".repeat(600)
            messageRepository.seed(
                listOf(
                    message("m1", "anchor", Role.USER, 1700000000L),
                    message("m2", longContent, Role.ASSISTANT, 1700000001L),
                ),
            )

            val result = executor.execute(baseRequest(argumentsJson = """{"message_id":"m1"}"""))
            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            val content =
                json["messages"]!!
                    .jsonArray[1]
                    .jsonObject["content"]!!
                    .jsonPrimitive.content
            assertTrue(content.endsWith("..."))
            assertEquals(503, content.length)
        }

    @Test
    fun `execute clamps before and after to max`() =
        runTest {
            messageRepository.seed(
                listOf(
                    message("m1", "anchor", Role.USER, 1700000000L),
                ),
            )

            executor.execute(baseRequest(argumentsJson = """{"message_id":"m1","before":100,"after":100}"""))

            assertEquals(
                listOf(
                    FakeMessageRepository.MessagesAroundCall(
                        chatId = ChatId("c1"),
                        timestamp = 1700000000L,
                        before = 20,
                        after = 20,
                    ),
                ),
                messageRepository.getMessagesAroundCalls,
            )
        }
}
