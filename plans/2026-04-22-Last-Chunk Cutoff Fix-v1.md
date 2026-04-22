# Last-Chunk Cutoff Bug — Diagnostic Analysis & Fix Plan

## Symptom

The last chunk of LLM text frequently gets cut off during live streaming, but navigating away and back shows the full message. This means Room has the complete data — the live streaming pipeline is losing the final emission somewhere.

## Root-Cause Analysis

After tracing the full data flow from `GenerateChatResponseUseCase` → `ActiveChatTurnStore` → `ChatViewModel.combine` → `projectChatMessages` → `StreamableMarkdownText`, I identified **three interacting bugs** that form the cutoff:

---

### Bug 1: `StreamableMarkdownText` LaunchedEffect exits before final target is rendered (PRIMARY CAUSE)

**Location:** `core/ui/src/main/kotlin/com/browntowndev/pocketcrew/core/ui/component/markdown/StreamableMarkdownText.kt:64-106`

**The Problem:**

```kotlin
// Line 64: If isStreaming == true, displayedText starts as ""
var displayedText by remember { mutableStateOf(if (isStreaming) "" else markdown) }

// Line 72-106: LaunchedEffect key is `isStreaming`
LaunchedEffect(isStreaming) {
    if (!isStreaming) {
        return@LaunchedEffect  // <-- Key: exits immediately when isStreaming flips to false
    }
    while (isActive) {
        // typewriter animation loop
    }
}
```

**The critical race:**

1. During streaming, `isStreaming = true`, `LaunchedEffect` runs the typewriter loop, advancing `displayedText` toward `targetText` character-by-character.
2. The last chunk arrives → `targetText` (= `markdown` param) updates to the full value.
3. Almost simultaneously (or even before the typewriter catches up), the generation completes. The `MergeMessagesUseCase` sees Room has `COMPLETE` → projects the DB message → `indicatorState` becomes `Complete` → `isStreaming` flips to `false`.
4. When `isStreaming` becomes `false`, Compose **recomposes** `StreamableMarkdownText`. During this recomposition:
   - Line 68: `if (!isStreaming && displayedText != targetText) { displayedText = targetText }` — this *should* snap, but **doesn't always fire before the LaunchedEffect is cancelled**.
   - The `LaunchedEffect(isStreaming)` is **re-launched** with the new key `false`. But line 73-74: `if (!isStreaming) return@LaunchedEffect` — it **exits immediately**.
5. Here's the subtle Compose timing issue: `rememberUpdatedState(markdown)` at line 63 captures the *latest* `markdown` into `targetText`. But `displayedText` is a `remember`-ed state — it's **not reset** during recomposition. It retains whatever partial value it had from the typewriter loop.

6. **The snap line 68-69** runs during composition, which should set `displayedText = targetText`. But there's a critical problem: the `targetText` read at line 63 uses `rememberUpdatedState`, which captures the *next* `markdown` value. However, if the `markdown` prop and `isStreaming` prop flip in the **same composition frame** (which they can, since both derive from the same `uiState` emission), the old `targetText` reference may still be lagging. Actually `rememberUpdatedState` *should* always have the latest... but the real issue is subtler:

7. **The real issue:** When the handoff happens, `projectChatMessages` switches from using the active snapshot message (with `messageState = GENERATING`) to using the DB message (with `messageState = COMPLETE`). The DB message from Room might have slightly **different content** than the last active snapshot because `PersistAccumulatedChatMessagesUseCase.sanitizePersistedContent()` strips tool call/result traces. So `displayedText` (which was chasing the unsanitized snapshot content) could be *longer* than or *not start with* the new sanitized `targetText` (the DB content). This causes line 84's check `target.length < displayedText.length || !target.startsWith(displayedText)` to be true — so line 86 snaps `displayedText = target`. BUT the `LaunchedEffect` has already exited. The **composition frame that runs the snap and the LaunchedEffect cancellation happen in a non-obvious order**: if `displayedText` is set during composition but the old LaunchedEffect (from `isStreaming = true`) hasn't yet received the `isStreaming = false` recomposition, the typewriter loop might overwrite the snap on its next iteration.

Actually, re-examining: the most common scenario is simpler. The `isStreaming` and `markdown` change in the same recomposition. Line 68-69 runs in composition (synchronously) and sets `displayedText = targetText`. The `LaunchedEffect(isStreaming)` is cancelled (old key) and restarted (new key = false), which returns immediately. This *should* work. **BUT** there's a gap:

