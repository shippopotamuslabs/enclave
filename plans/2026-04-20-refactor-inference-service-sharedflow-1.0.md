# Refactor InferenceService from BroadcastReceiver to SharedFlow

## Objective

Replace the `BroadcastReceiver`/`sendBroadcast()` communication mechanism in the MOA (`Crew`) inference pipeline with a `SharedFlow`-based `InferenceEventBus`, matching the architecture already established by `ChatInferenceService`. This eliminates the IPC overhead of broadcasts for high-frequency LLM token streaming, simplifies the consumer code in `InferenceServicePipelineExecutor`, and removes an entire category of lifecycle bugs (receiver leaks, missed broadcasts, ordering issues).

**Key architectural principle:** The chunks and progress updates are transient UI state. They do not need to survive process death. If the foreground service process is killed, the pipeline stops and does not resume. This makes `SharedFlow` (an in-memory hot stream) the correct abstraction.

---

## Current State Analysis

### Files Involved

| File | Role | Lines |
|------|------|-------|
| `feature/moa-pipeline-worker/.../moa/InferenceEventBus.kt` | Singleton `@Inject` class. Currently **unused**. Holds `MutableSharedFlow<Intent>` | 22 |
| `feature/moa-pipeline-worker/.../moa/service/InferenceService.kt` | Foreground service. Sends 4 broadcast actions (`BROADCAST_PROGRESS`, `BROADCAST_ERROR`, `BROADCAST_STEP_COMPLETED`, `BROADCAST_STEP_STARTED`) via `sendBroadcast()` | 728 |
| `feature/moa-pipeline-worker/.../moa/InferenceServicePipelineExecutor.kt` | `PipelineExecutorPort` impl. Registers a `BroadcastReceiver` inside `callbackFlow` for both `executePipeline()` and `resumeFromState()` | 280 |
| `feature/moa-pipeline-worker/.../moa/InferenceServicePipelineExecutorTest.kt` | Unit tests that mock `BroadcastReceiver` registration and simulate `Intent` deliveries | 306 |
| `feature/moa-pipeline-worker/.../moa/service/InferenceServiceStarter.kt` | Helper to build/start service intents. Already has `stopService()` method | 120 |

### Broadcast Usage Map

| Broadcast Action | Producer (InferenceService) | Consumer (PipelineExecutor) | Maps to MessageGenerationState |
|---|---|---|---|
| `BROADCAST_STEP_STARTED` | `broadcastStepStarted()` before model generation | Receiver → `trySend(Processing)` | `Processing(modelType)` |
| `BROADCAST_PROGRESS` (thinking chunk) | `broadcastProgress(EXTRA_THINKING_CHUNK)` | Receiver → `trySend(ThinkingLive)` | `ThinkingLive(chunk, modelType)` |
| `BROADCAST_PROGRESS` (step output) | `broadcastProgress(EXTRA_STEP_OUTPUT)` | Receiver → `trySend(GeneratingText)` | `GeneratingText(chunk, modelType)` |
| `BROADCAST_STEP_COMPLETED` | `broadcastStepCompleted()` after step done | Receiver → `trySend(StepCompleted)`; closes flow if FINAL | `StepCompleted(...)` |
| `BROADCAST_ERROR` | `broadcastError()` on exception | Receiver → `trySend(Failed)`; closes flow | `Failed(error, modelType)` |

**Critical finding:** These broadcast constants and actions are referenced **only** in the four files listed above. No other component in the codebase listens to them. Safe to remove entirely.

### Reference Pattern: ChatInferenceService

`ChatInferenceService` (`feature/chat-inference-service/.../service/ChatInferenceService.kt:69-79`) already uses the target pattern:

```kotlin
internal val _stateFlow = MutableSharedFlow<Pair<ChatId, MessageGenerationState>>(extraBufferCapacity = 64)

fun observeState(chatId: ChatId): Flow<MessageGenerationState> {
    return _stateFlow.asSharedFlow()
        .filter { it.first == chatId }
        .map { it.second }
}
```

