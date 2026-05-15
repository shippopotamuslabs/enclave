# AGENT-007 — Turn I/O types (`ModelRequest` / `ModelResponse`)

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E1 — Rust domain model |
| **Depends on** | AGENT-004 |
| **Blocks** | AGENT-017, AGENT-027 |

## Description

Types crossing the host boundary:

- `ModelRequest { session_messages: Vec<AgentMessage>, system_prompt: String, tools_schema_json: String, allow_tool_call: bool }`
  - `tools_schema_json`: raw JSON blob assembled by the host transport (provider-specific schema format); engine passes it through opaquely
- `ModelResponse { assistant_text: Option<String>, tool_calls: Vec<RawToolCall>, finish_reason: FinishReason }`
- `RawToolCall { id: Option<String>, name: String, arguments_json: String }`
- `FinishReason`: `ToolUse`, `Stop`, `MaxTokens`, `Other(String)`

### `AgentAction` (state machine yields)

```rust
pub enum AgentAction {
    CallModel(ModelRequest),
    ExecuteTools(Vec<ToolCallRecord>),    // after repair pipeline
    RequestSummary(SummaryRequest),       // see AGENT-012 for SummaryRequest type
    Finished,
    Failed(AgentError),
}
```

`EmitEvent` is **not** a variant — events accumulate internally and are drained by `AgentEngine::drain_events(handle) -> Vec<AgentEvent>` (added in AGENT-049).

Document in module rustdoc how Kotlin maps these to provider SDK types.

## Acceptance criteria

- [ ] Tests: empty model response, parallel multi-tool response
- [ ] `AgentAction` variants (no `EmitEvent`) covered by fixture tests in AGENT-020
- [ ] `FinishReason::Other` handles any unknown string from provider

## Files

- `third_party/agent_core/src/turn_io.rs`
- `third_party/agent_core/src/action.rs`
