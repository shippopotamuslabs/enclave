package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.artifact.ArtifactToolExecutor
import com.browntowndev.enclave.core.data.artifact.SearchArtifactToolExecutor
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionEvent
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    private lateinit var searchArtifactToolExecutor: SearchArtifactToolExecutor
    private lateinit var xSearchToolExecutor: XSearchToolExecutorImpl
    private lateinit var xGetThreadToolExecutor: XGetThreadToolExecutor
    private lateinit var xTrendingToolExecutor: XTrendingToolExecutor
    private lateinit var xUserTweetsToolExecutor: XUserTweetsToolExecutor
    private lateinit var xMentionSearchToolExecutor: XMentionSearchToolExecutor
    private lateinit var mediaToolExecutor: MediaToolExecutor
    private lateinit var eventBus: ToolExecutionEventBus
    private lateinit var compositeToolExecutor: CompositeToolExecutor

    @BeforeEach
    fun setup() {
        searchToolExecutor = mockk()
        imageInspectToolExecutor = mockk()
        extractToolExecutor = mockk()
        searchChatHistoryToolExecutor = mockk()
        searchChatToolExecutor = mockk()
        getMessageContextToolExecutor = mockk()
        manageMemoriesToolExecutor = mockk()
        artifactToolExecutor = mockk()
        searchArtifactToolExecutor = mockk()
        xSearchToolExecutor = mockk()
        xGetThreadToolExecutor = mockk()
        xTrendingToolExecutor = mockk()
        xUserTweetsToolExecutor = mockk()
        xMentionSearchToolExecutor = mockk()
        mediaToolExecutor = mockk()
        eventBus = mockk(relaxed = true)
        compositeToolExecutor =
            CompositeToolExecutor(
                searchToolExecutor = searchToolExecutor,
                imageInspectToolExecutor = imageInspectToolExecutor,
                extractToolExecutor = extractToolExecutor,
                searchChatHistoryToolExecutor = searchChatHistoryToolExecutor,
                searchChatToolExecutor = searchChatToolExecutor,
                getMessageContextToolExecutor = getMessageContextToolExecutor,
                manageMemoriesToolExecutor = manageMemoriesToolExecutor,
                artifactToolExecutor = artifactToolExecutor,
                searchArtifactToolExecutor = searchArtifactToolExecutor,
                xSearchToolExecutor = xSearchToolExecutor,
                xGetThreadToolExecutor = xGetThreadToolExecutor,
                xTrendingToolExecutor = xTrendingToolExecutor,
                xUserTweetsToolExecutor = xUserTweetsToolExecutor,
                xMentionSearchToolExecutor = xMentionSearchToolExecutor,
                mediaToolExecutor = mediaToolExecutor,
                eventBus = eventBus,
                telemetryEventPort = mockk(relaxed = true),
            )
    }

    @Test
    fun `execute emits started and finished events for successful execution`() =
        runTest {
            // Given
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-123"),
                    userMessageId = MessageId("msg-123"),
                    toolName = ToolDefinition.WEB_SEARCH.name,
                    argumentsJson = "{\"query\": \"test\"}",
                    provider = "tavily",
                    modelType = ModelType.FAST,
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
    fun `execute emits started and finished with error for failed execution`() =
        runTest {
            // Given
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-123"),
                    userMessageId = MessageId("msg-123"),
                    toolName = ToolDefinition.WEB_SEARCH.name,
                    argumentsJson = "{\"query\": \"test\"}",
                    provider = "tavily",
                    modelType = ModelType.FAST,
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
    fun `execute routes tavily_extract to extractToolExecutor`() =
        runTest {
            // Given
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-456"),
                    userMessageId = MessageId("msg-456"),
                    toolName = ToolDefinition.WEB_EXTRACT.name,
                    argumentsJson = "{\"urls\":[\"https://example.com\"]}",
                    provider = "tavily",
                    modelType = ModelType.FAST,
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
    fun `execute emits started and finished for tavily_extract`() =
        runTest {
            // Given
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-789"),
                    userMessageId = MessageId("msg-789"),
                    toolName = ToolDefinition.WEB_EXTRACT.name,
                    argumentsJson = "{\"urls\":[\"https://example.com\"]}",
                    provider = "tavily",
                    modelType = ModelType.FAST,
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
    fun `execute routes search_x_recent to xSearchToolExecutor`() =
        runTest {
            // Given
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-xyz"),
                    userMessageId = MessageId("msg-xyz"),
                    toolName = ToolDefinition.X_SEARCH.name,
                    argumentsJson = "{\"query\":\"test\"}",
                    provider = "x",
                    modelType = ModelType.FAST,
                )
            val expectedResult = ToolExecutionResult(toolName = request.toolName, resultJson = "{\"status\": \"success\"}")
            coEvery { xSearchToolExecutor.execute(request) } returns expectedResult

            // When
            val result = compositeToolExecutor.execute(request)

            // Then
            coVerify { xSearchToolExecutor.execute(request) }
            assertEquals(expectedResult, result)
        }

    @Test
    fun `execute routes generate_image to mediaToolExecutor`() =
        runTest {
            // Given
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-media"),
                    userMessageId = MessageId("msg-media"),
                    toolName = ToolDefinition.GENERATE_IMAGE.name,
                    argumentsJson = "{\"prompt\":\"test\"}",
                    provider = "replicate",
                    modelType = ModelType.FAST,
                )
            val expectedResult = ToolExecutionResult(toolName = request.toolName, resultJson = "{\"status\": \"success\"}")
            coEvery { mediaToolExecutor.execute(request) } returns expectedResult

            // When
            val result = compositeToolExecutor.execute(request)

            // Then
            coVerify { mediaToolExecutor.execute(request) }
            assertEquals(expectedResult, result)
        }

    @Test
    fun `all tools in ToolDefinition ALL_TOOLS are supported`() =
        runTest {
            // Setup mocks for all executors to handle any request
            val executors =
                listOf(
                    searchToolExecutor,
                    extractToolExecutor,
                    imageInspectToolExecutor,
                    searchChatHistoryToolExecutor,
                    searchChatToolExecutor,
                    getMessageContextToolExecutor,
                    manageMemoriesToolExecutor,
                    artifactToolExecutor,
                    xSearchToolExecutor,
                    xGetThreadToolExecutor,
                    xTrendingToolExecutor,
                    xUserTweetsToolExecutor,
                    xMentionSearchToolExecutor,
                    mediaToolExecutor,
                )
            executors.forEach {
                coEvery { it.execute(any()) } returns ToolExecutionResult("any", "{}")
            }

            ToolDefinition.ALL_TOOLS.forEach { tool ->
                val request =
                    ToolCallRequest(
                        toolName = tool.name,
                        argumentsJson = "{}",
                        provider = "test",
                        modelType = ModelType.FAST,
                    )

                try {
                    compositeToolExecutor.execute(request)
                } catch (e: IllegalArgumentException) {
                    if (e.message?.startsWith("Unsupported tool") == true) {
                        org.junit.jupiter.api.Assertions.fail<Unit>(
                            "Tool ${tool.name} is defined in ToolDefinition.ALL_TOOLS but not supported by CompositeToolExecutor",
                        )
                    }
                    // Other IllegalArgumentExceptions are okay (e.g. from ToolCallRequest decoding)
                } catch (e: Exception) {
                    // Other exceptions are fine, they mean the routing worked but execution failed (expected)
                }
            }
        }

    @Test
    fun `SCENARIO_COMPOSITE_EXECUTOR_ROUTING`() =
        runTest {
            val getThreadRequest =
                ToolCallRequest(toolName = "x_get_thread", argumentsJson = "{}", provider = "x", modelType = ModelType.FAST)
            val trendingRequest = ToolCallRequest(toolName = "x_trending", argumentsJson = "{}", provider = "x", modelType = ModelType.FAST)
            val userTweetsRequest =
                ToolCallRequest(toolName = "x_user_tweets", argumentsJson = "{}", provider = "x", modelType = ModelType.FAST)
            val mentionSearchRequest =
                ToolCallRequest(toolName = "x_mention_search", argumentsJson = "{}", provider = "x", modelType = ModelType.FAST)

            coEvery { xGetThreadToolExecutor.execute(getThreadRequest) } returns ToolExecutionResult("x_get_thread", "thread_result")
            coEvery { xTrendingToolExecutor.execute(trendingRequest) } returns ToolExecutionResult("x_trending", "trending_result")
            coEvery { xUserTweetsToolExecutor.execute(userTweetsRequest) } returns
                ToolExecutionResult("x_user_tweets", "user_tweets_result")
            coEvery { xMentionSearchToolExecutor.execute(mentionSearchRequest) } returns
                ToolExecutionResult("x_mention_search", "mention_search_result")

            assertEquals("thread_result", compositeToolExecutor.execute(getThreadRequest).resultJson)
            assertEquals("trending_result", compositeToolExecutor.execute(trendingRequest).resultJson)
            assertEquals("user_tweets_result", compositeToolExecutor.execute(userTweetsRequest).resultJson)
            assertEquals("mention_search_result", compositeToolExecutor.execute(mentionSearchRequest).resultJson)
        }
}
