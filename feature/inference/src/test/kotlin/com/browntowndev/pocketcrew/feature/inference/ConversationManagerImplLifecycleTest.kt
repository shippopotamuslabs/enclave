package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertSame

class ConversationManagerImplLifecycleTest {

    private val context = mockk<Context>(relaxed = true)
    private val localModelRepository = mockk<LocalModelRepositoryPort>(relaxed = true)
    private val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)

    private lateinit var manager: ConversationManagerImpl
    private val mockEngine = mockk<Engine>(relaxed = true)
    private val mockConversation = mockk<Conversation>(relaxed = true)

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        manager = spyk(ConversationManagerImpl(
            context = context,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
        ))

        // Default mock behaviors
        every { manager.createEngine(any(), any(), any(), any(), any()) } returns mockEngine
        every { manager.getModelPath(any()) } returns "/mock/path"
        every { mockEngine.createConversation(any()) } returns mockConversation
        every { mockEngine.initialize() } just Runs
        every { mockConversation.isAlive } returns true
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `getConversation reuses same instance when config is identical`() = runTest {
        // Given
        val configId = LocalModelConfigurationId("config-1")
        val activeConfig = mockk<ActiveModelConfiguration>(relaxed = true)
        every { activeConfig.id } returns configId
        every { activeConfig.isLocal } returns true
        every { activeConfig.topK } returns null
        every { activeConfig.topP } returns null
        every { activeConfig.temperature } returns null
        coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns activeConfig
        
        val asset = mockk<com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset>(relaxed = true)
        coEvery { localModelRepository.getAssetByConfigId(configId) } returns asset
        every { asset.metadata.localFileName } returns "model.bin"

        // When
        val conv1 = manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})
        val conv2 = manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})

        // Then
        assertSame(conv1, conv2)
        verify(exactly = 1) { manager.createEngine(any(), any(), any(), any(), any()) }
        verify(exactly = 1) { mockEngine.createConversation(any()) }
    }

    @Test
    fun `getConversation recreates conversation when signature changes`() = runTest {
        // Given
        val configId = LocalModelConfigurationId("config-1")
        val activeConfig = mockk<ActiveModelConfiguration>(relaxed = true)
        every { activeConfig.id } returns configId
        every { activeConfig.isLocal } returns true
        every { activeConfig.topK } returns null
        every { activeConfig.topP } returns null
        every { activeConfig.temperature } returns null
        coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns activeConfig
        
        val asset = mockk<com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset>(relaxed = true)
        coEvery { localModelRepository.getAssetByConfigId(configId) } returns asset
        every { asset.metadata.localFileName } returns "model.bin"

        // When
        manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})
        
        // Change signature by providing different options
        val options = GenerationOptions(reasoningBudget = 0, temperature = 0.5f)
        manager.getConversation(ModelType.FAST, options, onLoadingStarted = {})

        // Then
        // Conversation should be recreated, but Engine should be reused
        verify(exactly = 1) { manager.createEngine(any(), any(), any(), any(), any()) }
        verify(exactly = 2) { mockEngine.createConversation(any()) }
        verify(exactly = 1) { mockConversation.close() }
    }

    @Test
    fun `getConversation recreates engine when model path changes`() = runTest {
        // Given
        val configId1 = LocalModelConfigurationId("config-1")
        val activeConfig1 = mockk<ActiveModelConfiguration>(relaxed = true)
        every { activeConfig1.id } returns configId1
        every { activeConfig1.isLocal } returns true
        every { activeConfig1.topK } returns null
        every { activeConfig1.topP } returns null
        every { activeConfig1.temperature } returns null
        coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns activeConfig1
        
        val asset1 = mockk<com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset>(relaxed = true)
        coEvery { localModelRepository.getAssetByConfigId(configId1) } returns asset1
        every { asset1.metadata.localFileName } returns "model1.bin"
        every { manager.getModelPath("model1.bin") } returns "/path/1"

        // First call
        manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})

        // Change model path
        val configId2 = LocalModelConfigurationId("config-2")
        val activeConfig2 = mockk<ActiveModelConfiguration>(relaxed = true)
        every { activeConfig2.id } returns configId2
        every { activeConfig2.isLocal } returns true
        every { activeConfig2.topK } returns null
        every { activeConfig2.topP } returns null
        every { activeConfig2.temperature } returns null
        coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns activeConfig2
        
        val asset2 = mockk<com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset>(relaxed = true)
        coEvery { localModelRepository.getAssetByConfigId(configId2) } returns asset2
        every { asset2.metadata.localFileName } returns "model2.bin"
        every { manager.getModelPath("model2.bin") } returns "/path/2"

        // When
        manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})

        // Then
        verify(exactly = 2) { manager.createEngine(any(), any(), any(), any(), any()) }
        verify(exactly = 1) { mockEngine.close() }
    }
}
