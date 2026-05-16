# AGENT-021 — UniFFI exports for engine API

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E5 — UniFFI & Android crate |
| **Depends on** | AGENT-006, AGENT-017 |
| **Blocks** | AGENT-022, AGENT-023 |

## Description

Add `#[uniffi::export]` for:

- `AgentEngine` (constructor + all session methods)
- Records: `AgentConfig`, `SessionCapabilities`, `AgentMessage`, `ModelRequest`, `ModelResponse`, `RawToolCall`, `ToolCallRecord`, `ToolResultRecord`, `SummaryRequest`, `SummaryResult`, `CompactionEvent`, `AgentEvent`
- `AgentException` mapping for exported `AgentError` variants

**Session handle:** use UniFFI **object reference** (`Arc<SessionState>` wrapped as a UniFFI object). Drop semantics are handled by Rust; no manual `u64` id management in Kotlin. This is consistent with how `docgen_core` exposes session-like objects.

**`AgentException` mapping:**

| `AgentError` variant | Maps to |
|---|---|
| `RepairFailed` | `AgentException.RepairFailed(name, reason)` |
| `GuardrailExceeded` | `AgentException.GuardrailExceeded(kind)` |
| `CompactionFailed` | `AgentException.CompactionFailed(tier, reason)` |
| `InvalidTransition` | `AgentException.InvalidTransition(from, to)` |
| `UnsupportedTool` | `AgentException.Internal(message)` |
| `Internal` | `AgentException.Internal(message)` |

Do **not** export transport/executor callback traits yet (AGENT-022).

## Acceptance criteria

- [ ] `cargo run --bin uniffi-bindgen generate` succeeds on release `.so`
- [ ] Scaffold Kotlin compiles when copied to `core/agent` (manual smoke before AGENT-024)
- [ ] Session handle is a UniFFI object reference (not `u64`)
- [ ] `AgentException` variants listed above are all exported

## Files

- `third_party/agent_core/src/ffi/mod.rs`
- Updates to `src/lib.rs`
