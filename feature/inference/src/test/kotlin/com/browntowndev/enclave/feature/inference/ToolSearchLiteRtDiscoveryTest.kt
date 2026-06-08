package com.browntowndev.enclave.feature.inference

import android.util.Log
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import com.browntowndev.enclave.domain.port.inference.Bm25OnlyEmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.usecase.chat.SearchToolsHostCoordinator
import com.browntowndev.enclave.domain.usecase.chat.ToolCallStrategy
import com.browntowndev.enclave.domain.usecase.chat.ToolSessionActivationRegistry
import com.browntowndev.enclave.feature.inference.conversation.ConversationCancellationGate
import com.browntowndev.enclave.feature.inference.conversation.ConversationSignatureCache
import com.browntowndev.enclave.feature.inference.conversation.LiteRtToolCallLoop
import com.browntowndev.enclave.feature.inference.conversation.NoopAgentPolicyPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ToolSearchLiteRtDiscoveryTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val executorCalls = AtomicInteger(0)
    private lateinit var signatureCache: ConversationSignatureCache
    private lateinit var toolLoop: LiteRtToolCallLoop

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        executorCalls.set(0)
        val countingExecutor =
            object : ToolExecutorPort {
                override suspend fun execute(
                    request: com.browntowndev.enclave.domain.model.inference.ToolCallRequest,
                ): com.browntowndev.enclave.domain.model.inference.ToolExecutionResult {
                    executorCalls.incrementAndGet()
                    return com.browntowndev.enclave.domain.model.inference.ToolExecutionResult(
                        toolName = request.toolName,
                        resultJson = """{"ok":true}""",
                    )
                }
            }

        signatureCache = ConversationSignatureCache()
        signatureCache.toolActivationRegistry =
            ToolSessionActivationRegistry(
                onActiveNamesUpdated = { names -> signatureCache.activeToolNames = names },
            )
        toolLoop =
            LiteRtToolCallLoop(
                cache = signatureCache,
                toolExecutor = countingExecutor,
                loggingPort = loggingPort,
                agentPolicyPort = NoopAgentPolicyPort,
                cancellationGate = ConversationCancellationGate(),
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
    fun `TSD-12 LiteRtToolCallLoop updates active names after search`() =
        runTest {
            signatureCache.toolActivationRegistry.seedTurn(
                listOf("search_tools", "web_search", "web_extract"),
            )
            signatureCache.toolCatalog =
                listOf(
                    catalogEntry("search_tools", ToolDisclosureMode.PRELOADED),
                    catalogEntry("web_search", ToolDisclosureMode.PRELOADED),
                    catalogEntry("web_extract", ToolDisclosureMode.PRELOADED),
                    catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                )
            signatureCache.toolCallStrategy = ToolCallStrategy.LITE_RT_NATIVE

            val result =
                toolLoop.executeToolSafely(
                    ToolCallRequest(
                        toolName = ToolDefinition.SEARCH_TOOLS.name,
                        argumentsJson = """{"query":"append_blocks"}""",
                        provider = "LITERT",
                        modelType = ModelType.FAST,
                        chatId = ChatId("chat"),
                        userMessageId = MessageId("user"),
                        assistantMessageId = MessageId("assistant"),
                    ),
                )

            assertEquals(0, executorCalls.get())
            assertTrue(result.contains("\"status\":\"ok\""))
            assertEquals(
                listOf("search_tools", "web_search", "web_extract", "append_blocks"),
                signatureCache.activeToolNames,
            )
        }

    @Test
    fun `search_tools discovery marks signature stale without closing conversation`() =
        runTest {
            val conversation = mockk<com.google.ai.edge.litertlm.Conversation>(relaxed = true)
            signatureCache.conversation = conversation
            signatureCache.currentSignature =
                com.browntowndev.enclave.feature.inference.conversation.ConversationSignature(
                    modelType = ModelType.FAST,
                    configId = com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId("cfg"),
                    thinkingEnabled = false,
                    systemPrompt = "sys",
                    temperature = 0.7,
                    topP = 0.95,
                    topK = 40,
                    hasSearchTool = true,
                    hasExtractTool = false,
                    hasImageTool = false,
                    hasMemoryTools = false,
                    hasGetMessageContextTool = false,
                    hasArtifactTools = false,
                    activeToolNamesKey = "search_tools",
                )
            signatureCache.toolActivationRegistry =
                ToolSessionActivationRegistry(
                    onActiveNamesUpdated = { names -> signatureCache.activeToolNames = names },
                    onSearchDiscoveredNewTools = { signatureCache.currentSignature = null },
                )
            signatureCache.toolCatalog =
                listOf(
                    catalogEntry("search_tools", ToolDisclosureMode.PRELOADED),
                    catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                )

            toolLoop.handleToolSearch(argumentsJson = """{"query":"append_blocks"}""")

            assertEquals(conversation, signatureCache.conversation)
            io.mockk.verify(exactly = 0) { conversation.close() }
            org.junit.jupiter.api.Assertions.assertNull(signatureCache.currentSignature)
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
}
