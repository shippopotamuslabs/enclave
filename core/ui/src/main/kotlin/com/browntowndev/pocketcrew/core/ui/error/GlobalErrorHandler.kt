package com.browntowndev.pocketcrew.core.ui.error
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler


/**
 * Global exception handler that catches uncaught coroutine exceptions.
 * Registered via ServiceLoader in app/src/main/resources/META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler
 */
class GlobalErrorHandler : CoroutineExceptionHandler {
    override val key = CoroutineExceptionHandler.Key

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ErrorHandlerEntryPoint {
        fun loggingPort(): LoggingPort
    }

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        val app = ErrorHandlerContextProvider.application ?: return
        
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                app,
                ErrorHandlerEntryPoint::class.java
            )
            val loggingPort = entryPoint.loggingPort()
            
            loggingPort.recordException("GlobalErrorHandler", "Uncaught coroutine exception", exception)
            loggingPort.error("GlobalErrorHandler", "Uncaught coroutine exception", exception)
        } catch (e: Exception) {
            // Fallback if Hilt is not yet initialized or other issues
            Log.e("GlobalErrorHandler", "Failed to log global exception", e)
            Log.e("GlobalErrorHandler", "Original exception: ", exception)
        }
    }
}

/**
 * A [ContentProvider] used to capture the application [Context] at process start.
 * This is the Android-recommended way to provide context to static/ServiceLoader-instantiated
 * classes without relying on public mutating globals.
 */
class ErrorHandlerContextProvider : ContentProvider() {
    companion object {
        var application: Application? = null
            private set
    }

    override fun onCreate(): Boolean {
        application = context?.applicationContext as? Application
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
