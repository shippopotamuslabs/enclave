@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the OpenAI Java SDK surface (third-party SDK boundary).
package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.openai.StreamedOpenAiResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.core.http.StreamResponse
import com.openai.models.chat.completions.ChatCompletionChunk
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.services.blocking.ChatService
import com.openai.services.blocking.chat.ChatCompletionService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.stream.Stream


class DeepSeekInferenceServiceImplTest {
    private val loggingPort = FakeLoggingPort()
    private val client = mockk<OpenAIClient>()

    @Test
    fun `SCN-INFER-001 reasoner strips reasoning_content on assistant replay`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val metadata =
                """
                {"provider_reasoning":{"reasoning_content":"hidden chain"}}
                """.trimIndent()
            val service =
                createService(
                    modelId = "deepseek-reasoner",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(
                            id = "assistant-1",
                            role = Role.ASSISTANT,
                            content = "prior answer",
                            metadataJson = metadata,
                        ),
                        AgentLoopMessage(
                            id = "u1",
                            role = Role.USER,
                            content = "continue",
                            metadataJson = "null",
                        ),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val assistant =
                requireNotNull(capturedParams)
                    .messages()
                    .first { it.isAssistant() }
                    .asAssistant()
            assertNull(assistant._additionalProperties()["reasoning_content"])
        }

    @Test
    fun `SCN-INFER-002 reasoner omits tools from request params`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createService(
                    modelId = "deepseek-reasoner",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = listOf(ToolDefinition.WEB_SEARCH),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val params = requireNotNull(capturedParams)
            assertTrue(params.tools().orElse(emptyList()).isEmpty())
            assertFalse(params.toolChoice().isPresent)
        }

    @Test
    fun `SCN-INFER-003 reasoner omits temperature from request params`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createService(
                    modelId = "deepseek-reasoner",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(temperature = 0.7f, reasoningBudget = 0),
            )

