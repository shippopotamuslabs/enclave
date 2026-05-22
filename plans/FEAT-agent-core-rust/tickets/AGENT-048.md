# AGENT-048 — Android CI: NDK cross-compile + `:core:agent:assemble` smoke

| Field | Value |
|-------|--------|
| **Type** | CI |
| **Epic** | E5 — UniFFI & Android crate |
| **Depends on** | AGENT-023, AGENT-024 |
| **Blocks** | — |

## Description

AGENT-003 covers Rust-only CI (`cargo test`, `clippy`, `fmt`). This ticket adds the Android build leg:

**CI job:** `agent-android-assemble`

1. Install Android NDK (same version used by `docgen_core` CI — check existing `build-docgen-bindings.sh` for NDK version)
2. Run `scripts/build-agent-bindings.sh` (cross-compile `aarch64-linux-android`, generate UniFFI Kotlin)
3. Run `./gradlew :core:agent:assemble`
4. Assert `.so` present under `core/agent/src/main/jniLibs/arm64-v8a/`

Trigger: MR touching `third_party/agent_core/**`, `core/agent/**`, or `scripts/build-agent-bindings.sh`.

Use same CI runner image as the existing Android build job (NDK 27, API 34 — verify against `build-docgen-bindings.sh`).

## Acceptance criteria

- [ ] Job runs automatically on qualifying MRs
- [ ] Job fails if `.so` is missing or Kotlin scaffold doesn't compile
- [ ] NDK version documented in `agent_core/README.md`
- [ ] Job name and trigger documented in `.gitlab-ci.yml`

## Files

- `.gitlab-ci.yml` (new job)
- `agent_core/README.md` (NDK version note)
