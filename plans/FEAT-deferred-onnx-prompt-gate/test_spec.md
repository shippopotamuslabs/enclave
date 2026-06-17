# Test Spec: Deferred ONNX Prompt Gate

**Ticket ID:** `FEAT-deferred-onnx-prompt-gate`  
**Companion spec:** `plans/FEAT-deferred-onnx-prompt-gate/spec.md`  
**Date:** 2026-06-03

---

## 1. Test Framework and Run Commands

| Concern | Command |
|---------|---------|
| Domain gate + rollback (primary) | `./gradlew :core:domain:testDebugUnitTest --tests "com.browntowndev.enclave.domain.usecase.chat.ProcessPromptWithSafetyGateUseCaseTest" --tests "com.browntowndev.enclave.domain.usecase.chat.RollbackPromptSubmissionUseCaseTest" --no-daemon` |
| Data soft-delete port | `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.enclave.core.data.repository.MessageRepositorySoftDeleteTest" --no-daemon` |
| Inference orchestrator send pipeline | `./gradlew :feature:chat:testDebugUnitTest --tests "com.browntowndev.enclave.feature.chat.InferenceOrchestratorSendTest" --no-daemon` |
| ViewModel send + moderation | `./gradlew :feature:chat:testDebugUnitTest --tests "com.browntowndev.enclave.feature.chat.ChatViewModelSendHappyPathTest" --no-daemon` |
| Existing moderation use case (regression) | `./gradlew :core:domain:testDebugUnitTest --tests "com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCaseTest" --no-daemon` |
| Compile smoke | `./gradlew :core:domain:assembleDebug :core:data:assembleDebug :feature:chat:assembleDebug --no-daemon` |
| Full ticket regression | `./gradlew :core:domain:testDebugUnitTest :core:data:testDebugUnitTest :feature:chat:testDebugUnitTest --tests "*ProcessPromptWithSafetyGate*" --tests "*RollbackPromptSubmission*" --tests "*MessageRepositorySoftDelete*" --tests "*InferenceOrchestratorSendTest*" --tests "*ChatViewModelSendHappyPathTest*" --no-daemon` |

**Frameworks:**

- JUnit 5 (`@Test`, `@DisplayName`, `@ExtendWith(MainDispatcherExtension::class)` / `MainDispatcherRule`)
- `runTest` + `StandardTestDispatcher` + `advanceTimeBy` / `advanceUntilIdle`
- Turbine for `moderationMessages` Flow where applicable
- MockK for orchestrator/ViewModel boundaries; **fakes** for domain gate tests per `UNIT_TESTING.md`

**Literal constants (do not paraphrase in assertions):**

| Constant | Value |
|----------|-------|
| Snackbar message | `Prompt rejected due to moderation` |
| Placeholder delay | `650L` ms (`ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS`) |
| Koala block fixture score | `S3` → `0.66`, threshold `sexual/minors` → `0.65` |
| Test prompt (blocked) | `blocked input` |
| Test prompt (allowed) | `Hello crew` |

---

## 2. Scenario Index

