# Last-Chunk Cutoff Fix — Atomic Handoff

## Objective

Eliminate the visible text cutoff/rollback during the transition from streaming snapshot data to Room-persisted data. The UI must never display a frame where content is shorter, missing, or in a pre-COMPLETE state compared to the previous frame.

## Root-Cause Analysis

### The Race

`acknowledgeHandoffIfNeeded` (ChatViewModel.kt:430-458) detects that Room has a COMPLETE message for a snapshot key, then launches an **async coroutine** (`viewModelScope.launch`) to call `activeChatTurnSnapshotPort.acknowledgeHandoff(key)`, which calls `retire(key)` — setting the snapshot flow to `null` and removing the entry. This async launch creates a window between "Room emits COMPLETE" and "snapshot is retired" where:

1. The `combine` that computes `uiState` sees `dbMessages` with GENERATING/partial content (from an intermediate persist in the service path) and `activeSnapshot` already `null` (retired).
2. `projectChatMessages` returns only the partial GENERATING message since the snapshot is gone.
3. The user sees text regress from 100% to 80%.

Even in the foreground path (no intermediate persists), there's a frame where `activeSnapshot` is `null` but Room hasn't emitted COMPLETE yet — the message disappears entirely for one frame.

### Why MergeMessagesUseCase Doesn't Save Us

`MergeMessagesUseCase` (MergeMessagesUseCase.kt:45-52) prefers `dbMessage` when `dbMessage.messageState == COMPLETE`. But when the snapshot is `null` and `dbMessage.messageState == GENERATING` (intermediate persist), the merge falls through to the `else` branch which returns `inFlightMessage.copy(...)` — but `inFlightMessage` is `null` because the snapshot was retired. So the merge returns `dbMessage` (the GENERATING/partial row). This is the rollback.

### User Constraint

The user wants to **keep** the `if (markdown.isEmpty()) return` spinner guard in `StreamableMarkdownText.kt:60`. The fix must be in the data layer.

## Solution

**Remove the async snapshot retirement.** The snapshot stays alive in `ActiveChatTurnStore` until its 5-minute terminal cleanup timer fires. Flow-level key management (`_acknowledgedTurnKeys`, `_requestedTurnKey`) remains synchronous, which causes `activeTurnKeyFlow` to transition to `null`, unsubscribing from the snapshot. But the snapshot data itself is not destroyed.

This makes the handoff atomic because `MergeMessagesUseCase` always has both data sources available during the transition:
- **Before COMPLETE Room data:** snapshot (COMPLETE, full text) + DB (GENERATING, partial) → merge picks snapshot ✓
- **After COMPLETE Room data:** snapshot (COMPLETE, full text) + DB (COMPLETE, full text) → merge picks DB ✓
- **After acknowledgement:** snapshot no longer observed (key → null in `activeTurnKeyFlow`) + DB (COMPLETE) → only DB used ✓

At no point is there a frame where the snapshot is retired but DB is not yet COMPLETE.

## Implementation Plan

