package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
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

/**
 * xAI prompt cache via [XaiRequestMapper.XAI_CONVERSATION_ID_HEADER] requires a stable
 * message prefix across tool-loop turns. Volatile sidecars belong before the live tail.
 */
class XaiPromptCacheSidecarPlacementTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    fun AOC_15_completeSingleTurn_toolContinuation_placesSidecarBeforeToolResult() =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = xaiSingleTurnService(capturedParams)
            val sidecars = """State ledger JSON: {"turn":1}"""

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Search for cats"),
                        AgentLoopMessage(
                            id = "a1",
                            role = Role.ASSISTANT,
                            content = "",
                            metadataJson =
                                """
                                {"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"query\":\"cats\"}"}]}
                                """.trimIndent(),
                        ),
                        AgentLoopMessage(
                            id = "t1",
                            role = Role.USER,
                            content = """{"results":[{"title":"Cat"}]}""",
                            metadataJson = """{"call_id":"call_1"}""",
                        ),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = sidecars,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val messages = capturedParams.single().messages()
            assertEquals(5, messages.size)
            assertTrue(messages[0].isSystem())
            assertEquals("You are a helpful assistant.", messages[0].asSystem().content().asText())
            assertTrue(messages[1].isUser())
            assertEquals("Search for cats", messages[1].asUser().content().asText())
            assertTrue(messages[2].isAssistant())
            assertTrue(messages[3].isSystem())
            assertEquals(sidecars, messages[3].asSystem().content().asText())
            assertTrue(messages[4].isTool())
            assertFalse(messages[1].isSystem())
        }

    private fun xaiSingleTurnService(capturedParams: MutableList<ChatCompletionCreateParams>): BaseOpenAiSdkInferenceService =
        object : BaseOpenAiSdkInferenceService(
            client = mockk<OpenAIClient>(),
            modelId = "grok-4.3",
            provider = "XAI",
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            toolStreamGatePort = FakeToolStreamGatePort(),
        ) {
            override val tag: String = "XaiPromptCacheSidecarPlacementTest"

            override suspend fun streamChatCompletions(
                params: ChatCompletionCreateParams,
                chatId: com.browntowndev.enclave.domain.model.chat.ChatId?,
                userMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                assistantMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                emitEvent: suspend (InferenceEvent) -> Unit,
            ): StreamedOpenAiResponse {
                capturedParams.add(params)
                return StreamedOpenAiResponse(
                    emittedAny = true,
                    functionCalls = emptyList(),
                    responseId = "resp_1",
                    providerToolCallIds = emptyList(),
                    providerToolItemIds = emptyList(),
                    assistantMessageText = "final answer",
                )
            }
        }
}
