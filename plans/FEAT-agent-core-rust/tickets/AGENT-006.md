# AGENT-006 — `AgentError` & event types

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E1 — Rust domain model |
| **Depends on** | AGENT-004 |
| **Blocks** | AGENT-017, AGENT-021 |

## Description

Define errors and observability events:

### `AgentError`

```rust
pub enum AgentError {
    UnsupportedTool { name: String },
    RepairFailed { name: String, reason: String },
    GuardrailExceeded { kind: String },
    CompactionFailed { tier: CompactionTier, reason: String },
    InvalidTransition { from: String, to: String },
    Internal(String),
}
```

### `CompactionTrigger`

```rust
pub enum CompactionTrigger {
    Preflight,        // run during create_session before first CallModel
    MidTurn,          // run at top of turn loop when pressure >= threshold
    PostToolResult,   // run after appending tool results
}
```

### `CompactionEvent`

```rust
pub struct CompactionEvent {
    pub tier: CompactionTier,
    pub tokens_before: u32,
    pub tokens_after: u32,
    pub trigger: CompactionTrigger,
}
```

### `CompactionTier`

```rust
pub enum CompactionTier { Micro, Fifo, Summary, ArtifactSquash }
```

### `AgentEvent`

```rust
pub enum AgentEvent {
    TurnStarted { turn_index: u32, allow_tool_call: bool },
    TurnCompleted { turn_index: u32, tool_call_count: u32, terminal: bool },
    CompactionApplied(CompactionEvent),
    ContextPressure { ratio: f32 },
    ToolRepaired { original_name: String, resolved_name: String, confidence: f32 },
    ToolSoftFailure { tool_name: String, turn_index: u32 },
    KvCacheInvalidated { reason: String },   // emitted when kv_fixed session compacts
}
```

Events accumulate in an internal `Vec<AgentEvent>` per session, drained by `AgentEngine::drain_events(handle)` (defined in AGENT-017/AGENT-049). Prepare subset for UniFFI record export in AGENT-021.

## Acceptance criteria

- [ ] All variants implement `Display` and `serde::Serialize`
- [ ] `KvCacheInvalidated` event defined and drainable
- [ ] Events accumulate in session and drain via engine API (test: emit 3 events, drain, assert vec len 3, second drain empty)

## Files

- `third_party/agent_core/src/error.rs`
- `third_party/agent_core/src/events.rs`