- [ ] **1. Refactor `acknowledgeHandoffIfNeeded` to be synchronous — remove async snapshot retirement**

  In `ChatViewModel.kt`, replace the `acknowledgeHandoffIfNeeded` method (lines 430-458) with a version that does NOT call `activeChatTurnSnapshotPort.acknowledgeHandoff(key)`. Instead:

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

      // Synchronous flow-level acknowledgement only. No async snapshot retirement.
      // The snapshot data remains in ActiveChatTurnStore until its 5-minute terminal
      // cleanup timer fires, but MergeMessagesUseCase prefers COMPLETE DB messages
      // so the snapshot is ignored during the handoff transition. This eliminates
      // the race condition where the snapshot is retired before Room emits COMPLETE.
      _acknowledgedTurnKeys.update { keys -> keys + key }
      if (_requestedTurnKey.value == key) {
          _requestedTurnKey.update { null }
      }
  }
  ```

  Key changes from the current implementation:
  - Removed `handoffAcknowledgementsInFlight.add(key)` guard — no longer needed since no async launch
  - Removed `viewModelScope.launch { ... }` wrapper — all updates are synchronous
  - Removed `activeChatTurnSnapshotPort.acknowledgeHandoff(key)` — this was the root cause of the race condition
  - Removed `_acknowledgedTurnKeys.update` from inside the async block — now synchronous
  - Removed `handoffAcknowledgementsInFlight.remove(key)` from the `finally` block

  **Rationale:** `acknowledgeHandoff(key)` calls `retire(key)` which sets the snapshot `MutableStateFlow` to `null` and removes the entry. When called asynchronously, it creates an unpredictable gap between Room COMPLETE emission and snapshot retirement. By NOT retiring the snapshot, `MergeMessagesUseCase` always has both data sources available, and it correctly picks the COMPLETE DB message over the snapshot once Room catches up.

- [ ] **2. Remove the `handoffAcknowledgementsInFlight` field**

  In `ChatViewModel.kt`, remove:
  - The field declaration at line 140: `private val handoffAcknowledgementsInFlight = mutableSetOf<ActiveChatTurnKey>()`
  - The usage in `stopGeneration()` at line 610: `handoffAcknowledgementsInFlight.remove(key)` (inside the `viewModelScope.launch` block)
  - The usage in `createNewChat()` at line 621: `handoffAcknowledgementsInFlight.remove(key)` (inside the `viewModelScope.launch` block)

  **Rationale:** `handoffAcknowledgementsInFlight` was a deduplication guard for the async coroutine launch. Since we no longer launch an async coroutine, this guard is unnecessary.

- [ ] **3. Handle `stopGeneration()` and `createNewChat()` — keep `activeChatTurnSnapshotPort.clear(key)` calls**

  These methods explicitly clear the snapshot on user action (stop generation, new chat). This is correct behavior — when the user stops generation or starts a new chat, we want immediate snapshot cleanup. Keep these as-is:
  - `stopGeneration()` (lines 602-614): Keep `activeChatTurnSnapshotPort.clear(key)` call
  - `createNewChat()` (lines 616-632): Keep `activeChatTurnSnapshotPort.clear(key)` call

  However, remove the `handoffAcknowledgementsInFlight.remove(key)` calls from both methods (step 2).

- [ ] **4. Verify `MergeMessagesUseCase` handles the overlap correctly (no change needed)**

  `MergeMessagesUseCase.kt:45-52`:
  ```kotlin
  return if (dbMessage.messageState == MessageState.COMPLETE) {
      dbMessage  // DB has the final persisted state, use it
  } else {
      inFlightMessage.copy(createdAt = dbMessage.createdAt)
      // DB is still processing, use in-flight which has real content
  }
  ```

  When both snapshot and DB COMPLETE exist for the same message ID:
  - `dbMessage.messageState == COMPLETE` → returns `dbMessage` ✓

  When snapshot is COMPLETE but DB is GENERATING:
  - `dbMessage.messageState != COMPLETE` → returns `inFlightMessage` (snapshot) with `createdAt` from DB ✓

  When snapshot is `null` (after key acknowledged, flow unsubscribed):
  - `inFlightMessage == null` → returns `dbMessage` (whatever state it's in)

  The only problematic case is when `inFlightMessage == null` AND `dbMessage.messageState != COMPLETE`. But this can only happen if the snapshot is retired (key acknowledged → `flatMapLatest` returns null) before Room has the COMPLETE row. With the proposed fix, the snapshot is NOT retired until the 5-minute terminal cleanup — and by that time, Room has long since persisted the COMPLETE row. So `inFlightMessage == null` will only coexist with `dbMessage.messageState == COMPLETE`. ✓

- [ ] **5. Verify `ActiveChatTurnStore.scheduleTerminalCleanup` covers the snapshot lifecycle (no change needed)**

  `ActiveChatTurnStore.kt:46-50` and `105-120`: When `publish()` is called with a terminal snapshot (`isTerminalSnapshot == true`), `scheduleTerminalCleanup` is called, which removes the entry after 5 minutes. This is sufficient for cleanup.

  Additionally, `acknowledgeHandoff()` calls `retire()` which immediately removes the entry. With the proposed fix, `acknowledgeHandoff` is no longer called during normal handoff. The entry stays alive for up to 5 minutes. Given that each entry holds a few KB of strings, this memory overhead is negligible.

- [ ] **6. Verify `activeTurnKeyFlow` scan logic (no change needed)**

  `ChatViewModel.kt:179-201`: The `scan` operator in `activeTurnKeyFlow` transitions to `null` when the key is in `_acknowledgedTurnKeys`. With the synchronous update in step 1:

  Frame N (acknowledgement frame):
  - `uiState` combine fires with `dbMessages = [COMPLETE msg]`, `activeSnapshot = (key, COMPLETE snapshot)`
  - `projectChatMessages` merges: COMPLETE db + COMPLETE snapshot → picks db ✓
  - `acknowledgeHandoffIfNeeded` adds key to `_acknowledgedTurnKeys` synchronously

  Frame N+1 (after `_acknowledgedTurnKeys` update propagates):
  - `activeTurnKeyFlow` transitions to `null` (scan sees key in acknowledged set)
  - `activeTurnSnapshotFlow` emits `(null, null)`
  - `uiState` combine fires with `dbMessages = [COMPLETE msg]`, `activeSnapshot = null`
  - `projectChatMessages` merges: COMPLETE db + null snapshot → picks db ✓

  **No gap frame.** Both frames show the correct COMPLETE DB message.

- [ ] **7. Write unit tests for the new `acknowledgeHandoffIfNeeded` behavior**

  In the appropriate test file (likely `ChatViewModelTest.kt`), add tests that verify:

  1. `acknowledgeHandoffIfNeeded` does NOT call `activeChatTurnSnapshotPort.acknowledgeHandoff()` — verify the mock is never called
  2. `acknowledgeHandoffIfNeeded` synchronously adds the key to `_acknowledgedTurnKeys` when DB has COMPLETE messages matching the snapshot
  3. `acknowledgeHandoffIfNeeded` synchronously clears `_requestedTurnKey` when it matches the acknowledged key
  4. `projectChatMessages` returns the DB message when both snapshot and DB have COMPLETE data for the same message ID
  5. `projectChatMessages` returns the snapshot message when DB has GENERATING data and snapshot has COMPLETE data
  6. `projectChatMessages` returns the DB message when snapshot is null and DB has COMPLETE data
  7. End-to-end scenario: simulate the handoff sequence (snapshot COMPLETE → DB COMPLETE → acknowledgement) and verify no intermediate frame shows partial content

- [ ] **8. Run existing tests to verify no regression**

  Run `./gradlew :feature:chat:testDebugUnitTest` to verify all existing ChatViewModel tests pass with the new `acknowledgeHandoffIfNeeded` implementation.

## Verification Criteria

- [ ] When an LLM response completes, the last visible text chunk never regresses to a shorter version
- [ ] When navigating away from and back to a chat, the full message is present
- [ ] `StreamableMarkdownText` never receives empty `markdown` during the handoff (the data layer guarantees this)
- [ ] The `isStreaming` indicator transitions directly from `true` to `false` without flicker
- [ ] No async coroutine launches in `acknowledgeHandoffIfNeeded`
- [ ] All existing unit tests pass
- [ ] New tests cover the synchronous handoff behavior

## Potential Risks and Mitigations

1. **Snapshot data persists for up to 5 minutes after handoff**
   - Mitigation: `ActiveChatTurnStore.scheduleTerminalCleanup` handles this with a 5-minute timer. Each snapshot holds a few KB of strings. Even with heavy usage, memory impact is negligible. If concern arises, reduce the timer to 30 seconds.

2. **`_acknowledgedTurnKeys` accumulates entries over time**
   - Mitigation: Each key is two UUID strings (~72 bytes). Even after hundreds of conversations, total memory is <100KB. If needed, add cleanup in `createNewChat()` to clear stale entries.

3. **`stopGeneration()` and `createNewChat()` still call `activeChatTurnSnapshotPort.clear(key)`**
   - Mitigation: These are user-initiated actions where immediate snapshot cleanup is correct. The snapshot should be cleared when the user actively stops generation or starts a new chat.

4. **Multiple messages in CREW mode**: In CREW mode, a single `ActiveChatTurnKey` may reference multiple assistant messages (draft_one, draft_two, synthesis). The `shouldAcknowledge` check (`activeSnapshot.messages.keys.any { id -> id in completedDbIds }`) correctly handles this — it acknowledges when ANY message ID in the snapshot appears as COMPLETE in the DB. Verify that all message IDs in the snapshot appear as COMPLETE in the DB before acknowledging. The current check is sufficient because the terminal snapshot has all messages in COMPLETE state, and the DB persist writes all messages atomically (within `@Transaction`).

## Alternative Approaches Considered

1. **Keep async `acknowledgeHandoff` but add a grace period where snapshot data is buffered in UI state**: Would require duplicating snapshot state in the ViewModel, managing lifecycle separately, and adding complex state coordination. The proposed approach (defer retirement to store cleanup) is simpler and leverages existing infrastructure.

2. **Use `distinctUntilChanged` on projected messages to suppress transient frames**: Would only mask symptoms. If the transient frame has different content (shorter text), `distinctUntilChanged` won't suppress it since the content differs.

3. **Use a `SharedFlow` or `ConflatedBroadcastChannel` to buffer the last snapshot value after retirement**: Over-engineered. The store's terminal cleanup plus `MergeMessagesUseCase` logic already handles the overlap correctly.

## Files to Modify

1. **`feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatViewModel.kt`**:
   - Lines 430-458: Replace `acknowledgeHandoffIfNeeded` implementation (remove async launch, remove `acknowledgeHandoff` call)
   - Line 140: Remove `handoffAcknowledgementsInFlight` field declaration
   - Lines 602-614: Remove `handoffAcknowledgementsInFlight.remove(key)` from `stopGeneration()`
   - Lines 616-632: Remove `handoffAcknowledgementsInFlight.remove(key)` from `createNewChat()`

2. **`feature/chat/src/test/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatViewModelTest.kt`** (or appropriate test file):
   - Add tests for the new synchronous `acknowledgeHandoffIfNeeded` behavior
   - Add test verifying `activeChatTurnSnapshotPort.acknowledgeHandoff()` is NOT called during normal handoff