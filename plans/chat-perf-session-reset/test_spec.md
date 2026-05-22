# chat-perf-session-reset — Test spec

**Ticket ID:** `chat-perf-session-reset`  
**Companion spec:** `plans/chat-perf-session-reset/spec.md`

## Test framework and run commands

| Concern | Command |
|---------|---------|
| Orchestrator session | `./gradlew :feature:chat:testDebugUnitTest --tests "com.browntowndev.pocketcrew.feature.chat.InferenceOrchestratorSessionTest" --no-daemon` |
| Projector + reducer session | `./gradlew :feature:chat:testDebugUnitTest --tests "com.browntowndev.pocketcrew.feature.chat.ChatMessageProjectorSessionTest" --tests "com.browntowndev.pocketcrew.feature.chat.delegates.ChatUiStateReducerSessionTest" --no-daemon` |
| MessageList height + scroll | `./gradlew :feature:chat:testDebugUnitTest --tests "com.browntowndev.pocketcrew.feature.chat.components.messagelist.MessageListScrollPolicyTest" --tests "com.browntowndev.pocketcrew.feature.chat.components.messagelist.MessageListScrollPolicyHeightTest" --tests "com.browntowndev.pocketcrew.feature.chat.components.messagelist.MessageListHeightTrackerTest" --no-daemon` |
| Navigation policy | `./gradlew :app:testDebugUnitTest --tests "com.browntowndev.pocketcrew.presentation.navigation.ChatNavigationPolicyTest" --no-daemon` |
| Full chat module regression | `./gradlew :feature:chat:testDebugUnitTest --no-daemon` |
| Pre-merge | `./gradlew ktlintCheck` and `./gradlew detekt` |

**Frameworks:** JUnit 5, MockK (orchestrator deps only), `MainDispatcherRule` where coroutines used, `SavedStateHandle` real instance for orchestrator tests.

**Test file paths:**

| File | Package |
|------|---------|
| `feature/chat/src/test/kotlin/.../InferenceOrchestratorSessionTest.kt` | `com.browntowndev.pocketcrew.feature.chat` |
| `feature/chat/src/test/kotlin/.../ChatMessageProjectorSessionTest.kt` | `com.browntowndev.pocketcrew.feature.chat` |
| `feature/chat/src/test/kotlin/.../delegates/ChatUiStateReducerSessionTest.kt` | `com.browntowndev.pocketcrew.feature.chat.delegates` |
| `feature/chat/src/test/kotlin/.../components/messagelist/MessageListScrollPolicyHeightTest.kt` | `com.browntowndev.pocketcrew.feature.chat.components.messagelist` |
| `feature/chat/src/test/kotlin/.../components/messagelist/MessageListHeightTrackerTest.kt` | same |
| `app/src/test/kotlin/.../presentation/navigation/ChatNavigationPolicyTest.kt` | `com.browntowndev.pocketcrew.presentation.navigation` |

---

## Scenario index

| ID | Category | Title |
|----|----------|-------|
| SC-PERF-01 | Happy | syncActiveChatFromNavigation sets currentChatId to nav id |
| SC-PERF-02 | Happy | syncActiveChatFromNavigation writes SavedStateHandle chatId |
| SC-PERF-03 | Happy | acknowledgedTurnKeys pruned to active chat only |
| SC-PERF-04 | Happy | stale orchestrator chat overridden by navigation sync |
| SC-PERF-05 | Edge | sync with null navChatId clears currentChatId and saved state |
| SC-PERF-06 | Happy | projector clear wipes memo so identical inputs recompute |
| SC-PERF-07 | Happy | onActiveChatChanged with same id is no-op |
| SC-PERF-08 | Happy | reducer calls projector onActiveChatChanged on chat change |
| SC-PERF-09 | Happy | openChatFromHistory sets launchSingleTop true |
| SC-PERF-10 | Happy | openChatFromHistory pops up to Routes.CHAT non-inclusive |
| SC-PERF-11 | Happy | shouldCommitMessageHeightPx rejects sub-threshold delta |
| SC-PERF-12 | Happy | shouldCommitMessageHeightPx accepts first measurement |
| SC-PERF-13 | Happy | height tracker ignores 50 sub-8px oscillations — exactly 1 commit |
| SC-PERF-14 | Happy | MessageListLazyCacheWindow equals 360.dp constant |
| SC-PERF-15 | Happy | ObserveKeyCompute logging guarded by BuildConfig.DEBUG |
| SC-PERF-16 | Regression | MessageListScrollPolicyTest 12 tests unchanged and passing |
| SC-PERF-M-01 | Mutation | projector onActiveChatChanged without memo clear fails SC-PERF-06 |
| SC-PERF-M-02 | Mutation | sync without ack prune leaves foreign chatId keys — fails SC-PERF-03 |
| SC-PERF-M-03 | Mutation | height tracker writing every pixel fails SC-PERF-13 |

