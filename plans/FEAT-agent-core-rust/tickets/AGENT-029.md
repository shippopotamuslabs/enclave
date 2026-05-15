# AGENT-029 — `AgentToolExecutorAdapter`

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E6 — Kotlin adapters |
| **Depends on** | AGENT-026 |
| **Blocks** | AGENT-031 |

## Description

Implement `AgentToolExecutorCallback`:

- Input: `List<ToolCallRecord>` (repaired, from engine)
- For each `ToolCallRecord`:
  - Extract `chatId`, `userMessageId`, `assistantMessageId` from `AgentSession.session_context` (a `serde_json::Value` set in `RustAgentEngineAdapter` from the `sessionContext` parameter — AGENT-026)
  - Map `ToolCallRecord` → `ToolCallRequest` attaching those IDs
  - Delegate to `ToolExecutorPort` / `CompositeToolExecutor`
  - Map `ToolExecutionResult.resultJson` → `ToolResultRecord { call_id, result_json, truncated=false, soft_failure=<result contains top-level "error" key> }`
- Exceptions from `CompositeToolExecutor` must be caught and converted to error JSON result (`ToolResultRecord.soft_failure=true`), not propagated as JNI exceptions

## Acceptance criteria

- [ ] Existing `CompositeToolExecutorTest` still passes
- [ ] Parallel tool calls from one turn executed per existing executor semantics (no ordering guarantee change)
- [ ] Exceptions → error JSON result, not crash
- [ ] `chatId` / message id plumbing test: verify `ToolCallRequest.chatId` matches value from `session_context`
- [ ] `soft_failure = true` when result JSON contains `{ "error": ... }` at top level

## Files

- `feature/inference/.../agent/AgentToolExecutorAdapter.kt`
