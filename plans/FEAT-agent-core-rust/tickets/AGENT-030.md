# AGENT-030 — Feature flag: `UseRustAgentLoop`

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E7 — Inference migration |
| **Depends on** | AGENT-026 |
| **Blocks** | AGENT-031, AGENT-032, AGENT-033, AGENT-034 |

## Description

Add feature flag:

- **Storage:** DataStore in `core/data` (same layer as other feature toggles)
- **Off (default until AGENT-036):** legacy `LlmToolingOrchestrator` path
- **On:** `AgentLoopPort` / `RustAgentEngineAdapter`
- Not hot-swappable mid-session — read once at adapter construction, consistent for session lifetime
- Log active path per chat generation: `"AgentLoop: [RUST|LEGACY] provider=<name>"`
- Expose in internal debug settings for QA if applicable

## Acceptance criteria

- [ ] Flag off: zero behavior change in CI (all existing tests pass)
- [ ] Flag on: routes to Rust adapter (may have incomplete provider coverage until AGENT-031+)
- [ ] ADR (AGENT-001) references flag location and removal plan
- [ ] Flag storage in `core/data` (not `feature/settings` or `BuildConfig`)

## Files

- `core/data/.../features/RustAgentLoopFlag.kt` (or equivalent DataStore key)
- `feature/inference/...` routing branch
