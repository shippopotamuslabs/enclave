# MessageList Send/Scroll/Pin Audit — Pocket Crew vs Google AI Edge Gallery

Date: 2026-05-21
Branch: `feat/agent-core`
Scope: read-only investigation. No production code changes.

Sources referenced:
- Pocket Crew (this repo)
  - `feature/chat/.../components/messagelist/MessageList.kt`
  - `feature/chat/.../components/messagelist/MessageListScrollBehavior.kt`
  - `feature/chat/.../components/messagelist/MessageListLayout.kt`
  - `feature/chat/.../components/messagelist/MessageListItem.kt`
  - `feature/chat/.../ChatScreen.kt`
  - `feature/chat/.../delegates/InferenceOrchestrator.kt`
  - `feature/chat/.../components/InputBar.kt`
  - `contracts/UI_DESIGN_SPEC.md`
- Gallery (read-only, `/tmp/gallery`)
  - `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/chat/ChatPanel.kt`
  - `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt`

---

## 1. Executive summary — top 3 blockers for buttery 60 FPS pin

1. **Two competing scroll mechanics fight each other.** The new-turn pin uses a 900 ms `withFrameNanos` linear `scrollTo` loop targeting `messageListScrollOffsetForLastUserAtTopPx` (sum of measured row heights **above** the last user). The bottom-spacer math (`messageListDynamicBottomPaddingPx`) is designed so that `scrollState.maxValue` already places the user at the top. The two targets are only equal when every row above the user is measured and stable. They diverge during the animation because (a) the assistant placeholder inserts 500 ms in and grows the spacer / `maxValue`, (b) ToolCallBanner / ThoughtForHeader chrome on the **previous** assistant turn relaxes from "Generating" to "Complete" (`MessageListItem.kt:183-202`) and remeasures, and (c) `dynamicBottomPaddingPx` is a `derivedStateOf` over a `mutableStateMapOf` height map — every per-row `onSizeChanged` writes the map and re-derives the spacer mid-animation. Pin ends at a stale offset and the prior assistant footer stays glued to the top edge (matches the screenshot description).
2. **The pin animation is linear, frame-driven, and ~3× longer than Gallery's.** `messageListSmoothScrollToOffset` (`MessageListScrollBehavior.kt:266-289`) drives `scrollState.scrollTo` every frame with `LinearEasing`-equivalent progress over 900 ms. Gallery uses one `animateScrollTo(maxValue, tween(600 ms, FastOutSlowInEasing))` (`ChatPanel.kt:304-313`, `scrollToBottom:719-732`). Linear easing alone reads as "steppy"; combined with mid-flight target shifts it reads as jagged.
3. **The IME is never actively dismissed and never coordinated with the scroll.** `InputBar.kt:248` uses `ImeAction.Default` with no `KeyboardActions`; `ChatScreen.kt` and the send delegates contain zero `clearFocus()` / `SoftwareKeyboardController.hide()`. Compose's `imePadding()` reshrinks the chat viewport when the keyboard *eventually* collapses (e.g. on tap-out), and that shrink happens **after** the pin animation, moving every row and undoing the pin. Gallery dismisses focus on send (`ChatPanel.kt:662-664`) **and** clears it on any downward `NestedScroll` (`ChatPanel.kt:271-284`). We do neither.

These three together produce: keyboard stays up → scroll starts → 500 ms later the placeholder + ack-banner growth shifts `maxValue` while a parallel `LaunchedEffect` is feeding `scrollTo` per frame → user finally taps to dismiss IME → viewport shrinks → the pin is now visibly off. Even on emulator with no IME interaction, items 1 + 2 alone are sufficient to reproduce the symptom.

---

## 2. Side-by-side: Gallery vs Pocket Crew

