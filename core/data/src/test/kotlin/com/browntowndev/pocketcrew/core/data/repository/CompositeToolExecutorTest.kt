package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

class CompositeToolExecutorTest {

    private lateinit var searchToolExecutor: SearchToolExecutorImpl
    private lateinit var imageInspectToolExecutor: ImageInspectToolExecutor
    private lateinit var eventBus: ToolExecutionEventBus
    private lateinit var compositeToolExecutor: CompositeToolExecutor

    @Before
    fun setup() {
        searchToolExecutor = mockk()
        imageInspectToolExecutor = mockk()
        eventBus = mockk(relaxed = true)
        compositeToolExecutor = CompositeToolExecutor(
            searchToolExecutor = searchToolExecutor,
            imageInspectToolExecutor = imageInspectToolExecutor,
            eventBus = eventBus
        )
    }

    @Test
    fun `execute emits started and finished events for successful execution`() = runTest {
        // Given
        val request = ToolCallRequest(
            chatId = ChatId("chat-123"),
            userMessageId = MessageId("msg-123"),
            toolName = ToolDefinition.TAVILY_WEB_SEARCH.name,
            argumentsJson = "{\"query\": \"test\"}",
            provider = "tavily",
            modelType = ModelType.FAST
        )
        val expectedResult = ToolExecutionResult(toolName = request.toolName, resultJson = "{\"status\": \"ok\"}")
        coEvery { searchToolExecutor.execute(request) } returns expectedResult

        // When
        compositeToolExecutor.execute(request)

        // Then
        coVerify(ordering = io.mockk.Ordering.SEQUENCE) {
            // Should emit Started (cast it to Started to access toolName)
            eventBus.emit(match { (it as? ToolExecutionEvent.Started)?.toolName == request.toolName })
            
            // Should execute tool
            searchToolExecutor.execute(request)
            
            // Should emit Finished
            eventBus.emit(match { it is ToolExecutionEvent.Finished && it.error == null })
        }
    }

    @Test
    fun `execute emits started and finished with error for failed execution`() = runTest {
        // Given
        val request = ToolCallRequest(
            chatId = ChatId("chat-123"),
            userMessageId = MessageId("msg-123"),
            toolName = ToolDefinition.TAVILY_WEB_SEARCH.name,
            argumentsJson = "{\"query\": \"test\"}",
            provider = "tavily",
            modelType = ModelType.FAST
        )
        val errorMessage = "API error"
        coEvery { searchToolExecutor.execute(request) } throws IOException(errorMessage)

        // When
        try {
            compositeToolExecutor.execute(request)
        } catch (e: Exception) {
            // Expected
        }

        // Then
        coVerify(ordering = io.mockk.Ordering.SEQUENCE) {
            // Should emit Started
            eventBus.emit(match { (it as? ToolExecutionEvent.Started)?.toolName == request.toolName })
            
            // Should execute tool
            searchToolExecutor.execute(request)
            
            // Should emit Finished with error
            eventBus.emit(match { (it as? ToolExecutionEvent.Finished)?.error == errorMessage })
        }
    }
}
