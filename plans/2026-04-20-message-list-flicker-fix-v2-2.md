# MessageList: Fix In-Flight → DB Transition Flicker & Scroll Jump (v2)

## Objective

Eliminate the visual flicker that occurs when an assistant message transitions from in-flight to database-persisted state, and prevent the scroll position from jumping to the top when this transition happens while the user is reading a streaming response.

---

## Root Cause Analysis

### Problem: Flicker During In-Flight → DB Transition

The flicker occurs because of a **race between two flow observers** that both read from `_inFlightMessages`:

1. **`uiState` combine** (line 253): Uses `dbMessagesFlow.debounce(50)` — the 50ms debounce delays the DB emission.
2. **`observeCompletedInFlightMessages`** (line 348): Uses `dbMessagesFlow` (no debounce) — reacts immediately to DB changes.

**The race sequence:**
1. Inference completes → DB writes COMPLETE message → `dbMessagesFlow` emits the new COMPLETE message.
2. The **prune observer** (no debounce) fires immediately, sees the COMPLETE DB message, and **removes** the in-flight entry from `_inFlightMessages`.
3. The **`uiState` combine** with its 50ms debounce has NOT yet re-emitted with the new DB data.
4. **Gap frame**: `_inFlightMessages` is now empty for that ID, and the debounced DB flow hasn't caught up → the combined result has **no message for that assistant ID** → blank composition → scroll position invalidation.
5. ~50ms later the debounced DB flow emits → message reappears → but scroll position is already lost.

**Why the scroll jumps to the top**: When the assistant message briefly disappears, the `active_interaction` item in the LazyColumn (which contains "user prompt + streaming response") loses content. If the user was scrolled deep into the response, the LazyColumn recalculates and their scroll offset references an item/content region that no longer exists. This causes the list to snap back to the nearest valid position, which is typically the user message at the top of the `active_interaction` column.

### Why the existing v1 fix (deferred pruning) is insufficient

The v1 plan correctly identified that we need to keep in-flight messages around until DB confirms. The implementation added `observeCompletedInFlightMessages()` with `pruneCompletedInFlightMessages()`. However, this prune observer uses **the non-debounced `dbMessagesFlow`** while the `uiState` combine uses **the debounced version**. The prune observer removes the in-flight entry *before* the combine has seen the corresponding DB message, creating the gap frame.

---

## Implementation Plan

### Fix 1: Align Pruning with the Debounced DB Flow in the Combine

The core fix is to ensure that in-flight messages are never removed before the `uiState` combine has access to the corresponding COMPLETE DB message.

**Strategy**: Instead of pruning `_inFlightMessages` from a separate observer on the non-debounced flow, move the pruning logic into the `uiState` combine itself — or synchronize the prune observer to use the same debounced flow source as `uiState`.

- [ ] **1.1 Refactor: Remove the separate `observeCompletedInFlightMessages()` observer from `init` block** — This observer currently fires on the non-debounced `dbMessagesFlow`, which is the direct cause of the race condition. Remove it from `init` in `ChatViewModel.kt:151`.

  *Rationale*: The separate observer prunes before the combine has the COMPLETE data, causing the flicker gap.

- [ ] **1.2 Add pruning logic inside the `uiState` combine lambda** — After the combine computes `allMergedMessages` and before returning `ChatUiState`, add a side-effect that prunes `_inFlightMessages` entries whose IDs now appear as COMPLETE in the DB messages. Use `_inFlightMessages.update { }` to remove matched entries.

  *Rationale*: By pruning inside the combine, the in-flight entry is only removed **in the same recomposition** that already has the COMPLETE DB message available. This makes the transition atomic: Frame N has in-flight, Frame N+1 has both (merge picks DB COMPLETE), and then in-flight is removed for Frame N+2 — but since Frame N+1 already had the DB message, there's never a gap.

  **Implementation detail**: Since `_inFlightMessages.update` inside a `combine` would cause a re-emission loop (changing `_inFlightMessages` triggers the combine to re-fire), we need to handle this carefully. Use `viewModelScope.launch` to post the prune update, giving the current combine emission time to propagate first:
  ```kotlin
  // Inside the combine lambda, after computing allMergedMessages:
  val completedDbIds = messages.filter { it.messageState == MessageState.COMPLETE }.map { it.id }.toSet()
  if (completedDbIds.isNotEmpty() && inFlight.isNotEmpty()) {
      val idsToPrune = inFlight.keys.filter { it in completedDbIds }
      if (idsToPrune.isNotEmpty()) {
          viewModelScope.launch { _inFlightMessages.update { current -> current.filterKeys { it !in completedDbIds } } }
      }
  }
  ```