| Concern | Gallery `ChatPanel.kt` | Pocket Crew | Risk |
|---|---|---|---|
| List container | `Column { … } + verticalScroll(listState)` (`L347-354`) | `Column { … } + verticalScroll(scrollState)` (`MessageList.kt:150-156`) | Same; both OK for short chats |
| Height map | `mutableStateMapOf<Int, Int>` keyed by **index** (`L205, L399-402`) | `mutableStateMapOf<MessageId, Int>` (`MessageList.kt:112`) | Ours is more correct (stable across reordering); not a perf risk |
| `dynamicBottomPadding` | `derivedStateOf` over `itemHeights[lastUser..end]` (`L241-268`) | `derivedStateOf` over `itemHeightsById` slice (`MessageList.kt:115-130`) and `MessageListLayout.kt:93-110` | Same shape; equally invalidates per row size change |
| New-user scroll trigger | `LaunchedEffect(lastUserMessageIndex) { awaitFrame(); animateScrollTo(maxValue, tween 600 ms FastOutSlowIn) }` (`L304-313`) | `LaunchedEffect(lastUserMessageId, lastMessage.id, lastMessage.role) { messageListPinLastUserToTop(...) }` (`MessageListScrollBehavior.kt:78-89`) wraps `awaitMeasuredHeights → withFrameNanos loop → final snap` | Ours mixes two scroll engines and races layout |
| Assistant/loading row insert | Inserted **on Dispatchers.Default after `delay(500)`** *while inference primer runs* (`LlmChatViewModel.kt:133-144`) — totally off the scroll's critical path | Inserted exactly **500 ms after** `processPrompt` returns, via `InferenceOrchestrator.kt:127-135`. The pin animation is ~900 ms, so the insert lands **inside** the animation, in the same recomposition cycle as the scroll's `withFrameNanos` loop. | Direct cause of mid-animation spacer change |
| Open-thread initial scroll | not in this file (handled by message-source seeding) | `messageListScrollToConversationBottom` with double `scrollTo(max)` (`MessageListScrollBehavior.kt:118-132`) | Minor; second `scrollTo` is harmless but unnecessary |
| Catch-up to growing `maxValue` | none | `LaunchedEffect(lastUserMessageIndex)` + `snapshotFlow(maxValue, value, canScrollForward).debounce(32)` then `scrollTo(maxValue)` if at bottom (`MessageListScrollBehavior.kt:91-104`) | Mostly dormant for new-turn pin (because pin target ≠ bottom), but it does fire during tool-banner/streaming growth. Adds another writer to `scrollState.value` after pin completes. |
| IME / focus on send | `focusManager.clearFocus()` in `onSendMessage` (`L662-664`) | nothing. `ImeAction.Default`, no `KeyboardActions`, no `clearFocus` anywhere in `feature/chat` | Keyboard stays up; viewport shrinks at an unrelated time |
| IME / focus on scroll | `NestedScrollConnection.onPreScroll { if (available.y > 0) focusManager.clearFocus() }` (`L271-284`, plumbed via `nestedScroll`) | absent | Manual scroll-down also can't dismiss IME |
| `imePadding()` placement | inside the inner `Column` that contains *both* the scrollable area and the input (`L335-336`) | outer `Column` in `ChatScreen.kt:108-114` — scroll area + InputBar siblings, same outer column | Equivalent; not a regression |
| Bubble-shape clipping in scroll | bubbles use `Modifier.clip(MessageBubbleShape(...))` (cheap shape) | `MessageBubble` / `AssistantBubble` (not inspected here) plus per-row `padding(top=6, bottom=6)` and an extra outer `Column.padding(horizontal=10.dp)` (`MessageListItem.kt:53-58`) | Likely fine; flag if profiler shows it |
| Reserved/animated chrome | latency text and action button rows on user/assistant (`L529-565`) | Plus `MessageListItemIndicators` w/ a 1 Hz ticker `LaunchedEffect` (`MessageListItem.kt:113-119`), `ThoughtForHeader`, ToolCallBanner, reserved `Spacer(62.dp)` for `CREW` generating slot (`L33, 249-251`) | The 1 Hz `delay(1000)` recompose-while-active is acceptable; **the indicator transitioning from Generating → Complete on the previous turn rearranges layout above the new user row** — this is the row whose height is most likely to remeasure during the pin |

---

## 3. Confirmed bugs