The MOA pipeline will follow this exactly, using `String` (chatId) instead of `ChatId`.

---

## Implementation Plan

### Phase 1: Refactor InferenceEventBus to carry domain state

- [ ] **1.1** Change `InferenceEventBus` internal flow type from `Intent` to `Pair<String, MessageGenerationState>` (`String` = chatId).
- [ ] **1.2** Set `extraBufferCapacity = 64` to match `ChatInferenceService` and provide backpressure resilience for token streaming.
- [ ] **1.3** Add `observe(chatId: String): Flow<MessageGenerationState>` method that filters the shared flow by chatId and maps to the state, mirroring `ChatInferenceService.observeState()`.
- [ ] **1.4** Update `emit()` and `tryEmit()` signatures to accept `(chatId: String, state: MessageGenerationState)`.
- [ ] **1.5** Remove the now-unused `android.content.Intent` import.

**Rationale:** The event bus is already a `@Singleton` with `@Inject constructor()`, so Hilt wiring requires zero changes. By making it carry typed domain state instead of opaque `Intent` objects, we eliminate serialization/deserialization overhead and type-unsafe string extra access.

### Phase 2: Refactor InferenceService to emit via InferenceEventBus

- [ ] **2.1** Inject `InferenceEventBus` into `InferenceService` alongside existing dependencies.
- [ ] **2.2** Remove **all** broadcast-related companion constants (`BROADCAST_PROGRESS`, `BROADCAST_ERROR`, `BROADCAST_STEP_COMPLETED`, `BROADCAST_STEP_STARTED`, and all `EXTRA_*` keys used only for broadcasts).
- [ ] **2.3** Remove the four broadcast helper methods: `broadcastProgress()`, `broadcastStepCompleted()`, `broadcastStepStarted()`, `broadcastError()`.
- [ ] **2.4** In `executePipeline()`, replace `broadcastStepStarted(modelType)` with `inferenceEventBus.emit(chatId, MessageGenerationState.Processing(modelType))`.
- [ ] **2.5** In `executeStepForPipeline()`, replace `broadcastProgress(EXTRA_THINKING_CHUNK, ...)` with `inferenceEventBus.emit(chatId, MessageGenerationState.ThinkingLive(event.chunk, modelType))`.
- [ ] **2.6** In `executeStepForPipeline()`, replace `broadcastProgress(EXTRA_STEP_OUTPUT, ...)` with `inferenceEventBus.emit(chatId, MessageGenerationState.GeneratingText(event.chunk, modelType))`.
- [ ] **2.7** In `executePipeline()`, replace `broadcastStepCompleted(...)` with `inferenceEventBus.emit(chatId, MessageGenerationState.StepCompleted(stepOutput = "", modelDisplayName = ..., modelType = ..., stepType = ...))`. Preserve `stepOutput = ""` to match current behavior (step outputs are accumulated in `PipelineState`, not broadcast).
- [ ] **2.8** In `executePipeline()` catch blocks, replace `broadcastError(...)` / `broadcastProgress(EXTRA_THINKING_STEP, "Cancelled")` with typed `MessageGenerationState.Failed(...)` or `MessageGenerationState.ThinkingLive("Cancelled", ...)` emissions via the event bus. Use `ModelType.MAIN` as the default model type for outer-level errors to preserve existing fallback behavior.
- [ ] **2.9** Verify `InferenceService` still imports only domain types it needs; remove any now-unused imports (e.g., `android.content.Intent` if no longer needed for broadcasts).

**Rationale:** This is the core producer-side change. The service is already running inside a coroutine scope, so emitting to a `MutableSharedFlow` is a direct suspend call with no IPC overhead. Removing broadcast constants reduces the public API surface of the service.

### Phase 3: Refactor InferenceServicePipelineExecutor to collect from SharedFlow

