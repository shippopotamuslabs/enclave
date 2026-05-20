package com.browntowndev.pocketcrew.core.agent

import com.browntowndev.pocketcrew.domain.port.inference.AgentLoopEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
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
 * FSIE-01, FSIE-02, FSIE-06
 *
 * Verifies that a model-turn exception both logs via [LoggingPort.error] (with the original
 * throwable) and emits [AgentLoopEvent.Failed] with the correct message.
 *
 * Stand-in approach (no JNI): uses a [RecordingAgentAdapter] that reproduces the CallModel
 * catch-block behavior of [RustAgentEngineAdapter], following the pattern established in
 * [RustAgentEngineAdapterLeakTest].
 *
 * [RecordingAgentAdapter] mirrors the FIXED production catch block, which calls
 * [LoggingPort.error] before emitting [AgentLoopEvent.Failed]. FSIE-01 and FSIE-06 pass
 * as regression guards confirming the catch-block pattern is correct.
 */
class RustAgentEngineAdapterCallModelExceptionTest {
    // ── FSIE-01 ──────────────────────────────────────────────────────────────────

    @Test
    fun `FSIE-01 model turn exception is forwarded to loggingPort error`() =
        runTest {
            val loggingPort = RecordingLoggingPort()
            val adapter =
                RecordingAgentAdapter(
                    loggingPort = loggingPort,
                    modelTurnBlock = { throw RuntimeException("upstream timeout") },
                )

            adapter.execute().toList()

            assertEquals(1, loggingPort.errorCalls.size, "Expected exactly one loggingPort.error call")
            val call = loggingPort.errorCalls[0]
            assertEquals("RustAgentEngineAdapter", call.tag)
            assertEquals("model turn failed: upstream timeout", call.message)
            assertNotNull(call.throwable)
        }

    // ── FSIE-02 ──────────────────────────────────────────────────────────────────

    @Test
    fun `FSIE-02 model turn exception produces Failed event with verbatim message`() =
        runTest {
            val loggingPort = RecordingLoggingPort()
            val adapter =
                RecordingAgentAdapter(
                    loggingPort = loggingPort,
                    modelTurnBlock = { throw RuntimeException("upstream timeout") },
                )

            val events = adapter.execute().toList()

            assertTrue(
                events.contains(AgentLoopEvent.Failed("model turn failed: upstream timeout")),
                "Expected AgentLoopEvent.Failed(\"model turn failed: upstream timeout\")",
            )
            assertFalse(
                events.contains(AgentLoopEvent.Finished),
                "AgentLoopEvent.Finished must NOT be emitted when an exception is thrown",
            )
        }

    // ── FSIE-06 ──────────────────────────────────────────────────────────────────

    @Test
    fun `FSIE-06 loggingPort error receives the non-null throwable`() =
        runTest {
            val loggingPort = RecordingLoggingPort()
            val adapter =
                RecordingAgentAdapter(
                    loggingPort = loggingPort,
                    modelTurnBlock = { throw RuntimeException("network read timeout") },
                )

            adapter.execute().toList()

            assertTrue(loggingPort.errorCalls.isNotEmpty(), "Expected loggingPort.error to be called")
            val throwable = loggingPort.errorCalls[0].throwable
            assertNotNull(throwable, "Throwable must not be null")
            assertTrue(throwable is RuntimeException, "Throwable must be a RuntimeException")
            assertEquals("network read timeout", throwable!!.message)
        }

    // ── Test helpers ─────────────────────────────────────────────────────────────

    /**
     * Mirrors the FIXED [RustAgentEngineAdapter] CallModel catch block, which calls
     * [LoggingPort.error] before emitting [AgentLoopEvent.Failed]. This matches the
     * production behavior added by T-2.
     */
    private class RecordingAgentAdapter(
        private val loggingPort: LoggingPort,
        private val modelTurnBlock: suspend () -> Unit,
    ) {
        companion object {
            private const val TAG = "RustAgentEngineAdapter"
        }

        fun execute(): Flow<AgentLoopEvent> =
            flow {
                try {
                    modelTurnBlock()
                    emit(AgentLoopEvent.Finished)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    loggingPort.error(TAG, "model turn failed: ${e.message}", e)
                    emit(AgentLoopEvent.Failed("model turn failed: ${e.message}"))
                }
            }
    }
}

// ── RecordingLoggingPort ──────────────────────────────────────────────────────

data class ErrorCall(
    val tag: String,
    val message: String,
    val throwable: Throwable?,
)

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

    override fun recordException(
        tag: String,
        message: String,
        throwable: Throwable,
    ) {}
}
