# Test Spec: Optimistic Send + Decoupled Assistant Persistence

**Ticket ID:** `2026-05-20-optimistic-send-decoupled-assistant`  
**Companion spec:** `plans/2026-05-20-optimistic-send-decoupled-assistant/spec.md`

---

## 1. Test Framework and Run Commands

| Concern | Command |
|---------|---------|
| Domain unit tests (primary) | `./gradlew :core:domain:testDebugUnitTest --tests "com.browntowndev.pocketcrew.domain.usecase.chat.CreateUserMessageUseCaseTest" --tests "com.browntowndev.pocketcrew.domain.usecase.chat.CreateAssistantPlaceholderUseCaseTest" --tests "com.browntowndev.pocketcrew.domain.usecase.chat.MergeDbMessagesWithOptimisticUseCaseTest" --no-daemon` |
| Chat feature unit tests | `./gradlew :feature:chat:testDebugUnitTest --tests "com.browntowndev.pocketcrew.feature.chat.InferenceOrchestratorSendTest" --tests "com.browntowndev.pocketcrew.feature.chat.ChatViewModelTest" --tests "com.browntowndev.pocketcrew.feature.chat.ChatViewModelGenerationStateTransitionTest" --tests "com.browntowndev.pocketcrew.feature.chat.components.messagelist.MessageListScrollBehaviorTest" --no-daemon` |
| Full module regression (chat) | `./gradlew :feature:chat:testDebugUnitTest --no-daemon` |
| Full module regression (domain) | `./gradlew :core:domain:testDebugUnitTest --no-daemon` |
| Style / static (pre-merge) | `./gradlew ktlintCheck` and `./gradlew detekt` |
| Compile smoke | `./gradlew :feature:chat:assembleDebug :core:domain:assembleDebug` |

**Frameworks:** JUnit 5 (`@Test`, `@DisplayName`, `@ExtendWith(MainDispatcherExtension::class)`), `runTest` + `StandardTestDispatcher`, Turbine for `StateFlow` where applicable, MockK only for orchestrator/repository boundaries (prefer Fakes when available).

**Test file paths (create if missing):**

| File | Package |
|------|---------|
| `core/domain/src/test/kotlin/.../CreateAssistantPlaceholderUseCaseTest.kt` | `com.browntowndev.pocketcrew.domain.usecase.chat` |
| `core/domain/src/test/kotlin/.../MergeDbMessagesWithOptimisticUseCaseTest.kt` | same |
| `feature/chat/src/test/kotlin/.../InferenceOrchestratorSendTest.kt` | `com.browntowndev.pocketcrew.feature.chat` |

---

## 2. Scenario Index

| ID | Category | Title |
|----|----------|-------|
| OSDA-D-01 | Happy | New chat persists user only — no assistant `saveMessage` |
| OSDA-D-02 | Happy | Existing chat persists user only |
| OSDA-D-03 | Happy | Chat name generation unchanged for new chat |
| OSDA-D-04 | Happy | Client user `MessageId` preserved through save |
| OSDA-D-05 | Happy | Assistant placeholder empty content and `createdAt = user + 1` |
| OSDA-D-06 | Happy | Assistant placeholder uses caller-supplied `MessageId` |
| OSDA-D-07 | Edge | Assistant placeholder does not create chat |
| OSDA-D-08 | Happy | Merge appends optimistic user when absent from DB |
| OSDA-D-09 | Happy | Merge drops optimistic when DB has same id |
| OSDA-D-10 | Edge | Merge keeps chronological order by `createdAt` |
| OSDA-O-01 | Happy | Orchestrator sets optimistic before `processPrompt` |
| OSDA-O-02 | Happy | `processPrompt` invoked before `createAssistantPlaceholder` |
| OSDA-O-03 | Happy | 500ms delay between user persist and assistant persist |
| OSDA-O-04 | Error | Failed `processPrompt` clears optimistic |
| OSDA-O-05 | Happy | `generateChatResponse` uses pre-assigned assistant id |
| OSDA-V-01 | Happy | UI message list includes optimistic user before DB row |
| OSDA-V-02 | Happy | UI message list omits optimistic after DB confirms id |
| OSDA-V-03 | Error | `onSendMessage` error clears optimistic and shows handler |
| OSDA-ML-01 | Happy | Visible messages include only user when DB is user-only |
| OSDA-ML-02 | Edge | Defer disabled yields full list once assistant arrives |
| OSDA-ML-03 | Happy | MessageList reveal delay equals `ChatTurnTiming` constant |
| OSDA-M-01 | Mutation | Combined user+assistant transaction fails OSDA-D-01 |
| OSDA-M-02 | Mutation | Skipping merge use case fails OSDA-V-01 |
| OSDA-M-03 | Mutation | Zero-delay orchestrator fails OSDA-O-03 |

