# Last-Chunk Cutoff Fix — Atomic Handoff

## Objective

Eliminate the visible text cutoff/rollback that occurs during the transition from streaming snapshot data to Room-persisted data by making the handoff atomic: the UI should see exactly one swap from "snapshot content" to "Room content" with zero intermediate frames where content is missing, shorter, or in a pre-COMPLETE state.

## Root-Cause Analysis

### The Race Condition

When an LLM response finishes generating, the following events race:

1. `GenerateChatResponseUseCase` or `ChatInferenceService` publishes a **terminal** (`COMPLETE`) snapshot with the full text to `ActiveChatTurnStore`.
2. The `combine` in `ChatViewModel.uiState` picks up this snapshot, `projectChatMessages` correctly prefers it over the DB's still-GENERATING row, and the UI displays full text.
3. Meanwhile, the `finally` block in `GenerateChatResponseUseCase` (or `persistenceSession.flush()` in the service path) persists the COMPLETE message to Room.
4. Room emits the COMPLETE message via `dbMessagesFlow`.
5. `acknowledgeHandoffIfNeeded` sees COMPLETE in the DB, launches a coroutine to `acknowledgeHandoff(key)`.
6. That async coroutine eventually runs: `retire(key)` sets the snapshot flow to `null`, removes the entry.
7. `activeTurnKeyFlow` `scan` observes `_acknowledgedTurnKeys` containing the key, transitions to `null`.
8. `flatMapLatest` unsubscribes from snapshot observation → `activeTurnSnapshotFlow` emits `(null, null)`.

**The problem:** Between step 5 (acknowledgeHandoff launches) and step 8+ (snapshot fully cleared), there can be `combine` emissions where:
- `activeSnapshot` is already `null` (retired)
- `dbMessages` still carries a **GENERATING** row with partial content (intermediate persist from the service path)

This produces a frame where `projectChatMessages` returns the partial GENERATING message instead of the full COMPLETE snapshot. The user sees text regress.

Even in the foreground path (no intermediate persists), there's a briefer window where the snapshot is `null` but the Room COMPLETE row hasn't been emitted yet — causing a single frame where the message disappears entirely.

### Secondary Issue: `StreamableMarkdownText` Early Return

`StreamableMarkdownText.kt:60` has `if (markdown.isEmpty()) return`. When `markdown` briefly becomes empty during the handoff gap, the composable exits early, preventing the snap-to-full-text logic (lines 68-69) from executing. This causes the `displayedText` state to freeze at its last streaming value until the next non-empty `markdown` arrives. This is a contributing symptom, not the root cause.

### User Constraint

The user explicitly wants to **keep the spinner guard** (`if (markdown.isEmpty()) return`) in `StreamableMarkdownText`. The fix must be in the data layer, not the UI layer.

## Implementation Plan

- [ ] **1. Replace async `acknowledgeHandoff` with synchronous snapshot retirement inside the `combine` block**

  Currently, `acknowledgeHandoffIfNeeded` (ChatViewModel.kt:430-458) detects that Room has COMPLETE data and then launches an async coroutine via `viewModelScope.launch` to call `activeChatTurnSnapshotPort.acknowledgeHandoff(key)`. This async launch creates the gap window.

  **Change the handoff to be synchronous** — instead of `acknowledgeHandoff` which launches `retire()`, directly set the snapshot flow value to `null` synchronously within the `combine` transformation, so that the same `combine` emission that projects the COMPLETE DB message ALSO projects a null snapshot. This ensures no frame exists where the snapshot is gone but the DB isn't yet COMPLETE.

  **Implementation approach:**
  - Remove `acknowledgeHandoffIfNeeded` from the `combine` lambda entirely.
  - Add a new `StateFlow<Set<ActiveChatTurnKey>>` called `_retiredTurnKeys` that accumulates keys for which Room has confirmed COMPLETE.
  - In the `combine` for `uiState`, after projecting messages, synchronously add any keys to `_retiredTurnKeys` where the DB has COMPLETE messages matching snapshot keys. Then filter `_acknowledgedTurnKeys` to only contain keys in `_retiredTurnKeys`.
  - Use `_retiredTurnKeys` in the `activeTurnKeyFlow` `scan` to suppress snapshots for keys that have been retired, so `flatMapLatest` returns `null` for those keys immediately — within the same combine frame.

  Actually, this is overcomplicating it. The simplest atomic approach:

  **Redefine the merge priority.** In `projectChatMessages`, when both a DB message and an active snapshot message exist for the same ID, and the DB message is COMPLETE, use the DB message. This already happens in `MergeMessagesUseCase`. The only issue is that the snapshot gets retired BEFORE the DB has emitted COMPLETE.

  **The real fix:** Make the snapshot stay alive until the SAME `combine` emission where the DB COMPLETE message is projected. This means the `acknowledgeHandoff` (which retires the snapshot) must be synchronous with the combine emission, not async.

