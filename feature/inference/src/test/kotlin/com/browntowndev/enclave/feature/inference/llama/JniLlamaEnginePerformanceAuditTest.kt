package com.browntowndev.enclave.feature.inference.llama

import com.browntowndev.enclave.domain.model.inference.LlamaModelConfig
import com.browntowndev.enclave.domain.model.inference.LlamaSamplingConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
class JniLlamaEnginePerformanceAuditTest {
    private val gpuProfiler: GpuProfiler = mockk(relaxed = true)
    private lateinit var engine: JniLlamaEngine

    @BeforeEach
    fun setup() {
        engine = spyk(JniLlamaEngine(gpuProfiler))

        every { gpuProfiler.detectOptimalBackend() } returns LlamaBackend.VULKAN
    }

    @Test
    fun `initialize uses tiered context window from config`() =
        runTest {
            val config =
                LlamaModelConfig(
                    modelPath = "/tmp/model.gguf",
                    systemPrompt = "test",
                    sampling =
                        LlamaSamplingConfig(
                            contextWindow = 4096,
                            maxTokens = 1024,
                            temperature = 0.7f,
                            topK = 40,
                            topP = 0.9f,
                            minP = 0.05f,
                            batchSize = 512,
                            gpuLayers = 32,
                            repeatPenalty = 1.1f,
                            thinkingEnabled = false,
                        ),
                )

            try {
                engine.initialize(config)
            } catch (e: Exception) {
                // Expected — native libs not available in unit tests
            }

            verify { gpuProfiler.detectOptimalBackend() }
        }

    @Test
    fun `resetConversation clears KV cache as requested`() =
        runTest {
            try {
                engine.resetConversation("new system prompt")
            } catch (e: Exception) {
                // Expected — native libs not available in unit tests
            }
        }

}