---

## 3. Happy Path Scenarios

### OSDA-D-01 — New chat persists user only

**File:** `CreateUserMessageUseCaseTest.kt`

```gherkin
Scenario: OSDA-D-01 — null chatId saves chat and user without assistant
  Given messageRepository.saveMessage is tracked
  And chatRepository.createChat succeeds with ChatId "chat-new-1"
  And a user Message with chatId null and content text "Hello"
  When CreateUserMessageUseCase is invoked
  Then messageRepository.saveMessage is called exactly 1 time
  And chatRepository.createChat is called exactly 1 time
  And the saved message role equals USER
  And UserPromptResult.chatId equals ChatId "chat-new-1"
  And UserPromptResult.userMessageId equals MessageId "user-msg-id"
```

---

### OSDA-D-02 — Existing chat persists user only

**File:** `CreateUserMessageUseCaseTest.kt`

```gherkin
Scenario: OSDA-D-02 — existing chatId saves single user row
  Given existingChatId equals ChatId "existing-chat-99"
  And a user Message with chatId existingChatId
  When CreateUserMessageUseCase is invoked
  Then messageRepository.saveMessage is called exactly 1 time
  And chatRepository.createChat is called exactly 0 times
  And UserPromptResult.chatId equals existingChatId
```

---

### OSDA-D-03 — Chat name generation unchanged

**File:** `CreateUserMessageUseCaseTest.kt`

```gherkin
Scenario: OSDA-D-03 — five-word cap name generation still applies
  Given a user Message with chatId null and content text "one two three four five six"
  When CreateUserMessageUseCase is invoked
  Then captured chat name equals "one two three four five"
```

---

### OSDA-D-04 — Client MessageId preserved

**File:** `CreateUserMessageUseCaseTest.kt`

```gherkin
Scenario: OSDA-D-04 — repository returns same id as input message
  Given user MessageId "client-uuid-7a2b"
  And messageRepository.saveMessage returns MessageId "client-uuid-7a2b"
  When CreateUserMessageUseCase is invoked
  Then UserPromptResult.userMessageId equals MessageId "client-uuid-7a2b"
```

---

### OSDA-D-05 — Assistant placeholder shape

**File:** `CreateAssistantPlaceholderUseCaseTest.kt`

```gherkin
Scenario: OSDA-D-05 — placeholder is empty assistant after user timestamp
  Given chatId equals ChatId "chat-1"
  And userMessageCreatedAtMillis equals 1_700_000_000_000
  And assistantMessageId equals MessageId "asst-pre-1"
  When CreateAssistantPlaceholderUseCase is invoked
  Then saved message role equals ASSISTANT
  And saved message content.text equals ""
  And saved message createdAt equals 1_700_000_000_001
  And AssistantPlaceholderResult.assistantMessageId equals MessageId "asst-pre-1"
```

---

### OSDA-D-06 — Caller-supplied assistant id

**File:** `CreateAssistantPlaceholderUseCaseTest.kt`

```gherkin
Scenario: OSDA-D-06 — saveMessage uses orchestrator-provided id
  Given assistantMessageId equals MessageId "fixed-asst-id"
  When CreateAssistantPlaceholderUseCase is invoked
  Then saved message id equals MessageId "fixed-asst-id"
```

---

### OSDA-D-08 — Merge appends optimistic user

**File:** `MergeDbMessagesWithOptimisticUseCaseTest.kt`

```gherkin
Scenario: OSDA-D-08 — optimistic user visible when not yet in DB
  Given dbMessages contains 1 USER message with id "u-old"
  And optimisticUserMessage is USER with id "u-new" and createdAt 100
  When MergeDbMessagesWithOptimisticUseCase is invoked
  Then result size equals 2
  And result last message id equals MessageId "u-new"
```

---

### OSDA-D-09 — Merge drops optimistic on DB match

**File:** `MergeDbMessagesWithOptimisticUseCaseTest.kt`

```gherkin
Scenario: OSDA-D-09 — duplicate id prefers DB only
  Given dbMessages contains USER with id "u-same"
  And optimisticUserMessage is USER with id "u-same"
  When MergeDbMessagesWithOptimisticUseCase is invoked
  Then result size equals 1
  And result single message id equals MessageId "u-same"
  And result reference equals dbMessages
```

---

### OSDA-O-01 — Optimistic before persist

**File:** `InferenceOrchestratorSendTest.kt`