Each item is a behavior the code does *today* that mathematically or empirically breaks the pin.

### B1. Pin target captured before all relevant rows are remeasured  
`messageListPinLastUserToTop` (`MessageListScrollBehavior.kt:138-172`) awaits heights only **through `lastUserMessageIndex`**. The *previous* assistant rows above are not awaited if their last-known height happens to still be set in `itemHeightsById`. But on send, those rows transition from `IndicatorState.Generating` → `IndicatorState.Complete` (`MessageListItem.kt:183-202`) which mounts/unmounts `ThoughtForHeader`, drops the `GeneratingIndicator`, possibly mounts the action row, and remeasures. The cached height becomes stale → `messageListScrollOffsetForLastUserAtTopPx` undershoots → pin lands with the prior footer still visible.

### B2. Assistant placeholder insertion races the pin animation  
`InferenceOrchestrator.sendMessage` (`InferenceOrchestrator.kt:106-135`):
- L106: set optimistic user, clear input
- L111: `chatUseCases.processPrompt(domainMessage)` (suspends on Room write — main-safe but suspends)
- L127: replace optimistic with persisted user (same id)
- **L129: `delay(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)` — 500 ms**
- L131-135: `createAssistantPlaceholder` (Room insert → Flow emits → MessageList grows by one row → `dynamicBottomPaddingPx` recomputes → `scrollState.maxValue` jumps)

Pin animation is `MESSAGE_LIST_NEW_TURN_SMOOTH_SCROLL_DURATION_MS = 900` (`MessageListScrollBehavior.kt:34`). The placeholder lands at ~500 ms into the 900 ms animation. Gallery sidesteps this entirely by inserting the loading row on `Dispatchers.Default` **after** a `delay(500)` that happens during model warm-up, **not** during the scroll animation (`LlmChatViewModel.kt:133-144`).

### B3. Linear per-frame `scrollTo` loop  
`messageListSmoothScrollToOffset` (`MessageListScrollBehavior.kt:266-289`) drives `scrollState.scrollTo(offset)` with `progress = elapsedMs / duration`. No easing. The framework's `animateScrollTo(value, tween(..., FastOutSlowInEasing))` would give equivalent jank-free movement, handle interruption correctly, and naturally re-evaluate `maxValue` because it animates *toward* a value (not a frozen integer captured before the animation). The current loop is also missing a `withFrameNanos` initial-frame correction — first frame can land with zero progress if `frameStartNanos` was captured one frame back.

### B4. `dynamicBottomPaddingPx` is a `derivedStateOf` over a `mutableStateMapOf`  
`MessageList.kt:115-130`. Every per-row `onSizeChanged` call writes `itemHeightsById[message.id] = size.height` (`MessageList.kt:162-166`). Each write invalidates the `derivedStateOf` for the bottom spacer → Spacer height recomposes → Column relayouts → `maxValue` updates → catch-up `snapshotFlow` (`MessageListScrollBehavior.kt:94-104`) re-evaluates. During the pin animation, this can fire many times per second.

### B5. The catch-up effect can fight the pin animation tail  
`LaunchedEffect(lastUserMessageIndex)` (`MessageListScrollBehavior.kt:91-104`) collects `(maxValue, value, canScrollForward)` and snaps to `maxValue` when at the bottom. After the pin's `settledOffset` snap (`MessageListScrollBehavior.kt:165-171`), if `dynamicBottomPaddingPx` math is consistent, `currentValue == maxValue` and `!canScrollForward`. But if B1 leaves the pin slightly short, the user appears partially below the top edge **and** `canScrollForward` is true (`maxValue > currentValue`), so the predicate `shouldMessageListSnapToGrowingMax` returns false — meaning the catch-up correctly does not move the pin. **However**, the very moment the placeholder inserts (B2), `maxValue` grows by the placeholder row height and the predicate flips to true *if* the user happened to already be at the new bottom — which the pin animation can put them at briefly. This produces an extra unrequested `scrollTo(maxValue)` after the pin nominally ends.

