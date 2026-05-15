# AGENT-036 — Default flag on & soak release

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E7 — Inference migration |
| **Depends on** | AGENT-031, AGENT-032, AGENT-033, AGENT-034 |
| **Blocks** | AGENT-037, AGENT-043, AGENT-044, AGENT-045 |

## Description

- Set `UseRustAgentLoop` default **on** for release builds
- Remove the flag-off code path from inference service routing (no more branch to `LlmToolingOrchestrator`)
- Legacy orchestrator class (`LlmToolingOrchestrator.kt`) left in place for this ticket — deletion is owned by AGENT-043 after benchmarks confirm no regressions

**Scope of this ticket:** flip default + delete routing branch only. Do not delete `LlmToolingOrchestrator.kt` or its tests here.

## Acceptance criteria

- [ ] `./gradlew test` full suite green with flag on
- [ ] `./gradlew detekt` maxIssues 0 for touched files
- [ ] No `IllegalStateException` for "Model ignored context-full warning" reachable in production code
- [ ] Routing branch for flag-off removed (flag cannot be turned off via DataStore in release builds)

## Files

- `core/data/.../features/RustAgentLoopFlag.kt`
- Inference service routing
