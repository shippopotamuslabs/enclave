# chat-perf-session-reset — Chat scroll/UI performance on session switch

## Feature summary

Fix chat scroll and Compose performance degradation when switching between chats from History or when message history grows. Root causes: stale `InferenceOrchestrator._currentChatId` overriding navigation `initialChatId`, unbounded `acknowledgedTurnKeys`, stacked `ChatViewModel` instances from History navigation, oversized `LazyLayoutCacheWindow` (2400.dp), per-token `onSizeChanged` invalidating `MessageListContent`, and uncached `ChatMessageProjector` state surviving chat switches.

**Non-negotiable:** Preserve the programmatic scroll contract in `MessageListScrollPolicy.kt` / `MessageListScrollBehavior.kt`. All 12 existing `MessageListScrollPolicyTest` tests must pass unchanged.

## File structure

| File | Action | Responsibility |
|------|--------|----------------|
| `feature/chat/build.gradle.kts` | Modify | Enable `buildFeatures { buildConfig = true }` for DEBUG-gated logging |
| `feature/chat/.../delegates/InferenceOrchestrator.kt` | Modify | `syncActiveChatFromNavigation`, prune `acknowledgedTurnKeys` to active chat, clear stale `requestedTurnKey` |
| `feature/chat/.../ChatViewModel.kt` | Modify | Expose `onNavigationChatIdChanged`; gate `ObserveKeyCompute` `Log.d` behind `BuildConfig.DEBUG` |
| `feature/chat/.../ChatRoute.kt` | Modify | `LaunchedEffect(initialChatId)` calls ViewModel session sync on nav chat change |
| `feature/chat/.../ChatMessageProjection.kt` | Modify | `ChatMessageProjector.onActiveChatChanged` / `clear()` — wipe memo + per-message cache |
| `feature/chat/.../delegates/ChatUiStateReducer.kt` | Modify | Call `chatMessageProjector.onActiveChatChanged` alongside `ChatMessageUiMapper` |
| `feature/chat/.../components/messagelist/MessageList.kt` | Modify | Reduce cache window; `remember(chatId)` height map; debounced height commits |
| `feature/chat/.../components/messagelist/MessageListScrollPolicy.kt` | Modify | Add `shouldCommitMessageHeightPx` threshold helper (pure, unit-testable) |
| `feature/chat/.../components/messagelist/MessageListHeightTracker.kt` | Create | Holds height map; commits only when `shouldCommitMessageHeightPx` returns true |
| `app/.../presentation/navigation/PocketCrewNavGraph.kt` | Modify | History → chat uses `launchSingleTop` + `popUpTo(Routes.CHAT)` |
| `app/.../presentation/navigation/ChatNavigationPolicy.kt` | Create | Pure/builder helpers for history-open-chat `NavOptions` (testable without Compose NavHost) |
| `feature/chat/src/test/.../InferenceOrchestratorSessionTest.kt` | Create | Session sync + ack pruning |
| `feature/chat/src/test/.../ChatMessageProjectorSessionTest.kt` | Create | Projector clear on chat change |
| `feature/chat/src/test/.../delegates/ChatUiStateReducerSessionTest.kt` | Create | Reducer invokes projector clear |
| `feature/chat/src/test/.../components/messagelist/MessageListScrollPolicyHeightTest.kt` | Create | Height commit threshold + existing scroll policy regression import |
| `feature/chat/src/test/.../components/messagelist/MessageListHeightTrackerTest.kt` | Create | Debounce / commit-count behavior |
| `app/src/test/.../presentation/navigation/ChatNavigationPolicyTest.kt` | Create | `launchSingleTop` and `popUpTo` contract |

No schema or contract-file changes.

## API contracts

### `InferenceOrchestrator`

```kotlin
/**
 * Align orchestrator session with navigation/SavedState chat id when user opens a different chat.
 * Does not cancel in-flight inference; callers may add that later if product requires it.
 */
fun syncActiveChatFromNavigation(navChatId: ChatId?)

private fun pruneAcknowledgedTurnKeysToActiveChat(activeChatId: ChatId?)
```

**`syncActiveChatFromNavigation` behavior:**

