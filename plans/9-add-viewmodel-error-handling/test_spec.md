# Test Specification: ViewModel Error Handling & Remote Logging

## 1. Unit Tests

### 1.1. `ViewModelErrorHandlerImplTest`
**File Path:** `core/ui/src/test/kotlin/com/browntowndev/pocketcrew/core/ui/error/ViewModelErrorHandlerImplTest.kt`

- **Scenario: Single error emits to flow**
  - **Given:** A fresh `ViewModelErrorHandlerImpl`.
  - **When:** `handleError` is called once.
  - **Then:** `errorEvents` should emit the user message exactly once.
  - **And:** `LoggingPort.recordException` should be called.

- **Scenario: Rapid errors are debounced**
  - **Given:** A fresh `ViewModelErrorHandlerImpl`.
  - **When:** `handleError` is called 3 times within 1 second.
  - **Then:** `errorEvents` should emit the user message exactly once.
  - **And:** `LoggingPort.recordException` should be called 3 times (logging all, but only showing 1 Snackbar).

- **Scenario: Errors after debounce interval are shown**
  - **Given:** An error was handled at T=0.
  - **When:** `handleError` is called at T=5.1 seconds.
  - **Then:** `errorEvents` should emit a second message.

- **Scenario: CancellationException is ignored**
  - **Given:** `handleError` is called with `CancellationException`.
  - **When:** `handleError` executes.
  - **Then:** `errorEvents` should NOT emit.
  - **And:** `LoggingPort` should NOT be called.

### 1.2. `AndroidLoggingAdapterTest`
**File Path:** `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/AndroidLoggingAdapterTest.kt`

- **Scenario: Crashlytics is called**
  - **Given:** A mock `FirebaseCrashlytics`.
  - **When:** `recordException` is called.
  - **Then:** Verify `setCustomKey` and `recordException` are called on the mock.

- **Scenario: Graceful degradation**
  - **Given:** `FirebaseCrashlytics.getInstance()` throws an exception (e.g., initialization failed).
  - **When:** `recordException` is called.
  - **Then:** No crash occurs, and standard `Log.e` is used.

## 2. Integration Tests (ViewModels)

### 2.1. `ChatViewModelTest`
- **Scenario: Error in onSendMessage is caught**
  - **Given:** `chatUseCases.processPrompt` throws an `IOException`.
  - **When:** `onSendMessage` is called.
  - **Then:** `ViewModelErrorHandler.handleError` is invoked with `ChatViewModel` tag.

### 2.2. `HistoryViewModelTest`
- **Scenario: Error in deleteChat is caught**
  - **Given:** `deleteChatUseCase` throws an exception.
  - **When:** `deleteChat` is called.
  - **Then:** `ViewModelErrorHandler.handleError` is invoked.

## 3. UI Tests (Compose)

### 3.1. `MainScreenErrorTest`
**File Path:** `app/src/androidTest/kotlin/com/browntowndev/pocketcrew/ui/MainScreenErrorTest.kt`

- **Scenario: Snackbar visibility**
  - **Given:** The app is running on the main screen.
  - **When:** An event is emitted on `ViewModelErrorHandler.errorEvents`.
  - **Then:** A Snackbar with the expected text is displayed.
  - **And:** It disappears after the standard duration.
