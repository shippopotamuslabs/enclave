# Background-Safe Chat Inference — Implementation Verification Report

## Objective

Verify that the implementation plan (`/home/sean/Code/implementation_plan.md`) was properly executed and assess unit test coverage of all new code.

---

## Verification Results

### PASS — Component 1: `ChatInferenceExecutorPort` (Domain Port)

- **File**: `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/ChatInferenceExecutorPort.kt`
- Interface signature matches the plan exactly: 7 parameters on `execute()`, plus `stop()` method
- Lives in `:domain` — pure Kotlin, no Android dependencies
- Properly consumed by `GenerateChatResponseUseCase` and `CancelInferenceUseCase`

### PASS (with note) — Component 2: `DirectChatInferenceExecutor` (Direct/Fallback Executor)

- **File**: `feature/chat-inference-service/src/main/kotlin/.../service/DirectChatInferenceExecutor.kt`
- **Plan said**: "Pure Kotlin. Lives in `:domain`" → **Actually lives in `:feature:chat-inference-service`** (an Android module). The class itself has no Android imports, so it could theoretically be in `:domain`, but its dependency on `InferenceFactoryPort` (implemented in `:feature:inference`) makes this placement reasonable for now since `:domain` cannot depend on `:feature:inference`.
- Functionally correct: delegates to `InferenceFactoryPort.withInferenceService()`, uses `ChatHistoryRehydrator` and `ChatInferenceRequestPreparer`, maps events properly
- `stop()` is correctly a no-op
- **No unit tests exist for this class**

### PASS (with deviations) — Component 3: Feature Module `:feature:chat-inference-service`

#### 3a. Module Structure
- `build.gradle.kts` — Present, correctly configured
- `AndroidManifest.xml` — Present, correctly declares FGS with `specialUse`
- All key classes implemented

#### 3b. `ChatInferenceExecutorRouter`
- **File**: `feature/chat-inference-service/.../service/ChatInferenceExecutorRouter.kt`
- Routes based on `backgroundInferenceEnabled` flag — correctly delegates to `serviceStarter.startInference()` when true, `directExecutor.execute()` when false
- **Deviation**: Plan specified routing between `serviceExecutor` and `directExecutor` where `serviceExecutor` would be a `ChatInferenceServiceExecutor` implementing `ChatInferenceExecutorPort`. The actual implementation uses `ChatInferenceServiceStarter` directly (not through a separate executor port implementation).
- **Test coverage**: `ChatInferenceExecutorRouterTest` exists with 3 tests covering routing

#### 3c. `ChatInferenceService`
- **File**: `feature/chat-inference-service/.../service/ChatInferenceService.kt`
- Uses `@AndroidEntryPoint`, `foregroundServiceType="specialUse"`, `ACTION_START`/`ACTION_STOP`
- Uses `serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())`
- Notification system: ongoing notification with stop action, completion notification with deep link
- **Deviation from plan**: Uses `SharedFlow` instead of `BroadcastReceiver` pattern for state propagation. The `ChatInferenceServiceStarter.startInference()` returns `ChatInferenceService.observeState(chatId)` directly.
- **Missing**: Plan specified "The completion notification is only shown when the app is not in the foreground" — this foreground check is NOT implemented. `showCompletionNotification()` runs unconditionally on `Finished` event.

#### 3d. `ChatInferenceServiceStarter`
- **File**: `feature/chat-inference-service/.../service/ChatInferenceServiceStarter.kt`
- Creates start/stop intents, calls `context.startForegroundService()`
- Returns `ChatInferenceService.observeState(chatId)` from `startInference()`

#### 3e. `ChatInferenceServiceModule` (DI)
- Binds `ChatInferenceExecutorRouter` → `ChatInferenceExecutorPort`
- Hilt `@Module` with `@InstallIn(SingletonComponent::class)` — correct

