package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.inference.ToolStreamGateFinish
import com.browntowndev.enclave.domain.model.inference.ToolStreamGateHandle
import com.browntowndev.enclave.domain.model.inference.ToolStreamGatePushResult
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SafeBufferedTextLengthTest {
    @Test
    fun `returns buffered length when gate is healthy`() =
        runTest {
            val gate = ModelTurnStreamGate(HealthyPort(), allowToolCall = true)
            gate.onProviderTextChunk("hello") { }
            assertEquals(5, safeBufferedTextLength(gate))
        }

    @Test
    fun `returns zero when bufferedText throws`() =
        runTest {
            val gate = ModelTurnStreamGate(BrokenBufferedTextPort(), allowToolCall = true)
            gate.onProviderTextChunk("hello") { }
            assertEquals(0, safeBufferedTextLength(gate))
        }

    private class HealthyPort : ToolStreamGatePort {
        private var buffer = ""

        override fun create(allowToolCall: Boolean): ToolStreamGateHandle =
            ToolStreamGateHandle(nativeHandle = Unit)

        override fun reset(handle: ToolStreamGateHandle) {
            buffer = ""
        }

        override fun push(
            handle: ToolStreamGateHandle,
            chunk: String,
        ): ToolStreamGatePushResult {
            buffer += chunk
            return ToolStreamGatePushResult(emittedPrefix = chunk)
        }

        override fun finish(
            handle: ToolStreamGateHandle,
            hasNativeToolCalls: Boolean,
        ): ToolStreamGateFinish = ToolStreamGateFinish(trailingFlush = "", hasToolMarker = false)

        override fun bufferedText(handle: ToolStreamGateHandle): String = buffer
    }

    private class BrokenBufferedTextPort : ToolStreamGatePort {
        override fun create(allowToolCall: Boolean): ToolStreamGateHandle =
            ToolStreamGateHandle(nativeHandle = Unit)

        override fun reset(handle: ToolStreamGateHandle) = Unit

        override fun push(
            handle: ToolStreamGateHandle,
            chunk: String,
        ): ToolStreamGatePushResult = ToolStreamGatePushResult(emittedPrefix = chunk)

        override fun finish(
            handle: ToolStreamGateHandle,
            hasNativeToolCalls: Boolean,
        ): ToolStreamGateFinish = ToolStreamGateFinish(trailingFlush = "", hasToolMarker = false)

        override fun bufferedText(handle: ToolStreamGateHandle): String =
            error("tool stream gate lock: PoisonError { .. }")
    }
}