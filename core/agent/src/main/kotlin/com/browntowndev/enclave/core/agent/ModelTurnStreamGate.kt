package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.inference.ToolStreamGateHandle
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort

/**
 * Per-model-turn streaming gate over [ToolStreamGatePort] (centralized-leaked-tool-call-recovery).
 */
internal class ModelTurnStreamGate(
    private val port: ToolStreamGatePort,
    allowToolCall: Boolean,
) {
    private val handle: ToolStreamGateHandle = port.create(allowToolCall)

    suspend fun onProviderTextChunk(
        chunk: String,
        emit: suspend (String) -> Unit,
    ) {
        val push = port.push(handle, chunk)
        if (push.emittedPrefix.isNotEmpty()) {
            emit(push.emittedPrefix)
        }
    }

    suspend fun finish(
        hasNativeToolCalls: Boolean,
        emit: suspend (String) -> Unit = {},
    ): String {
        val finish = port.finish(handle, hasNativeToolCalls)
        if (finish.trailingFlush.isNotEmpty()) {
            emit(finish.trailingFlush)
        }
        return port.bufferedText(handle)
    }

    fun bufferedText(): String = port.bufferedText(handle)
}
