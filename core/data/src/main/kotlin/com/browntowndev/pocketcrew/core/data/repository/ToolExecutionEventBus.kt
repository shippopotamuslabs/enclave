package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutionEventPort
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolExecutionEventBus @Inject constructor() : ToolExecutionEventPort {
    private val _events = MutableSharedFlow<ToolExecutionEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val events: SharedFlow<ToolExecutionEvent> = _events.asSharedFlow()

    /**
     * Internal API for the data layer to broadcast events.
     * Uses tryEmit to ensure it never blocks the caller (inference engine).
     */
    fun emit(event: ToolExecutionEvent) {
        _events.tryEmit(event)
    }
}
