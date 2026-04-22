# Chat Inference Service — Implementation Fixes

## Objective

Fix all identified issues in the `:feature:chat-inference-service` implementation, including the critical build-breaking missing dependency, the foreground notification check, the architectural `ChatInferenceServiceExecutor` bridge class, and the test coverage gaps.

## Key Design Decision: SharedFlow vs BroadcastReceiver

The current implementation uses `SharedFlow` for streaming inference state from `ChatInferenceService` back to the caller, rather than the `BroadcastReceiver` pattern used by the MOA `InferenceService`. **This is the correct choice** for single-model chat inference because:

1. **Latency**: SharedFlow delivers events in-process with zero serialization; BroadcastReceiver requires Intent serialization/deserialization per chunk
2. **Type safety**: SharedFlow carries `MessageGenerationState` directly; BroadcastReceiver requires string extras and manual parsing
3. **Backpressure**: SharedFlow supports `extraBufferCapacity`; BroadcastReceiver has no backpressure control
4. **Process model**: Both `ChatInferenceService` and the MOA `InferenceService` run in the same app process (no `android:process` attribute). BroadcastReceiver's cross-process capability is unused overhead here.

The MOA `InferenceService` uses BroadcastReceiver because it has discrete step boundaries (4 steps, each producing a final output), not rapid token streaming. For chat inference's continuous token flow, SharedFlow is strictly better.

## Implementation Plan

### Phase 1: Critical Build Fix

- [ ] **1.1** Add `implementation(project(":feature:chat-inference-service"))` to `app/build.gradle.kts` (after line 140, alongside other feature module dependencies). Without this, the service class, Hilt module, and manifest merger are excluded from the APK.

### Phase 2: Architectural — ChatInferenceServiceExecutor Bridge Class

Currently `ChatInferenceServiceStarter.startInference()` returns `Flow<MessageGenerationState>` by directly calling `ChatInferenceService.observeState(chatId)`. This conflates two responsibilities: (a) starting the service, and (b) bridging service state to Flow. The plan called for a separate `ChatInferenceServiceExecutor` implementing `ChatInferenceExecutorPort` that handles the bridge. This class should:

- Start the service via `ChatInferenceServiceStarter`
- Collect the `SharedFlow` from `ChatInferenceService.observeState(chatId)`
- Handle errors from service startup (e.g., `ForegroundServiceStartNotAllowedException`)
- Implement `stop()` by calling `ChatInferenceServiceStarter.stopInference()`

- [ ] **2.1** Create `ChatInferenceServiceExecutor` in `feature/chat-inference-service/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/service/ChatInferenceServiceExecutor.kt` that implements `ChatInferenceExecutorPort`
  - `execute()`: Calls `serviceStarter.startInference()` to start the service, then returns `ChatInferenceService.observeState(chatId)` (the SharedFlow)
  - `stop()`: Calls `serviceStarter.stopInference()`
  - Wraps service start in try/catch for `ForegroundServiceStartNotAllowedException` on Android 12+, emitting `MessageGenerationState.Failed` if the service can't start
- [ ] **2.2** Simplify `ChatInferenceServiceStarter` to remove the `startInference()` method that returns `Flow<MessageGenerationState>`. Replace it with a void `startService()` method that only creates the intent and starts the foreground service. Keep `stopInference()` as-is.
- [ ] **2.3** Update `ChatInferenceExecutorRouter` to depend on `ChatInferenceServiceExecutor` instead of `ChatInferenceServiceStarter`. Route `backgroundInferenceEnabled=true` to `serviceExecutor.execute()`.
- [ ] **2.4** Update `ChatInferenceServiceModule` Hilt module — no changes needed since it binds `ChatInferenceExecutorRouter` which transitively resolves the new dependency graph via Hilt `@Inject`.

### Phase 3: Foreground Check for Completion Notification

