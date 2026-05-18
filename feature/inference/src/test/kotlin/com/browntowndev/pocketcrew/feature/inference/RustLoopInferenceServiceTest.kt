package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.AgentContextReference
import com.browntowndev.pocketcrew.domain.model.inference.AgentFinishReason
import com.browntowndev.pocketcrew.domain.model.inference.AgentIncludedFile
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopConfig
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopContextPolicyTier
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopHostCallbacks
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopMessage
import com.browntowndev.pocketcrew.domain.model.inference.AgentModelTurnResult
import com.browntowndev.pocketcrew.domain.model.inference.AgentSummaryRequest
import com.browntowndev.pocketcrew.domain.model.inference.AgentSummaryResult
import com.browntowndev.pocketcrew.domain.model.inference.AgentToolCall
import com.browntowndev.pocketcrew.domain.model.inference.AgentToolResult
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.AgentLoopEvent
import com.browntowndev.pocketcrew.domain.port.inference.AgentLoopPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.usecase.chat.RollingSummarizer
import com.browntowndev.pocketcrew.domain.usecase.inference.ResolveCompactionSummaryModelUseCase
import com.browntowndev.pocketcrew.domain.util.JTokkitTokenCounter
import io.mockk.coEvery
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RustLoopInferenceServiceTest {
    private val logger = RecordingLogger()

    @Test
    fun sendPrompt_populatesSessionContextAndLlamaCapabilities() = runTest {
        val port = CapturingAgentLoopPort { _ -> send(AgentLoopEvent.Finished) }
        val service = createService(agentLoopPort = port, provider = "LLAMA")

        service.sendPrompt(
            prompt = "hello",
            options = GenerationOptions(
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
        assertEquals(2u, config.agent.maxModelRequestsPerTurn)
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
    fun sendPrompt_populatesIncludedFilesAndHostedCapabilities() = runTest {
        val port = CapturingAgentLoopPort { _ -> send(AgentLoopEvent.Finished) }
        val service = createService(agentLoopPort = port, provider = "OPENAI")

        service.sendPrompt(
            prompt = "hello",
            options = GenerationOptions(
                reasoningBudget = 0,
                includedFiles = listOf(
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
    fun sendPrompt_marksLargeHostedWindowsAsHighContext() = runTest {
        val port = CapturingAgentLoopPort { _ -> send(AgentLoopEvent.Finished) }
        val service = createService(agentLoopPort = port, provider = "OPENAI")

        service.sendPrompt(
            prompt = "hello",
            options = GenerationOptions(reasoningBudget = 0, contextWindow = 200_000),
        ).toList()

        val config = requireNotNull(port.capturedConfig)
        assertEquals(AgentLoopContextPolicyTier.HIGH_CONTEXT, config.capabilities.contextPolicyTier)
    }

    @Test
    fun completeModelTurn_restoresToolsFromDeferredContextIndex() = runTest {
        val inner = RecordingRustCompatibleService()
        val port = CapturingAgentLoopPort { host ->
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

        service.sendPrompt(
            prompt = "tools",
            options = GenerationOptions(
                reasoningBudget = 0,
                availableTools = listOf(ToolDefinition.WEB_SEARCH, ToolDefinition.APPEND_BLOCKS),
            ),
        ).toList()

        assertEquals(listOf(ToolDefinition.WEB_SEARCH.name), inner.lastAvailableTools.map { it.name })
    }

    @Test
    fun completeModelTurn_malformedDeferredContextIndexFailsClosed() = runTest {
        val inner = RecordingRustCompatibleService()
        val port = CapturingAgentLoopPort { host ->
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

        service.sendPrompt(
            prompt = "tools",
            options = GenerationOptions(
                reasoningBudget = 0,
                availableTools = listOf(ToolDefinition.WEB_SEARCH),
            ),
        ).toList()

        assertTrue(inner.lastAvailableTools.isEmpty())
    }

    @Test
    fun completeModelTurn_forwardsContextSidecarsToProviderPrompt() = runTest {
        val inner = RecordingRustCompatibleService()
        val port = CapturingAgentLoopPort { host ->
            host.completeModelTurn(
                sessionMessages = emptyList(),
                systemPrompt = "base system",
                toolsSchemaJson = "[]",
                allowToolCall = false,
                contextReferences = listOf(
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

        assertTrue(requireNotNull(inner.lastSystemPrompt).contains("Context management sidecars"))
        assertTrue(requireNotNull(inner.lastSystemPrompt).contains("file-outline-src/Main.kt"))
        assertTrue(requireNotNull(inner.lastSystemPrompt).contains("File context index JSON"))
    }

    @Test
    fun executeTools_runsCallsInParallel() = runTest {
        val started = AtomicInteger(0)
        val bothStarted = CompletableDeferred<Unit>()
        val executor = object : ToolExecutorPort {
            override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
                if (started.incrementAndGet() == 2) {
                    bothStarted.complete(Unit)
                }
                withTimeout(1_000) { bothStarted.await() }
                return ToolExecutionResult(toolName = request.toolName, resultJson = """{"ok":true}""")
            }
        }
        lateinit var results: List<AgentToolResult>
        val port = CapturingAgentLoopPort { host ->
            results = host.executeTools(
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
    fun executeTools_translatesThrowableToSoftFailureResult() = runTest {
        val executor = object : ToolExecutorPort {
            override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
                error("boom")
            }
        }
        lateinit var result: AgentToolResult
        val port = CapturingAgentLoopPort { host ->
            result = host.executeTools(
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
    fun summarize_usesHostedFastWhenContextWindowFits() = runTest {
        val summaryService = RecordingInferenceService(
            events = listOf(InferenceEvent.PartialResponse("summary text", ModelType.FAST)),
        )
        val factory = RecordingInferenceFactory(summaryService)
        lateinit var summary: AgentSummaryResult
        val port = CapturingAgentLoopPort { host ->
            summary = host.summarize(
                AgentSummaryRequest(
                    messagesToSummarize = listOf(
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
        val service = createService(
            agentLoopPort = port,
            inferenceFactoryProvider = Provider { factory },
            resolveCompactionSummaryModel = resolveCompactionSummaryModel(
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
    fun summarize_usesActiveTurnWhenFastIsLocal() = runTest {
        val summaryService = RecordingInferenceService(
            events = listOf(InferenceEvent.PartialResponse("summary text", ModelType.THINKING)),
        )
        val factory = RecordingInferenceFactory(summaryService)
        lateinit var summary: AgentSummaryResult
        val port = CapturingAgentLoopPort { host ->
            summary = host.summarize(
                AgentSummaryRequest(
                    messagesToSummarize = listOf(
                        AgentLoopMessage("u1", Role.USER, "Question one"),
                        AgentLoopMessage("a1", Role.ASSISTANT, "Answer one"),
                    ),
                    instruction = "summarize",
                    lastMessageId = "a1",
                ),
            )
            send(AgentLoopEvent.Finished)
        }
        val service = createService(
            agentLoopPort = port,
            inferenceFactoryProvider = Provider { factory },
            resolveCompactionSummaryModel = resolveCompactionSummaryModel(
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
    fun agentEvents_propagateCompactionRepairAndToolSideEffects() = runTest {
        val port = CapturingAgentLoopPort { host ->
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

        val events = service.sendPrompt(
            prompt = "events",
            options = GenerationOptions(
                reasoningBudget = 0,
                assistantMessageId = MessageId("assistant-1"),
            ),
        ).toList()

        assertNotNull(events.filterIsInstance<InferenceEvent.WebSearchResults>().single())
        assertEquals("FIFO", events.filterIsInstance<InferenceEvent.CompactionApplied>().single().tier)
        assertEquals("web_search", events.filterIsInstance<InferenceEvent.ToolRepaired>().single().resolvedName)
    }

    @Test
    fun `FB-K-007 RustLoop supports multiple complete model callbacks`() = runTest {
        val inner = RecordingRustCompatibleService(
            responses = listOf(
                AgentModelTurnResult("first", emptyList(), AgentFinishReason.Stop),
                AgentModelTurnResult("second", emptyList(), AgentFinishReason.Stop),
            ),
        )
        val port = CapturingAgentLoopPort { host ->
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
    fun `FB-K-008 multi callback text tokens emit partial responses before finished`() = runTest {
        val port = CapturingAgentLoopPort { _ ->
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
    fun sendPrompt_retainsAssistantTextInSessionHistoryForFollowupTurns() = runTest {
        val port = CapturingAgentLoopPort { _ ->
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

    private fun createService(
        agentLoopPort: AgentLoopPort,
        inner: RustCompatibleInferenceService = RecordingRustCompatibleService(),
        toolExecutor: ToolExecutorPort = object : ToolExecutorPort {
            override suspend fun execute(request: ToolCallRequest): ToolExecutionResult =
                ToolExecutionResult(toolName = request.toolName, resultJson = """{"ok":true}""")
        },
        provider: String = "OPENAI",
        inferenceFactoryProvider: Provider<InferenceFactoryPort> = Provider {
            RecordingInferenceFactory(RecordingInferenceService())
        },
        resolveCompactionSummaryModel: ResolveCompactionSummaryModelUseCase =
            resolveCompactionSummaryModel(fastConfig = null),
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
        )

    private fun resolveCompactionSummaryModel(
        fastConfig: ActiveModelConfiguration?,
    ): ResolveCompactionSummaryModelUseCase {
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
        ): Flow<AgentLoopEvent> = channelFlow {
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
        var lastAvailableTools: List<ToolDefinition> = emptyList()
        var completeSingleTurnCallCount = 0

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
            availableTools: List<ToolDefinition>,
            allowToolCall: Boolean,
            onTextToken: suspend (String) -> Unit,
            onThinkingToken: suspend (String) -> Unit,
            options: GenerationOptions,
        ): AgentModelTurnResult {
            completeSingleTurnCallCount += 1
            lastSystemPrompt = systemPrompt
            lastAvailableTools = availableTools
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
    }

    private class RecordingInferenceService(
        private val events: List<InferenceEvent> = emptyList(),
    ) : LlmInferencePort {
        val prompts = mutableListOf<String>()

        override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
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
        override fun debug(tag: String, message: String) {}

        override fun info(tag: String, message: String) {}

        override fun warning(tag: String, message: String) {}

        override fun error(tag: String, message: String, throwable: Throwable?) {}

        override fun recordException(tag: String, message: String, throwable: Throwable) {}
    }
}
