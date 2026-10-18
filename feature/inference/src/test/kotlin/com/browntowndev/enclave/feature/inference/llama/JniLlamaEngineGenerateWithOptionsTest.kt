package com.browntowndev.enclave.feature.inference.llama

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.LlamaModelConfig
import com.browntowndev.enclave.domain.model.inference.LlamaSamplingConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.browntowndev.enclave.domain.model.chat.ChatMessage as DomainChatMessage

class JniLlamaEngineGenerateWithOptionsTest {
    /**
     * Recording subclass fake for the owned [JniLlamaEngine]: captures every
     * `startCompletion` invocation instead of crossing into native code, and
     * completes the generation synchronously through the supplied callback.
     */
    private class RecordingJniLlamaEngine : JniLlamaEngine() {
        data class CompletionCall(
            val roles: List<String>,
            val contents: List<String>,
            val imagePaths: List<String>,
            val temperature: Float,
            val topK: Int,
            val topP: Float,
            val minP: Float,
            val maxTokens: Int,
            val repeatPenalty: Float,
            val penaltyFreq: Float,
            val penaltyPresent: Float,
            val reasoningBudget: Int,
            val continuation: Boolean,
            val prevMessageCount: Int,
        )

        val completionCalls = mutableListOf<CompletionCall>()

        override fun startCompletion(
            roles: Array<String>,
            contents: Array<String>,
            imagePaths: Array<String>,
            temperature: Float,
            topK: Int,
            topP: Float,
            minP: Float,
            maxTokens: Int,
            repeatPenalty: Float,
            penaltyFreq: Float,
            penaltyPresent: Float,
            reasoningBudget: Int,
            callback: NativeTokenCallback,
            continuation: Boolean,
            prevMessageCount: Int,
        ) {
            completionCalls +=
                CompletionCall(
                    roles = roles.toList(),
                    contents = contents.toList(),
                    imagePaths = imagePaths.toList(),
                    temperature = temperature,
                    topK = topK,
                    topP = topP,
                    minP = minP,
                    maxTokens = maxTokens,
                    repeatPenalty = repeatPenalty,
                    penaltyFreq = penaltyFreq,
                    penaltyPresent = penaltyPresent,
                    reasoningBudget = reasoningBudget,
                    continuation = continuation,
                    prevMessageCount = prevMessageCount,
                )
            callback.onComplete(10, 20, 8)
        }
    }

    @Nested
    inner class DefenseScenario5 {
        @Test
        fun `generateWithOptions method exists on JniLlamaEngine`() {
            val method = JniLlamaEngine::class.java.methods.find { it.name == "generateWithOptions" }
            assertTrue(method != null, "JniLlamaEngine must have generateWithOptions(GenerationOptions) method")
        }

        private fun setupEngineWithMocks(): RecordingJniLlamaEngine {
            val engine = RecordingJniLlamaEngine()

            engine.modelLifecycle.loaded.set(true)
            engine.modelLifecycle.currentConfig =
                LlamaModelConfig(
                    modelPath = "test",
                    systemPrompt = "test",
                    sampling =
                        LlamaSamplingConfig(
                            contextWindow = 4096,
                            thinkingEnabled = true, // By default thinking
                            temperature = 0.7f,
                            topK = 40,
                            topP = 0.9f,
                            minP = 0.05f,
                            maxTokens = 100,
                            batchSize = 10,
                            gpuLayers = 0,
                            repeatPenalty = 1.0f,
                        ),
                )

            return engine
        }

        @Test
        fun `reasoningBudget=0 yields penalties 0_05f and regular timeout`() =
            runTest {
                val engine = setupEngineWithMocks()
                try {
                    engine.generateWithOptions(GenerationOptions(reasoningBudget = 0)).toList()
                } catch (e: Exception) {
                    // Expected: exception swallowed — only verifying the recorded interaction
                }

                val call = engine.completionCalls.single()
                assertEquals(0.05f, call.penaltyFreq)
                assertEquals(0.05f, call.penaltyPresent)
                assertEquals(0, call.reasoningBudget)
            }

        @Test
        fun `reasoningBudget=2048 yields penalties 0_0f and thinking timeout`() =
            runTest {
                val engine = setupEngineWithMocks()
                try {
                    engine.generateWithOptions(GenerationOptions(reasoningBudget = 2048)).toList()
                } catch (e: Exception) {
                    // Expected: exception swallowed — only verifying the recorded interaction
                }

                val call = engine.completionCalls.single()
                assertEquals(0.0f, call.penaltyFreq)
                assertEquals(0.0f, call.penaltyPresent)
                assertEquals(2048, call.reasoningBudget)
            }

        @Test
        fun `options temperature overrides config default`() =
            runTest {
                val engine = setupEngineWithMocks()
                try {
                    engine.generateWithOptions(GenerationOptions(reasoningBudget = 0, temperature = 0.5f)).toList()
                } catch (e: Exception) {
                    // Expected: exception swallowed — only verifying the recorded interaction
                }

                val call = engine.completionCalls.single()
                assertEquals(0.5f, call.temperature)
            }

        @Test
        fun `generateWithOptions passes overridden system prompt into native completion`() =
            runTest {
                val engine = setupEngineWithMocks()
                engine.startConversation("Base system prompt")
                engine.appendMessage(DomainChatMessage(role = Role.USER, content = "User question"))

                try {
                    engine
                        .generateWithOptions(
                            GenerationOptions(
                                reasoningBudget = 0,
                                systemPrompt = "Use tools when needed.\n<tool_call>{\"name\":\"web_search\",\"arguments\":{\"query\":\"...\"}}</tool_call>",
                            ),
                        ).toList()
                } catch (e: Exception) {
                    // Expected: exception swallowed — only verifying the recorded interaction
                }

                val contents = engine.completionCalls.single().contents
                assertEquals(
                    "Use tools when needed.\n<tool_call>{\"name\":\"web_search\",\"arguments\":{\"query\":\"...\"}}</tool_call>",
                    contents.first(),
                )
                assertEquals("User question", contents[1])
            }

        @Test
        fun `generateWithOptions prepends media markers and forwards image paths`() =
            runTest {
                val engine = setupEngineWithMocks()
                engine.startConversation("Base system prompt")
                engine.appendMessage(DomainChatMessage(role = Role.USER, content = "Describe this image"))

                try {
                    engine
                        .generateWithOptions(
                            GenerationOptions(
                                reasoningBudget = 0,
                                imageUris = listOf("file:///tmp/cat.jpg", "file:///tmp/dog.jpg"),
                            ),
                        ).toList()
                } catch (_: Exception) {
                    // Expected: exception swallowed — only verifying the recorded interaction
                }

                val call = engine.completionCalls.single()
                assertTrue(call.contents.last().startsWith("<__media__><__media__>Describe this image"))
                assertEquals(listOf("file:///tmp/cat.jpg", "file:///tmp/dog.jpg"), call.imagePaths)
            }
    }
}
