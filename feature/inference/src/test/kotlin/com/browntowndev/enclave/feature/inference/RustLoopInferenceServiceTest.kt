package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.AgentContextReference
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentIncludedFile
import com.browntowndev.enclave.domain.model.inference.AgentLoopConfig
import com.browntowndev.enclave.domain.model.inference.AgentLoopContextPolicyTier
import com.browntowndev.enclave.domain.model.inference.AgentLoopHostCallbacks
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.AgentSummaryRequest
import com.browntowndev.enclave.domain.model.inference.AgentSummaryResult
import com.browntowndev.enclave.domain.model.inference.AgentToolCall
import com.browntowndev.enclave.domain.model.inference.AgentToolResult
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.inference.AgentLoopEvent
import com.browntowndev.enclave.domain.port.inference.AgentLoopPort
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.DeviceEnvironmentRepositoryPort
import com.browntowndev.enclave.domain.port.repository.NoopDeviceEnvironmentRepository
import com.browntowndev.enclave.domain.usecase.chat.InMemoryCommittedInferenceTranscriptStore
import com.browntowndev.enclave.domain.usecase.chat.RollingSummarizer
import com.browntowndev.enclave.domain.usecase.inference.ResolveCompactionSummaryModelUseCase
import com.browntowndev.enclave.domain.util.JTokkitTokenCounter
import com.browntowndev.enclave.feature.inference.conversation.ConversationCancellationGate
import com.browntowndev.enclave.feature.inference.conversation.ConversationSignatureCache
import com.browntowndev.enclave.feature.inference.conversation.LiteRtToolCallLoop
import com.browntowndev.enclave.feature.inference.conversation.NoopAgentPolicyPort
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider

class RustLoopInferenceServiceTest {
    private val logger = RecordingLogger()

    @Test
    fun `LTD-05 disclosure tool activates append_blocks for the next Rust backed model turn`() =
        runTest {
            lateinit var result: AgentToolResult
            val nextTurnCompleted = CompletableDeferred<Unit>()
            val innerService = RecordingRustCompatibleService()
            val port =
                CapturingAgentLoopPort { host ->
                    result =
                        host
                            .executeTools(
                                listOf(
                                    AgentToolCall(
                                        callId = "call-1",
                                        name = "search_tools",
                                        argumentsJson = """{"query":"append_blocks"}""",
                                    ),
                                ),
                            ).single()
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "",
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
            val service = createService(agentLoopPort = port, inner = innerService)

            service
                .sendPrompt(
                    prompt = "write",
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            toolCatalog = lazyDisclosureCatalog(),
                            activeTools =
                                listOf(
                                    ToolDefinition.SEARCH_TOOLS,
                                    ToolDefinition.WEB_SEARCH,
                                    ToolDefinition.WEB_EXTRACT,
                                ),
                        ),
                ).toList()
            withTimeout(1_000) { nextTurnCompleted.await() }

            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            val nextTurnToolNames = innerService.lastAvailableTools.map { tool -> tool.name }

            assertAll(
                { assertEquals("ok", json["status"]?.jsonPrimitive?.content) },
                { assertEquals(listOf("append_blocks"), json["activated_tools"]?.jsonArray?.map { it.jsonPrimitive.content }) },
                { assertEquals(listOf("append_blocks"), result.activatedToolNames) },
                {
                    assertEquals(
                        listOf(
                            "append_blocks",
                            "search_tools",
                            "web_extract",
                            "web_search",
                        ),
                        nextTurnToolNames.sorted(),
                    )
                },
            )
        }

    @Test
    fun `LTD-11 disclosure interception never invokes ToolExecutorPort`() =
        runTest {
            val callCount = AtomicInteger(0)
            val executor =
                object : ToolExecutorPort {
                    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
                        callCount.incrementAndGet()
                        return ToolExecutionResult(request.toolName, """{"ok":true}""")
                    }
                }
            lateinit var rustResult: AgentToolResult
            val port =
                CapturingAgentLoopPort { host ->
                    rustResult =
                        host
                            .executeTools(
                                listOf(
                                    AgentToolCall(
                                        callId = "call-1",
                                        name = "search_tools",
                                        argumentsJson = """{"query":"append_blocks"}""",
                                    ),
                                ),
                            ).single()
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port, toolExecutor = executor)
            val cache = ConversationSignatureCache()
            val cancellationGate = ConversationCancellationGate()
            cancellationGate.beginRequest()
            val liteRtLoop =
                LiteRtToolCallLoop(
                    cache = cache,
                    toolExecutor = executor,
                    loggingPort = logger,
                    agentPolicyPort = NoopAgentPolicyPort,
                    cancellationGate = cancellationGate,
                    searchToolsHostCoordinator =
                        com.browntowndev.enclave.domain.usecase.chat.SearchToolsHostCoordinator(
                            embeddingEngine = com.browntowndev.enclave.domain.port.inference.Bm25OnlyEmbeddingEnginePort,
                        ),
                )

