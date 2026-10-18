package com.browntowndev.enclave.feature.inference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationEvent
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.telemetry.TelemetryProviderKey
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySummaryBuilder
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class LlamaInferenceServiceImplTelemetryTest {
    private val sessionManager = FakeLlamaChatSessionManager()
    private val loggingPort = FakeLoggingPort()
    private val localModelRepository = FakeLocalModelRepository()
    private val activeModelProvider = FakeActiveModelProvider()
    private lateinit var context: Context
    private lateinit var telemetrySupport: InferenceTelemetrySupport

    private val engineFake: FakeLlamaEnginePort get() = sessionManager.engineFake

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        telemetrySupport = testInferenceTelemetrySupport()

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
    }

    @Test
    fun `completeSingleTurn captures timing native token counts and kv prefix cache hit rate`() =
        runTest {
            engineFake.generateWithOptionsResult =
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
            assertEquals(180, telemetry.inputTokens)
            assertEquals(80, telemetry.cacheReadInputTokens)
            assertEquals("llama.cpp", telemetry.providerLabel)
            assertEquals(TelemetryProviderKey.LLAMA, telemetry.providerKey)
            assertNotNull(telemetry.firstOutputTokenAtMs)

            val summary =
                TelemetrySummaryBuilder.buildMessageSummary(
                    messageId = MessageId("assistant-1"),
                    rows = listOf(telemetry),
                )
            assertEquals(44.4, summary.cacheHitRatePercent)
        }
}