- [ ] **1.3 Verify the fallback timeout still works** — The `scheduleInFlightCompletionFallback()` at line 591 is called from `onCompletion` in the inference flow. This should remain as-is (it's a safety net for when the DB never confirms). Confirm it cancels when `_inFlightMessages` becomes empty (which it does at line 358-360, but needs to be checked after the refactor — the `if (_inFlightMessages.value.isEmpty())` check in `pruneInFlightMessagesConfirmedByDatabase` will still fire from the new pruning location).

  *Rationale*: The 5-second fallback timeout is the safety net. It must still function independently.

- [ ] **1.4 Remove `dbMessagesFlow.debounce(50)` from the combine or reduce its impact** — The 50ms debounce on `dbMessagesFlow` in the combine (line 256) is a second source of delay. Consider whether this debounce is still needed. The debounce was originally added to avoid excessive UI recompositions from rapid DB updates (e.g., during streaming token persistence). Since token-level updates during streaming are handled via in-flight messages (not DB), the DB updates during streaming are primarily for status transitions and final persistence — which can be processed immediately.

  **Recommended approach**: Remove the `.debounce(50)` from `dbMessagesFlow` in the combine at line 256. The in-flight messages already handle real-time updates, so the DB flow only needs to emit for status transitions which are infrequent and should be processed promptly.

  *Alternative*: If debounce removal causes performance issues, reduce to `.debounce(16)` (one frame) or `.distinctUntilChanged()` on the messages list. However, since `messages` is a `List<Message>` with different object identities per emission, `distinctUntilChanged` may not help. The safest fix is to remove debounce entirely and rely on `StateIn`'s `WhileSubscribed(5_000)` to handle downstream.

---

### Fix 2: Prevent Scroll Jump on Message Identity Change in LazyColumn

Even with Fix 1 eliminating the gap frame, there's a secondary risk: when the message transitions from in-flight to DB-backed, the message's content object identity changes (different `ChatMessage` instance). If the LazyColumn key scheme doesn't guarantee stability, the item might be recomposed or its height recalculated, potentially shifting scroll position.

The current `MessageList` key scheme (lines 119, 135) uses:
- History items: `"history_msg_${messages[index].id}"` — stable (depends on message ID)
- Active interaction: `"active_interaction"` — **NOT stable** across transitions

The `active_interaction` is a single item that contains all messages from the latest user message to the end. This is architecturally good for scroll stability (it prevents jumping when height changes). However, the content of this item changes during the in-flight → DB transition.

- [ ] **2.1 Verify that `active_interaction` key stability is sufficient** — The key `"active_interaction"` is constant as long as the latest user message index doesn't change. During in-flight → DB transition, the latest user message stays the same, so the key is stable. The content changes from in-flight data to DB data, but since it's the same item, Compose will recompose in-place without destroying/recreating the item. Verify this by checking that `latestUserMessageIndex` doesn't change during the transition (it shouldn't, since both in-flight and DB messages for the same ID have the same user message).

  *Rationale*: If the key remains the same, the LazyColumn item is preserved and only its content changes. This means scroll position within the item should be maintained automatically. The `isInitialLoadDone` / `lastScrolledUserMessageId` scroll logic won't trigger because `latestUserMessageId` doesn't change during the transition.

- [ ] **2.2 Guard against `isInitialLoadDone` being stale after chat ID changes** — When the user opens a different chat, the `MessageList` composable might retain `isInitialLoadDone = true` and `lastScrolledUserMessageId` from the previous chat. Add a reset mechanism: when `messages` becomes empty (which happens briefly during chat switch), reset `isInitialLoadDone` and `lastScrolledUserMessageId`.

  *Rationale*: Without this reset, switching chats won't auto-scroll to the latest message because `isInitialLoadDone` is already `true`.

  Implementation:
  ```kotlin
  LaunchedEffect(messages.isEmpty()) {
      if (messages.isEmpty()) {
          isInitialLoadDone = false
          lastScrolledUserMessageId = null
      }
  }
  ```

---

### Fix 3: Remove the 50ms Debounce Hazard Entirely (Alternative to 1.4)

If removing the debounce entirely (Fix 1.4) is deemed too risky, an alternative is to use a **distinct debounced flow for pruning only**.

- [ ] **3.1 (Alternative to 1.4) Create a separate debounced flow for pruning** — Instead of pruning in the combine, create a `dbMessagesPruneFlow` that is the debounced version of `dbMessagesFlow`, and use that as the pruning trigger in `observeCompletedInFlightMessages`. This ensures pruning happens at the same cadence as the combine's DB emissions.

  ```kotlin
  private val debouncedDbMessagesFlow = dbMessagesFlow.debounce(50)
  
  private fun observeCompletedInFlightMessages() {
      debouncedDbMessagesFlow.onEach { messages ->
          pruneInFlightMessagesConfirmedByDatabase(messages)
      }.launchIn(viewModelScope)
  }
  ```

  Then the `uiState` combine also uses `debouncedDbMessagesFlow`:
  ```kotlin
  val uiState: StateFlow<ChatUiState> = combine(
      uiInputsWithPolicyFlow,
      inferenceLockManager.isInferenceBlocked,
      debouncedDbMessagesFlow,  // Same debounced source
      _inFlightMessages,
      _activeToolCallBanner
  ) { ... }
  ```

  *Rationale*: Both the pruning and the combine now see the DB messages at the exact same time, eliminating the race. In the same frame, the combine has the COMPLETE DB message AND the prune removes the in-flight entry. The combine's current emission still has the in-flight entry (because pruning is an `onEach` that posts asynchronously), so the next combine emission will have the DB message without in-flight — no gap.

