# Chat Inference Service — Implementation Fixes v2

## Objective

Fix all identified issues in the `:feature:chat-inference-service` implementation, including the critical build-breaking missing dependency, the foreground notification check, the architectural bridge class, the `DirectChatInferenceExecutor` location, and test coverage gaps.

## Key Design Decisions

### SharedFlow vs BroadcastReceiver

The current implementation uses `SharedFlow` for streaming inference state from `ChatInferenceService` back to the caller, rather than the `BroadcastReceiver` pattern used by the MOA `InferenceService`. **SharedFlow is the correct choice** for single-model chat inference because:

1. **Latency**: SharedFlow delivers events in-process with zero serialization; BroadcastReceiver requires Intent serialization/deserialization per chunk
2. **Type safety**: SharedFlow carries `MessageGenerationState` directly; BroadcastReceiver requires string extras and manual parsing
3. **Backpressure**: SharedFlow supports `extraBufferCapacity`; BroadcastReceiver has no backpressure control
4. **Process model**: Both services run in the same app process. BroadcastReceiver's cross-process capability is unused overhead.

The MOA `InferenceService` uses BroadcastReceiver for discrete step boundaries (4 steps, each producing a final output), not continuous token streaming. For chat inference's rapid token flow, SharedFlow is strictly better.

### DirectChatInferenceExecutor lives in `:domain`

The plan originally specified `:domain` and that was correct. The class is pure Kotlin — all imports are `com.browntowndev.pocketcrew.domain.*`, `kotlinx.coroutines.*`, or `javax.inject.*`. The `:domain` module already has `javax.inject:javax.inject:1` on its classpath (`core/domain/build.gradle.kts:59`), so `@Inject` is available. No new dependencies are needed.

## Implementation Plan

### Phase 1: Critical Build Fix

- [ ] **1.1** Add `implementation(project(":feature:chat-inference-service"))` to `app/build.gradle.kts` after line 140 (alongside other feature module dependencies). Without this, the service class, Hilt module, and manifest merger are excluded from the APK — the entire background inference path fails at runtime.

### Phase 2: Move DirectChatInferenceExecutor to `:domain`

- [ ] **2.1** Move `DirectChatInferenceExecutor.kt` from `feature/chat-inference-service/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/service/` to `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/` — updating the package declaration to `com.browntowndev.pocketcrew.domain.usecase.chat`. All dependencies (`InferenceFactoryPort`, `ActiveModelProviderPort`, `MessageRepository`, `SettingsRepository`, `SearchToolPromptComposer`, `LoggingPort`, `ChatHistoryRehydrator`, `ChatInferenceRequestPreparer`) already exist in `:domain`.
- [ ] **2.2** Update all imports referencing the old package location: `ChatInferenceExecutorRouter`, `ChatInferenceServiceModule` (if it references the class directly), and the router test.

### Phase 3: Architectural — ChatInferenceServiceExecutor Bridge Class

Currently `ChatInferenceServiceStarter.startInference()` returns `Flow<MessageGenerationState>` by directly calling `ChatInferenceService.observeState(chatId)`. This conflates two responsibilities: (a) starting the service, and (b) bridging service state to Flow. A separate `ChatInferenceServiceExecutor` implementing `ChatInferenceExecutorPort` should handle the bridge.

- [ ] **3.1** Create `ChatInferenceServiceExecutor` in `feature/chat-inference-service/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/service/ChatInferenceServiceExecutor.kt`:
  - Implements `ChatInferenceExecutorPort`
  - `execute()`: Calls `serviceStarter.startService()` (void) to start the FGS, then returns `ChatInferenceService.observeState(chatId)` (the SharedFlow)
  - `stop()`: Calls `serviceStarter.stopInference()`
  - Wraps service start in try/catch for `ForegroundServiceStartNotAllowedException` on Android 12+, wrapping the SharedFlow with `callbackFlow` so a failed start emits `MessageGenerationState.Failed`
- [ ] **3.2** Simplify `ChatInferenceServiceStarter` — remove the `startInference()` method that returns `Flow<MessageGenerationState>`. Replace with `startService()` (void) that only creates the intent and calls `context.startForegroundService(intent)`. Keep `stopInference()` as-is.
- [ ] **3.3** Update `ChatInferenceExecutorRouter` to depend on `ChatInferenceServiceExecutor` instead of `ChatInferenceServiceStarter`. Route `backgroundInferenceEnabled=true` to `serviceExecutor.execute()`.
- [ ] **3.4** Update `ChatInferenceServiceModule` — no changes needed since it binds `ChatInferenceExecutorRouter` which transitively resolves dependencies via Hilt `@Inject`.

### Phase 4: Foreground Check for Completion Notification

