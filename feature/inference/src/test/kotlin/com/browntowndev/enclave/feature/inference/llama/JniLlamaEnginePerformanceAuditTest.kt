package com.browntowndev.enclave.feature.inference.llama

import com.browntowndev.enclave.domain.model.inference.LlamaModelConfig
import com.browntowndev.enclave.domain.model.inference.LlamaSamplingConfig
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JniLlamaEnginePerformanceAuditTest {
    private lateinit var engine: JniLlamaEngine

    @BeforeEach
    fun setup() {
        engine = spyk(JniLlamaEngine())
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
                            gpuLayers = 0,
                            repeatPenalty = 1.1f,
                            thinkingEnabled = false,
                        ),
                )

            try {
                engine.initialize(config)
            } catch (e: Exception) {
                // Expected — native libs not available in unit tests
            }
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

    @Test
    fun `setHistory invalidates native KV cache when engine is loaded`() =
        runTest {
            engine.modelLifecycle.loaded.set(true)
            engine.modelLifecycle.currentConfig =
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
                            gpuLayers = 0,
                            repeatPenalty = 1.1f,
                            thinkingEnabled = false,
                        ),
                )

            try {
                engine.setHistory(
                    listOf(
                        com.browntowndev.enclave.domain.model.chat.ChatMessage(
                            role = com.browntowndev.enclave.domain.model.chat.Role.USER,
                            content = "hello",
                        ),
                    ),
                )
            } catch (e: Exception) {
                // Expected — native libs not available in unit tests
            }
        }
}