- [ ] **1a. Refactor `acknowledgeHandoffIfNeeded` to retire the snapshot synchronously within the combine**

  In `ChatViewModel.kt`, inside the `combine` lambda (line 353-428):
  - Remove the call to `acknowledgeHandoffIfNeeded(messages, activeTurnSnapshotState)` (line 404).
  - Instead, after projecting messages and building the `ChatUiState`, check synchronously: for each key in `activeTurnSnapshotState` (if non-null), do any of its message IDs appear in `messages` with `MessageState.COMPLETE`? If so, add that key to `_acknowledgedTurnKeys` synchronously, and also synchronously retire the snapshot by calling `activeChatTurnSnapshotPort.acknowledgeHandoff(key)` — but this is a suspend function.

  The issue: `acknowledgeHandoff` is `suspend`. We can't call it synchronously inside `combine`. We need a different approach.

  **Better approach:** Instead of retiring the snapshot in `ActiveChatTurnStore`, control visibility purely through the `combine` and `activeTurnKeyFlow`:

  1. When the `combine` projection detects that a DB message is COMPLETE for a snapshot key, add the key to `_acknowledgedTurnKeys` synchronously (this is just `_acknowledgedTurnKeys.update { it + key }` — no suspension needed).
  2. The `activeTurnKeyFlow` `scan` will then see the key in `_acknowledgedTurnKeys` and transition to `null`.
  3. `flatMapLatest` will switch to `flowOf(null)`.
  4. The next `combine` emission will have `activeTurnSnapshotState = (null, null)`.

  But this is ALSO async from the combine's perspective — the `_acknowledgedTurnKeys.update` triggers a downstream flow emission, which arrives in the next frame.

  **The truly atomic approach:** Don't retire the snapshot at all via flow side-effects. Instead, make `projectChatMessages` handle the case where both sources have COMPLETE data by always preferring the DB source, and then let the snapshot naturally expire via `ActiveChatTurnStore.scheduleTerminalCleanup` (which has a 5-minute timeout).

  Wait — the current `scheduleTerminalCleanup` already handles this! Looking at `ActiveChatTurnStore.kt:105-120`, when a terminal snapshot is published, it schedules a cleanup after 5 minutes. So the snapshot would naturally go away. The problem is that `acknowledgeHandoffIfNeeded` is retiring it immediately and asynchronously.

- [ ] **1b. Remove the eager `acknowledgeHandoffIfNeeded` call and rely on `ActiveChatTurnStore`'s terminal cleanup**

  The simplest fix: Stop calling `acknowledgeHandoffIfNeeded` from the `combine` lambda entirely. Remove or comment out the call at line 404. The `ActiveChatTurnStore.scheduleTerminalCleanup` already retires snapshots after 5 minutes. The `MergeMessagesUseCase` already prefers COMPLETE DB messages over snapshots. So the handoff is inherently atomic:

  - During streaming: snapshot has real-time content, DB has GENERATING/partial → merge prefers snapshot ✓
  - When COMPLETE snapshot arrives: merge prefers snapshot (since DB is still GENERATING) ✓
  - When Room COMPLETE arrives: merge prefers DB (since DB is COMPLETE) ✓ — and snapshot is still alive but ignored
  - 5 minutes later: scheduleTerminalCleanup retires the snapshot — by which time the DB COMPLETE row is long since persistent

  This is the **simplest and most correct** fix. No race condition is possible because there's no async handoff. The merge logic naturally handles the transition.

  **But we still need to clear `_requestedTurnKey`** when the turn completes, otherwise the `activeTurnCandidateFlow` would keep pointing at the completed message. Let's trace this: `activeTurnCandidateFlow` at line 154-177 computes `incompleteKey` by finding messages with `messageState != COMPLETE`. Once Room emits COMPLETE, `incompleteKey` is null. And `requestedTurnKey` is already nulled when acknowledged. So `_requestedTurnKey` needs to be cleared.

  The current `acknowledgeHandoffIfNeeded` also does `_requestedTurnKey.update { null }` (line 452-454). We need to keep that behavior but without the snapshot retirement.

