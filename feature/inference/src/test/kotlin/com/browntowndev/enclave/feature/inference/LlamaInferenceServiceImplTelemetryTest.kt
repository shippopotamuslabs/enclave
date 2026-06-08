package com.browntowndev.enclave.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationEvent
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySummaryBuilder
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.llama.LlamaChatSessionManager
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class LlamaInferenceServiceImplTelemetryTest {
    private lateinit var sessionManager: LlamaChatSessionManager
    private lateinit var loggingPort: LoggingPort
    private lateinit var localModelRepository: LocalModelRepositoryPort
    private lateinit var activeModelProvider: ActiveModelProviderPort
    private lateinit var context: Context
    private lateinit var telemetrySupport: InferenceTelemetrySupport

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        sessionManager = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)
        localModelRepository = mockk()
        activeModelProvider = mockk()
        context = mockk(relaxed = true)
        every { context.getExternalFilesDir(null) } returns File("/tmp")

        telemetrySupport = testInferenceTelemetrySupport()

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
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `completeSingleTurn captures timing native token counts and kv prefix cache hit rate`() =
        runTest {
            coEvery { sessionManager.streamAssistantResponseWithOptions(any()) } returns
                flowOf(
                    GenerationEvent.Token("Hel"),
                    GenerationEvent.Token("lo"),
                    GenerationEvent.Completed(
                        fullText = "Hello",
                        promptTokens = 100,
                        generatedTokens = 42,
                        prefixReusedTokens = 80,
                    ),
                )

            val service =
                LlamaInferenceServiceImpl(
                    sessionManager = sessionManager,
                    processThinkingTokens = ProcessThinkingTokensUseCase(),
                    loggingPort = loggingPort,
                    localModelRepository = localModelRepository,
                    activeModelProvider = activeModelProvider,
                    context = context,
                    modelType = ModelType.FAST,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                    telemetrySupport = telemetrySupport,
                )

            val result =
                service.completeSingleTurn(
                    sessionMessages =
                        listOf(
                            AgentLoopMessage(id = "u1", role = com.browntowndev.enclave.domain.model.chat.Role.USER, content = "hi"),
                        ),
                    systemPrompt = "You are helpful.",
                    contextSidecars = null,
                    availableTools = emptyList(),
                    allowToolCall = false,
                    onTextToken = {},
                    onThinkingToken = {},
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            modelType = ModelType.FAST,
                            modelId = "test-llama",
                            chatId = ChatId("chat-1"),
                            assistantMessageId = MessageId("assistant-1"),
                        ),
                    onLifecycleEvent = {},
                )

            val telemetry = result.telemetry
            assertNotNull(telemetry)
            assertEquals(42, telemetry!!.outputTokens)
            assertEquals(100, telemetry.inputTokens)
            assertEquals(80, telemetry.cacheReadInputTokens)
            assertNotNull(telemetry.firstOutputTokenAtMs)

            val summary =
                TelemetrySummaryBuilder.buildMessageSummary(
                    messageId = MessageId("assistant-1"),
                    rows = listOf(telemetry),
                )
            assertEquals(80.0, summary.cacheHitRatePercent)
        }
}
