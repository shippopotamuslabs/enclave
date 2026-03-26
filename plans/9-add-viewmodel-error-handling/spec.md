# Specification: ViewModel Error Handling & Remote Logging

## 1. Overview
This specification details the implementation of a centralized error handling and remote logging architecture for the PocketCrew application. It introduces a global `ViewModelErrorHandler` singleton and integrates Firebase Crashlytics with the existing `LoggingPort`.

## 2. Abstractions and Interfaces

### 2.1. `LoggingPort` Update
**Path:** `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/LoggingPort.kt`

Add the `recordException` method to capture exceptions specifically for remote logging without needing to overload the `error` method.

```kotlin
interface LoggingPort {
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warning(tag: String, message: String)
    fun error(tag: String, message: String, throwable: Throwable? = null)
    
    // New method for remote crash reporting
    fun recordException(tag: String, message: String, throwable: Throwable)
}
```

### 2.2. `ViewModelErrorHandler`
**Path:** `core/ui/src/main/kotlin/com/browntowndev/pocketcrew/core/ui/error/ViewModelErrorHandler.kt`

A Hilt-provided singleton that all ViewModels will inject. It centralizes the 5-second debounce logic and handles pushing user-friendly messages to the UI.

```kotlin
package com.browntowndev.pocketcrew.core.ui.error

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.CoroutineExceptionHandler

interface ViewModelErrorHandler {
    // Flow observed by the single Activity/Scaffold to show Snackbars
    val errorEvents: SharedFlow<String>
    
    // Primary method to handle caught errors
    fun handleError(
        tag: String, 
        message: String, 
        throwable: Throwable, 
        userMessage: String = "An unexpected error occurred"
    )

    // Helper to generate a CoroutineExceptionHandler for a specific context
    fun coroutineExceptionHandler(
        tag: String, 
        message: String, 
        userMessage: String = "An unexpected error occurred"
    ): CoroutineExceptionHandler
}
```

### 2.3. `ViewModelErrorHandlerImpl`
**Path:** `core/ui/src/main/kotlin/com/browntowndev/pocketcrew/core/ui/error/ViewModelErrorHandlerImpl.kt`

```kotlin
package com.browntowndev.pocketcrew.core.ui.error

import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViewModelErrorHandlerImpl @Inject constructor(
    private val loggingPort: LoggingPort
) : ViewModelErrorHandler {
    
    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()
    
    private var lastErrorTime = 0L
    private val DEBOUNCE_MILLIS = 5000L

    override fun handleError(tag: String, message: String, throwable: Throwable, userMessage: String) {
        if (throwable is CancellationException) return // Ignore coroutine cancellations
        
        // 1. Log to Crashlytics and Logcat
        loggingPort.recordException(tag, message, throwable)
        loggingPort.error(tag, message, throwable)
        
        // 2. Debounce and emit user-facing message
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastErrorTime >= DEBOUNCE_MILLIS) {
            lastErrorTime = currentTime
            _errorEvents.tryEmit(userMessage)
        }
    }

    override fun coroutineExceptionHandler(tag: String, message: String, userMessage: String): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            handleError(tag, message, throwable, userMessage)
        }
    }
}
```

## 3. Remote Logging Integration (Crashlytics)

### 3.1. `AndroidLoggingAdapter` Update
**Path:** `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/AndroidLoggingAdapter.kt`

We will integrate Firebase Crashlytics. To handle "graceful degradation", we will wrap the Crashlytics call in a `runCatching` block to ensure a missing Google Play Services or uninitialized Firebase does not crash the app. Offline buffering is handled automatically by the Crashlytics SDK. Custom keys (device model, app version) will be set during app initialization.

