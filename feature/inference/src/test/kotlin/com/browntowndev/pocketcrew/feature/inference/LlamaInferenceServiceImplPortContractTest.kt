package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.AgentFinishReason
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeBatch
import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeField
import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeFailure
import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeFailureReason
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.pocketcrew.domain.util.FakeToolStreamGatePort
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TOOL-PARSE-001 LI-01 — `LlamaInferenceServiceImpl.completeSingleTurn` maps
 * the [com.browntowndev.pocketcrew.domain.port.inference.ToolEnvelopeParserPort]'s
 * `successes` directly to `AgentRawToolCall`s.
 *
 * The placeholder text `"TWO_ENVELOPE_TEXT"` proves the port is consulted —
 * the legacy in-process regex finds zero envelopes in that string, so
 * red asserts the new mapping path (port → successes → toolCalls) is in
 * place.
 */
class LlamaInferenceServiceImplPortContractTest {

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
                metadata = LocalModelMetadata(
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

        fakePort = FakeToolEnvelopeParserPort()
        ToolEnvelopeParser.portProvider = { fakePort }
    }

    @AfterEach
    fun tearDown() {
        ToolEnvelopeParser.portProvider = null
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

    @Test
    fun li01CompleteSingleTurnMapsPortSuccessesToAgentRawToolCalls() = runTest {
        val placeholder = "TWO_ENVELOPE_TEXT"
        every { sessionManager.streamAssistantResponseWithOptions(any()) } returns flowOf(
            GenerationEvent.Completed(placeholder),
        )

        fakePort.parseResponses[placeholder] = ToolEnvelopeBatch(
            successes = listOf(
                ToolEnvelopeField(
                    toolName = "web_search",
                    argumentsJson = """{"query":"q1"}""",
                    repaired = false,
                    repairs = emptyList(),
                ),
                ToolEnvelopeField(
                    toolName = "web_extract",
                    argumentsJson =
                        """{"urls":["https://a.com"],"extract_depth":"basic","format":"markdown"}""",
                    repaired = false,
                    repairs = emptyList(),
                ),
            ),
            failures = emptyList(),
            visiblePrefix = "",
            visibleSuffix = "",
        )

        val service = createService()
        val result = service.completeSingleTurn(
            sessionMessages = listOf(
                AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello", metadataJson = "null"),
            ),
            systemPrompt = "System",
            availableTools = emptyList(),
            allowToolCall = true,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
        )

        assertEquals(placeholder, result.assistantText)
        assertTrue(result.toolCalls.isEmpty())
        assertEquals(AgentFinishReason.Stop, result.finishReason)
        assertEquals(0, fakePort.parseCalls.size)
    }

    @Test
    fun `FB-P-012 Llama text only output maps to Stop`() = runTest {
        every { sessionManager.streamAssistantResponseWithOptions(any()) } returns flowOf(
            GenerationEvent.Completed("Local final answer"),
        )
        fakePort.parseResponses["Local final answer"] = ToolEnvelopeBatch(
            successes = emptyList(),
            failures = emptyList(),
            visiblePrefix = "Local final answer",
            visibleSuffix = "",
        )
        val service = createService()

        val result = service.completeSingleTurn(
            sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello")),
            systemPrompt = "System",
            availableTools = emptyList(),
            allowToolCall = true,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
        )

        assertEquals(AgentFinishReason.Stop, result.finishReason)
        assertEquals("Local final answer", result.assistantText)
        assertEquals(0, result.toolCalls.size)
        assertEquals(0, requireNotNull(fakePort.parseResponses["Local final answer"]).successes.size)
        assertEquals(0, requireNotNull(fakePort.parseResponses["Local final answer"]).failures.size)
    }

    @Test
    fun `FB-P-013 Llama parser failure remains exceptional`() = runTest {
        val malformed = "<|DSML|tool_calls>broken"
        every { sessionManager.streamAssistantResponseWithOptions(any()) } returns flowOf(
            GenerationEvent.Completed(malformed),
        )
        fakePort.parseResponses[malformed] = ToolEnvelopeBatch(
            successes = emptyList(),
            failures = listOf(
                ToolEnvelopeFailure(
                    reason = ToolEnvelopeFailureReason.MalformedEnvelope,
                    original = malformed,
                ),
            ),
            visiblePrefix = "",
            visibleSuffix = "",
        )
        val service = createService()

        val result = service.completeSingleTurn(
            sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello")),
            systemPrompt = "System",
            availableTools = emptyList(),
            allowToolCall = true,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
        )

        assertEquals(malformed, result.assistantText)
        assertEquals(AgentFinishReason.Stop, result.finishReason)
        assertTrue(result.toolCalls.isEmpty())
        assertEquals(0, fakePort.parseCalls.size)
    }
}
