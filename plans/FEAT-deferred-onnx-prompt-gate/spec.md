# Spec: Deferred ONNX Prompt Gate (Optimistic Send + Post-Persist Moderation)

**Ticket ID:** `FEAT-deferred-onnx-prompt-gate`  
**Date:** 2026-06-03  
**Contracts read:** `ARCHITECTURE_RULES.md`, `DATA_LAYER_RULES.md`, `CODE_STYLE_RULES.md`, `UNIT_TESTING.md`, `INFERENCE_RULES.md`  
**Prior art:** `plans/2026-05-20-optimistic-send-decoupled-assistant/spec.md` (optimistic user + decoupled assistant placeholder)

---

## 1. Feature Summary

### Problem

`ChatViewModel.onSendMessage()` runs `ModeratePromptUseCase` (Koala + Criminal Intent ONNX, ~1–2s cold) **before** `InferenceOrchestrator.sendMessage()` → `CreateUserMessageUseCase`. The user message does not appear in `MessageList` until moderation finishes, so the first send feels frozen.

### Target send pipeline

```text
Tap send → clearFocus + clear input (existing InputBar contract)
  → optimistic USER row visible immediately (existing InferenceOrchestrator StateFlow)
  → persist USER (+ chat if new) via CreateUserMessageUseCase
  → persist ASSISTANT placeholder (MessageState.PROCESSING → IndicatorState.Processing in UI)
  → ONNX moderation (unless trusted-model bypass)
  → if BLOCKED: compensating rollback + restore prompt to InputBar + snackbar
  → if ALLOWED: detached title ONNX (new chat, non-blank prompt only) + delay 650ms + generateChatResponse
```

### Requirements

| # | Requirement |
|---|-------------|
| 1 | User message appears in `MessageList` **before** ONNX moderation completes (optimistic row + DB persist without waiting on moderation). |
| 2 | ONNX moderation runs **after** user persist and **after** assistant placeholder persist, on `Dispatchers.IO` inside `:feature:inference` ports (unchanged ONNX implementation). |
| 3 | While moderation runs, the assistant placeholder row shows `IndicatorState.Processing` (`Message.messageState` default `PROCESSING`). |
| 4 | Trusted-model bypass preserved: when `ShouldModeratePromptUseCase` returns `false` for the active mode’s model selection, skip ONNX entirely. |
| 5 | Title generation (`GenerateChatTitleUseCase` / flan-t5) runs only for **first message of a new chat** with **non-blank** prompt text, detached after successful user persist (parallel with moderation is allowed). |
| 6 | Logical transaction on block: soft-delete user + assistant messages on existing chats; `ChatRepository.deleteChat` on new chats (cascade); clear optimistic user; restore original prompt text to InputBar; emit snackbar `"Prompt rejected due to moderation"` on existing `moderationMessages` channel. |
| 7 | On allow: existing inference contract unchanged (`userMessageId`, pre-assigned `assistantMessageId`, `generateChatResponse`, `ActiveChatTurnKey`). |
| 8 | `ChatViewModel` does not call `ModeratePromptUseCase` directly; moderation + rollback orchestration lives in `:core:domain`. |
| 9 | No Room schema / entity / migration changes. |

### Non-goals

- Changing ONNX model assets, thresholds, or classifier implementations in `:feature:inference`.
- Rewriting `MessageList` scroll/defer policy (keep `ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS = 650L` between gate **Allowed** and `generateChatResponse` unless QA proves redundant).
- Studio / multimodal send paths (`MultimodalViewModel`) — out of scope unless product explicitly expands ticket.
- Persisting moderation scores or blocked-prompt audit rows (no new tables).

### Current path (verified 2026-06-03)

| Step | Location | Behavior |
|------|----------|----------|
| Moderation | `ChatViewModel.onSendMessage` L614–626 | `shouldModerate` → `moderatePromptUseCase(input)` → early return + snackbar |
| Send | `ChatViewModel` L640–648 | `inferenceOrchestrator.sendMessage(...)` |
| Optimistic + persist | `InferenceOrchestrator.sendMessage` L109–153 | `_optimisticUserMessage`, `processPrompt`, title job, delay 650ms, `createAssistantPlaceholder`, `generateChatResponse` |
| User-only persist | `CreateUserMessageUseCase` | Transaction: optional `createChat` + single `saveMessage` (USER) |
| Placeholder | `CreateAssistantPlaceholderUseCase` | Empty assistant, `createdAt = userCreatedAt + 1` |
| Message delete port | `MessageRepository` | **No** `softDelete` / `deleteMessage` today |
| Data soft-delete | `MessageCommandDao.softDeleteById` | `UPDATE message SET deleted = 1 ...` |
| Chat delete | `ChatRepository.deleteChat` | `chatDao.softDeleteById` (cascade messages) |
| Blocked send test | `ChatViewModelSendHappyPathTest.sendBlockedByModeration` | Expects moderation **before** `processPrompt` — **must update** |

