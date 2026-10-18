package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.domain.model.chat.Chat
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

/**
 * Translated from MockK to `:core:testing` fakes (ENC-28): the message
 * repository fake returns the canned cross-chat matches and around-context
 * through its override knobs, and the chat repository fake serves its
 * seeded chats.
 */
class SearchChatHistoryToolExecutorTest {
    private lateinit var messageRepository: FakeMessageRepository
    private lateinit var chatRepository: FakeChatRepository
    private lateinit var executor: SearchChatHistoryToolExecutor
    private val embeddingVector = floatArrayOf(0.1f, 0.2f, 0.3f)

    @BeforeEach
    fun setup() {
        messageRepository = FakeMessageRepository()
        chatRepository = FakeChatRepository()
        val embeddingEngine = FakeEmbeddingEngine()
        embeddingEngine.uniformEmbedding = embeddingVector
        executor =
            SearchChatHistoryToolExecutor(
                loggingPort = FakeLoggingPort(),
                messageRepository = messageRepository,
                chatRepository = chatRepository,
                embeddingEngine = embeddingEngine,
            )
    }

    private fun baseRequest(
        toolName: String = ToolDefinition.SEARCH_CHAT_HISTORY.name,
        argumentsJson: String = """{"queries":["kotlin coroutines"]}""",
    ) = ToolCallRequest(
        toolName = toolName,
        argumentsJson = argumentsJson,
        provider = "OPENAI",
        modelType = ModelType.FAST,
        chatId = ChatId("chat-1"),
        userMessageId = null,
    )

    private fun seedChat(
        id: String,
        name: String,
    ) {
        chatRepository.seedChats(
            listOf(Chat(id = ChatId(id), name = name, created = Date(1000L), lastModified = Date(2000L))),
        )
    }