- [ ] **4.1** Add a foreground state check in `ChatInferenceService.showCompletionNotification()` using `ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)`. Only show the completion notification when the app is NOT in the foreground.
- [ ] **4.2** Add `implementation(libs.androidx.lifecycle.process)` to `feature/chat-inference-service/build.gradle.kts` if not already present (needed for `ProcessLifecycleOwner`).

### Phase 5: Test Coverage

- [ ] **5.1** Write `DirectChatInferenceExecutorTest` in `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/DirectChatInferenceExecutorTest.kt` — Test the full inference flow: history rehydration, prompt preparation, event mapping for all `InferenceEvent` variants, error handling for `InferenceBusyException`, `IllegalStateException`, `IOException`, generic exceptions, and `CancellationException` re-throw. Test that `stop()` is a no-op. Uses `FakeInferenceFactory` from existing test fixtures.
- [ ] **5.2** Write `ChatInferenceServiceExecutorTest` in `feature/chat-inference-service/src/test/kotlin/com/browntowndev/pocketcrew/feature/chat/service/ChatInferenceServiceExecutorTest.kt` — Test that `execute()` starts the service and returns the SharedFlow from `ChatInferenceService.observeState()`, that `stop()` calls `stopInference()`, and that `ForegroundServiceStartNotAllowedException` is handled by emitting `MessageGenerationState.Failed`.
- [ ] **5.3** Write `CancelInferenceUseCaseTest` in `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/inference/CancelInferenceUseCaseTest.kt` — Test that `invoke()` calls `conversationManager.cancelCurrentGeneration()`, `conversationManager.cancelProcess()`, and `chatInferenceExecutor.stop()`.
- [ ] **5.4** Update `ChatInferenceExecutorRouterTest` — Update to use `ChatInferenceServiceExecutor` instead of `ChatInferenceServiceStarter`. Test routing to `serviceExecutor` when `backgroundInferenceEnabled=true` and to `directExecutor` when `false`.
- [ ] **5.5** Verify existing `GenerateChatResponseUseCaseSearchToolTest` still compiles — check that the `chatInferenceExecutor` constructor parameter is correctly mocked in existing tests.

### Phase 6: Build Verification

- [ ] **6.1** Run `./gradlew :core:domain:assemble` to verify domain still has no Android dependencies after adding `DirectChatInferenceExecutor`.
- [ ] **6.2** Run `./gradlew :core:domain:testDebugUnitTest` to verify domain tests pass.
- [ ] **6.3** Run `./gradlew :feature:chat-inference-service:assemble` to verify the module compiles.
- [ ] **6.4** Run `./gradlew :feature:chat-inference-service:testDebugUnitTest` to verify module tests pass.
- [ ] **6.5** Run `./gradlew :app:assembleDebug` to verify the full app compiles with the new dependency.

## Verification Criteria

- `app/build.gradle.kts` includes `implementation(project(":feature:chat-inference-service"))`
- `DirectChatInferenceExecutor` lives in `core/domain/src/main/kotlin/.../domain/usecase/chat/` with correct package
- `ChatInferenceServiceExecutor` implements `ChatInferenceExecutorPort` and bridges service start + SharedFlow collection
- `ChatInferenceServiceStarter.startService()` returns void (no Flow)
- `ChatInferenceExecutorRouter` routes to `ChatInferenceServiceExecutor` (not `ChatInferenceServiceStarter`)
- Completion notification only shows when app is backgrounded
- All new test files pass
- `./gradlew :app:assembleDebug` succeeds
- `./gradlew :core:domain:assemble` succeeds (proving no Android deps leaked into domain)

## Potential Risks and Mitigations

1. **ForegroundServiceStartNotAllowedException on Android 12+**: If the app is in the background when `startForegroundService()` is called, the system throws. Mitigation: `ChatInferenceServiceExecutor` wraps the SharedFlow in `callbackFlow` and catches the exception, emitting `MessageGenerationState.Failed` with a descriptive error.
2. **SharedFlow buffer overflow**: `extraBufferCapacity = 64` may drop events under extreme load. Mitigation: Acceptable since UI state updates are idempotent — the last state wins.
3. **ProcessLifecycleOwner needs lifecycle-process dependency**: Must add to module's build.gradle.kts. Mitigation: Check if available transitively from app module; if not, add explicitly.

## Alternative Approaches

1. **Keep DirectChatInferenceExecutor in `:feature:chat-inference-service`**: Avoids the file move but contradicts the plan's intent and keeps a pure-Kotlin class needlessly coupled to an Android feature module. Moving to `:domain` is cleaner.
2. **Use BroadcastReceiver instead of SharedFlow**: Would align with MOA pattern but adds latency and complexity (Intent serialization per chunk) with no benefit for same-process scenarios. SharedFlow is the better choice for continuous token streaming.
3. **Flag-based foreground check** (simple boolean) instead of `ProcessLifecycleOwner`: Simpler but requires manual lifecycle wiring. `ProcessLifecycleOwner` is the standard Android approach with no manual integration needed.