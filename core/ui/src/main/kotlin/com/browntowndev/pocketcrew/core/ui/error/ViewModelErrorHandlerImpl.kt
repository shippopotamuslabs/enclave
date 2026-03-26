package com.browntowndev.pocketcrew.core.ui.error

import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.util.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViewModelErrorHandlerImpl @Inject constructor(
    private val loggingPort: LoggingPort,
    private val clock: Clock
) : ViewModelErrorHandler {

    private val _errorEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private var lastErrorTime = -5000L
    private val DEBOUNCE_MILLIS = 5000L

    override fun handleError(tag: String, message: String, throwable: Throwable, userMessage: String) {
        if (throwable is CancellationException) return

        // 1. Log to Crashlytics and Logcat
        loggingPort.recordException(tag, message, throwable)
        loggingPort.error(tag, message, throwable)

        // 2. Debounce and emit user-facing message
        val currentTime = clock.currentTimeMillis()
        if (currentTime - lastErrorTime >= DEBOUNCE_MILLIS) {
            lastErrorTime = currentTime
            _errorEvents.tryEmit(userMessage)
        }
    }

    override fun coroutineExceptionHandler(
        tag: String,
        message: String,
        userMessage: String
    ): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            handleError(tag, message, throwable, userMessage)
        }
    }
}