    @Test
    fun `execute returns messages with chat metadata and match_type`() =
        runTest {
            val messages =
                listOf(
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
            messageRepository.searchAcrossChatsResults = messages
            messageRepository.messagesAroundResults =
                listOf(
                    Message(
                        id = MessageId("m0"),
                        chatId = ChatId("c1"),
                        content = Content(text = "prior msg"),
                        role = Role.USER,
                        createdAt = 1699999999L,
                    ),
                )
            seedChat("c1", "Kotlin Discussion")

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
    fun `execute marks context messages with match_type context`() =
        runTest {
            val matchedMessage =
                Message(
                    id = MessageId("m2"),
                    chatId = ChatId("c1"),
                    content = Content(text = "matched message"),
                    role = Role.USER,
                    createdAt = 1700000000L,
                )
            messageRepository.searchAcrossChatsResults = listOf(matchedMessage)
            messageRepository.messagesAroundResults =
                listOf(
                    Message(
                        id = MessageId("m1"),
                        chatId = ChatId("c1"),
                        content = Content(text = "before msg"),
                        role = Role.USER,
                        createdAt = 1699999999L,
                    ),
                    Message(
                        id = MessageId("m3"),
                        chatId = ChatId("c1"),
                        content = Content(text = "after msg"),
                        role = Role.ASSISTANT,
                        createdAt = 1700000001L,
                    ),
                )
            seedChat("c1", "Chat")

            val result = executor.execute(baseRequest())
            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            val messagesArray = json["messages"]!!.jsonArray

            val contextMessages = messagesArray.filter { it.jsonObject["match_type"]!!.jsonPrimitive.content == "context" }
            assertEquals(2, contextMessages.size)
        }

    @Test
    fun `execute passes multiple queries to repository`() =
        runTest {
            val messages =
                listOf(
                    Message(
                        id = MessageId("m1"),
                        chatId = ChatId("c1"),
                        content = Content(text = "cow moo"),
                        role = Role.USER,
                        createdAt = 1000L,
                    ),
                )
            messageRepository.searchAcrossChatsResults = messages
            messageRepository.messagesAroundResults = emptyList()
            seedChat("c1", "Farm Chat")

            val request = baseRequest(argumentsJson = """{"queries":["cow","cow photo","moo"]}""")
            val result = executor.execute(request)

            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            val queriesArray = json["queries"]!!.jsonArray
            assertEquals(3, queriesArray.size)
        }

    @Test
    fun `execute throws on unsupported tool name`() =
        runTest {
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
    fun `execute throws when queries is missing and no query fallback`() =
        runTest {
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
    fun `execute falls back to single query parameter when queries is absent`() =
        runTest {
            val messages =
                listOf(
                    Message(
                        id = MessageId("m1"),
                        chatId = ChatId("c1"),
                        content = Content(text = "cow moo"),
                        role = Role.USER,
                        createdAt = 1000L,
                    ),
                )
            messageRepository.searchAcrossChatsResults = messages
            messageRepository.messagesAroundResults = emptyList()
            seedChat("c1", "Farm Chat")

            val request = baseRequest(argumentsJson = """{"query":"cow"}""")
            val result = executor.execute(request)

            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            val queriesArray = json["queries"]!!.jsonArray
            assertEquals(1, queriesArray.size)
            assertEquals("cow", queriesArray[0].jsonPrimitive.content)
        }

    @Test
    fun `execute prefers queries array over single query`() =
        runTest {
            val messages =
                listOf(
                    Message(
                        id = MessageId("m1"),
                        chatId = ChatId("c1"),
                        content = Content(text = "cow"),
                        role = Role.USER,
                        createdAt = 1000L,
                    ),
                )
            messageRepository.searchAcrossChatsResults = messages
            messageRepository.messagesAroundResults = emptyList()
            seedChat("c1", "Chat")

            val request = baseRequest(argumentsJson = """{"queries":["cow","moo"],"query":"cow"}""")
            val result = executor.execute(request)

            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            val queriesArray = json["queries"]!!.jsonArray
            assertEquals(2, queriesArray.size)
        }

    @Test
    fun `execute throws when queries array is empty`() =
        runTest {
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
    fun `execute returns empty results when no messages match`() =
        runTest {
            messageRepository.searchAcrossChatsResults = emptyList()

            val result = executor.execute(baseRequest())
            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            assertEquals(0, json["total_matched"]!!.jsonPrimitive.int)
            assertEquals(0, json["messages"]!!.jsonArray.size)
        }

    @Test
    fun `execute truncates long message content`() =
        runTest {
            val longContent = "a".repeat(600)
            val messages =
                listOf(
                    Message(
                        id = MessageId("m1"),
                        chatId = ChatId("c1"),
                        content = Content(text = longContent),
                        role = Role.USER,
                        createdAt = 1700000000L,
                    ),
                )
            messageRepository.searchAcrossChatsResults = messages
            messageRepository.messagesAroundResults = emptyList()
            seedChat("c1", "Chat")

            val result = executor.execute(baseRequest())
            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            val content =
                json["messages"]!!
                    .jsonArray[0]
                    .jsonObject["content"]!!
                    .jsonPrimitive.content
            assertTrue(content.endsWith("..."))
            assertEquals(503, content.length)
        }

    @Test
    fun `execute uses Unknown for missing chat names`() =
        runTest {
            val messages =
                listOf(
                    Message(
                        id = MessageId("m1"),
                        chatId = ChatId("c1"),
                        content = Content(text = "msg1"),
                        role = Role.USER,
                        createdAt = 1000L,
                    ),
                )
            messageRepository.searchAcrossChatsResults = messages
            messageRepository.messagesAroundResults = emptyList()
            chatRepository.seedChats(emptyList())

            val result = executor.execute(baseRequest())
            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            val messagesArray = json["messages"]!!.jsonArray
            assertEquals("Unknown", messagesArray[0].jsonObject["chat_name"]!!.jsonPrimitive.content)
        }
}
