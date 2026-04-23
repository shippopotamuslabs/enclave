# Technical Specification: Fix Inference Cancellation Stuckness

## 1. Objective
Ensure that inference cancellation (specifically via notification 'Stop' action) results in immediate UI state transition out of 'PROCESSING' state, even if the underlying native engine takes time to abort.

**Acceptance Criteria:**
- Clicking 'Stop' in the notification immediately clears the live snapshot for the active turn.
- Clicking 'Stop' immediately flushes the persistence session with `markIncompleteAsCancelled = true`.
- The 'ProcessingIndicator' disappears from the chat screen within < 500ms of the stop action.

## 2. System Architecture

### Target Files
- [MODIFY] [ChatInferenceService.kt](file:///home/sean/Code/pocket-crew/feature/chat-inference-service/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/service/ChatInferenceService.kt)

### Component Boundaries
- The change is confined to the Service Layer.
- It introduces `latestStartId` tracking to ensure proper `stopSelf(id)` usage.
- It introduces class-level tracking of the `persistenceSession` and `assistantMessageId` to allow external cleanup (via `ACTION_STOP`).

## 3. Data Models & Schemas
- No schema changes.
- Reuses `ActiveChatTurnKey` for identifying the turn to clear in the snapshot port.
- Reuses `MessageState` (COMPLETE) as the terminal state for cancelled messages.

## 4. API Contracts & Interfaces
- No API contract changes.

## 5. Permissions & Config Delta
- No permissions or config changes.

## 6. Constitution Audit
- This design adheres to the project's core architectural rules (AGENTS.md).
- Specifically, it respects the "Clean Architecture" mandate by keeping the orchestration in the `:app` (service) layer and using domain ports for persistence and snapshots.

## 7. Cross-Spec Dependencies
- No cross-spec dependencies.