- [ ] **3.1** Inject `InferenceEventBus` into `InferenceServicePipelineExecutor` constructor.
- [ ] **3.2** Remove all `BroadcastReceiver`, `IntentFilter`, `callbackFlow`, and `awaitClose` usage from `executePipeline()`.
- [ ] **3.3** Rewrite `executePipeline()` to return a cold `flow { }` that:
  1. Creates initial `PipelineState` and calls `serviceStarter.startService(chatId, userMessage, stateJson)`.
  2. Collects from `inferenceEventBus.observe(chatId)`.
  3. Emits each state downstream.
  4. Returns from `collect` (completing the flow) when `MessageGenerationState.StepCompleted` with `stepType == PipelineStep.FINAL` is seen, or when `MessageGenerationState.Failed` is seen.
- [ ] **3.4** Remove all `BroadcastReceiver`, `IntentFilter`, `callbackFlow`, and `awaitClose` usage from `resumeFromState()`.
- [ ] **3.5** Rewrite `resumeFromState()` to return a cold `flow { }` that:
  1. Fetches saved state from `pipelineStateRepository`; emits `Failed` and returns early if null.
  2. Calls `serviceStarter.startServiceResume(chatId, savedState.toJson())`.
  3. Collects from `inferenceEventBus.observe(chatId)` with the same terminal-state logic as `executePipeline()`.
- [ ] **3.6** In `stopPipeline()`, replace the `context.sendBroadcast(stopIntent)` call with `serviceStarter.stopService()`. The `InferenceServiceStarter` already has this method; it sends an `ACTION_STOP` intent via `startService()`, which is the correct way to signal a foreground service.
- [ ] **3.7** Remove now-unused imports: `BroadcastReceiver`, `Context`, `Intent`, `IntentFilter`, `callbackFlow`, `awaitClose`.
- [ ] **3.8** Remove the private helper methods `handleProgressIntent()` and `handleStepCompletedIntent()` as they are no longer needed (the service now emits typed states directly).

**Rationale:** The executor shrinks dramatically. The `callbackFlow` + `BroadcastReceiver` pattern (≈180 lines of receiver registration, intent parsing, and manual `trySend`/`close` logic) collapses to a simple `flow { collect { emit } }` with terminal-state detection. This eliminates the risk of receiver leaks and removes all string-based intent extra parsing.

### Phase 4: Rewrite unit tests for SharedFlow behavior

- [ ] **4.1** Rewrite `InferenceServicePipelineExecutorTest` to instantiate a real `InferenceEventBus` instead of mocking `BroadcastReceiver` registration.
- [ ] **4.2** Update `executePipeline forwards thinking chunks with newlines and spaces` test:
  - Call `executor.executePipeline("chat1", "Hello")` and collect states.
  - Emit `MessageGenerationState.ThinkingLive` events directly to `InferenceEventBus`.
  - Assert the collector receives them verbatim (newlines and spaces preserved).
  - Emit a terminal `StepCompleted(FINAL)` to complete the flow.
- [ ] **4.3** Update `executePipeline forwards step output chunks with newlines and spaces` test:
  - Same pattern as 4.2 but with `MessageGenerationState.GeneratingText` events.
- [ ] **4.4** Update `resumeFromState forwards thinking chunks with newlines and spaces` test:
  - Mock `pipelineStateRepository.getPipelineState()` to return a state.
  - Call `executor.resumeFromState()` and collect.
  - Emit states via `InferenceEventBus` and assert collection.
- [ ] **4.5** Update `executePipeline handles broadcast error and forwards it successfully` test:
  - Emit `MessageGenerationState.Failed` via the event bus.
  - Assert the collector receives the `Failed` state and the flow completes.
- [ ] **4.6** Add **chatId filtering test**:
  - Start collecting for `chatId = "123"`.
  - Emit events for `chatId = "456"` to the event bus.
  - Assert the collector for `"123"` receives **none** of them.
  - Emit terminal event for `"123"` to close the test flow.
