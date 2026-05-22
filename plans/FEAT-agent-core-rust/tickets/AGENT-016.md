# AGENT-016 — Auto-finalize incomplete artifacts

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E3 — Tool repair & guardrails |
| **Depends on** | AGENT-005, AGENT-017 |
| **Blocks** | AGENT-031 |

## Description

Port post-loop logic from `LlmToolingOrchestrator` (`LlmToolingOrchestrator.kt:278-320`):

- Track `pending_artifact_ids: HashSet<String>` in session state
  - Add to set on successful `start_artifact` (result JSON contains `{ "status": "success", "artifactId": "..." }`)
  - Remove from set on `append_blocks` result with `isComplete = true`
  - Add/keep in set on `append_blocks` result with `isComplete = false`
- On terminal `Finished`, if `pending_artifact_ids` is non-empty:
  - Engine yields `AgentAction::ExecuteTools` with synthetic `append_blocks` calls: `{ "artifactId": "<id>", "blocks": [], "isComplete": true }` for each pending id
  - Engine then transitions to `Finished`
- Synthetic auto-finalize calls are **not** counted in `tool_call_count` for UI metrics (they are excluded from the guardrail counter per `LlmToolingOrchestrator.kt:1281-1362` test `artifact auto-finalize does not inflate finished toolCallCount for UI budgets`)

## Acceptance criteria

- [ ] Rust tests mirror `LlmToolingOrchestratorAutoFinalizeTest.kt`
- [ ] Engine yields `ExecuteTools` before `Finished` when finalize needed
- [ ] Auto-finalize calls excluded from `tool_call_count`
- [ ] Failed finalize (tool executor returns error) logs warning but session transitions to `Finished` anyway (does not crash)
- [ ] `pending_artifact_ids` tracking correct across interleaved `start_artifact` + `append_blocks` calls

## Files

- `third_party/agent_core/src/artifact_finalize.rs`
