package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.inference.ToolExecutionEvent
import kotlinx.coroutines.flow.SharedFlow

/**
 * Port for broadcasting and observing tool execution events.
 */
interface ToolExecutionEventPort {
    /**
     * Observable stream of tool execution events.
     */
    val events: SharedFlow<ToolExecutionEvent>
}
