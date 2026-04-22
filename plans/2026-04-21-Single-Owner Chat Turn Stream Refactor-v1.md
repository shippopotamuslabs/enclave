# Single-Owner Chat Turn Stream Refactor — Implementation Plan

## Invariants

- **Single visible accumulator:** For one assistant message id, exactly one component may accumulate inference events into visible text.
- **ViewModel observes, never publishes:** `ChatViewModel` may observe active turn snapshots, project them into `uiState`, and acknowledge handoff, but it must not publish assistant content snapshots.
- **Stable message identity:** The assistant message id created at turn start must stay stable from placeholder through live stream through completed Room row.
- **No disappearing handoff:** The active assistant message must never be removed from `uiState` before the completed Room message for the same id is already present in an emitted state.
- **Room terminal writes are atomic:** Final content, thinking data, model metadata, sources, and `MessageState.COMPLETE` must become visible as one Room transaction.
- **Live stream drives typewriter UI:** Room is not the high-frequency token transport. Live snapshots drive streaming; Room drives durable history and recovery.
- **Reattach must work:** Navigating away and returning while inference is active must reattach to the latest live snapshot for the same active turn.
- **Complete Room row wins in place:** Once Room emits the same assistant id as `COMPLETE`, the UI switches to Room data without changing the LazyList key or temporarily dropping the row.
- **Late stale snapshots cannot regress text:** Defensive stale guards must prevent older nonterminal snapshots from replacing newer visible content.

## Objective

Centralize live inference snapshot ownership behind a domain port, eliminate competing `_inFlightMessages` accumulation from `ChatViewModel`, and guarantee flicker-free handoff from live snapshot to durable Room data while preserving reattach after ViewModel recreation.

## Architecture Overview

```text
Before:
  ChatViewModel._inFlightMessages <- direct flow collection
  ChatViewModel._inFlightMessages <- background InferenceEventBus.observeChatSnapshot()
  Both write to the same MutableStateFlow and race with Room emissions.

After:
  Direct inference:
    GenerateChatResponseUseCase publishes live snapshots through ActiveChatTurnSnapshotPort
    only when backgroundInferenceEnabled == false.

  Background inference:
    ChatInferenceService publishes live snapshots through ActiveChatTurnSnapshotPort.
    InferenceEventBus state events remain for ChatInferenceServiceExecutor.

  ChatViewModel:
    observes ActiveChatTurnSnapshotPort only.
    projects Room messages plus active snapshot into uiState.
    acknowledges handoff only after uiState has emitted the COMPLETE Room row.
```

## Phase 1: Add Domain Port

- Create `ActiveChatTurnSnapshotPort` in `:core:domain`, using the project's `Port` naming convention.
- Define a domain key type, for example `ActiveChatTurnKey(chatId: ChatId, assistantMessageId: MessageId)`.
- Port methods:
  - `fun observe(key: ActiveChatTurnKey): Flow<AccumulatedMessages?>`
  - `suspend fun publish(key: ActiveChatTurnKey, snapshot: AccumulatedMessages)`
  - `suspend fun markSourcesExtracted(key: ActiveChatTurnKey, urls: List<String>)`
  - `suspend fun acknowledgeHandoff(key: ActiveChatTurnKey)`
  - `suspend fun clear(key: ActiveChatTurnKey)`
- Keep the port pure Kotlin. No Android, Hilt, Room, or feature types in `:core:domain`.

## Phase 2: Implement Store in `:feature:inference`

- Create `ActiveChatTurnStore` implementing `ActiveChatTurnSnapshotPort`.
- Store snapshots in a replayable per-key `MutableStateFlow<AccumulatedMessages?>`.
- Use mutex-protected read-modify-write per key; do not rely on a bare `ConcurrentHashMap` plus non-atomic flow updates.
- Defensive merge rules:
  - New message id: insert.
  - Same message id, nonterminal old and nonterminal new: reject shorter `content` or shorter `thinkingRaw`.
  - Terminal new snapshot: accept terminal state even if content is shorter for `Blocked` or `Failed`.
  - Preserve already-extracted Tavily source flags when merging source lists.
