package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.inference.ToolStreamGateFinish
import com.browntowndev.enclave.domain.model.inference.ToolStreamGateHandle
import com.browntowndev.enclave.domain.model.inference.ToolStreamGatePushResult
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * centralized-leaked-tool-call-recovery — CL-K-01, CL-K-02.
 */
class ModelTurnStreamGateTest {
    @Test
    fun cl_k_01_dsml_holdback_emits_empty() =
        runTest {
            // JVM unit tests use FakeToolStreamGatePort; JNI holdback is in RustToolStreamGateContractTest.
            val gate = ModelTurnStreamGate(FakeToolStreamGatePort(), allowToolCall = true)
            val emitted = StringBuilder()
            gate.onProviderTextChunk("<|DSML|tool_calls>") { emitted.append(it) }
            assertEquals("", emitted.toString(), "CL-K-01 emitted tokens")
            assertEquals("<|DSML|tool_calls>", gate.bufferedText(), "CL-K-01 bufferedText")
        }

    @Test
    fun cl_k_02_buffered_text_preserves_raw_stream() =
        runTest {
            val gate = ModelTurnStreamGate(FakeToolStreamGatePort(), allowToolCall = true)
            gate.onProviderTextChunk("Hello ") {}
            gate.onProviderTextChunk("world") {}
            assertEquals("Hello world", gate.bufferedText(), "CL-K-02 bufferedText")
        }

    @Test
    fun faal_sg_03_emits_preamble_before_marker() =
        runTest {
            val port =
                PreambleToolStreamGatePort(
                    pushResult = ToolStreamGatePushResult(emittedPrefix = "Let me try with a simpler "),
                    finishResult = ToolStreamGateFinish(trailingFlush = "", hasToolMarker = true),
                )
            val gate = ModelTurnStreamGate(port, allowToolCall = true)
            val emitted = mutableListOf<String>()

            gate.onProviderTextChunk("Let me try with a simpler <tool") { emitted.add(it) }
            gate.finish(hasNativeToolCalls = true) { emitted.add(it) }

            assertEquals(
                "Let me try with a simpler ",
                emitted.joinToString(separator = ""),
                "FAAL-SG-03 emitted tokens",
            )
        }

    @Test
    fun envelope_tool_markup_finishes_like_native_tool_calls_without_emitting_marker() =
        runTest {
            val port = FakeToolStreamGatePort()
            val gate = ModelTurnStreamGate(port, allowToolCall = true)
            val emitted = mutableListOf<String>()
            val envelope =
                """<tool_call>{"name":"web_search","arguments":{"query":"kotlin"}}</tool_call>"""

            gate.onProviderTextChunk(envelope) { emitted.add(it) }
            gate.finish(hasNativeToolCalls = false) { emitted.add(it) }

            assertEquals(listOf<String>(), emitted, "envelope markup must not flush as visible text")
            assertEquals(true, port.finishCalls.single().second, "finish must use native-tool-turn path")
            assertEquals(envelope, gate.bufferedText(), "buffered envelope preserved for engine ingest")
        }

    @Test
    fun faal_sg_04_finish_forwards_trailing_flush_to_emit() =
        runTest {
            val port =
                PreambleToolStreamGatePort(
                    pushResult = ToolStreamGatePushResult(emittedPrefix = ""),
                    finishResult = ToolStreamGateFinish(trailingFlush = "tail text", hasToolMarker = true),
                )
            val gate = ModelTurnStreamGate(port, allowToolCall = true)
            val emitted = mutableListOf<String>()

            gate.finish(hasNativeToolCalls = true) { emitted.add(it) }

            assertEquals(listOf("tail text"), emitted, "FAAL-SG-04 emitted trailingFlush")
        }

    private class PreambleToolStreamGatePort(
        private val pushResult: ToolStreamGatePushResult,
        private val finishResult: ToolStreamGateFinish,
    ) : ToolStreamGatePort {
        private val handle = ToolStreamGateHandle(nativeHandle = Unit)
        private val buffer = StringBuilder()

        override fun create(allowToolCall: Boolean): ToolStreamGateHandle = handle

        override fun reset(handle: ToolStreamGateHandle) {
            buffer.clear()
        }

        override fun push(
            handle: ToolStreamGateHandle,
            chunk: String,
        ): ToolStreamGatePushResult {
            buffer.append(chunk)
            return pushResult
        }

        override fun finish(
            handle: ToolStreamGateHandle,
            hasNativeToolCalls: Boolean,
        ): ToolStreamGateFinish = finishResult

        override fun bufferedText(handle: ToolStreamGateHandle): String = buffer.toString()

        override fun hasToolMarkup(handle: ToolStreamGateHandle): Boolean = finishResult.hasToolMarker
    }
}