### B6. Keyboard is never dismissed in the send path  
`InputBar.kt:248` (`imeAction = ImeAction.Default`), no `KeyboardActions { onAny/onSend = { … } }`. `ChatScreen.kt` and the delegates do not invoke `focusManager.clearFocus()`. `imePadding()` (`ChatScreen.kt:113`) is correct, but `imePadding` only shrinks when the IME actually closes. Since nothing closes it on send, the pin animation runs with the input still focused — and any later collapse (tap-out, edit, mode change) shrinks the viewport, recomputes `dynamicBottomPaddingPx`, and visibly moves the pinned row.

### B7. UI spec drift (contracts vs runtime)  
`UI_DESIGN_SPEC.md:61-63` still declares the chat MessageList as a `LazyColumn` with `reverseLayout = false`. Runtime is `Column + verticalScroll`. Either the contract is wrong, or the implementation drifted. Worth resolving before any further refactor.

---

## 4. Hypotheses (need a trace / profile to confirm)

### H1. Multiple `LaunchedEffect`s are recomposing-then-cancelling each other  
The pin effect's keys are `(lastUserMessageId, messages.lastOrNull()?.id, messages.lastOrNull()?.role)` (`MessageListScrollBehavior.kt:78`). The moment the assistant placeholder lands, `messages.lastOrNull()?.id` changes from the user's id to the assistant's id, **the key changes, the LaunchedEffect cancels mid-loop**, and the early-return guard `if (lastMessage.role != MessageRole.User || lastMessage.id != lastUserMessageId)` aborts the new instance. Net effect: the pin animation gets cut off when the placeholder appears, leaving the scroll position wherever it happened to be at ~500 ms.

This is consistent with the reported symptom ("stops short").

### H2. `consumeWindowInsets` is missing under our top-padded outer column  
Gallery's outer column is `modifier.padding(innerPadding).consumeWindowInsets(innerPadding).imePadding()` (`ChatPanel.kt:336`). Pocket Crew uses `padding(top = innerPadding.calculateTopPadding()).imePadding()` (`ChatScreen.kt:108-113`) — no `consumeWindowInsets`. Without it, nested children may double-consume status-bar insets and recompute padding when the IME opens, contributing to layout jitter.

### H3. `mergedDbMessagesFlow` republishes the user message twice  
`ChatViewModel.kt:142-156` merges `optimisticUserMessage` with DB results. After Room confirms (L121-127 in orchestrator), `_optimisticUserMessage.value = persistedUserMessage` is reassigned to the same id — this still emits to the StateFlow because it is a new object (data class with `createdAt` updated). MessageList sees an emit with **identical content** and recomposes. If `ChatMessage` equality is not stable, items recompose, `onSizeChanged` fires, and `itemHeightsById` is rewritten with the same values. Cheap individually, but compound with the rest.

### H4. The `1 Hz` ticker on the previous assistant's indicator  
`MessageListItem.kt:113-119` runs `while (true) { … delay(1000L) }` while a thinking indicator is active. On a new send, the previous assistant's indicator typically already transitioned to `Complete`, so this ticker is dormant. But during the brief moment between the placeholder appearing and the next inference's first thinking token, both the previous-turn `Complete` indicator chrome and the new-turn indicator can be on screen. Confirm with a recompose counter.

### H5. Bubble shape and image-bubble layout on user rows  
Not inspected in this pass. If `UserBubble` renders an `Image` (avatar) or `AsyncImage`, its intrinsic-size pass can shift row height the frame after composition. Worth measuring with `Layout Inspector → Counts`.

---

## 5. Timeline (current behavior — what we actually do)

