package com.browntowndev.enclave.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationEvent
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.llama.LlamaChatSessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class LlamaInferenceServiceImplTest {
    private lateinit var sessionManager: LlamaChatSessionManager
    private lateinit var loggingPort: LoggingPort
    private lateinit var localModelRepository: LocalModelRepositoryPort
    private lateinit var activeModelProvider: ActiveModelProviderPort
    private lateinit var context: Context

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        sessionManager = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)
        localModelRepository = mockk()
        activeModelProvider = mockk()
        context = mockk(relaxed = true)

        every { context.getExternalFilesDir(null) } returns File("/tmp")

        coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns
            ActiveModelConfiguration(
                id = LocalModelConfigurationId("7"),
                isLocal = true,
                name = "Llama Fast",
                modelId = "7",
                systemPrompt = "You are a helpful assistant.",
                reasoningEffort = null,
                temperature = 0.7,
                topK = 40,
                topP = 0.9,
                maxTokens = 512,
                minP = 0.0,
                repetitionPenalty = 1.1,
                contextWindow = 4096,
                thinkingEnabled = false,
            )
        coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("7")) } returns
            LocalModelAsset(
                metadata =
                    LocalModelMetadata(
                        id = LocalModelId("99"),
                        huggingFaceModelName = "test/llama",
                        remoteFileName = "model.gguf",
                        localFileName = "model.gguf",
                        sha256 = "abc123",
                        sizeInBytes = 1024L,
                        modelFileFormat = ModelFileFormat.GGUF,
                    ),
                configurations = emptyList(),
            )
        coEvery { sessionManager.initializeEngine(any()) } returns Unit
        coEvery { sessionManager.setHistory(any()) } returns Unit
        coEvery { sessionManager.startNewConversation() } returns Unit
        coEvery { sessionManager.clearConversation() } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `sendPrompt forwards imageUris to llama generation options`() =
        runTest {
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.VISION) } returns
                ActiveModelConfiguration(
                    id = LocalModelConfigurationId("9"),
                    isLocal = true,
                    name = "Llama Vision",
                    modelId = "9",
                    systemPrompt = "You describe images.",
                    reasoningEffort = null,
                    temperature = 0.7,
                    topK = 40,
                    topP = 0.9,
                    maxTokens = 512,
                    minP = 0.0,
                    repetitionPenalty = 1.1,
                    contextWindow = 4096,
                    thinkingEnabled = false,
                )
            coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("9")) } returns
                LocalModelAsset(
                    metadata =
                        LocalModelMetadata(
                            id = LocalModelId("199"),
                            huggingFaceModelName = "test/llama-vision",
                            remoteFileName = "model.gguf",
                            localFileName = "model.gguf",
                            sha256 = "abc123",
                            sizeInBytes = 1024L,
                            modelFileFormat = ModelFileFormat.GGUF,
                            isMultimodal = true,
                            mmprojLocalFileName = "mmproj.gguf",
                        ),
                    configurations = emptyList(),
                )
            coEvery { sessionManager.sendUserMessage(any()) } returns Unit
            every { sessionManager.streamAssistantResponseWithOptions(any()) } returns
                flowOf(
                    GenerationEvent.Completed("Detailed image description"),
                )

            val optionsSlot = slot<GenerationOptions>()
            val service = createService(ModelType.VISION)

            service
                .sendPrompt(
                    prompt = "Describe this image in detail",
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            modelType = ModelType.VISION,
                            imageUris = listOf("file:///tmp/cat.jpg"),
                            modelId = "9",
                        ),
                    closeConversation = false,
                ).toList()

            verify { sessionManager.streamAssistantResponseWithOptions(capture(optionsSlot)) }
            assertEquals(listOf("file:///tmp/cat.jpg"), optionsSlot.captured.imageUris)
        }

    @Test
    fun `sendPrompt returns typed error when vision capable gguf model is missing mmproj`() =
        runTest {
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.VISION) } returns
                ActiveModelConfiguration(
                    id = LocalModelConfigurationId("9"),
                    isLocal = true,
                    name = "Llama Vision",
                    modelId = "9",
                    systemPrompt = "You describe images.",
                    reasoningEffort = null,
                    temperature = 0.7,
                    topK = 40,
                    topP = 0.9,
                    maxTokens = 512,
                    minP = 0.0,
                    repetitionPenalty = 1.1,
                    contextWindow = 4096,
                    thinkingEnabled = false,
                )
            coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("9")) } returns
                LocalModelAsset(
                    metadata =
                        LocalModelMetadata(
                            id = LocalModelId("199"),
                            huggingFaceModelName = "test/llama-vision",
                            remoteFileName = "model.gguf",
                            localFileName = "model.gguf",
                            sha256 = "abc123",
                            sizeInBytes = 1024L,
                            modelFileFormat = ModelFileFormat.GGUF,
                            isMultimodal = true,
                        ),
                    configurations = emptyList(),
                )

            val service = createService(ModelType.VISION)
            val events =
                service
                    .sendPrompt(
                        prompt = "Describe this image in detail",
                        options =
                            GenerationOptions(
                                reasoningBudget = 0,
                                modelType = ModelType.VISION,
                                imageUris = listOf("file:///tmp/cat.jpg"),
                                modelId = "9",
                            ),
                        closeConversation = false,
                    ).toList()

            val error = events.filterIsInstance<InferenceEvent.Error>().single()
            assertTrue(error.cause is IllegalStateException)
            assertEquals("Vision-capable GGUF model requires an mmproj companion file.", error.cause.message)
        }

    @Test
    fun `completeSingleTurn emits engine loading before processing on cold load`() =
        runTest {
            every { sessionManager.streamAssistantResponseWithOptions(any()) } returns
                flowOf(
                    GenerationEvent.Completed("Assistant text"),
                )
            val lifecycleEvents = mutableListOf<InferenceEvent>()
            val service = createService(ModelType.FAST)

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hi!", metadataJson = "null"),
                    ),
                systemPrompt = "System",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 2048, modelType = ModelType.FAST),
                onLifecycleEvent = { lifecycleEvents += it },
            )

            val loadingIndex = lifecycleEvents.indexOfFirst { it is InferenceEvent.EngineLoading }
            val processingIndex = lifecycleEvents.indexOfFirst { it is InferenceEvent.Processing }
            assertTrue(loadingIndex >= 0)
            assertTrue(processingIndex >= 0)
            assertTrue(loadingIndex < processingIndex)
        }

    @Test
    fun `completeSingleTurn streams visible response tokens incrementally`() =
        runTest {
            every { sessionManager.streamAssistantResponseWithOptions(any()) } returns
                flow {
                    emit(GenerationEvent.Token("<think>reasoning</think>"))
                    emit(GenerationEvent.Token("Hello"))
                    emit(GenerationEvent.Token(" world"))
                    emit(
                        GenerationEvent.Completed(
                            fullText = "<think>reasoning</think>Hello world",
                            promptTokens = 4,
                            generatedTokens = 6,
                            prefixReusedTokens = 0,
                        ),
                    )
                }
            val thinkingTokens = mutableListOf<String>()
            val textTokens = mutableListOf<String>()
            val service = createService(ModelType.FAST)

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hi!", metadataJson = "null"),
                    ),
                systemPrompt = "System",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = { textTokens += it },
                onThinkingToken = { thinkingTokens += it },
                options = GenerationOptions(reasoningBudget = 2048, modelType = ModelType.FAST),
            )

            assertEquals(listOf("reasoning"), thinkingTokens)
            assertEquals(listOf("Hello", " world"), textTokens)
        }

    @Test
    fun `completeSingleTurn maps agent tool budget sidecar to system role not assistant`() =
        runTest {
            every { sessionManager.streamAssistantResponseWithOptions(any()) } returns
                flowOf(
                    GenerationEvent.Completed("Assistant text"),
                )
            val capturedHistories = mutableListOf<List<ChatMessage>>()
            val service = createService(ModelType.FAST)
            val budgetContent =
                "<system_context>\nTool budget: 3/50 used (47 remaining). Tools enabled.\n</system_context>"

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Question", metadataJson = "null"),
                        AgentLoopMessage(
                            id = "budget",
                            role = Role.ASSISTANT,
                            content = budgetContent,
                            metadataJson = """{"agent_tool_budget":true}""",
                        ),
                    ),
                systemPrompt = "System",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
            )

            coVerify(atLeast = 1) { sessionManager.setHistory(capture(capturedHistories)) }
            val history = capturedHistories.last()
            assertEquals(2, history.size)
            assertEquals(Role.USER, history[0].role)
            assertEquals(Role.SYSTEM, history[1].role)
            assertEquals(budgetContent, history[1].content)
            assertEquals(false, history[1].content.contains(""""_agent""""))
        }

    @Test
    fun `completeSingleTurn rebuilds session history from Rust messages`() =
        runTest {
            every { sessionManager.streamAssistantResponseWithOptions(any()) } returns
                flowOf(
                    GenerationEvent.Completed("Assistant text"),
                )
            val capturedHistories = mutableListOf<List<ChatMessage>>()
            val service = createService(ModelType.FAST)

            val result =
                service.completeSingleTurn(
                    sessionMessages =
                        listOf(
                            AgentLoopMessage(id = "u1", role = Role.USER, content = "Question", metadataJson = "null"),
                            AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Prior answer", metadataJson = "null"),
                        ),
                    systemPrompt = "System",
                    availableTools = emptyList(),
                    allowToolCall = false,
                    onTextToken = {},
                    onThinkingToken = {},
                    options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
                )

            coVerify(atLeast = 1) { sessionManager.setHistory(capture(capturedHistories)) }
            assertEquals(
                listOf(
                    ChatMessage(Role.USER, "Question"),
                    ChatMessage(Role.ASSISTANT, "Prior answer"),
                ),
                capturedHistories.last(),
            )
            assertEquals(AgentFinishReason.Stop, result.finishReason)
            assertEquals("Assistant text", result.assistantText)
        }

    @Test
    fun `completeSingleTurn enables continuation only for a valid append-only transcript`() =
        runTest {
            val continuations = mutableListOf<Boolean>()
            val prevMessageCounts = mutableListOf<Int>()
            every {
                sessionManager.streamAssistantResponseWithOptions(
                    any(),
                    capture(continuations),
                    capture(prevMessageCounts),
                )
            } returns flowOf(GenerationEvent.Completed("Assistant text"))
            val service = createService(ModelType.FAST)
            val baseOptions = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST, transcriptRevision = 1L)
            val transcripts =
                (1..7).map { count ->
                    (1..count).map { index ->
                        AgentLoopMessage(id = "m$index", role = Role.USER, content = "message $index")
                    }
                }

            service.completeTurnForTest(transcripts[0], "System A", baseOptions)
            service.completeTurnForTest(transcripts[1], "System A", baseOptions)
            service.completeTurnForTest(transcripts[2], "System A", baseOptions.copy(transcriptRevision = 2L))
            service.completeTurnForTest(transcripts[3], "System B", baseOptions.copy(transcriptRevision = 2L))
            service.completeTurnForTest(transcripts[4], "System B", baseOptions.copy(transcriptRevision = 2L))
            service.clearAppendOnlyOnRustCompactionBoundary()
            service.completeTurnForTest(transcripts[5], "System B", baseOptions.copy(transcriptRevision = 2L))
            service.completeTurnForTest(
                transcripts[6],
                "System B",
                baseOptions.copy(reasoningBudget = 1, transcriptRevision = 2L),
            )
            service.completeTurnForTest(
                listOf(AgentLoopMessage(id = "rewritten", role = Role.USER, content = "replacement")),
                "System B",
                baseOptions.copy(reasoningBudget = 1, transcriptRevision = 2L),
            )

            assertEquals(listOf(false, true, false, false, true, false, false, false), continuations)
            assertEquals(listOf(0, 2, 0, 0, 5, 0, 0, 0), prevMessageCounts)
        }

    @Test
    fun `completeSingleTurn preserves injected sidecars in the continuation prefix`() =
        runTest {
            val capturedHistories = mutableListOf<List<ChatMessage>>()
            val continuations = mutableListOf<Boolean>()
            val prevMessageCounts = mutableListOf<Int>()
            coEvery { sessionManager.setHistory(capture(capturedHistories)) } returns Unit
            every {
                sessionManager.streamAssistantResponseWithOptions(
                    any(),
                    capture(continuations),
                    capture(prevMessageCounts),
                )
            } returns flowOf(GenerationEvent.Completed("Assistant text"))
            val service = createService(ModelType.FAST)
            val options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST, transcriptRevision = 7L)
            val firstTurn = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "First question"))
            val secondTurn =
                firstTurn +
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "First answer") +
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "Second question")

            service.completeTurnForTest(firstTurn, "Stable system", options, contextSidecars = "ledger one")
            service.completeTurnForTest(secondTurn, "Stable system", options, contextSidecars = "ledger two")

            assertEquals(listOf(false, true), continuations)
            assertEquals(listOf(0, 2), prevMessageCounts)
            assertEquals(
                listOf(
                    ChatMessage(Role.USER, "[context]\nledger one\n[/context]\n\nFirst question"),
                    ChatMessage(Role.ASSISTANT, "First answer"),
                    ChatMessage(Role.USER, "[context]\nledger two\n[/context]\n\nSecond question"),
                ),
                capturedHistories.last(),
            )
        }

    @Test
    fun `completeSingleTurn invalidates continuation after generation error`() =
        runTest {
            val continuations = mutableListOf<Boolean>()
            val prevMessageCounts = mutableListOf<Int>()
            every {
                sessionManager.streamAssistantResponseWithOptions(
                    any(),
                    capture(continuations),
                    capture(prevMessageCounts),
                )
            } returnsMany
                listOf(
                    flowOf(GenerationEvent.Completed("First answer")),
                    flowOf(GenerationEvent.Error(IllegalStateException("decode failed"))),
                    flowOf(GenerationEvent.Completed("Recovered answer")),
                )
            val service = createService(ModelType.FAST)
            val options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST, transcriptRevision = 9L)
            val firstTurn = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "First"))
            val secondTurn = firstTurn + AgentLoopMessage(id = "u2", role = Role.USER, content = "Second")
            val thirdTurn = secondTurn + AgentLoopMessage(id = "u3", role = Role.USER, content = "Third")

            service.completeTurnForTest(firstTurn, "System", options)
            val failed = runCatching { service.completeTurnForTest(secondTurn, "System", options) }
            service.completeTurnForTest(thirdTurn, "System", options)

            assertTrue(failed.isFailure)
            assertEquals(listOf(false, true, false), continuations)
            assertEquals(listOf(0, 2, 0), prevMessageCounts)
        }

    @Test
    fun `completeSingleTurn maps all AgentLoopRowKind variants correctly to DomainChatMessage`() =
        runTest {
            every { sessionManager.streamAssistantResponseWithOptions(any()) } returns
                flowOf(GenerationEvent.Completed("Assistant text"))
            val capturedHistories = mutableListOf<List<ChatMessage>>()
            val service = createService(ModelType.FAST)

            val sessionMessages = listOf(
                // 1. SystemPrompt
                AgentLoopMessage(id = "s1", role = Role.SYSTEM, content = "System Prompt Content", metadataJson = "null"),
                // 2. UserText
                AgentLoopMessage(id = "u1", role = Role.USER, content = "User Content", metadataJson = "null"),
                // 3. CompactSummary
                AgentLoopMessage(id = "compact-summary-001", role = Role.ASSISTANT, content = "Summary Content", metadataJson = "null"),
                // 4. EngineInternal
                AgentLoopMessage(
                    id = "budget",
                    role = Role.ASSISTANT,
                    content = "<system_context>Tool budget: 1/35 used.</system_context>",
                    metadataJson = """{"agent_tool_budget":true}""",
                ),
                // 5. ToolResult (mapped via ToolEnvelopeParserPort)
                AgentLoopMessage(
                    id = "t1",
                    role = Role.USER,
                    content = """{"result":"ok"}""",
                    metadataJson = """{"call_id":"call_1"}""",
                ),
                // 6. AssistantText
                AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Assistant Content", metadataJson = "null"),
                // 7. Null/blank assistant -> fallback to fallback message
                AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "  ", metadataJson = "null")
            )

            service.completeSingleTurn(
                sessionMessages = sessionMessages,
                systemPrompt = "System",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
            )

            coVerify(atLeast = 1) { sessionManager.setHistory(capture(capturedHistories)) }
            val history = capturedHistories.last()

            assertEquals(7, history.size)

            // 1. SystemPrompt -> SYSTEM
            assertEquals(Role.SYSTEM, history[0].role)
            assertEquals("System Prompt Content", history[0].content)

            // 2. UserText -> USER
            assertEquals(Role.USER, history[1].role)
            assertEquals("User Content", history[1].content)

            // 3. CompactSummary -> ASSISTANT
            assertEquals(Role.ASSISTANT, history[2].role)
            assertEquals("Summary Content", history[2].content)

            // 4. EngineInternal -> SYSTEM
            assertEquals(Role.SYSTEM, history[3].role)
            assertEquals("<system_context>Tool budget: 1/35 used.</system_context>", history[3].content)

            // 5. ToolResult -> USER (formatted with fake parser)
            assertEquals(Role.USER, history[4].role)
            assertEquals("<tool_result>{\"result\":\"ok\"}</tool_result>", history[4].content)

            // 6. AssistantText -> ASSISTANT
            assertEquals(Role.ASSISTANT, history[5].role)
            assertEquals("Assistant Content", history[5].content)

            // 7. Null/blank assistant -> fallback to original Role/Content
            assertEquals(Role.ASSISTANT, history[6].role)
            assertEquals("  ", history[6].content)
        }

    private suspend fun LlamaInferenceServiceImpl.completeTurnForTest(
        messages: List<AgentLoopMessage>,
        systemPrompt: String,
        options: GenerationOptions,
        contextSidecars: String? = null,
    ) {
        completeSingleTurn(
            sessionMessages = messages,
            systemPrompt = systemPrompt,
            contextSidecars = contextSidecars,
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = options,
        )
    }

    private fun createService(modelType: ModelType = ModelType.FAST): LlamaInferenceServiceImpl =
        LlamaInferenceServiceImpl(
            sessionManager = sessionManager,
            processThinkingTokens = ProcessThinkingTokensUseCase(),
            loggingPort = loggingPort,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            context = context,
            modelType = modelType,
            toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
            toolStreamGatePort = FakeToolStreamGatePort(),
        )
}