- [ ] **1c. Refine: Keep key cleanup but defer snapshot retirement to the store's terminal cleanup**

  Replace `acknowledgeHandoffIfNeeded` with a simpler synchronous method that:
  1. Adds the key to `_acknowledgedTurnKeys` (synchronous StateFlow update — this makes `activeTurnKeyFlow` transition to null on the next emission, which stops further snapshot observations)
  2. Clears `_requestedTurnKey` if it matches
  3. Does NOT call `activeChatTurnSnapshotPort.acknowledgeHandoff(key)` — letting the 5-minute terminal cleanup handle the actual snapshot retirement

  This means `activeTurnKeyFlow` transitions to `null` synchronously, which causes `flatMapLatest` to unsubscribe from the snapshot flow. The snapshot data stays in `ActiveChatTurnStore` but nobody is observing it. Meanwhile, `dbMessagesFlow` already has the COMPLETE row. The `combine` will see `activeTurnSnapshotState = (null, null)` and use the DB COMPLETE message. This is atomic because `projectChatMessages` prefers COMPLETE DB data.

  **Wait — there's still a subtle issue.** When `activeTurnKeyFlow` transitions to `null` via `_acknowledgedTurnKeys.update`, this triggers `flatMapLatest` to switch. But `_acknowledgedTurnKeys.update` happens in a coroutine (since we're inside `combine` which is already running on a StateFlow collector). The update to `_acknowledgedTurnKeys` causes `scan` to re-emit `null`, which causes `flatMapLatest` to emit `flowOf(null)`. These are all sequential within the flow pipeline — no frames of "snapshot exists but wrong data."

  Actually, the flow pipeline guarantees: the `combine` that includes `_acknowledgedTurnKeys` (via `activeTurnCandidateFlow` combining with `_acknowledgedTurnKeys`) will recompute, and `scan` will emit `null`, and `flatMapLatest` will switch, and the `combine` for `uiState` will see the new snapshot state — all within the same coroutine dispatch. But critically, `_acknowledgedTurnKeys` is one of the inputs to `activeTurnCandidateFlow`, which feeds into `activeTurnKeyFlow`, which feeds into `activeTurnSnapshotFlow`, which feeds into `uiState`. So updating it WILL cause a recomposition, but the recomposition will already have the DB COMPLETE data.

  **However**, there's a 1-frame window where `activeTurnKeyFlow` has just transitioned to `null` but the `combine` hasn't yet re-emitted with the new `activeTurnSnapshotFlow = null`. In that window, `projectChatMessages` could receive `activeSnapshot` from the old emission. But since `MergeMessagesUseCase` prefers COMPLETE DB messages, this is fine — the snapshot data is still valid (it's COMPLETE too).

  **The key insight:** The actual problem is NOT the snapshot staying alive. It's the snapshot being killed BEFORE the DB is ready. So if we stop killing the snapshot eagerly, and instead let the merge logic handle it, we win.

- [ ] **2. Simplify `acknowledgeHandoffIfNeeded` to only manage flow-level key lifecycle (no snapshot retirement)**

  Replace the implementation at `ChatViewModel.kt:430-458`:

  ```kotlin
  private fun acknowledgeHandoffIfNeeded(
      dbMessages: List<Message>,
      activeTurnSnapshotState: ActiveTurnSnapshotState,
  ) {
      val key = activeTurnSnapshotState.key ?: return
      val activeSnapshot = activeTurnSnapshotState.snapshot ?: return

      val completedDbIds = dbMessages.asSequence()
          .filter { it.messageState == MessageState.COMPLETE }
          .map { it.id }
          .toSet()

      if (completedDbIds.isEmpty()) return

      val shouldAcknowledge = activeSnapshot.messages.keys.any { id -> id in completedDbIds }
      if (!shouldAcknowledge) return

      // Add to acknowledged set synchronously — this causes activeTurnKeyFlow
      // to transition to null on the next emission, which unsubscribes from
      // the snapshot flow. The snapshot data remains in ActiveChatTurnStore
      // but is no longer observed. MergeMessagesUseCase prefers COMPLETE DB
      // messages, so the handoff is atomic: the combine frame that sees
      // COMPLETE in DB also keeps the snapshot alive (ignored by merge).
      // ActiveChatTurnStore.scheduleTerminalCleanup will retire the snapshot
      // after 5 minutes.
      _acknowledgedTurnKeys.update { keys -> keys + key }
      if (_requestedTurnKey.value == key) {
          _requestedTurnKey.update { null }
      }
      handoffAcknowledgementsInFlight.remove(key)
  }
  ```

  Key changes:
  - Removed the `handoffAcknowledgementsInFlight.add(key)` guard (no longer needed since we're not launching an async coroutine).
  - Removed the `viewModelScope.launch { activeChatTurnSnapshotPort.acknowledgeHandoff(key) }` call entirely. This is the source of the async gap.
  - Removed `_acknowledgedTurnKeys.update` from the async block — it's now synchronous.
  - The snapshot stays in `ActiveChatTurnStore` for up to 5 minutes (terminal cleanup), which is harmless since `MergeMessagesUseCase` prefers COMPLETE DB data.

- [ ] **3. Verify `MergeMessagesUseCase` handles the overlap correctly**

  The existing merge logic at `MergeMessagesUseCase.kt:45-52`:
  ```kotlin
  return if (dbMessage.messageState == MessageState.COMPLETE) {
      dbMessage  // DB has the final persisted state, use it
  } else {
      inFlightMessage.copy(createdAt = dbMessage.createdAt)
      // DB is still processing, use in-flight which has real content
  }
  ```

  This is exactly correct. When both the snapshot and DB COMPLETE exist for the same message ID, it returns `dbMessage`. Since the DB row now has full content (the COMPLETE persist writes full content), this is the complete final message. No change needed here.

- [ ] **4. Verify `projectChatMessages` handles null snapshot correctly**

  `projectChatMessages` at `ChatMessageProjection.kt:12-32`:
  - When `activeSnapshot` is null (after acknowledgement), `activeMessagesMap` is empty
  - The merge only processes `dbMessagesMap`, which includes the COMPLETE message
  - Result: UI shows the COMPLETE DB message

  When `activeSnapshot` is non-null (snapshot still alive after DB COMPLETE):
  - `mergeMessagesUseCase(dbMessage, activeMessage)` returns `dbMessage` because `dbMessage.messageState == COMPLETE`
  - Result: UI shows the COMPLETE DB message (same as above)

  **Both paths produce the same correct result.** The handoff is seamless.

- [ ] **5. Handle `activeTurnKeyFlow` scan logic to avoid premature null**

  Review `activeTurnKeyFlow` at lines 179-201:

  ```kotlin
  private val activeTurnKeyFlow = combine(
      activeTurnCandidateFlow,
      _acknowledgedTurnKeys,
  ) { candidate, acknowledgedKeys ->
      candidate to acknowledgedKeys
  }.scan(null as ActiveChatTurnKey?) { currentKey, (candidate, acknowledgedKeys) ->
      val candidateKey = candidate.key?.takeUnless { key -> key in acknowledgedKeys }
      when {
          currentKey != null && currentKey in acknowledgedKeys -> null
          candidateKey != null -> candidateKey
          currentKey != null && candidate.chatId == currentKey.chatId &&
              candidate.messages.any { message ->
                  message.id == currentKey.assistantMessageId &&
                      message.messageState == MessageState.COMPLETE
              } -> currentKey
          else -> null
      }
  }.distinctUntilChanged()
  ```

  When `_acknowledgedTurnKeys` is updated synchronously with the key (step 2), the `combine` re-fires. The `scan` sees `currentKey` in `acknowledgedKeys` and transitions to `null`. This causes `flatMapLatest` to switch to `flowOf(ActiveTurnSnapshotState(key = null, snapshot = null))`.

  But this transition happens on the **next** `combine` emission after `_acknowledgedTurnKeys` is updated. Since `_acknowledgedTurnKeys.update` is synchronous and `combine` uses `distinctUntilChanged`, the next emission will have the new acknowledged set. However, at the same time, the DB COMPLETE message is already in `dbMessagesFlow`. So the **same** combine emission that sees `_acknowledgedTurnKeys` containing the key ALSO sees the COMPLETE DB message in `dbMessages`.

  This means: `projectChatMessages` receives `activeSnapshot = (null, null)` + `dbMessages` with COMPLETE → returns only COMPLETE DB messages. ✓ No gap.

  However, there's a subtle timing issue. The `_acknowledgedTurnKeys.update` inside `acknowledgeHandoffIfNeeded` (which runs inside the `combine` lambda for `uiState`) triggers a downstream recomputation of `activeTurnKeyFlow` → `activeTurnSnapshotFlow`. This recomputation happens asynchronously (on the next dispatch). So there's still a one-frame window where:
  - `uiState` combine has completed with snapshot alive + DB COMPLETE
  - `_acknowledgedTurnKeys` has been updated
  - But `activeTurnKeyFlow` hasn't yet recomputed to `null`
  - `activeTurnSnapshotFlow` still has the old snapshot

  This window is **safe** because `MergeMessagesUseCase` prefers COMPLETE DB data. Both sources have COMPLETE data, and the merge picks DB. **Atomic from the user's perspective.**

- [ ] **6. Write/update unit tests for `acknowledgeHandoffIfNeeded` and `projectChatMessages`**

  - Test that `acknowledgeHandoffIfNeeded` no longer calls `activeChatTurnSnapshotPort.acknowledgeHandoff()` (verify it's not called/removed from the method).
  - Test that when both snapshot and DB have COMPLETE data, `projectChatMessages` returns the DB version.
  - Test that when snapshot is COMPLETE but DB is GENERATING, `projectChatMessages` returns the snapshot version.
  - Test that `_acknowledgedTurnKeys` is updated synchronously when DB COMPLETE is detected.
  - Test that the snapshot data remains accessible (not retired) after handoff acknowledgement.

- [ ] **7. Handle the `handoffAcknowledgementsInFlight` set**

  Since we removed the async coroutine launch, `handoffAcknowledgementsInFlight` is no longer needed for deduplication. The `_acknowledgedTurnKeys` StateFlow already prevents re-acknowledgement (the `scan` in `activeTurnKeyFlow` filters out acknowledged keys). Remove the `handoffAcknowledgementsInFlight` field and its usages.

- [ ] **8. Handle `stopGeneration()` cleanup path**

  `stopGeneration()` at line 602-614 calls `activeChatTurnSnapshotPort.clear(key)`, which sets the snapshot to `null` and removes the entry. This is correct for user-initiated stops where the message is incomplete. Keep this behavior unchanged — user-stopped messages should immediately clear from the snapshot.

- [ ] **9. Handle `createNewChat()` cleanup path**

  `createNewChat()` at line 616-632 also calls `activeChatTurnSnapshotPort.clear(key)`. This is also correct — navigating to a new chat should clear stale snapshot state. Keep unchanged.

## Verification Criteria

- When an LLM response completes, the last visible text chunk should never regress to a shorter version
- When navigating away from and back to a chat, the full message should still be present
- `StreamableMarkdownText` should never receive empty `markdown` during the handoff (the data layer guarantees this)
- The `isStreaming` indicator should transition directly from `true` to `false` without flicker
- All existing unit tests pass
- New tests verify the synchronous handoff behavior

## Potential Risks and Mitigations

1. **Snapshot data persists for up to 5 minutes after handoff**
   - Mitigation: `ActiveChatTurnStore.scheduleTerminalCleanup` already handles this (5-minute delay). The snapshot occupies minimal memory (just a few strings). This is acceptable.

2. **`_acknowledgedTurnKeys` accumulates over time**
   - Mitigation: These are just `ActiveChatTurnKey` objects (two UUID strings each). Even with hundreds of conversations, memory impact is negligible. If needed, add cleanup when chat changes.

3. **Race between `_acknowledgedTurnKeys.update` and `activeTurnKeyFlow` recomputation**
   - Mitigation: Even if there's a one-frame window where the snapshot is still alive but DB is COMPLETE, `MergeMessagesUseCase` prefers the DB data. The UI shows correct content in both states.

4. **`activeTurnKeyFlow` `scan` might not transition to `null` immediately**
   - Mitigation: The `scan` operator processes `_acknowledgedTurnKeys` updates synchronously when they arrive. Since `_acknowledgedTurnKeys` is a `MutableStateFlow`, the `combine` that feeds `scan` will re-emit, and `scan` will see the acknowledged key. This is guaranteed by Kotlin Flow's sequential processing guarantee.

## Alternative Approaches

1. **Keep async `acknowledgeHandoff` but add a "grace period" where snapshot data is held in the UI state even after retirement**: This would require duplicating snapshot state in the ViewModel and managing its lifecycle separately. More complex, more state to track, harder to reason about. The proposed approach (deferring retirement to the store's terminal cleanup) is simpler and leverages existing infrastructure.

2. **Use `distinctUntilChanged` on the projected messages to suppress the transient frame**: This would mask the symptom but not fix the root cause. If the transient frame has different content (shorter text), `distinctUntilChanged` wouldn't suppress it since the content differs. It would only suppress if the frame had identical content (which is the happy path after the fix).

3. **Add a `ConflatedBroadcastChannel` or `SharedFlow` to buffer the last snapshot value alongside `StateFlow`**: This would allow the `combine` to see the last snapshot value even after retirement. Over-engineered — the store's terminal cleanup already provides this functionality.