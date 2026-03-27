# Architecture Decision Record: ViewModel Error Handling & Remote Logging

## Status
Accepted

## Context
The application needed a unified way to handle errors originating from ViewModels, background Workers, and Services. The solution required showing user-friendly Snackbars (without spamming the user), logging exceptions to a remote service (Firebase Crashlytics) for observability, and ignoring structured concurrency cancellations. 

The application has a mix of ViewModels, some using standard Hilt injection and others using Assisted Injection (e.g., `DownloadViewModel`).

## Decision

1. **Adopt a Hilt-provided `ViewModelErrorHandler` Singleton (Option B)**
   We evaluated three primary options for shared ViewModel error handling:
   - *Option A: Base ViewModel.* Rejected because it complicates `AssistedInject` factories. In Android, creating a Base ViewModel that takes arguments often results in generic boilerplate that makes AssistedInject cumbersome.
   - *Option C: Custom `runCatching` extensions.* Rejected because it decentralizes the flow of error emissions. Handling a global UI debounce (5 seconds) across multiple decoupled extensions is difficult.
   - *Option B: Singleton `ViewModelErrorHandler`.* **Selected.** By injecting a singleton error handler into any ViewModel (or Worker/Service), we can maintain a central state for the 5-second Snackbar debounce. It is highly robust, works seamlessly with Assisted Injection, and provides a single stream (`SharedFlow`) for the UI to observe.

2. **Integrate Remote Logging into `LoggingPort`**
   Instead of introducing a new `RemoteLoggingPort`, we are extending the existing `LoggingPort` with a `recordException` method. This reduces the cognitive load for developers (they only need to inject one port) and ensures that all error logging is automatically considered for remote transmission.

3. **Firebase Crashlytics with Graceful Degradation**
   Crashlytics was chosen because it natively supports offline buffering and automatic retry. To ensure the app can run without Google Play Services or if Firebase initialization fails, we wrap the SDK calls in a `runCatching` block inside the `AndroidLoggingAdapter`. PII is excluded naturally by only sending stack traces and custom keys (Device Model, App Version).

4. **Global CoroutineExceptionHandler via ServiceLoader**
   To catch unhandled exceptions globally across all un-scoped coroutines (including Compose event handlers and Workers), we will register a global `CoroutineExceptionHandler` using the `META-INF/services` mechanism for `kotlinx.coroutines.CoroutineExceptionHandler`. This handler will use an Hilt `EntryPoint` to resolve the `LoggingPort` statically, as constructor injection is not possible for classes loaded via `ServiceLoader`.

5. **Ignore `CancellationException`**
   We explicitly filter out `CancellationException` to avoid logging or showing errors when coroutines are cancelled as part of normal structured concurrency (e.g., navigating away from a screen).

## Consequences
- **Positive:** All UI errors are routed through a single pipeline, guaranteeing the 5-second debounce and preventing Snackbar spam.
- **Positive:** Developers don't need to manually catch and log to Crashlytics; the `ViewModelErrorHandler` does it automatically.
- **Positive:** `CancellationException` is explicitly ignored, adhering to Kotlin Coroutines best practices.
- **Negative:** We must collect the `errorEvents` flow at the highest level of the UI (e.g., the root Scaffold in `MainActivity`), which means we cannot easily show screen-specific Snackbar styling without passing additional state through the handler.
