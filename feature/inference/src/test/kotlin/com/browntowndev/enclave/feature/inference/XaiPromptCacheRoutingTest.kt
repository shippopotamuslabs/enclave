@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the OpenAI Java SDK surface (third-party SDK boundary).
package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private typealias CapturedChatParams = MutableList<ChatCompletionCreateParams>

class XaiPromptCacheRoutingTest {
    private val loggingPort = FakeLoggingPort()

    @Test
    fun completeSingleTurn_xaiWithChatId_addsConversationIdHeader(): Unit =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = xaiSingleTurnService(capturedParams)

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        chatId = ChatId("chat-123"),
                    ),
            )

            assertEquals(
                listOf("chat-123"),
                capturedParams.single()._headers().values("x-grok-conv-id"),
            )
        }

    @Test
    fun completeSingleTurn_xaiWithoutChatId_omitsConversationIdHeader(): Unit =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = xaiSingleTurnService(capturedParams)

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            assertTrue(
                capturedParams
                    .single()
                    ._headers()
                    .values("x-grok-conv-id")
                    .isEmpty(),
            )
        }

    @Test
    fun completeSingleTurn_openAiWithChatId_setsPromptCacheKey(): Unit =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = openAiSingleTurnService(capturedParams)

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        chatId = ChatId("chat-123"),
                    ),
            )

            assertEquals("chat-123", capturedParams.single().promptCacheKey().orElse(null))
        }

    @Test
    fun completeSingleTurn_openRouterAnthropicModel_addsCacheControlWithoutPromptCacheKey(): Unit =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = openRouterSingleTurnService(capturedParams, modelId = "anthropic/claude-sonnet-4")

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Stable user message"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Stable assistant message"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Current user message"),
                    ),
                systemPrompt = "Stable system prompt",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        chatId = ChatId("chat-123"),
                    ),
            )

            val params = capturedParams.single()
            val requestBody = params.toString()
            assertTrue(requestBody.contains("cache_control"), requestBody)
            assertTrue(requestBody.contains("ephemeral"), requestBody)
            assertFalse(params.promptCacheKey().isPresent)

            val headers = params._headers()
            val sessionIdHeader = headers.values("X-Session-Id").firstOrNull()
                ?: headers.values("x-session-id").firstOrNull()
            assertEquals("chat-123", sessionIdHeader)

            val sessionIdBody = params._additionalBodyProperties()["session_id"]?.convert(String::class.java)
            assertEquals("chat-123", sessionIdBody)
        }

    private fun xaiSingleTurnService(capturedParams: CapturedChatParams): BaseOpenAiSdkInferenceService =
        singleTurnService(capturedParams = capturedParams, modelId = "grok-4.3", provider = "XAI")

    private fun openAiSingleTurnService(capturedParams: CapturedChatParams): BaseOpenAiSdkInferenceService =
        singleTurnService(capturedParams = capturedParams, modelId = "gpt-4o", provider = "OPENAI")

    private fun openRouterSingleTurnService(
        capturedParams: CapturedChatParams,
        modelId: String,
    ): BaseOpenAiSdkInferenceService = singleTurnService(capturedParams = capturedParams, modelId = modelId, provider = "OPENROUTER")

    private fun singleTurnService(
        capturedParams: CapturedChatParams,
        modelId: String,
        provider: String,
    ): BaseOpenAiSdkInferenceService =
        when (provider) {
            "XAI" -> object : XaiInferenceServiceImpl(
                client = mockk<OpenAIClient>(),
                modelId = modelId,
                modelType = ModelType.FAST,
                loggingPort = loggingPort,
                toolStreamGatePort = FakeToolStreamGatePort(),
            ) {
                override suspend fun streamChatCompletions(
                    params: ChatCompletionCreateParams,
                    chatId: ChatId?,
                    userMessageId: MessageId?,
                    assistantMessageId: MessageId?,
                    emitEvent: suspend (InferenceEvent) -> Unit,
                ): StreamedOpenAiResponse {
                    capturedParams.add(params)
                    return StreamedOpenAiResponse(
                        emittedAny = true,
                        functionCalls = emptyList(),
                        responseId = "resp_1",
                        providerToolCallIds = emptyList(),
                        providerToolItemIds = emptyList(),
                        assistantMessageText = "mock-response",
                    )
                }
            }
            "OPENROUTER" -> object : OpenRouterInferenceServiceImpl(
                client = mockk<OpenAIClient>(),
                modelId = modelId,
                modelType = ModelType.FAST,
                openRouterRouting = com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration(),
                loggingPort = loggingPort,
                toolStreamGatePort = FakeToolStreamGatePort(),
            ) {
                override suspend fun streamChatCompletions(
                    params: ChatCompletionCreateParams,
                    chatId: ChatId?,
                    userMessageId: MessageId?,
                    assistantMessageId: MessageId?,
                    emitEvent: suspend (InferenceEvent) -> Unit,
                ): StreamedOpenAiResponse {
                    capturedParams.add(params)
                    return StreamedOpenAiResponse(
                        emittedAny = true,
                        functionCalls = emptyList(),
                        responseId = "resp_1",
                        providerToolCallIds = emptyList(),
                        providerToolItemIds = emptyList(),
                        assistantMessageText = "mock-response",
                    )
                }
            }
            else -> object : BaseOpenAiSdkInferenceService(
                client = mockk<OpenAIClient>(),
                modelId = modelId,
                provider = provider,
                modelType = ModelType.FAST,
                loggingPort = loggingPort,
                toolStreamGatePort = FakeToolStreamGatePort(),
            ) {
                override val tag: String = "TestXai"

                override suspend fun streamChatCompletions(
                    params: ChatCompletionCreateParams,
                    chatId: ChatId?,
                    userMessageId: MessageId?,
                    assistantMessageId: MessageId?,
                    emitEvent: suspend (InferenceEvent) -> Unit,
                ): StreamedOpenAiResponse {
                    capturedParams.add(params)
                    return StreamedOpenAiResponse(
                        emittedAny = true,
                        functionCalls = emptyList(),
                        responseId = "resp_1",
                        providerToolCallIds = emptyList(),
                        providerToolItemIds = emptyList(),
                        assistantMessageText = "mock-response",
                    )
                }
            }
        }

    @Test
    fun completeSingleTurn_openRouterDeepSeekModel_attachesReasoningContentAndSuppliesPlaceholder(): Unit =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = openRouterSingleTurnService(capturedParams, modelId = "deepseek/deepseek-v4-flash")

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Search"),
                        AgentLoopMessage(
                            id = "a1",
                            role = Role.ASSISTANT,
                            content = "",
                            metadataJson =
                                """
                                {
                                  "provider_reasoning": {"reasoning_content": "let's search"},
                                  "raw_tool_calls": [{"id": "call_1", "name": "web_search", "arguments_json": "{}"}]
                                }
                                """.trimIndent(),
                        ),
                        AgentLoopMessage(
                            id = "t1",
                            role = Role.USER,
                            content = """{"results":[]}""",
                            metadataJson = """{"call_id":"call_1"}""",
                        ),
                    ),
                systemPrompt = "System prompt",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 1024,
                        includeReasoningContentOnRequest = true,
                        chatId = ChatId("chat-123"),
                    ),
            )

            val params = capturedParams.single()
            val messages = params.messages()
            val assistantMessage = messages.first { it.isAssistant() }.asAssistant()

            val reasoningContent = assistantMessage._additionalProperties()["reasoning_content"]?.convert(String::class.java)
            assertEquals("let's search", reasoningContent)
            assertTrue(assistantMessage.content().isPresent)
            assertEquals(".", assistantMessage.content().get().asText())
        }
}