---

## Happy path scenarios

### SC-PERF-01

**File:** `InferenceOrchestratorSessionTest.kt`

```gherkin
Given InferenceOrchestrator with _currentChatId ChatId "chat-a"
When syncActiveChatFromNavigation is called with ChatId "chat-b"
Then currentChatId.value equals ChatId "chat-b"
```

### SC-PERF-02

**File:** `InferenceOrchestratorSessionTest.kt`

```gherkin
Given SavedStateHandle with no chatId key
When syncActiveChatFromNavigation is called with ChatId "chat-b"
Then savedStateHandle.get<String>("chatId") equals "chat-b"
```

### SC-PERF-03

**File:** `InferenceOrchestratorSessionTest.kt`

```gherkin
Given acknowledgedTurnKeys contains ActiveChatTurnKey(ChatId "chat-a", MessageId "asst-a")
And acknowledgedTurnKeys contains ActiveChatTurnKey(ChatId "chat-b", MessageId "asst-b")
When syncActiveChatFromNavigation is called with ChatId "chat-b"
Then acknowledgedTurnKeys.value equals setOf(ActiveChatTurnKey(ChatId "chat-b", MessageId "asst-b"))
```

### SC-PERF-04

**File:** `InferenceOrchestratorSessionTest.kt`

```gherkin
Given _currentChatId is ChatId "chat-stale"
And initial navigation target is ChatId "chat-nav"
When syncActiveChatFromNavigation is called with ChatId "chat-nav"
Then activeChatId resolution chat-stale ?: chat-nav yields ChatId "chat-nav"
```

*(Test documents orchestrator field after sync equals `chat-nav`; ViewModel `activeChatIdFlow` integration optional in same file.)*

### SC-PERF-05

**File:** `InferenceOrchestratorSessionTest.kt`

```gherkin
Given _currentChatId is ChatId "chat-a"
When syncActiveChatFromNavigation is called with null
Then currentChatId.value is null
And savedStateHandle.get<String>("chatId") is null
And acknowledgedTurnKeys.value is emptySet()
```

### SC-PERF-06

**File:** `ChatMessageProjectorSessionTest.kt`

```gherkin
Given dbMessages list L for ChatId "chat-a"
And first project(L, null, null) returns result R1
When onActiveChatChanged is called with ChatId "chat-b"
And second project(L, null, null) returns result R2
Then R1.messages !== R2.messages (reference inequality)
```

### SC-PERF-07

**File:** `ChatMessageProjectorSessionTest.kt`

```gherkin
Given dbMessages list L
And onActiveChatChanged ChatId "chat-a"
And first equals project(L, null, null).messages
And second equals project(L, null, null).messages
When onActiveChatChanged ChatId "chat-a" is called again
And third equals project(L, null, null).messages
Then first === second
And second === third
```

### SC-PERF-08

**File:** `ChatUiStateReducerSessionTest.kt`

```gherkin
Given ChatUiStateReducer with real ChatMessageUiMapper and internal projector
And ReduceParams chatId ChatId "chat-1" with dbMessages list L
And resultA equals reduce(params chat-1).uiState.messages
When reduce is called with same L and chatId ChatId "chat-2"
Then resultB.uiState.messages !== resultA (reference inequality on first message when L non-empty)
```

### SC-PERF-09

**File:** `ChatNavigationPolicyTest.kt`

```gherkin
When NavOptionsBuilder.openChatFromHistory is applied
Then launchSingleTop equals true
```

### SC-PERF-10

**File:** `ChatNavigationPolicyTest.kt`

```gherkin
When NavOptionsBuilder.openChatFromHistory is applied
Then popUpTo destination route equals "chat"
And popUpTo inclusive equals false
```

### SC-PERF-11

**File:** `MessageListScrollPolicyHeightTest.kt`

```gherkin
When shouldCommitMessageHeightPx(previousHeightPx=100, newHeightPx=103, minDeltaPx=8)
Then result equals false
```

### SC-PERF-12

**File:** `MessageListScrollPolicyHeightTest.kt`

```gherkin
When shouldCommitMessageHeightPx(previousHeightPx=null, newHeightPx=50, minDeltaPx=8)
Then result equals true
```

### SC-PERF-13

**File:** `MessageListHeightTrackerTest.kt`

