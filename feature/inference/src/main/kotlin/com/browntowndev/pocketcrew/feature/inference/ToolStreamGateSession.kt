package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ToolStreamGateHandle
import com.browntowndev.pocketcrew.domain.port.inference.ToolStreamGatePort

/**
 * Thin wrapper over [ToolStreamGatePort] for streaming assistant text (TOOL-PARSE-002).
 */
internal class ToolStreamGateSession(
    private val port: ToolStreamGatePort,
    allowToolCall: Boolean,
) {
    private val handle: ToolStreamGateHandle = port.create(allowToolCall)

    suspend fun onTextToken(
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
