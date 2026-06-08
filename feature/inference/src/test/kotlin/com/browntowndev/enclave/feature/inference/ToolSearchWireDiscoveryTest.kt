package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopConfig
import com.browntowndev.enclave.domain.model.inference.AgentLoopHostCallbacks
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.AgentToolCall
import com.browntowndev.enclave.domain.model.inference.AgentToolResult
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.inference.Bm25OnlyEmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.AgentLoopEvent
import com.browntowndev.enclave.domain.port.inference.AgentLoopPort
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.NoopDeviceEnvironmentRepository
import com.browntowndev.enclave.domain.usecase.chat.InMemoryCommittedInferenceTranscriptStore
import com.browntowndev.enclave.domain.usecase.chat.InMemoryMonotonicToolActivationStore
import com.browntowndev.enclave.domain.usecase.chat.RollingSummarizer
import com.browntowndev.enclave.domain.usecase.inference.ResolveCompactionSummaryModelUseCase
import com.browntowndev.enclave.domain.util.JTokkitTokenCounter
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider

class ToolSearchWireDiscoveryTest {
    private val logger = RecordingLogger()

    @Test
    fun `TSD-11 RustLoopInferenceService handles search_tools without ToolExecutorPort`() =
        runTest {
            val executorCalls = AtomicInteger(0)
            val executor =
                object : ToolExecutorPort {
                    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
                        executorCalls.incrementAndGet()
                        return ToolExecutionResult(request.toolName, """{"ok":true}""")
                    }
                }
            lateinit var result: AgentToolResult
            val port =
                CapturingAgentLoopPort { host ->
                    result =
                        host
                            .executeTools(
                                listOf(
                                    AgentToolCall(
                                        callId = "call-search",
                                        name = "search_tools",
                                        argumentsJson = """{"query":"append_blocks"}""",
                                    ),
                                ),
                            ).single()
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port, toolExecutor = executor)

