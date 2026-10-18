package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Recording [ViewModelErrorHandler] fake (ENC-28). */
class FakeViewModelErrorHandler : ViewModelErrorHandler {
    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    val handledThrowables = mutableListOf<Throwable>()
    val handledMessages = mutableListOf<String>()
    val handledTags = mutableListOf<String>()
    val handledUserMessages = mutableListOf<String>()

    override fun handleError(tag: String, message: String, throwable: Throwable, userMessage: String) {
        handledThrowables += throwable
        handledMessages += message
        handledTags += tag
        handledUserMessages += userMessage
        _errorEvents.tryEmit(userMessage)
    }

    override fun coroutineExceptionHandler(
        tag: String,
        message: String,
        userMessage: String,
    ): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            handledThrowables += throwable
            handledMessages += message
            _errorEvents.tryEmit(userMessage)
        }
}
