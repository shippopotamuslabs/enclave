package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopConfig
import com.browntowndev.enclave.domain.model.inference.AgentLoopHostCallbacks
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.inference.AgentLoopEvent
import com.browntowndev.enclave.domain.port.inference.AgentLoopPort
import com.browntowndev.enclave.domain.port.inference.CommittedInferencePromptPort
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.NoopCommittedInferencePromptPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.usecase.chat.InMemoryCommittedInferencePromptStore
import com.browntowndev.enclave.domain.usecase.chat.RollingSummarizer
import com.browntowndev.enclave.domain.usecase.inference.ResolveCompactionSummaryModelUseCase
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.domain.util.JTokkitTokenCounter
import com.browntowndev.enclave.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.inject.Provider

class RustLoopVolatileTurnContextCommitTest {
    private val logger = NoopLogger()

    @Test
    fun committed_inference_prompt_store_receives_bare_user_content_on_finished() =
        runTest {
            val committedPrompts = InMemoryCommittedInferencePromptStore()
            val userMessageId = MessageId("u1")
            val volatilePrefix = "# RELEVANT WEB SEARCH RESULTS:\nresult-text-abc"
            val expectedContent = "search query"
            val port = FakeAgentLoopPort {
                send(AgentLoopEvent.Finished)
            }
            val service =
                createService(
                    agentLoopPort = port,
                    committedInferencePrompts = committedPrompts,
                )

            service.sendPrompt(
                prompt = "search query",
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        userMessageId = userMessageId,
                        volatileTurnContext = volatilePrefix,
                    ),
            ).toList()

