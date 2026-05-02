package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.core.data.artifact.ArtifactToolExecutor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class CompositeToolExecutorTest {

    private lateinit var searchToolExecutor: SearchToolExecutorImpl
    private lateinit var imageInspectToolExecutor: ImageInspectToolExecutor
    private lateinit var extractToolExecutor: ExtractToolExecutorImpl
    private lateinit var searchChatHistoryToolExecutor: SearchChatHistoryToolExecutor
    private lateinit var searchChatToolExecutor: SearchChatToolExecutor
    private lateinit var getMessageContextToolExecutor: GetMessageContextToolExecutor
    private lateinit var manageMemoriesToolExecutor: ManageMemoriesToolExecutor
    private lateinit var artifactToolExecutor: ArtifactToolExecutor
    private lateinit var eventBus: ToolExecutionEventBus
    private lateinit var compositeToolExecutor: CompositeToolExecutor

    @Before
    fun setup() {
        searchToolExecutor = mockk()
        imageInspectToolExecutor = mockk()
        extractToolExecutor = mockk()
        searchChatHistoryToolExecutor = mockk()
        searchChatToolExecutor = mockk()
        getMessageContextToolExecutor = mockk()
        manageMemoriesToolExecutor = mockk()
        artifactToolExecutor = mockk()
        eventBus = mockk(relaxed = true)
        compositeToolExecutor = CompositeToolExecutor(
            searchToolExecutor = searchToolExecutor,
            imageInspectToolExecutor = imageInspectToolExecutor,
            extractToolExecutor = extractToolExecutor,
            searchChatHistoryToolExecutor = searchChatHistoryToolExecutor,
            searchChatToolExecutor = searchChatToolExecutor,
            getMessageContextToolExecutor = getMessageContextToolExecutor,
            manageMemoriesToolExecutor = manageMemoriesToolExecutor,
            artifactToolExecutor = artifactToolExecutor,
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

    @Test
    fun `execute routes tavily_extract to extractToolExecutor`() = runTest {
        // Given
        val request = ToolCallRequest(
            chatId = ChatId("chat-456"),
            userMessageId = MessageId("msg-456"),
            toolName = ToolDefinition.TAVILY_EXTRACT.name,
            argumentsJson = "{\"urls\":[\"https://example.com\"]}",
            provider = "tavily",
            modelType = ModelType.FAST
        )
        val expectedResult = ToolExecutionResult(toolName = request.toolName, resultJson = "{\"status\": \"ok\"}")
        coEvery { extractToolExecutor.execute(request) } returns expectedResult

        // When
        val result = compositeToolExecutor.execute(request)

        // Then
        coVerify { extractToolExecutor.execute(request) }
        assertEquals(expectedResult, result)
    }

    @Test
    fun `execute emits started and finished for tavily_extract`() = runTest {
        // Given
        val request = ToolCallRequest(
            chatId = ChatId("chat-789"),
            userMessageId = MessageId("msg-789"),
            toolName = ToolDefinition.TAVILY_EXTRACT.name,
            argumentsJson = "{\"urls\":[\"https://example.com\"]}",
            provider = "tavily",
            modelType = ModelType.FAST
        )
        val expectedResult = ToolExecutionResult(toolName = request.toolName, resultJson = "{\"status\": \"ok\"}")
        coEvery { extractToolExecutor.execute(request) } returns expectedResult

        // When
        compositeToolExecutor.execute(request)

        // Then
        coVerify(ordering = io.mockk.Ordering.SEQUENCE) {
            eventBus.emit(match { (it as? ToolExecutionEvent.Started)?.toolName == request.toolName })
            extractToolExecutor.execute(request)
            eventBus.emit(match { it is ToolExecutionEvent.Finished && it.error == null })
        }
    }

    @Test
    fun `execute routes generate_artifact to artifactToolExecutor`() = runTest {
        // Given
        val request = ToolCallRequest(
            chatId = ChatId("chat-abc"),
            userMessageId = MessageId("msg-abc"),
            toolName = ToolDefinition.GENERATE_ARTIFACT.name,
            argumentsJson = "{\"documentType\":\"PDF\",\"title\":\"Report\",\"content\":\"{}\"}",
            provider = "xai",
            modelType = ModelType.FAST
        )
        val expectedResult = ToolExecutionResult(toolName = request.toolName, resultJson = "{\"status\": \"success\"}")
        coEvery { artifactToolExecutor.execute(request) } returns expectedResult

        // When
        val result = compositeToolExecutor.execute(request)

        // Then
        coVerify { artifactToolExecutor.execute(request) }
        assertEquals(expectedResult, result)
    }

    @Test
    fun `all tools in ToolDefinition ALL_TOOLS are supported`() = runTest {
        // Setup mocks for all executors to handle any request
        val executors = listOf(
            searchToolExecutor, extractToolExecutor, imageInspectToolExecutor,
            searchChatHistoryToolExecutor, searchChatToolExecutor,
            getMessageContextToolExecutor, manageMemoriesToolExecutor, artifactToolExecutor
        )
        executors.forEach {
            coEvery { it.execute(any()) } returns ToolExecutionResult("any", "{}")
        }

        ToolDefinition.ALL_TOOLS.forEach { tool ->
            val request = ToolCallRequest(
                toolName = tool.name,
                argumentsJson = "{}",
                provider = "test",
                modelType = ModelType.FAST
            )

            try {
                compositeToolExecutor.execute(request)
            } catch (e: IllegalArgumentException) {
                if (e.message?.startsWith("Unsupported tool") == true) {
                    org.junit.Assert.fail("Tool ${tool.name} is defined in ToolDefinition.ALL_TOOLS but not supported by CompositeToolExecutor")
                }
                // Other IllegalArgumentExceptions are okay (e.g. from ToolCallRequest decoding)
            } catch (e: Exception) {
                // Other exceptions are fine, they mean the routing worked but execution failed (expected)
            }
        }
    }
}
