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
import com.browntowndev.enclave.domain.model.inference.AgentToolCall
import com.browntowndev.enclave.domain.model.inference.AgentToolResult
import com.browntowndev.enclave.domain.port.inference.AgentLoopEvent
import com.browntowndev.enclave.domain.port.inference.AgentManualCompactRequest
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * JNI contract for [RustAgentEngineAdapter] active-session bookkeeping: an older
 * session draining its `finally` block must not clear the references owned by a
 * newer session (manual compact / context reads would start failing).
 */
class RustAgentEngineAdapterActiveSessionContractTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun loadAgentCoreLibrary() {
            RustToolParserTestLibrary.loadAgentCoreLibrary()
        }
    }

    private val adapter = RustAgentEngineAdapter(NoOpLogger, FakeToolStreamGatePort())

    @Test
    fun finishedBackgroundSessionDoesNotClearNewerActiveSession() =
        runTest {
            // Session 1: completes immediately.
            adapter
                .execute(
                    initialMessages = listOf(ChatMessage(Role.USER, "first")),
                    config = config(),
                    sessionContext = emptyMap(),
                    host = stopHost(),
                ).toList()

            // Session 2: holds the model turn open so the session stays active.
            val turnEntered = CompletableDeferred<Unit>()
            val releaseTurn = CompletableDeferred<Unit>()
            val job =
                adapter
                    .execute(
                        initialMessages = listOf(ChatMessage(Role.USER, "second")),
                        config = config(),
                        sessionContext = emptyMap(),
                        host = stopHost(onTurn = {
                            turnEntered.complete(Unit)
                            releaseTurn.await()
                        }),
                    ).launchIn(this)

            turnEntered.await()
            // Session 2 is active; manual compact must find an active session even
            // though session 1 already ran its finally block.
            val result = adapter.requestManualCompact(AgentManualCompactRequest())
            assertTrue(result.tokensBefore >= 0)

            releaseTurn.complete(Unit)
            job.join()
        }

    @Test
    fun interleavedFinallyOfOlderSessionKeepsNewerSessionActive() =
        runTest {
            // Session 1: holds its model turn open.
            val turn1Entered = CompletableDeferred<Unit>()
            val release1 = CompletableDeferred<Unit>()
            val job1 =
                adapter
                    .execute(
                        initialMessages = listOf(ChatMessage(Role.USER, "first")),
                        config = config(),
                        sessionContext = emptyMap(),
                        host = stopHost(onTurn = {
                            turn1Entered.complete(Unit)
                            release1.await()
                        }),
                    ).launchIn(this)
            turn1Entered.await()

            // Session 2 starts and becomes the active session, holding its turn open.
            val turn2Entered = CompletableDeferred<Unit>()
            val release2 = CompletableDeferred<Unit>()
            val job2 =
                adapter
                    .execute(
                        initialMessages = listOf(ChatMessage(Role.USER, "second")),
                        config = config(),
                        sessionContext = emptyMap(),
                        host = stopHost(onTurn = {
                            turn2Entered.complete(Unit)
                            release2.await()
                        }),
                    ).launchIn(this)
            turn2Entered.await()

            // Session 1 finishes after session 2 became active; its finally must not
            // null out session 2's references.
            release1.complete(Unit)
            job1.join()

            val result = adapter.requestManualCompact(AgentManualCompactRequest())
            assertTrue(result.tokensBefore >= 0)

            release2.complete(Unit)
            job2.join()
        }

    private fun stopHost(onTurn: suspend () -> Unit = {}): AgentLoopHostCallbacks =
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
                onTurn()
                onTextToken("ok")
                return AgentModelTurnResult(
                    assistantText = "ok",
                    toolCalls = emptyList(),
                    finishReason = AgentFinishReason.Stop,
                )
            }

            override suspend fun executeTools(calls: List<AgentToolCall>): List<AgentToolResult> = emptyList()

            override suspend fun summarize(
                request: com.browntowndev.enclave.domain.model.inference.AgentSummaryRequest,
            ): com.browntowndev.enclave.domain.model.inference.AgentSummaryResult =
                com.browntowndev.enclave.domain.model.inference.AgentSummaryResult(
                    summaryText = "summary",
                    lastMessageId = request.lastMessageId,
                )
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

    private object NoOpLogger : LoggingPort {
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
    }
}