```gherkin
Scenario: OSDA-O-01 — optimistic StateFlow set before processPrompt
  Given chatUseCases.processPrompt suspends until released
  When sendMessage is called with input "Hi"
  Then optimisticUserMessage value content.text equals "Hi"
  And chatUseCases.processPrompt has not completed yet
```

---

### OSDA-O-02 — Persist ordering

**File:** `InferenceOrchestratorSendTest.kt`

```gherkin
Scenario: OSDA-O-02 — user persist completes before assistant placeholder
  Given a call-order recorder
  When sendMessage completes successfully
  Then recorder order equals ["processPrompt", "createAssistantPlaceholder", "generateChatResponse"]
```

---

### OSDA-O-03 — 500ms gap

**File:** `InferenceOrchestratorSendTest.kt`

```gherkin
Scenario: OSDA-O-03 — assistant persist delayed by ChatTurnTiming
  Given test dispatcher virtual time at 0
  When sendMessage runs until processPrompt completes
  And virtual time advances by 499 milliseconds
  Then createAssistantPlaceholder has not been called
  When virtual time advances by 1 millisecond
  Then createAssistantPlaceholder is called exactly 1 time
```

---

### OSDA-O-05 — Generation ids

**File:** `InferenceOrchestratorSendTest.kt`

```gherkin
Scenario: OSDA-O-05 — generateChatResponse receives orchestrator assistant id
  Given pre-generated assistantMessageId equals MessageId "asst-orch-99"
  When sendMessage completes
  Then generateChatResponse assistantMessageId argument equals MessageId "asst-orch-99"
  And generateChatResponse userMessageId argument equals UserPromptResult.userMessageId
```

---

### OSDA-V-01 — UI shows optimistic user

**File:** `ChatViewModelTest.kt`

```gherkin
Scenario: OSDA-V-01 — uiState lists optimistic user before DB emit
  Given dbMessagesFlow emits empty list
  And inferenceOrchestrator.optimisticUserMessage emits USER "Pending" id "opt-1"
  When uiState is collected
  Then uiState.messages last USER content.text equals "Pending"
  And uiState.messages count equals 1
```

---

### OSDA-V-02 — UI drops optimistic after DB

**File:** `ChatViewModelTest.kt`

```gherkin
Scenario: OSDA-V-02 — DB row replaces optimistic with same id
  Given optimistic USER id "opt-1" text "Pending"
  And dbMessagesFlow emits USER id "opt-1" text "Pending"
  When uiState is collected
  Then uiState.messages count equals 1
  And inferenceOrchestrator.clearOptimisticUser was invoked
```

---

### OSDA-ML-01 — User-only visible list

**File:** `MessageListScrollBehaviorTest.kt`

```gherkin
Scenario: OSDA-ML-01 — user-only chat needs no tail defer
  Given messages are USER "u2" only in a prior turn plus USER "u3"
  And deferTailBeyondIndex equals MESSAGE_LIST_DEFER_TAIL_DISABLED
  When messageListVisibleMessages runs
  Then visible messages size equals 2
  And visible messages last role equals USER
```

---

### OSDA-ML-03 — Timing constant alignment

**File:** `MessageListScrollBehaviorTest.kt` or `ChatTurnTimingTest.kt`

```gherkin
Scenario: OSDA-ML-03 — MessageList reveal delay matches domain timing
  Given ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS
  Then MESSAGE_LIST_ASSISTANT_TAIL_REVEAL_DELAY_MS equals ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS
  And MESSAGE_LIST_ASSISTANT_TAIL_REVEAL_DELAY_MS equals 500
```

---

## 4. Error and Edge Scenarios

### OSDA-D-07 — Placeholder does not create chat

**File:** `CreateAssistantPlaceholderUseCaseTest.kt`

```gherkin
Scenario: OSDA-D-07 — assistant save never creates chat
  When CreateAssistantPlaceholderUseCase is invoked
  Then chatRepository.createChat is called exactly 0 times
```

---

### OSDA-D-10 — Merge sort order

**File:** `MergeDbMessagesWithOptimisticUseCaseTest.kt`

```gherkin
Scenario: OSDA-D-10 — optimistic inserted by createdAt ordering
  Given dbMessages USER id "u1" createdAt 50
  And optimisticUserMessage USER id "u2" createdAt 150
  When MergeDbMessagesWithOptimisticUseCase is invoked
  Then result map id order equals ["u1", "u2"]
```

---

### OSDA-O-04 — processPrompt failure

**File:** `InferenceOrchestratorSendTest.kt`

```gherkin
Scenario: OSDA-O-04 — failed user persist clears optimistic
  Given chatUseCases.processPrompt throws IOException
  When sendMessage is invoked
  Then optimisticUserMessage value is null
  And createAssistantPlaceholder is called exactly 0 times
  And generateChatResponse is called exactly 0 times
```

