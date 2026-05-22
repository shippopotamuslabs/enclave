# AGENT-015 — Guardrails: turns, soft-failure streak, recursion

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E3 — Tool repair & guardrails |
| **Depends on** | AGENT-006 |
| **Blocks** | AGENT-017, AGENT-034 |

## Description

Port policies from `LlmToolingOrchestrator` (`LlmToolingOrchestrator.kt:109-113, 244-258`):

- `max_turns` / max tool call count from `AgentConfig.max_turns`
- Three consecutive turns where **every** tool result is soft failure (JSON contains top-level `error` key) → `allow_tool_call = false`
- Reset soft-failure streak on hard exception or any non-soft result
- Greedy `append_blocks` extension: when last turn contained an `append_blocks` call and `AgentConfig.greedy_append_blocks = true`, effective cap is `max_turns + 20`
- **Behavior change from legacy:** context pressure never produces `IllegalStateException`; engine compacts first (AGENT-017 owns the loop; guardrails only signal `allow_tool_call`)

Log `"validation retry exhausted"` when parallel soft-failure streak triggers (mirrors `LlmToolingOrchestrator.kt:249-257`).

### Test scenarios (port from `LlmToolingOrchestratorTest.kt`)

Map directly to these existing Kotlin test methods:

- `self-heal disables further tool calls after soft failure budget is exhausted` — three-turn streak → `allow_tool_call=false`
- `self-heal resets consecutive soft failure counter after a hard tool failure` — streak reset on exception
- `self-heal logs validation retry exhaustion after repeated all-error tool turns` — log message present
- `allowToolCall is true while under limit and false at limit` — max_turns boundary
- `allowToolCall decrements correctly across multiple tool calls` — count across turns
- `handles tool call count exceeding maxToolCalls gracefully first then throws` — recursion limit behavior (note: Rust engine returns `AgentAction::Failed` instead of throwing, but same count semantics)

## Acceptance criteria

- [ ] Rust tests covering all six scenarios above
- [ ] ADR (AGENT-001) documents divergence: no `IllegalStateException` on context pressure
- [ ] `allow_tool_call` exposed on `ModelRequest` (set by guardrails each turn)
- [ ] Greedy cap `= max_turns + 20` when `greedy_append_blocks = true` and last turn had `append_blocks`

## Files

- `third_party/agent_core/src/guardrails.rs`
