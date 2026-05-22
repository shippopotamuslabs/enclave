# AGENT-046 — Native Rust host trait surface

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E1 — Rust domain model |
| **Depends on** | AGENT-007 |
| **Blocks** | AGENT-017, AGENT-022, AGENT-041 |

## Description

Define the three native Rust host traits that `AgentEngine` is generic over. These traits are the stable contract between the engine and any consumer — Android (via UniFFI shim in AGENT-022) or a future native desktop app (direct impl, no UniFFI).

```rust
// third_party/agent_core/src/host.rs

pub trait AgentTransport: Send + Sync {
    fn complete_model_turn(&self, request: ModelRequest) -> Result<ModelResponse, AgentError>;
}

pub trait AgentToolExecutor: Send + Sync {
    fn execute_tools(&self, calls: Vec<ToolCallRecord>) -> Result<Vec<ToolResultRecord>, AgentError>;
}

pub trait AgentSummarizer: Send + Sync {
    /// Called only when `SessionCapabilities.summarization_allowed = true`.
    fn summarize(&self, request: SummaryRequest) -> Result<SummaryResult, AgentError>;
}

/// No-op summarizer for local models.
pub struct NoOpSummarizer;
impl AgentSummarizer for NoOpSummarizer {
    fn summarize(&self, _: SummaryRequest) -> Result<SummaryResult, AgentError> {
        Err(AgentError::Internal("Summarizer not available".into()))
    }
}
```

AGENT-017 uses `AgentEngine<T: AgentTransport, E: AgentToolExecutor, S: AgentSummarizer>`.

AGENT-022 implements `UniFfiHostAdapter` that wraps UniFFI callback traits and implements these native traits.

AGENT-041 implements mock versions for the desktop smoke test example.

## Acceptance criteria

- [ ] Traits compile with `#[cfg(test)]` mock impls
- [ ] `NoOpSummarizer` returns `Internal` error (not panics)
- [ ] No UniFFI or Android imports in `host.rs`
- [ ] Trait object safety verified (no associated types blocking `dyn`)

## Files

- `third_party/agent_core/src/host.rs`
