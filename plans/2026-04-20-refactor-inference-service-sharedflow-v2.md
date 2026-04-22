# Refactor InferenceService from BroadcastReceiver to InferenceEventBus SharedFlow

## Objective

Replace the `BroadcastReceiver`/`sendBroadcast()` mechanism in the MOA (`Crew`) `InferenceService` with the **same** `InferenceEventBus` `@Singleton` pattern already proven by `ChatInferenceServiceExecutor` / `ChatInferenceService`. This eliminates Intent serialization, IPC overhead, and string-based parsing for every LLM token chunk in the multi-step pipeline.

The refactor **does not create a new event bus** — it extends the existing `InferenceEventBus` with pipeline-specific methods and wires `InferenceService` (producer) and `InferenceServicePipelineExecutor` (consumer) to use it.

---

## Assumptions

1. `MessageGenerationState` is the correct state type for both chat and pipeline events.
2. The MOA pipeline has no `assistantMessageId` during execution, so pipeline streams are keyed by `chatId: String` only.
3. Process death during pipeline execution is acceptable — the pipeline does not need to resume its stream; `PipelineStateRepository` already handles durable progress separately.
4. The existing broadcast actions (`BROADCAST_PROGRESS`, `BROADCAST_STEP_COMPLETED`, `BROADCAST_STEP_STARTED`, `BROADCAST_ERROR`) and all their `EXTRA_*` constants are internal to the `feature:moa-pipeline-worker` module and safe to remove entirely.

---

## Implementation Plan

### Phase 1: Extend `InferenceEventBus` for pipeline streams

- [ ] Add a `pipelineStreams` map (`ConcurrentHashMap<String, MutableSharedFlow<MessageGenerationState>>`) keyed by `chatId: String`
- [ ] Add `openPipelineRequest(chatId: String): Flow<MessageGenerationState>` — creates/replaces a stream for the given chatId, returning it as `SharedFlow`
- [ ] Add `emitPipelineState(chatId: String, state: MessageGenerationState)` — emits a state to the stream for the given chatId
- [ ] Add `tryEmitPipelineState(chatId: String, state: MessageGenerationState): Boolean` — non-suspending emit variant for use from non-coroutine contexts if needed
- [ ] Add `clearPipelineRequest(chatId: String)` — removes the stream entry from the map
- [ ] Reuse the same `newChatStream()` configuration (`replay = 64`, `extraBufferCapacity = 1024`, `BufferOverflow.DROP_OLDEST`) for pipeline streams to maintain consistent backpressure behavior
- [ ] Keep `chatStreams` / `chatSnapshots` completely untouched — the chat path is already working and verified

**Rationale:** Uses the exact same per-key stream isolation pattern as `openChatRequest()` / `emitChatState()`, but with a simpler `String` key since the pipeline does not have an `assistantMessageId` during execution.

### Phase 2: Refactor `InferenceService` (producer)

- [ ] Inject `InferenceEventBus` into `InferenceService` (add `@Inject lateinit var inferenceEventBus: InferenceEventBus`)
- [ ] Add a `private var currentChatId: String? = null` field for keying event bus emissions
- [ ] Store `chatId` in `currentChatId` at the start of `handleStartCommand()` and `handleResumeCommand()`
- [ ] Replace `broadcastProgress()` with direct `inferenceEventBus.emitPipelineState()` calls:
  - `Thinking` chunk → `emitPipelineState(chatId, MessageGenerationState.ThinkingLive(chunk, modelType))`
  - `PartialResponse` chunk → `emitPipelineState(chatId, MessageGenerationState.GeneratingText(chunk, modelType))`
