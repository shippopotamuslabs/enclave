package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * centralized-leaked-tool-call-recovery — CL-K-03.
 *
 * **Stand-in (no JNI):** [RustAgentEngineAdapter] is UniFFI-backed; JVM unit tests cannot
 * drive its `onTextToken` path without native libs. This test exercises the same forwarding
 * contract via [ModelTurnStreamGate.onProviderTextChunk], which the adapter calls from
 * `onTextToken` when `allowToolCall` is true (see [RustAgentEngineAdapter]).
 *
 * Full adapter integration remains an instrumented / on-device follow-up; CL-K-03 coverage
 * here is the holdback behavior the adapter delegates to the gate.
 */
class RustAgentEngineAdapterLeakTest {
    @Test
    fun cl_k_03_adapter_suppresses_dsml_text_token() =
        runTest {
            val fakePort = FakeToolStreamGatePort()
            val gate = ModelTurnStreamGate(fakePort, allowToolCall = true)
            val dsmlChunk = "<|DSML|tool_calls>"
            val textTokens = StringBuilder()

            forwardModelTurnTextToken(gate, dsmlChunk) { textTokens.append(it) }

            assertEquals("", textTokens.toString(), "CL-K-03 TextToken concat")
            val handle = fakePort.pushCalls.single().first
            assertEquals(
                dsmlChunk,
                fakePort.bufferedText(handle),
                "CL-K-03 FakeToolStreamGatePort.bufferedText",
            )
        }

    /**
     * Stand-in for adapter `onTextToken` → [ModelTurnStreamGate.onProviderTextChunk].
     */
    private suspend fun forwardModelTurnTextToken(
        gate: ModelTurnStreamGate,
        chunk: String,
        emitTextToken: suspend (String) -> Unit,
    ) {
        gate.onProviderTextChunk(chunk, emitTextToken)
    }
}
