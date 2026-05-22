package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.inference.ToolStreamGateFinish
import com.browntowndev.enclave.domain.model.inference.ToolStreamGateHandle
import com.browntowndev.enclave.domain.model.inference.ToolStreamGatePushResult

/**
 * Pure-domain port for Rust `tool_stream_gate` (TOOL-PARSE-002).
 * Holds back partial tool markers during streaming; does not parse envelopes.
 */
interface ToolStreamGatePort {
    fun create(allowToolCall: Boolean): ToolStreamGateHandle

    fun reset(handle: ToolStreamGateHandle)

    fun push(
        handle: ToolStreamGateHandle,
        chunk: String,
    ): ToolStreamGatePushResult

    fun finish(
        handle: ToolStreamGateHandle,
        hasNativeToolCalls: Boolean,
    ): ToolStreamGateFinish

    fun bufferedText(handle: ToolStreamGateHandle): String
}