            val disclosureCatalog =
                listOf(
                    catalogEntry("search_tools", ToolDisclosureMode.PRELOADED),
                    catalogEntry("web_search", ToolDisclosureMode.PRELOADED),
                    catalogEntry("web_extract", ToolDisclosureMode.PRELOADED),
                    catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                )
            cache.toolCatalog = disclosureCatalog
            cache.toolActivationRegistry =
                com.browntowndev.enclave.domain.usecase.chat.ToolSessionActivationRegistry(
                    onActiveNamesUpdated = { names -> cache.activeToolNames = names },
                )
            cache.toolActivationRegistry.seedTurn(listOf("search_tools", "web_search", "web_extract"))
            cache.toolCallStrategy = com.browntowndev.enclave.domain.usecase.chat.ToolCallStrategy.LITE_RT_NATIVE
            service
                .sendPrompt(
                    prompt = "disclose",
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            toolCatalog = disclosureCatalog,
                            activeTools =
                                listOf(
                                    ToolDefinition.SEARCH_TOOLS,
                                    ToolDefinition.WEB_SEARCH,
                                    ToolDefinition.WEB_EXTRACT,
                                ),
                        ),
                ).toList()
            val liteRtTurnContext =
                com.browntowndev.enclave.feature.inference.conversation.LiteRtToolContext(
                    modelType = ModelType.FAST,
                    chatId = null,
                    userMessageId = null,
                    toolLoop = liteRtLoop,
                    turn =
                        com.browntowndev.enclave.feature.inference.conversation.LiteRtTurnSnapshot(
                            requestId = cancellationGate.currentRequestId(),
                            systemPrompt = cache.currentSystemPrompt,
                            contextWindow = cache.currentContextWindow,
                            history = cache.history,
                            modelPath = cache.currentModelPath,
                            latestAssistantMessageId = cache.latestAssistantMessageId,
                            toolCatalog = cache.toolCatalog,
                            toolSearchIndex = cache.toolSearchIndex,
                            toolCallStrategy = cache.toolCallStrategy,
                            transientToolResultTokens = cache.transientToolResultTokens.get(),
                        ),
                )
            val liteRtResult =
                liteRtLoop.executeToolSafely(
                    ToolCallRequest(
                        toolName = "search_tools",
                        argumentsJson = """{"query":"append_blocks"}""",
                        provider = "LITERT",
                        modelType = ModelType.FAST,
                    ),
                    turnContext = liteRtTurnContext,
                )

            assertAll(
                { assertEquals(0, callCount.get()) },
                { assertEquals(listOf("append_blocks"), rustResult.activatedToolNames) },
                { assertTrue(liteRtResult.contains("\"activated_tools\":[\"append_blocks\"]")) },
                { assertEquals(0, callCount.get()) },
            )
        }

    @Test
    fun `start_artifact success activates append_blocks for next model turn`() =
        runTest {
            lateinit var result: AgentToolResult
            val nextTurnCompleted = CompletableDeferred<Unit>()
            val innerService = RecordingRustCompatibleService()
            val artifactId = "hbWxQYaTV7WyJ39rLRiq9to9LCu2"
            val port =
                CapturingAgentLoopPort { host ->
                    result =
                        host
                            .executeTools(
                                listOf(
                                    AgentToolCall(
                                        callId = "call-artifact-1",
                                        name = "start_artifact",
                                        argumentsJson =
                                            """{"author":"aether_oracle xAI","title":"MiniMax M3 - Complete Technical Overview"}""",
                                    ),
                                ),
                            ).single()
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "",
                        toolsSchemaJson =
                            serializeToolNames(
                                listOf(
                                    "search_tools",
                                    "web_search",
                                    "web_extract",
                                    "start_artifact",
                                    "append_blocks",
                                    "search_current_artifact",
                                ),
                            ),
                        allowToolCall = true,
                    )
                    nextTurnCompleted.complete(Unit)
                    send(AgentLoopEvent.Finished)
                }
            val executor =
                object : ToolExecutorPort {
                    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult =
                        ToolExecutionResult(
                            toolName = request.toolName,
                            resultJson =
                                """{"status":"success","artifactId":"$artifactId","message":"Artifact initialized."}""",
                        )
                }
            val service =
                createService(
                    agentLoopPort = port,
                    inner = innerService,
                    toolExecutor = executor,
                )

            service
                .sendPrompt(
                    prompt = "write overview",
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            toolCatalog =
                                listOf(
                                    catalogEntry("search_tools", ToolDisclosureMode.PRELOADED),
                                    catalogEntry("web_search", ToolDisclosureMode.PRELOADED),
                                    catalogEntry("web_extract", ToolDisclosureMode.PRELOADED),
                                    catalogEntry("start_artifact", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                                    catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                                    catalogEntry("search_current_artifact", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                                ),
                            activeTools =
                                listOf(
                                    ToolDefinition.SEARCH_TOOLS,
                                    ToolDefinition.WEB_SEARCH,
                                    ToolDefinition.WEB_EXTRACT,
                                    ToolDefinition.START_ARTIFACT,
                                ),
                        ),
                ).toList()
            withTimeout(1_000) { nextTurnCompleted.await() }

            val nextTurnToolNames = innerService.lastAvailableTools.map { it.name }.sorted()

            assertAll(
                {
                    assertEquals(
                        listOf(
                            ToolDefinition.APPEND_BLOCKS.name,
                            ToolDefinition.SEARCH_CURRENT_ARTIFACT.name,
                        ),
                        result.activatedToolNames.sorted(),
                    )
                },
                {
                    assertEquals(
                        listOf(
                            "append_blocks",
                            "search_current_artifact",
                            "search_tools",
                            "start_artifact",
                            "web_extract",
                            "web_search",
                        ),
                        nextTurnToolNames,
                    )
                },
            )
        }

    private fun lazyDisclosureCatalog(): List<ToolCatalogEntry> =
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
        Json.encodeToString(
            JsonArray.serializer(),
            buildJsonArray {
                names.forEach { name ->
                    add(
                        buildJsonObject {
                            put("name", name)
                            put("description", "$name description")
                        },
                    )
                }
            },
        )

    @Test
    fun sendPrompt_populatesSessionContextAndLlamaCapabilities() =
        runTest {
            val port = CapturingAgentLoopPort { _ -> send(AgentLoopEvent.Finished) }
            val service = createService(agentLoopPort = port, provider = "LLAMA")

            service
                .sendPrompt(
                    prompt = "hello",
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            contextWindow = 4096,
                            systemPrompt = "system once",
                            chatId = ChatId("chat-1"),
                            userMessageId = MessageId("user-1"),
                            assistantMessageId = MessageId("assistant-1"),
                        ),
                ).toList()

            val config = requireNotNull(port.capturedConfig)
            assertEquals(4096u, config.io.contextWindowTokens)
            assertEquals("system once", config.io.systemPrompt)
            assertEquals(6u, config.agent.maxModelRequestsPerTurn)
            assertTrue(config.agent.enableLoopHooks)
            assertTrue(config.agent.enablePendingWorkGuard)
            assertFalse(config.capabilities.summarizationAllowed)
            assertTrue(config.capabilities.kvFixed)
            assertTrue(config.capabilities.lifecycleHooksSupported)
            assertEquals(
                mapOf(
                    "chat_id" to "chat-1",
                    "user_message_id" to "user-1",
                    "assistant_message_id" to "assistant-1",
                ),
                port.capturedSessionContext,
            )
        }

    @Test
    fun sendPrompt_populatesIncludedFilesAndHostedCapabilities() =
        runTest {
            val port = CapturingAgentLoopPort { _ -> send(AgentLoopEvent.Finished) }
            val service = createService(agentLoopPort = port, provider = "OPENAI")

            service
                .sendPrompt(
                    prompt = "hello",
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            includedFiles =
                                listOf(
                                    AgentIncludedFile(
                                        path = "src/Main.kt",
                                        tokenEstimate = 128u,
                                        content = "fun main() {}",
                                    ),
                                ),
                        ),
                ).toList()

            val config = requireNotNull(port.capturedConfig)
            assertTrue(config.capabilities.toolSchemaDeferralSupported)
            assertTrue(config.capabilities.fileBackedContextSupported)
            assertTrue(config.capabilities.promptCacheSupported)
            assertTrue(config.capabilities.lifecycleHooksSupported)
            assertTrue(requireNotNull(port.capturedSessionContext["included_files"]).contains("src/Main.kt"))
        }

    @Test
    fun sendPrompt_marksLargeHostedWindowsAsHighContext() =
        runTest {
            val port = CapturingAgentLoopPort { _ -> send(AgentLoopEvent.Finished) }
            val service = createService(agentLoopPort = port, provider = "OPENAI")

            service
                .sendPrompt(
                    prompt = "hello",
                    options = GenerationOptions(reasoningBudget = 0, contextWindow = 200_000),
                ).toList()

            val config = requireNotNull(port.capturedConfig)
            assertEquals(AgentLoopContextPolicyTier.HIGH_CONTEXT, config.capabilities.contextPolicyTier)
        }

    @Test
    fun completeModelTurn_restoresToolsFromDeferredContextIndex() =
        runTest {
            val inner = RecordingRustCompatibleService()
            val port =
                CapturingAgentLoopPort { host ->
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "system",
                        toolsSchemaJson = "[]",
                        allowToolCall = true,
                        toolContextIndexJson =
                            """{"advertised_tools":[{"name":"web_search"},{"name":"web_extract"}]}""",
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
                            activeTools = listOf(ToolDefinition.WEB_SEARCH, ToolDefinition.APPEND_BLOCKS),
                        ),
                ).toList()

            assertEquals(
                listOf(ToolDefinition.WEB_SEARCH.name, ToolDefinition.APPEND_BLOCKS.name),
                inner.lastAvailableTools.map { it.name },
            )
        }

    @Test
    fun completeModelTurn_malformedDeferredContextIndexFailsClosed() =
        runTest {
            val inner = RecordingRustCompatibleService()
            val port =
                CapturingAgentLoopPort { host ->
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "system",
                        toolsSchemaJson = "[]",
                        allowToolCall = true,
                        toolContextIndexJson = """{"advertised_tools":""",
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
                            activeTools = listOf(ToolDefinition.WEB_SEARCH),
                        ),
                ).toList()

            assertTrue(inner.lastAvailableTools.isEmpty())
        }

    @Test
    fun wireTools_stable_when_rust_toolsSchemaJson_grows_across_model_turns() =
        runTest {
            val inner = RecordingRustCompatibleService()
            val port =
                CapturingAgentLoopPort { host ->
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "system",
                        toolsSchemaJson = serializeToolNames(listOf("web_search")),
                        allowToolCall = true,
                    )
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "system",
                        toolsSchemaJson =
                            serializeToolNames(listOf("web_search", "append_blocks", "web_extract")),
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
                            activeTools =
                                listOf(
                                    ToolDefinition.WEB_SEARCH,
                                    ToolDefinition.APPEND_BLOCKS,
                                    ToolDefinition.WEB_EXTRACT,
                                ),
                        ),
                ).toList()

            assertEquals(2, inner.completeSingleTurnCallCount)
            val firstTools = inner.toolsPerInvocation[0].map { it.name }
            val secondTools = inner.toolsPerInvocation[1].map { it.name }
            assertEquals(firstTools, secondTools)
            assertEquals(
                listOf("append_blocks", "web_extract", "web_search"),
                firstTools.sorted(),
            )
        }

    @Test
    fun completeModelTurn_keepsFullToolListWhenAllowToolCallFalseForCacheStability() =
        runTest {
            val inner = RecordingRustCompatibleService()
            val port =
                CapturingAgentLoopPort { host ->
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "system",
                        toolsSchemaJson = serializeToolNames(listOf("web_search", "append_blocks")),
                        allowToolCall = false,
                    )
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port, inner = inner)

            service
                .sendPrompt(
                    prompt = "no tools this turn",
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            activeTools = listOf(ToolDefinition.WEB_SEARCH, ToolDefinition.APPEND_BLOCKS),
                        ),
                ).toList()

            assertEquals(
                listOf("web_search", "append_blocks"),
                inner.lastAvailableTools.map { it.name },
            )
        }

    @Test
    fun completeModelTurn_forwardsContextSidecarsToProviderPrompt() =
        runTest {
            val inner = RecordingRustCompatibleService()
            val port =
                CapturingAgentLoopPort { host ->
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "base system",
                        toolsSchemaJson = "[]",
                        allowToolCall = false,
                        contextReferences =
                            listOf(
                                AgentContextReference(
                                    id = "file-outline-src/Main.kt",
                                    kind = "FILE_OUTLINE",
                                    title = "outline:src/Main.kt",
                                    byteLen = 10u,
                                    tokenEstimate = 2u,
                                    preview = "fun main",
                                    retrievalHint = "read_context_reference(\"file-outline-src/Main.kt\")",
                                ),
                            ),
                        fileContextIndexJson = """{"root_ref":"file-context-root"}""",
                    )
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port, inner = inner)

            service.sendPrompt("sidecars").toList()

            assertTrue(requireNotNull(inner.lastContextSidecars).contains("Context management sidecars"))
            assertTrue(requireNotNull(inner.lastContextSidecars).contains("file-outline-src/Main.kt"))
            assertTrue(requireNotNull(inner.lastContextSidecars).contains("File context index JSON"))
        }

    @Test
    fun executeTools_runsCallsInParallel() =
        runTest {
            val started = AtomicInteger(0)
            val bothStarted = CompletableDeferred<Unit>()
            val executor =
                object : ToolExecutorPort {
                    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
                        if (started.incrementAndGet() == 2) {
                            bothStarted.complete(Unit)
                        }
                        withTimeout(1_000) { bothStarted.await() }
                        return ToolExecutionResult(toolName = request.toolName, resultJson = """{"ok":true}""")
                    }
                }
            lateinit var results: List<AgentToolResult>
            val port =
                CapturingAgentLoopPort { host ->
                    results =
                        host.executeTools(
                            listOf(
                                AgentToolCall("call-1", ToolDefinition.WEB_SEARCH.name, """{"query":"a"}"""),
                                AgentToolCall("call-2", ToolDefinition.WEB_EXTRACT.name, """{"urls":["https://example.com"]}"""),
                            ),
                        )
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port, toolExecutor = executor)

            service.sendPrompt("parallel").toList()

            assertEquals(2, started.get())
            assertEquals(listOf("call-1", "call-2"), results.map { it.callId })
            assertTrue(results.none { it.softFailure })
        }

    @Test
    fun executeTools_translatesThrowableToSoftFailureResult() =
        runTest {
            val executor =
                object : ToolExecutorPort {
                    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
                        error("boom")
                    }
                }
            lateinit var result: AgentToolResult
            val port =
                CapturingAgentLoopPort { host ->
                    result =
                        host
                            .executeTools(
                                listOf(AgentToolCall("call-1", ToolDefinition.WEB_SEARCH.name, """{"query":"a"}""")),
                            ).single()
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port, toolExecutor = executor)

            service.sendPrompt("tool failure").toList()

            assertEquals("call-1", result.callId)
            assertTrue(result.softFailure)
            assertTrue(result.resultJson.contains("tool_execution_failed"))
            assertTrue(result.resultJson.contains("boom"))
        }

    @Test
    fun summarize_usesHostedFastWhenContextWindowFits() =
        runTest {
            val summaryService =
                RecordingInferenceService(
                    events = listOf(InferenceEvent.PartialResponse("summary text", ModelType.FAST)),
                )
            val factory = RecordingInferenceFactory(summaryService)
            lateinit var summary: AgentSummaryResult
            val port =
                CapturingAgentLoopPort { host ->
                    summary =
                        host.summarize(
                            AgentSummaryRequest(
                                messagesToSummarize =
                                    listOf(
                                        AgentLoopMessage("u1", Role.USER, "Question one"),
                                        AgentLoopMessage("a1", Role.ASSISTANT, "Answer one"),
                                        AgentLoopMessage("u2", Role.USER, "Question two"),
                                        AgentLoopMessage("a2", Role.ASSISTANT, "Answer two"),
                                    ),
                                instruction = "summarize preserving decisions",
                                lastMessageId = "a2",
                                focusInstruction = "focus on tools",
                                historyRef = "history-u1-a2",
                                stateLedgerSnapshotJson = """{"objective":"ship"}""",
                            ),
                        )
                    send(AgentLoopEvent.Finished)
                }
            val service =
                createService(
                    agentLoopPort = port,
                    inferenceFactoryProvider = Provider { factory },
                    resolveCompactionSummaryModel =
                        resolveCompactionSummaryModel(
                            fastConfig = hostedFastConfig(contextWindow = 200_000),
                        ),
                )

            service.sendPrompt("summarize").toList()

            assertEquals(ModelType.FAST, factory.requestedModelType)
            assertEquals("summary text", summary.summaryText)
            assertEquals("a2", summary.lastMessageId)
            assertTrue(summaryService.prompts.single().contains("Question one"))
            assertTrue(summaryService.prompts.single().contains("summarize preserving decisions"))
            assertTrue(summaryService.prompts.single().contains("focus on tools"))
            assertTrue(summaryService.prompts.single().contains("history-u1-a2"))
            assertTrue(summaryService.prompts.single().contains("ship"))
        }

    @Test
    fun summarize_usesActiveTurnWhenFastIsLocal() =
        runTest {
            val summaryService =
                RecordingInferenceService(
                    events = listOf(InferenceEvent.PartialResponse("summary text", ModelType.THINKING)),
                )
            val factory = RecordingInferenceFactory(summaryService)
            lateinit var summary: AgentSummaryResult
            val port =
                CapturingAgentLoopPort { host ->
                    summary =
                        host.summarize(
                            AgentSummaryRequest(
                                messagesToSummarize =
                                    listOf(
                                        AgentLoopMessage("u1", Role.USER, "Question one"),
                                        AgentLoopMessage("a1", Role.ASSISTANT, "Answer one"),
                                    ),
                                instruction = "summarize",
                                lastMessageId = "a1",
                            ),
                        )
                    send(AgentLoopEvent.Finished)
                }
            val service =
                createService(
                    agentLoopPort = port,
                    inferenceFactoryProvider = Provider { factory },
                    resolveCompactionSummaryModel =
                        resolveCompactionSummaryModel(
                            fastConfig =
                                hostedFastConfig(contextWindow = 200_000).copy(
                                    id = LocalModelConfigurationId("local-fast"),
                                    isLocal = true,
                                ),
                        ),
                )

            service.sendPrompt("summarize").toList()

            assertEquals(ModelType.THINKING, factory.requestedModelType)
            assertEquals("summary text", summary.summaryText)
        }

    @Test
    fun agentEvents_propagateCompactionRepairAndToolSideEffects() =
        runTest {
            val port =
                CapturingAgentLoopPort { host ->
                    host.onToolResult(
                        toolName = ToolDefinition.WEB_SEARCH.name,
                        argumentsJson = """{"query":"android"}""",
                        resultJson = """{"results":[{"title":"Android","url":"https://example.com","content":"Result","score":0.9}]}""",
                    )
                    send(
                        AgentLoopEvent.CompactionApplied(
                            tier = "FIFO",
                            tokensBefore = 100,
                            tokensAfter = 60,
                            trigger = "PREFLIGHT",
                        ),
                    )
                    send(AgentLoopEvent.ToolRepaired(originalName = "web_serch", resolvedName = "web_search", confidence = 0.9f))
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port)

            val events =
                service
                    .sendPrompt(
                        prompt = "events",
                        options =
                            GenerationOptions(
                                reasoningBudget = 0,
                                assistantMessageId = MessageId("assistant-1"),
                            ),
                    ).toList()

            assertNotNull(events.filterIsInstance<InferenceEvent.WebSearchResults>().single())
            assertEquals("FIFO", events.filterIsInstance<InferenceEvent.CompactionApplied>().single().tier)
            assertEquals("web_search", events.filterIsInstance<InferenceEvent.ToolRepaired>().single().resolvedName)
        }

    @Test
    fun `FAAL-AR-03 append_blocks with fullDocument emits Artifacts`() =
        runTest {
            val port =
                CapturingAgentLoopPort { host ->
                    host.onToolResult(
                        toolName = ToolDefinition.APPEND_BLOCKS.name,
                        argumentsJson = """{"artifactId":"art-done-1"}""",
                        resultJson =
                            """
                            {
                              "status":"success",
                              "isComplete":true,
                              "title":"Done Doc",
                              "fullDocument":{"version":"1.0","metadata":{"title":"Done Doc"},"blocks":[{"type":"paragraph","text":"Done"}]}
                            }
                            """.trimIndent(),
                    )
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port)

            val events =
                service
                    .sendPrompt(
                        prompt = "artifact",
                        options = GenerationOptions(reasoningBudget = 0),
                    ).toList()

            val artifactEvent = events.filterIsInstance<InferenceEvent.Artifacts>().single()
            assertEquals(1, artifactEvent.artifacts.size, "FAAL-AR-03 artifacts size")
            assertTrue(artifactEvent.artifacts[0].content.contains("Done"), "FAAL-AR-03 content")
        }

    @Test
    fun AOC_03_rust_loop_dual_completeModelTurn_forwards_sidecars() =
        runTest {
            val inner = RecordingRustCompatibleService()
            val port =
                CapturingAgentLoopPort { host ->
                    host.completeModelTurn(
                        sessionMessages =
                            listOf(
                                AgentLoopMessage(id = "u1", role = Role.USER, content = "Search for cats"),
                            ),
                        systemPrompt = "You are a helpful assistant.",
                        toolsSchemaJson = "[]",
                        allowToolCall = true,
                        stateLedgerJson = """{"turn":1}""",
                    )
                    host.completeModelTurn(
                        sessionMessages =
                            listOf(
                                AgentLoopMessage(id = "u1", role = Role.USER, content = "Search for cats"),
                                AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "tool pending"),
                                AgentLoopMessage(
                                    id = "t1",
                                    role = Role.USER,
                                    content = """{"results":[]}""",
                                    metadataJson = """{"call_id":"call_1"}""",
                                ),
                            ),
                        systemPrompt = "You are a helpful assistant.",
                        toolsSchemaJson = "[]",
                        allowToolCall = false,
                        stateLedgerJson = """{"turn":2}""",
                    )
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port, inner = inner)

            service.sendPrompt("run tool loop", options = GenerationOptions(reasoningBudget = 0)).toList()

            assertEquals(2, inner.completeSingleTurnCallCount)
            assertEquals(true, inner.lastContextSidecars.orEmpty().contains("State ledger"))
            assertEquals(
                Role.USER,
                inner.sessionMessagesByCall
                    .first()
                    .first()
                    .role,
            )
        }

    @Test
    fun completeModelTurn_forwardsMonotonicTelemetrySequenceIndex() =
        runTest {
            val inner = RecordingRustCompatibleService()
            val port =
                CapturingAgentLoopPort { host ->
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "system",
                        toolsSchemaJson = "[]",
                        allowToolCall = true,
                    )
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "system",
                        toolsSchemaJson = "[]",
                        allowToolCall = false,
                    )
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port, inner = inner)

            service.sendPrompt("tool loop").toList()

            assertEquals(listOf(0, 1), inner.telemetrySequenceIndices)
        }

    @Test
    fun `FB-K-007 RustLoop supports multiple complete model callbacks`() =
        runTest {
            val inner =
                RecordingRustCompatibleService(
                    responses =
                        listOf(
                            AgentModelTurnResult("first", emptyList(), AgentFinishReason.Stop),
                            AgentModelTurnResult("second", emptyList(), AgentFinishReason.Stop),
                        ),
                )
            val port =
                CapturingAgentLoopPort { host ->
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "system",
                        toolsSchemaJson = "[]",
                        allowToolCall = true,
                    )
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "system",
                        toolsSchemaJson = "[]",
                        allowToolCall = true,
                    )
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port, inner = inner)

            service.sendPrompt("continue").toList()

            assertEquals(2, inner.completeSingleTurnCallCount)
            assertEquals("continue", requireNotNull(port.capturedInitialMessages).last().content)
        }

    @Test
    fun `FB-K-008 multi callback text tokens emit partial responses before finished`() =
        runTest {
            val port =
                CapturingAgentLoopPort { _ ->
                    send(AgentLoopEvent.TextToken("first"))
                    send(AgentLoopEvent.TextToken("second"))
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port)

            val events = service.sendPrompt("stream").toList()

            val chunks = events.filterIsInstance<InferenceEvent.PartialResponse>().map { it.chunk }
            assertEquals(listOf("first", "second"), chunks)
            assertEquals(1, events.count { it is InferenceEvent.Finished })
            assertTrue(events.last() is InferenceEvent.Finished)
        }

    @Test
    fun sendPrompt_retainsAssistantTextInSessionHistoryForFollowupTurns() =
        runTest {
            val port =
                CapturingAgentLoopPort { _ ->
                    send(AgentLoopEvent.TextToken("remembered answer"))
                    send(AgentLoopEvent.Finished)
                }
            val service = createService(agentLoopPort = port)

            service.sendPrompt("first").toList()
            service.sendPrompt("follow up").toList()

            val messages = requireNotNull(port.capturedInitialMessages)
            assertEquals(
                listOf(Role.USER, Role.ASSISTANT, Role.USER),
                messages.map { it.role },
            )
            assertEquals("first", messages[0].content)
            assertEquals("remembered answer", messages[1].content)
            assertEquals("follow up", messages[2].content)
        }

    @Test
    fun sendPrompt_prefersCommittedProviderTranscriptOverDbHistoryForCacheStability() =
        runTest {
            val transcriptStore = InMemoryCommittedInferenceTranscriptStore()
            val chatId = ChatId("cache-chat")
            val firstInner =
                RecordingRustCompatibleService(
                    responses =
                        listOf(
                            AgentModelTurnResult(
                                assistantText = "final answer",
                                toolCalls = emptyList(),
                                finishReason = AgentFinishReason.Stop,
                                providerReasoningContent = "encrypted-reasoning",
                            ),
                        ),
                )
            val firstPort =
                CapturingAgentLoopPort { host ->
                    host.completeModelTurn(
                        sessionMessages =
                            listOf(
                                AgentLoopMessage(id = "u1", role = Role.USER, content = "first"),
                                AgentLoopMessage(
                                    id = "a-tool",
                                    role = Role.ASSISTANT,
                                    content = "",
                                    metadataJson =
                                        """
                                        {"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"query\":\"cats\"}"}]}
                                        """.trimIndent(),
                                ),
                                AgentLoopMessage(
                                    id = "tool-result",
                                    role = Role.USER,
                                    content = """{"results":[{"title":"Cat"}]}""",
                                    metadataJson = """{"call_id":"call_1"}""",
                                ),
                            ),
                        systemPrompt = "system",
                        toolsSchemaJson = "[]",
                        allowToolCall = false,
                    )
                    send(AgentLoopEvent.Finished)
                }
            val firstService =
                createService(
                    agentLoopPort = firstPort,
                    inner = firstInner,
                    committedInferenceTranscripts = transcriptStore,
                )

            firstService
                .sendPrompt(
                    prompt = "first",
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            chatId = chatId,
                            userMessageId = MessageId("u1"),
                            assistantMessageId = MessageId("a1"),
                        ),
                ).toList()

            val secondPort =
                CapturingAgentLoopPort {
                    send(AgentLoopEvent.Finished)
                }
            val secondService =
                createService(
                    agentLoopPort = secondPort,
                    committedInferenceTranscripts = transcriptStore,
                )
            secondService.setHistory(
                listOf(
                    ChatMessage(role = Role.USER, content = "first"),
                    ChatMessage(role = Role.ASSISTANT, content = "final answer"),
                ),
            )

            secondService
                .sendPrompt(
                    prompt = "follow up",
                    options = GenerationOptions(reasoningBudget = 0, chatId = chatId),
                ).toList()

            val initialMessages = requireNotNull(secondPort.capturedInitialMessages)
            assertEquals("first", initialMessages[0].content)
            assertTrue(initialMessages[1].metadataJson.contains("raw_tool_calls"))
            assertEquals("""{"call_id":"call_1"}""", initialMessages[2].metadataJson)
            assertTrue(initialMessages[3].metadataJson.contains("provider_reasoning"))
            assertEquals("follow up", initialMessages.last().content)
        }

    @Test
    fun `buildProviderTranscript filters out engine-internal sidecar messages`() =
        runTest {
            val transcriptStore = InMemoryCommittedInferenceTranscriptStore()
            val innerService =
                RecordingRustCompatibleService(
                    responses =
                        listOf(
                            AgentModelTurnResult(
                                assistantText = "final answer",
                                toolCalls = emptyList(),
                                finishReason = AgentFinishReason.Stop,
                            ),
                        ),
                )
            val port =
                CapturingAgentLoopPort { host ->
                    host.completeModelTurn(
                        sessionMessages =
                            listOf(
                                AgentLoopMessage(
                                    id = "u1",
                                    role = Role.USER,
                                    content = "regular user prompt",
                                ),
                                AgentLoopMessage(
                                    id = "budget",
                                    role = Role.USER,
                                    content = """{"_agent":{"tool_calls_remaining":1}}""",
                                    metadataJson = """{"agent_tool_budget":true}""",
                                ),
                                AgentLoopMessage(
                                    id = "nudge",
                                    role = Role.USER,
                                    content = """{"tools_blocked_nudge":true}""",
                                    metadataJson = """{"tools_blocked_nudge":true}""",
                                ),
                                AgentLoopMessage(
                                    id = "parse-failure",
                                    role = Role.USER,
                                    content = """{"envelope_parse_failure":true}""",
                                    metadataJson = """{"envelope_parse_failure":true}""",
                                ),
                                AgentLoopMessage(
                                    id = "synthetic-agent",
                                    role = Role.USER,
                                    content = """{"_agent":{"some":"guidance"}}""",
                                ),
                            ),
                        systemPrompt = "",
                        toolsSchemaJson = "[]",
                        allowToolCall = false,
                    )
                    send(AgentLoopEvent.Finished)
                }
            val service =
                createService(
                    agentLoopPort = port,
                    inner = innerService,
                    committedInferenceTranscripts = transcriptStore,
                )

            service
                .sendPrompt(
                    prompt = "start",
                    options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-1")),
                ).toList()

            val committed = transcriptStore.get(ChatId("chat-1"))
            assertNotNull(committed)
            assertEquals(2, committed!!.size)
            assertEquals("regular user prompt", committed[0].content)
            assertEquals("final answer", committed[1].content)
        }

    @Test
    fun `web search and X tools are marked disabled in sidecars when offline and fail fast`() =
        runTest {
            val deviceEnvironment = mockk<DeviceEnvironmentRepositoryPort>()
            coEvery { deviceEnvironment.isNetworkAvailable() } returns false

            val innerService =
                RecordingRustCompatibleService(
                    responses =
                        listOf(
                            AgentModelTurnResult(
                                assistantText = "let's search",
                                toolCalls = emptyList(),
                                finishReason = AgentFinishReason.Stop,
                            ),
                        ),
                )

            val port =
                CapturingAgentLoopPort { host ->
                    // 1. Verify buildContextSidecars appends the Offline status
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "",
                        toolsSchemaJson = "[]",
                        allowToolCall = true,
                    )

                    // 2. Verify executeTools fails fast for web_search
                    val calls =
                        listOf(
                            AgentToolCall("call-1", "web_search", """{"query":"weather"}"""),
                        )
                    val toolResults = host.executeTools(calls)
                    val capturedToolResult = toolResults.firstOrNull()

                    assertNotNull(capturedToolResult)
                    assertTrue(capturedToolResult!!.softFailure)
                    assertTrue(capturedToolResult.resultJson.contains("network_unavailable"))

                    send(AgentLoopEvent.Finished)
                }

            val service =
                createService(
                    agentLoopPort = port,
                    inner = innerService,
                    toolExecutor =
                        object : ToolExecutorPort {
                            override suspend fun execute(request: ToolCallRequest): ToolExecutionResult =
                                throw AssertionError("Should not execute tool when offline")
                        },
                    deviceEnvironmentRepository = deviceEnvironment,
                )

            service.sendPrompt("start").toList()

            assertNotNull(innerService.lastContextSidecars)
            assertTrue(innerService.lastContextSidecars!!.contains("Internet status: Offline"))
            assertTrue(innerService.lastContextSidecars!!.contains("disabled due to no internet access: web_search"))
        }

    @Test
    fun `web search and X tools dynamic connectivity state transitions`() =
        runTest {
            val deviceEnvironment = mockk<DeviceEnvironmentRepositoryPort>()
            val networkStates = mutableListOf(true, false, true)
            coEvery { deviceEnvironment.isNetworkAvailable() } answers {
                networkStates.removeFirst()
            }

            val sidecarsList = mutableListOf<String?>()
            val innerService =
                object : RustCompatibleInferenceService {
                    override val baseUrl: String? = null

                    override fun sendPrompt(
                        prompt: String,
                        closeConversation: Boolean,
                    ) = flowOf(InferenceEvent.Finished(ModelType.THINKING))

                    override fun sendPrompt(
                        prompt: String,
                        options: GenerationOptions,
                        closeConversation: Boolean,
                    ) = flowOf(InferenceEvent.Finished(ModelType.THINKING))

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
                        sidecarsList.add(contextSidecars)
                        return AgentModelTurnResult(
                            assistantText = "turn",
                            toolCalls = emptyList(),
                            finishReason = AgentFinishReason.Stop,
                        )
                    }
                }

            val port =
                CapturingAgentLoopPort { host ->
                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "",
                        toolsSchemaJson = "[]",
                        allowToolCall = true,
                    )

                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "",
                        toolsSchemaJson = "[]",
                        allowToolCall = true,
                    )

                    host.completeModelTurn(
                        sessionMessages = emptyList(),
                        systemPrompt = "",
                        toolsSchemaJson = "[]",
                        allowToolCall = true,
                    )

                    send(AgentLoopEvent.Finished)
                }

            val service =
                createService(
                    agentLoopPort = port,
                    inner = innerService,
                    deviceEnvironmentRepository = deviceEnvironment,
                )

            service.sendPrompt("start").toList()

            assertEquals(3, sidecarsList.size)
            assertNull(sidecarsList[0])
            assertNotNull(sidecarsList[1])
            assertTrue(sidecarsList[1]!!.contains("Internet status: Offline"))
            assertNotNull(sidecarsList[2])
            assertTrue(sidecarsList[2]!!.contains("Internet status: Online. Search and X tools are now re-enabled."))
        }

    private fun createService(
        agentLoopPort: AgentLoopPort,
        inner: RustCompatibleInferenceService = RecordingRustCompatibleService(),
        toolExecutor: ToolExecutorPort =
            object : ToolExecutorPort {
                override suspend fun execute(request: ToolCallRequest): ToolExecutionResult =
                    ToolExecutionResult(toolName = request.toolName, resultJson = """{"ok":true}""")
            },
        provider: String = "OPENAI",
        inferenceFactoryProvider: Provider<InferenceFactoryPort> =
            Provider {
                RecordingInferenceFactory(RecordingInferenceService())
            },
        resolveCompactionSummaryModel: ResolveCompactionSummaryModelUseCase =
            resolveCompactionSummaryModel(fastConfig = null),
        committedInferenceTranscripts: InMemoryCommittedInferenceTranscriptStore =
            InMemoryCommittedInferenceTranscriptStore(),
        deviceEnvironmentRepository: DeviceEnvironmentRepositoryPort = NoopDeviceEnvironmentRepository,
    ): RustLoopInferenceService =
        RustLoopInferenceService(
            inner = inner,
            agentLoopPort = agentLoopPort,
            toolExecutor = toolExecutor,
            loggingPort = logger,
            provider = provider,
            modelType = ModelType.THINKING,
            inferenceFactoryProvider = inferenceFactoryProvider,
            rollingSummarizer = RollingSummarizer(logger, JTokkitTokenCounter),
            resolveCompactionSummaryModel = resolveCompactionSummaryModel,
            committedInferenceTranscripts = committedInferenceTranscripts,
            deviceEnvironmentRepository = deviceEnvironmentRepository,
            embeddingEnginePort = com.browntowndev.enclave.domain.port.inference.Bm25OnlyEmbeddingEnginePort,
        )

    private fun resolveCompactionSummaryModel(fastConfig: ActiveModelConfiguration?): ResolveCompactionSummaryModelUseCase {
        val activeModelProvider = mockk<ActiveModelProviderPort>()
        coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns fastConfig
        return ResolveCompactionSummaryModelUseCase(activeModelProvider, JTokkitTokenCounter)
    }

    private fun hostedFastConfig(contextWindow: Int): ActiveModelConfiguration =
        ActiveModelConfiguration(
            id = ApiModelConfigurationId("fast-api"),
            isLocal = false,
            name = "Fast API",
            modelId = "fast-model",
            systemPrompt = null,
            contextWindow = contextWindow,
            thinkingEnabled = false,
            temperature = null,
            topK = null,
            topP = null,
            maxTokens = null,
            minP = null,
            repetitionPenalty = null,
        )

    private class CapturingAgentLoopPort(
        private val behavior: suspend ProducerScope<AgentLoopEvent>.(AgentLoopHostCallbacks) -> Unit,
    ) : AgentLoopPort {
        var capturedConfig: AgentLoopConfig? = null
        var capturedSessionContext: Map<String, String> = emptyMap()
        var capturedInitialMessages: List<ChatMessage>? = null

        override fun execute(
            initialMessages: List<ChatMessage>,
            config: AgentLoopConfig,
            sessionContext: Map<String, String>,
            host: AgentLoopHostCallbacks,
        ): Flow<AgentLoopEvent> =
            channelFlow {
                capturedInitialMessages = initialMessages
                capturedConfig = config
                capturedSessionContext = sessionContext
                behavior(host)
            }
    }

    private class RecordingRustCompatibleService(
        private val responses: List<AgentModelTurnResult> = emptyList(),
    ) : RustCompatibleInferenceService {
        var lastSystemPrompt: String? = null
        var lastContextSidecars: String? = null
        var lastAvailableTools: List<ToolDefinition> = emptyList()
        var completeSingleTurnCallCount = 0
        val toolsPerInvocation = mutableListOf<List<ToolDefinition>>()
        val telemetrySequenceIndices = mutableListOf<Int>()
        val sessionMessagesByCall = mutableListOf<List<AgentLoopMessage>>()

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
            completeSingleTurnCallCount += 1
            sessionMessagesByCall.add(sessionMessages)
            toolsPerInvocation.add(availableTools)
            lastSystemPrompt = systemPrompt
            lastContextSidecars = contextSidecars
            lastAvailableTools = availableTools
            telemetrySequenceIndices.add(options.telemetrySequenceIndex)
            responses.getOrNull(completeSingleTurnCallCount - 1)?.let { return it }
            return AgentModelTurnResult(
                assistantText = "done",
                toolCalls = emptyList(),
                finishReason = AgentFinishReason.Stop,
            )
        }
    }

    private class RecordingInferenceFactory(
        private val service: LlmInferencePort,
    ) : InferenceFactoryPort {
        var requestedModelType: ModelType? = null

        override suspend fun <T> withInferenceService(
            modelType: ModelType,
            block: suspend (LlmInferencePort) -> T,
        ): T {
            requestedModelType = modelType
            return block(service)
        }

        override suspend fun <T> withRawApiServiceForConfig(
            configId: ApiModelConfigurationId,
            block: suspend (LlmInferencePort) -> T,
        ): T = block(service)
    }

    private class RecordingInferenceService(
        private val events: List<InferenceEvent> = emptyList(),
    ) : LlmInferencePort {
        val prompts = mutableListOf<String>()

        override fun sendPrompt(
            prompt: String,
            closeConversation: Boolean,
        ): Flow<InferenceEvent> {
            prompts += prompt
            return flowOf(*events.toTypedArray())
        }

        override fun sendPrompt(
            prompt: String,
            options: GenerationOptions,
            closeConversation: Boolean,
        ): Flow<InferenceEvent> {
            prompts += prompt
            return flowOf(*events.toTypedArray())
        }

        override suspend fun setHistory(messages: List<ChatMessage>) {}

        override suspend fun closeSession() {}
    }

    private class RecordingLogger : LoggingPort {
        override fun debug(
            tag: String,
            message: String,
        ) {}

        override fun info(
            tag: String,
            message: String,
        ) {}

        override fun warning(
            tag: String,
            message: String,
        ) {}

        override fun error(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {}
    }
}
