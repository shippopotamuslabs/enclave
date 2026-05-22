package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.inference.ToolStreamGateFinish
import com.browntowndev.enclave.domain.model.inference.ToolStreamGateHandle
import com.browntowndev.enclave.domain.model.inference.ToolStreamGatePushResult
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UniFFI adapter for Rust `tool_stream_gate` (TOOL-PARSE-002).
 */
@Singleton
class RustToolStreamGate
    @Inject
    constructor() : ToolStreamGatePort {
        internal var createDelegate: (Boolean) -> FfiToolStreamGate = ::createToolStreamGate

        override fun create(allowToolCall: Boolean): ToolStreamGateHandle =
            ToolStreamGateHandle(nativeHandle = createDelegate(allowToolCall))

        override fun reset(handle: ToolStreamGateHandle) {
            ffi(handle).reset()
        }

        override fun push(
            handle: ToolStreamGateHandle,
            chunk: String,
        ): ToolStreamGatePushResult {
            val result = ffi(handle).push(chunk)
            return ToolStreamGatePushResult(emittedPrefix = result.emittedPrefix)
        }

        override fun finish(
            handle: ToolStreamGateHandle,
            hasNativeToolCalls: Boolean,
        ): ToolStreamGateFinish {
            val result = ffi(handle).finish(hasNativeToolCalls)
            return ToolStreamGateFinish(
                trailingFlush = result.trailingFlush,
                hasToolMarker = result.hasToolMarker,
            )
        }

        override fun bufferedText(handle: ToolStreamGateHandle): String = ffi(handle).bufferedText()

        private fun ffi(handle: ToolStreamGateHandle): FfiToolStreamGate = handle.nativeHandle as FfiToolStreamGate
    }