- `acknowledgeHandoff` and `clear` should set the flow value to `null` before removing or retiring the entry, so existing observers are notified.
- Cleanup policy:
  - Do not use a simple timeout after last publish for active nonterminal turns.
  - Schedule delayed cleanup only after terminal snapshot, explicit clear, or handoff acknowledgement.
  - If nonterminal abandoned cleanup is still desired, make it long, configurable, and tested with an injected dispatcher/clock.
- Add Hilt binding so `:feature:chat`, `:feature:chat-inference-service`, and domain use-case construction all receive the same singleton implementation.

## Phase 3: Publish from the Correct Owner

- Inject `ActiveChatTurnSnapshotPort` into `GenerateChatResponseUseCase`.
- In `GenerateChatResponseUseCase`, publish accumulated snapshots only for direct foreground inference:
  - `backgroundInferenceEnabled == false`
  - single-model modes and direct paths only
- Do not publish from `GenerateChatResponseUseCase` for background inference. The service owns that stream.
- Inject `ActiveChatTurnSnapshotPort` into `ChatInferenceService`.
- In `ChatInferenceService.publishState()`, publish snapshots to the port/store.
- Keep `InferenceEventBus.emitChatState()` unchanged for `ChatInferenceServiceExecutor`.
- Stop using `InferenceEventBus.emitChatSnapshot()` and `observeChatSnapshot()` for the chat UI path. Keep the methods only if another module still uses them after audit.

## Phase 4: Refactor `ChatViewModel`

- Inject `ActiveChatTurnSnapshotPort`.
- Remove `_inFlightMessages`, `inFlightCompletionFallbackJob`, `observeActiveBackgroundInference()`, `pruneInFlightMessagesAsync()`, and the top-level `pruneCompletedInFlightMessages()`.
- Keep `inferenceJob` for lifecycle/cancellation of the direct flow collection, but remove snapshot publishing from the ViewModel.
- Add a stable active-turn key flow that derives the active assistant message id from Room incomplete rows, but does not drop the active snapshot before handoff.
- Avoid mutating `currentTurnKey` inside a flow lambda as a hidden side effect. Carry the key alongside the active snapshot or expose a dedicated `StateFlow<ActiveChatTurnKey?>`.
- `uiState` should combine:
  - inputs/settings
  - inference lock state
  - Room messages
  - active turn snapshot plus its key
  - active tool banner
- Add a pure projection helper:
  - Inputs: `dbMessages`, optional active snapshot, resolved `chatId`, `MergeMessagesUseCase`.
  - Output: projected domain messages for UI mapping.
  - Rule: if Room has a `COMPLETE` row for the same message id, use Room in place.
- Handoff acknowledgement:
  - During `uiState` projection, detect when Room contains `COMPLETE` for an id present in the active snapshot.
  - Emit `uiState` with the complete Room message still present under the same id.
  - Only after that emission, launch acknowledgement to the port/store.
  - Do not derive `activeTurnSnapshotFlow` so it becomes `null` merely because the DB row completed before this emitted-state handoff occurs.
- `stopGeneration()` and `createNewChat()` should clear the active key through the port after cancelling active work.

## Phase 5: Tool Source Updates

- Replace `ChatViewModel.markSourceExtracted()` mutation of `_inFlightMessages` with `ActiveChatTurnSnapshotPort.markSourcesExtracted(...)`.
- If there is no active key, return early.
- The accumulator should still reconcile `ExtractedUrlTracker` on later reductions so eager UI updates and persisted source flags converge.

## Phase 6: Tests

- `ActiveChatTurnStoreTest`:
  - Late observer receives latest snapshot.
  - Shorter nonterminal snapshot does not replace longer current snapshot.
  - Terminal `Blocked` or `Failed` replacement is accepted even if shorter.
  - Equal-length snapshot with additional source metadata is accepted.
  - Extracted source flags are preserved across merges.
  - `acknowledgeHandoff` emits `null` and retires the entry.
  - Cleanup does not clear active nonterminal turns prematurely.
