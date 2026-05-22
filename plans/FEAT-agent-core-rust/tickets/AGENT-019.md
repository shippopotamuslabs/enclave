# AGENT-019 — Tool result truncation policy (Rust)

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E4 — Agent loop engine |
| **Depends on** | AGENT-008, AGENT-017 |
| **Blocks** | AGENT-027, AGENT-034 |

## Description

Before appending tool results to session on `submit_tool_results`:

- Compute per-result budget from `TokenLedger::truncate_budget_per_result(session_capabilities)`:
  - **Cloud providers:** `AgentConfig.tool_result_budget_cloud_bytes` (default **4096 bytes**)
  - **Local models (`SessionCapabilities.tool_result_budget_tokens = Some(n)`):** `n * 4` bytes (approx; matches `LocalToolResultPolicy` behavior)
- If `result_json.len() > budget`: truncate to budget bytes at a UTF-8 char boundary, append note `"\n[Result truncated to fit context budget]"`, set `ToolResultRecord.truncated = true`
- Ledger counts only truncated payload length, not original

This replaces the duplicate truncation currently in `LlamaInferenceServiceImpl.onToolResultsMapped` (removed in AGENT-033) and overlapping `LocalToolResultPolicy` (removed in AGENT-043).

## Acceptance criteria

- [ ] Truncation applied on `submit_tool_results` path inside engine
- [ ] Unit tests: oversized JSON → under budget, `truncated = true`, note appended
- [ ] Under-budget JSON: `truncated = false`, unchanged
- [ ] Ledger token estimate uses truncated length, not original (test: truncated result has lower token count in `Pressure`)
- [ ] Cloud vs local budget paths both tested

## Files

- `third_party/agent_core/src/truncate.rs`
