package com.browntowndev.enclave.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.inference.Bm25OnlyEmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.chat.SearchToolsHostCoordinator
import com.browntowndev.enclave.domain.usecase.chat.ToolCallStrategy
import com.browntowndev.enclave.domain.usecase.chat.ToolSessionActivationRegistry
import com.browntowndev.enclave.feature.inference.conversation.ConversationCancellationGate
import com.browntowndev.enclave.feature.inference.conversation.ConversationSignatureCache
import com.browntowndev.enclave.feature.inference.conversation.LiteRtNativeToolsets
import com.browntowndev.enclave.feature.inference.conversation.LiteRtToolCallLoop
import com.browntowndev.enclave.feature.inference.conversation.LiteRtTurnSnapshot
import com.browntowndev.enclave.feature.inference.conversation.LiteRtToolContext
import com.browntowndev.enclave.feature.inference.conversation.SystemPromptTemplates
import com.browntowndev.enclave.feature.inference.conversation.NoopAgentPolicyPort
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
    private lateinit var signatureCache: ConversationSignatureCache
    private lateinit var cancellationGate: ConversationCancellationGate

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
                embeddingEnginePort = Bm25OnlyEmbeddingEnginePort,
            )

        signatureCache = ConversationSignatureCache()
        cancellationGate = ConversationCancellationGate()
        cancellationGate.beginRequest()
        signatureCache.toolActivationRegistry =
            ToolSessionActivationRegistry(
                onActiveNamesUpdated = { names -> signatureCache.activeToolNames = names },
            )
        toolLoop =
            LiteRtToolCallLoop(
                cache = signatureCache,
                toolExecutor = toolExecutor,
                loggingPort = loggingPort,
                agentPolicyPort = NoopAgentPolicyPort,
                cancellationGate = cancellationGate,
                searchToolsHostCoordinator =
                    SearchToolsHostCoordinator(
                        embeddingEngine = Bm25OnlyEmbeddingEnginePort,
                    ),
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `LTD-12 LiteRT native toolset list expands after search_tools`() =
        runTest {
            signatureCache.toolActivationRegistry.seedTurn(
                listOf("tool_discovery_tool", "web_search", "web_extract"),
            )
            signatureCache.toolCatalog =
                listOf(
                    catalogEntry("tool_discovery_tool", ToolDisclosureMode.PRELOADED),
                    catalogEntry("web_search", ToolDisclosureMode.PRELOADED),
                    catalogEntry("web_extract", ToolDisclosureMode.PRELOADED),
                    catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                )
            signatureCache.toolCallStrategy = ToolCallStrategy.LITE_RT_NATIVE
            val searchResult =
                toolLoop.handleToolSearch(
                    argumentsJson = """{"capability":"append_blocks","limit":"5"}""",
                    turnContext = createToolContext(),
                )
            val toolsets =
                LiteRtNativeToolsets.buildToolList(
                    activeToolNames = signatureCache.activeToolNames,
                    context = createToolContext(),
                )
            val callableNames = toolsets.flatMap { toolset -> toolset.javaClass.methods.map { it.name } }

            assertTrue(searchResult.contains("\"activated_tools\":[\"append_blocks\"]"))
            assertEquals(
                listOf("tool_discovery_tool", "web_search", "web_extract", "append_blocks"),
                signatureCache.activeToolNames,
            )
            assertEquals(
                listOf("tool_discovery_tool", "tavily_web_search", "tavily_extract", "append_blocks"),
                callableNames
                    .filter {
                        it in
                            listOf(
                                "tool_discovery_tool",
                                "tavily_web_search",
                                "tavily_extract",
                                ToolDefinition.APPEND_BLOCKS.name,
                            )
                    },
            )
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

    @Test
    fun `search_tools returns no activation for tools absent from turn catalog`() =
        runTest {
            signatureCache.toolActivationRegistry.seedTurn(
                listOf("tool_discovery_tool", "web_search", "web_extract"),
            )
            signatureCache.toolCatalog =
                listOf(
                    catalogEntry("tool_discovery_tool", ToolDisclosureMode.PRELOADED),
                    catalogEntry("web_search", ToolDisclosureMode.PRELOADED),
                    catalogEntry("web_extract", ToolDisclosureMode.PRELOADED),
                    catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                )

            val searchResult =
                toolLoop.handleToolSearch(
                    argumentsJson = """{"capability":"xyzzy_nonexistent_capability_token","limit":"5"}""",
                    turnContext = createToolContext(),
                )

            assertTrue(searchResult.contains("\"status\":\"ok\""))
            assertTrue(searchResult.contains("\"activated_tools\":[]"))
            assertEquals(
                listOf("tool_discovery_tool", "web_search", "web_extract"),
                signatureCache.activeToolNames,
            )
        }

    private fun catalogEntry(
        name: String,
        disclosureMode: ToolDisclosureMode,
    ): ToolCatalogEntry =
        ToolCatalogEntry(
            name = name,
            description = "$name description",
            disclosureMode = disclosureMode,
        )

    private fun createToolContext(requestId: Int = cancellationGate.currentRequestId()): LiteRtToolContext =
        LiteRtToolContext(
            modelType = ModelType.FAST,
            chatId = ChatId("chat-1"),
            userMessageId = MessageId("msg-1"),
            toolLoop = toolLoop,
            turn =
                LiteRtTurnSnapshot(
                    requestId = requestId,
                    systemPrompt = SystemPromptTemplates.DEFAULT,
                    contextWindow = ConversationSignatureCache.DEFAULT_CONTEXT_WINDOW,
                    history = signatureCache.history,
                    modelPath = signatureCache.currentModelPath,
                    latestAssistantMessageId = null,
                    toolCatalog = signatureCache.toolCatalog,
                    toolSearchIndex = signatureCache.toolSearchIndex,
                    toolCallStrategy = signatureCache.toolCallStrategy,
                    transientToolResultTokens = signatureCache.transientToolResultTokens.get(),
                ),
        )

    private fun createSearchChatHistoryToolset(): LiteRtNativeToolsets.LocalSearchChatHistoryToolset =
        LiteRtNativeToolsets.LocalSearchChatHistoryToolset(createToolContext())

    private fun createExtractToolset(): LiteRtNativeToolsets.LocalExtractToolset =
        LiteRtNativeToolsets.LocalExtractToolset(createToolContext())
}
