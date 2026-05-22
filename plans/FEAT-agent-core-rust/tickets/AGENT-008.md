# AGENT-008 — Token ledger & budget (Rust)

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E2 — Budget & compaction |
| **Depends on** | AGENT-004 |
| **Blocks** | AGENT-009, AGENT-017, AGENT-019 |

## Description

Port `ContextWindowPlanner` + `ToolContextBudget` logic to Rust:

- `Budget`, `TokenLedger::estimate(session, config) -> Pressure`
- `should_compact(pressure: &Pressure, config: &AgentConfig) -> bool` using `threshold_ratio = 0.80` (locked in ADR)
- Reserves: output, reasoning, tool schema tokens (schema token count supplied by host via `ModelRequest.tools_schema_json` length estimate)
- `truncate_budget_per_result(session_capabilities) -> usize` — returns byte budget per tool result: cloud=`AgentConfig.tool_result_budget_cloud_bytes` (default 4096); local=`SessionCapabilities.tool_result_budget_tokens * avg_bytes_per_token` if `Some`, else cloud default

**Tokenizer:** use `tiktoken-rs` (closest approximation to `JTokkitTokenCounter`). Provider-specific over/under-count corrected via `AgentConfig.token_correction: HashMap<String, f32>` — key is provider name (e.g., `"anthropic"`, `"google"`), value is a multiplier applied to raw tiktoken count.

```rust
pub struct Pressure {
    pub total_tokens: u32,
    pub threshold_tokens: u32,
    pub usable_prompt_tokens: u32,
    pub ratio: f32,   // total_tokens / context_window_tokens
}
```

## Acceptance criteria

- [ ] Port key scenarios from `ContextWindowPlannerTest.kt` into Rust unit tests
- [ ] Document intentional ratio change from legacy 0.75 / 0.85 in ADR (AGENT-001 already owns this)
- [ ] `Pressure` exposes `total_tokens`, `threshold_tokens`, `usable_prompt_tokens`, `ratio`
- [ ] `should_compact` returns true at ratio ≥ 0.80
- [ ] `token_correction` multiplier test: provider factor 1.1 at 100 tokens → `110` estimated

## Files

- `third_party/agent_core/src/budget.rs`
