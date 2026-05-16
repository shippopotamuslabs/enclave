# AGENT-028 — `AgentSummarizerAdapter`

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E6 — Kotlin adapters |
| **Depends on** | AGENT-012, AGENT-026 |
| **Blocks** | AGENT-037 |

## Description

Implement `AgentSummarizerCallback`:

- Delegate to existing `RollingSummarizer` + `LlmInferencePort` (FAST model)
- Map `SummaryRequest` / `SummaryResult` to Rust types
- Run on IO dispatcher; handle cancellation

## Acceptance criteria

- [ ] Integration test with fake `LlmInferencePort` returns summary and engine continues
- [ ] Not invoked when `summarization_allowed = false`
- [ ] Errors surface as `CompactionFailed` or recoverable engine error per ADR

## Files

- `feature/inference/.../agent/AgentSummarizerAdapter.kt`