| ID | Category | Title |
|----|----------|-------|
| DOPG-D-01 | Domain / happy | Gate persists user then placeholder before moderation |
| DOPG-D-02 | Domain / happy | Gate returns Allowed when moderation passes |
| DOPG-D-03 | Domain / happy | Gate runs moderation after placeholder persist |
| DOPG-D-04 | Domain / happy | Gate skips ONNX when shouldModerate is false |
| DOPG-D-05 | Domain / error | Gate returns Blocked and invokes rollback when ONNX blocks |
| DOPG-D-06 | Domain / error | Rollback on existing chat soft-deletes user and assistant |
| DOPG-D-07 | Domain / error | Rollback on new chat calls deleteChat only |
| DOPG-D-08 | Data | softDeleteMessage sets deleted flag (user row) |
| DOPG-D-09 | Data | softDeleteMessage removes row from active message query |
| DOPG-D-10 | Domain / edge | Blank promptText skips ONNX and returns Allowed |
| DOPG-O-01 | Orchestrator | Optimistic user visible before gate completes |
| DOPG-O-02 | Orchestrator | Gate Allowed path calls generateChatResponse after 650ms delay |
| DOPG-O-03 | Orchestrator | Placeholder created inside gate before delay (not delayed 650ms) |
| DOPG-O-04 | Orchestrator | Blocked path clears optimistic and does not call generateChatResponse |
| DOPG-O-05 | Orchestrator | Title generation launched only when wasNewChat and input non-blank |
| DOPG-O-06 | Orchestrator | Blocked new-chat path does not leave requestedTurnKey set |
| DOPG-V-01 | ViewModel | onSendMessage does not call moderatePromptUseCase before orchestrator |
| DOPG-V-02 | ViewModel | Blocked send restores input and emits moderation snackbar |
| DOPG-V-03 | ViewModel | Blocked send runs processPromptWithSafetyGate (persist before block) |
| DOPG-V-04 | ViewModel | Trusted model bypass still sends through orchestrator with shouldModerate false |
| DOPG-M-01 | Mutation defense | Removing rollback call leaves Blocked test failing (user row remains) |
| DOPG-M-02 | Mutation defense | Reordering gate to moderate-before-persist fails DOPG-D-03 / DOPG-V-03 |

---

## 3. Domain Scenarios (`:core:domain`)

### DOPG-D-01 — Gate persists user then placeholder before moderation

**File:** `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/ProcessPromptWithSafetyGateUseCaseTest.kt`

```gherkin
Scenario: DOPG-D-01 — call order is createUser, createPlaceholder, then moderate
  Given a FakeMessageRepository and FakeChatRepository
  And a RecordingModerationPort that completes only after a CompletableDeferred is signaled
  And ProcessPromptWithSafetyGateUseCase with shouldModerate = true
  And a PromptSafetyGateRequest with message.chatId = ChatId("chat-existing-1")
  And promptTextForModeration = "blocked input"
  When invoke is launched in runTest
  Then createUserMessageUseCase completes first (user row count = 1)
  And createAssistantPlaceholderUseCase completes second (assistant row exists with messageState PROCESSING)
  And ModeratePromptUseCase has not completed before placeholder exists
  When the moderation deferred is completed with blocked = true
  Then the result is PromptSafetyGateResult.Blocked
```

---

### DOPG-D-02 — Gate returns Allowed when moderation passes

```gherkin
Scenario: DOPG-D-02 — allowed path returns UserPromptResult
  Given AllowPromptModerationPort (blocked = false)
  And shouldModerate = true
  And message.chatId = ChatId("chat-existing-2")
  When ProcessPromptWithSafetyGateUseCase is invoked
  Then the result is PromptSafetyGateResult.Allowed
  And Allowed.userResult.chatId equals ChatId("chat-existing-2")
  And Allowed.assistantMessageId equals the request assistantMessageId
  And RollbackPromptSubmissionUseCase was not invoked
```

---

### DOPG-D-03 — Gate runs moderation after placeholder persist

```gherkin
Scenario: DOPG-D-03 — moderation starts only after assistant placeholder saved
  Given a RecordingCallOrderPort wrapping ModeratePromptUseCase
  And shouldModerate = true
  When invoke completes with Allowed
  Then call order equals ["createUser", "createPlaceholder", "moderate"]
```

---

### DOPG-D-04 — Gate skips ONNX when shouldModerate is false

```gherkin
Scenario: DOPG-D-04 — trusted bypass skips moderation port
  Given a ModerationPort spy that would return blocked = true if called
  And shouldModerate = false
  When invoke completes
  Then result is Allowed
  And the moderation port classify method was called 0 times
  And user and assistant messages remain in FakeMessageRepository
```

---

