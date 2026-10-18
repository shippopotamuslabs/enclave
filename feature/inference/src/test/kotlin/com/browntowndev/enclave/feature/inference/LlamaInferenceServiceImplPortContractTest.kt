package com.browntowndev.enclave.feature.inference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationEvent
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeBatch
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeFailure
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeFailureReason
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeField
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.domain.util.ToolEnvelopeParser
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TOOL-PARSE-001 LI-01 — `LlamaInferenceServiceImpl.completeSingleTurn` maps
 * the [com.browntowndev.enclave.domain.port.inference.ToolEnvelopeParserPort]'s
 * `successes` directly to `AgentRawToolCall`s.
 *
 * The placeholder text `"TWO_ENVELOPE_TEXT"` proves the port is consulted —
 * the legacy in-process regex finds zero envelopes in that string, so
 * red asserts the new mapping path (port → successes → toolCalls) is in
 * place.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class LlamaInferenceServiceImplPortContractTest {
    private val sessionManager = FakeLlamaChatSessionManager()
    private val loggingPort = FakeLoggingPort()
    private val localModelRepository = FakeLocalModelRepository()
    private val activeModelProvider = FakeActiveModelProvider()
    private lateinit var context: Context
    private lateinit var fakePort: FakeToolEnvelopeParserPort

    private val engineFake: FakeLlamaEnginePort get() = sessionManager.engineFake

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        activeModelProvider.configuration =
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
        runBlocking {
            val asset =
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
            localModelRepository.upsertLocalAsset(asset)
            localModelRepository.upsertLocalConfiguration(
                LocalModelConfiguration(
                    id = LocalModelConfigurationId("7"),
                    localModelId = asset.metadata.id,
                    displayName = "Test Model",
                    maxTokens = 512,
                    contextWindow = 4096,
                    temperature = 0.7,
                    topP = 0.9,
                    topK = 40,
                    repetitionPenalty = 1.1,
                    systemPrompt = "System",
                ),
            )
        }

        fakePort = FakeToolEnvelopeParserPort()
        ToolEnvelopeParser.portProvider = { fakePort }
    }

    @AfterEach
    fun tearDown() {
        ToolEnvelopeParser.portProvider = null
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
    fun li01CompleteSingleTurnMapsPortSuccessesToAgentRawToolCalls() =
        runTest {
            val placeholder = "TWO_ENVELOPE_TEXT"
            engineFake.generateWithOptionsResult =
                flowOf(
                    GenerationEvent.Completed(placeholder),
                )

            fakePort.parseResponses[placeholder] =
                ToolEnvelopeBatch(
                    successes =
                        listOf(
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

            assertEquals(placeholder, result.assistantText)
            assertTrue(result.toolCalls.isEmpty())
            assertEquals(AgentFinishReason.Stop, result.finishReason)
            assertEquals(0, fakePort.parseCalls.size)
        }

    @Test
    fun `FB-P-012 Llama text only output maps to Stop`() =
        runTest {
            engineFake.generateWithOptionsResult =
                flowOf(
                    GenerationEvent.Completed("Local final answer"),
                )
            fakePort.parseResponses["Local final answer"] =
                ToolEnvelopeBatch(
                    successes = emptyList(),
                    failures = emptyList(),
                    visiblePrefix = "Local final answer",
                    visibleSuffix = "",
                )
            val service = createService()

            val result =
                service.completeSingleTurn(
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
    fun `FB-P-013 Llama parser failure remains exceptional`() =
        runTest {
            val malformed = "<|DSML|tool_calls>broken"
            engineFake.generateWithOptionsResult =
                flowOf(
                    GenerationEvent.Completed(malformed),
                )
            fakePort.parseResponses[malformed] =
                ToolEnvelopeBatch(
                    successes = emptyList(),
                    failures =
                        listOf(
                            ToolEnvelopeFailure(
                                reason = ToolEnvelopeFailureReason.MalformedEnvelope,
                                original = malformed,
                            ),
                        ),
                    visiblePrefix = "",
                    visibleSuffix = "",
                )
            val service = createService()

            val result =
                service.completeSingleTurn(
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
