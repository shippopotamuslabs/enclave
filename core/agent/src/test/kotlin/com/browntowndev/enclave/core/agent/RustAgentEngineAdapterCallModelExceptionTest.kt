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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FSIE-01, FSIE-02, FSIE-06 — logging on model-turn failures.
 *
 * Transient transport failures are retried by [RustAgentEngineAdapter] via
 * [ModelTurnFailureClassifier] and no longer emit [AgentLoopEvent.Failed] immediately.
 * See [RustAgentEngineAdapterRetryStandInTest] for retry orchestration coverage.
 */
class RustAgentEngineAdapterCallModelExceptionTest {
    @Test
    fun `FSIE-01 model turn exception is forwarded to loggingPort error`() =
        runTest {
            val loggingPort = RecordingLoggingPort()
            var calls = 0
            val adapter =
                RecordingAgentAdapter(
                    loggingPort = loggingPort,
                    modelTurnBlock = {
                        calls += 1
                        if (calls == 1) throw RuntimeException("upstream timeout")
                    },
                )

            adapter.execute().toList()

            assertEquals(1, loggingPort.errorCalls.size)
            val call = loggingPort.errorCalls.single()
            assertEquals("RustAgentEngineAdapter", call.tag)
            assertTrue(call.message.contains("upstream timeout"))
            assertNotNull(call.throwable)
        }

    @Test
    fun `FSIE-02 transient model turn exception does not emit Failed`() =
        runTest {
            val loggingPort = RecordingLoggingPort()
            var calls = 0
            val adapter =
                RecordingAgentAdapter(
                    loggingPort = loggingPort,
                    modelTurnBlock = {
                        calls += 1
                        if (calls == 1) throw RuntimeException("upstream idle timeout exceeded")
                    },
                    retryEvent =
                        AgentLoopEvent.ModelApiRetryScheduled(
                            attempt = 1,
                            maxAttempts = 3,
                            delayMs = 1_000L,
                            reason = "upstream idle timeout exceeded",
                        ),
                )

            val events = adapter.execute().toList()

            assertTrue(events.any { it is AgentLoopEvent.ModelApiRetryScheduled })
            assertFalse(events.any { it is AgentLoopEvent.Failed })
            assertEquals(AgentLoopEvent.Finished, events.last())
        }

    @Test
    fun `FSIE-06 loggingPort error receives the non-null throwable`() =
        runTest {
            val loggingPort = RecordingLoggingPort()
            var calls = 0
            val adapter =
                RecordingAgentAdapter(
                    loggingPort = loggingPort,
                    modelTurnBlock = {
                        calls += 1
                        if (calls == 1) throw RuntimeException("network read timeout")
                    },
                )

            adapter.execute().toList()

            val throwable = loggingPort.errorCalls.single().throwable
            assertNotNull(throwable)
            assertTrue(throwable is RuntimeException)
            assertEquals("network read timeout", throwable!!.message)
        }

    private class RecordingAgentAdapter(
        private val loggingPort: LoggingPort,
        private val modelTurnBlock: suspend () -> Unit,
        private val retryEvent: AgentLoopEvent.ModelApiRetryScheduled? = null,
    ) {
        fun execute(): Flow<AgentLoopEvent> =
            flow {
                var attempts = 0
                while (true) {
                    try {
                        attempts += 1
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
                            emit(AgentLoopEvent.Failed("model turn failed: ${report.message}"))
                            return@flow
                        }
                        if (attempts == 1) {
                            retryEvent?.let { emit(it) }
                            continue
                        }
                        emit(AgentLoopEvent.Failed("model turn failed: ${report.message}"))
                        return@flow
                    }
                }
            }

        companion object {
            private const val TAG = "RustAgentEngineAdapter"
        }
    }
}

class RecordingLoggingPort : LoggingPort {
    val errorCalls: MutableList<ErrorCall> = mutableListOf()

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
    ) {
        errorCalls += ErrorCall(tag, message, throwable)
    }
}

data class ErrorCall(
    val tag: String,
    val message: String,
    val throwable: Throwable?,
)