            val params = requireNotNull(capturedParams)
            assertFalse(params.temperature().isPresent)
            assertFalse(params.topP().isPresent)
        }

    @Test
    fun `SCN-INFER-004 deepseek-chat includes tool_choice when tools available`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createService(
                    modelId = "deepseek-chat",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = listOf(ToolDefinition.WEB_SEARCH),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val params = requireNotNull(capturedParams)
            assertTrue(params.toolChoice().isPresent)
            assertEquals(1, params.tools().orElse(emptyList()).size)
        }

    @Test
    fun `SCN-INFER-005 V4 replays exact reasoning_content on tool-call turn`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val metadata =
                """
                {
                  "provider_reasoning":{"reasoning_content":"need tool output first"},
                  "raw_tool_calls":[{"id":"call_ds_1","name":"web_search","arguments_json":"{\"q\":\"test\"}"}]
                }
                """.trimIndent()
            val service =
                createService(
                    modelId = "deepseek-v4-pro",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(
                            id = "assistant-1",
                            role = Role.ASSISTANT,
                            content = "",
                            metadataJson = metadata,
                        ),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 2048,
                        includeReasoningContentOnRequest = true,
                    ),
            )

            val reasoning =
                requireNotNull(capturedParams)
                    .messages()[0]
                    .asAssistant()
                    ._additionalProperties()["reasoning_content"]
                    ?.convert(String::class.java)
            assertEquals("need tool output first", reasoning)
        }

    @Test
    fun `SCN-INFER-006 V4 uses max_tokens field not max_completion_tokens`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createService(
                    modelId = "deepseek-v4-pro",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 2048,
                        maxTokens = 4096,
                    ),
            )

            val params = requireNotNull(capturedParams)
            assertEquals(4096L, params._additionalBodyProperties()["max_tokens"]?.convert(Long::class.java))
            assertFalse(params.maxCompletionTokens().isPresent)
        }

    @Test
    fun `SCN-INFER-007 V4 sends thinking extra body when reasoning engaged`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createService(
                    modelId = "deepseek-v4-pro",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 1024),
            )

            val thinking = requireNotNull(capturedParams)._additionalBodyProperties()["thinking"]
            assertEquals("""{"type":"enabled"}""", thinking.toString())
        }

    @Test
    fun `SCN-INFER-008 V4 omits tool_choice when reasoning engaged with tools`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createService(
                    modelId = "deepseek-v4-pro",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = listOf(ToolDefinition.WEB_SEARCH),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 2048),
            )

            val params = requireNotNull(capturedParams)
            assertTrue(params.tools().orElse(emptyList()).isNotEmpty())
            assertFalse(params.toolChoice().isPresent)
        }

    @Test
    fun `SCN-INFER-009 V4 supplies placeholder content on blank tool replay`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val metadata =
                """
                {
                  "provider_reasoning":{"reasoning_content":"need tool output first"},
                  "raw_tool_calls":[{"id":"call_ds_1","name":"web_search","arguments_json":"{\"q\":\"test\"}"}]
                }
                """.trimIndent()
            val service =
                createService(
                    modelId = "deepseek-v4-pro",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(
                            id = "assistant-1",
                            role = Role.ASSISTANT,
                            content = "",
                            metadataJson = metadata,
                        ),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 2048,
                        includeReasoningContentOnRequest = true,
                    ),
            )

            val assistantContent =
                requireNotNull(capturedParams)
                    .messages()[0]
                    .asAssistant()
                    .content()
            assertTrue(assistantContent.isPresent)
            assertEquals(".", assistantContent.get().asText())
            assertDoesNotThrow {
                ObjectMapper().writeValueAsBytes(requireNotNull(capturedParams)._body())
            }
        }

    @Test
    fun `SCN-INFER-010 stream reasoning_content delta emits Thinking`() =
        runTest {
            val thinkingChunks = mutableListOf<String>()
            val service = createStreamingService(modelId = "deepseek-v4-pro")

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = { thinkingChunks.add(it) },
                options = GenerationOptions(reasoningBudget = 0),
            )

            assertEquals(listOf("step one"), thinkingChunks)
        }

    @Test
    fun `SCN-MUT-001 lazy max_completion_tokens fails DeepSeek max_tokens requirement`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createService(
                    modelId = "deepseek-v4-pro",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 2048,
                        maxTokens = 4096,
                    ),
            )

            val params = requireNotNull(capturedParams)
            assertEquals(4096L, params._additionalBodyProperties()["max_tokens"]?.convert(Long::class.java))
            assertFalse(params.maxCompletionTokens().isPresent)
        }

    @Test
    fun `SCN-MUT-002 lazy reasoning_content attach on reasoner fails strip requirement`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val metadata =
                """
                {"provider_reasoning":{"reasoning_content":"leak"}}
                """.trimIndent()
            val service =
                createService(
                    modelId = "deepseek-reasoner",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(
                            id = "assistant-1",
                            role = Role.ASSISTANT,
                            content = "prior answer",
                            metadataJson = metadata,
                        ),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        includeReasoningContentOnRequest = true,
                    ),
            )

            val assistant =
                requireNotNull(capturedParams)
                    .messages()[0]
                    .asAssistant()
            assertNull(assistant._additionalProperties()["reasoning_content"])
        }

    @Test
    fun `SCN-MUT-003 lazy missing exact reasoning replay on V4 tool turn fails`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val metadata =
                """
                {
                  "provider_reasoning":{"reasoning_content":"need tool output first"},
                  "raw_tool_calls":[{"id":"call_ds_1","name":"web_search","arguments_json":"{\"q\":\"test\"}"}]
                }
                """.trimIndent()
            val service =
                createService(
                    modelId = "deepseek-v4-pro",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(
                            id = "assistant-1",
                            role = Role.ASSISTANT,
                            content = "",
                            metadataJson = metadata,
                        ),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 2048,
                        includeReasoningContentOnRequest = true,
                    ),
            )

            val reasoning =
                requireNotNull(capturedParams)
                    .messages()[0]
                    .asAssistant()
                    ._additionalProperties()["reasoning_content"]
                    ?.convert(String::class.java)
            assertEquals("need tool output first", reasoning)
        }

    @Test
    fun `SCN-MUT-004 lazy promptCacheKey on DeepSeek fails regression requirement`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createService(
                    modelId = "deepseek-chat",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        chatId = ChatId("chat-99"),
                    ),
            )

            assertFalse(requireNotNull(capturedParams).promptCacheKey().isPresent)
        }

    @Test
    fun `SCN-REGRESSION-002 DeepSeek omits promptCacheKey`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createService(
                    modelId = "deepseek-chat",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        chatId = ChatId("chat-deepseek-1"),
                    ),
            )

            val params = requireNotNull(capturedParams)
            assertFalse(params.promptCacheKey().isPresent)
            assertFalse(
                params
                    ._headers()
                    .values("X-Session-Id")
                    .contains("chat-deepseek-1"),
            )
        }

    @Test
    fun `SCN-INFER-EDGE-002 V4 non reasoning mode restores tool_choice`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createService(
                    modelId = "deepseek-v4-pro",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = listOf(ToolDefinition.WEB_SEARCH),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            assertTrue(requireNotNull(capturedParams).toolChoice().isPresent)
        }

    @Test
    fun `SCN-INFER-EDGE-003 V4 non reasoning mode omits thinking extra body`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createService(
                    modelId = "deepseek-v4-pro",
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            assertNull(requireNotNull(capturedParams)._additionalBodyProperties()["thinking"])
        }

    private fun createStreamingService(modelId: String): DeepSeekInferenceServiceImpl {
        val chatService = mockk<ChatService>()
        val completionService = mockk<ChatCompletionService>()
        val streamResponse = mockk<StreamResponse<ChatCompletionChunk>>()
        every { client.chat() } returns chatService
        every { chatService.completions() } returns completionService
        every { completionService.createStreaming(any<ChatCompletionCreateParams>()) } returns streamResponse
        every { streamResponse.stream() } returns
            Stream.of(
                mockReasoningChunk("step one"),
                mockFinishChunk(),
            )
        every { streamResponse.close() } returns Unit
        return DeepSeekInferenceServiceImpl(
            client = client,
            modelId = modelId,
            modelType = ModelType.FAST,
            baseUrl = "https://api.deepseek.com",
            loggingPort = loggingPort,
            toolStreamGatePort = FakeToolStreamGatePort(),
        )
    }

    private fun mockReasoningChunk(reasoning: String): ChatCompletionChunk {
        val event = mockk<ChatCompletionChunk>()
        val choice = mockk<ChatCompletionChunk.Choice>()
        val delta = mockk<ChatCompletionChunk.Choice.Delta>()
        every { event.id() } returns "chatcmpl-deepseek-test"
        every { event.usage() } returns Optional.empty()
        every { event.choices() } returns listOf(choice)
        every { choice.delta() } returns delta
        every { choice.finishReason() } returns Optional.empty()
        every { delta.content() } returns Optional.empty()
        every { delta._additionalProperties() } returns mapOf("reasoning_content" to JsonValue.from(reasoning))
        every { delta.toolCalls() } returns Optional.empty()
        return event
    }

    private fun mockFinishChunk(): ChatCompletionChunk {
        val event = mockk<ChatCompletionChunk>()
        val choice = mockk<ChatCompletionChunk.Choice>()
        val delta = mockk<ChatCompletionChunk.Choice.Delta>()
        every { event.id() } returns "chatcmpl-deepseek-test"
        every { event.usage() } returns Optional.empty()
        every { event.choices() } returns listOf(choice)
        every { choice.delta() } returns delta
        every { choice.finishReason() } returns Optional.of(ChatCompletionChunk.Choice.FinishReason.STOP)
        every { delta.content() } returns Optional.empty()
        every { delta._additionalProperties() } returns emptyMap<String, JsonValue>()
        every { delta.toolCalls() } returns Optional.empty()
        return event
    }

    private fun createService(
        modelId: String,
        onParams: (ChatCompletionCreateParams) -> Unit = {},
        toolStreamGatePort: ToolStreamGatePort = FakeToolStreamGatePort(),
    ): DeepSeekInferenceServiceImpl =
        object : DeepSeekInferenceServiceImpl(
            client = client,
            modelId = modelId,
            modelType = ModelType.FAST,
            baseUrl = "https://api.deepseek.com",
            loggingPort = loggingPort,
            toolStreamGatePort = toolStreamGatePort,
        ) {
            override suspend fun streamChatCompletions(
                params: ChatCompletionCreateParams,
                chatId: com.browntowndev.enclave.domain.model.chat.ChatId?,
                userMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                assistantMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                emitEvent: suspend (InferenceEvent) -> Unit,
            ): StreamedOpenAiResponse {
                onParams(params)
                return StreamedOpenAiResponse(
                    emittedAny = false,
                    functionCalls = emptyList(),
                    responseId = "resp_stub",
                    providerToolCallIds = emptyList(),
                    providerToolItemIds = emptyList(),
                    assistantMessageText = "",
                )
            }
        }

}