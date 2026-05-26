package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentContextReference
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopConfig
import com.browntowndev.enclave.domain.model.inference.AgentLoopHostCallbacks
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentLoopSessionIoParams
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.AgentRawToolCall
import com.browntowndev.enclave.domain.model.inference.AgentSummaryRequest
import com.browntowndev.enclave.domain.model.inference.AgentSummaryResult
import com.browntowndev.enclave.domain.model.inference.AgentToolCall
import com.browntowndev.enclave.domain.model.inference.AgentToolResult
import com.browntowndev.enclave.domain.port.inference.AgentLoopEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled("JVM host cannot load the Android arm64-v8a agent_core JNI library; covered by :core:agent:assembleDebug.")
class RustAgentEngineAdapterTest {
    private val adapter = RustAgentEngineAdapter(RecordingLogger(), FakeToolStreamGatePort())

    @Test
    fun fullLoopWithMockHost_yieldsFinished() =
        runTest {
            val events =
                adapter
                    .execute(
                        initialMessages = listOf(ChatMessage(Role.USER, "hello")),
                        config = config(),
                        sessionContext = mapOf("chat_id" to "chat-1"),
                        host =
                            object : AgentLoopHostCallbacks {
                                override suspend fun completeModelTurn(
                                    sessionMessages: List<AgentLoopMessage>,
                                    systemPrompt: String,
                                    toolsSchemaJson: String,
                                    allowToolCall: Boolean,
                                    contextReferences: List<AgentContextReference>,
                                    stateLedgerJson: String?,
                                    toolContextIndexJson: String?,
                                    fileContextIndexJson: String?,
                                    cacheHints: List<String>,
                                    transcriptRevision: Long,
                                    onTextToken: suspend (String) -> Unit,
                                    onThinkingToken: suspend (String) -> Unit,
                                ): AgentModelTurnResult {
                                    onTextToken("hi")
                                    return AgentModelTurnResult(
                                        assistantText = "hi",
                                        toolCalls = emptyList(),
                                        finishReason = AgentFinishReason.Stop,
                                    )
                                }

                                override suspend fun executeTools(calls: List<AgentToolCall>): List<AgentToolResult> = emptyList()
                            },
                    ).toList()

            assertTrue(events.any { it == AgentLoopEvent.TextToken("hi") })
            assertEquals(AgentLoopEvent.Finished, events.last())
        }

    @Test
    fun flowEmitsCorrectSequenceForTwoToolTurn() =
        runTest {
            var turn = 0
            val events =
                adapter
                    .execute(
                        initialMessages = listOf(ChatMessage(Role.USER, "search")),
                        config = config(),
                        sessionContext = emptyMap(),
                        host =
                            object : AgentLoopHostCallbacks {
                                override suspend fun completeModelTurn(
                                    sessionMessages: List<AgentLoopMessage>,
                                    systemPrompt: String,
                                    toolsSchemaJson: String,
                                    allowToolCall: Boolean,
                                    contextReferences: List<AgentContextReference>,
                                    stateLedgerJson: String?,
                                    toolContextIndexJson: String?,
                                    fileContextIndexJson: String?,
                                    cacheHints: List<String>,
                                    transcriptRevision: Long,
                                    onTextToken: suspend (String) -> Unit,
                                    onThinkingToken: suspend (String) -> Unit,
                                ): AgentModelTurnResult {
                                    turn += 1
                                    return if (turn == 1) {
                                        AgentModelTurnResult(
                                            assistantText = null,
                                            toolCalls =
                                                listOf(
                                                    AgentRawToolCall("call-1", "web_search", """{"query":"android"}"""),
                                                    AgentRawToolCall("call-2", "web_extract", """{"urls":["https://example.com"]}"""),
                                                ),
                                            finishReason = AgentFinishReason.ToolUse,
                                        )
                                    } else {
                                        AgentModelTurnResult(
                                            assistantText = "done",
                                            toolCalls = emptyList(),
                                            finishReason = AgentFinishReason.Stop,
                                        )
                                    }
                                }

                                override suspend fun executeTools(calls: List<AgentToolCall>): List<AgentToolResult> =
                                    calls.map { call ->
                                        AgentToolResult(
                                            callId = call.callId,
                                            resultJson = """{"ok":true}""",
                                            truncated = false,
                                            softFailure = false,
                                        )
                                    }
                            },
                    ).toList()

            assertEquals(2, events.filterIsInstance<AgentLoopEvent.ToolCallExecuted>().size)
            assertTrue(events.filterIsInstance<AgentLoopEvent.TurnStarted>().isNotEmpty())
            assertEquals(AgentLoopEvent.Finished, events.last())
        }

    @Test
    fun terminalAction_drainsEventsBeforeReturningFinished() =
        runTest {
            val events =
                adapter
                    .execute(
                        initialMessages = listOf(ChatMessage(Role.USER, "summarize")),
                        config = config(),
                        sessionContext = emptyMap(),
                        host =
                            object : AgentLoopHostCallbacks {
                                override suspend fun completeModelTurn(
                                    sessionMessages: List<AgentLoopMessage>,
                                    systemPrompt: String,
                                    toolsSchemaJson: String,
                                    allowToolCall: Boolean,
                                    contextReferences: List<AgentContextReference>,
                                    stateLedgerJson: String?,
                                    toolContextIndexJson: String?,
                                    fileContextIndexJson: String?,
                                    cacheHints: List<String>,
                                    transcriptRevision: Long,
                                    onTextToken: suspend (String) -> Unit,
                                    onThinkingToken: suspend (String) -> Unit,
                                ): AgentModelTurnResult =
                                    AgentModelTurnResult(
                                        assistantText = "done",
                                        toolCalls = emptyList(),
                                        finishReason = AgentFinishReason.Stop,
                                    )

                                override suspend fun executeTools(calls: List<AgentToolCall>): List<AgentToolResult> = emptyList()

                                override suspend fun summarize(request: AgentSummaryRequest): AgentSummaryResult =
                                    AgentSummaryResult(summaryText = "summary", lastMessageId = request.lastMessageId)
                            },
                    ).toList()

            assertEquals(AgentLoopEvent.Finished, events.last())
            assertTrue(events.dropLast(1).any { it is AgentLoopEvent.TurnCompleted })
        }

    private fun config(): AgentLoopConfig =
        AgentLoopConfig(
            io =
                AgentLoopSessionIoParams(
                    provider = "TEST",
                    contextWindowTokens = 128_000u,
                    toolsSchemaJson = "[]",
                    systemPrompt = "system",
                ),
        )

    private class RecordingLogger : LoggingPort {
        override fun debug(
            tag: String,
            message: String,
        ) {}

        override fun info(
            tag: String,
            message: String,
        ) {}

        override fun warning(
            tag: String,
            message: String,
        ) {}

        override fun error(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {}

        override fun recordException(
            tag: String,
            message: String,
            throwable: Throwable,
        ) {}
    }
}
