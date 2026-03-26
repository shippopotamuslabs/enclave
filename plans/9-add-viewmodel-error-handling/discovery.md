# Discovery Phase: ViewModel Error Handling & Remote Logging

## 1. Scope & Behavior

1. The existing codebase uses a `HistoryEvent.ShowError(...)` pattern via `SharedFlow`. Should the new shared error handling layer follow this same pattern (a base ViewModel that exposes a shared error Flow), or should it be a different abstraction?

**ANSWER** This seems fine to me, but if there's a better pattern we can use it instead. Just make sure everything is centralized to use the same pattern.

2. Should the error-handling layer apply globally to all 5+ ViewModels, or only to specific ones? **ANSWER** All

3. Are there operations (e.g., `onSendMessage()` in `ChatViewModel`) where errors should never surface to the user silently — only log? **ANSWER** No, no silent failures. Tell the user something happened.

4. What error types should be caught? All `Exception`, specific subtypes (`IOException`, `TimeoutException`), or only non-fatal ones? 

**ANSWER** Everything should be caught and logged unless it is eplicitly within a retry pattern or other graceful error handling pattern that doesn't result in a failure. Anything resulting in a failure of a given
action that the user expected to succeed should be logged and shown to the user as a good user-facing message. Don't silently fail and leave them wondering what happened.

5. Should caught errors always be both logged to Crashalytics and shown to the user via Snackbar, or should some errors only be logged?

**ANSWER** See 4

## 2. Data Persistence & Privacy

6. **[ARCHITECTURAL]** Should `LoggingPort` be extended with a `recordException()` or `logToRemote()` method, or should Crashalytics be wired in separately from `LoggingPort`?

**ANSWER** This actually seems like a really good place to put it. In the "log error" we should just also log to Crashalytics in addition to the console.

7. Are there privacy/GDPR considerations when sending stack traces to a remote service? Does the app have consent/disclosure in place?

**ANSWER** I am only deploying this within the US. So whatever US laws require. No I have no consent or disclosure in place yet. I am only in the infancy of the app.

8. Should PII (user inputs, conversation content) ever appear in crash logs? If not, how should it be redacted?

**ANSWER** No PII at all. Just exceptions that can be used for troubleshooting and identifying issues with the app. Right now there's no way for PII to even enter into the app in the first place that I know of anyway. But
if you see some, redact it.

9. Should custom attributes (user ID, app version, device model) be attached to Crashalytics events?

**ANSWER** Device model and app version for sure. That would be very helpful given the type of app this is.

## 3. UI/UX Flows

10. What should the Snackbar message say for different error types? A generic fallback message, or the raw exception message?

**ANSWER** Definitely NOT a raw stack trace. Everything shown should be a user-friendly message like: "Failed to send message", etc.

11. Should the Snackbar include a Retry action? If so, should each operation define its own retry logic?

**ANSWER** No, not now at least. This will massively balloon complexity.

12. What happens if multiple errors fire in rapid succession? Should the Snackbar replace the previous one, append to it, or queue?

**ANSWER** Spamming the shit out of them with snackbars seems wrong, so I guess debounce and only show 1 per 5 seconds or something? Idk, how do major apps handle this?

13. What should the Snackbar duration be — short (`SnackbarDuration.Short`), long, or indefinite until dismissed by the user?

**ANSWER** Whatever Android best practices are for this sort of thing.

14. Should errors ever block navigation (e.g., prevent the user from leaving a screen)?

**ANSWER** No.

## 4. Technical Architecture

15. **[ARCHITECTURAL]** The codebase has no base ViewModel class — all ViewModels extend Android's ViewModel directly. Three approaches are common in the Android ecosystem. Which approach is preferred, considering that `DownloadViewModel` uses assisted injection with a Factory?

    - A. Base ViewModel class with a `CoroutineExceptionHandler` and shared error `SharedFlow` (requires all ViewModels to inherit)
    - B. A Hilt-provided `ViewModelErrorHandler` singleton that ViewModels inject and call manually from their catch blocks
    - C. A custom `runCatching` extension on suspend functions that wraps calls and emits to a shared error channel

    **ANSWER** I am not an Android native dev primarily so I'm not sure which is best. Do whichever one is the most robust and S-Tier production grade for an enterprise level app.

