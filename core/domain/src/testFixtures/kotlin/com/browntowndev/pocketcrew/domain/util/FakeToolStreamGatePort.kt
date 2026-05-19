package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.inference.ToolStreamGateFinish
import com.browntowndev.pocketcrew.domain.model.inference.ToolStreamGateHandle
import com.browntowndev.pocketcrew.domain.model.inference.ToolStreamGatePushResult
import com.browntowndev.pocketcrew.domain.port.inference.ToolStreamGatePort

/**
 * Fake [ToolStreamGatePort] for TOOL-PARSE-002 tests.
 *
 * Accumulates chunks without emitting during [push]; [finish] flushes the full buffer
 * as `trailingFlush` so agent-loop tests can assert raw submit behavior on JVM.
 */
class FakeToolStreamGatePort : ToolStreamGatePort {
    val pushCalls: MutableList<Pair<ToolStreamGateHandle, String>> = mutableListOf()
    val finishCalls: MutableList<Pair<ToolStreamGateHandle, Boolean>> = mutableListOf()
    val resetCalls: MutableList<ToolStreamGateHandle> = mutableListOf()

    private val buffers = mutableMapOf<ToolStreamGateHandle, StringBuilder>()
    private var nextId = 0

    override fun create(allowToolCall: Boolean): ToolStreamGateHandle {
        val handle = ToolStreamGateHandle(nativeHandle = FakeHandle(nextId++, allowToolCall))
        buffers[handle] = StringBuilder()
        return handle
    }

    override fun reset(handle: ToolStreamGateHandle) {
        resetCalls.add(handle)
        buffers[handle]?.clear()
    }

    override fun push(
        handle: ToolStreamGateHandle,
        chunk: String,
    ): ToolStreamGatePushResult {
        pushCalls.add(handle to chunk)
        buffers.getValue(handle).append(chunk)
        val allowToolCall = (handle.nativeHandle as FakeHandle).allowToolCall
        return ToolStreamGatePushResult(
            emittedPrefix = if (allowToolCall) "" else chunk,
        )
    }

    override fun finish(
        handle: ToolStreamGateHandle,
        hasNativeToolCalls: Boolean,
    ): ToolStreamGateFinish {
        finishCalls.add(handle to hasNativeToolCalls)
        val buffered = buffers.getValue(handle).toString()
        val allowToolCall = (handle.nativeHandle as FakeHandle).allowToolCall
        return ToolStreamGateFinish(
            trailingFlush = if (allowToolCall) buffered else "",
            hasToolMarker = false,
        )
    }

    override fun bufferedText(handle: ToolStreamGateHandle): String = buffers.getValue(handle).toString()

    data class FakeHandle(
        val id: Int,
        val allowToolCall: Boolean,
    )
}
