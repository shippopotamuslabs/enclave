# AGENT-003 — Rust CI: `agent_core` test job

| Field | Value |
|-------|--------|
| **Type** | CI |
| **Epic** | E0 — Planning & repository |
| **Depends on** | AGENT-002 |
| **Blocks** | — |

## Description

Add GitLab CI jobs for `third_party/agent_core`:

**Job 1: `agent-core-rust-test` (host)**
- `cargo fmt --check`
- `cargo clippy -- -D warnings`
- `cargo test`
- Cache `target/` per branch
- Trigger: changes under `third_party/agent_core/**`

**Job 2: `agent-core-cross-compile` (Android NDK)**
- Cross-compile `aarch64-linux-android` using same runner image + NDK version as `docgen_core` CI
- Assert `.so` produced (no need to run tests — link success is the signal)
- Trigger: same as job 1

## Acceptance criteria

- [ ] MR touching `agent_core` runs both jobs automatically
- [ ] Job 1 fails on clippy warnings
- [ ] Job 2 fails if cross-compile fails (catches FFI signature breakage before AGENT-023 build script)
- [ ] Both jobs documented in `agent_core/README.md` for local reproduction

## Files

- `.gitlab-ci.yml` (or existing Rust job extension)
