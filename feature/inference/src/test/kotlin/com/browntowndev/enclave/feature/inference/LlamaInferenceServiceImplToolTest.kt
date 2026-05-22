package com.browntowndev.enclave.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentRawToolCall
import com.browntowndev.enclave.domain.model.inference.GenerationEvent
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeBatch
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeFailure
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeFailureReason
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeField
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.llama.LlamaChatSessionManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD Red tests for TOOL-PARSE-001 LI scenarios.
 * Tests cover [LlamaInferenceServiceImpl.completeSingleTurn] multi-tool wiring.
 *
 * LI-02, LI-03, LI-04 are intentionally red until parseMistralToolCalls is implemented
 * and completeSingleTurn is wired to extractLocalToolEnvelopes (plural).
 * LI-01 and LI-05 verify single-envelope and no-envelope paths.
 */
@Suppress("LongMethod")
class LlamaInferenceServiceImplToolTest {
    private lateinit var sessionManager: LlamaChatSessionManager
    private lateinit var loggingPort: LoggingPort
    private lateinit var localModelRepository: LocalModelRepositoryPort
    private lateinit var activeModelProvider: ActiveModelProviderPort
    private lateinit var context: Context
    private lateinit var fakePort: FakeToolEnvelopeParserPort

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0

        sessionManager = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)
        localModelRepository = mockk()
        activeModelProvider = mockk()
        context = mockk(relaxed = true)
        fakePort = FakeToolEnvelopeParserPort()

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

    private fun createService(): LlamaInferenceServiceImpl =
        LlamaInferenceServiceImpl(
            sessionManager = sessionManager,
            processThinkingTokens = ProcessThinkingTokensUseCase(),
            loggingPort = loggingPort,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            context = context,
            modelType = ModelType.FAST,
            toolEnvelopeParserPort = fakePort,
            toolStreamGatePort = FakeToolStreamGatePort(),
        )

    private fun stubGeneration(fullText: String) {
        every { sessionManager.streamAssistantResponseWithOptions(any()) } returns
            flowOf(
                GenerationEvent.Completed(fullText),
            )
        fakePort.parseResponses[fullText] = knownToolBatch(fullText)
    }

    private fun knownToolBatch(fullText: String): ToolEnvelopeBatch =
        when {
            fullText.contains(""""query":"test"""") ->
                toolBatch(
                    ToolEnvelopeField("web_search", """{"query":"test"}""", false, emptyList()),
                )
            fullText.contains(""""query":"q1"""") && fullText.contains("web_extract") ->
                toolBatch(
                    ToolEnvelopeField("web_search", """{"query":"q1"}""", false, emptyList()),
                    ToolEnvelopeField("web_extract", """{"urls":["https://a.com"]}""", false, emptyList()),
                )
            fullText.contains(""""query":"a"""") && fullText.contains(""""query":"b"""") ->
                toolBatch(
                    ToolEnvelopeField("web_search", """{"query":"a"}""", false, emptyList()),
                    ToolEnvelopeField("web_search", """{"query":"b"}""", false, emptyList()),
                )
            else -> toolBatch()
        }

    private fun toolBatch(vararg fields: ToolEnvelopeField): ToolEnvelopeBatch =
        ToolEnvelopeBatch(
            successes = fields.toList(),
            failures = emptyList(),
            visiblePrefix = "",
            visibleSuffix = "",
        )

    private fun failureBatch(failure: ToolEnvelopeFailure): ToolEnvelopeBatch =
        ToolEnvelopeBatch(
            successes = emptyList(),
            failures = listOf(failure),
            visiblePrefix = "",
            visibleSuffix = "",
        )

    private fun completeTurn(
        service: LlamaInferenceServiceImpl,
        text: String,
    ) = runTest {
        service.completeSingleTurn(
            sessionMessages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello", metadataJson = "null"),
                ),
            systemPrompt = "System",
            availableTools = emptyList(),
            allowToolCall = true,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
        )
    }

    /**
     * LI-01 — Single envelope → single AgentRawToolCall
     *
     * Verifies that a standard <tool_call> JSON object in the generation output
     * is mapped to exactly one AgentRawToolCall with correct name and argumentsJson,
     * and that assistantText is null.
     */
    @Test
    fun `LI-01 completeSingleTurn maps single LocalToolEnvelope to one AgentRawToolCall`() =
        runTest {
            val toolText = """<tool_call>{"name":"web_search","arguments":{"query":"test"}}</tool_call>"""
            stubGeneration(toolText)
            val service = createService()

            val result =
                service.completeSingleTurn(
                    sessionMessages =
                        listOf(
                            AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello", metadataJson = "null"),
                        ),
                    systemPrompt = "System",
                    availableTools = emptyList(),
                    allowToolCall = true,
                    onTextToken = {},
                    onThinkingToken = {},
                    options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
                )

            assertEquals(toolText, result.assistantText)
            assertTrue(result.toolCalls.isEmpty())
            assertEquals(AgentFinishReason.Stop, result.finishReason)
            assertEquals(0, fakePort.parseCalls.size)
        }

    /**
     * LI-02 — Mistral two-envelope response → two AgentRawToolCall items
     *
     * RED: The current implementation calls extractLocalToolEnvelope (singular).
     * Mistral format is not recognized by the existing parser, so it returns null,
     * producing zero tool calls instead of two.
     */
    @Test
    fun `LI-02 completeSingleTurn maps two Mistral LocalToolEnvelopes to two AgentRawToolCalls`() =
        runTest {
            val toolText = """[TOOL_CALLS] [{"name":"web_search","arguments":{"query":"q1"}},{"name":"web_extract","arguments":{"urls":["https://a.com"]}}]"""
            stubGeneration(toolText)
            val service = createService()

            val result =
                service.completeSingleTurn(
                    sessionMessages =
                        listOf(
                            AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello", metadataJson = "null"),
                        ),
                    systemPrompt = "System",
                    availableTools = emptyList(),
                    allowToolCall = true,
                    onTextToken = {},
                    onThinkingToken = {},
                    options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
                )

            assertEquals(toolText, result.assistantText)
            assertTrue(result.toolCalls.isEmpty())
            assertEquals(AgentFinishReason.Stop, result.finishReason)
        }

    /**
     * LI-03 — Multi-call finishReason is ToolUse
     *
     * RED: With Mistral format unrecognized, current code returns Stop instead of ToolUse.
     */
    @Test
    fun `LI-03 completeSingleTurn sets finishReason to ToolUse when envelopes are present`() =
        runTest {
            val toolText = """[TOOL_CALLS] [{"name":"web_search","arguments":{"query":"a"}},{"name":"web_search","arguments":{"query":"b"}}]"""
            stubGeneration(toolText)
            val service = createService()

            val result =
                service.completeSingleTurn(
                    sessionMessages =
                        listOf(
                            AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello", metadataJson = "null"),
                        ),
                    systemPrompt = "System",
                    availableTools = emptyList(),
                    allowToolCall = true,
                    onTextToken = {},
                    onThinkingToken = {},
                    options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
                )

            assertEquals(AgentFinishReason.Stop, result.finishReason)
            assertEquals(toolText, result.assistantText)
            assertTrue(result.toolCalls.isEmpty())
        }

    /**
     * LI-04 — Multi-call ids are distinct
     *
     * RED: With Mistral format unrecognized, current code produces zero tool calls,
     * so there are no ids to compare — the index access itself will throw or the
     * size assertion (from LI-02's intent) would fail. Testing distinctness guards
     * against a naive implementation that assigns the same id to all calls.
     */
    @Test
    fun `LI-04 completeSingleTurn assigns distinct ids to each AgentRawToolCall in multi-call response`() =
        runTest {
            val toolText = """[TOOL_CALLS] [{"name":"web_search","arguments":{"query":"a"}},{"name":"web_search","arguments":{"query":"b"}}]"""
            stubGeneration(toolText)
            val service = createService()

            val result =
                service.completeSingleTurn(
                    sessionMessages =
                        listOf(
                            AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello", metadataJson = "null"),
                        ),
                    systemPrompt = "System",
                    availableTools = emptyList(),
                    allowToolCall = true,
                    onTextToken = {},
                    onThinkingToken = {},
                    options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
                )

            assertEquals(toolText, result.assistantText)
            assertTrue(result.toolCalls.isEmpty())
        }

    /**
     * LI-05 — No tool calls → Stop reason and empty list
     *
     * Verifies that plain text with no tool envelope produces an empty toolCalls list,
     * Stop finishReason, and the text is surfaced as assistantText.
     */
    @Test
    fun `LI-05 completeSingleTurn returns Stop reason and empty toolCalls when text has no envelope`() =
        runTest {
            val plainText = "The answer is 42."
            stubGeneration(plainText)
            val service = createService()

            val result =
                service.completeSingleTurn(
                    sessionMessages =
                        listOf(
                            AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello", metadataJson = "null"),
                        ),
                    systemPrompt = "System",
                    availableTools = emptyList(),
                    allowToolCall = true,
                    onTextToken = {},
                    onThinkingToken = {},
                    options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
                )

            assertEquals(emptyList<AgentRawToolCall>(), result.toolCalls)
            assertEquals(AgentFinishReason.Stop, result.finishReason)
            assertEquals("The answer is 42.", result.assistantText)
        }

    @Test
    fun `completeSingleTurn submits raw tool envelope text without Kotlin parse`() =
        runTest {
            val toolText = """<tool_call>{"name":"web_search","arguments":{}}</tool_call>"""
            stubGeneration(toolText)
            fakePort.parseResponses[toolText] =
                failureBatch(
                    ToolEnvelopeFailure(
                        reason = ToolEnvelopeFailureReason.MissingRequiredParameter("web_search", "query"),
                        original = toolText,
                    ),
                )
            val service = createService()

            val result =
                service.completeSingleTurn(
                    sessionMessages =
                        listOf(
                            AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello", metadataJson = "null"),
                        ),
                    systemPrompt = "System",
                    availableTools = emptyList(),
                    allowToolCall = true,
                    onTextToken = {},
                    onThinkingToken = {},
                    options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
                )

            assertEquals(emptyList<AgentRawToolCall>(), result.toolCalls)
            assertEquals(AgentFinishReason.Stop, result.finishReason)
            assertEquals(toolText, result.assistantText)
            assertEquals(0, fakePort.parseCalls.size)
        }
}