16. **[ARCHITECTURAL]** Should errors from outside ViewModels also be caught (e.g., Worker classes, Service classes, Compose event handlers)?

**ANSWER** Yes, I want these caught as well and logged. I want robust error handling.

17. **[ARCHITECTURAL]** Should the solution integrate with the existing `LoggingPort` interface, or create a separate `RemoteLoggingPort`?

**ANSWER** Integrated. Then it's hard to forget to log to remote. If they're not integrated you have to do double calls.

18. **[ARCHITECTURAL]** Should unhandled coroutine exceptions (those that bypass `viewModelScope.launch {}`) be caught globally via `CoroutineExceptionHandler`? If so, where should that be installed?

**ANSWER** My intuition is yes but I don't actually know what CoroutineExceptionHandler is or where it should be placed, so use best practices here.

## 5. Remote Logging Alternatives

19. **[ARCHITECTURAL]** The user asked about Crashalytics alternatives. Criteria that matter for this app include non-fatal exception monitoring, custom event/log support, offline buffering, EU data residency options, and free tier availability. Should the evaluation be limited to Crashalytics, or expanded to include Sentry, Logbee, Bugfender, or Instabug?

**ANSWER** This is a US only app and I am only familiar with Crashalytics. So really I want to know if there are any other options that would add anything or be better. They have to have a generous non-paid tier like Firebase.

20. Does the team need both crash reporting (post-mortem stack traces) and non-fatal error monitoring?

**ANSWER** I would like both if possible.

21. Is there an existing logging/monitoring platform (Datadog, New Relic, Firebase ecosystem beyond Crashalytics) that the team is already committed to?

**ANSWER** I have no commitments. Whatever is best I want to use.

## 6. Testing & Observability

22. How should the error-handling layer itself be tested? Unit tests verifying logs are sent? UI tests verifying Snackbar appears?

**ANSWER** I'm not sure. Whatever best practices are.

23. Should error frequency be tracked in analytics (e.g., "X users see error Y per day")?

**ANSWER** Doesn't Firebase Crashalytics do this already?

24. Who should receive alerts when new crash types appear? Is PagerDuty or Slack integration needed?

**ANSWER** No I don't want paged or anything. I am in indie dev making this.

25. Should there be a way to locally suppress known-bug errors so they don't inflate crash rates?

**ANSWER** I don't understand the use case. So I would write code that says "don't log this because it's a bug" instead of fixing the bug? That seems crazy. So no.

## 7. Edge Cases

26. What happens when Crashalytics fails to initialize (e.g., missing Google Play Services)? Should the app degrade gracefully or crash?

**ANSWER** Just don't use Crashalytics in that case. Still run just fine, just don't log anything.

27. What happens when the device is offline? Should logs be buffered locally and retried when connectivity returns?

**ANSWER** Yes.

28. Should background errors (e.g., from WorkManager workers) be treated differently from foreground UI errors?

**ANSWER** I am not sure. Partitioning may help comb through the logs, but I'm not sure how to do this.

29. **[ARCHITECTURAL]** Should cancellation exceptions (`CancellationException`) be caught and logged, or always ignored to respect structured concurrency?

**ANSWER** Ignore them it sounds like. I don't know what these are but it sounds like you are saying it's best practice to ignore them.

30. **[ARCHITECTURAL]** Should errors from `kotlinx.coroutines.flow` operators (e.g., upstream exceptions in `combine {}`, `flatMapLatest {}`) be caught at the ViewModel level, or handled upstream in the use case?

**ANSWER** Whatever is best design pratice.
