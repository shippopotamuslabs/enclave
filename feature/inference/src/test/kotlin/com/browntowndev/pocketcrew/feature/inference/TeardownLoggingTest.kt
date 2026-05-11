package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import app.cash.turbine.test
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeardownLoggingTest {

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
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
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
        every { mockConversation.isAlive } returns true

        // Configure a valid model setup
        val configId = LocalModelConfigurationId("config-1")
        val activeConfig = mockk<ActiveModelConfiguration>(relaxed = true)
        every { activeConfig.id } returns configId
        every { activeConfig.isLocal } returns true
        coEvery { activeModelProvider.getActiveConfiguration(any()) } returns activeConfig
        val asset = mockk<com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset>(relaxed = true)
        coEvery { localModelRepository.getAssetByConfigId(configId) } returns asset
        every { asset.metadata.localFileName } returns "model.bin"
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `engine initialization failure teardown is logged`() = runTest {
        // Given: engine initialization throws
        val initError = RuntimeException("Init failed")
        every { mockEngine.initialize() } throws initError
        
        // And: teardown throws
        val teardownError = RuntimeException("Teardown failed")
        every { mockEngine.close() } throws teardownError

        // When: getConversation is called
        assertThrows(RuntimeException::class.java) {
            runBlocking {
                manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})
            }
        }

        // Then: Log.w is called for the teardown failure
        verify {
            Log.w(
                "ConversationManager",
                "Failed to close engine during error cleanup",
                teardownError
            )
        }
    }

    @Test
    fun `engine initialization failure teardown logged and original error propagated`() = runTest {
        // Given: engine initialization throws
        val initError = RuntimeException("ORIGINAL_INIT_ERROR")
        every { mockEngine.initialize() } throws initError
        
        // And: teardown throws
        val teardownError = RuntimeException("Teardown failed")
        every { mockEngine.close() } throws teardownError

        // When: getConversation is called
        val exception = assertThrows(RuntimeException::class.java) {
            runBlocking {
                manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})
            }
        }

        // Then: The original error is propagated (wrapped in IllegalStateException)
        var rootCause: Throwable? = exception
        while (rootCause?.cause != null && rootCause.cause != rootCause) {
            rootCause = rootCause.cause
        }
        assert(rootCause?.message == "ORIGINAL_INIT_ERROR")
        
        // And: Teardown failure was logged
        verify { Log.w(any(), match { it.contains("cleanup") }, teardownError) }
    }

    @Test
    fun `inference reset teardown failure is logged`() = runTest {
        // Given: Inference service with mocked manager
        val mockManager = mockk<ConversationManager>(relaxed = true)
        val service = LiteRtInferenceServiceImpl(
            conversationManager = mockManager,
            processThinkingTokens = mockk(relaxed = true),
            toolExecutionEventPort = mockk(relaxed = true),
            modelType = ModelType.FAST
        )
        
        // And: Inference fails, requiring reset
        coEvery { mockManager.getConversation(any(), any(), any()) } throws RuntimeException("Inference failed")
        
        // And: reset teardown fails
        val teardownError = RuntimeException("Reset teardown failed")
        coEvery { mockManager.closeEngine() } throws teardownError

        // When: inference is attempted
        service.sendPrompt(
            prompt = "test",
            options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
            closeConversation = false
        ).test {
            // Flow will eventually emit an error event and then finish
            // Note: Loading might not be emitted if getConversation throws before it calls the callback
            val event = awaitItem()
            assert(event is InferenceEvent.Error)
            awaitComplete()
        }

        // Then: Log.w is called for the reset teardown failure
        verify {
            Log.w(
                "LiteRtInferenceService",
                "Failed to close engine during inference reset",
                teardownError
            )
        }
    }
}

