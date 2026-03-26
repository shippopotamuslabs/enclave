package com.browntowndev.pocketcrew.core.ui.error

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.SharedFlow

/**
 * Interface for a centralized ViewModel error handler.
 */
interface ViewModelErrorHandler {
    /**
     * A [SharedFlow] that emits user-facing error messages to be displayed in a Snackbar.
     */
    val errorEvents: SharedFlow<String>

    /**
     * Handles an error by logging it and optionally displaying a user-facing message.
     * @param tag The tag for the error (usually the class name).
     * @param message A developer-facing description of the error.
     * @param throwable The [Throwable] that was caught.
     * @param userMessage A user-friendly message to display in the UI.
     */
    fun handleError(
        tag: String,
        message: String,
        throwable: Throwable,
        userMessage: String = "An unexpected error occurred"
    )

    /**
     * Creates a [CoroutineExceptionHandler] that routes errors to [handleError].
     * @param tag The tag for the error.
     * @param message A developer-facing description.
     * @param userMessage A user-friendly message.
     */
    fun coroutineExceptionHandler(
        tag: String,
        message: String,
        userMessage: String = "An unexpected error occurred"
    ): CoroutineExceptionHandler
}
