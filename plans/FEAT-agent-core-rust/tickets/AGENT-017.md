# AGENT-017 — Session state machine (`AgentEngine`)

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E4 — Agent loop engine |
| **Depends on** | AGENT-007, AGENT-008, AGENT-009, AGENT-014, AGENT-015, AGENT-046 |
| **Blocks** | AGENT-016, AGENT-018, AGENT-021, AGENT-041 |

## Description

Implement `AgentEngine` state machine. Engine is generic over native Rust host traits defined in AGENT-046.

### API

```rust
pub struct AgentEngine<T: AgentTransport, E: AgentToolExecutor, S: AgentSummarizer> {
    transport: Arc<T>,
    executor: Arc<E>,
    summarizer: Arc<S>,
}

impl<T, E, S> AgentEngine<T, E, S> {
    pub fn new(transport: Arc<T>, executor: Arc<E>, summarizer: Arc<S>) -> Self;

    pub fn create_session(
        &self,
        initial_messages: Vec<AgentMessage>,
        config: AgentConfig,
        capabilities: SessionCapabilities,
        session_context: serde_json::Value,
    ) -> SessionHandle;

    pub fn next_action(&self, handle: &SessionHandle) -> AgentAction;
    pub fn submit_model_response(&self, handle: &SessionHandle, response: ModelResponse);
    pub fn submit_tool_results(&self, handle: &SessionHandle, results: Vec<ToolResultRecord>);
    pub fn submit_summary(&self, handle: &SessionHandle, summary: SummaryResult);
    pub fn cancel(&self, handle: &SessionHandle);
    pub fn drain_events(&self, handle: &SessionHandle) -> Vec<AgentEvent>;  // see AGENT-049
}
```

### Per-turn flow

1. Estimate pressure via `TokenLedger` → run `CompactionPipeline` if `should_compact`
   - `Applied`: continue to step 2 with compacted session
   - `NeedsHostCall(req)`: emit `AgentAction::RequestSummary(req)`; resume compaction on `submit_summary`
   - If `kv_fixed = true` and compaction occurred: emit `AgentEvent::KvCacheInvalidated`
2. Evaluate guardrails → set `allow_tool_call`
3. Return `AgentAction::CallModel(ModelRequest { ..., allow_tool_call })`
4. On `submit_model_response`: parse `RawToolCall`s → run `ToolRepairPipeline`
   - All repairs succeed → `AgentAction::ExecuteTools(Vec<ToolCallRecord>)`
   - Reject → inject error `ToolResultRecord` as if tool returned error (soft-failure path)
5. On `submit_tool_results`: append to session, update ledger snapshot, increment `tool_call_count` for real calls
6. Check auto-finalize (AGENT-016), then loop

**Behavior: no `IllegalStateException` on pressure.** Engine always compacts first (ADR).

### `tool_call_count` semantics

- Increments on each real tool invocation or reported error (repair reject counts as a soft-failure result)
- Does **not** increment for synthetic auto-finalize `append_blocks` calls (AGENT-016)
- Matches legacy `LlmToolingOrchestrator.kt` count for guardrail/UI purposes

## Acceptance criteria

- [ ] ≥10 integration tests: 3-tool happy path, soft-failure streak, mid-loop compact+continue, fuzzy repair, max turns, cancel, preflight compaction, kv_fixed compaction (AGENT-047 adds dedicated test), artifact auto-finalize
- [ ] No path returns `Failed(GuardrailExceeded)` solely because model called a tool under pressure without a compaction attempt
- [ ] `tool_call_count` semantics match legacy for all test scenarios

## Files

- `third_party/agent_core/src/engine/mod.rs`
- `third_party/agent_core/src/engine/state.rs`
