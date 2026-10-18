// MockK is used only for LiteRT-LM SDK types (com.google.ai.edge.litertlm.*), a third-party SDK boundary.
@file:Suppress("ForbiddenMockKInTests")

package com.browntowndev.enclave.feature.inference

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
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
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class TeardownLoggingTest {
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

        // Configure a valid model setup
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
    }

    @Test
    fun `engine initialization failure teardown is logged`() =
        runTest {
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
            assertTrue(
                warnLogLogged(
                    tag = "ConversationManager",
                    message = "Failed to close engine during error cleanup",
                    throwable = teardownError,
                ),
            )
        }

    @Test
    fun `engine initialization failure teardown logged and original error propagated`() =
        runTest {
            // Given: engine initialization throws
            val initError = RuntimeException("ORIGINAL_INIT_ERROR")
            every { mockEngine.initialize() } throws initError

            // And: teardown throws
            val teardownError = RuntimeException("Teardown failed")
            every { mockEngine.close() } throws teardownError

            // When: getConversation is called
            val exception =
                assertThrows(RuntimeException::class.java) {
                    runBlocking {
                        manager.getConversation(ModelType.FAST, null, onLoadingStarted = {})
                    }
                }

            // Then: The original error is propagated (wrapped in IllegalStateException)
            var rootCause: Throwable? = exception
            while (rootCause?.cause != null && rootCause.cause != rootCause) {
                rootCause = rootCause.cause
            }
            assertTrue(rootCause?.message == "ORIGINAL_INIT_ERROR")

            // And: Teardown failure was logged
            assertTrue(
                ShadowLog
                    .getLogsForTag("ConversationManager")
                    .any {
                        it.type == Log.WARN && it.msg.contains("cleanup") && it.throwable === teardownError
                    },
            )
        }

    @Test
    fun `inference reset teardown failure is logged`() =
        runTest {
            // Given: Inference service with fake manager
            val fakeManager = FakeConversationManager()
            val service =
                LiteRtInferenceServiceImpl(
                    conversationManager = fakeManager,
                    processThinkingTokens = ProcessThinkingTokensUseCase(),
                    toolExecutionEventPort = FakeToolExecutionEventPort(),
                    modelType = ModelType.FAST,
                )

            // And: Inference fails, requiring reset
            fakeManager.getConversationFailure = RuntimeException("Inference failed")

            // And: reset teardown fails
            val teardownError = RuntimeException("Reset teardown failed")
            fakeManager.closeEngineFailure = teardownError

            // When: inference is attempted
            service
                .sendPrompt(
                    prompt = "test",
                    options = GenerationOptions(reasoningBudget = 0, modelType = ModelType.FAST),
                    closeConversation = false,
                ).test {
                    // Flow will eventually emit an error event and then finish
                    // Note: Loading might not be emitted if getConversation throws before it calls the callback
                    val event = awaitItem()
                    assertTrue(event is InferenceEvent.Error)
                    awaitComplete()
                }

            // Then: Log.w is called for the reset teardown failure
            assertTrue(
                warnLogLogged(
                    tag = "LiteRtInferenceService",
                    message = "Failed to close engine during inference reset",
                    throwable = teardownError,
                ),
            )
        }

    private fun warnLogLogged(
        tag: String,
        message: String,
        throwable: Throwable,
    ): Boolean =
        ShadowLog
            .getLogsForTag(tag)
            .any { it.type == Log.WARN && it.msg == message && it.throwable === throwable }

    private fun registerAsset(
        configId: LocalModelConfigurationId,
        localFileName: String,
    ) {
        val assetId = LocalModelId("asset-${configId.value}")
        kotlinx.coroutines.runBlocking {
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
}