```kotlin
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import javax.inject.Inject

class AndroidLoggingAdapter @Inject constructor() : LoggingPort {
    // ... existing overrides ...

    override fun recordException(tag: String, message: String, throwable: Throwable) {
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("LogTag", tag)
            crashlytics.setCustomKey("Message", message)
            crashlytics.recordException(throwable)
        }.onFailure {
            // Graceful degradation: If Crashlytics is unavailable, fallback to standard log
            Log.e("AndroidLoggingAdapter", "Crashlytics not available. Error: $message", throwable)
        }
    }
}
```

### 3.2. Global `CoroutineExceptionHandler`
**Path:** `app/src/main/resources/META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler`

To catch errors from Workers, Services, and untracked scopes globally, we will register a global CoroutineExceptionHandler using Java's `ServiceLoader` mechanism for kotlinx.coroutines. This handler will resolve the `LoggingPort` (via an entry point or static reference) and log unhandled exceptions directly to Crashlytics.

## 4. ViewModel Refactoring Detail

All ViewModels will inject `ViewModelErrorHandler` as `private val errorHandler`.

### 4.1. `HistoryViewModel` (`feature/history`)
**Current:** Uses local `HistoryEvent.ShowError` and `_events.emit`.
**Refactor:**
- Inject `errorHandler`.
- Replace `_events.emit(HistoryEvent.ShowError("..."))` in `deleteChat`, `renameChat`, and `togglePin` with `errorHandler.handleError(TAG, "Operation failed", e, "Friendly user message")`.
- Remove `ShowError` from `HistoryEvent` if it's no longer used.

### 4.2. `ChatViewModel` (`feature/chat`)
**Current:** No explicit error handling in `onSendMessage`.
**Refactor:**
- Inject `errorHandler`.
- In `onSendMessage`, use `viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to send message", "Could not send message. Please try again."))`.
- Add `catch` operators to flows if they can fail upstream.

### 4.3. `DownloadViewModel` (`feature/download`)
**Current:** Uses `Log.e` and `modelDownloadOrchestrator.setError`.
**Refactor:**
- Inject `errorHandler`.
- Replace `Log.e` with `errorHandler.handleError` where UI feedback is needed.
- Ensure the `initialErrorMessage` in `init` is routed through `errorHandler`.

### 4.4. `MainViewModel` & `PocketCrewAppViewModel` (`app`)
**Refactor:**
- Inject `errorHandler`.
- Use it to catch any unhandled exceptions during app-level state initialization or navigation.

### 4.5. `SettingsViewModel` (`feature/settings`)
**Refactor:**
- Inject `errorHandler`.
- Wrap settings update operations (e.g., toggling haptics or theme) in `errorHandler.coroutineExceptionHandler`.

## 5. Global CoroutineExceptionHandler

To catch errors from `WorkManager`, `Services`, and Compose handlers that are NOT explicitly caught by a ViewModel's `launch` block:

1. Create `GlobalErrorHandler` in `core/ui`.
2. Register it in `app/src/main/resources/META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler`.
3. The global handler will use a `EntryPoint` to obtain the `LoggingPort` and `ViewModelErrorHandler` since it cannot use constructor injection.

```kotlin
class GlobalErrorHandler : CoroutineExceptionHandler {
    override val key = CoroutineExceptionHandler.Key
    override fun handleException(context: CoroutineContext, exception: Throwable) {
        // Resolve LoggingPort via EntryPoint
        // log to Crashlytics
    }
}
```

## 6. Testing Architecture

### 6.1. Unit Tests
- `ViewModelErrorHandlerImplTest`: Test debounce logic using `kotlinx-coroutines-test` and `currentTimeMillis` manipulation.
- `AndroidLoggingAdapterTest`: Verify Crashlytics interaction and degradation.

### 6.2. Integration Tests
- `ChatViewModelTest`: Verify that when `processPrompt` throws, the `errorHandler` is called with the expected tag and user message.

### 6.3. UI Tests
- `ErrorSnackbarTest`: Use Compose UI tests to verify that emitting an event on `ViewModelErrorHandler.errorEvents` causes a Snackbar to appear in the root `Scaffold`.
