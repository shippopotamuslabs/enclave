package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Constructor

class ConversationManagerImplToolsTest {

    private val context = mockk<Context>(relaxed = true)
    private val localModelRepository = mockk<LocalModelRepositoryPort>(relaxed = true)
    private val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)

    private lateinit var manager: ConversationManagerImpl

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        manager = ConversationManagerImpl(
            context = context,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `LocalSearchChatHistoryToolset splits multiple queries correctly`() = runTest {
        // Given
        val toolset = instantiateToolset(
            "LocalSearchChatHistoryToolset",
            ModelType.FAST,
            "chat-1",
            "msg-1"
        )

        val searchMethod = toolset.javaClass.declaredMethods.find { it.name == "search_chat_history" }!!
        searchMethod.isAccessible = true
        
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult("search_chat_history", "{\"results\":[]}")

        // When
        searchMethod.invoke(toolset, "query1, query2 ,  query3")

        // Then
        val requestSlot = slot<ToolCallRequest>()
        coVerify { toolExecutor.execute(capture(requestSlot)) }
        
        val request = requestSlot.captured
        assertTrue(request.argumentsJson.contains("query1"))
        assertTrue(request.argumentsJson.contains("query2"))
        assertTrue(request.argumentsJson.contains("query3"))
    }

    @Test
    fun `LocalExtractToolset parses URLs correctly`() = runTest {
        // Given
        val toolset = instantiateToolset(
            "LocalExtractToolset",
            ModelType.FAST,
            "chat-1",
            "msg-1"
        )

        val extractMethod = toolset.javaClass.declaredMethods.find { it.name == "tavily_extract" }!!
        extractMethod.isAccessible = true
        
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult("tavily_extract", "{\"results\":[]}")

        // When
        extractMethod.invoke(toolset, "[\"https://example.com\", \"https://google.com\"]", "advanced", "markdown")

        // Then
        val requestSlot = slot<ToolCallRequest>()
        coVerify { toolExecutor.execute(capture(requestSlot)) }
        
        val request = requestSlot.captured
        assertTrue(request.argumentsJson.contains("https://example.com"))
        assertTrue(request.argumentsJson.contains("https://google.com"))
        assertTrue(request.argumentsJson.contains("advanced"))
        assertTrue(request.argumentsJson.contains("markdown"))
    }

    @Test
    fun `executeToolSafely handles tool execution failure and returns error JSON`() = runTest {
        // Given
        coEvery { toolExecutor.execute(any()) } throws RuntimeException("Network error")
        
        val request = ToolCallRequest(
            toolName = "web_search",
            argumentsJson = "{\"query\":\"test\"}",
            provider = "LITERT",
            modelType = ModelType.FAST
        )

        // When
        val result = manager.executeToolSafelyForTest(request)

        // Then
        assertTrue(result.contains("tool_execution_failed"))
        assertTrue(result.contains("Network error"))
        verify { loggingPort.error(any(), any(), any()) }
    }

    private fun instantiateToolset(
        className: String,
        modelType: ModelType,
        chatId: String?,
        messageId: String?
    ): Any {
        val toolsetClass = manager.javaClass.getDeclaredClasses()
            .find { it.simpleName == className }!!
        
        // Find constructor with (Outer, ModelType, String, String, DefaultMarker)
        val constructor = toolsetClass.getDeclaredConstructors()
            .find { it.parameterCount == 5 }!!
        
        constructor.isAccessible = true
        return constructor.newInstance(manager, modelType, chatId, messageId, null)
    }
}
