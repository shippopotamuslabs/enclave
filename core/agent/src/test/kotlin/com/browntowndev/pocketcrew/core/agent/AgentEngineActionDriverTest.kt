package com.browntowndev.pocketcrew.core.agent

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.AgentContextReference
import com.browntowndev.pocketcrew.domain.model.inference.AgentFinishReason
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopAgentConfig
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopHostCallbacks
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopMessage
import com.browntowndev.pocketcrew.domain.model.inference.AgentModelTurnResult
import com.browntowndev.pocketcrew.domain.model.inference.AgentToolCall
import com.browntowndev.pocketcrew.domain.model.inference.AgentToolResult
import com.browntowndev.pocketcrew.domain.port.inference.AgentLoopEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentEngineActionDriverTest {
    private val driver = AgentEngineActionDriver()

    @Test
    fun `FB-K-003 maps max model requests config to runtime config`() {
        val mapped = driver.mapConfigToFfi(
            AgentLoopAgentConfig(
                maxModelRequestsPerTurn = 4u,
                enableLoopHooks = true,
                enablePendingWorkGuard = true,
            ),
        )

        assertEquals(4u, mapped.maxModelRequestsPerTurn)
        assertTrue(mapped.enableLoopHooks)
        assertTrue(mapped.enablePendingWorkGuard)
    }

    @Test
    fun `FB-K-004 call model then finished emits text and final finished`() = runTest {
        val host = RecordingHost(
            responses = listOf(
                AgentModelTurnResult("done", emptyList(), AgentFinishReason.Stop),
            ),
            textChunks = listOf("done"),
        )

        val events = driver.execute(
            initialMessages = listOf(ChatMessage(Role.USER, "hello")),
            actions = listOf(modelAction(), FfiAgentAction.Finished),
            host = host,
        ).toList()

        assertTrue(events.contains(AgentLoopEvent.TextToken("done")))
        assertEquals(AgentLoopEvent.Finished, events.last())
        assertEquals(1, host.submittedModelResponses)
    }

    @Test
    fun `FB-K-005 call model then failed emits final failed only`() = runTest {
        val host = RecordingHost(
            responses = listOf(AgentModelTurnResult("partial", emptyList(), AgentFinishReason.MaxTokens)),
        )

        val events = driver.execute(
            initialMessages = listOf(ChatMessage(Role.USER, "hello")),
            actions = listOf(
                modelAction(),
                FfiAgentAction.Failed(AgentException.Internal("Model stopped before completion: max_tokens")),
            ),
            host = host,
        ).toList()

        assertEquals(AgentLoopEvent.Failed("Model stopped before completion: max_tokens"), events.last())
        assertEquals(0, events.count { it == AgentLoopEvent.Finished })
    }

    @Test
    fun `FB-K-006 multi callback sequence completes model tool model finished`() = runTest {
        val host = RecordingHost(
            responses = listOf(
                AgentModelTurnResult(null, emptyList(), AgentFinishReason.ToolUse),
                AgentModelTurnResult("done", emptyList(), AgentFinishReason.Stop),
            ),
            toolResults = listOf(AgentToolResult("call-1", """{"ok":true}""", truncated = false, softFailure = false)),
        )

        val events = driver.execute(
            initialMessages = listOf(ChatMessage(Role.USER, "search")),
            actions = listOf(
                modelAction(),
                FfiAgentAction.ExecuteTools(
                    listOf(FfiToolCallRecord("call-1", "web_search", """{"query":"finish"}""", null)),
                ),
                modelAction(),
                FfiAgentAction.Finished,
            ),
            host = host,
        ).toList()

        assertEquals(2, host.submittedModelResponses)
        assertEquals(1, host.executedToolBatches)
        val toolEvent = events.filterIsInstance<AgentLoopEvent.ToolCallExecuted>().single()
        assertEquals("web_search", toolEvent.toolName)
        assertEquals("""{"ok":true}""", toolEvent.resultJson)
        assertEquals(AgentLoopEvent.Finished, events.last())
    }

    @Test
    fun `FB-K-007 pending work continuation reaches host as second model request`() = runTest {
        val host = RecordingHost(
            responses = listOf(
                AgentModelTurnResult("draft", emptyList(), AgentFinishReason.Stop),
                AgentModelTurnResult("verified", emptyList(), AgentFinishReason.Stop),
            ),
        )

        val events = driver.execute(
            initialMessages = listOf(ChatMessage(Role.USER, "ship")),
            actions = listOf(
                modelAction(),
                modelAction(
                    messages = listOf(
                        FfiAgentMessage(
                            id = "pending-work-reminder-1",
                            role = FfiAgentRole.SYSTEM,
                            content = "Pending work remains: verify failing tests",
                            metadataJson = "null",
                        ),
                    ),
                ),
                FfiAgentAction.Finished,
            ),
            host = host,
        ).toList()

        assertEquals(2, host.submittedModelResponses)
        assertEquals(
            "Pending work remains: verify failing tests",
            host.receivedSessionMessages.last().single().content,
        )
        assertEquals(AgentLoopEvent.Finished, events.last())
    }

    private fun modelAction(messages: List<FfiAgentMessage> = emptyList()): FfiAgentAction.CallModel =
        FfiAgentAction.CallModel(
            FfiModelRequest(
                sessionMessages = messages,
                systemPrompt = "sys",
                toolsSchemaJson = "[]",
                allowToolCall = true,
                contextReferences = emptyList(),
                stateLedgerJson = null,
                toolContextIndexJson = null,
                fileContextIndexJson = null,
                cacheHints = emptyList(),
            ),
        )

    private class RecordingHost(
        private val responses: List<AgentModelTurnResult>,
        private val textChunks: List<String> = emptyList(),
        private val toolResults: List<AgentToolResult> = emptyList(),
    ) : AgentLoopHostCallbacks {
        var submittedModelResponses = 0
        var executedToolBatches = 0
        val receivedSessionMessages = mutableListOf<List<AgentLoopMessage>>()
        private var nextResponseIndex = 0

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
            onTextToken: suspend (String) -> Unit,
            onThinkingToken: suspend (String) -> Unit,
        ): AgentModelTurnResult {
            textChunks.forEach { onTextToken(it) }
            submittedModelResponses += 1
            receivedSessionMessages += sessionMessages
            return responses[nextResponseIndex++]
        }

        override suspend fun executeTools(calls: List<AgentToolCall>): List<AgentToolResult> {
            executedToolBatches += 1
            return toolResults
        }
    }
}
