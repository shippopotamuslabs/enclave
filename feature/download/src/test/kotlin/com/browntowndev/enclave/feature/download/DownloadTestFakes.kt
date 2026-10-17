package com.browntowndev.enclave.feature.download

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow

/** Recording [ViewModelErrorHandler] fake for the download test suites (ENC-28). */
class FakeViewModelErrorHandler : ViewModelErrorHandler {
    data class RecordedError(
        val tag: String,
        val message: String,
        val throwable: Throwable,
        val userMessage: String,
    )

    override val errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)

    val recordedErrors = mutableListOf<RecordedError>()

    override fun handleError(tag: String, message: String, throwable: Throwable, userMessage: String) {
        recordedErrors += RecordedError(tag, message, throwable, userMessage)
        errorEvents.tryEmit(userMessage)
    }

    override fun coroutineExceptionHandler(tag: String, message: String, userMessage: String): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            handleError(tag, message, throwable, userMessage)
        }
}

/** Recording [DownloadPlanRefresher] for the download test suites (ENC-28). */
class RecordingDownloadPlanRefresher : DownloadPlanRefresher {
    var refreshCount = 0
        private set

    override suspend fun refresh() {
        refreshCount++
    }
}