### DOPG-D-05 — Gate returns Blocked and invokes rollback when ONNX blocks

```gherkin
Scenario: DOPG-D-05 — blocked path triggers rollback
  Given FixedPromptModerationPort with S3 score 0.66 and threshold 0.65 (blocked)
  And shouldModerate = true
  And message.chatId = ChatId("chat-existing-3")
  When invoke completes
  Then result is Blocked
  And Blocked.moderationResult.triggeredLabels contains "sexual/minors"
  And RollbackPromptSubmissionUseCase was invoked exactly once with wasNewChat = false
```

---

### DOPG-D-06 — Rollback on existing chat soft-deletes user and assistant

**File:** `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/RollbackPromptSubmissionUseCaseTest.kt`

```gherkin
Scenario: DOPG-D-06 — existing chat rollback soft-deletes both messages
  Given FakeMessageRepository containing user MessageId("user-rb-1") and assistant MessageId("asst-rb-1") in ChatId("chat-rb-existing")
  When RollbackPromptSubmissionUseCase(chatId = ChatId("chat-rb-existing"), userMessageId = MessageId("user-rb-1"), assistantMessageId = MessageId("asst-rb-1"), wasNewChat = false)
  Then FakeMessageRepository.activeMessagesForChat(ChatId("chat-rb-existing")) is empty
  And chatRepository.deleteChat was called 0 times
```

---

### DOPG-D-07 — Rollback on new chat calls deleteChat only

```gherkin
Scenario: DOPG-D-07 — new chat rollback deletes chat cascade
  Given chatRepository recorded ChatId("chat-rb-new") with 2 messages
  When RollbackPromptSubmissionUseCase(..., wasNewChat = true) with chatId = ChatId("chat-rb-new")
  Then chatRepository.deleteChat was called exactly once with ChatId("chat-rb-new")
  And messageRepository.softDeleteMessage was called 0 times
```

---

### DOPG-D-08 — softDeleteMessage sets deleted flag (user row)

**File:** `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/repository/MessageRepositorySoftDeleteTest.kt`

```gherkin
Scenario: DOPG-D-08 — soft delete marks message deleted in DAO
  Given an in-memory Room database with one active user message MessageId("msg-soft-1")
  When MessageRepository.softDeleteMessage(MessageId("msg-soft-1"))
  Then MessageCommandDao raw query shows deleted = 1 for id "msg-soft-1"
```

---

### DOPG-D-09 — softDeleteMessage removes row from active message query

```gherkin
Scenario: DOPG-D-09 — soft-deleted message excluded from observe query
  Given getMessagesForChat Flow contains MessageId("msg-soft-2") before delete
  When softDeleteMessage(MessageId("msg-soft-2"))
  Then the next Flow emission for that chatId does not contain MessageId("msg-soft-2")
```

---

### DOPG-D-10 — Blank promptText skips ONNX and returns Allowed

```gherkin
Scenario: DOPG-D-10 — blank moderation text is allowed without port call side effect
  Given shouldModerate = true
  And promptTextForModeration = ""
  And message.content.text = ""
  When invoke completes
  Then result is Allowed
  And ModeratePromptUseCase short-circuit behavior matches existing test (blocked = false)
```

---

## 4. Orchestrator Scenarios (`:feature:chat`)

### DOPG-O-01 — Optimistic user visible before gate completes

**File:** `feature/chat/src/test/kotlin/com/browntowndev/enclave/feature/chat/InferenceOrchestratorSendTest.kt`

```gherkin
Scenario: DOPG-O-01 — optimistic text set before processPromptWithSafetyGate returns
  Given processPromptWithSafetyGate awaits a CompletableDeferred
  When sendMessage is launched with input = "Hello crew"
  Then optimisticUserMessage.content.text equals "Hello crew" before the deferred completes
```

---

### DOPG-O-02 — Gate Allowed path calls generateChatResponse after 650ms delay