---

## 2. File Structure Table

| Action | Path | Responsibility |
|--------|------|----------------|
| **Create** | `core/domain/.../usecase/chat/ProcessPromptWithSafetyGateUseCase.kt` | Orchestrate persist → placeholder → moderate → allow/block |
| **Create** | `core/domain/.../usecase/chat/RollbackPromptSubmissionUseCase.kt` | Compensating deletes on moderation block |
| **Create** | `core/domain/.../usecase/chat/PromptSafetyGateResult.kt` | Sealed allow/block outcomes (same package as use case or dedicated file) |
| **Modify** | `core/domain/.../port/repository/MessageRepository.kt` | Add `softDeleteMessage(messageId)` port |
| **Modify** | `core/data/.../repository/MessageRepositoryImpl.kt` | Delegate soft-delete to `MessageCommandDao` |
| **Modify** | `core/data/.../repository/message/MessagePersistenceCoordinator.kt` | Optional: centralize soft-delete if coordinator owns commands |
| **Modify** | `core/domain/.../usecase/chat/ChatUseCases.kt` | Expose gate use case + facade method |
| **Modify** | `core/domain/.../usecase/chat/ChatUseCasesImpl.kt` | Wire gate + rollback |
| **Modify** | `feature/chat/.../delegates/InferenceOrchestrator.kt` | Call gate; reorder placeholder before moderation; callback on block |
| **Modify** | `feature/chat/.../ChatViewModel.kt` | Remove pre-send moderation; pass `shouldModerate` + handle block callback |
| **Modify** | `feature/chat/.../di/ChatUseCasesModule.kt` | Only if manual bindings required (prefer `@Inject` constructor) |
| **Create** | `core/domain/.../test/.../ProcessPromptWithSafetyGateUseCaseTest.kt` | Gate + rollback contracts |
| **Create** | `core/domain/.../test/.../RollbackPromptSubmissionUseCaseTest.kt` | Rollback matrix |
| **Create** | `core/data/.../test/.../MessageRepositorySoftDeleteTest.kt` | Port implementation |
| **Modify** | `feature/chat/.../test/.../InferenceOrchestratorSendTest.kt` | Pipeline ordering (gate, placeholder-before-generate, delay after allow) |
| **Modify** | `feature/chat/.../test/.../ChatViewModelSendHappyPathTest.kt` | Moderation-after-persist + rollback assertions |
| **Modify** | `feature/chat/.../test/.../ChatViewModelTestSupport.kt` | Inject gate / remove direct `ModeratePromptUseCase` from send path mocks |

**No changes expected:** ONNX classifiers in `:feature:inference`, `ModeratePromptUseCase` scoring logic, `ShouldModeratePromptUseCase`, `GenerateChatResponseUseCase` public API, Room entities.

---

## 3. API Contracts

### 3.1 `MessageRepository.softDeleteMessage` (new port)

**Path:** `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/repository/MessageRepository.kt`

```kotlin
/**
 * Soft-deletes a message (sets deleted=1, pending_sync=1).
 * Used for moderation rollback — not for user-initiated delete UI.
 */
suspend fun softDeleteMessage(messageId: MessageId)
```

| Rule | Detail |
|------|--------|
| Implementation | `:core:data` → `MessageCommandDao.softDeleteById(id, System.currentTimeMillis())` |
| Sync | Follow existing command DAO pattern (`pending_sync = 1`) |
| Observable queries | Soft-deleted rows must not appear in `getMessagesForChat` Flow (existing `deleted = 0` filters) |
| Schema | **No** new columns |

### 3.2 `RollbackPromptSubmissionUseCase` (new)

**Path:** `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/RollbackPromptSubmissionUseCase.kt`

