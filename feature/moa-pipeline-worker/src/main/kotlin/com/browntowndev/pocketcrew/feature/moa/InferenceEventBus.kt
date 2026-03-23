package com.browntowndev.pocketcrew.feature.moa

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InferenceEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<Intent>(extraBufferCapacity = 1024)
    val events: SharedFlow<Intent> = _events.asSharedFlow()

    suspend fun emit(intent: Intent) {
        _events.emit(intent)
    }
    
    fun tryEmit(intent: Intent) {
        _events.tryEmit(intent)
    }
}