#### 3f. Missing `ChatInferenceServiceExecutor`
- **The plan specified** a `ChatInferenceServiceExecutor` class implementing `ChatInferenceExecutorPort` using a `BroadcastReceiver` pattern. **This class does not exist.** The router directly calls `serviceStarter.startInference()`. The SharedFlow approach is simpler but means the service must run in the same process (not an issue for FGS, but departs from the plan's explicit BroadcastReceiver design).

### PASS — Component 4: Use Case Changes (`GenerateChatResponseUseCase`)

- **File**: `core/domain/src/main/kotlin/.../usecase/chat/GenerateChatResponseUseCase.kt`
- `executeSingleModelMode()` correctly delegates to `chatInferenceExecutor.execute()`
- `backgroundInferenceEnabled` parameter flows from `invoke()` through to the executor
- CREW mode logic unchanged
- `finally` block with `NonCancellable` persistence preserved

### PASS — Component 5: Settings Addition

- `SettingsData.backgroundInferenceEnabled: Boolean = true` — Added correctly
- `SettingsRepository.updateBackgroundInferenceEnabled()` — Interface method added
- `SettingsRepositoryImpl` — DataStore key implemented with default `true`
- `SettingsHomeUiState.backgroundInferenceEnabled` — Added to UI model
- `ChatUiState.backgroundInferenceEnabled` — Added to chat UI model
- `SettingsUiStateFactory` — Maps persisted settings to UI state
- `ChatViewModel` — Passes `backgroundInferenceEnabled` to `generateChatResponse()`
- Settings toggle UI — Present in `SettingsScreen.kt` with appropriate title and subtitle

### PARTIAL — Component 6: Manifest & Build Configuration

- **`settings.gradle.kts`** — Module registered correctly (`include(":feature:chat-inference-service")`)
- **`app/build.gradle.kts`** — **MISSING** `implementation(project(":feature:chat-inference-service"))`. The module is declared but the app doesn't depend on it. This means the service's code, Hilt module, and manifest won't be merged into the APK.
- **`app/AndroidManifest.xml`** — The old `InferenceService` (MOA pipeline) is still present. The plan called for removing it, but this is the MOA service, not a placeholder — it should stay. However, the new `ChatInferenceService` from the feature module's manifest needs to be merged via the app dependency, which won't happen until `app/build.gradle.kts` is updated.

### PARTIAL — Component 7: Notification + Deep Link

- Progress notification — Implemented with "Generating AI response..." text, stop button, deep link content intent
- Completion notification — Implemented with "AI response complete." text and deep link
- **Missing**: Foreground check before showing completion notification (plan specified using `ProcessLifecycleOwner` or similar)

---

## Critical Build Issue

### `:feature:chat-inference-service` is not a dependency of `:app`

The module is declared in `settings.gradle.kts` but NO `build.gradle.kts` file includes it as a dependency. Without `implementation(project(":feature:chat-inference-service"))` in `app/build.gradle.kts`, the following won't be included in the APK:
1. The `ChatInferenceService` Android component
2. The `ChatInferenceServiceModule` Hilt binding
3. The `AndroidManifest.xml` service declaration

**Impact**: The entire background inference path will fail at runtime. The router's `serviceStarter.startInference()` will fail because `ChatInferenceService` class won't be in the classpath. Hilt won't be able to resolve `ChatInferenceExecutorPort` because the module isn't loaded.

**Fix**: Add `implementation(project(":feature:chat-inference-service"))` to `app/build.gradle.kts`.

---

## Test Coverage Assessment

### Existing Test Files

| Test File | Status | Coverage |
|-----------|--------|----------|
| `ChatInferenceExecutorRouterTest.kt` | EXISTS | 3 tests: routes to service when enabled, routes to direct when disabled, stop delegates to serviceStarter |
| `ChatInferenceRequestPreparerTest.kt` | EXISTS (domain) | Tests for the extracted helper |
| `ChatHistoryRehydratorTest.kt` | EXISTS (domain) | Tests for the extracted helper |

### Missing Test Files (Per Plan)

| Missing Test | Plan Reference | Risk |
|-------------|---------------|------|
| `DirectChatInferenceExecutor` tests | Plan specified: "Unit test DirectChatInferenceExecutor (extracted existing logic, existing test coverage applies)" | **MEDIUM** — New class with no tests. Core inference logic that was previously inline. |
| `ChatInferenceService` tests | Not explicitly in plan, but the service is a new complex Android component | **HIGH** — FGS lifecycle, notification channel creation, state emission, error handling, and cancellation all need testing. |
| `ChatInferenceServiceStarter` tests | Not in plan | **LOW** — Simple wrapper for intent creation. |
| `GenerateChatResponseUseCase` updated tests | Plan specified: "Update GenerateChatResponseUseCase tests to mock the new port" | **MEDIUM** — The use case now delegates to `chatInferenceExecutor` for FAST/THINKING modes. Need to verify the delegation works correctly, especially that CREW mode still bypasses the executor. |
| `CancelInferenceUseCase` updated tests | Not in plan, but the class was modified to include `chatInferenceExecutor.stop()` | **MEDIUM** — New dependency, need to verify `stop()` is called. |
| `ChatInferenceExecutorPort` tests | No interface test needed (tested via implementations) | N/A |

### Test Coverage Gaps Summary

1. **No test for `DirectChatInferenceExecutor`** — This class contains significant logic (inference execution, history rehydration, error handling, event mapping). The plan claimed "existing test coverage applies" but this is a new class and no tests were written for it.

2. **No test for `ChatInferenceService`** — This is the most complex new component. It manages FGS lifecycle, coroutine scope, notifications, intent handling, and SharedFlow emission. There are zero tests for it.

3. **No test for `GenerateChatResponseUseCase` updates** — The use case was refactored to delegate FAST/THINKING modes to `chatInferenceExecutor`. The existing test files (`GenerateChatResponseUseCaseSearchToolTest`, `GenerateChatResponseUseCaseImageToolTest`) may not cover the new delegation pattern.

4. **No test for `CancelInferenceUseCase`** — Modified to call `chatInferenceExecutor.stop()`, but has no test file.

---

## Summary of Issues

### Critical (Build-Breaking)

1. **Missing app dependency**: `app/build.gradle.kts` does not include `implementation(project(":feature:chat-inference-service"))`. The feature module's code, Hilt module, and manifest won't be merged into the APK.

### High (Functional Defects)

2. **No foreground check for completion notification**: `showCompletionNotification()` fires unconditionally, even when the user is actively viewing the chat. This will be annoying UX. The plan explicitly specified a foreground check.

### Medium (Missing Coverage)

3. **No unit tests for `DirectChatInferenceExecutor`** — Contains substantial inference logic.
4. **No unit tests for `ChatInferenceService`** — Complex FGS with state management.
5. **No unit tests for `GenerateChatResponseUseCase` delegation to `chatInferenceExecutor`** — Need to verify FAST/THINKING delegations.
6. **No unit tests for `CancelInferenceUseCase`** calling `chatInferenceExecutor.stop()`.

### Low (Deviation from Plan, Not Blockers)

7. **`DirectChatInferenceExecutor` located in `:feature:chat-inference-service` instead of `:domain`** — Plan said `:domain`. Current location works but couples the direct executor to the Android feature module unnecessarily.
8. **SharedFlow pattern instead of BroadcastReceiver** — Plan's `ChatInferenceServiceExecutor` class with BroadcastReceiver doesn't exist. Current approach uses `SharedFlow` + direct `observeState()`. This works for in-process communication but diverges from the plan.
9. **`ChatInferenceServiceStarter` named differently** — Plan called it `ChatInferenceServiceStarter` which matches, but the plan also specified a separate `ChatInferenceServiceExecutor` class that was never created.

### Resolved (Plan Error)

10. **App manifest InferenceService** — Plan said to remove the `InferenceService` from `app/AndroidManifest.xml`, but this is the MOA pipeline service, not a placeholder. It should remain. No action needed.