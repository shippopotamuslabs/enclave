# Spec: Optimistic Send + Decoupled Assistant Persistence

**Ticket ID:** `2026-05-20-optimistic-send-decoupled-assistant`  
**Date:** 2026-05-21  
**Contracts read:** `AGENTS.md`, `ARCHITECTURE_RULES.md`, `DATA_LAYER_RULES.md`, `CODE_STYLE_RULES.md`, `UNIT_TESTING.md`, `UI_DESIGN_SPEC.md` (MessageList scroll parity)

---

## 1. Feature Summary

### Problem

On send, Pocket Crew persists the user message and an empty assistant placeholder in a **single** `CreateUserMessageUseCase` transaction. Room’s `getChat` Flow then emits **user + assistant** together. `MessageList` must hide the assistant tail (`deferTailBeyondIndex`) and wait 500ms before reveal so scroll-to-latest does not fight a growing list — matching Google AI Edge Gallery behavior only approximately. Users still see scroll jank because the first meaningful layout includes two new rows and IME/focus churn overlaps scroll.

### Target behavior (both features)

```text
Tap send → clearFocus (InputBar) + clear input
  → optimistic user row visible immediately (#1)
  → persist user (+ chat if new) only — Flow emits user-only (#2)
  → MessageList: awaitFrame + animateScrollTo max (Gallery parity)
  → delay 500ms
  → persist assistant placeholder (#2)
  → generateChatResponse (same userMessageId / assistantMessageId contract)
  → drop optimistic user when DB Flow contains same MessageId (#1)
```

### Requirements

| # | Requirement |
|---|-------------|
| 1 | UI shows the outgoing user message **before** `processPrompt` completes (optimistic `Message` with client `MessageId`). |
| 2 | `CreateUserMessageUseCase` saves **user (+ chat)** only; **no** assistant `saveMessage` in that transaction. |
| 3 | New `CreateAssistantPlaceholderUseCase` saves the empty assistant row **after** a fixed 500ms delay from successful user persist (same constant as MessageList reveal delay). |
| 4 | `GenerateChatResponseUseCase` still receives stable `userMessageId` and `assistantMessageId`; user row must exist in DB before generation starts. |
| 5 | Optimistic user is removed when `dbMessages` contains a message with the same `MessageId` (no duplicate bubbles). |
| 6 | CREW mode must not create a second placeholder for `defaultAssistantMessageId` (`ChatGenerationAccumulatorManager` already seeds `assistantMessageIds[ModelType.DRAFT_ONE]`). |
| 7 | No Room schema / entity / migration changes. |

### Non-goals

- Changing inference streaming, snapshot handoff, or `MergeMessagesUseCase` (active-turn merge) semantics.
- Compose UI tests on device/emulator (unit + reducer/orchestrator tests only).
- Rewriting MessageList from Column scroll to LazyColumn (out of scope unless defer removal requires it).
- `UniversalVoiceTrailingAction` / IME: existing `clearFocus()` on send stays; no new focus contract in this ticket.

### Current path (verified)

| Step | Location | Behavior |
|------|----------|----------|
| Build `Message` | `InferenceOrchestrator.sendMessage` | Client `MessageId`, `createdAt = System.currentTimeMillis()` |
| Persist | `chatUseCases.processPrompt` → `CreateUserMessageUseCase` | One transaction: user + assistant placeholder (`createdAt = now + 1`) |
| UI list | `ChatViewModel.dbMessagesFlow` → `ChatUiStateReducer` → `MessageList` | Full list including empty assistant |
| Scroll | `MessageListScrollToLatestEffect` | `deferTailBeyondIndex` + 500ms reveal |

---

