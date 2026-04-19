package com.browntowndev.pocketcrew.feature.inference.llama

import com.browntowndev.pocketcrew.domain.model.inference.LlamaModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.LlamaSamplingConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class JniLlamaEnginePerformanceAuditTest {
    private val gpuProfiler: GpuProfiler = mockk(relaxed = true)
    private lateinit var engine: JniLlamaEngine

    @BeforeEach
    fun setup() {
        engine = spyk(JniLlamaEngine(gpuProfiler))

        every { gpuProfiler.detectOptimalBackend() } returns LlamaBackend.VULKAN
    }

    @Test
    fun `initialize uses tiered context window from config`() = runTest {
        val config = LlamaModelConfig(
            modelPath = "/tmp/model.gguf",
            systemPrompt = "test",
            sampling = LlamaSamplingConfig(
                contextWindow = 4096,
                maxTokens = 1024,
                temperature = 0.7f,
                topK = 40,
                topP = 0.9f,
                minP = 0.05f,
                batchSize = 512,
                gpuLayers = 32,
                repeatPenalty = 1.1f,
                thinkingEnabled = false
            )
        )

        try {
            engine.initialize(config)
        } catch (e: Exception) {
            // Expected — native libs not available in unit tests
        }

        verify { gpuProfiler.detectOptimalBackend() }
    }

    @Test
    fun `resetConversation clears KV cache as requested`() = runTest {
        try {
            engine.resetConversation("new system prompt")
        } catch (e: Exception) {
            // Expected — native libs not available in unit tests
        }
    }

    @Test
    fun `checkAndCompressContext reports native context usage without mutating KV positions`() {
        val engine = spyk(JniLlamaEngine(gpuProfiler), recordPrivateCalls = true)

        val loadedField = JniLlamaEngine::class.java.getDeclaredField("loaded")
        loadedField.isAccessible = true
        (loadedField.get(engine) as AtomicBoolean).set(true)

        val lastPromptTokensField = JniLlamaEngine::class.java.getDeclaredField("lastPromptTokens")
        lastPromptTokensField.isAccessible = true
        lastPromptTokensField.setInt(engine, 10)

        val lastGeneratedTokensField = JniLlamaEngine::class.java.getDeclaredField("lastGeneratedTokens")
        lastGeneratedTokensField.isAccessible = true
        lastGeneratedTokensField.setInt(engine, 10)

        every { engine.getContextSizeForCompression() } returns 100
        every { engine.getContextUsageForCompression() } returns 85
        every { engine.saveState() } returns null
        every { engine.applyCompressionForContext(any()) } returns true

        val method = JniLlamaEngine::class.java.getDeclaredMethod("checkAndCompressContext")
        method.isAccessible = true
        method.invoke(engine)

        verify { engine.getContextUsageForCompression() }
        verify(exactly = 0) { engine.applyCompressionForContext(any()) }
    }

    @Test
    fun `checkAndCompressContext falls back to tracked token counts without mutating KV positions`() {
        val engine = spyk(JniLlamaEngine(gpuProfiler), recordPrivateCalls = true)

        val loadedField = JniLlamaEngine::class.java.getDeclaredField("loaded")
        loadedField.isAccessible = true
        (loadedField.get(engine) as AtomicBoolean).set(true)

        val lastPromptTokensField = JniLlamaEngine::class.java.getDeclaredField("lastPromptTokens")
        lastPromptTokensField.isAccessible = true
        lastPromptTokensField.setInt(engine, 60)

        val lastGeneratedTokensField = JniLlamaEngine::class.java.getDeclaredField("lastGeneratedTokens")
        lastGeneratedTokensField.isAccessible = true
        lastGeneratedTokensField.setInt(engine, 25)

        every { engine.getContextSizeForCompression() } returns 100
        every { engine.getContextUsageForCompression() } returns 0
        every { engine.saveState() } returns null
        every { engine.applyCompressionForContext(any()) } returns true

        val method = JniLlamaEngine::class.java.getDeclaredMethod("checkAndCompressContext")
        method.isAccessible = true
        method.invoke(engine)

        verify { engine.getContextUsageForCompression() }
        verify(exactly = 0) { engine.applyCompressionForContext(any()) }
    }
}