```kotlin
class RollbackPromptSubmissionUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val transactionProvider: TransactionProvider,
)

suspend operator fun invoke(
    chatId: ChatId,
    userMessageId: MessageId,
    assistantMessageId: MessageId,
    wasNewChat: Boolean,
)
```

| `wasNewChat` | Action |
|--------------|--------|
| `true` | `chatRepository.deleteChat(chatId)` — cascade removes user + assistant rows |
| `false` | Inside `transactionProvider.runInTransaction`: `softDeleteMessage(userMessageId)` then `softDeleteMessage(assistantMessageId)` |

| Rule | Detail |
|------|--------|
| Idempotency | Second invoke on already-deleted ids must not throw (DAO update 0 rows is acceptable) |
| Title side-effect | If `GenerateChatTitleUseCase` already renamed chat, `deleteChat` removes chat — acceptable for blocked first message |
| No rollback of | Optimistic StateFlow (presentation), inference jobs (none started on block) |

### 3.3 `ProcessPromptWithSafetyGateUseCase` (new)

**Path:** `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ProcessPromptWithSafetyGateUseCase.kt`

```kotlin
data class PromptSafetyGateRequest(
    val message: Message,
    val promptTextForModeration: String,
    val shouldModerate: Boolean,
    val assistantMessageId: MessageId,
)

sealed interface PromptSafetyGateResult {
    data class Allowed(
        val userResult: UserPromptResult,
        val assistantMessageId: MessageId,
        val wasNewChat: Boolean,
    ) : PromptSafetyGateResult

    data class Blocked(
        val moderationResult: PromptModerationResult,
    ) : PromptSafetyGateResult
}

class ProcessPromptWithSafetyGateUseCase @Inject constructor(
    private val createUserMessageUseCase: CreateUserMessageUseCase,
    private val createAssistantPlaceholderUseCase: CreateAssistantPlaceholderUseCase,
    private val moderatePromptUseCase: ModeratePromptUseCase,
    private val rollbackPromptSubmissionUseCase: RollbackPromptSubmissionUseCase,
)

suspend operator fun invoke(request: PromptSafetyGateRequest): PromptSafetyGateResult
```

**Ordered steps inside `invoke`:**

1. `wasNewChat = request.message.chatId == null`
2. `userResult = createUserMessageUseCase(request.message)` — on failure, propagate exception (no rollback needed)
3. `createAssistantPlaceholderUseCase(chatId = userResult.chatId, userMessageCreatedAtMillis = userResult.userMessageCreatedAtMillis, assistantMessageId = request.assistantMessageId)`
4. If `request.shouldModerate`:
   - `result = moderatePromptUseCase(request.promptTextForModeration)`
   - If `result.blocked`: `rollbackPromptSubmissionUseCase(chatId = userResult.chatId, userMessageId = userResult.userMessageId, assistantMessageId = request.assistantMessageId, wasNewChat = wasNewChat)` → return `Blocked(result)`
5. Return `Allowed(userResult, request.assistantMessageId, wasNewChat)`

| Rule | Detail |
|------|--------|
| Blank prompt | `ModeratePromptUseCase("")` returns `blocked = false` — gate still Allowed |
| Moderation IO | Stays inside existing ports (`PromptModerationPort`, `CriminalIntentModerationPort`) in `:feature:inference` |
| Placeholder state | Rely on `Message` default `messageState = MessageState.PROCESSING` in `CreateAssistantPlaceholderUseCase` |
| No ONNX in ViewModel | ViewModel only computes `shouldModerate` boolean(s) and passes into orchestrator |

### 3.4 `ChatUseCases` facade (modified)

**Path:** `core/domain/.../ChatUseCases.kt`, `ChatUseCasesImpl.kt`

| Member | Type |
|--------|------|
| `processPromptWithSafetyGateUseCase` | `ProcessPromptWithSafetyGateUseCase` |
| `suspend fun processPromptWithSafetyGate(request: PromptSafetyGateRequest): PromptSafetyGateResult` | Delegates to use case |

Existing `processPrompt`, `createAssistantPlaceholder` remain for direct callers/tests; **send path** uses gate only.

### 3.5 `InferenceOrchestrator.sendMessage` (modified)

**Path:** `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/delegates/InferenceOrchestrator.kt`

**New parameters:**

