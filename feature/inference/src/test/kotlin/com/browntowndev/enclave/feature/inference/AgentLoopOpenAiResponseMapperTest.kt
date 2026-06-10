package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentLoopOpenAiResponseMapperTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    fun `mapSession maps assistant raw_tool_calls to function_call input items`() {
        val mapped =
            AgentLoopOpenAiResponseMapper.mapSession(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(
                            id = "assistant-1",
                            role = Role.ASSISTANT,
                            content = "I'll search.",
                            metadataJson =
                                """{"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"query\":\"rust\"}"}]}""",
                        ),
                    ),
                imageUris = emptyList(),
                imageTargetIndex = -1,
                loggingPort = loggingPort,
                tag = "test",
                modelId = "gpt-4o",
            )

        val input =
            mapped.segments
                .filterIsInstance<ResponseWireSegment.Input>()
                .map { it.item }
        assertEquals(2, input.size)
        assertTrue(input[0].isEasyInputMessage())
        assertTrue(input[1].isFunctionCall())
        assertEquals("web_search", input[1].asFunctionCall().name())
        assertEquals("call_1", input[1].asFunctionCall().callId())
        assertEquals("""{"query":"rust"}""", input[1].asFunctionCall().arguments())
    }

    @Test
    fun `mapSession maps tool result metadata to function_call_output`() {
        val mapped =
            AgentLoopOpenAiResponseMapper.mapSession(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(
                            id = "assistant-1",
                            role = Role.ASSISTANT,
                            content = "",
                            metadataJson =
                                """{"raw_tool_calls":[{"id":"call_xyz","name":"web_search","arguments_json":"{}"}]}""",
                        ),
                        AgentLoopMessage(
                            id = "tool-result-1",
                            role = Role.ASSISTANT,
                            content = """{"result":"ok"}""",
                            metadataJson = """{"call_id":"call_xyz"}""",
                        ),
                    ),
                imageUris = emptyList(),
                imageTargetIndex = -1,
                loggingPort = loggingPort,
                tag = "test",
                modelId = "gpt-4o",
            )

        val outputItem =
            mapped.segments
                .filterIsInstance<ResponseWireSegment.Input>()
                .map { it.item }
                .first { it.isFunctionCallOutput() }
        assertEquals("call_xyz", outputItem.asFunctionCallOutput().callId())
        assertTrue(outputItem.asFunctionCallOutput().output().toString().contains("""{"result":"ok"}"""))
    }

    @Test
    fun `mapSessionToResponseParams preserves OpenAI instructions and function_call replay`() {
        val mapped =
            AgentLoopOpenAiResponseMapper.mapSession(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "sys", role = Role.SYSTEM, content = "Be concise."),
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(
                            id = "a1",
                            role = Role.ASSISTANT,
                            content = "Searching.",
                            metadataJson =
                                """{"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"q\":\"kotlin\"}"}]}""",
                        ),
                    ),
                imageUris = emptyList(),
                imageTargetIndex = 1,
                loggingPort = loggingPort,
                tag = "test",
                modelId = "gpt-4o",
            )
        val wire = ResponseWireSegmentSupport.resolveForOpenAi(mapped.segments)
        val params =
            OpenAiRequestMapper.mapSessionToResponseParams(
                modelId = "gpt-4o",
                wire = wire,
                options = GenerationOptions(reasoningBudget = 0),
                availableTools = emptyList(),
            )

        assertEquals("Be concise.", params.instructions().orElse(null))
        val input = params.input().orElseThrow().asResponse()
        assertTrue(input.any { it.isFunctionCall() })
        assertFalse(input.any { it.isMessage() && it.asMessage().role().toString().contains("system") })
    }

    @Test
    fun `resolveForXai keeps system transcript in input instead of instructions`() {
        val mapped =
            AgentLoopOpenAiResponseMapper.mapSession(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "sys", role = Role.SYSTEM, content = "Be concise."),
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                imageUris = emptyList(),
                imageTargetIndex = 1,
                loggingPort = loggingPort,
                tag = "test",
                modelId = "grok-4.3",
            )
        val wire = ResponseWireSegmentSupport.resolveForXai(mapped.segments)
        val params =
            XaiRequestMapper.mapSessionToResponseParams(
                modelId = "grok-4.3",
                wire = wire,
                options = GenerationOptions(reasoningBudget = 0),
                availableTools = emptyList(),
            )

        assertFalse(params.instructions().isPresent)
        assertTrue(
            params
                .input()
                .orElseThrow()
                .toString()
                .contains("Be concise."),
        )
    }
}