# AGENT-037 — Slim `ChatHistoryRehydrator` to load-only

| Field | Value |
|-------|--------|
| **Type** | Refactor |
| **Epic** | E8 — Preflight unification |
| **Depends on** | AGENT-018, AGENT-028, AGENT-036 |
| **Blocks** | — |

## Description

Refactor `ChatHistoryRehydrator` to only:

- Load messages from repository
- Apply vision metadata / summary pointer for DB slice
- Build initial `List<ChatMessage>` for session

Remove from rehydrator:

- `ChatHistoryCompressor` FIFO
- `RollingSummarizer` invocation
- `ContextWindowPlanner` exceed handling

Compaction runs in Rust `create_session` preflight (AGENT-018).

## Acceptance criteria

- [ ] `ChatHistoryRehydrator` unit tests updated
- [ ] `DirectChatInferenceExecutor` and `ChatInferenceService` share identical rehydration behavior
- [ ] No duplicate FIFO in Kotlin integration tests

## Files

- `core/domain/.../ChatHistoryRehydrator.kt`
- Related tests