---

## Recommended Approach (Priority Order)

**Primary fix**: Fix 1.2 + Fix 1.4 — Prune inside the combine and remove the 50ms debounce. This is the cleanest solution and eliminates the race entirely.

**Fallback fix**: Fix 3.1 — If removing debounce causes issues, use the same debounced flow for both pruning and combining.

**Stability fix**: Fix 2.1 + 2.2 — Verify key stability and add chat-switch reset logic.

---

## Verification Criteria

- [ ] **No flicker on message completion**: When the assistant finishes generating, the message must remain visible without any frame where the content disappears. The transition from in-flight → database must be atomic from the user's perspective.
- [ ] **Scroll position preserved on completion**: When the user is scrolled to a specific position reading a response and the assistant finishes generating, the scroll position must not jump to the top or shift significantly.
- [ ] **No auto-scroll during assistant streaming**: The LazyColumn must not force-scroll when assistant content is added during streaming. Only new user messages trigger auto-scroll.
- [ ] **Auto-scroll on user message send**: When a new user message is added, the list scrolls to show it.
- [ ] **Chat switching works correctly**: Switching to a different chat resets scroll state and auto-scrolls to the latest message.
- [ ] **Fallback timeout still works**: If DB never confirms a message, the 5-second fallback clears `_inFlightMessages`.
- [ ] **All existing tests pass**: `./gradlew :feature:chat:testDebugUnitTest` passes.
- [ ] **New test for prune timing**: A test that verifies in-flight messages are retained when DB has not yet emitted, and only pruned after the combine has access to the COMPLETE DB message.

---

## Potential Risks and Mitigations

1. **Re-emission loop from pruning inside combine**
   - *Risk*: Calling `_inFlightMessages.update { }` inside the combine lambda will cause `_inFlightMessages` to emit again, triggering the combine to re-fire.
   - *Mitigation*: Use `viewModelScope.launch` to post the prune update asynchronously (launch + `update`). This means the current combine emission completes with the in-flight entry still present, and the prune update triggers a second combine emission that now has the COMPLETE DB message. The user sees Frame N (in-flight data) → Frame N+1 (both present, merge picks DB COMPLETE) → Frame N+2 (in-flight pruned, DB data remains). No gap frame exists because Frame N+1 already had the DB message.

2. **Removing debounce may increase combine re-emissions**
   - *Risk*: Room emits frequently during multi-step Crew pipeline (DRAFT_ONE → DRAFT_TWO → SYNTHESIS → FINAL), causing many combine re-computations.
   - *Mitigation*: Each combine re-computation is cheap (it's just merging maps and mapping to UI models). The `stateIn` with `WhileSubscribed(5000)` already handles downstream throttling. If performance profiling shows excessive recompositions, apply `distinctUntilChanged()` on the final `ChatUiState` flow.

3. **Chat switch scroll reset**
   - *Risk*: Resetting `isInitialLoadDone` on empty messages might trigger an unintended auto-scroll if the chat list briefly becomes non-empty before being fully loaded.
   - *Mitigation*: The `LaunchedEffect(messages.isNotEmpty())` key already handles this — it only fires when messages transition from empty to non-empty. The reset on empty is safe.

4. **Multiple in-flight messages for Crew mode**
   - *Risk*: Crew pipeline has multiple assistant messages in flight simultaneously. The prune logic must handle each one independently.
   - *Mitigation*: The proposed prune logic checks per-ID (`inFlight.keys.filter { it in completedDbIds }`), so each pipeline step is pruned individually when its DB confirmation arrives. No batching issue.

---

## Alternative Approaches

1. **Use `distinctUntilChanged()` on the final `ChatUiState` flow**: Instead of removing the debounce, add `.distinctUntilChanged()` after the combine. This prevents duplicate emissions but doesn't fix the race condition. Not recommended as the primary fix.

2. **Retain in-flight messages with a "stale" flag**: Instead of pruning, mark in-flight entries as "superseded" when the DB confirms, and skip them in the merge. The entries get garbage-collected on the next chat switch. This avoids mutation inside combine but adds complexity. Not recommended — the async launch approach in Fix 1.2 is simpler.

3. **Use `snapshotFlow` in MessageList to detect transient empty states**: Add a `derivedStateOf` that detects when the active interaction suddenly loses content and suppresses the layout change for one frame. This is fighting symptoms, not the root cause. Not recommended.