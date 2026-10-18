package com.browntowndev.enclave.feature.inference

import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeToolExecutor
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import com.browntowndev.enclave.domain.port.inference.Bm25OnlyEmbeddingEnginePort
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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ConversationManagerImplToolsTest {
    private val localModelRepository = FakeLocalModelRepository()
    private val activeModelProvider = FakeActiveModelProvider()
    private val loggingPort = FakeLoggingPort()
    private val toolExecutor = FakeToolExecutor()

    private lateinit var manager: ConversationManagerImpl
    private lateinit var toolLoop: LiteRtToolCallLoop
    private lateinit var signatureCache: ConversationSignatureCache
    private lateinit var cancellationGate: ConversationCancellationGate

    @BeforeEach
    fun setUp() {
        manager =
            ConversationManagerImpl(
                context = ApplicationProvider.getApplicationContext(),
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
                listOf(
                    ToolDefinition.APPEND_BLOCKS.name,
                    "tool_discovery_tool",
                    "tavily_extract",
                    "tavily_web_search",
                ),
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
    fun `buildToolList order is deterministic across activation orderings for cache stability`() {
        val toolContext = createToolContext()
        val orderA = listOf("web_search", "tool_discovery_tool", "append_blocks", "web_extract")
        val orderB = listOf("append_blocks", "web_extract", "tool_discovery_tool", "web_search")

        val toolsetsA = LiteRtNativeToolsets.buildToolList(orderA, toolContext)
        val toolsetsB = LiteRtNativeToolsets.buildToolList(orderB, toolContext)

        val classNamesA = toolsetsA.map { it.javaClass.simpleName }
        val classNamesB = toolsetsB.map { it.javaClass.simpleName }

        assertEquals(classNamesA, classNamesB)
        assertEquals(
            listOf(
                "LocalAppendBlocksToolset",
                "LocalSearchToolsToolset",
                "LocalExtractToolset",
                "LocalSearchToolset",
            ),
            classNamesA,
        )
    }

    @Test
    fun `LocalSearchChatHistoryToolset splits multiple queries correctly`() =
        runTest {
            val toolset = createSearchChatHistoryToolset()

            val searchMethod = toolset.javaClass.getDeclaredMethod("search_chat_history", String::class.java)

            toolExecutor.resultJson = "{\"results\":[]}"

            searchMethod.invoke(toolset, "query1, query2 ,  query3")

            val request = toolExecutor.executedRequests.single()
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

            toolExecutor.resultJson = "{\"results\":[]}"

            extractMethod.invoke(toolset, "[\"https://example.com\", \"https://google.com\"]", "advanced", "markdown")

            val request = toolExecutor.executedRequests.single()
            assertTrue(request.argumentsJson.contains("https://example.com"))
            assertTrue(request.argumentsJson.contains("https://google.com"))
            assertTrue(request.argumentsJson.contains("advanced"))
            assertTrue(request.argumentsJson.contains("markdown"))
        }

    @Test
    fun `executeToolSafely handles tool execution failure and returns error JSON`() =
        runTest {
            toolExecutor.failure = RuntimeException("Network error")

            val request =
                com.browntowndev.enclave.domain.model.inference.ToolCallRequest(
                    toolName = "web_search",
                    argumentsJson = "{\"query\":\"test\"}",
                    provider = "LITERT",
                    modelType = ModelType.FAST,
                )

            val result = manager.executeToolSafelyForTest(request)

            assertTrue(result.contains("tool_execution_failed"))
            assertTrue(result.contains("Network error"))
            assertTrue(loggingPort.errorCalls.isNotEmpty())
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