- [ ] Replace `broadcastStepStarted(modelType)` with `emitPipelineState(chatId, MessageGenerationState.Processing(modelType))`
- [ ] Replace `broadcastStepCompleted(...)` with `emitPipelineState(chatId, MessageGenerationState.StepCompleted(...))`
- [ ] Replace `broadcastError(errorMessage)` with `emitPipelineState(chatId, MessageGenerationState.Failed(IllegalStateException(errorMessage), ModelType.MAIN))` (use the actual step's `modelType` if available)
- [ ] In `finally` blocks (after pipeline execution or on error), call `inferenceEventBus.clearPipelineRequest(chatId)` to clean up the stream
- [ ] Remove all broadcast constant declarations (`BROADCAST_PROGRESS`, `BROADCAST_ERROR`, `BROADCAST_STEP_COMPLETED`, `BROADCAST_STEP_STARTED`, and all `EXTRA_*` fields)
- [ ] Remove the four `broadcast*` methods entirely (`broadcastProgress`, `broadcastStepCompleted`, `broadcastStepStarted`, `broadcastError`)
- [ ] Remove unused imports (`IntentFilter` is no longer needed in the service)

**Rationale:** The service becomes a pure producer of typed `MessageGenerationState` events, matching `ChatInferenceService.publishState()` / `emitState()` exactly. No more Intent creation, no more string extras.

### Phase 3: Refactor `InferenceServicePipelineExecutor` (consumer)

- [ ] Inject `InferenceEventBus` into the constructor (add `private val inferenceEventBus: InferenceEventBus`)
- [ ] Remove the `BroadcastReceiver`, `IntentFilter`, `callbackFlow`, `awaitClose`, and `Context` dependency entirely
- [ ] Rewrite `executePipeline()` as a `flow { }` builder (matching `ChatInferenceServiceExecutor.execute()` exactly):
  - [ ] Open a stream: `val stateFlow = inferenceEventBus.openPipelineRequest(chatId)`
  - [ ] Call `serviceStarter.startService(chatId, userMessage, stateJson)` 
  - [ ] Use `emitAll(stateFlow.transformWhile { ... })` to collect states
  - [ ] Inside `transformWhile`, `emit(state)` and check `state.isTerminal()` (terminal = `Failed`, `Finished`, or a custom terminal state for the pipeline; see Phase 4)
  - [ ] On terminal state, log it and return `false` to stop the `transformWhile`, which completes the `emitAll`
  - [ ] Wrap `emitAll` in `try { ... } catch (e: Exception) { ... }` and re-emulate the error handling of `ChatInferenceServiceExecutor`
  - [ ] In `finally`, if a terminal state was seen, call `inferenceEventBus.clearPipelineRequest(chatId)`
- [ ] Rewrite `resumeFromState()` with the exact same `flow { }` + `emitAll(transformWhile)` pattern, opening the stream before calling `serviceStarter.startServiceResume()`
- [ ] Remove `handleProgressIntent()` and `handleStepCompletedIntent()` — they are no longer needed since the service emits typed `MessageGenerationState` directly
- [ ] In `stopPipeline()`, replace `context.sendBroadcast(stopIntent)` with `serviceStarter.stopService()` (the `InferenceServiceStarter` already has this method, matching how `ChatInferenceServiceExecutor.stop()` works)
- [ ] Remove the `@param:ApplicationContext private val context: Context` constructor parameter

**Rationale:** The executor follows the exact proven pattern from `ChatInferenceServiceExecutor` (lines 47–111). `callbackFlow` with `BroadcastReceiver` is replaced by `flow { emitAll(...) }` collecting from a `SharedFlow`. `transformWhile` handles both emission and terminal completion. The `finally` block cleans up the stream entry.

### Phase 4: Add pipeline-specific terminal state helper

- [ ] Define a private extension `MessageGenerationState.isTerminal()` inside `InferenceServicePipelineExecutor` (or a shared utilities object) that returns `true` for:
  - `MessageGenerationState.Failed`
  - `MessageGenerationState.Finished`
  - `MessageGenerationState.StepCompleted` where `stepType == PipelineStep.FINAL`
- [ ] Handle the edge case: `StepCompleted` with a non-final step is **not** terminal — the pipeline continues to the next step

**Rationale:** `SharedFlow` never completes on its own. `transformWhile` must return `false` only on a truly terminal state. The previous callbackFlow used `close()` after `FINAL` step; this achieves the same by ending the `emitAll`. `ChatInferenceServiceExecutor` uses `isTerminal()` that checks `Finished`, `Failed`, `Blocked` — the pipeline adds the `FINAL` `StepCompleted` rule.

### Phase 5: Rewrite unit tests

The existing `InferenceServicePipelineExecutorTest` is entirely built around `BroadcastReceiver` mocking. It must be rewritten to use the `InferenceEventBus` directly.

- [ ] Create a real `InferenceEventBus` instance in tests (it's a plain `@Inject` constructor with no Android dependencies, so `InferenceEventBus()` works in JVM tests)
- [ ] Replace `BroadcastReceiver` mocking with emitting states directly into the event bus
- [ ] Rewrite `executePipeline forwards thinking chunks with newlines and spaces`:
  - Launch collection from `executor.executePipeline(...)`
  - Emit `MessageGenerationState.Processing(ModelType.MAIN)` then `ThinkingLive("\n", ModelType.MAIN)`, `ThinkingLive("   ", ModelType.MAIN)` via `inferenceEventBus.emitPipelineState(...)`
  - Emit `StepCompleted(..., stepType = PipelineStep.FINAL)` to close the flow
  - Assert collected events match expectations
- [ ] Rewrite `executePipeline forwards step output chunks with newlines and spaces`:
  - Emit `GeneratingText("\n", ...)`, `GeneratingText("   ", ...)`
  - Assert collected events
- [ ] Rewrite `resumeFromState forwards thinking chunks`:
  - Same pattern but call `executor.resumeFromState(...)`
  - Mock `pipelineStateRepository.getPipelineState(...)` to return a state
  - Emit via event bus and assert
- [ ] Rewrite `executePipeline handles broadcast error and forwards it successfully` → rename to `executePipeline handles error state and forwards it successfully`:
  - Emit `MessageGenerationState.Failed(...)`
  - Assert flow terminates with the error state
- [ ] Add test `executePipeline terminal state after FINAL StepCompleted closes flow`:
  - Emit `StepCompleted` with `stepType = PipelineStep.FINAL`
  - Assert flow completes without emitting additional states afterward
- [ ] Add test `emissions for different chatIds are isolated`:
  - Open two pipeline streams for two different chatIds
  - Emit to each
  - Assert each collector only sees its own events
- [ ] Remove all `mockk` imports for `BroadcastReceiver`, `Intent`, `IntentFilter`, `Context`
- [ ] Ensure `InferenceServiceStarter` is still mocked (`startService`, `startServiceResume`, `stopService` are all mockable)

**Rationale:** Tests become simpler, more deterministic, and do not require Android framework mocking. The event bus is pure Kotlin coroutines, so tests run on JVM without `isReturnDefaultValues` workarounds.

### Phase 6: Validation and cleanup

- [ ] Run `./gradlew :feature:moa-pipeline-worker:assemble` — verify no compilation errors
- [ ] Run `./gradlew :feature:moa-pipeline-worker:test` — verify all unit tests pass
- [ ] Run `./gradlew :app:assembleDebug` — verify full app compilation
- [ ] Run `./gradlew ktlintCheck` — verify formatting
- [ ] Search for any remaining references to removed broadcast constants (e.g., grep for `BROADCAST_PROGRESS`, `BROADCAST_ERROR`, `BROADCAST_STEP_COMPLETED`, `BROADCAST_STEP_STARTED`) — expect zero results
- [ ] Verify `InferenceService` no longer imports `android.content.IntentFilter`, `android.content.BroadcastReceiver`, or `LocalBroadcastManager`
- [ ] Verify `InferenceServicePipelineExecutor` no longer imports `BroadcastReceiver`, `IntentFilter`, `callbackFlow`, `awaitClose`

---

## Verification Criteria

1. **No broadcast code remains** in `InferenceService` or `InferenceServicePipelineExecutor`
2. **All 4 broadcast actions** and their `EXTRA_*` constants are deleted from `InferenceService`
3. **`InferenceService`** emits `MessageGenerationState` directly through `InferenceEventBus` using typed method calls (no string extras, no Intents)
4. **`InferenceServicePipelineExecutor`** collects from `InferenceEventBus.openPipelineRequest(chatId)` using `flow { emitAll(transformWhile { ... }) }`, identical pattern to `ChatInferenceServiceExecutor`
5. **Flow completion is guaranteed** on terminal state (`Failed`, `StepCompleted(FINAL)`) — the `emitAll` terminates and the downstream use case's `finally` block runs
6. **Stream cleanup** happens in both success and error paths via `clearPipelineRequest(chatId)`
7. **All existing unit tests pass** with the new event bus-based test strategy
8. **`InferenceEventBus` chat methods** are untouched — `ChatInferenceService` continues to function identically

---

## Potential Risks and Mitigations

### Risk 1: `SharedFlow` never completes if terminal state is missed
`SharedFlow` does not close on its own. If the service crashes before emitting a terminal state, the consumer's `emitAll` could hang forever.

**Mitigation:** The service's `finally` block (after `executePipeline()` or in error handlers) already calls `stopForeground` and `stopSelf`. Add `clearPipelineRequest(chatId)` there too. On the consumer side, the `flow { }` builder in `InferenceServicePipelineExecutor` naturally terminates if the outer coroutine scope is cancelled (e.g., user navigates away, ViewModel cleared). The upstream use case (`GenerateCrewResponseUseCase` equivalent) should also have a timeout or explicit cancellation on lifecycle events.

### Risk 2: Chat and pipeline event isolation
Using `String` keys for pipeline and `ChatRequestKey` for chat means no collision, but adding a new map in `InferenceEventBus` increases its surface area.

**Mitigation:** The `ConcurrentHashMap<String, ...>` for pipeline streams is entirely separate from `chatStreams` (keyed by `ChatRequestKey`). A `String` and a `ChatRequestKey` can never collide because they are different types in different maps.

### Risk 3: ARCHITECTURE_RULES.md deviation
`ARCHITECTURE_RULES.md` states "WorkManager `Data` persistence is the sole bridge for background-to-UI state" and "Use `WorkManager.setProgress`" rather than singleton/static listeners.

**Mitigation:** This is the same deviation already accepted for `ChatInferenceService` → `InferenceEventBus`. The rationale is documented: `SharedFlow` is correct for real-time LLM token streaming where process death is acceptable. WorkManager's `Data` / `ProgressStyle` is designed for coarse-grained durable progress, not hundreds of sub-second chunk emissions. This plan is consistent with the established and verified pattern.

### Risk 4: `InferenceService` now depends on `feature:inference`
`InferenceEventBus` lives in `:feature:inference`. The MOA module already depends on `:feature:inference` (line 49 of `feature/moa-pipeline-worker/build.gradle.kts`), so this is safe.

### Risk 5: Process death during pipeline execution
If the service process dies mid-pipeline, the `SharedFlow` dies with it. The UI would stop receiving updates.

**Mitigation:** **Acceptable and intended.** The requirement explicitly states: "If the process dies it's fine for the whole pipeline to just stop and not resume." Durable pipeline progress is handled by `PipelineStateRepository` (persistence to Room), and the UI can detect the stale state separately if recovery is desired later.

---

## Alternative Approaches (Rejected)

1. **New separate event bus for pipeline only**
   Rejected: Unnecessary duplication. The existing `InferenceEventBus` already handles per-key stream isolation and cleanup. Adding a second bus would create two nearly identical singletons to maintain.

2. **Reuse `ChatRequestKey` with a dummy `MessageId`**
   Rejected: The pipeline does not have an `assistantMessageId` during execution. Fabricating one for the key would be confusing and semantically wrong. A simpler `String` key for pipeline streams is cleaner.

3. **Keep broadcasts alongside SharedFlow during migration**
   Rejected: The user explicitly wants to replace broadcasts. Maintaining both paths increases complexity, risks desynchronization, and defeats the performance goal.

---

## Files to Modify

| File | Changes |
|------|---------|
| `feature/inference/.../InferenceEventBus.kt` | Add pipeline stream map + methods |
| `feature/moa-pipeline-worker/.../InferenceService.kt` | Emit via event bus, remove all broadcast code |
| `feature/moa-pipeline-worker/.../InferenceServicePipelineExecutor.kt` | Collect via event bus, remove BroadcastReceiver |
| `feature/moa-pipeline-worker/.../InferenceServicePipelineExecutorTest.kt` | Rewrite for event bus, remove BroadcastReceiver mocks |

## Files to Delete (code only, not files)

- `InferenceService`: All `BROADCAST_*` constants, all `EXTRA_*` broadcast constants, all `broadcast*` methods
- `InferenceServicePipelineExecutor`: `BroadcastReceiver` anonymous class, `IntentFilter` setup, `handleProgressIntent()`, `handleStepCompletedIntent()`, `callbackFlow`/`awaitClose` usage