```
t=0   ms  user taps Send
          InputBar.onSend → ChatViewModel.sendMessage → InferenceOrchestrator.sendMessage
          ├─ _optimisticUserMessage = Message(...)          (StateFlow emit)
          ├─ onInputCleared()                                (text="")
          │                                                  IME: still open (B6)
          │                                                  ChatViewModel.uiState recomposes
          │                                                  MessageList recomposes with new user row
          ├─ chatUseCases.processPrompt(...)  (suspends, Room write)
t≈30  ms  user row laid out → onSizeChanged → itemHeightsById[userId] = h
          dynamicBottomPaddingPx derives new spacer
          maxValue jumps (Column relayout)
          MessageListScrollEffects:
            - LaunchedEffect(threadKey, messages.size)        (cancels/restarts; guarded by conversationBottomScrolled)
            - LaunchedEffect(lastUserMessageId, last.id, last.role)
                → messageListPinLastUserToTop
                   awaitMeasuredHeights(0..lastUserIdx, 400 ms)
                   awaitFrame()
                   targetOffset = sum(heights[0..lastUserIdx-1])     ← may be wrong (B1)
                   start withFrameNanos loop, duration 900 ms
            - LaunchedEffect(lastUserMessageIndex)
                → snapshotFlow(maxValue, value, canScrollForward).debounce(32)…
t≈80  ms  pin loop first scrollTo()
t≈150 ms processPrompt returns; orchestrator runs delay(500)
          (no UI work yet — but previous-turn IndicatorState transitions can fire if inference completed near this moment)
t≈650 ms createAssistantPlaceholder → Room insert → Flow emits
          MessageList grows by 1 row
          dynamicBottomPaddingPx re-derives → spacer shrinks
          maxValue grows (B2)
          LaunchedEffect(lastUserMessageId, last.id, last.role) key changes (last.id now assistant) → CANCEL (H1)
          The pin loop is gone. Position is wherever progress=(650-80)/900 ≈ 0.63 puts it.
          Catch-up effect debounce fires; if !canScrollForward → scrollTo(maxValue)  (B5)
t≈700 ms IndicatorState.EngineLoading or Processing mounts on new assistant
          its row height grows → spacer shrinks → maxValue grows
t≈… ms   user finally taps to dismiss IME (or taps another row)
          imePadding() shrinks → viewport shrinks → dynamicBottomPaddingPx re-derives → pin moves
```

### What Gallery does (`ChatPanel.kt` + `LlmChatViewModel.kt`)

```
t=0     user taps Send
        onSendMessage(...) → focusManager.clearFocus()      (IME dismiss kicked off immediately)
        curMessage = ""
        viewModel.generateResponse(...) launched on Dispatchers.Default:
          setInProgress(true); setPreparing(true)
          addMessage(ChatMessageLoading)                     ← NOT YET — addMessage runs on Default,
                                                              effect is async; in practice posts at next frame
          while (model.instance == null) delay(100)
          delay(500)                                          ← key: loading row insert blocked here
          [run inference]
t≈16    user row laid out → itemHeights[userIndex] = h
        dynamicBottomPadding derives spacer so maxValue places user at top
        LaunchedEffect(lastUserMessageIndex):
          awaitFrame()
          listState.animateScrollTo(maxValue, tween(600 ms, FastOutSlowIn))
        Pin animation runs uninterrupted — IME has already closed (viewport stable),
        no placeholder insert during the 600 ms.
t≈616   animation ends; user row top-aligned.
t≈700+  loading row inserts; spacer shrinks; maxValue grows; user is already pinned
        — additional growth pushes content below, no upward scroll required.
```

ASCII delta — note the three concurrent events squeezed into our 900 ms window vs Gallery's single 600 ms window with no concurrent events.

---

## 6. Compose best-practice scorecard

For each item, P (Pocket Crew) vs target. ❗ = violation, ⚠ = risk, ✓ = OK.