## 2. File Structure Table

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `core/domain/.../CreateUserMessageUseCase.kt` | User (+ chat) only; rename result type |
| Create | `core/domain/.../CreateAssistantPlaceholderUseCase.kt` | Delayed assistant placeholder persist |
| Create | `core/domain/.../MergeDbMessagesWithOptimisticUseCase.kt` | Pre-reducer DB + optimistic merge |
| Modify | `core/domain/.../ChatUseCases.kt` | Expose new use cases + facade methods |
| Modify | `core/domain/.../ChatUseCasesImpl.kt` | Wire implementations |
| Modify | `feature/chat/.../InferenceOrchestrator.kt` | Optimistic state, ordered send pipeline, 500ms gap |
| Modify | `feature/chat/.../ChatViewModel.kt` | Combine optimistic + merge before reducer |
| Modify | `feature/chat/.../ChatUiStateReducer.kt` | Accept merged message list (or merge inside reducer) |
| Modify | `feature/chat/.../messagelist/MessageListScrollBehavior.kt` | Update comments; align delay source |
| Modify | `feature/chat/.../messagelist/MessageList.kt` | Simplify/remove defer if decoupling makes it redundant |
| Modify | `core/domain/.../GenerateChatResponseUseCase.kt` | **Verify only** — no API change expected |
| Modify | `core/domain/.../ChatGenerationAccumulatorManager.kt` | **Verify only** — CREW must not duplicate default placeholder |
| Modify | `core/domain/.../test/.../CreateUserMessageUseCaseTest.kt` | Split assertions; remove assistant expectations |
| Create | `core/domain/.../test/.../CreateAssistantPlaceholderUseCaseTest.kt` | Placeholder contract tests |
| Create | `core/domain/.../test/.../MergeDbMessagesWithOptimisticUseCaseTest.kt` | Merge contract tests |
| Create | `feature/chat/.../test/.../InferenceOrchestratorSendTest.kt` | Send ordering + optimistic lifecycle |
| Modify | `feature/chat/.../test/.../ChatViewModelTest.kt` | Optimistic visibility + error cleanup |
| Modify | `feature/chat/.../test/.../ChatViewModelGenerationStateTransitionTest.kt` | `UserPromptResult` + `createAssistantPlaceholder` mocks |
| Modify | `feature/chat/.../test/.../ChatViewModelTestSupport.kt` | Pass new orchestrator deps if needed |
| Modify | `feature/chat/.../test/.../MessageListScrollBehaviorTest.kt` | Defer policy tests if defer simplified |

**No changes expected:** `MessageRepository`, `ChatRepositoryImpl`, Room entities, `GenerateChatResponseUseCase` public signature, Hilt modules (constructor `@Inject` only).

---

## 3. API Contracts

### 3.1 `CreateUserMessageUseCase` (modified)

**Path:** `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/CreateUserMessageUseCase.kt`

```kotlin
data class UserPromptResult(
    val userMessageId: MessageId,
    val chatId: ChatId,
)

suspend operator fun invoke(message: Message): UserPromptResult
```

| Rule | Detail |
|------|--------|
| Transaction | Single `transactionProvider.runInTransaction` for chat creation (if `message.chatId == null`) + **one** `messageRepository.saveMessage` for USER |
| Preserved IDs | Return `message.id` as `userMessageId` when repository returns same id (client UUID from orchestrator) |
| `createdAt` | Set on user message as today (`now` / `Date().time`) |
| Removed | Assistant placeholder creation and `PromptResult.assistantMessageId` |
| Chat naming | `generateChatName` behavior unchanged |

### 3.2 `CreateAssistantPlaceholderUseCase` (new)

**Path:** `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/CreateAssistantPlaceholderUseCase.kt`

```kotlin
data class AssistantPlaceholderResult(
    val assistantMessageId: MessageId,
)

suspend operator fun invoke(
    chatId: ChatId,
    userMessageCreatedAtMillis: Long,
    assistantMessageId: MessageId,
): AssistantPlaceholderResult
```

| Rule | Detail |
|------|--------|
| Message body | `role = ASSISTANT`, `content.text = ""`, `messageState` default/processing as today’s placeholder |
| `createdAt` | `userMessageCreatedAtMillis + 1` (preserves sort order after user row) |
| `chatId` | Required; must match persisted user |
| ID | **Caller-supplied** `assistantMessageId` (orchestrator generates UUID before optimistic send) so `GenerateChatResponseUseCase` and `ActiveChatTurnKey` are stable |
| Transaction | Optional single `saveMessage`; no chat creation |

### 3.3 `MergeDbMessagesWithOptimisticUseCase` (new)

**Path:** `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/MergeDbMessagesWithOptimisticUseCase.kt`

```kotlin
operator fun invoke(
    dbMessages: List<Message>,
    optimisticUserMessage: Message?,
): List<Message>
```

| Condition | Result |
|-----------|--------|
| `optimisticUserMessage == null` | `dbMessages` unchanged |
| DB contains `optimisticUserMessage.id` | `dbMessages` unchanged (optimistic superseded) |
| Else | Insert optimistic user; return list sorted by `createdAt` ascending |

### 3.4 `ChatUseCases` facade (modified)

**Path:** `core/domain/.../ChatUseCases.kt`, `ChatUseCasesImpl.kt`