**The actual typewriter animation gap:** During normal streaming, `displayedText` lags behind `targetText` by up to 500 characters (lines 89-96). When `isStreaming` suddenly flips to `false`:
- If the last `targetText` update and `isStreaming=false` arrive in the *same* composition, line 68-69 snaps `displayedText` correctly.
- If they arrive in **separate compositions** (the more common real-world scenario due to `combine` producing separate emissions), then:
  - Frame N: `targetText` updates to final value, `isStreaming` still `true`. The `LaunchedEffect` continues the typewriter loop, advancing `displayedText` toward the final target.
  - Frame N+1: `isStreaming` flips to `false`. Line 68-69 snaps `displayedText = targetText`. ✓ This works.

  **But what if the acknowledgement/handoff causes `activeTurnSnapshotFlow` to emit `null` before the DB has caught up?** Then:

  Frame N: Last snapshot arrives with full text, `isStreaming = true`.
  Frame N+1: Acknowledgement fires → `activeTurnKeyFlow` becomes `null` → `activeTurnSnapshotFlow` emits `(key=null, snapshot=null)` → `projectChatMessages` now only has DB messages. **If the DB is still showing the placeholder (GENERATING, empty content)**, the `MergeMessagesUseCase` returns the DB message (since in-flight is gone). The projected message now has **empty content** and `messageState = GENERATING`. So `isStreaming` remains true but `markdown` becomes empty. Then `StreamableMarkdownText` returns early at line 60: `if (markdown.isEmpty()) return`.

  Frame N+2: Room finally emits the COMPLETE message with full content. `markdown` now has content again, but `isStreaming = false` (COMPLETE state). Line 68-69 snaps. **This works, but there was a one-frame gap where the message disappeared entirely.**

**HOWEVER**, the most likely scenario for "last chunk cut off" specifically is:

The `activeTurnSnapshotFlow` snaps to the terminal (COMPLETE) snapshot *before* the ViewModel acknowledges the handoff. During this window, the snapshot contains the full text but `messageState = COMPLETE`. The `indicatorState` becomes `Complete`, so `isStreaming = false`. But `displayedText` was mid-typewriter at some prefix. The snap at line 68-69 *should* catch this... unless there's a problem with `rememberUpdatedState`.

**The real smoking gun: Line 63 + Line 68 interaction.**

```kotlin
val targetText by rememberUpdatedState(markdown)  // line 63
...
if (!isStreaming && displayedText != targetText) {  // line 68
    displayedText = targetText  // line 69
}
```

`rememberUpdatedState` updates `targetText` to the *new* markdown on every recomposition. Line 68-69 runs during composition. This is correct **as long as `markdown` is non-empty**.

**BUT if the handoff creates an intermediate state where `markdown` is empty** (the gap between snapshot retirement and DB emission), then:
- `targetText` becomes `""`
- Line 60: `if (markdown.isEmpty()) return` — **the composable returns early, never executing line 68-69**
- `displayedText` retains its old partial value in the `remember` block
- When the DB emission arrives and `markdown` becomes the full text, the composable is called again:
  - `isStreaming = false`, `markdown = "full text"`
  - Line 64: `var displayedText by remember { mutableStateOf(if (isStreaming) "" else markdown) }` — **`remember` preserves the OLD partial value**. The `if (isStreaming) "" else markdown` initializer only runs on first composition, not on recomposition.
  - Line 68: `!isStreaming && displayedText != targetText` → true → `displayedText = targetText` → snaps to full text.

So this particular gap scenario should self-heal on the next frame. But it causes a visible flicker (one frame of empty, then full text).

**The real race that causes cutoff: The `activeTurnSnapshotFlow` going null while DB hasn't emitted COMPLETE yet.**

---

### Bug 2: Handoff acknowledgement can race ahead of Room's COMPLETE emission

**Location:** `ChatViewModel.kt:430-458` (`acknowledgeHandoffIfNeeded`)