```kotlin
fun sendMessage(
    scope: CoroutineScope,
    input: String,
    selectedMode: ChatModeUi,
    selectedImageUris: List<String>,
    fileAttachments: List<FileAttachment>,
    initialChatId: ChatId?,
    shouldModerate: Boolean,
    onInputCleared: () -> Unit,
    onModerationBlocked: (promptText: String) -> Unit,
)
```

**Reordered steps (non-empty input branch):**

1. Pre-generate `assistantMessageId` (unchanged).
2. Build `domainMessage`, set `_optimisticUserMessage`, `onInputCleared()` (unchanged).
3. `when (chatUseCases.processPromptWithSafetyGate(...))`:
   - **`Blocked`**: `_optimisticUserMessage = null`; `onModerationBlocked(input)`; `return@launch`
   - **`Allowed`**: continue below
4. `_currentChatId`, `savedStateHandle`, update optimistic with persisted ids (unchanged).
5. If `wasNewChat && input.isNotBlank()`: detached `scope.launch { generateChatTitle(...) }` (unchanged).
6. `delay(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)` — **after** gate Allowed, **before** `generateChatResponse` only (placeholder already exists from gate step 3).
7. Set `_requestedTurnKey`, launch `generateChatResponse` (unchanged IDs).

**Removed from orchestrator:** direct `processPrompt` + `createAssistantPlaceholder` calls on send path.

### 3.6 `ChatViewModel.onSendMessage` (modified)

**Path:** `feature/chat/.../ChatViewModel.kt`

| Change | Detail |
|--------|--------|
| Keep | `shouldModerate` resolution via `shouldModeratePromptUseCase` per `ChatModeUi` (lines 604–612) |
| Remove | `moderatePromptUseCase` invocation and early return before orchestrator |
| Remove constructor dep | `moderatePromptUseCase: ModeratePromptUseCase` if unused elsewhere in ViewModel |
| Add orchestrator args | `shouldModerate = shouldModerate`, `onModerationBlocked = { prompt -> lastBlockedPrompt = prompt; restore input; send snackbar }` |
| Snackbar literal | `PROMPT_REJECTED_MESSAGE = "Prompt rejected due to moderation"` (existing constant) |
| Input restore | `chatInputCoordinator.onInputChange(prompt)` (restores blocked prompt to InputBar) |

### 3.7 Presentation mapping (verify only)

**Paths:** `ChatMessageUiMapper`, `ChatUiStateReducer`

- Assistant placeholder with `MessageState.PROCESSING` maps to `IndicatorState.Processing` (existing).
- During ONNX, UI reads placeholder from DB Flow — no new DB columns for “moderating”.

---

## 4. Data Model / Schema Changes

**None.** Schema lock per `DATA_LAYER_RULES.md`:

- No `is_moderating`, `is_pending_send`, or other transient flags in Room.
- Soft-delete uses existing `message.deleted` column via `MessageCommandDao.softDeleteById`.
- Chat removal uses existing `ChatRepository.deleteChat` / `ChatDao.softDeleteById`.

---

## 5. Constitution Audit

| Rule | Compliance |
|------|------------|
| Clean architecture | New orchestration in `:core:domain`; ONNX stays in `:feature:inference` via ports |
| Domain purity | No Android/Compose types in new use cases or result types |
| Dependency direction | `:feature:chat` → `:core:domain` only; data implements new port method |
| INFERENCE_RULES §7 | Utility ONNX off main thread; not behind `InferenceLockManager`; silent degrade only inside classifiers — **block** is explicit product policy via gate |
| UI events | Snackbar via existing `Channel` + `LaunchedEffect` in `ChatRoute` |
| Test mandate | Gherkin scenarios in `test_spec.md` with exact literals |
| Schema lock | No waiver required |

---

## 6. Ordered Implementation Tasks

