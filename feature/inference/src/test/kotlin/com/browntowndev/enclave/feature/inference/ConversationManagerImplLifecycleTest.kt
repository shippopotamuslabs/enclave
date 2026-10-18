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
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ConversationManagerImplLifecycleTest {
    private val localModelRepository = FakeLocalModelRepository()
    private val activeModelProvider = FakeActiveModelProvider()
    private val loggingPort = FakeLoggingPort()
    private val toolExecutor = FakeToolExecutor()

    private lateinit var manager: ConversationManagerImpl
    private val mockEngine = mockk<Engine>(relaxed = true)
    private val mockConversation = mockk<Conversation>(relaxed = true)

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

    @Test
    fun `getConversation reuses same instance when config is identical`() =
        runTest {
            // Given
            val configId = LocalModelConfigurationId("config-1")
            activeModelProvider.configuration = activeConfiguration(configId)
            registerAsset(configId, "model.bin")

            // When
            val conv1 = manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})
            val conv2 = manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})

            // Then
            assertSame(conv1, conv2)
            verify(exactly = 1) { mockEngine.initialize() }
            verify(exactly = 1) { mockEngine.createConversation(any()) }
        }

    @Test
    fun `getConversation recreates conversation when signature changes`() =
        runTest {
            // Given
            val configId = LocalModelConfigurationId("config-1")
            activeModelProvider.configuration = activeConfiguration(configId)
            registerAsset(configId, "model.bin")

            // When
            manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})

            // Change signature by providing different options
            val options = GenerationOptions(reasoningBudget = 0, temperature = 0.5f)
            manager.getConversation(ModelType.FAST, options, onLoadingStarted = {})

            // Then
            // Conversation should be recreated, but Engine should be reused
            verify(exactly = 1) { mockEngine.initialize() }
            verify(exactly = 2) { mockEngine.createConversation(any()) }
            verify(exactly = 1) { mockConversation.close() }
        }

    @Test
    fun `getConversation recreates engine when model path changes`() =
        runTest {
            // Given
            val configId1 = LocalModelConfigurationId("config-1")
            activeModelProvider.configuration = activeConfiguration(configId1)
            registerAsset(configId1, "model1.bin")

            // First call
            manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})

            // Change model path
            val configId2 = LocalModelConfigurationId("config-2")
            activeModelProvider.configuration = activeConfiguration(configId2)
            registerAsset(configId2, "model2.bin")

            // When
            manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})

            // Then
            verify(exactly = 2) { mockEngine.initialize() }
            verify(exactly = 1) { mockEngine.close() }
        }

    private fun activeConfiguration(configId: LocalModelConfigurationId): ActiveModelConfiguration =
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