**The Problem:**

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
    if (!handoffAcknowledgementsInFlight.add(key)) return

    viewModelScope.launch {
        activeChatTurnSnapshotPort.acknowledgeHandoff(key)  // <-- Retires the snapshot!
        _acknowledgedTurnKeys.update { keys -> keys + key }
        ...
    }
}
```

This is called from inside the `combine` lambda (line 404). The flow of events:

1. `GenerateChatResponseUseCase` finishes → `finally` block calls `persistAccumulatedMessages` → Room writes COMPLETE with full content.
2. Room emits updated messages via `dbMessagesFlow`.
3. `combine` fires with the new DB messages (including COMPLETE). The `activeTurnSnapshotFlow` still has the snapshot (with full text, COMPLETE state).
4. `projectChatMessages` projects the DB message (since `dbMessage.messageState == COMPLETE` → `MergeMessagesUseCase` returns `dbMessage`). ✓ Good — full text is visible.
5. `acknowledgeHandoffIfNeeded` fires, sees `completedDbIds` contains the assistant message ID, launches `acknowledgeHandoff(key)`.
6. `acknowledgeHandoff` calls `retire(key)` which sets the entry's flow to `null` and removes the entry.
7. This causes `activeTurnSnapshotFlow` to emit `(key, null)`.
8. `activeTurnKeyFlow` detects the key is in `acknowledgedTurnKeys` and transitions to `null`.
9. `activeTurnSnapshotFlow` emits `(null, null)`.
10. `combine` fires again. `projectChatMessages` now has no active snapshot, only DB messages. Since DB message is COMPLETE with full content, this is fine. ✓

**This path actually works correctly.** The problem is when the persistence happens *non-atomically* with the snapshot publication:

---

### Bug 3: The snapshot terminal publication happens BEFORE DB persistence in `GenerateChatResponseUseCase`

**Location:** `GenerateChatResponseUseCase.kt:87-129`

**The critical sequence:**

```kotlin
baseFlow.collect { state ->
    // ...
    val accumulatedMessages = accumulatorManager.reduce(state)
    if (!backgroundInferenceEnabled) {
        activeChatTurnSnapshotPort.publish(
            key = ActiveChatTurnKey(chatId, assistantMessageId),
            snapshot = accumulatedMessages,  // <-- publishes COMPLETE snapshot
        )
    }
    emit(accumulatedMessages)
}
// finally block:
withContext(NonCancellable + Dispatchers.IO) {
    // ... persistAccumulatedMessages runs HERE, after the flow completes
    persistAccumulatedMessages(accumulatorManager)
}
```

When `MessageGenerationState.Finished` arrives:
1. `accumulatorManager.reduce(Finished)` → sets `isComplete = true`, `currentState = COMPLETE`. Returns `AccumulatedMessages` with the full accumulated text.
2. `activeChatTurnSnapshotPort.publish()` pushes the **terminal** snapshot (COMPLETE with full content) to the store.
3. The store's `publish` method calls `mergeAccumulatedMessages`, then sees `isTerminalSnapshot()` → calls `scheduleTerminalCleanup(key, entry)` with a 5-minute delay.
4. `emit(accumulatedMessages)` emits to the flow collector (which is the `inferenceJob` in `ChatViewModel.onSendMessage`, which does nothing with the emission now — `.onEach { }`).
5. The `collect` lambda finishes (no more events). The `finally` block runs.
6. `persistAccumulatedMessages` writes to Room.

**The race:** Between steps 2 and 6, the `activeTurnSnapshotFlow` already has the terminal (COMPLETE) snapshot. The `ChatViewModel.combine` fires with this snapshot. The `projectChatMessages` merge logic sees: `dbMessage.messageState != COMPLETE` (because Room hasn't been written yet — the DB row is still a placeholder with `GENERATING` state and empty/minimal content). So `MergeMessagesUseCase` returns `inFlightMessage.copy(createdAt = dbMessage.createdAt)` — which has the **full text from the snapshot**. 

**This is correct during the window.** The full text IS visible from the snapshot. ✓

But then: `acknowledgeHandoffIfNeeded` checks: does `dbMessages` contain a COMPLETE message for this ID? **No, not yet.** So it doesn't acknowledge. ✓ No premature acknowledgement.

Then Room writes (step 6). Room emits. `dbMessagesFlow` fires with COMPLETE. `combine` fires. `projectChatMessages` now uses the DB COMPLETE message. `acknowledgeHandoffIfNeeded` acknowledges. Snapshot retires. Everything flows correctly. ✓

**Wait — so where's the actual bug?** The bug is in `ActiveChatTurnStore.scheduleTerminalCleanup`:

Line 46-51 of `ActiveChatTurnStore.kt`:
```kotlin
if (entry.flow.value.isTerminalSnapshot()) {
    scheduleTerminalCleanup(key, entry)  // <-- schedules after 5 min
} else {
    cleanupJobs.remove(key)?.cancel()
}
```

After publishing the terminal snapshot, `scheduleTerminalCleanup` is called. But this just schedules a 5-minute cleanup, it doesn't do anything immediate. This is fine.

**The actual last-chunk cutoff mechanism — the real race:**

Let me re-examine `activeTurnKeyFlow` more carefully.

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

The `scan` operator:
- Starts with `null`.
- On each emission from `combine`, it receives `(candidate, acknowledgedKeys)`.
- `candidate` is `ActiveTurnCandidate(chatId, messages, key)` where `key` comes from `activeTurnCandidateFlow`.

Let's trace `activeTurnCandidateFlow`:
```kotlin
private val activeTurnCandidateFlow = combine(
    activeChatIdFlow,
    dbMessagesFlow,
    _requestedTurnKey,
) { chatId, messages, requestedTurnKey ->
    val incompleteKey = chatId?.let { id ->
        messages
            .lastOrNull { message ->
                message.role == Role.ASSISTANT && message.messageState != MessageState.COMPLETE
            }
            ?.let { message -> ActiveChatTurnKey(id, message.id) }
    }
    val requestedKey = requestedTurnKey?.takeIf { key ->
        key.chatId == chatId &&
            messages.none { message ->
                message.id == key.assistantMessageId && message.messageState == MessageState.COMPLETE
            }
    }
    ActiveTurnCandidate(
        chatId = chatId,
        messages = messages,
        key = requestedKey ?: incompleteKey,
    )
}
```

**THE BUG:** When `dbMessagesFlow` emits the COMPLETE message (step 6 above), `activeTurnCandidateFlow` recomputes:
- `incompleteKey`: looks for last assistant message where `messageState != COMPLETE`. The just-completed message is now COMPLETE, so `incompleteKey = null` (unless there's another incomplete assistant message).
- `requestedKey`: `_requestedTurnKey` was set when the turn started, but the `takeIf` filter checks `messages.none { message.id == key.assistantMessageId && message.messageState == COMPLETE }` — this fails because the message IS now COMPLETE. So `requestedKey = null`.
- `key = requestedKey ?: incompleteKey = null`

Now the `scan` in `activeTurnKeyFlow` receives `candidate.key = null`:
- `candidateKey = null?.takeUnless { ... } = null`
- We hit: `currentKey != null && candidate.chatId == currentKey.chatId && candidate.messages.any { message -> message.id == currentKey.assistantMessageId && message.messageState == MessageState.COMPLETE } -> currentKey`
- This keeps `currentKey` alive! ✓ Good — it recognizes that Room has COMPLETE for the active key and keeps the key.

**But wait**: when `_acknowledgedTurnKeys` is updated (after acknowledgement fires), the `scan` re-runs:
- `currentKey != null && currentKey in acknowledgedKeys -> null`
- `activeTurnKeyFlow` becomes `null`.
- `activeTurnSnapshotFlow` emits `(null, null)`.
- `combine` fires one more time with no snapshot — but Room has COMPLETE, so `projectChatMessages` returns the DB message. ✓

**This looks correct.** So what's causing the cutoff?

---

### Actually: The REAL root cause is in `projectChatMessages` line 24-27

```kotlin
val mergedMessages = dbMessagesMap.mapValues { (id, dbMessage) ->
    val activeMessage = activeMessagesMap[id]
    mergeMessagesUseCase(dbMessage, activeMessage) ?: dbMessage
}
```

And `MergeMessagesUseCase`:
```kotlin
return if (dbMessage.messageState == MessageState.COMPLETE) {
    dbMessage  // DB has final state, use it
} else {
    inFlightMessage.copy(createdAt = dbMessage.createdAt)  // use live
}
```

**The last-chunk cutoff happens when the terminal snapshot publishes with `messageState = COMPLETE` but the DB message is still incomplete (not yet persisted). Here's the exact sequence:**

Frame 1: `GeneratingText("final chunk")` → accumulator appends → publish snapshot with `messageState = GENERATING`, full content.
Frame 2: `Finished` → accumulator sets `isComplete = true`, `messageState = COMPLETE` → publish snapshot with `messageState = COMPLETE`, full content.
Frame 3 (before Room write): `dbMessagesFlow` still has placeholder (`GENERATING` state, minimal content). `activeTurnSnapshotFlow` has snapshot with `COMPLETE` state.
  - `projectChatMessages`: `dbMessage.messageState = GENERATING` ≠ `COMPLETE` → returns `activeMessage.copy(createdAt = dbMessage.createdAt)` → ✓ full content visible.
Frame 4: Room writes COMPLETE. `dbMessagesFlow` emits COMPLETE with full content.
  - `projectChatMessages`: `dbMessage.messageState = COMPLETE` → returns `dbMessage` → ✓ full content visible.
  - `acknowledgeHandoffIfNeeded` fires → launches acknowledgement.

This seems fine... **unless the `activeTurnKeyFlow` drops the key before Room catches up.**

Let me re-examine: after the terminal snapshot publishes, `ActiveChatTurnStore.scheduleTerminalCleanup` fires but it's 5 minutes delayed, so no problem there.

What about when `activeTurnCandidateFlow` re-derives `key`? The `incompleteKey` is derived from `dbMessagesFlow`. If the DB placeholder still has `GENERATING` state, `incompleteKey` still points to this assistant message. `requestedKey` is also valid. So `key` is non-null. `activeTurnKeyFlow` keeps the key. The snapshot observation continues. ✓

**OK, I was overthinking this. Let me look at the actual cutoff from the `StreamableMarkdownText` side more carefully.**

### The Actual Bug: `StreamableMarkdownText` typewriter animation can't catch up to the final content before `isStreaming` flips

When `isStreaming` flips from `true` to `false`, this happens because `indicatorState` changed from `Generating` to `Complete`. This means `messageState` changed from `GENERATING` to `COMPLETE`.

**The problem:** The transition from GENERATING → COMPLETE happens in the **same** `AccumulatedMessages` snapshot publication. Step 2 above: `Finished` event causes the accumulator to set `messageState = COMPLETE` on the same snapshot that has the full text. So the ViewModel gets a single combine emission where:
- `activeTurnSnapshot` has `messageState = COMPLETE` with full text
- OR `dbMessage` has `messageState = COMPLETE` with full text

In either case, `projectChatMessages` returns a message with full text and state = COMPLETE. The `indicatorState` becomes `Complete`, so `isStreaming = false`. And `markdown` = full text.

In `StreamableMarkdownText`, this recomposition:
- `targetText` captures the full text (via `rememberUpdatedState`)
- `displayedText` still has the partial typewriter value from `remember`
- Line 68-69: `!isStreaming && displayedText != targetText` → true → `displayedText = targetText` ✓

**But what if the previous composition (with `isStreaming = true`) had a `markdown` value that was the full text (last snapshot with GENERATING), and the typewriter loop was running? At this point `displayedText` was some prefix of the full text. Then the NEXT composition flips `isStreaming = false`. Line 68-69 snaps. This SHOULD work.**

**WAIT — I think I found it. The issue is with `rememberUpdatedState` and the LaunchedEffect's snapshot read.**

Look at line 78:
```kotlin
val target = targetText
```

This reads `targetText` which is a `rememberUpdatedState` reference. The value updates on every recomposition. But the **LaunchedEffect coroutine** reads this at `delay(15)` intervals. If between one `delay(15)` and the next, the composition happens twice (once with `isStreaming = true`, once with `isStreaming = false`), the second composition cancels the LaunchedEffect with key `isStreaming = true` and starts a new one with key `isStreaming = false` that returns immediately.

The snap at line 68-69 happens during the second composition. `displayedText` is set to `targetText`. ✓

**BUT** — what if there's a composition frame where `markdown` goes EMPTY? This happens if the active snapshot is retired but the DB message hasn't arrived yet. In this case, `StreamableMarkdownText` returns at line 60 (`if (markdown.isEmpty()) return`), and `displayedText` *never gets snapped*. The `remember` block retains the old partial `displayedText`.

When the DB emission eventually arrives with the full text, `StreamableMarkdownText` is called again:
- `isStreaming = false` (COMPLETE state from DB)
- `markdown = "full sanitized content"`
- `displayedText` = old partial value from `remember`
- Line 68-69: `displayedText = targetText` → snaps to full content. ✓ This works.

**But there IS a visible flicker: the message disappears for one frame and reappears.** This matches the "last chunk cut off" description — the user sees the text stop advancing mid-typewriter, the message might vanish briefly, then it comes back fully.

Actually, re-reading the user description: "the last chunk of text that comes from the LLM frequently gets cut off." This sounds more like the typewriter simply stops before reaching the end and never catches up — not a brief flicker.

**Let me re-examine the LaunchedEffect cancellation more carefully.**

When `isStreaming` changes from `true` to `false`:
1. The old `LaunchedEffect(isStreaming = true)` coroutine is **cancelled**.
2. But first, composition runs. Line 68-69 sets `displayedText = targetText`.
3. Then the new `LaunchedEffect(isStreaming = false)` launches and returns immediately.

The question is: **does composition's assignment at line 69 take effect before or after the old LaunchedEffect is cancelled?**

In Compose, composition is synchronous (on the main thread). `LaunchedEffect` coroutines run on `CoroutineScope(rememberCoroutineScope())`. The cancellation happens during the composition/recomposition lifecycle. The composition assigns `displayedText = targetText` synchronously. The old coroutine is cancelled after composition. **So line 69's assignment is valid.** The `displayedText` should have the full text.

**WAIT — There's a subtle bug I missed!**

Look at line 63-64:
```kotlin
val targetText by rememberUpdatedState(markdown)
var displayedText by remember { mutableStateOf(if (isStreaming) "" else markdown) }
```

Line 68-69:
```kotlin
if (!isStreaming && displayedText != targetText) {
    displayedText = targetText
}
```

**If `markdown` is EMPTY during the isStreaming=false frame**, the composable returns at line 60 before reaching line 68-69. `displayedText` is never snapped. Then when a non-empty `markdown` arrives, `displayedText` still has the old partial string. But line 68-69 runs: `displayedText = targetText`, and the full text is shown. So this path actually self-heals, just with a flash of emptiness.

**THE ACTUAL BUG I'VE BEEN MISSING: What if the problem isn't in StreamableMarkdownText at all, but in the data flow?**

What if the last `GeneratingText` delta from the inference engine is emitted BUT the `accumulatorManager.reduce()` is called with `Finished` immediately after, and the **content** in the `Finished` snapshot is the same as the last `GeneratingText` emission... BUT then `PersistAccumulatedChatMessagesUseCase.sanitizePersistedContent` strips tool traces, making the DB content **shorter** than the raw accumulated content?

No, that would make the restored text *shorter* than what was shown during streaming, which is the opposite of the reported symptom (full text on revisit, partial during streaming).

**Let me reconsider the symptom:** "last chunk gets cut off during live streaming, but full text on revisit." This means:
- During streaming: the displayed text is BEHIND the full text (missing the last chunk).
- On revisit: the full text is visible (from Room).

This is classic typewriter animation lag. The typewriter can't keep up with the final burst of text. The question is: does the snap at line 68-69 fire when the stream completes?

**I think I've finally found it. The problem is: `isStreaming` may flip to `false` BEFORE `markdown` gets the full text, because of the handoff gap.**

Timeline:
1. `GeneratingText("chunk1")` → published → `markdown = "chunk1"`, `isStreaming = true`
2. `GeneratingText("chunk2")` → published → `markdown = "chunk1chunk2"`, `isStreaming = true`  
3. `Finished` → published as COMPLETE → `markdown = "chunk1chunk2"`, `isStreaming = false` (Complete indicator)
   
   **Actually no**: At step 3, the active snapshot has `messageState = COMPLETE`. But `MergeMessagesUseCase` checks `dbMessage.messageState == COMPLETE`. The DB message is still GENERATING. So the merge returns the active snapshot message. The `indicatorState` is derived from `message.messageState`, which is `COMPLETE`. So `isStreaming = false`.

   But `markdown = "chunk1chunk2"` (full content from snapshot). Line 68-69 snaps. ✓

4. Room writes COMPLETE → `dbMessagesFlow` emits → `markdown = "chunk1chunk2"` (same content). Still `isStreaming = false`. ✓

5. `acknowledgeHandoffIfNeeded` fires → `activeTurnSnapshotPort.acknowledgeHandoff(key)` → `retire(key)` → `activeTurnSnapshotFlow` emits `(null, null)`.

6. `combine` fires: `activeSnapshot = null`, `messages` has COMPLETE DB message. `projectChatMessages` returns just the DB message. `isStreaming = false`. `markdown = "chunk1chunk2"`. ✓

**Wait, but what if step 5 and step 4 happen in a weird order?** The acknowledgement is launched via `viewModelScope.launch` (line 448) — it's asynchronous. It could fire at any time. If it fires between step 3 and step 4:

5'. `acknowledgeHandoff` fires → snapshot retired → `activeTurnSnapshotFlow` emits `(null, null)`.
6'. `combine` fires: `activeSnapshot = null`, `messages` still has GENERATING placeholder (Room hasn't written yet). `projectChatMessages` returns the GENERATING placeholder with **empty content**. `markdown = ""`. `StreamableMarkdownText` returns at line 60. **Message disappears!**

Then step 4 happens:
4'. Room writes → `dbMessagesFlow` emits COMPLETE → `combine` fires → `markdown = "chunk1chunk2"`, `isStreaming = false`. Line 68-69: `displayedText` still has some partial value from the typewriter → snapped to full content. ✓ But there was a one-frame flash of empty.

**Hmm, but the user says the last chunk is "cut off" — not that it briefly disappears.** Let me reconsider.

**Actually, I think the key insight is about when `isStreaming` flips to `false` vs when `markdown` gets the final value, and the typewriter animation.**

What if the sequence is:
1. `isStreaming = true`, `markdown = "partial"` → typewriter advances toward "partial"
2. `isStreaming = true`, `markdown = "full text"` → typewriter advances toward "full text"
3. `isStreaming = false`, `markdown = "full text"` → snap

The snap at step 3 works. But what if step 2 and 3 happen in the SAME composition? Then:
- `targetText` captures "full text"
- `displayedText` was at "parti" (from previous typewriter)
- `isStreaming = false` → line 68-69 → `displayedText = targetText = "full text"` ✓

This works. BUT what if they happen in separate compositions, and step 3 happens with `markdown = ""`?

1. `isStreaming = true`, `markdown = "partial"` → typewriter advances
2. `isStreaming = true`, Snapshot retired (acknowledgement) → `markdown = ""` → `StreamableMarkdownText` **returns early at line 60**
3. `isStreaming = false`, `markdown = "full text from DB"` → `displayedText` still "parti" → line 68-69 snaps to "full text"

But **at step 2**, the composable returns early, so **line 68-69 never runs**. `displayedText` is stuck at "parti". At step 3, the composable re-enters with non-empty markdown, line 68-69 runs and fixes it. This should work, but **step 2 causes a visual gap**.

**BUT WHAT IF step 2 has `markdown = ""` and `isStreaming = false`?** Then the composable returns early, `displayedText` never gets snapped, and `isStreaming = false` means the typewriter LaunchedEffect has already exited. When step 3 arrives with full text and `isStreaming = false`, line 68-69 snaps correctly. So there WOULD be a visual gap between step 2 (message vanishes) and step 3 (full text appears).

**But the user describes "last chunk cut off" — meaning during continuous streaming, the text stops advancing before the end, and never catches up.** This implies the snap at line 68-69 ISN'T firing.

**I think I've found the definitive scenario:**

1. During streaming, `displayedText` lags behind `markdown` by the typewriter delay.
2. The last token(s) arrive. `markdown` now has the full text. `isStreaming = true`. The typewriter loop is running, advancing `displayedText`.
3. The `Finished` event arrives. The accumulator publishes a snapshot with `messageState = COMPLETE`.
4. In the SAME `combine` emission (or the next one quickly), both:
   a. `markdown` is updated to the full text (from the snapshot)
   b. `indicatorState` flips to `Complete` → `isStreaming = false`

5. Composition runs. `targetText` gets the full text. `isStreaming = false`. Line 68-69: `displayedText = targetText` = full text. **BUT** the `LaunchedEffect(isStreaming)` with key `true` was cancelled and replaced with key `false` (which returns immediately).

6. **The rendering shows the full text.** ✓

Actually wait, this works. Let me think about what "cut off" really looks like...

**AHA. The issue might be that during the gap between snapshot retirement and DB COMPLETE emission, the composable is fed `markdown = ""` and `isStreaming = true` (if the DB placeholder has GENERATING state with empty content and there's no snapshot). In this case, `StreamableMarkdownText` returns early at line 60, and the message row in `LazyColumn` may get de-allocated/re-allocated. When it comes back, `remember` reinitializes `displayedText` to `""` (since `isStreaming` is still true). The typewriter starts from scratch — but there IS a full text coming from... nowhere, because the snapshot is gone and the DB has empty content. The typewriter loop would see `targetText = ""` and just suspend. No advancement. The text sits as empty.**

Then when the DB writes COMPLETE, `markdown` becomes full text and `isStreaming` becomes false. Line 68-69 snaps. But the user saw the text shrink to empty and then pop back. This is the "cut off" effect.

**Or even worse:** what if the DB placeholder has PARTIAL content from an intermediate persist? `ChatGenerationProgressSession` does partial persists every 500ms. So the DB might have some partial content with GENERATING state. In this case:

After snapshot retirement:
- `markdown` = partial DB content
- `isStreaming = true` (GENERATING state)
- `displayedText` gets re-initialized to `""` (since `isStreaming` is true again and this might be a new composable instance)
- Typewriter starts from `""`, chasing `targetText` = partial content
- User sees the text REDUCE from the nearly-full snapshot content to the smaller DB partial content
- Some time later, DB COMPLETE arrives → `markdown` = full content, `isStreaming = false` → snap

**This is "the last chunk gets cut off":** the text was nearly complete during live streaming, then suddenly drops back to a shorter partial, then eventually fixes when DB catches up. The "cut off" is the drop.

---

## Confirmed Root Causes (Prioritized)

### 1. (CRITICAL) Handoff gap: `acknowledgeHandoff` can fire before Room emits COMPLETE

`acknowledgeHandoffIfNeeded` launches asynchronously via `viewModelScope.launch`. If it fires and retires the snapshot before `dbMessagesFlow` has emitted the COMPLETE row, there is a window where:
- Active snapshot is `null` (retired)
- DB still has GENERATING with partial/empty content
- `projectChatMessages` returns the partial DB message
- User sees text regress or disappear

### 2. (HIGH) `StreamableMarkdownText` returns early on empty markdown, losing `displayedText`

When `markdown.isEmpty()` (line 60), the composable returns without executing the snap logic (lines 68-69). The old `displayedText` value in `remember` is never updated to match `targetText` on the next non-empty frame.

### 3. (MEDIUM) `activeTurnKeyFlow` can drop the key when `dbMessagesFlow` emits COMPLETE before acknowledgement completes

The `scan` operator keeps the key alive only until `acknowledgedTurnKeys` contains it. But the acknowledgement is asynchronous. If `_acknowledgedTurnKeys` is updated (via the launched coroutine) while `dbMessagesFlow` also emits COMPLETE, the `scan` might see the acknowledged key in the same emission and transition to `null`, even though the retirement hasn't propagated through `activeTurnSnapshotFlow` yet.

---

## Implementation Plan

- [ ] **1. Fix `acknowledgeHandoffIfNeeded` to be synchronous and guarded** — In `ChatViewModel.kt:430-458`, the handoff acknowledgement must NOT be launched asynchronously. Instead:
  - Check that the DB has COMPLETE for the assistant message ID **and** the combine has emitted with that state visible in `uiState` before acknowledging.
  - Move acknowledgement to a `onEach` subscriber on `uiState` (after `.stateIn`) that checks if the *emitted* `ChatUiState` contains a COMPLETE message for the active turn, and only then acknowledges.
  - **Rationale:** The current design launches acknowledgement from inside the `combine` lambda, which runs BEFORE `uiState` has emitted. The acknowledgement coroutine can retire the snapshot before the emitted state reaches collectors.

- [ ] **2. Remove early return in `StreamableMarkdownText` for empty markdown** — In `StreamableMarkdownText.kt:60`, remove `if (markdown.isEmpty()) return`. Instead:
  - Allow the composable to proceed through lines 68-69 even with empty text.
  - If `markdown` is empty and `isStreaming` is false, set `displayedText = ""` (line 69 handles this: `displayedText != targetText` → `displayedText = ""`).
  - If `markdown` is empty and `isStreaming` is true, the typewriter loop should still run but `targetText` will be `""`, so `displayedText == target` → the loop suspends. No harm.
  - Add a conditional: only render the `Markdown`/`SimpleMarkdownText` component when `textToRender.isNotEmpty()`. The composable body still runs composition logic, but the visual render is suppressed.
  - **Rationale:** The early return prevents the snap logic (lines 68-69) from executing, which means `displayedText` can become stale if the composable was previously showing partial text and `markdown` briefly goes empty during the handoff gap.

- [ ] **3. Add a guard in `acknowledgeHandoffIfNeeded` to verify the COMPLETE message has been projected into `uiState`** — In `ChatViewModel.kt`:
  - Instead of acknowledging from inside the `combine` lambda, use a separate `uiState.onEach` collector that:
    1. Gets the current `activeTurnKeyFlow.value`.
    2. Checks if the emitted `ChatUiState.messages` contains a COMPLETE message with that ID.
    3. If so, acknowledges the handoff.
  - This ensures the acknowledgement only fires AFTER the COMPLETE state has been emitted to subscribers, not before.
  - **Rationale:** The `combine` lambda is a pure projection function. Side effects (like launching coroutines that mutate shared state) should not happen inside `combine`. Moving the acknowledgement to a downstream `onEach` guarantees proper ordering.

- [ ] **4. Ensure `activeTurnKeyFlow` doesn't drop the key until the snapshot is actually retired** — In `ChatViewModel.kt:179-201`:
  - The `scan` operator should not transition to `null` solely because `candidate.key` is null. It should also check whether the snapshot for `currentKey` still exists (i.e., `activeTurnSnapshotFlow` has a non-null snapshot for `currentKey`).
  - Alternatively, add a distinct `activeTurnSnapshotFlow` emission check: only allow `activeTurnKeyFlow` to become `null` after `activeTurnSnapshotFlow` has emitted `(null, null)` for the retiring key.
  - **Rationale:** Currently, `activeTurnKeyFlow` derives from `dbMessagesFlow` and `_acknowledgedTurnKeys`. When the DB emits COMPLETE (making `incompleteKey = null`) and acknowledgement fires (adding to `acknowledgedTurnKeys`), the `scan` transitions to `null`. But `activeTurnSnapshotFlow` is derived from `activeTurnKeyFlow`. If the key becomes `null`, the snapshot observation stops, potentially before `retire()` has propagated through the store's `MutableStateFlow`. There's a brief window where `activeTurnKeyFlow = null` but the store still has the entry, causing `flatMapLatest` to unsubscribe from the snapshot flow prematurely.

- [ ] **5. (Optional) Add `distinctUntilChanged` on `projectChatMessages` result by message ID + content** — In `ChatViewModel.kt`, after `projectChatMessages`, filter out emissions where the assistant message content hasn't actually changed. This reduces unnecessary recompositions and prevents the `StreamableMarkdownText` from receiving spurious empty-or-partial interim states.
  - **Rationale:** During the handoff gap, the combine may emit multiple states with the same DB content. Filtering reduces the chance that `StreamableMarkdownText` sees a transient empty or regressed state.

- [ ] **6. Add unit test for the handoff gap scenario** — In `ChatViewModelTest.kt`, add a test that:
  - Publishes a terminal (COMPLETE) snapshot to the store.
  - Verifies that `uiState` emits the full text from the snapshot.
  - Then updates the DB to COMPLETE.
  - Then acknowledges the handoff.
  - Verifies that the message content is never empty or regressed at any point in the emission sequence.
  - **Rationale:** The current tests (`snapshot to Room handoff doesn't drop message`) verify that the message doesn't disappear, but don't verify that the CONTENT doesn't regress during the transition.

