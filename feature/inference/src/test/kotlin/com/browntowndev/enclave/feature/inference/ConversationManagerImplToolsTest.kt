package com.browntowndev.enclave.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.feature.inference.conversation.ConversationCancellationGate
import com.browntowndev.enclave.feature.inference.conversation.ConversationSignatureCache
import com.browntowndev.enclave.feature.inference.conversation.LiteRtNativeToolsets
import com.browntowndev.enclave.feature.inference.conversation.LiteRtToolCallLoop
import com.browntowndev.enclave.feature.inference.conversation.LiteRtToolContext
import com.browntowndev.enclave.feature.inference.conversation.NoopAgentPolicyPort
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConversationManagerImplToolsTest {
    private val context = mockk<Context>(relaxed = true)
    private val localModelRepository = mockk<LocalModelRepositoryPort>(relaxed = true)
    private val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)

    private lateinit var manager: ConversationManagerImpl
    private lateinit var toolLoop: LiteRtToolCallLoop

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        manager =
            ConversationManagerImpl(
                context = context,
                localModelRepository = localModelRepository,
                activeModelProvider = activeModelProvider,
                loggingPort = loggingPort,
                toolExecutor = toolExecutor,
            )

        toolLoop =
            LiteRtToolCallLoop(
                cache = ConversationSignatureCache(),
                toolExecutor = toolExecutor,
                loggingPort = loggingPort,
                agentPolicyPort = NoopAgentPolicyPort,
                cancellationGate = ConversationCancellationGate(),
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `LocalSearchChatHistoryToolset splits multiple queries correctly`() =
        runTest {
            val toolset = createSearchChatHistoryToolset()

            val searchMethod = toolset.javaClass.getDeclaredMethod("search_chat_history", String::class.java)

            coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult("search_chat_history", "{\"results\":[]}")

            searchMethod.invoke(toolset, "query1, query2 ,  query3")

            val requestSlot = slot<ToolCallRequest>()
            coVerify { toolExecutor.execute(capture(requestSlot)) }

            val request = requestSlot.captured
            assertTrue(request.argumentsJson.contains("query1"))
            assertTrue(request.argumentsJson.contains("query2"))
            assertTrue(request.argumentsJson.contains("query3"))
        }

    @Test
    fun `LocalExtractToolset parses URLs correctly`() =
        runTest {
            val toolset = createExtractToolset()

            val extractMethod =
                toolset.javaClass.getDeclaredMethod(
                    "tavily_extract",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                )

            coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult("tavily_extract", "{\"results\":[]}")

            extractMethod.invoke(toolset, "[\"https://example.com\", \"https://google.com\"]", "advanced", "markdown")

            val requestSlot = slot<ToolCallRequest>()
            coVerify { toolExecutor.execute(capture(requestSlot)) }

            val request = requestSlot.captured
            assertTrue(request.argumentsJson.contains("https://example.com"))
            assertTrue(request.argumentsJson.contains("https://google.com"))
            assertTrue(request.argumentsJson.contains("advanced"))
            assertTrue(request.argumentsJson.contains("markdown"))
        }

    @Test
    fun `executeToolSafely handles tool execution failure and returns error JSON`() =
        runTest {
            coEvery { toolExecutor.execute(any()) } throws RuntimeException("Network error")

            val request =
                ToolCallRequest(
                    toolName = "web_search",
                    argumentsJson = "{\"query\":\"test\"}",
                    provider = "LITERT",
                    modelType = ModelType.FAST,
                )

            val result = manager.executeToolSafelyForTest(request)

            assertTrue(result.contains("tool_execution_failed"))
            assertTrue(result.contains("Network error"))
            verify { loggingPort.error(any(), any(), any()) }
        }

    private fun createToolContext(): LiteRtToolContext =
        LiteRtToolContext(
            modelType = ModelType.FAST,
            chatId = ChatId("chat-1"),
            userMessageId = MessageId("msg-1"),
            toolLoop = toolLoop,
        )

    private fun createSearchChatHistoryToolset(): LiteRtNativeToolsets.LocalSearchChatHistoryToolset =
        LiteRtNativeToolsets.LocalSearchChatHistoryToolset(createToolContext())

    private fun createExtractToolset(): LiteRtNativeToolsets.LocalExtractToolset =
        LiteRtNativeToolsets.LocalExtractToolset(createToolContext())
}