            assertEquals(expectedContent, committedPrompts.get(userMessageId))
        }

    // ---------------------------------------------------------------------------
    // CSPC-01 — N+1 turn, no volatile context, wire user content byte-identical
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_01_no_volatile_context_turn2_user_content_byte_identical() =
        runTest {
            val capturing = CapturingRustCompatibleService()

            // Turn 1: FakeAgentLoopPort calls completeModelTurn with sessionMessages
            val port1 = FakeAgentLoopPort { host ->
                host.completeModelTurn(
                    sessionMessages = listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                    systemPrompt = "",
                    toolsSchemaJson = "[]",
                    allowToolCall = false,
                )
                send(AgentLoopEvent.Finished)
            }
            val service1 = createService(agentLoopPort = port1, inner = capturing)

            service1.sendPrompt(
                prompt = "Hello",
                options = GenerationOptions(reasoningBudget = 0),
            ).toList()

            val turn1SessionMessages = capturing.capturedTurnArgs.last()
            assertEquals("Hello", turn1SessionMessages[0].content, "CSPC-01 turn 1: sessionMessages[0].content")

            // Turn 2: FakeAgentLoopPort replays sessionMessages with history
            val port2 = FakeAgentLoopPort { host ->
                host.completeModelTurn(
                    sessionMessages = listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "World"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Hello"),
                    ),
                    systemPrompt = "",
                    toolsSchemaJson = "[]",
                    allowToolCall = false,
                )
                send(AgentLoopEvent.Finished)
            }
            val service2 = createService(agentLoopPort = port2, inner = capturing)

            service2.sendPrompt(
                prompt = "Hello",
                options = GenerationOptions(reasoningBudget = 0),
            ).toList()

            val turn2SessionMessages = capturing.capturedTurnArgs.last()
            assertEquals(
                "Hello",
                turn2SessionMessages[0].content,
                "CSPC-01 turn 2: sessionMessages[0].content must equal turn 1 sessionMessages[0].content",
            )
            assertEquals(
                turn1SessionMessages[0].content,
                turn2SessionMessages[0].content,
                "CSPC-01: sessionMessages[0].content must be byte-identical across turns",
            )
        }

    // ---------------------------------------------------------------------------
    // CSPC-02 — N+1 turn with mid-turn tool calls — wire user content byte-identical
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_02_mid_turn_tool_calls_do_not_corrupt_user_content_in_next_turn() =
        runTest {
            val capturing = CapturingRustCompatibleService()

            val turn2SessionMessages = listOf(
                AgentLoopMessage(id = "u1", role = Role.USER, content = "What time is it?"),
                AgentLoopMessage(
                    id = "a1",
                    role = Role.ASSISTANT,
                    content = "",
                    metadataJson = """{"raw_tool_calls":[{"id":"c1","name":"web_search","arguments_json":"{}"}]}""",
                ),
                AgentLoopMessage(
                    id = "t1",
                    role = Role.USER,
                    content = """{"result":"3pm"}""",
                    metadataJson = """{"call_id":"c1"}""",
                ),
                AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "It is 3pm"),
                AgentLoopMessage(id = "u2", role = Role.USER, content = "Tell me more"),
            )

            val port = FakeAgentLoopPort { host ->
                host.completeModelTurn(
                    sessionMessages = turn2SessionMessages,
                    systemPrompt = "",
                    toolsSchemaJson = "[]",
                    allowToolCall = false,
                )
                send(AgentLoopEvent.Finished)
            }
            val service = createService(agentLoopPort = port, inner = capturing)

            service.sendPrompt(
                prompt = "Tell me more",
                options = GenerationOptions(reasoningBudget = 0),
            ).toList()

            val capturedMessages = capturing.capturedTurnArgs.last()
            assertEquals(
                "Tell me more",
                capturedMessages.last().content,
                "CSPC-02: sessionMessages.last().content must be 'Tell me more' (no RAG prefix inserted)",
            )
            assertEquals(
                Role.USER,
                capturedMessages.last().role,
                "CSPC-02: sessionMessages.last().role must be USER",
            )
            assertEquals(
                "What time is it?",
                capturedMessages[0].content,
                "CSPC-02: sessionMessages[0].content must be unchanged",
            )
        }

    // ---------------------------------------------------------------------------
    // CSPC-03a — Turn 1 fires web pre-retrieval — turn 2 wire reproduces RAG prefix
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_03a_web_rag_prefix_stored_in_rust_history_turn2_replays_identical_bytes() =
        runTest {
            val providerCapture = captureOpenAiParams()

            val webRagPrefix = "# RELEVANT WEB SEARCH RESULTS:\nresult-text-abc"
            val barePrompt = "search query"

            val port1 = FakeAgentLoopPort { host ->
                host.completeModelTurn(
                    sessionMessages = listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = barePrompt),
                    ),
                    systemPrompt = "",
                    toolsSchemaJson = "[]",
                    allowToolCall = false,
                )
                send(AgentLoopEvent.Finished)
            }
            val service1 = createService(agentLoopPort = port1, inner = providerCapture.service)

            service1.sendPrompt(
                prompt = barePrompt,
                options = GenerationOptions(
                    reasoningBudget = 0,
                    volatileTurnContext = webRagPrefix,
                ),
            ).toList()

            assertEquals(
                barePrompt,
                port1.capturedInitialMessages.last().content,
                "CSPC-03a turn 1: Rust history stores bare user prompt without RAG prefix",
            )
            assertTrue(
                providerCapture.params.last().messages().any { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText().contains("RELEVANT WEB SEARCH RESULTS")
                },
                "CSPC-03a turn 1: provider wire must include pre-retrieval system sidecar",
            )

            val port2 = FakeAgentLoopPort { host ->
                host.completeModelTurn(
                    sessionMessages = listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = barePrompt),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "some answer"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "follow up"),
                    ),
                    systemPrompt = "",
                    toolsSchemaJson = "[]",
                    allowToolCall = false,
                )
                send(AgentLoopEvent.Finished)
            }
            val service2 = createService(agentLoopPort = port2, inner = providerCapture.service)

            service2.sendPrompt(
                prompt = "follow up",
                options = GenerationOptions(reasoningBudget = 0, volatileTurnContext = null),
            ).toList()

            val turn2SessionMessages = providerCapture.params.last().messages()
            assertEquals(
                barePrompt,
                turn2SessionMessages.first { it.isUser() }.asUser().content().asText(),
                "CSPC-03a turn 2: first user wire content stays bare for cache prefix stability",
            )
            assertTrue(
                turn2SessionMessages.any { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText().contains("RELEVANT WEB SEARCH RESULTS")
                },
                "CSPC-03a turn 2: frozen pre-retrieval sidecar replays on provider wire",
            )
        }

    // ---------------------------------------------------------------------------
    // CSPC-03b — Turn 1 fires X pre-retrieval — turn 2 replays same RAG prefix
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_03b_x_rag_prefix_stored_in_rust_history_turn2_replays_identical_bytes() =
        runTest {
            val providerCapture = captureOpenAiParams()
            val xRagPrefix = "# RELEVANT X SEARCH RESULTS:\ntweet-data-xyz"
            val barePrompt = "x query"

            val port1 = FakeAgentLoopPort { host ->
                host.completeModelTurn(
                    sessionMessages = listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = barePrompt),
                    ),
                    systemPrompt = "",
                    toolsSchemaJson = "[]",
                    allowToolCall = false,
                )
                send(AgentLoopEvent.Finished)
            }
            createService(agentLoopPort = port1, inner = providerCapture.service).sendPrompt(
                prompt = barePrompt,
                options = GenerationOptions(reasoningBudget = 0, volatileTurnContext = xRagPrefix),
            ).toList()

            assertEquals(barePrompt, port1.capturedInitialMessages.last().content)

            val port2 = FakeAgentLoopPort { host ->
                host.completeModelTurn(
                    sessionMessages = listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = barePrompt),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "ok"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "follow up"),
                    ),
                    systemPrompt = "",
                    toolsSchemaJson = "[]",
                    allowToolCall = false,
                )
                send(AgentLoopEvent.Finished)
            }
            createService(agentLoopPort = port2, inner = providerCapture.service).sendPrompt(
                prompt = "follow up",
                options = GenerationOptions(reasoningBudget = 0, volatileTurnContext = null),
            ).toList()

            val turn2Wire = providerCapture.params.last().messages()
            assertEquals(
                barePrompt,
                turn2Wire.first { it.isUser() }.asUser().content().asText(),
            )
            assertTrue(
                turn2Wire.any { it.isSystem() && it.asSystem().content().asText().contains("RELEVANT X SEARCH RESULTS") },
            )
        }

    // ---------------------------------------------------------------------------
    // CSPC-03c — Turn 1 fires web + X pre-retrieval — turn 2 replays combined RAG prefix
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_03c_combined_web_x_rag_prefix_stored_in_rust_history_turn2_replays_it() =
        runTest {
            val providerCapture = captureOpenAiParams()
            val combinedRag = "# RELEVANT WEB SEARCH RESULTS:\nweb-results\n\n# RELEVANT X SEARCH RESULTS:\nx-results"
            val barePrompt = "combo query"

            val port1 = FakeAgentLoopPort { host ->
                host.completeModelTurn(
                    sessionMessages = listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = barePrompt),
                    ),
                    systemPrompt = "",
                    toolsSchemaJson = "[]",
                    allowToolCall = false,
                )
                send(AgentLoopEvent.Finished)
            }
            createService(agentLoopPort = port1, inner = providerCapture.service).sendPrompt(
                prompt = barePrompt,
                options = GenerationOptions(reasoningBudget = 0, volatileTurnContext = combinedRag),
            ).toList()

            assertEquals(barePrompt, port1.capturedInitialMessages.last().content)

            val port2 = FakeAgentLoopPort { host ->
                host.completeModelTurn(
                    sessionMessages = listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = barePrompt),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "ok"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "follow up"),
                    ),
                    systemPrompt = "",
                    toolsSchemaJson = "[]",
                    allowToolCall = false,
                )
                send(AgentLoopEvent.Finished)
            }
            createService(agentLoopPort = port2, inner = providerCapture.service).sendPrompt(
                prompt = "follow up",
                options = GenerationOptions(reasoningBudget = 0, volatileTurnContext = null),
            ).toList()

            val turn2Wire = providerCapture.params.last().messages()
            assertEquals(
                barePrompt,
                turn2Wire.first { it.isUser() }.asUser().content().asText(),
            )
            assertTrue(
                turn2Wire.any { it.isSystem() && it.asSystem().content().asText().contains("RELEVANT WEB SEARCH RESULTS") },
            )
        }

    // ---------------------------------------------------------------------------
    // CSPC-05 — Retrieved memories only — turn 2 wire reproduces turn 1 user content
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_05_knn_memory_prefix_stored_in_rust_history_turn2_replays_turn1_user_content() =
        runTest {
            val providerCapture = captureOpenAiParams()
            val memoryPrefix = "# RELEVANT FACTS & CONTEXT:\n- User prefers concise answers\n- User is a developer"
            val barePrompt = "help me code"

            val port1 = FakeAgentLoopPort { host ->
                host.completeModelTurn(
                    sessionMessages = listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = barePrompt),
                    ),
                    systemPrompt = "",
                    toolsSchemaJson = "[]",
                    allowToolCall = false,
                )
                send(AgentLoopEvent.Finished)
            }
            createService(agentLoopPort = port1, inner = providerCapture.service).sendPrompt(
                prompt = barePrompt,
                options = GenerationOptions(reasoningBudget = 0, volatileTurnContext = memoryPrefix),
            ).toList()

            assertEquals(barePrompt, port1.capturedInitialMessages.last().content)

            val port2 = FakeAgentLoopPort { host ->
                host.completeModelTurn(
                    sessionMessages = listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = barePrompt),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "ok"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "more help"),
                    ),
                    systemPrompt = "",
                    toolsSchemaJson = "[]",
                    allowToolCall = false,
                )
                send(AgentLoopEvent.Finished)
            }
            createService(agentLoopPort = port2, inner = providerCapture.service).sendPrompt(
                prompt = "more help",
                options = GenerationOptions(reasoningBudget = 0, volatileTurnContext = null),
            ).toList()

            val turn2Wire = providerCapture.params.last().messages()
            assertEquals(
                barePrompt,
                turn2Wire.first { it.isUser() }.asUser().content().asText(),
            )
            assertTrue(
                turn2Wire.any { it.isSystem() && it.asSystem().content().asText().contains("RELEVANT FACTS & CONTEXT") },
            )
        }

    // ---------------------------------------------------------------------------
    // CSPC-06 — Compaction boundary fires — log clears, prior frozen sidecars absent
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_06_compaction_boundary_clears_append_only_log_prior_sidecar_absent_from_wire() =
        runTest {
            val providerCapture = captureOpenAiParams()
            val turn1Needle = """"turn":1"""
            val turn2Needle = """"turn":2"""
            val port = SequentialAgentLoopPort { host ->
                host.completeModelTurn(
                    sessionMessages = listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "First"),
                    ),
                    systemPrompt = "",
                    toolsSchemaJson = "[]",
                    allowToolCall = false,
                    stateLedgerJson = """{"turn":1}""",
                )
                assertTrue(
                    requireNotNull(providerCapture.service.appendOnlyContextForTests())
                        .committedVolatileSidecarTexts()
                        .any { sidecar -> sidecar.contains(turn1Needle) },
                    "CSPC-06 setup: turn 1 sidecar must freeze before the compaction boundary",
                )

                emit(AgentLoopEvent.CompactionBoundaryApplied(revisionAfter = 1))
                assertTrue(
                    providerCapture.service
                        .appendOnlyContextForTests()
                        ?.committedVolatileSidecarTexts()
                        ?.isEmpty() == true,
                    "CSPC-06 setup: compaction boundary must clear frozen provider sidecars",
                )

                host.completeModelTurn(
                    sessionMessages = listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "First"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "ok"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Second"),
                    ),
                    systemPrompt = "",
                    toolsSchemaJson = "[]",
                    allowToolCall = false,
                    stateLedgerJson = """{"turn":2}""",
                )
                emit(AgentLoopEvent.Finished)
            }
            val service = createService(agentLoopPort = port, inner = providerCapture.service)

            service.sendPrompt(
                prompt = "First",
                options = GenerationOptions(reasoningBudget = 0),
            ).toList()

            val turn2WireSystemTexts =
                providerCapture.params.last().messages()
                    .filter { message -> message.isSystem() }
                    .map { message -> message.asSystem().content().asText() }
            assertFalse(
                turn2WireSystemTexts.any { text -> text.contains(turn1Needle) },
                "CSPC-06: turn 2 provider wire must NOT replay turn 1 sidecar after compaction boundary",
            )
            assertEquals(
                1,
                turn2WireSystemTexts.count { text -> text.contains(turn2Needle) },
                "CSPC-06: turn 2 provider wire must include only the current turn 2 sidecar",
            )
        }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun createService(
        agentLoopPort: AgentLoopPort,
        inner: RustCompatibleInferenceService = CapturingRustCompatibleService(),
        committedInferencePrompts: CommittedInferencePromptPort = NoopCommittedInferencePromptPort,
    ): RustLoopInferenceService {
        val activeModelProvider = mockk<com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort>()
        coEvery { activeModelProvider.getActiveConfiguration(any()) } returns null
        val resolveCompactionSummaryModel = ResolveCompactionSummaryModelUseCase(activeModelProvider, JTokkitTokenCounter)

        return RustLoopInferenceService(
            inner = inner,
            agentLoopPort = agentLoopPort,
            toolExecutor = object : ToolExecutorPort {
                override suspend fun execute(request: ToolCallRequest): ToolExecutionResult =
                    ToolExecutionResult(toolName = request.toolName, resultJson = """{"ok":true}""")
            },
            loggingPort = logger,
            provider = "OPENAI",
            modelType = ModelType.THINKING,
            inferenceFactoryProvider = Provider {
                object : com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort {
                    override suspend fun <T> withInferenceService(
                        modelType: ModelType,
                        block: suspend (com.browntowndev.enclave.domain.port.inference.LlmInferencePort) -> T,
                    ): T = throw UnsupportedOperationException("Not needed in CSPC tests")
                }
            },
            rollingSummarizer = RollingSummarizer(logger, JTokkitTokenCounter),
            resolveCompactionSummaryModel = resolveCompactionSummaryModel,
            committedInferencePrompts = committedInferencePrompts,
        )
    }

    // ---------------------------------------------------------------------------
    // Test doubles
    // ---------------------------------------------------------------------------

    /**
     * Captures initialMessages on each execute() call and delegates scenario execution
     * to the provided lambda. Lambda receiver is the ProducerScope so `send(event)` works.
     */
    private class FakeAgentLoopPort(
        private val behavior: suspend kotlinx.coroutines.channels.ProducerScope<AgentLoopEvent>.(AgentLoopHostCallbacks) -> Unit,
    ) : AgentLoopPort {
        var capturedInitialMessages: List<ChatMessage> = emptyList()

        override fun execute(
            initialMessages: List<ChatMessage>,
            config: AgentLoopConfig,
            sessionContext: Map<String, String>,
            host: AgentLoopHostCallbacks,
        ): Flow<AgentLoopEvent> =
            kotlinx.coroutines.flow.channelFlow {
                capturedInitialMessages = initialMessages
                behavior(host)
            }
    }

    private class SequentialAgentLoopPort(
        private val behavior: suspend kotlinx.coroutines.flow.FlowCollector<AgentLoopEvent>.(AgentLoopHostCallbacks) -> Unit,
    ) : AgentLoopPort {
        override fun execute(
            initialMessages: List<ChatMessage>,
            config: AgentLoopConfig,
            sessionContext: Map<String, String>,
            host: AgentLoopHostCallbacks,
        ): Flow<AgentLoopEvent> =
            kotlinx.coroutines.flow.flow {
                behavior(host)
            }
    }

    private data class CapturedOpenAiParams(
        val service: BaseOpenAiSdkInferenceService,
        val params: MutableList<ChatCompletionCreateParams>,
    )

    private fun captureOpenAiParams(): CapturedOpenAiParams {
        val capturedParams = mutableListOf<ChatCompletionCreateParams>()
        val service =
            object : BaseOpenAiSdkInferenceService(
                client = mockk<OpenAIClient>(),
                modelId = "grok-4.3",
                provider = "XAI",
                modelType = ModelType.FAST,
                loggingPort = logger,
                toolStreamGatePort = FakeToolStreamGatePort(),
            ) {
                override val tag: String = "RustLoopVolatileTurnContextCommitTest"

                override suspend fun streamChatCompletions(
                    params: ChatCompletionCreateParams,
                    chatId: com.browntowndev.enclave.domain.model.chat.ChatId?,
                    userMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                    assistantMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                    emitEvent: suspend (InferenceEvent) -> Unit,
                ): StreamedOpenAiResponse {
                    capturedParams.add(params)
                    return StreamedOpenAiResponse(
                        emittedAny = true,
                        functionCalls = emptyList(),
                        responseId = "resp_1",
                        providerToolCallIds = emptyList(),
                        providerToolItemIds = emptyList(),
                        assistantMessageText = "ok",
                    )
                }
            }
        return CapturedOpenAiParams(service = service, params = capturedParams)
    }

    /**
     * Captures all completeSingleTurn invocations for assertion.
     */
    private class CapturingRustCompatibleService : RustCompatibleInferenceService {
        val capturedTurnArgs = mutableListOf<List<AgentLoopMessage>>()

        override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> =
            flowOf(InferenceEvent.Finished(ModelType.THINKING))

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
        ): AgentModelTurnResult {
            capturedTurnArgs.add(sessionMessages)
            return AgentModelTurnResult(
                assistantText = "ok",
                toolCalls = emptyList(),
                finishReason = AgentFinishReason.Stop,
            )
        }
    }

    private class NoopLogger : LoggingPort {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
        override fun recordException(tag: String, message: String, throwable: Throwable) {}
    }
}
