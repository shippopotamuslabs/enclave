# AGENT-022 — UniFFI host callback traits (transport & tools)

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E5 — UniFFI & Android crate |
| **Depends on** | AGENT-021, AGENT-007, AGENT-012, AGENT-046 |
| **Blocks** | AGENT-026 |

## Description

Export UniFFI callback traits for Kotlin implementation, and wire them to the native Rust traits from AGENT-046 via a shim adapter.

### UniFFI callback interfaces

```rust
#[uniffi::export(callback_interface)]
pub trait AgentTransportCallback: Send + Sync {
    fn complete_model_turn(&self, request: ModelRequest) -> ModelResponse;
}

#[uniffi::export(callback_interface)]
pub trait AgentToolExecutorCallback: Send + Sync {
    fn execute_tools(&self, calls: Vec<ToolCallRecord>) -> Vec<ToolResultRecord>;
}

#[uniffi::export(callback_interface)]
pub trait AgentSummarizerCallback: Send + Sync {
    fn summarize(&self, request: SummaryRequest) -> SummaryResult;
}
```

### Adapter shim

`UniFfiHostAdapter` implements the native Rust traits from AGENT-046 by delegating to the UniFFI callbacks. `AgentEngine` on Android is constructed via:

```rust
pub fn create_engine(
    transport: Box<dyn AgentTransportCallback>,
    executor: Box<dyn AgentToolExecutorCallback>,
    summarizer: Option<Box<dyn AgentSummarizerCallback>>,
) -> Arc<AgentEngine<UniFfiHostAdapter, ...>>
```

The `summarizer` is `Option` — when `None` (or when `SessionCapabilities.summarization_allowed = false`), the summary tier is a no-op.

Desktop consumers (future) implement `AgentTransport`, `AgentToolExecutor`, `AgentSummarizer` directly in Rust without going through this callback layer.

## Acceptance criteria

- [ ] Rust integration tests use in-crate mock implementations of callback traits
- [ ] README documents: callbacks run on caller thread; Kotlin must dispatch on `Dispatchers.IO`, never Main
- [ ] `summarizer = None` accepted when `summarization_allowed = false` (test: no-op summarizer path)
- [ ] `UniFfiHostAdapter` compiles with both callback traits wired to native trait impls

## Files

- `third_party/agent_core/src/ffi/callbacks.rs`
