# AGENT-023 — `scripts/build-agent-bindings.sh`

| Field | Value |
|-------|--------|
| **Type** | DevOps |
| **Epic** | E5 — UniFFI & Android crate |
| **Depends on** | AGENT-021 |
| **Blocks** | AGENT-024 |

## Description

Create `scripts/build-agent-bindings.sh` modeled on `scripts/build-docgen-bindings.sh`:

- Cross-compile `libagent_core.so` for `aarch64-linux-android` (NDK 27, API 34)
- Run `cargo run --release --bin uniffi-bindgen generate`
- Output Kotlin to `core/agent/src/main/kotlin`
- Copy `.so` to `core/agent/src/main/jniLibs/arm64-v8a/`
- Use `core/agent/uniffi.toml` for package name

## Acceptance criteria

- [ ] Script runs end-to-end on Linux dev machine documented in README
- [ ] Idempotent (safe to re-run)
- [ ] NDK / API versions match docgen script

## Files

- `scripts/build-agent-bindings.sh`
- `core/agent/uniffi.toml` (created in AGENT-024, path referenced here)
