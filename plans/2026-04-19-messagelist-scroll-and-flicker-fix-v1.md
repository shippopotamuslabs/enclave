# MessageList: Scroll Behavior & In-Flight Flicker Fix

## Objective

Make two targeted changes to the MessageList composable and ChatViewModel:
1. **Stop auto-scrolling during streaming** — let the user scroll naturally while an AssistantResponse streams in, but still auto-scroll when the user sends a new message.
2. **Eliminate the flicker** when `_inFlightMessages` is cleared on inference completion and the database Flow hasn't yet emitted the finalized message, causing the message to temporarily vanish for one composition frame.

---

## Implementation Plan

### Problem 1: Auto-Scroll During Streaming

**Root Cause:** `MessageList.kt:70-73` — `LaunchedEffect(messages.size)` unconditionally calls `listState.animateScrollToItem(0)` every time the message list size changes. During streaming, the assistant message's content progressively grows (or new pipeline-step messages appear), triggering size changes and forcing the user back to the bottom.

**Solution:** Replace the blanket `messages.size` key with a more selective trigger. Only auto-scroll when a *new user message* is added to the list. Detect this by tracking the set of user-message IDs rather than the total list size.

- [ ] **1.1 Add `isGenerating` parameter to `MessageList` composable** — Currently `MessageList` receives `hasActiveIndicator` but not `isGenerating`. Add `isGenerating: Boolean = false` parameter so the composable knows when a response is being streamed. This is already available in `ChatUiState.isGenerating` and is passed through `ChatScreen`.

  *Rationale:* Needed so the scroll logic can differentiate "user just sent a message" from "assistant is streaming tokens."

- [ ] **1.2 Replace the unconditional `LaunchedEffect(messages.size)` with selective user-message scroll logic in `MessageList.kt`** — Track the set of user message IDs. When a *new* user message ID appears in the list, scroll to item 0. During streaming (when `isGenerating` is true), do NOT auto-scroll on message list changes that are from the assistant.

  Implementation approach:
  - Use `remember` to track the last known set of user message IDs.
  - In a `LaunchedEffect` keyed on the set of user message IDs, compare with previous set and only scroll if new user messages appeared.
  - Alternatively, a simpler approach: use `derivedStateOf` or `snapshotFlow` to detect when the latest user message changes, and only then trigger the scroll.
  - The simplest robust approach: key the `LaunchedEffect` on a derived value — the ID of the last user message in the list. When this changes (new user message sent), scroll. When only assistant content changes (streaming), do nothing.

  *Rationale:* Keying on the last user message ID ensures we auto-scroll exactly when the user sends a message, but never during streaming content updates.

- [ ] **1.3 Pass `isGenerating` from `ChatScreen.kt` to `MessageList`** — In `ChatScreen.kt:62-69`, add `isGenerating = uiState.isGenerating` to the `MessageList` call.

  *Rationale:* Wires the new parameter through the existing call site.

- [ ] **1.4 Update all `MessageList` preview usages** that would break due to the new parameter — The previews in `MessageList.kt` don't pass `isGenerating`. Add the default parameter value so previews continue to compile without changes (the parameter should default to `false`).

---

### Problem 2: Flicker When In-Flight Messages Transition to Database

**Root Cause Race Condition:**
1. Inference flow completes → `GenerateChatResponseUseCase` calls `persistAccumulatedMessages` → database is updated.
2. Then `onCompletion` handler in `ChatViewModel.kt:556` fires and sets `_inFlightMessages.value = emptyMap()`.
3. The database Flow has a 50ms debounce (`chatUseCases.getChat(id).debounce(50)` at `ChatViewModel.kt:248`).
4. There is a window where: in-flight is cleared → DB hasn't re-emitted yet → `combine` produces a frame with NO message for that ID → the message disappears from the list for one composition.
5. Next DB emission arrives → message reappears as a DB message (COMPLETE state) → one-frame flicker.

This flicker also destroys the user's scroll position because LazyColumn recalculates its items.

**Solution Strategy:** Instead of clearing `_inFlightMessages` atomically in `onCompletion`, keep the completed in-flight messages around until the database Flow confirms it has caught up with the COMPLETE state for those message IDs. The merge logic already handles this: `MergeMessagesUseCase` returns the `dbMessage` when `dbMessage.messageState == COMPLETE`. So if we keep the in-flight messages until the DB emission contains matching COMPLETE messages, the merge will seamlessly transition.

