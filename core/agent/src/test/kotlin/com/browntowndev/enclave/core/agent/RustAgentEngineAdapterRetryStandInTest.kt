package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.port.inference.AgentLoopEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * MAR-K-10 — Stand-in for [RustAgentEngineAdapter] transient retry orchestration (no JNI).
 */
class RustAgentEngineAdapterRetryStandInTest {
    @Test
    fun `MAR-K-10 transient model failure schedules retry instead of Failed`() =
        runTest {
            var attempts = 0
            val events =
                ModelTurnRetryStandIn(
                    loggingPort = RecordingLoggingPort(),
                    modelTurnBlock = {
                        attempts += 1
                        if (attempts == 1) {
                            throw RuntimeException("SseException: 200: Upstream idle timeout exceeded code=502")
                        }
                    },
                    retryEvent =
                        AgentLoopEvent.ModelApiRetryScheduled(
                            attempt = 1,
                            maxAttempts = 3,
                            delayMs = 1_000L,
                            reason = "Upstream idle timeout exceeded",
                        ),
                ).execute().toList()

            assertTrue(events.any { it is AgentLoopEvent.ModelApiRetryScheduled })
            assertFalse(events.any { it is AgentLoopEvent.Failed })
            assertEquals(AgentLoopEvent.Finished, events.last())
            assertEquals(2, attempts)
        }

    @Test
    fun `MAR-K-11 permanent model failure emits Failed`() =
        runTest {
            val events =
                ModelTurnRetryStandIn(
                    loggingPort = RecordingLoggingPort(),
                    modelTurnBlock = { throw RuntimeException("HTTP 401: Invalid API key") },
                    retryEvent = null,
                    terminalFailure = AgentLoopEvent.Failed("Model API unavailable: Invalid API key"),
                ).execute().toList()

            assertEquals(AgentLoopEvent.Failed("Model API unavailable: Invalid API key"), events.last())
            assertFalse(events.any { it is AgentLoopEvent.ModelApiRetryScheduled })
        }

    /**
     * Mirrors production [RustAgentEngineAdapter] catch → classify → submit failure → drain retry event.
     */
    private class ModelTurnRetryStandIn(
        private val loggingPort: LoggingPort,
        private val modelTurnBlock: suspend () -> Unit,
        private val retryEvent: AgentLoopEvent.ModelApiRetryScheduled?,
        private val terminalFailure: AgentLoopEvent.Failed? = null,
    ) {
        fun execute(): Flow<AgentLoopEvent> =
            flow {
                while (true) {
                    try {
                        modelTurnBlock()
                        emit(AgentLoopEvent.Finished)
                        return@flow
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        val report = ModelTurnFailureClassifier.classify(e, partialOutputChars = 0)
                        loggingPort.error(
                            TAG,
                            "model turn failed (${report.errorClass}): ${report.message}",
                            e,
                        )
                        if (report.errorClass == FfiModelApiErrorClass.PERMANENT) {
                            emit(
                                terminalFailure
                                    ?: AgentLoopEvent.Failed("Model API unavailable: ${report.message}"),
                            )
                            return@flow
                        }
                        retryEvent?.let { emit(it) }
                    }
                }
            }

        companion object {
            private const val TAG = "RustAgentEngineAdapter"
        }
    }
}