```gherkin
Given MessageListHeightTracker with key "chat-1:msg-1"
When onSizeChanged is called 50 times alternating newHeightPx 200 and 201
Then commit count equals 1
And get("chat-1:msg-1") equals 200
```

```gherkin
Given tracker with key "chat-1:msg-1" and committed height 200
When onSizeChanged is called with newHeightPx 208
Then commit count equals 2
And get("chat-1:msg-1") equals 208
```

### SC-PERF-14

**File:** `MessageListScrollPolicyHeightTest.kt`

```gherkin
When MessageListLazyCacheWindowDp is read from MessageList.kt companion or internal constant
Then value equals 360.dp
```

*(Expose `internal val MessageListLazyCacheWindowDp = 360.dp` for test, or test via reflection-free duplicate constant assertion in test file marked must match — prefer internal const in `MessageList.kt`.)*

### SC-PERF-15

**File:** `ChatViewModelObserveKeyLoggingTest.kt` (optional small file) **or** byte-code/source inspection test

```gherkin
Given ChatViewModel.kt source or compiled debug variant
When activeTurnSnapshotObserveKeyFlow combine lambda is inspected
Then Log.d tag "ObserveKeyCompute" appears only inside if (BuildConfig.DEBUG) block
```

*(JUnit: use Kotlin source read in test resources OR detekt/custom lint rule; minimum bar: `grep`-style test reading `ChatViewModel.kt` as classpath resource asserting substring order `if (BuildConfig.DEBUG)` before `Log.d("ObserveKeyCompute"`.)*

### SC-PERF-16

**File:** existing `MessageListScrollPolicyTest.kt` (no edits)

```gherkin
When ./gradlew :feature:chat:testDebugUnitTest --tests MessageListScrollPolicyTest runs
Then test count equals 12
And failed test count equals 0
And resolveMessageListProgrammaticScrollAction initial open still returns ScrollToUserTurn(lazyIndex=3, animated=false)
And streaming case still returns MessageListProgrammaticScrollAction.None
```

---

## Edge case scenarios

### SC-PERF-05

*(Listed above — null nav chat clears session.)*

### SC-PERF-03 foreign requestedTurnKey

Add assertion in `InferenceOrchestratorSessionTest.kt` under SC-PERF-03 extension:

```gherkin
Given requestedTurnKey is ActiveChatTurnKey(ChatId "chat-a", MessageId "asst-a")
When syncActiveChatFromNavigation is called with ChatId "chat-b"
Then requestedTurnKey.value is null
```

---

## Mutation-defense

### SC-PERF-M-01

**Risk:** `onActiveChatChanged` clears `messageCache` only but leaves `memoResult` — second `project` returns stale list for wrong chat.

**Fails when:** SC-PERF-06 assertion `R1.messages !== R2.messages` not met after chat switch with same `L` input.

### SC-PERF-M-02

**Risk:** `syncActiveChatFromNavigation` updates `_currentChatId` but does not filter `acknowledgedTurnKeys`.

**Fails when:** SC-PERF-03 Then set still contains `ActiveChatTurnKey(ChatId "chat-a", ...)`.

### SC-PERF-M-03

**Risk:** `MessageListHeightTracker` assigns `messageHeightsPx[key] = newHeightPx` on every `onSizeChanged` without threshold.

**Fails when:** SC-PERF-13 oscillation case Then `commit count equals 1` — lazy impl produces 50 commits.

---

## Scroll contract preservation (manual / automated)

These behaviors are **not** re-tested with new scenarios; SC-PERF-16 locks existing literals:

| Behavior | Expected action |
|----------|-----------------|
| Initial open | `ScrollToUserTurn(lazyIndex=3, animated=false)` |
| New user message | `ScrollToUserTurn(lazyIndex=5, animated=true)` |
| Streaming tokens | `MessageListProgrammaticScrollAction.None` |
| Assistant complete | `MessageListProgrammaticScrollAction.None` |
| Bottom spacer 704−108−12 | `584` px |
| Active turn heights 96+220+80+spacing | `420` px |

Implementation must not modify `MessageListScrollPolicyTest.kt` to greenwash regressions.

---

## Commands summary (pre-merge)

```bash
./gradlew :feature:chat:testDebugUnitTest --tests "*InferenceOrchestratorSessionTest" --tests "*ChatMessageProjectorSessionTest" --tests "*ChatUiStateReducerSessionTest" --tests "*MessageListScrollPolicy*" --tests "*MessageListHeightTrackerTest" --no-daemon
./gradlew :app:testDebugUnitTest --tests "*ChatNavigationPolicyTest" --no-daemon
./gradlew ktlintCheck
./gradlew detekt
```
