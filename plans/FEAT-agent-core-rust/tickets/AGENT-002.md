# AGENT-002 — Create `agent_core` Rust crate (submodule layout)

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E0 — Planning & repository |
| **Depends on** | AGENT-001 |
| **Blocks** | AGENT-003, AGENT-004 |

## Description

Add `third_party/agent_core/` mirroring `third_party/docgen_core`:

- `Cargo.toml`:
  - `edition = "2021"`, `rust-version = "1.78"` (MSRV)
  - `crate-type = ["cdylib", "staticlib", "rlib"]`
  - `uniffi = "0.29"` with `features = ["cli"]` (match `docgen_core`)
- `src/lib.rs` with `uniffi::setup_scaffolding!()`
- `src/uniffi-bindgen.rs` binary
- Dependencies: `serde`, `serde_json`, `thiserror`, `strsim`, `log`, `tiktoken-rs`, `once_cell`
- Stub `AgentError` enum with one variant for bindgen smoke test
- `README.md`: build for host + `aarch64-linux-android`
- **Layout:** git submodule under `third_party/agent_core/` — same approach as `docgen_core` (not vendored copy)

## Acceptance criteria

- [ ] `cargo test` passes in `third_party/agent_core`
- [ ] Submodule layout matches `docgen_core` (git submodule, documented in README)
- [ ] UniFFI version aligned with `docgen_core` and `gradle/libs.versions.toml`
- [ ] `edition = "2021"` and `rust-version = "1.78"` in `Cargo.toml`

## Files

- `third_party/agent_core/Cargo.toml`
- `third_party/agent_core/src/lib.rs`
- `third_party/agent_core/src/uniffi-bindgen.rs`
- `third_party/agent_core/README.md`