- [ ] **2.1 Change `_inFlightMessages` clearing in `onCompletion` handler to be deferred/conditional** — In `ChatViewModel.kt:553-557`, instead of immediately setting `_inFlightMessages.value = emptyMap()`, change the approach to:

  **Option A (Recommended): Remove `_inFlightMessages.value = emptyMap()` from `onCompletion` entirely**, and instead let the in-flight entries be cleaned up by a distinct observation of the DB flow. Add a new observer that compares the current `_inFlightMessages` keys against the DB messages. When the DB flow emits messages that are in `COMPLETE` state for all in-flight message IDs, clear only those matched entries from `_inFlightMessages`.

  Specifically, add a `forEach`-style observer on the DB messages flow that runs alongside the existing `combine`. When `messages` (the DB list) contains all IDs from `_inFlightMessages` AND those DB messages have `messageState == COMPLETE`, remove those entries from `_inFlightMessages`. This ensures the in-flight entries are only removed *after* the DB has caught up, making the transition seamless.

  *Rationale:* The merge logic in `MergeMessagesUseCase` already prefers `dbMessage` when it's COMPLETE. So during the transition window, both in-flight and DB messages exist for the same ID. The merge returns the DB message — no flicker. Once the DB confirms the message is COMPLETE, the in-flight entry is removed — the message stays present because the DB version is already in the list.

- [ ] **2.2 Add a side-effect observer on the DB flow in `ChatViewModel` that prunes completed in-flight messages** — After the existing `combine` for `uiState`, add an `onEach` subscriber on the DB messages flow that:
  1. Gets the current `_inFlightMessages` keys.
  2. For each key, checks if the DB messages list now contains a message with that ID AND `messageState == COMPLETE`.
  3. If so, removes that key from `_inFlightMessages`.
  4. This must be a separate flow collection (not inside the `combine`) so it can mutate `_inFlightMessages` without interfering with the `combine`'s read of it.

  *Rationale:* A separate observer can mutate `_inFlightMessages` independently. When it removes entries, the `combine` will re-emit on the next frame, but by then the DB message is already present, so there's no gap.

- [ ] **2.3 Simplify `onCompletion` in `onSendMessage`** — Remove or simplify the `_inFlightMessages.value = emptyMap()` in the `onCompletion` handler at `ChatViewModel.kt:556`. Keep it as a safety fallback (e.g., a delayed clear after a timeout) or remove entirely since the observer from 2.2 handles cleanup.

  *Rationale:* The onCompletion clear is the direct cause of the flicker. Removing it (or making it defensive) eliminates the gap.

- [ ] **2.4 Verify `MergeMessagesUseCase` handles the overlap correctly** — The existing merge logic at `MergeMessagesUseCase.kt:45-52` already returns `dbMessage` when `dbMessage.messageState == COMPLETE`. This is exactly what we need: when both in-flight and DB COMPLETE exist for the same ID, the DB version wins. No change needed here, but confirm by reading the test at `MergeMessagesUseCaseTest.kt:40-46`.

- [ ] **2.5 Handle edge cases in the prune observer** — The prune observer must handle:
  - Chat changes (new chat / navigating away): When `_currentChatId` changes, all in-flight messages should be cleared immediately (as it already is in `createNewChat`). No change needed here, but verify the observer doesn't hold stale references.
  - Error/Failed states: If inference fails and doesn't produce a COMPLETE DB entry, the in-flight messages could persist indefinitely. Add a safety timeout (e.g., 5 seconds) that forcibly clears in-flight if the DB hasn't confirmed. Alternatively, when `stopGeneration()` is called, the existing immediate clear is correct (no flicker risk since the user initiated the stop).

---

### Verification & Testing

- [ ] **3.1 Update existing `ChatViewModelTest.kt` tests** — Any test that verifies `_inFlightMessages` is cleared on completion needs to be updated to test the new deferred clearing behavior. The test should now verify that `_inFlightMessages` is cleared *after* the DB confirms COMPLETE state for those IDs.

- [ ] **3.2 Add test for the prune observer** — Write a unit test in `ChatViewModelTest.kt` that:
  1. Sets up in-flight messages.
  2. Emits DB messages with COMPLETE state for those IDs.
  3. Verifies the in-flight messages are pruned.
  4. Verifies that if DB messages are NOT yet COMPLETE, in-flight messages are retained.

- [ ] **3.3 Add manual test scenario for scroll behavior** — Verify:
  1. Send a message → list auto-scrolls to show the user message.
  2. While assistant streams, scroll up → list stays where scrolled, does NOT auto-scroll down.
  3. While assistant is still streaming, send another message → list auto-scrolls to show the new user message.
  4. After streaming completes → no flicker on the assistant message.

