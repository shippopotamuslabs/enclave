# AGENT-024 — Android module `:core:agent`

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E5 — UniFFI & Android crate |
| **Depends on** | AGENT-023 |
| **Blocks** | AGENT-025, AGENT-026 |

## Description

Create Android library module `core/agent/`:

- `build.gradle.kts` mirror `:core:docgen` (jniLibs, uniffi runtime, JNA aar)
- `uniffi.toml` → `package_name = "com.browntowndev.pocketcrew.core.agent"`
- `include(":core:agent")` in `settings.gradle.kts`
- `:feature:inference` adds `implementation(project(":core:agent"))`
- Commit generated `agent_core.kt` + `.so` per project policy (document in AGENT-025)

## Acceptance criteria

- [ ] `./gradlew :core:agent:assemble` succeeds after running binding script
- [ ] JNI library loads in minimal JVM test or instrumentation smoke
- [ ] No dependency on Compose / Room in `:core:agent`

## Files

- `core/agent/build.gradle.kts`
- `core/agent/uniffi.toml`
- `settings.gradle.kts`
- `core/agent/src/main/jniLibs/arm64-v8a/libagent_core.so`
- `core/agent/src/main/kotlin/.../agent_core.kt`
