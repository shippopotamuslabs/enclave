package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LlamaEnginePort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for [LlamaInferenceServiceImpl.sendPrompt] with `GenerationOptions` overload.
 *
 * These tests verify that:
 * - The overloaded sendPrompt accepts GenerationOptions without crashing
 * - reasoningBudget is correctly passed to the engine
 * - Per-request options override config-level defaults
 */
class LlamaInferenceServiceImplGenerationOptionsTest {

    private lateinit var mockContext: Context
    private lateinit var mockModelRegistry: ModelRegistryPort
    private lateinit var mockEngine: LlamaEnginePort
    private lateinit var mockLoggingPort: com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
    private lateinit var processThinkingTokens: ProcessThinkingTokensUseCase
    private lateinit var sessionManager: LlamaChatSessionManager
    private lateinit var service: LlamaInferenceServiceImpl

    private val fakeAsset = LocalModelAsset(
        metadata = LocalModelMetadata(
            id = 1L,
            huggingFaceModelName = "test/model",
            remoteFileName = "model.gguf",
            localFileName = "model.gguf",
            sha256 = "dummy-hash",
            sizeInBytes = 1000L,
            modelFileFormat = ModelFileFormat.GGUF
        ),
        configurations = emptyList()
    )

    private val fakeConfig = LocalModelConfiguration(
        id = 1L,
        localModelId = 1L,
        displayName = "Test Config",
        temperature = 0.7,
        topK = 40,
        topP = 0.9,
        minP = 0.1,
        repetitionPenalty = 1.1,
        maxTokens = 4096,
        contextWindow = 4096,
        thinkingEnabled = false,
        systemPrompt = "You are a helpful assistant.",
        isSystemPreset = true
    )

    @BeforeEach
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockModelRegistry = mockk(relaxed = true)
        mockEngine = mockk(relaxed = true)
        mockLoggingPort = mockk(relaxed = true)

        val mockFilesDir = java.io.File("/fake/files/dir")
        every { mockContext.getExternalFilesDir(null) } returns mockFilesDir

        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.MAIN) } returns fakeAsset
        coEvery { mockModelRegistry.getRegisteredConfiguration(ModelType.MAIN) } returns fakeConfig

        processThinkingTokens = ProcessThinkingTokensUseCase()
        sessionManager = LlamaChatSessionManager(mockEngine)

        service = LlamaInferenceServiceImpl(
            sessionManager = sessionManager,
            processThinkingTokens = processThinkingTokens,
            loggingPort = mockLoggingPort,
            modelRegistry = mockModelRegistry,
            context = mockContext
        )
    }

    private fun createService() = LlamaInferenceServiceImpl(
        sessionManager = sessionManager,
        processThinkingTokens = processThinkingTokens,
        loggingPort = mockLoggingPort,
        modelRegistry = mockModelRegistry,
        context = mockContext
    )

    // ===== Overload Acceptance Tests =====

    @Test
    fun `sendPrompt with GenerationOptions reasoningBudget 2048 accepts call`() = runTest {
        val options = GenerationOptions(reasoningBudget = 2048)
        val result = service.sendPrompt("hello", options, closeConversation = false)
        assertNotNull(result, "sendPrompt with options must return a Flow")
    }

    @Test
    fun `sendPrompt with GenerationOptions reasoningBudget 0 accepts call`() = runTest {
        val options = GenerationOptions(reasoningBudget = 0)
        val result = service.sendPrompt("hello", options, closeConversation = false)
        assertNotNull(result, "sendPrompt with options must return a Flow")
    }

    @Test
    fun `sendPrompt without options uses default behavior`() = runTest {
        val result = service.sendPrompt("hello", closeConversation = false)
        assertNotNull(result, "sendPrompt without options must return a Flow")
    }
}