- [ ] **3.1** Add a foreground state tracker to `ChatInferenceService` using `ProcessLifecycleOwner`. Inject an `Application` context and use `ProcessLifecycleOwner.get().lifecycle.currentState` to check if the app is in the foreground when `InferenceEvent.Finished` is received. Only call `showCompletionNotification()` when the app is NOT in the foreground.
- [ ] **3.2** Add `implementation(libs.androidx.lifecycle.process)` to `feature/chat-inference-service/build.gradle.kts` if not already present (needed for `ProcessLifecycleOwner`).

### Phase 4: Test Coverage

- [ ] **4.1** Write `ChatInferenceServiceExecutorTest` — Test that `execute()` starts the service and returns the SharedFlow, that `stop()` calls `stopInference()`, and that `ForegroundServiceStartNotAllowedException` is handled.
- [ ] **4.2** Write `DirectChatInferenceExecutorTest` — Test the full inference flow: history rehydration, prompt preparation, event mapping, error handling for `InferenceBusyException`, `IllegalStateException`, `IOException`, and generic exceptions. Test that `stop()` is a no-op.
- [ ] **4.3** Write `CancelInferenceUseCaseTest` — Test that `cancelInference()` calls `chatInferenceExecutor.stop()` and triggers the cancellation flow.
- [ ] **4.4** Update `ChatInferenceExecutorRouterTest` — Update to use `ChatInferenceServiceExecutor` instead of `ChatInferenceServiceStarter`. Test routing to `serviceExecutor` when `backgroundInferenceEnabled=true` and to `directExecutor` when `false`.
- [ ] **4.5** Verify existing `GenerateChatResponseUseCaseSearchToolTest` still compiles with the `chatInferenceExecutor` constructor parameter.

### Phase 5: Build Verification

- [ ] **5.1** Run `./gradlew :feature:chat-inference-service:assemble` to verify the module compiles.
- [ ] **5.2** Run `./gradlew :feature:chat-inference-service:testDebugUnitTest` to verify tests pass.
- [ ] **5.3** Run `./gradlew :core:domain:assemble` to verify domain still has no Android dependencies.
- [ ] **5.4** Run `./gradlew :app:assembleDebug` to verify the full app compiles with the new dependency.

## Verification Criteria

- `app/build.gradle.kts` includes `implementation(project(":feature:chat-inference-service"))`
- `ChatInferenceServiceExecutor` implements `ChatInferenceExecutorPort` and bridges service start + SharedFlow collection
- `ChatInferenceServiceStarter` only starts/stops the service (no Flow return)
- `ChatInferenceExecutorRouter` routes to `ChatInferenceServiceExecutor` (not `ChatInferenceServiceStarter`)
- Completion notification only shows when app is backgrounded
- All new test files pass
- `./gradlew :app:assembleDebug` succeeds

## Potential Risks and Mitigations

1. **ForegroundServiceStartNotAllowedException on Android 12+**: If the app is in the background when `startForegroundService()` is called, the system throws this exception. Mitigation: `ChatInferenceServiceExecutor` catches this and emits `MessageGenerationState.Failed` with a descriptive error.
2. **SharedFlow replay/buffer**: The current `extraBufferCapacity = 64` may cause events to be missed if the collector isn't fast enough. Mitigation: This is acceptable since UI state updates are idempotent — the last state wins.
3. **ProcessLifecycleOwner needs lifecycle-process dependency**: Must add `androidx.lifecycle:lifecycle-process` to the module's build.gradle. Mitigation: Already likely available transitively, but will add explicitly.

## Alternative Approaches

1. **Move DirectChatInferenceExecutor to `:domain`**: The plan originally called for this, but it would require adding Hilt dependency to `:domain`, violating clean architecture. The current location in `:feature:chat-inference-service` is acceptable since it only uses domain interfaces.
2. **Use BroadcastReceiver instead of SharedFlow**: Would align with MOA pattern but adds latency and complexity for no benefit in same-process scenarios. SharedFlow is the better choice for continuous token streaming.
3. **Use a flag-based foreground check** (simple boolean): Instead of `ProcessLifecycleOwner`, use a simple `AtomicBoolean` set by Activity lifecycle callbacks. Simpler but requires more wiring. `ProcessLifecycleOwner` is the standard Android approach.