| Member | Type |
|--------|------|
| `processPromptUseCase` | `CreateUserMessageUseCase` (unchanged property name) |
| `createAssistantPlaceholderUseCase` | `CreateAssistantPlaceholderUseCase` |
| `mergeDbMessagesWithOptimisticUseCase` | `MergeDbMessagesWithOptimisticUseCase` |
| `suspend fun processPrompt(message: Message)` | Returns `UserPromptResult` |
| `suspend fun createAssistantPlaceholder(...)` | Delegates to new use case |

### 3.5 Shared timing constant

**Path:** `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/ChatTurnTiming.kt` (new, small)

```kotlin
object ChatTurnTiming {
    const val ASSISTANT_PLACEHOLDER_DELAY_MS = 500L
}
```

- `InferenceOrchestrator` uses this for `delay` before `createAssistantPlaceholder`.
- `MessageListScrollBehavior.kt` references the same value for reveal delay (replace duplicate `MESSAGE_LIST_ASSISTANT_TAIL_REVEAL_DELAY_MS` **or** make MessageList import domain constant — prefer single source in domain to satisfy layer direction).

### 3.6 `InferenceOrchestrator.sendMessage` (modified)

**Path:** `feature/chat/.../InferenceOrchestrator.kt`

New state:

```kotlin
private val _optimisticUserMessage = MutableStateFlow<Message?>(null)
val optimisticUserMessage: StateFlow<Message?>
```

Ordered steps inside existing `scope.launch` (after validation):

1. Pre-generate `assistantMessageId = MessageId(UUID.randomUUID().toString())`.
2. Build `domainMessage` (USER, client `MessageId`, content, attachments).
3. `_optimisticUserMessage.value = domainMessage`.
4. `onInputCleared()` (unchanged callback).
5. `val userResult = chatUseCases.processPrompt(domainMessage)`.
6. Update `_currentChatId`, `savedStateHandle`, prepare `ActiveChatTurnKey` **but do not** set `_requestedTurnKey` until assistant exists.
7. `delay(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)`.
8. `chatUseCases.createAssistantPlaceholder(chatId, userCreatedAt, assistantMessageId)` — use `domainMessage.createdAt` or reload user from DB if persist adjusted timestamp.
9. Set `_requestedTurnKey`, launch `generateChatResponse` with `userResult.userMessageId` and pre-assigned `assistantMessageId`.
10. On failure before step 9: `_optimisticUserMessage.value = null` in existing error handler path.

`ChatViewModel` collects `optimisticUserMessage` and applies `mergeDbMessagesWithOptimisticUseCase` before `ChatUiStateReducer.reduce`.

Clear optimistic: when merged list no longer needs optimistic (merge use case drops it), set orchestrator clear — either orchestrator observes `dbMessagesFlow` (coupling) or ViewModel calls `inferenceOrchestrator.clearOptimisticUser()` when merge returns without optimistic. Prefer **ViewModel** side effect in `combine` when `dbMessages.any { it.id == optimistic.id }` to keep orchestrator free of UI Flow.

```kotlin
fun clearOptimisticUser()
```

### 3.7 `GenerateChatResponseUseCase` / CREW (verify)

- Still loads user via `messageRepository.getMessageById(userMessageId)` at start — must run **after** step 5 above.
- `ChatGenerationAccumulatorManager` constructor: `assistantMessageIds` initialized with `ModelType.DRAFT_ONE to defaultAssistantMessageId`. Lazy `chatRepository.createAssistantMessage` runs only for **additional** crew model types — **no code change** if placeholder id matches `defaultAssistantMessageId`.

### 3.8 MessageList defer policy (conditional simplify)

**Paths:** `MessageList.kt`, `MessageListLayout.kt`, `MessageListScrollBehavior.kt`

After decoupled persistence, first DB emission is typically **user-only**; scroll runs without assistant height. Implementation task:

- **Default after verification:** keep `MessageListScrollToLatestEffect` (awaitFrame + animateScroll) but set defer to `MESSAGE_LIST_DEFER_TAIL_DISABLED` when the last message in UI is USER and no assistant row follows in the same frame.
- If manual QA on target devices shows no regression, remove `deferTailBeyondIndex` state entirely in a follow-up commit within this ticket’s Task T-10.

---

## 4. Data Model / Schema Changes

**None.** No new Room tables/columns, no DataStore keys, no domain model field additions. Optimistic user is presentation-era only until DB confirms the same `MessageId`.

---