- `ChatViewModelTest`:
  - Reattach after ViewModel recreation shows latest active snapshot.
  - No-flicker handoff: every collected `uiState` contains the assistant id while active snapshot transitions to complete Room row.
  - Complete Room content wins in place after handoff.
  - Late stale snapshot cannot regress visible text.
  - `stopGeneration()` clears the active store entry and exits generating UI state.
  - Direct inference snapshots appear via the port/store without ViewModel publishing.
- `GenerateChatResponseUseCaseTest`:
  - Direct foreground path publishes accumulated snapshots through `ActiveChatTurnSnapshotPort`.
  - Background path does not publish snapshots from the use case.
  - Terminal state persists final data as before.
- `ChatInferenceService` or service executor tests:
  - Service publishes snapshots through `ActiveChatTurnSnapshotPort`.
  - `InferenceEventBus` state-event contract still works for `ChatInferenceServiceExecutor`.
- Data-layer atomic test:
  - Use in-memory Room, not only mocks.
  - Collect the observable chat flow, call `persistAllMessageData()`, and assert observers never see an intermediate final state where content and `MessageState.COMPLETE` are split across emissions.
  - Keep a repository-level mock test only as a secondary contract check.

## Phase 7: Validation

- Run targeted tests first:
  - `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :feature:inference:testDebugUnitTest --no-daemon --stacktrace`
  - `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :feature:chat:testDebugUnitTest --tests "*ChatViewModelTest*" --no-daemon --stacktrace`
  - `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :feature:chat-inference-service:testDebugUnitTest --no-daemon --stacktrace`
  - `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :core:domain:testDebugUnitTest --no-daemon --stacktrace`
  - `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :core:data:testDebugUnitTest --no-daemon --stacktrace`
- Run compile/build checks:
  - `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :core:domain:assemble --no-daemon --stacktrace`
  - `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :feature:inference:assemble --no-daemon --stacktrace`
  - `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :feature:chat-inference-service:assembleDebug --no-daemon --stacktrace`
  - `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :feature:chat:assembleDebug --no-daemon --stacktrace`
  - `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :app:assembleDebug --no-daemon --stacktrace`
  - `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew ktlintCheck --no-daemon --stacktrace`

## Risks And Mitigations

- **Risk: domain port adds abstraction.**
  - Mitigation: The port is justified because active turn snapshots cross domain use case, foreground service, ViewModel reattach, and cancellation boundaries.
- **Risk: active key disappears too early on Room completion.**
  - Mitigation: key/snapshot stays alive until `uiState` has emitted the complete Room row and then acknowledges handoff.
- **Risk: duplicate publishers return.**
  - Mitigation: enforce path-specific ownership: use case publishes direct only; service publishes background only; ViewModel never publishes.
- **Risk: stale guard hides valid terminal replacement.**
  - Mitigation: terminal states have explicit precedence; length guard applies only to nonterminal text regression.
- **Risk: memory leaks.**
  - Mitigation: clear on cancel, clear after handoff, and delayed cleanup only after terminal/retired states.

## File Changes

| File | Action | Reason |
|------|--------|--------|
| `core/domain/.../ActiveChatTurnSnapshotPort.kt` | Create | Domain boundary for live turn snapshots |
| `feature/inference/.../ActiveChatTurnStore.kt` | Create | Singleton replayable implementation |
| `feature/inference/.../ActiveChatTurnStoreTest.kt` | Create | Store semantics and cleanup tests |
| `feature/inference/.../InferenceModule.kt` | Create/update | Bind port to store |
| `core/domain/.../GenerateChatResponseUseCase.kt` | Modify | Publish direct snapshots via port only |
| `core/domain/.../GenerateChatResponseUseCaseTest.kt` | Modify | Direct/background publishing contract |
| `feature/chat-inference-service/.../ChatInferenceService.kt` | Modify | Publish background snapshots via port |
| `feature/chat/.../ChatMessageProjection.kt` | Create | Pure projection helper |
| `feature/chat/.../ChatViewModel.kt` | Modify | Observe store, remove local in-flight map |
| `feature/chat/.../ChatViewModelTest.kt` | Modify | Reattach, stale, no-flicker handoff tests |
| `core/data/.../ChatRepositoryImplTest.kt` or DAO integration test | Modify | Atomic terminal persistence verification |