- [ ] **4.7** Add **terminal state completion test**:
  - Collect from `executePipeline()`.
  - Emit `Processing`, `GeneratingText`, then `StepCompleted(FINAL)`.
  - Assert the flow completes (collector `join()` returns) after the FINAL step.
- [ ] **4.8** Ensure all mocks for `serviceStarter`, `pipelineStateRepository`, and `loggingPort` remain correctly configured. Remove all `BroadcastReceiver`, `Intent`, and `IntentFilter` mocks.

**Rationale:** The existing tests are tightly coupled to `BroadcastReceiver` internals (capturing receiver slots, mocking `Intent` objects, verifying `registerReceiver` calls). These are implementation details that no longer exist. The new tests verify the actual contract: events go into the bus, the executor observes by chatId, and states flow through to the collector.

### Phase 5: Validation and cleanup

- [ ] **5.1** Run `./gradlew :feature:moa-pipeline-worker:assemble` to verify the module compiles.
- [ ] **5.2** Run `./gradlew :feature:moa-pipeline-worker:test` to verify all unit tests pass.
- [ ] **5.3** Run `./gradlew :app:assembleDebug` to verify app-level compilation with the modified module.
- [ ] **5.4** Run `./gradlew ktlintCheck` to ensure style compliance.
- [ ] **5.5** Search the codebase for any remaining references to the removed broadcast constants (`BROADCAST_PROGRESS`, `BROADCAST_ERROR`, `BROADCAST_STEP_COMPLETED`, `BROADCAST_STEP_STARTED`) to ensure complete removal.
- [ ] **5.6** Verify `InferenceServiceStarter.kt` javadoc comment on `createStopIntent()` no longer mentions `sendBroadcast()` (update if needed).

---

## Verification Criteria

- [ ] `InferenceService` contains zero `sendBroadcast()` calls and zero broadcast action constants.
- [ ] `InferenceServicePipelineExecutor` contains zero `BroadcastReceiver`, `IntentFilter`, or `callbackFlow` usage.
- [ ] `InferenceEventBus` carries `Pair<String, MessageGenerationState>` and provides `observe(chatId)` filtering.
- [ ] All existing `InferenceServicePipelineExecutorTest` scenarios pass with the new SharedFlow-based implementation.
- [ ] `./gradlew :feature:moa-pipeline-worker:assemble` passes with no errors.
- [ ] `./gradlew :feature:moa-pipeline-worker:test` passes with no failures.
- [ ] `./gradlew :app:assembleDebug` passes with no errors.
- [ ] `./gradlew ktlintCheck` passes with no style violations.
- [ ] A grep for `BROADCAST_PROGRESS\|BROADCAST_ERROR\|BROADCAST_STEP_COMPLETED\|BROADCAST_STEP_STARTED` across the entire repo returns zero matches outside of git history.

---

## Potential Risks and Mitigations

1. **Flow completion semantics change**
   - **Risk:** `SharedFlow` never completes on its own. If the executor does not explicitly terminate collection after `StepCompleted(FINAL)` or `Failed`, the consumer (`GenerateChatResponseUseCase`) will hang in `emitAll()` and never reach the `finally` persistence block.
   - **Mitigation:** The executor's `flow { }` builder must `return@collect` after emitting the terminal state. This is explicitly covered in the implementation plan (Phase 3.3, 3.5) and verified by the terminal state completion test (Phase 4.7).

2. **ChatId leak across concurrent/sequential pipelines**
   - **Risk:** If two pipelines run for the same `chatId` (e.g., rapid re-submission), both collectors would receive events from the shared bus.
   - **Mitigation:** This is the same behavior as broadcasts (both receivers would receive). The `InferenceService` already cancels `currentJob` before starting a new pipeline, so only one pipeline runs at a time. The old flow naturally stops receiving events and will be cancelled by the ViewModel/coroutine scope. The new chatId-filtered observation is no worse than the old broadcast model.