            service
                .sendPrompt(
                    prompt = "write",
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            toolCatalog = discoveryCatalog(),
                            activeTools =
                                listOf(
                                    ToolDefinition.SEARCH_TOOLS,
                                    ToolDefinition.WEB_SEARCH,
                                    ToolDefinition.WEB_EXTRACT,
                                ),
                        ),
                ).toList()

            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            assertAll(
                { assertEquals(0, executorCalls.get()) },
                { assertEquals("ok", json["status"]?.jsonPrimitive?.content) },
                { assertEquals(listOf("append_blocks"), result.activatedToolNames) },
            )
        }

    @Test
    fun `TSD-13 wireToolsForRequest registers preload wire registry only`() =
        runTest {
            val inner = RecordingWireToolsService()
            val port =
                CapturingAgentLoopPort { host ->
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "system",
                        toolsSchemaJson = serializeToolNames(listOf("search_tools", "web_search", "web_extract")),
                        allowToolCall = true,
                    )
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port, inner = inner)

            service
                .sendPrompt(
                    prompt = "tools",
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            toolCatalog =
                                listOf(
                                    catalogEntry("search_tools", ToolDisclosureMode.PRELOADED),
                                    catalogEntry("web_search", ToolDisclosureMode.PRELOADED),
                                    catalogEntry("web_extract", ToolDisclosureMode.PRELOADED),
                                    catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                                ),
                            activeTools =
                                listOf(
                                    ToolDefinition.SEARCH_TOOLS,
                                    ToolDefinition.WEB_SEARCH,
                                    ToolDefinition.WEB_EXTRACT,
                                ),
                        ),
                ).toList()

            val registered = inner.toolsPerInvocation.single().map { it.name }.sorted()
            assertEquals(listOf("search_tools", "web_extract", "web_search"), registered)
            assertFalse(registered.contains("append_blocks"))
        }

    @Test
    fun `TSD-27 activated tool persists on wire for subsequent model calls in session`() =
        runTest {
            val inner = RecordingWireToolsService()
            val nextTurnCompleted = CompletableDeferred<Unit>()
            val port =
                CapturingAgentLoopPort { host ->
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "system",
                        toolsSchemaJson =
                            serializeToolNames(
                                listOf(
                                    "search_tools",
                                    "web_search",
                                    "web_extract",
                                ),
                            ),
                        allowToolCall = true,
                    )
                    host
                        .executeTools(
                            listOf(
                                AgentToolCall(
                                    callId = "call-search",
                                    name = "search_tools",
                                    argumentsJson = """{"query":"append_blocks"}""",
                                ),
                            ),
                        ).single()
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "system",
                        toolsSchemaJson =
                            serializeToolNames(
                                listOf(
                                    "search_tools",
                                    "web_search",
                                    "web_extract",
                                    "append_blocks",
                                ),
                            ),
                        allowToolCall = true,
                    )
                    nextTurnCompleted.complete(Unit)
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port, inner = inner)

            service
                .sendPrompt(
                    prompt = "write",
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            toolCatalog = discoveryCatalog(),
                            activeTools =
                                listOf(
                                    ToolDefinition.SEARCH_TOOLS,
                                    ToolDefinition.WEB_SEARCH,
                                    ToolDefinition.WEB_EXTRACT,
                                ),
                        ),
                ).toList()
            withTimeout(1_000) { nextTurnCompleted.await() }

            val firstWire = inner.toolsPerInvocation.first().map { it.name }
            val lastWire = inner.toolsPerInvocation.last().map { it.name }
            assertAll(
                { assertFalse(firstWire.contains("append_blocks")) },
                { assertTrue(lastWire.contains("append_blocks")) },
                { assertTrue(lastWire.contains("web_search")) },
                {
                    assertFalse(
                        lastWire.any { name ->
                            name !in
                                setOf(
                                    "search_tools",
                                    "web_search",
                                    "web_extract",
                                    "append_blocks",
                                )
                        },
                    )
                },
            )
        }

    @Test
    fun `TSD-28 search_tools activation persists to monotonic store for next turn wire seed`() =
        runTest {
            val monotonicStore = InMemoryMonotonicToolActivationStore()
            val chatId = ChatId("chat-monotonic-wire")
            val inner = RecordingWireToolsService()
            var agentLoopInvocations = 0
            val port =
                CapturingAgentLoopPort { host ->
                    if (agentLoopInvocations == 0) {
                        host
                            .executeTools(
                                listOf(
                                    AgentToolCall(
                                        callId = "call-search",
                                        name = "search_tools",
                                        argumentsJson = """{"query":"append_blocks"}""",
                                    ),
                                ),
                            ).single()
                    }
                    agentLoopInvocations++
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "system",
                        toolsSchemaJson = serializeToolNames(listOf("search_tools")),
                        allowToolCall = true,
                    )
                    send(AgentLoopEvent.Finished)
                }
            val service =
                createService(
                    agentLoopPort = port,
                    inner = inner,
                    monotonicToolActivation = monotonicStore,
                )

            service
                .sendPrompt(
                    prompt = "write",
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            chatId = chatId,
                            toolCatalog = discoveryCatalog(),
                            activeTools =
                                listOf(
                                    ToolDefinition.SEARCH_TOOLS,
                                    ToolDefinition.WEB_SEARCH,
                                    ToolDefinition.WEB_EXTRACT,
                                ),
                        ),
                ).toList()

            assertTrue(monotonicStore.activatedNames(chatId).contains("append_blocks"))

            service
                .sendPrompt(
                    prompt = "continue",
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            chatId = chatId,
                            toolCatalog = discoveryCatalog(),
                            activeTools =
                                listOf(
                                    ToolDefinition.SEARCH_TOOLS,
                                    ToolDefinition.WEB_SEARCH,
                                    ToolDefinition.WEB_EXTRACT,
                                    ToolDefinition.APPEND_BLOCKS,
                                ),
                        ),
                ).toList()

            val seededWire = inner.toolsPerInvocation.last().map { it.name }
            assertTrue(seededWire.contains("append_blocks"))
        }

    @Test
    fun `MUT-02 wireToolsForRequest must not register deferred append_blocks`() =
        runTest {
            val inner = RecordingWireToolsService()
            val port =
                CapturingAgentLoopPort { host ->
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "system",
                        toolsSchemaJson = serializeToolNames(listOf("search_tools", "web_search")),
                        allowToolCall = true,
                    )
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port, inner = inner)

            service
                .sendPrompt(
                    prompt = "hello",
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            toolingEnabled = true,
                            toolCatalog =
                                listOf(
                                    catalogEntry("search_tools", ToolDisclosureMode.PRELOADED),
                                    catalogEntry("web_search", ToolDisclosureMode.PRELOADED),
                                    catalogEntry("web_extract", ToolDisclosureMode.PRELOADED),
                                    catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                                ),
                            activeTools = listOf(ToolDefinition.SEARCH_TOOLS, ToolDefinition.WEB_SEARCH),
                        ),
                ).toList()

            val params =
                OpenAiRequestMapper.mapToChatCompletionParams(
                    modelId = "gpt-4o",
                    prompt = "hello",
                    history = emptyList(),
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            toolingEnabled = true,
                            activeTools = inner.toolsPerInvocation.single(),
                        ),
                )
            val payload = params.tools().orElseThrow().toString()
            val registered = params.tools().orElseThrow().map { it.asFunction().function().name() }

            assertAll(
                { assertEquals(listOf("search_tools", "web_search"), registered.sorted()) },
                { assertFalse(payload.contains("\"append_blocks\"")) },
            )
        }

    private fun createService(
        agentLoopPort: AgentLoopPort,
        inner: RustCompatibleInferenceService = RecordingWireToolsService(),
        toolExecutor: ToolExecutorPort =
            object : ToolExecutorPort {
                override suspend fun execute(request: ToolCallRequest): ToolExecutionResult =
                    ToolExecutionResult(request.toolName, """{"ok":true}""")
            },
        embeddingEnginePort: EmbeddingEnginePort = Bm25OnlyEmbeddingEnginePort,
        monotonicToolActivation: com.browntowndev.enclave.domain.port.inference.MonotonicToolActivationPort =
            InMemoryMonotonicToolActivationStore(),
    ): RustLoopInferenceService =
        RustLoopInferenceService(
            inner = inner,
            agentLoopPort = agentLoopPort,
            toolExecutor = toolExecutor,
            loggingPort = logger,
            provider = "OPENAI",
            modelType = ModelType.THINKING,
            inferenceFactoryProvider = Provider { mockk(relaxed = true) },
            rollingSummarizer = RollingSummarizer(logger, JTokkitTokenCounter),
            resolveCompactionSummaryModel =
                ResolveCompactionSummaryModelUseCase(
                    mockk { coEvery { getActiveConfiguration(ModelType.FAST) } returns null },
                    JTokkitTokenCounter,
                ),
            committedInferenceTranscripts = InMemoryCommittedInferenceTranscriptStore(),
            deviceEnvironmentRepository = NoopDeviceEnvironmentRepository,
            embeddingEnginePort = embeddingEnginePort,
            monotonicToolActivation = monotonicToolActivation,
        )

    private fun discoveryCatalog(): List<ToolCatalogEntry> =
        listOf(
            catalogEntry("search_tools", ToolDisclosureMode.PRELOADED),
            catalogEntry("web_search", ToolDisclosureMode.PRELOADED),
            catalogEntry("web_extract", ToolDisclosureMode.PRELOADED),
            catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
        )

    private fun catalogEntry(
        name: String,
        disclosureMode: ToolDisclosureMode,
    ): ToolCatalogEntry =
        ToolCatalogEntry(
            name = name,
            description = "$name description",
            disclosureMode = disclosureMode,
        )

    private fun serializeToolNames(names: List<String>): String =
        names.joinToString(prefix = "[", postfix = "]") { name -> """{"name":"$name"}""" }

    private class CapturingAgentLoopPort(
        private val behavior: suspend ProducerScope<AgentLoopEvent>.(AgentLoopHostCallbacks) -> Unit,
    ) : AgentLoopPort {
        override fun execute(
            initialMessages: List<ChatMessage>,
            config: AgentLoopConfig,
            sessionContext: Map<String, String>,
            host: AgentLoopHostCallbacks,
        ): Flow<AgentLoopEvent> =
            channelFlow {
                behavior(host)
            }
    }

    private class RecordingLogger : LoggingPort {
        override fun debug(
            tag: String,
            message: String,
        ) = Unit

        override fun info(
            tag: String,
            message: String,
        ) = Unit

        override fun warning(
            tag: String,
            message: String,
        ) = Unit

        override fun error(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit
    }

    private class RecordingWireToolsService : RustCompatibleInferenceService {
        val toolsPerInvocation = mutableListOf<List<ToolDefinition>>()

        override fun sendPrompt(
            prompt: String,
            closeConversation: Boolean,
        ): Flow<InferenceEvent> = flowOf(InferenceEvent.Finished(ModelType.THINKING))

        override fun sendPrompt(
            prompt: String,
            options: GenerationOptions,
            closeConversation: Boolean,
        ): Flow<InferenceEvent> = flowOf(InferenceEvent.Finished(ModelType.THINKING))

        override suspend fun setHistory(messages: List<ChatMessage>) {}

        override suspend fun closeSession() {}

        override suspend fun completeSingleTurn(
            sessionMessages: List<AgentLoopMessage>,
            systemPrompt: String,
            contextSidecars: String?,
            availableTools: List<ToolDefinition>,
            allowToolCall: Boolean,
            onTextToken: suspend (String) -> Unit,
            onThinkingToken: suspend (String) -> Unit,
            options: GenerationOptions,
            onLifecycleEvent: suspend (InferenceEvent) -> Unit,
        ): AgentModelTurnResult {
            toolsPerInvocation.add(availableTools)
            return AgentModelTurnResult(
                assistantText = "done",
                toolCalls = emptyList(),
                finishReason = AgentFinishReason.Stop,
            )
        }
    }
}