| Task | File(s) | Scenario IDs |
|------|---------|----------------|
| **T-1** | Add `MessageRepository.softDeleteMessage` port + data implementation | DOPG-D-08, DOPG-D-09, DOPG-M-02 |
| **T-2** | Add `RollbackPromptSubmissionUseCase` | DOPG-D-06, DOPG-D-07, DOPG-D-08, DOPG-D-09 |
| **T-3** | Add `ProcessPromptWithSafetyGateUseCase` + `PromptSafetyGateResult` | DOPG-D-01 … DOPG-D-05, DOPG-D-10 |
| **T-4** | Wire `ChatUseCases` / `ChatUseCasesImpl` | DOPG-D-01 … DOPG-D-10 |
| **T-5** | Refactor `InferenceOrchestrator.sendMessage` to use gate; add `shouldModerate` + `onModerationBlocked` | DOPG-O-01 … DOPG-O-06 |
| **T-6** | Thin `ChatViewModel.onSendMessage` (remove pre-persist moderation) | DOPG-V-01 … DOPG-V-04 |
| **T-7** | Domain unit tests (`ProcessPromptWithSafetyGateUseCaseTest`, `RollbackPromptSubmissionUseCaseTest`) | DOPG-D-01 … DOPG-D-10, DOPG-M-01, DOPG-M-02 |
| **T-8** | Data test `MessageRepositorySoftDeleteTest` | DOPG-D-08, DOPG-D-09 |
| **T-9** | Update `InferenceOrchestratorSendTest` (ordering + delay semantics) | DOPG-O-01 … DOPG-O-06 |
| **T-10** | Update `ChatViewModelSendHappyPathTest` + `ChatViewModelTestSupport` | DOPG-V-01 … DOPG-V-04 |
| **T-11** | Run Gradle validation (see `test_spec.md`) | All |
| **T-12** | Manual QA: first-send perceived latency, blocked rollback on new/existing chat | DOPG-V-02, DOPG-V-03 |

**Dependency order:** T-1 → T-2 → T-3 → T-4 → T-5 → T-6 → T-7 → T-8 → T-9 → T-10 → T-11 → T-12.

---

## 7. Drift and Escalation Rules

- **Single gate owner:** All send-path ordering of persist → placeholder → moderate → rollback lives in `ProcessPromptWithSafetyGateUseCase`. Do not re-add `moderatePromptUseCase` to `ChatViewModel` without updating DOPG-V-01 and DOPG-M-01.
- **Placeholder before ONNX:** Assistant placeholder must exist before `ModeratePromptUseCase` runs. Do not move moderation before `createAssistantPlaceholder` without updating DOPG-D-03 and DOPG-O-04.
- **Delay authority:** `ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS` (650L) applies between gate **Allowed** and `generateChatResponse`, not between user persist and placeholder. Do not reintroduce pre-moderation delay before placeholder without updating DOPG-O-03.
- **Rollback matrix:** New chat → `deleteChat` only; existing chat → two `softDeleteMessage` calls. Inverting this breaks DOPG-D-07 / DOPG-D-09.
- **Trusted bypass:** `shouldModerate = false` must skip ONNX but still run persist + placeholder (DOPG-D-04, DOPG-V-04).
- **Title generation:** Only `wasNewChat && prompt.isNotBlank()` in orchestrator; do not gate title on moderation success beyond block rollback deleting the chat.
- **Schema waiver:** Any `is_blocked` column, moderation audit table, or persisting ONNX scores requires explicit **Contract Waiver** per `ARCHITECTURE_RULES.md` / `DATA_LAYER_RULES.md`.
- **Studio parity:** `MultimodalViewModel` moderation-before-send is a separate ticket unless explicitly merged.

---

## 8. Self-Review Checklist (pre-approval)

- [x] Verified `ChatViewModel.kt` moderation-before-orchestrator (L614–626)
- [x] Verified `InferenceOrchestrator.kt` optimistic → processPrompt → title → delay → placeholder → generate
- [x] Verified `CreateUserMessageUseCase` user-only transaction
- [x] Verified `MessageRepository` lacks delete; `MessageCommandDao.softDeleteById` exists
- [x] Verified `ChatRepository.deleteChat` exists
- [x] Verified `ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS = 650L`
- [x] Verified `sendBlockedByModeration` expects pre-persist moderation (will flip)
- [x] No schema change declared
- [x] Every task maps to scenario IDs in `test_spec.md`
- [x] No TBD/TODO placeholders

---

### PLAN COMPLETE

* spec.md:      `plans/FEAT-deferred-onnx-prompt-gate/spec.md`      (12 tasks)
* test_spec.md: `plans/FEAT-deferred-onnx-prompt-gate/test_spec.md` (22 scenarios + mutation defense)
* Self-review:  COMPLETE against contracts and verified code paths

**HUMAN GATE:** Approve `spec.md` and `test_spec.md` before TDD Red or implementation.