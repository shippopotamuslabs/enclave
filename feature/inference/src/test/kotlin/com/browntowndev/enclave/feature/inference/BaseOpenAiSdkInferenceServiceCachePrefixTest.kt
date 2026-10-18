@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the OpenAI Java SDK surface (third-party SDK boundary).
package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.enclave.domain.model.config.OpenRouterProviderSort
import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BaseOpenAiSdkInferenceServiceCachePrefixTest {
    private val loggingPort = FakeLoggingPort()

    @Test
    fun `completeSingleTurn message prefix is stable across turns for OpenAI`() =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = createTestService(capturedParams, modelId = "gpt-4o", provider = "OPENAI")

            val turn1Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "Turn 1 user request"),
                )

            service.completeSingleTurn(
                sessionMessages = turn1Messages,
                systemPrompt = "System instruction text",
                contextSidecars = "Sidecar context turn 1",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-123")),
            )

            val turn2Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "Turn 1 user request"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Turn 1 response text"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "Turn 2 user request"),
                )

            service.completeSingleTurn(
                sessionMessages = turn2Messages,
                systemPrompt = "System instruction text",
                contextSidecars = "Sidecar context turn 2",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-123")),
            )

            assertEquals(2, capturedParams.size)
            val params1 = capturedParams[0]
            val params2 = capturedParams[1]

            // Verify prefix routing
            assertEquals("chat-123", params1.promptCacheKey().orElse(null))
            assertEquals("chat-123", params2.promptCacheKey().orElse(null))

            // Verify message content mapping prefix stability
            val msgs1 = params1.messages()
            val msgs2 = params2.messages()

            // At Turn 2, the prefix of the message array sent to the model must match the message array sent in Turn 1
            // (excluding the current turn's active/volatile sidecar text, which is appended at tail insert index).
            // But actually, Turn 1 has system prompt + u1 + sidecar-1.
            // Turn 2 has system prompt + u1 + frozen sidecar-1 + a1 + u2 + sidecar-2.
            // Let's assert that the first 3 messages in Turn 2 (System, User, User/Sidecar) match Turn 1's messages.
            assertTrue(msgs2.size >= msgs1.size)
            for (i in msgs1.indices) {
                val m1 = msgs1[i]
                val m2 = msgs2[i]
                assertEquals(m1.toComparisonString(), m2.toComparisonString(), "Mismatch at message index $i")
            }
        }

    @Test
    fun `completeSingleTurn message prefix is stable across turns for xAI`() =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = createTestService(capturedParams, modelId = "grok-4.3", provider = "XAI")

            val turn1Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "Turn 1 user request"),
                )

            service.completeSingleTurn(
                sessionMessages = turn1Messages,
                systemPrompt = "System instruction text",
                contextSidecars = "Sidecar context turn 1",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-123")),
            )

            val turn2Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "Turn 1 user request"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Turn 1 response text"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "Turn 2 user request"),
                )

            service.completeSingleTurn(
                sessionMessages = turn2Messages,
                systemPrompt = "System instruction text",
                contextSidecars = "Sidecar context turn 2",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-123")),
            )

            assertEquals(2, capturedParams.size)
            val params1 = capturedParams[0]
            val params2 = capturedParams[1]

            // Verify xAI conversation ID header is attached
            assertEquals(listOf("chat-123"), params1._headers().values("x-grok-conv-id"))
            assertEquals(listOf("chat-123"), params2._headers().values("x-grok-conv-id"))

            // Verify message content mapping prefix stability
            val msgs1 = params1.messages()
            val msgs2 = params2.messages()

            assertTrue(msgs2.size >= msgs1.size)
            for (i in msgs1.indices) {
                val m1 = msgs1[i]
                val m2 = msgs2[i]
                assertEquals(m1.toComparisonString(), m2.toComparisonString(), "Mismatch at message index $i")
            }
        }

    @Test
    fun `completeSingleTurn message prefix is stable across turns for OpenRouter`() =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            // Using anthropic/claude model identifier to trigger cache control breakpoints
            val service = createTestService(capturedParams, modelId = "anthropic/claude-sonnet-4", provider = "OPENROUTER")

            val turn1Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "Turn 1 user request"),
                )

            service.completeSingleTurn(
                sessionMessages = turn1Messages,
                systemPrompt = "System instruction text",
                contextSidecars = "Sidecar context turn 1",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-123")),
            )

            val turn2Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "Turn 1 user request"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Turn 1 response text"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "Turn 2 user request"),
                )

            service.completeSingleTurn(
                sessionMessages = turn2Messages,
                systemPrompt = "System instruction text",
                contextSidecars = "Sidecar context turn 2",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-123")),
            )

            assertEquals(2, capturedParams.size)
            val params1 = capturedParams[0]
            val params2 = capturedParams[1]

            // Verify message content mapping prefix stability
            val msgs1 = params1.messages()
            val msgs2 = params2.messages()

            assertTrue(msgs2.size >= msgs1.size)
            for (i in msgs1.indices) {
                val m1 = msgs1[i]
                val m2 = msgs2[i]
                assertEquals(m1.toComparisonString(), m2.toComparisonString(), "Mismatch at message index $i")
            }

            // Verify OpenRouter cache control parameters exist in request body
            val requestBody1 = params1.toString()
            val requestBody2 = params2.toString()
            assertTrue(requestBody1.contains("cache_control"), "Turn 1 missing cache_control")
            assertTrue(requestBody2.contains("cache_control"), "Turn 2 missing cache_control")
        }

    @Test
    fun `completeSingleTurn applies OpenRouter routing body on live path`() =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service =
                createTestService(
                    capturedParams = capturedParams,
                    modelId = "anthropic/claude-sonnet-4",
                    provider = "OPENROUTER",
                    openRouterRouting =
                        OpenRouterRoutingConfiguration(
                            providerSort = OpenRouterProviderSort.LATENCY,
                            allowFallbacks = false,
                            requireParameters = true,
                            dataCollectionPolicy = OpenRouterDataCollectionPolicy.DENY,
                            zeroDataRetention = true,
                        ),
                )

            service.completeSingleTurn(
                sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hello")),
                systemPrompt = "",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val body = capturedParams.single()._additionalBodyProperties()
            assertTrue(body.containsKey("provider"))
            val provider = body["provider"]!!.convert(Map::class.javaObjectType) as Map<*, *>
            assertEquals("latency", provider["sort"])
            assertEquals(false, provider["allow_fallbacks"])
            assertEquals(true, provider["zdr"])
        }

    private fun createTestService(
        capturedParams: MutableList<ChatCompletionCreateParams>,
        modelId: String,
        provider: String,
        openRouterRouting: OpenRouterRoutingConfiguration? = null,
    ): BaseOpenAiSdkInferenceService {
        return when (provider) {
            "XAI" -> object : XaiInferenceServiceImpl(
                client = mockk<OpenAIClient>(),
                modelId = modelId,
                modelType = ModelType.FAST,
                loggingPort = loggingPort,
                toolStreamGatePort = FakeToolStreamGatePort(),
            ) {
                override val tag: String = "TestBaseOpenAiSdkService"

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
                openRouterRouting = openRouterRouting ?: com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration(),
                loggingPort = loggingPort,
                toolStreamGatePort = FakeToolStreamGatePort(),
            ) {
                override val tag: String = "TestBaseOpenAiSdkService"

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
                openRouterRouting = openRouterRouting,
                loggingPort = loggingPort,
                toolStreamGatePort = FakeToolStreamGatePort(),
            ) {
                override val tag: String = "TestBaseOpenAiSdkService"

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
    }



    private fun ChatCompletionMessageParam.toComparisonString(): String =
        when {
            isSystem() -> {
                val sys = asSystem()
                val content = sys.content()
                val contentStr =
                    if (content.isText()) {
                        content.asText()
                    } else {
                        content.asArrayOfContentParts().joinToString { part ->
                            part.text()
                        }
                    }
                "SYSTEM:$contentStr"
            }
            isUser() -> {
                val user = asUser()
                val content = user.content()
                val contentStr =
                    if (content.isText()) {
                        content.asText()
                    } else {
                        content.asArrayOfContentParts().joinToString { part ->
                            if (part.isText()) part.asText().text() else "IMAGE"
                        }
                    }
                "USER:$contentStr"
            }
            isAssistant() -> {
                val assistant = asAssistant()
                val toolCallsText =
                    if (assistant.toolCalls().isPresent) {
                        assistant.toolCalls().get().joinToString { tc ->
                            "${tc.asFunction().id()}:${tc.asFunction().function().name()}:${tc.asFunction().function().arguments()}"
                        }
                    } else {
                        "no_tools"
                    }
                val text =
                    if (assistant.content().isPresent) {
                        val content = assistant.content().get()
                        if (content.isText()) content.asText() else "content_parts"
                    } else {
                        ""
                    }
                "ASSISTANT:text=$text;tools=$toolCallsText"
            }
            isTool() -> {
                val tool = asTool()
                "TOOL:id=${tool.toolCallId()};content=${tool.content().asText()}"
            }
            else -> toString()
        }
}