3. **Process death during pipeline**
   - **Risk:** The user explicitly stated this is acceptable: "If the process dies it's fine for the whole pipeline to just stop and not resume." `SharedFlow` is in-memory and dies with the process, which is the desired behavior.
   - **Mitigation:** No action needed. This is a feature, not a bug. The `PipelineStateRepository` may still have saved state on disk, but the UI would need to trigger a fresh `executePipeline()` call rather than auto-resuming.

4. **Test fragility from mocking Intent internals**
   - **Risk:** The existing tests mock `Intent` objects extensively. A lazy refactor might leave both BroadcastReceiver and SharedFlow logic active, causing duplicate emissions or resource leaks.
   - **Mitigation:** The test rewrite (Phase 4) uses a real `InferenceEventBus` and verifies that no `BroadcastReceiver` path remains active. The mutation defense test scenario from the existing test spec is satisfied by the structural impossibility of receiving broadcast intents once the receiver code is deleted.

5. **Hilt injection cycle or missing binding**
   - **Risk:** `InferenceEventBus` is already a `@Singleton` with `@Inject constructor()`, but adding it to `InferenceService` (an `@AndroidEntryPoint`) and `InferenceServicePipelineExecutor` (a `@Singleton`) could theoretically cause issues if the class signature changes in a way Hilt dislikes.
   - **Mitigation:** The constructor stays `@Inject constructor()` with no new dependencies. Only the internal generic type of the `MutableSharedFlow` changes. Hilt does not inspect generic type parameters at compile time for injection binding. The module compiles as-is.

---

## Alternative Approaches Considered

1. **Keep BroadcastReceiver, add SharedFlow alongside it**
   - **Rejected:** This would not solve the performance problem and would create duplicate emission paths. The mutation defense test in the existing test spec explicitly guards against this lazy implementation.

2. **Use `Channel` instead of `SharedFlow`**
   - **Rejected:** A `Channel` is a cold stream with a single consumer. If the executor is cancelled and restarted, the old channel would be orphaned. `SharedFlow` is the correct hot-stream abstraction for multiple potential consumers and matches the `ChatInferenceService` precedent.

3. **Use `StateFlow` instead of `SharedFlow`**
   - **Rejected:** `StateFlow` only keeps the latest value. High-frequency token chunks would be dropped. `SharedFlow` with `extraBufferCapacity = 64` preserves every chunk in order.

4. **Move event bus to `:core:domain` module**
   - **Rejected:** The event bus is an implementation detail of the MOA pipeline feature module. It is not needed by the domain layer (which speaks in terms of `Flow<MessageGenerationState>` via `PipelineExecutorPort`). Keeping it in `:feature:moa-pipeline-worker` preserves clean architecture boundaries.

---

## Assumptions

- The broadcast actions (`BROADCAST_PROGRESS`, `BROADCAST_ERROR`, `BROADCAST_STEP_COMPLETED`, `BROADCAST_STEP_STARTED`) are used **only** for internal communication between `InferenceService` and `InferenceServicePipelineExecutor`. No external apps, widgets, or other modules listen to them. The codebase search confirms this.
- `InferenceEventBus` is currently unused in production code, so refactoring its generic type is a safe breaking change with no downstream consumers to update.
- `stepOutput` in `MessageGenerationState.StepCompleted` is intentionally empty string in the current implementation (the actual step outputs are stored in `PipelineState.stepOutputs`). This behavior is preserved.
- `ModelType.MAIN` is the appropriate fallback model type for error states that occur outside of a specific step context, matching the current default in `InferenceServicePipelineExecutor`.
- The `InferenceServiceStarter.stopService()` method is the correct replacement for `context.sendBroadcast(stopIntent)` in `stopPipeline()`, because `InferenceService` handles `ACTION_STOP` via `onStartCommand()`, not via a broadcast receiver.