| # | Topic | Status | Note |
|---|---|---|---|
| 1 | `Column + verticalScroll` for full message list (no virtualization) | ⚠ | Same as Gallery for now. Fine ≤ ~50 short messages. **For long chats LazyColumn is the right structure** (see §8). The current pin/jank problem is **not caused** by the Column choice; LazyColumn is a separate, larger refactor. |
| 2 | `ScrollState.scrollTo` per frame as animation engine | ❗ | Use `animateScrollTo(target, tween(..., FastOutSlowIn))`. Per-frame `scrollTo` is for cases where the target itself is moving each frame; we have a stable target. |
| 3 | Synchronizing scroll with IME animation | ❗ | No `WindowInsets.ime` reads, no `Modifier.imeNestedScroll`, no `bringIntoViewRequester`, no wait for IME inset settle. Start of pin should be gated on IME being closed (or, simpler, just close it explicitly before scrolling — Gallery's choice). |
| 4 | Minimizing recomposition during scroll | ⚠ | `derivedStateOf` on a `mutableStateMapOf` invalidates per height change; can't be avoided cheaply with current container choice but **freeze the spacer height during the pin animation** to break the read graph. |
| 5 | Multiple `LaunchedEffect`s on the same ScrollState | ❗ | Three concurrent effects (initial scroll, pin, catch-up). Two will fire on every new turn. **Consolidate to one effect keyed on the send event.** |
| 6 | `onSizeChanged` per row driving a global derived state | ⚠ | Acceptable if writes are cheap, but combined with item 5 and the long pin window, this is layout thrash territory. Consider writing to a non-State map (`SnapshotStateMap` is already State-backed) or batching writes via `derivedStateOf { itemHeightsById.size to itemHeightsById.values.sum() }` with `Snapshot.withMutableSnapshot`. |
| 7 | Pin via "set offset once after stable layout" vs multi-second animation | ❗ | A 600 ms tween that scrolls to a moving `maxValue` and naturally settles when layout settles is better than capturing a frozen offset before layout settles. |
| 8 | Profiling | – | No baseline. Need Macrobenchmark `MessageListSendBenchmark` (`StartupBenchmark` style) + Compose Recomposition Counts + Perfetto trace on `frame_drawn` markers around `Send`. |
| 9 | Stable keys for items | ✓ | We key heights by `MessageId`; items don't use explicit `key()` because `Column.forEachIndexed` doesn't support it, but with `Column` it does not matter for correctness. Would matter if we move to `LazyColumn`. |
| 10 | Stability annotations | ? | Did not audit `ChatMessage`. If it carries non-stable fields (lambdas, raw maps), every recompose churns. Worth a pass. |

---

## 7. Prioritized fix backlog

### P0 (do these first — they explain the symptom)

P0-1. **Use `animateScrollTo(maxValue, tween(durationMillis = 600, easing = FastOutSlowInEasing))` for the new-turn pin** instead of `messageListSmoothScrollToOffset` against a precomputed offset. (`MessageListScrollBehavior.kt:138-172`). Drop `messageListAwaitMeasuredHeights` for the user-pin path — the bottom spacer is already sized to make `maxValue` correct. Cost: small. Risk: low.

P0-2. **Delay the assistant placeholder past the pin animation.** Either bump `ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS` to ≥ pin duration + a frame (e.g. 650 ms), or — cleaner — gate `createAssistantPlaceholder` on a "scroll settled" signal exposed by `MessageListScrollEffects`. Even cleaner, follow Gallery: insert the placeholder **on the inference scope after model warm-up**, not on the orchestrator's send scope. Cost: small. Risk: cross-cuts ticket A's tests — keep the change in `InferenceOrchestrator.kt:127-135`.

P0-3. **Dismiss the IME on send.** Add `KeyboardActions(onSend = { … })` + `imeAction = ImeAction.Send` to `InputBar.kt:240-249` **or** call `focusManager.clearFocus()` / `LocalSoftwareKeyboardController.current?.hide()` from `InputBar`'s send callback. Do this **before** the orchestrator runs, so `imePadding()` finishes its shrink before the pin starts. Cost: small. Risk: minor — verify mic / attachment flows don't lose focus unintentionally.

P0-4. **Stop the pin `LaunchedEffect` from being cancelled by the placeholder insert.** Change its keys from `(lastUserMessageId, messages.lastOrNull()?.id, messages.lastOrNull()?.role)` to **just `lastUserMessageId`** (`MessageListScrollBehavior.kt:78`). The role/last-id guards inside the effect are sufficient to no-op when not appropriate; we don't need to restart on every list mutation. This single change closes H1.

### P1 (do these once P0 lands)

P1-1. **Remove the catch-up `LaunchedEffect`** (`MessageListScrollBehavior.kt:91-104`) for the new-turn path, or scope it strictly to "user is reading streaming output and is already at the bottom". Today it can fire after a successful pin and re-snap to a newly-grown `maxValue`.

P1-2. **Freeze the spacer during the pin.** Hoist `dynamicBottomPaddingPx` into a `var rememberedFrozenSpacer: Int?` that is non-null for the lifetime of the pin animation. On entry, snapshot; on exit, release. Re-uses Compose's `Snapshot.withMutableSnapshot` to avoid extra recomposes. Alternative: capture heights into a local `IntArray` for the duration. (Note: this is only worth doing if P0-2 doesn't already eliminate the mid-flight `maxValue` jump.)