1. If `_currentChatId.value == navChatId` → return immediately (no flow churn).
2. Set `_currentChatId.value = navChatId`.
3. Set `savedStateHandle["chatId"] = navChatId?.value` (mirror `sendMessage` / `createNewChat`).
4. `_acknowledgedTurnKeys` ← keep only keys where `key.chatId == navChatId` (empty set when `navChatId == null`).
5. `_requestedTurnKey` ← `null` if current key's `chatId != navChatId`, else unchanged.
6. Do **not** clear `_optimisticUserMessage` in this ticket (out of scope; existing merge path handles id match).

### `ChatViewModel`

```kotlin
fun onNavigationChatIdChanged(chatId: ChatId?)
```

Delegates to `inferenceOrchestrator.syncActiveChatFromNavigation(chatId)`. No direct projector access (reducer owns projector via `reduce`).

**Logging:** Wrap the `ObserveKeyCompute` `Log.d` block (lines ~249–257) in `if (BuildConfig.DEBUG) { ... }`. No log in release builds.

### `ChatRoute`

```kotlin
LaunchedEffect(viewModel.initialChatId) {
    viewModel.onNavigationChatIdChanged(viewModel.initialChatId)
}
```

Runs when History navigation supplies a new `chatId` query arg (same `hiltViewModel()` instance gets new `SavedStateHandle` entry on `launchSingleTop` replace).

### `ChatMessageProjector`

```kotlin
fun onActiveChatChanged(chatId: ChatId?)
fun clear()
```

**`onActiveChatChanged`:** If `chatId == cachedActiveChatId` return; else set `cachedActiveChatId`, call `clear()`.

**`clear()`:** Clear `messageCache`; set `memoDbMessages`, `memoSnapshot`, `memoKey`, `memoResult` to `null`.

### `ChatUiStateReducer.reduce`

Before `chatMessageProjector.project(...)`, call:

```kotlin
chatMessageProjector.onActiveChatChanged(params.chatId)
```

(Order: mapper clear already at line 67; projector clear immediately after or before project — both must run when `params.chatId` changes.)

### `MessageListScrollPolicy` (new pure API)

```kotlin
internal const val MessageListHeightCommitMinDeltaPx: Int = 8

internal fun shouldCommitMessageHeightPx(
    previousHeightPx: Int?,
    newHeightPx: Int,
    minDeltaPx: Int = MessageListHeightCommitMinDeltaPx,
): Boolean
```

Returns `true` when `previousHeightPx == null` or `abs(newHeightPx - previousHeightPx) >= minDeltaPx`.

### `MessageListHeightTracker`

```kotlin
internal class MessageListHeightTracker {
    fun get(heightKey: String): Int?
    fun onSizeChanged(heightKey: String, newHeightPx: Int): Boolean
}
```

`onSizeChanged` updates internal map only when `shouldCommitMessageHeightPx` returns true; returns whether a commit occurred (for tests).

### `MessageList` Compose constants

```kotlin
// Was 2_400.dp — retained ~12× viewport of off-screen composition.
// 360.dp ≈ 2–3 message rows ahead/behind at typical density; enough for FAB/scroll continuity.
private val MessageListLazyCacheWindow = 360.dp
```

Document inline comment referencing measurement: typical row 80–200.dp; 360.dp bounds off-screen cache without retaining ~30 screens.

**Height map:** `val messageHeightsPx = remember(chatId) { mutableStateMapOf<String, Int>() }` **or** `remember(chatId) { MessageListHeightTracker() }` wired through `onSizeChanged`.

**Recomposition scope:** Compute `bottomSpacerHeightPx` inside `remember(messageHeightsPx, ...)` or `derivedStateOf` keyed on tracker snapshot so streaming 1px deltas that fail `shouldCommitMessageHeightPx` do not recompose `LazyColumn` item block.

### `ChatNavigationPolicy` (app module)

```kotlin
fun NavOptionsBuilder.openChatFromHistory() {
    launchSingleTop = true
    popUpTo(Routes.CHAT) { inclusive = false }
}
```

`PocketCrewNavGraph` `onNavigateToChat` with non-null id:

```kotlin
navController.navigate(Routes.CHAT_WITH_ID.replace("{chatId}", id.value)) {
    openChatFromHistory()
}
```

New-chat branch (`id == null`) unchanged.

## Data model / schema changes

None.

## Ordered implementation tasks