## Verification Criteria

- [ ] Streaming a response shows the full text including the very last token, with no visible cutoff
- [ ] The typewriter animation catches up to the full text before the `Complete` indicator appears
- [ ] No frame where the message content regresses (goes from longer to shorter) during the GENERATING→COMPLETE transition
- [ ] No frame where the message content becomes empty during the handoff window
- [ ] Navigating away and back during streaming still re-attaches correctly
- [ ] `ChatViewModelTest` has a new test that verifies no content regression during handoff
- [ ] `StreamableMarkdownText` handles empty markdown input without losing accumulated displayed text state

## Potential Risks and Mitigations

1. **Risk: Removing the early return in `StreamableMarkdownText` could show empty markdown components**
   Mitigation: Conditionally render the `Markdown`/`SimpleMarkdownText` only when `textToRender.isNotEmpty()`, preserving the visual behavior of hiding empty messages while still running the snap logic.

2. **Risk: Moving acknowledgement to `onEach` on `uiState` could delay cleanup and leak snapshots**
   Mitigation: The `ActiveChatTurnStore` already has a 5-minute terminal cleanup that serves as a safety net. The acknowledgement is still a best-effort optimization; delayed acknowledgement will not cause correctness issues, just slightly longer memory retention of snapshots.

3. **Risk: Changing `activeTurnKeyFlow` scan logic could break reattach-after-navigation**
   Mitigation: Add a specific test for the reattach scenario (navigate away while GENERATING, return and verify latest snapshot is visible).

## Alternative Approaches

1. **Eager DB persist before snapshot terminal publication:** Change `GenerateChatResponseUseCase` to persist the final accumulator state BEFORE publishing the terminal snapshot. This would guarantee that when the COMPLETE snapshot is published, Room already has the COMPLETE row, eliminating the handoff gap entirely. Trade-off: adds latency to the final snapshot emission (DB write must complete first).

2. **Add a "handoff buffer" in `activeTurnSnapshotFlow`:** When `activeTurnKeyFlow` transitions from a real key to `null`, keep the last snapshot around for one additional emission so the `combine` sees both the DB COMPLETE message and the snapshot simultaneously. Trade-off: adds complexity to the flow pipeline.

3. **Two-phase persistence:** Write the content to Room FIRST (with GENERATING state), then publish the terminal snapshot, then write the COMPLETE state update. This would ensure the DB always has at least as much content as the snapshot. Trade-off: more Room writes during the critical path.