P1-3. **Match Gallery's downward-scroll focus clear.** Add a `NestedScrollConnection { onPreScroll(available, _) { if (available.y > 0) focusManager.clearFocus(); Offset.Zero } }` on the scroll container in `MessageList.kt:150-156` (`ChatPanel.kt:271-284` is the template). Pairs with P0-3 for full IME polish.

P1-4. **Resolve UI spec drift.** Either update `UI_DESIGN_SPEC.md:61-63` to reflect `Column + verticalScroll`, or schedule the LazyColumn migration (P2-1).

P1-5. **Drop the second `scrollTo(scrollState.maxValue)` in `messageListScrollToConversationBottom`** (`MessageListScrollBehavior.kt:128-132`). One call after `awaitFrame()` is enough.

P1-6. **Remove debug logging from the orchestrator's hot path.** `InferenceOrchestrator.kt:225-229` logs a `Map { take(4) }` on every ack update; the allocation is small but it's in the same recompose neighborhood as MessageList's draw cycle and is noise. Confirm with a profile before deleting.

### P2 (architecture / longer term)

P2-1. **Migrate MessageList to `LazyColumn` with stable keys.** Use `rememberLazyListState`, `state.layoutInfo.visibleItemsInfo`, and `animateScrollToItem(index, scrollOffset = 0)` for the pin (offset 0 places item top at viewport top — the Gallery effect without any spacer math). This eliminates the height map, the bottom-spacer math, and the catch-up effect entirely. It also fixes recomposition cost on chats > ~50 messages. **Only do this after P0 + P1 confirm the symptom is gone** — otherwise it's hard to tell what fixed what.

P2-2. **Macrobenchmark.** Add `MessageListSendBenchmark` running in `:feature:chat:macrobenchmark` (new module) that scrolls to bottom, sends a fixed string, and reports `frameDurationCpuMs` p99. Wire it into the per-module CI gate.

P2-3. **Compose Recomposition Counts in debug builds.** A `MessageListRecompositionScope` debug-only `Modifier.recomposeHighlighter()` (well-known helper) overlay in `feature/chat` previews. Helps catch H3-type regressions.

---

## 8. Architecture recommendation — `LazyColumn` vs `Column`

**Short answer:** keep `Column + verticalScroll` for the P0/P1 fix, plan `LazyColumn` as a separate P2 ticket.

Reasoning:
- The symptom is **not** caused by all-items-composed cost. With 50 messages on a modern phone, full composition is < 5 ms. The symptom is concurrent effect/state churn during a 900 ms window. Fixing the effect (P0-1, P0-4) eliminates the symptom regardless of container.
- `LazyColumn` is the right long-term container: virtualization, native `animateScrollToItem(index, scrollOffset = 0)` for pin-to-top, no manual height map, no bottom-spacer math. It also makes "scroll up while streaming" feasible without the catch-up hack.
- The contract (`UI_DESIGN_SPEC.md:61-63`) already calls for `LazyColumn`. The migration restores contract alignment.
- Why not now? `LazyColumn` changes how `onSizeChanged`-based features (ScrollToBottomFab visibility, tool banner placement, ThoughtForHeader sizing) are wired. Doing it under the same PR as the pin fix is high-risk; isolate.

---

## 9. Minimal-change set to feel Gallery-faithful

