package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.usecase.chat.SearchChatsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class SearchChatHistoryToolExecutorTest {

    private lateinit var loggingPort: LoggingPort
    private lateinit var searchChatsUseCase: SearchChatsUseCase
    private lateinit var executor: SearchChatHistoryToolExecutor

    @Before
    fun setup() {
        loggingPort = mockk(relaxed = true)
        searchChatsUseCase = mockk()
        executor = SearchChatHistoryToolExecutor(
            loggingPort = loggingPort,
            searchChatsUseCase = searchChatsUseCase,
        )
    }

    private fun baseRequest(toolName: String = ToolDefinition.SEARCH_CHAT_HISTORY.name) = ToolCallRequest(
        toolName = toolName,
        argumentsJson = """{"query":"kotlin coroutines"}""",
        provider = "OPENAI",
        modelType = ModelType.FAST,
        chatId = ChatId("chat-1"),
        userMessageId = null,
    )

    @Test
    fun `execute returns chat history results`() = runTest {
        val chats = listOf(
            Chat(id = ChatId("c1"), name = "Kotlin Discussion", created = Date(1000L), lastModified = Date(2000L)),
            Chat(id = ChatId("c2"), name = "Coroutines Deep Dive", created = Date(3000L), lastModified = Date(4000L)),
        )
        coEvery { searchChatsUseCase("kotlin coroutines") } returns flowOf(chats)

        val result = executor.execute(baseRequest())

        assertEquals(ToolDefinition.SEARCH_CHAT_HISTORY.name, result.toolName)
        val json = JSONObject(result.resultJson)
        assertEquals("kotlin coroutines", json.getString("query"))
        assertEquals(2, json.getInt("total_results"))
        val chatsArray = json.getJSONArray("chats")
        assertEquals(2, chatsArray.length())
        assertEquals("c1", chatsArray.getJSONObject(0).getString("chat_id"))
        assertEquals("Kotlin Discussion", chatsArray.getJSONObject(0).getString("name"))
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
    fun `execute throws when query is missing`() = runTest {
        val request = baseRequest().copy(argumentsJson = """{}""")
        var caught = false
        try {
            executor.execute(request)
        } catch (e: IllegalArgumentException) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `execute limits results to MAX_RESULTS`() = runTest {
        val chats = (1..15).map { i ->
            Chat(id = ChatId("c$i"), name = "Chat $i", created = Date(1000L * i), lastModified = Date(2000L * i))
        }
        coEvery { searchChatsUseCase(any()) } returns flowOf(chats)

        val result = executor.execute(baseRequest())
        val json = JSONObject(result.resultJson)
        assertEquals(15, json.getInt("total_results"))
        assertEquals(10, json.getInt("returned_results"))
        assertEquals(10, json.getJSONArray("chats").length())
    }

    @Test
    fun `execute returns empty results when no chats match`() = runTest {
        coEvery { searchChatsUseCase(any()) } returns flowOf(emptyList())

        val result = executor.execute(baseRequest())
        val json = JSONObject(result.resultJson)
        assertEquals(0, json.getInt("total_results"))
        assertEquals(0, json.getJSONArray("chats").length())
    }
}
