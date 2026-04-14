package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent
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
