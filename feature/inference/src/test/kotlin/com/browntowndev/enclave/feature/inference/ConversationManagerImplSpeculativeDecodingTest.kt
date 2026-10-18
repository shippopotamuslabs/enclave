// MockK is used only for LiteRT-LM SDK types (com.google.ai.edge.litertlm.*), a third-party SDK boundary.
@file:Suppress("ForbiddenMockKInTests")

package com.browntowndev.enclave.feature.inference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeToolExecutor
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@OptIn(ExperimentalApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ConversationManagerImplSpeculativeDecodingTest {
    private val localModelRepository = FakeLocalModelRepository()
    private val activeModelProvider = FakeActiveModelProvider()
    private val loggingPort = FakeLoggingPort()
    private val toolExecutor = FakeToolExecutor()
    private val mockEngine = mockk<Engine>(relaxed = true)
    private val mockConversation = mockk<Conversation>(relaxed = true)

    private lateinit var manager: ConversationManagerImpl

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        manager =
            ConversationManagerImpl(
                context = context,
                localModelRepository = localModelRepository,
                activeModelProvider = activeModelProvider,
                loggingPort = loggingPort,
                toolExecutor = toolExecutor,
                embeddingEnginePort = FakeEmbeddingEngine(),
            )

        manager.engineCreatorHolder.override = { _, _, _, _, _ -> mockEngine }

        every { mockEngine.createConversation(any()) } returns mockConversation
        every { mockConversation.isAlive } returns true
    }

    @AfterEach
    fun tearDown() {
        ExperimentalFlags.enableSpeculativeDecoding = false
        ExperimentalFlags.enableBenchmark = false
    }

    @Test
    fun `getConversation clears speculative decoding flag after engine initialization`() =
        runTest {
            val configId = LocalModelConfigurationId("config-1")
            activeModelProvider.configuration =
                ActiveModelConfiguration(
                    id = configId,
                    isLocal = true,
                    name = "Test Model",
                    modelId = configId.value,
                    systemPrompt = "System",
                    temperature = null,
                    topK = null,
                    topP = null,
                    maxTokens = 512,
                    minP = 0.0,
                    repetitionPenalty = 1.1,
                    contextWindow = 4096,
                    thinkingEnabled = false,
                )
            registerAsset(configId, "model.bin")
            ExperimentalFlags.enableSpeculativeDecoding = true

            manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})

            assertFalse(ExperimentalFlags.enableSpeculativeDecoding ?: false)
            assertFalse(ExperimentalFlags.enableBenchmark)
            verify(exactly = 1) { mockEngine.initialize() }
        }

    private suspend fun registerAsset(
        configId: LocalModelConfigurationId,
        localFileName: String,
    ) {
        val assetId = LocalModelId("asset-${configId.value}")
        localModelRepository.upsertLocalAsset(
            LocalModelAsset(
                metadata =
                    LocalModelMetadata(
                        id = assetId,
                        huggingFaceModelName = "test/model",
                        remoteFileName = localFileName,
                        localFileName = localFileName,
                        sha256 = "abc123",
                        sizeInBytes = 1024L,
                        modelFileFormat = ModelFileFormat.GGUF,
                    ),
                configurations = emptyList(),
            ),
        )
        localModelRepository.upsertLocalConfiguration(
            LocalModelConfiguration(
                id = configId,
                localModelId = assetId,
                displayName = "Test",
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