```gherkin
Scenario: DOPG-O-02 — generation starts only after ChatTurnTiming delay
  Given processPromptWithSafetyGate returns Allowed immediately
  When sendMessage runs
  And advanceTimeBy(649) milliseconds
  Then generateChatResponse was called 0 times
  When advanceTimeBy(1) additional millisecond (total 650)
  Then generateChatResponse was called exactly 1 time
```

---

### DOPG-O-03 — Placeholder created inside gate before delay (not delayed 650ms)

```gherkin
Scenario: DOPG-O-03 — createAssistantPlaceholder not called from orchestrator send path
  Given processPromptWithSafetyGate returns Allowed
  When sendMessage runs before advanceTimeBy(650)
  Then chatUseCases.createAssistantPlaceholder was called 0 times from orchestrator
  And processPromptWithSafetyGate was called exactly 1 time
```

---

### DOPG-O-04 — Blocked path clears optimistic and does not call generateChatResponse

```gherkin
Scenario: DOPG-O-04 — blocked moderation aborts inference
  Given processPromptWithSafetyGate returns Blocked
  When sendMessage runs and advanceUntilIdle
  Then optimisticUserMessage is null
  And generateChatResponse was called 0 times
  And onModerationBlocked was invoked with prompt text "blocked input"
```

---

### DOPG-O-05 — Title generation launched only when wasNewChat and input non-blank

```gherkin
Scenario: DOPG-O-05 — title job guard unchanged
  Given initialChatId = null and input = "Hello crew"
  And Allowed.wasNewChat = true
  When sendMessage completes through gate
  Then generateChatTitle was called exactly 1 time with prompt "Hello crew"
  Given input = "   " (blank) and wasNewChat = true
  Then generateChatTitle was called 0 times
```

---

### DOPG-O-06 — Blocked new-chat path does not leave requestedTurnKey set

```gherkin
Scenario: DOPG-O-06 — no active turn on moderation block
  Given initialChatId = null
  And processPromptWithSafetyGate returns Blocked
  When sendMessage completes
  Then requestedTurnKey is null
```

---

## 5. ViewModel Scenarios (`:feature:chat`)

### DOPG-V-01 — onSendMessage does not call moderatePromptUseCase before orchestrator

**File:** `feature/chat/src/test/kotlin/com/browntowndev/enclave/feature/chat/ChatViewModelSendHappyPathTest.kt`

```gherkin
Scenario: DOPG-V-01 — ViewModel delegates moderation to gate (no direct ModeratePromptUseCase)
  Given ChatViewModelTestSupport harness without injecting ModeratePromptUseCase into ViewModel
  When onSendMessage is called
  Then moderatePromptUseCase was invoked 0 times at ViewModel layer
  And inferenceOrchestrator.sendMessage was invoked with shouldModerate matching mode policy
```

---

### DOPG-V-02 — Blocked send restores input and emits moderation snackbar

```gherkin
Scenario: DOPG-V-02 — user sees snackbar and prompt restored after block
  Given orchestrator invokes onModerationBlocked with "blocked input"
  And viewModel.onInputChange("blocked input") before send
  When onSendMessage completes
  Then moderationMessages first item equals "Prompt rejected due to moderation"
  And uiState.inputText equals "blocked input"
```

**Replaces:** `sendBlockedByModeration` expectation `coVerify(exactly = 0) { chatUseCases.processPrompt(any()) }` → must assert gate/rollback instead.

---

### DOPG-V-03 — Blocked send runs persist before rollback (gate invoked)

```gherkin
Scenario: DOPG-V-03 — moderation after persist
  Given processPromptWithSafetyGate returns Blocked
  When onSendMessage completes
  Then chatUseCases.processPromptWithSafetyGate was called exactly 1 time before onModerationBlocked
  And chatUseCases.processPrompt was called 0 times from ViewModel send path
```

---

### DOPG-V-04 — Trusted model bypass still sends with shouldModerate false

