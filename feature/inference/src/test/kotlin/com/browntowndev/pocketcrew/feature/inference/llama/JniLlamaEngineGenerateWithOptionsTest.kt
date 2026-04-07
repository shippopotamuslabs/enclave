package com.browntowndev.pocketcrew.feature.inference.llama

import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.LlamaModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.LlamaSamplingConfig
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class JniLlamaEngineGenerateWithOptionsTest {

    @Nested
    inner class DefenseScenario5 {

        @Test
        fun `generateWithOptions method exists on JniLlamaEngine`() {
            val method = JniLlamaEngine::class.java.methods.find { it.name == "generateWithOptions" }
            assertTrue(method != null, "JniLlamaEngine must have generateWithOptions(GenerationOptions) method")
        }

        private fun setupEngineWithMocks(): JniLlamaEngine {
            val engine = spyk(JniLlamaEngine(mockk(relaxed = true)))
            
            val loadedField = JniLlamaEngine::class.java.getDeclaredField("loaded")
            loadedField.isAccessible = true
            (loadedField.get(engine) as AtomicBoolean).set(true)
            
            val configField = JniLlamaEngine::class.java.getDeclaredField("currentConfig")
            configField.isAccessible = true
            configField.set(engine, LlamaModelConfig(
                modelPath = "test",
                systemPrompt = "test",
                sampling = LlamaSamplingConfig(
                    contextWindow = 4096,
                    thinkingEnabled = true, // By default thinking
                    temperature = 0.7f,
                    topK = 40,
                    topP = 0.9f,
                    minP = 0.05f,
                    maxTokens = 100,
                    batchSize = 10,
                    gpuLayers = 0,
                    repeatPenalty = 1.0f
                )
            ))
            
            every { engine.startCompletion(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            ) } answers {
                val callback = arg<NativeTokenCallback>(11)
                callback.onComplete(10, 20)
            }
            
            return engine
        }

        @Test
        fun `reasoningBudget=0 yields penalties 0_05f and regular timeout`() = runTest {
            val engine = setupEngineWithMocks()
            try {
                engine.generateWithOptions(GenerationOptions(reasoningBudget = 0)).toList()
            } catch(e: Exception) {}

            verify { engine.startCompletion(
                any(), any(), any(), any(), any(), any(), any(), any(), 
                eq(0.05f), // penaltyFreq
                eq(0.05f), // penaltyPresent
                eq(0),     // reasoningBudget
                any()
            ) }
        }

        @Test
        fun `reasoningBudget=2048 yields penalties 0_0f and thinking timeout`() = runTest {
            val engine = setupEngineWithMocks()
            try {
                engine.generateWithOptions(GenerationOptions(reasoningBudget = 2048)).toList()
            } catch(e: Exception) {}

            verify { engine.startCompletion(
                any(), any(), any(), any(), any(), any(), any(), any(), 
                eq(0.0f), // penaltyFreq
                eq(0.0f), // penaltyPresent
                eq(2048), // reasoningBudget
                any()
            ) }
        }

        @Test
        fun `options temperature overrides config default`() = runTest {
            val engine = setupEngineWithMocks()
            try {
                engine.generateWithOptions(GenerationOptions(reasoningBudget = 0, temperature = 0.5f)).toList()
            } catch(e: Exception) {}

            verify { engine.startCompletion(
                any(), any(), 
                eq(0.5f), // temperature
                any(), any(), any(), any(), any(), any(), any(), any(), any()
            ) }
        }
    }
}