If you want one PR that buys ~90% of Gallery's perceived smoothness with minimal blast radius:

1. Replace the body of `messageListPinLastUserToTop` with:
   ```kotlin
   awaitFrame()
   scrollState.animateScrollTo(
       value = scrollState.maxValue,
       animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
   )
   ```
   (Keep the function signature so tests / callers don't move.)
2. Change the pin `LaunchedEffect`'s key list to `LaunchedEffect(lastUserMessageId)` only (`MessageListScrollBehavior.kt:78`).
3. In `InputBar`'s send callback path, call `LocalSoftwareKeyboardController.current?.hide()` and `focusManager.clearFocus()` **before** invoking `onSend(...)`.
4. Bump `ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS` to ~650 ms so the placeholder insert lands **after** the pin animation finishes.

That's the four-line equivalent of Gallery's setup. Items 5+ from §7 are polish.

---

## 10. Minimal reproduction

Emulator: Pixel 7, API 35, Android 16.

1. Open a chat with ≥ 20 messages, where the last assistant turn finished and shows `ThoughtForHeader` + action row (share/copy/time).
2. Tap the input field (keep IME open).
3. Type a short message (1-3 words).
4. Tap Send.
5. Watch the new user bubble. Expected: top-aligned to viewport. Actual: prior assistant's footer remains at the top, new user bubble visibly lower; the scroll animation feels staircased rather than smooth; if you tap to dismiss the keyboard after the animation, the pin visibly drifts.

---

## 11. Open questions answered

1. **Is 900 ms linear per-frame `scrollTo` inherently janky?** Not because `scrollTo` is expensive — `scrollTo` only updates `ScrollState.value`; the Column does **not** relayout per `scrollTo`. It's janky because (a) linear easing reads as steppy at this duration, (b) the target captured at t=0 is wrong by the time t=900 ms arrives (B1, B2), and (c) the effect can be cancelled mid-flight (H1).
2. **Should the pin start after IME settles?** Yes — but the easier path is to dismiss IME *before* invoking send (Gallery's approach). Detecting IME settle robustly requires `WindowInsetsCompat.toWindowInsets()` or `imeAnimationTarget == imeAnimationSource`, which is doable on API 30+ but adds moving parts. Dismiss-first is simpler and matches the reference.
3. **Should we freeze `dynamicBottomPadding` during the pin?** Only if P0-2 doesn't ship. If the placeholder is deferred past the pin and no row above the user can grow during the pin, the spacer is naturally stable.
4. **Is full-`Column` wrong for 100+ messages?** Yes for perf and memory; no for the specific jank we're seeing. P2-1.
5. **Minimum Gallery-faithful change set?** §9 above.

---

## 12. Test-spec hooks (for the next implementer)

Add to `feature/chat/src/test/kotlin/com/browntowndev/pocketcrew/feature/chat/components/messagelist/` (JUnit 5 per `AGENTS.md` §3.3):

- `MessageListPinAnimationTest` — given a scroll state and known heights, the animation should reach `maxValue` within `tween(600).duration + 1 frame`; intermediate values must be monotonically increasing.
- `MessageListPinNotCancelledByPlaceholderTest` — assert the pin effect's key change does not abort an in-flight pin when an assistant placeholder is appended. (Today: fails.)
- `InferenceOrchestratorPlaceholderDelayTest` — assert `createAssistantPlaceholder` is invoked no earlier than `ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS` and that increasing the constant is observable downstream.
- `InputBarDismissesImeOnSendTest` — Robolectric or Compose UI test asserting `LocalSoftwareKeyboardController.hide()` / focus cleared before `onSend` lambda fires.

UI-level: a macrobenchmark `MessageListSendBenchmark` would catch the visible jank regression but is P2.

---

## 13. Do-NOT list (per the handoff brief)

- Do not flip `reverseLayout = true`.
- Do not introduce DB columns for transient scroll/IME state.
- Do not change production code until this report is approved.
- Do not suggest replacing `Column` with `LazyColumn` in the same PR as the pin fix.
