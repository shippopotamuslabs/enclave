package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlamaEnginePort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Defense Scenario 7 (test_spec.md §4): `sendPrompt(options)` emits `InferenceEvent.Error`
 * when `ensureModelLoaded()` fails — parity with the default overload.
 *
 * Current gap: the default overload wraps `ensureModelLoaded()` in try/catch and emits
 * `InferenceEvent.Error`. The new overload calls `ensureModelLoaded()` **without** a try/catch,
 * so exceptions propagate as unhandled flow errors.
 */
class LlamaInferenceServiceImplSendPromptOptionsErrorTest {

    private lateinit var processThinkingTokens: ProcessThinkingTokensUseCase
    private lateinit var sessionManager: LlamaChatSessionManager
    private lateinit var mockModelRegistry: ModelRegistryPort
    private lateinit var mockEngine: LlamaEnginePort
    private lateinit var mockContext: Context
    private lateinit var mockLoggingPort: com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
    private lateinit var service: LlamaInferenceServiceImpl

    private fun createService() = LlamaInferenceServiceImpl(
        sessionManager = sessionManager,
        processThinkingTokens = processThinkingTokens,
        loggingPort = mockLoggingPort,
        modelRegistry = mockModelRegistry,
        context = mockContext
    )

    @BeforeEach
    fun setup() {
        mockModelRegistry = mockk<ModelRegistryPort>(relaxed = true).apply {
            coEvery { getRegisteredAsset(ModelType.FAST) } throws IllegalStateException("No registered asset")
        }
        mockEngine = mockk(relaxed = true)
        mockLoggingPort = mockk(relaxed = true)

        val mockFilesDir = java.io.File("/fake/files/dir")
        mockContext = mockk<Context>(relaxed = true)
        every { mockContext.getExternalFilesDir(null) } returns mockFilesDir

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

    @Nested
    inner class `Defense Scenario 7 — error handling parity` {

        @Test
        fun `sendPrompt without options emits Error event when ensureModelLoaded fails`() = runTest {
            // Default overload wraps ensureModelLoaded in try/catch
            coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } throws
                IllegalStateException("No registered asset")

            val events = mutableListOf<InferenceEvent>()
            service.sendPrompt("hello", closeConversation = false).collect { events.add(it) }

            val errors = events.filterIsInstance<InferenceEvent.Error>()
            assertTrue(errors.isNotEmpty(), "Default overload must emit Error event")
        }

        @Test
        fun `sendPrompt with options emits Error event when ensureModelLoaded fails`() = runTest {
            // Options overload currently does NOT wrap ensureModelLoaded — this test will fail
            coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } throws
                IllegalStateException("No registered asset")

            val options = GenerationOptions(reasoningBudget = 2048)
            val events = mutableListOf<InferenceEvent>()

            try {
                service.sendPrompt("hello", options, closeConversation = false).collect {
                    events.add(it)
                }
            } catch (e: IllegalStateException) {
                fail<Unit>(
                    "sendPrompt(options) must catch ensureModelLoaded exceptions and emit " +
                    "InferenceEvent.Error instead of propagating the exception. Got: ${e.message}"
                )
            }

            val errors = events.filterIsInstance<InferenceEvent.Error>()
            assertTrue(errors.isNotEmpty(), "sendPrompt(options) must emit Error event when ensureModelLoaded fails")
        }
    }
}
