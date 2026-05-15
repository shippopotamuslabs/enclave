# AGENT-032 — Migrate Anthropic & Google to Rust loop

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E7 — Inference migration |
| **Depends on** | AGENT-031 |
| **Blocks** | AGENT-036 |

## Description

Migrate `AnthropicInferenceServiceImpl` and `GoogleInferenceServiceImpl` tool loops to `AgentLoopPort` when flag on.

Remove per-service `executeToolingPrompt` duplication where possible; share transport helpers with AGENT-027.

## Acceptance criteria

- [ ] Provider-specific unit tests pass with flag on
- [ ] `appendStopToolsWarning` / stop-tools user message only when engine sets `allow_tool_call = false`
- [ ] No remaining `estimateContextExceeded` + hard orchestrator throw path in these services

## Files

- `feature/inference/.../AnthropicInferenceServiceImpl.kt`
- `feature/inference/.../GoogleInferenceServiceImpl.kt`