1. **PERF-T1 — Orchestrator session sync** (SC-PERF-01, SC-PERF-02, SC-PERF-03)  
   Add `syncActiveChatFromNavigation` + `pruneAcknowledgedTurnKeysToActiveChat` in `InferenceOrchestrator.kt`. Unit tests in `InferenceOrchestratorSessionTest.kt`.

2. **PERF-T2 — ViewModel + Route wiring** (SC-PERF-04, SC-PERF-05)  
   `ChatViewModel.onNavigationChatIdChanged`, `ChatRoute` `LaunchedEffect(initialChatId)`. Test: orchestrator receives nav id when `initialChatId` differs from stale `_currentChatId`.

3. **PERF-T3 — Projector session clear** (SC-PERF-06, SC-PERF-07, SC-PERF-08)  
   `ChatMessageProjector.onActiveChatChanged` / `clear()`. `ChatUiStateReducer` calls projector clear. Tests in `ChatMessageProjectorSessionTest.kt`, `ChatUiStateReducerSessionTest.kt`.

4. **PERF-T4 — Navigation hygiene** (SC-PERF-09, SC-PERF-10)  
   `ChatNavigationPolicy.kt` + `PocketCrewNavGraph` history navigate. `ChatNavigationPolicyTest.kt`.

5. **PERF-T5 — MessageList Compose perf** (SC-PERF-11, SC-PERF-12, SC-PERF-13, SC-PERF-14)  
   Cache window 360.dp, `remember(chatId)` heights, `MessageListHeightTracker` + policy helper. Tests in `MessageListScrollPolicyHeightTest.kt`, `MessageListHeightTrackerTest.kt`.

6. **PERF-T6 — DEBUG log gate** (SC-PERF-15)  
   `build.gradle.kts` `buildConfig = true`; gate `ObserveKeyCompute` log. Static/unit: no `Log.d("ObserveKeyCompute"` execution path testable via refactor extract if needed — prefer documenting manual/logcat check in test_spec SC-PERF-15 as compile-time guard (see test spec).

7. **PERF-T7 — Scroll regression guard** (SC-PERF-16)  
   Run `./gradlew :feature:chat:testDebugUnitTest --tests "*MessageListScrollPolicyTest"` — all 12 tests green, zero edits to test file.

## Scenario verification map

| Task | Scenario IDs |
|------|----------------|
| PERF-T1 | SC-PERF-01, SC-PERF-02, SC-PERF-03, SC-PERF-M-02 |
| PERF-T2 | SC-PERF-04, SC-PERF-05 |
| PERF-T3 | SC-PERF-06, SC-PERF-07, SC-PERF-08, SC-PERF-M-01 |
| PERF-T4 | SC-PERF-09, SC-PERF-10 |
| PERF-T5 | SC-PERF-11, SC-PERF-12, SC-PERF-13, SC-PERF-14, SC-PERF-M-03 |
| PERF-T6 | SC-PERF-15 |
| PERF-T7 | SC-PERF-16 |

## Drift and escalation

- Do **not** change `resolveMessageListProgrammaticScrollAction` semantics, `MessageListScrollToLatestEffect` `remember(chatId)` keys, or bottom-spacer math signatures.
- Do **not** add DB columns or persist height maps.
- Do **not** cancel active inference on chat switch unless product explicitly requests (separate ticket).
- If `popUpTo(Routes.CHAT)` fails runtime back-stack tests on device, escalate to `popUpTo(navController.graph.id)` with documented graph id — do not drop `launchSingleTop`.
- If `BuildConfig` in library module is blocked by CI policy, escalate for `LoggingPort.debug` alternative — do not leave unconditional `Log.d` in `activeTurnSnapshotObserveKeyFlow`.
- Contract waiver required for any edit under `/contracts/`.

## Constitution audit

| Contract | Status |
|----------|--------|
| `ARCHITECTURE_RULES.md` | Compliant — navigation stays in app graph; no `NavController` in composables; ViewModel maps domain → UI |
| `CODE_STYLE_RULES.md` | Compliant — pure policy functions tested with JUnit 5 |
| `DATA_LAYER_RULES.md` | N/A — no persistence changes |
| `UI_DESIGN_SPEC.md` | Compliant — scroll behavior unchanged; perf-only MessageList internals |
| `UNIT_TESTING.md` | Compliant — new unit tests per behavior change |

## Cross-spec dependencies

None.