- [ ] **3.4 Run contract validation checks** — `./gradlew :feature:chat:assembleDebug` and `./gradlew :feature:chat:testDebugUnitTest`

---

## Verification Criteria

- [ ] **No auto-scroll during assistant streaming**: While `isGenerating` is true and content is being added, the LazyColumn must not force-scroll to item 0. The user must be able to scroll freely.
- [ ] **Auto-scroll on user message send**: When a new user message is added to the list, the LazyColumn must scroll to show it. This auto-scroll must work both when the assistant is generating and when it's idle.
- [ ] **No flicker on message completion**: When the assistant finishes generating, the assistant message must remain visible without any frame where the content disappears. The transition from in-flight → database must be atomic from the user's perspective.
- [ ] **Scroll position preserved on completion**: When the user is scrolled to a specific position and the assistant finishes generating, the scroll position must not jump (unless the user was already at the bottom).
- [ ] **All existing tests pass**: `./gradlew :feature:chat:testDebugUnitTest` passes with updated expectations.

---

## Potential Risks and Mitigations

1. **Race between DB debounce and in-flight clearing**
   - *Risk:* The 50ms debounce on the DB flow means there's always a window. The prune observer approach eliminates this by never clearing in-flight until the DB has confirmed.
   - *Mitigation:* The prune observer reads the DB flow directly without debounce, so it reacts to DB emissions as soon as they happen. Even if the `combine`'s debounced DB flow hasn't re-emitted yet, the prune observer will see the next DB emission and clear the matched in-flight entries, ensuring the `combine` next re-computation has the DB message available.

2. **Memory leak from in-flight messages never cleared**
   - *Risk:* If the DB never emits COMPLETE for an in-flight message (e.g., error path), in-flight entries persist forever.
   - *Mitigation:* Add a safety timeout in the prune observer or handle in `onCleared`. Also, the existing `stopGeneration()` and `createNewChat()` paths already clear `_inFlightMessages` immediately, which is safe in those user-initiated scenarios (no flicker concern since the chat context changes).

3. **Compose key stability causing LazyColumn to re-layout items**
   - *Risk:* When in-flight message data changes (e.g., the indicator state changes from Generating to Complete), if the key or item identity changes, LazyColumn might re-layout and lose scroll position.
   - *Mitigation:* The existing key scheme `"msg_${message.id}"` at `MessageList.kt:88` is stable — it only depends on the message ID, not the content. So content/state changes within the same message won't cause LazyColumn to re-layout. This is already correct.

4. **Multiple in-flight messages for Crew mode pipeline steps**
   - *Risk:* In Crew mode, there can be multiple in-flight assistant messages (DRAFT_ONE, DRAFT_TWO, SYNTHESIS, FINAL). The prune logic must only clear the entries that the DB has confirmed COMPLETE, not all at once.
   - *Mitigation:* The prune observer checks per-ID, so pipeline steps that complete earlier are cleaned up individually. No batching is needed.

---

## Alternative Approaches

1. **Compose-side `remember` buffer for disappearing messages**: Instead of fixing the ViewModel merge logic, add a `remember` in the composable that temporarily holds the last rendered message for each ID, filling gaps when the list momentarily drops a message. This is a pure UI-side fix.
   - *Trade-off:* More complex composable logic, harder to reason about, doesn't fix the root cause. The ViewModel approach is cleaner and ensures the data flow is correct, not just papered over.

2. **Remove debounce on DB flow**: If we remove the `.debounce(50)` at `ChatViewModel.kt:248`, the DB emission would arrive faster and reduce the flicker window. However, this might cause excessive recompositions from rapid DB updates during multi-step Crew mode generation.
   - *Trade-off:* Could improve responsiveness but risks performance regression. The deferred-clear approach is orthogonal and works regardless of debounce duration.

3. **Keep in-flight entries with a `stale` flag instead of clearing**: Mark in-flight entries as stale in `onCompletion`, then have the merge logic prefer stale in-flight over nothing, and DB over stale in-flight. This requires modifying `MergeMessagesUseCase`.
   - *Trade-off:* Adds complexity to the pure domain use case. The prune-observer approach keeps the merge logic unchanged and handles cleanup at the ViewModel layer where it belongs.

4. **Use `distinctUntilChanged` on the messages list**: Add `distinctUntilChanged` after the merge to skip emitting frames where the effective message list hasn't changed.
   - *Trade-off:* The list DOES change (in-flight clears, then DB arrives), the messages are different objects. `distinctUntilChanged` on a `List<ChatMessage>` would need deep equality, which is expensive. Not viable.