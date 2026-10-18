@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the OpenAI Java SDK surface (third-party SDK boundary).
package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptCacheJsonBreakLoggingTest {
    private val loggingPort = FakeLoggingPort()
    private val client = mockk<OpenAIClient>(relaxed = true)

    @Test
    fun deepSeekSecondTurn_emitsPromptCacheJsonBreak() =
        runTest {
            val service =
                object : DeepSeekInferenceServiceImpl(
                    client = client,
                    modelId = "deepseek-v4-flash",
                    modelType = ModelType.FAST,
                    baseUrl = "https://api.deepseek.com",
                    loggingPort = loggingPort,
                    toolStreamGatePort = FakeToolStreamGatePort(),
                ) {
                    override suspend fun streamChatCompletions(
                        params: ChatCompletionCreateParams,
                        chatId: ChatId?,
                        userMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                        assistantMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                        emitEvent: suspend (InferenceEvent) -> Unit,
                    ): StreamedOpenAiResponse =
                        StreamedOpenAiResponse(
                            emittedAny = true,
                            functionCalls = emptyList(),
                            responseId = "resp_1",
                            providerToolCallIds = emptyList(),
                            providerToolItemIds = emptyList(),
                            assistantMessageText = "ok",
                            inputTokens = 100,
                            cacheReadInputTokens = 80,
                        )
                }

            val chatId = ChatId("cache-json-break-test")
            val options =
                GenerationOptions(
                    reasoningBudget = 0,
                    chatId = chatId,
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hi"),
                    ),
                systemPrompt = "System",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = options,
            )
            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hi"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Hello"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "More"),
                    ),
                systemPrompt = "System",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = options,
            )

            val logMessages = loggingPort.debugCalls.map { it.second }
            assertTrue(logMessages.any { it.startsWith("PROMPT_CACHE ") })
            assertTrue(logMessages.any { it.startsWith("PROMPT_CACHE_JSON_BREAK ") })
        }
}
