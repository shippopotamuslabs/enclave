package com.browntowndev.enclave.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalApi::class)
class ConversationManagerImplSpeculativeDecodingTest {
    private val context = mockk<Context>(relaxed = true)
    private val localModelRepository = mockk<LocalModelRepositoryPort>(relaxed = true)
    private val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)
    private val mockEngine = mockk<Engine>(relaxed = true)
    private val mockConversation = mockk<Conversation>(relaxed = true)

    private lateinit var manager: ConversationManagerImpl

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        manager =
            spyk(
                ConversationManagerImpl(
                    context = context,
                    localModelRepository = localModelRepository,
                    activeModelProvider = activeModelProvider,
                    loggingPort = loggingPort,
                    toolExecutor = toolExecutor,
                embeddingEnginePort = mockk(relaxed = true),
                ),
            )

        manager.engineCreatorHolder.override = { _, _, _, _, _ -> mockEngine }

        every { manager.getModelPath(any()) } returns "/mock/path"
        every { mockEngine.createConversation(any()) } returns mockConversation
        every { mockEngine.initialize() } just Runs
        every { mockConversation.isAlive } returns true
    }

    @AfterEach
    fun tearDown() {
        ExperimentalFlags.enableSpeculativeDecoding = false
        unmockkStatic(Log::class)
    }

    @Test
    fun `getConversation clears speculative decoding flag after engine initialization`() =
        runTest {
            val configId = LocalModelConfigurationId("config-1")
            val activeConfig = mockk<ActiveModelConfiguration>(relaxed = true)
            every { activeConfig.id } returns configId
            every { activeConfig.isLocal } returns true
            every { activeConfig.topK } returns null
            every { activeConfig.topP } returns null
            every { activeConfig.temperature } returns null
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns activeConfig

            val asset = mockk<LocalModelAsset>(relaxed = true)
            coEvery { localModelRepository.getAssetByConfigId(configId) } returns asset
            every { asset.metadata.localFileName } returns "model.bin"
            ExperimentalFlags.enableSpeculativeDecoding = true

            manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})

            assertFalse(ExperimentalFlags.enableSpeculativeDecoding ?: false)
            verify(exactly = 1) { mockEngine.initialize() }
        }
}
