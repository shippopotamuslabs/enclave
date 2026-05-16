# AGENT-041 — Desktop: `agent_core` contract & documentation

| Field | Value |
|-------|--------|
| **Type** | Documentation |
| **Epic** | E10 — Desktop & documentation |
| **Depends on** | AGENT-017, AGENT-046 |
| **Blocks** | AGENT-043 |

## Description

No desktop Rust app exists yet. This ticket ensures `agent_core` is *desktop-ready* and the contract for a future desktop consumer is documented.

**Deliverables:**

1. Verify `agent_core` compiles and the native Rust trait surface (from AGENT-046) works without UniFFI by adding an internal integration test binary:
   - `third_party/agent_core/examples/mock_desktop_loop.rs`
   - Implements `AgentTransport`, `AgentToolExecutor`, `AgentSummarizer` directly in Rust (no UniFFI, no callbacks)
   - Runs a 3-turn mock tool loop, asserts `Finished` action
   - `cargo run --example mock_desktop_loop` passes

2. Document in `third_party/agent_core/README.md`:
   - How a future desktop consumer adds `agent_core` as a Cargo path dependency
   - Version/submodule sync process (link to AGENT-042 versioning strategy)
   - Note: desktop consumers use native traits; UniFFI is Android-only

## Acceptance criteria

- [ ] `cargo run --example mock_desktop_loop` passes in CI
- [ ] `agent_core/README.md` has a "Desktop integration" section
- [ ] No external desktop repo work in this ticket

## Files

- `third_party/agent_core/examples/mock_desktop_loop.rs`
- `third_party/agent_core/README.md` (desktop section)
