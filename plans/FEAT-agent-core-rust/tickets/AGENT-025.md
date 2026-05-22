# AGENT-025 — Developer docs for bindings

| Field | Value |
|-------|--------|
| **Type** | Docs |
| **Epic** | E5 — UniFFI & Android crate |
| **Depends on** | AGENT-024 |
| **Blocks** | — |

## Description

Write `core/agent/README.md`:

- When to run `scripts/build-agent-bindings.sh`
- Checked-in vs CI-generated bindings policy (**recommend checked in**, like docgen)
- Submodule bump workflow for `third_party/agent_core`
- Desktop: direct `path` dependency, no UniFFI
- Troubleshooting: NDK path, contract version mismatch, JNA

Add note to `AGENTS.md` or contracts: agent loop policy lives in Rust.

## Acceptance criteria

- [ ] New contributor can rebuild bindings from README alone
- [ ] Cross-link from `plans/FEAT-agent-core-rust/README.md`

## Files

- `core/agent/README.md`