---

### OSDA-V-03 — ViewModel send error

**File:** `ChatViewModelTest.kt`

```gherkin
Scenario: OSDA-V-03 — onSendMessage error invokes handler and no stuck optimistic
  Given chatUseCases.processPrompt throws IOException
  When onSendMessage is called
  Then ViewModelErrorHandler.handleError is invoked with tag "ChatViewModel"
  And inferenceOrchestrator.optimisticUserMessage value is null
```

---

### OSDA-ML-02 — Defer disabled with assistant present

**File:** `MessageListScrollBehaviorTest.kt`

```gherkin
Scenario: OSDA-ML-02 — full list shown when defer disabled
  Given messages USER "u1" then ASSISTANT "a1"
  And deferTailBeyondIndex equals MESSAGE_LIST_DEFER_TAIL_DISABLED
  When messageListVisibleMessages runs
  Then visible messages size equals 2
  And visible messages last id equals MessageId "a1"
```

---

## 5. Mutation-Defense Scenarios

### OSDA-M-01 — Combined transaction guard

**File:** `CreateUserMessageUseCaseTest.kt`

```gherkin
Scenario: OSDA-M-01 — assistant save in user use case fails single-save assertion
  Given an implementation that calls saveMessage twice per invoke
  When CreateUserMessageUseCase is invoked for new chat
  Then messageRepository.saveMessage call count equals 2
  And the test annotated OSDA-D-01 fails
```

**Intent:** OSDA-D-01 `saveMessage` count assertion must be exactly `1`. Any regression merging assistant back into the use case breaks CI.

---

### OSDA-M-02 — Bypass merge guard

**File:** `ChatViewModelTest.kt`

```gherkin
Scenario: OSDA-M-02 — reducer fed raw dbMessages without merge fails optimistic visibility
  Given dbMessagesFlow emits empty list
  And optimisticUserMessage is non-null USER "X"
  When ChatUiStateReducer receives dbMessages only without merge
  Then uiState.messages count equals 0
  And the test annotated OSDA-V-01 fails
```

**Intent:** Implementation must call `mergeDbMessagesWithOptimisticUseCase` (or equivalent) before `reduce`; wiring optimistic only to orchestrator without ViewModel merge must fail OSDA-V-01.

---

### OSDA-M-03 — Zero-delay guard

**File:** `InferenceOrchestratorSendTest.kt`

```gherkin
Scenario: OSDA-M-03 — immediate assistant persist fails delay assertion
  Given implementation calls createAssistantPlaceholder with zero delay
  When sendMessage runs with virtual time frozen at 0 after processPrompt
  Then createAssistantPlaceholder call count equals 1
  And the test annotated OSDA-O-03 fails
```

**Intent:** OSDA-O-03 requires `createAssistantPlaceholder` not called until virtual time ≥ 500ms after user persist.

---

## 6. Task ↔ Scenario Traceability

| Spec task | Scenario IDs |
|-----------|----------------|
| T-1 | OSDA-O-03, OSDA-ML-03 |
| T-2 | OSDA-D-01, OSDA-D-02, OSDA-D-03, OSDA-D-04, OSDA-M-01 |
| T-3 | OSDA-D-05, OSDA-D-06, OSDA-D-07 |
| T-4 | OSDA-D-08, OSDA-D-09, OSDA-D-10, OSDA-M-02 |
| T-5 | OSDA-D-01 … OSDA-D-10 |
| T-6 | OSDA-O-01 … OSDA-O-05, OSDA-M-03 |
| T-7 | OSDA-V-01, OSDA-V-02, OSDA-V-03 |
| T-8 | OSDA-V-01, OSDA-V-02 |
| T-9 | OSDA-ML-01, OSDA-ML-02, OSDA-ML-03 |
| T-10 | OSDA-D-01 … OSDA-D-10, OSDA-M-01 |
| T-11 | OSDA-O-01 … OSDA-O-05, OSDA-V-01 … OSDA-V-03, OSDA-M-02, OSDA-M-03 |
| T-12 | All |

---

## 7. Self-Review Checklist (pre-approval)

- [x] JUnit 5 + Gradle commands match `AGENTS.md` scoped commands
- [x] Every scenario has stable `OSDA-*` ID
- [x] Gherkin uses literal `Then` values (counts, ids, strings, millis)
- [x] Mutation-defense scenarios OSDA-M-01 … OSDA-M-03 included
- [x] Happy, error, and edge categories covered
- [x] Task traceability table matches `spec.md` T-1 … T-12
- [x] No TBD/TODO placeholders
- [x] File paths verified against repo layout