```gherkin
Scenario: DOPG-V-04 — trusted bypass reaches orchestrator
  Given ShouldModeratePromptUseCase returns false for active mode
  And moderation port would block if called
  When onSendMessage with input "blocked input but trusted"
  Then inferenceOrchestrator.sendMessage was called with shouldModerate = false
  And processPromptWithSafetyGate or processPrompt path completes (generation started after 650ms)
```

**Replaces:** `sendBypassesModerationWhenTrusted` — keep `processPrompt` verify updated to `processPromptWithSafetyGate` when orchestrator wired.

---

## 6. Mutation-Defense Scenarios

### DOPG-M-01 — Removing rollback call leaves Blocked test failing (user row remains)

```gherkin
Scenario: DOPG-M-01 — rollback is mandatory for block semantics
  Given ProcessPromptWithSafetyGateUseCaseTest.DOPG-D-05
  When RollbackPromptSubmissionUseCase invoke body is commented out (mutation simulation)
  Then FakeMessageRepository still contains the user message for chat-existing-3
  And the test assertion "active messages empty" fails
```

**Implementation note:** Use a dedicated test double or `@Disabled` mutation comment in Red phase only; production code must invoke rollback.

---

### DOPG-M-02 — Reordering gate to moderate-before-persist fails DOPG-D-03 / DOPG-V-03

```gherkin
Scenario: DOPG-M-02 — persist-before-moderate order is enforced
  Given ProcessPromptWithSafetyGateUseCase reordered to call moderatePromptUseCase before createUserMessageUseCase
  When DOPG-D-03 RecordingCallOrderPort test runs
  Then expected call order ["createUser", "createPlaceholder", "moderate"] does not match
  And DOPG-V-03 fails because placeholder cannot exist before moderation without persist
```

---

## 7. Regression Guard Scenarios (unchanged behavior)

| ID | Command fragment | Expectation |
|----|------------------|-------------|
| DOPG-R-01 | `--tests "*ModeratePromptUseCaseTest*"` | Koala + criminal parallel scoring unchanged |
| DOPG-R-02 | `--tests "*ShouldModeratePromptUseCaseTest*"` | Trusted domain bypass logic unchanged |
| DOPG-R-03 | `--tests "*CreateUserMessageUseCaseTest*"` | User-only persist + chat naming unchanged |

---

## 8. Executable Validation (implementation complete)

Run in order:

1. `./gradlew :core:domain:testDebugUnitTest --tests "com.browntowndev.enclave.domain.usecase.chat.ProcessPromptWithSafetyGateUseCaseTest" --tests "com.browntowndev.enclave.domain.usecase.chat.RollbackPromptSubmissionUseCaseTest" --no-daemon`
2. `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.enclave.core.data.repository.MessageRepositorySoftDeleteTest" --no-daemon`
3. `./gradlew :feature:chat:testDebugUnitTest --tests "com.browntowndev.enclave.feature.chat.InferenceOrchestratorSendTest" --tests "com.browntowndev.enclave.feature.chat.ChatViewModelSendHappyPathTest" --no-daemon`
4. `./gradlew :core:domain:assembleDebug :feature:chat:assembleDebug :app:assembleDebug --no-daemon`
5. `./gradlew :core:domain:ktlintCheck :core:data:ktlintCheck :feature:chat:ktlintCheck --no-daemon`

All tests green before `TASK_STATUS: COMPLETE`.

---

## 9. Self-Review Checklist

- [x] Every spec task T-1…T-12 maps to at least one scenario ID
- [x] Literal snackbar, delay, and fixture scores specified
- [x] Mutation-defense scenarios DOPG-M-01 and DOPG-M-02 included
- [x] Exact Gradle commands with `com.browntowndev.enclave` packages
- [x] Documents breaking change to `sendBlockedByModeration` and OSDA-O-03 delay semantics
- [x] No TBD/TODO placeholders