## 5. Constitution Audit

| Rule | Compliance |
|------|------------|
| Clean architecture | New use cases in `:core:domain`; ViewModel maps domain → UI via existing reducer/mapper |
| Domain purity | No Android/Compose imports in domain use cases |
| Schema lock | No transient flags in DB |
| Navigation | No `NavController` in composables |
| Test mandate | Unit tests per `test_spec.md` |
| UI contract | Gallery scroll sequence preserved; defer simplified only with scenario coverage |

---

## 6. Ordered Implementation Tasks

| Task | File(s) | Scenario IDs |
|------|---------|----------------|
| **T-1** | Add `ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS` | OSDA-O-03, OSDA-ML-03 |
| **T-2** | Split `CreateUserMessageUseCase` → `UserPromptResult`; remove assistant save | OSDA-D-01, OSDA-D-02, OSDA-D-04, OSDA-M-01 |
| **T-3** | Add `CreateAssistantPlaceholderUseCase` | OSDA-D-05, OSDA-D-06, OSDA-D-07 |
| **T-4** | Add `MergeDbMessagesWithOptimisticUseCase` | OSDA-D-08, OSDA-D-09, OSDA-D-10, OSDA-M-02 |
| **T-5** | Wire `ChatUseCases` / `ChatUseCasesImpl` | OSDA-D-01 … OSDA-D-10 |
| **T-6** | Refactor `InferenceOrchestrator.sendMessage` pipeline + optimistic `StateFlow` | OSDA-O-01 … OSDA-O-05 |
| **T-7** | `ChatViewModel`: merge optimistic + `clearOptimisticUser` when DB confirms | OSDA-V-01, OSDA-V-02, OSDA-V-03 |
| **T-8** | `ChatUiStateReducer` / combine: feed merged messages into projector | OSDA-V-01, OSDA-V-02 |
| **T-9** | Align `MessageList` delay constant; evaluate defer removal | OSDA-ML-01, OSDA-ML-02, OSDA-ML-03 |
| **T-10** | Update `CreateUserMessageUseCaseTest`; add new domain tests | OSDA-D-01 … OSDA-D-10, OSDA-M-01 |
| **T-11** | Add `InferenceOrchestratorSendTest`; update ViewModel tests | OSDA-O-01 … OSDA-O-05, OSDA-V-01 … OSDA-V-03 |
| **T-12** | Run Gradle validation (see `test_spec.md`) | All |

**Dependency order:** T-1 → T-2 → T-3 → T-4 → T-5 → T-6 → T-7 → T-8 → T-9 → T-10 → T-11 → T-12.

---

## 7. Drift and Escalation Rules

- **Single send pipeline owner:** All send ordering lives in `InferenceOrchestrator`. Do not re-introduce assistant creation inside `CreateUserMessageUseCase` without updating OSDA-D-01/OSDA-M-01.
- **ID stability:** `assistantMessageId` must be allocated before `generateChatResponse` and must match the placeholder row created by `CreateAssistantPlaceholderUseCase`. Do not generate a second UUID at placeholder time.
- **Delay coupling:** `ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS` is the only authority for persist gap and MessageList reveal timing; do not fork divergent millis in feature module.
- **Optimistic lifetime:** Never persist optimistic state to Room. If product asks for “failed send” retry UI, use a separate ticket (still no schema).
- **CREW placeholders:** Additional `createAssistantMessage` calls in `ChatGenerationAccumulatorManager` are for extra model types only; escalating duplicate default rows requires updating OSDA-D-05 and CREW tests in `ChatGenerationAccumulatorManagerTest`.
- **Contract waiver:** Re-merging user+assistant into one transaction or storing `is_pending_send` in DB requires explicit **Contract Waiver** per `AGENTS.md`.

---

## 8. Self-Review Checklist (pre-approval)

- [x] Verified `CreateUserMessageUseCase.kt` combined user + assistant save
- [x] Verified `InferenceOrchestrator.kt` calls `processPrompt` before generation
- [x] Verified `ChatViewModel` uses `dbMessagesFlow` without optimistic layer today
- [x] Verified `MessageListScrollBehavior.kt` 500ms + defer comments reference combined insert
- [x] Verified `ChatGenerationAccumulatorManager` seeds `defaultAssistantMessageId` for CREW
- [x] Verified `GenerateChatResponseUseCase` requires both message IDs
- [x] No schema change declared
- [x] Every task maps to scenario IDs in `test_spec.md`
- [x] No TBD/TODO placeholders
