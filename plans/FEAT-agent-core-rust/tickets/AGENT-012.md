# AGENT-012 — Tier 4: LLM summary compaction (host callback)

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E2 — Budget & compaction |
| **Depends on** | AGENT-009, AGENT-007 |
| **Blocks** | AGENT-022, AGENT-028 |

## Description

When tiers 1–3 insufficient and `SessionCapabilities.summarization_allowed = true`:

### `SummaryRequest` type (defined here; used by AGENT-028 Kotlin adapter)

```rust
pub struct SummaryRequest {
    pub messages_to_summarize: Vec<AgentMessage>,
    pub instruction: String,   // "Summarize preserving: tool decisions made, artifact state, key facts. Omit raw tool output."
    pub last_message_id: String,
}

pub struct SummaryResult {
    pub summary_text: String,
    pub last_message_id: String,  // echo back for validation
}
```

### Strategy behavior

Returns `CompactionOutcome::NeedsHostCall(SummaryRequest { messages_to_summarize, instruction, last_message_id })`.

Engine, on receiving `NeedsHostCall`:
1. Yields `AgentAction::RequestSummary(request)` to host
2. Host calls `submit_summary(handle, SummaryResult)`
3. Engine inserts `AgentRole::CompactSummary` message at the position of `last_message_id`
4. Engine removes the summarized message range from `session.messages`
5. Engine re-evaluates pressure; if still over threshold, continues to next pipeline iteration (unlikely — Summary is last tier)

Skipped (returns `NotApplicable`) when `summarization_allowed = false` (local models, Llama, LiteRT).

## Acceptance criteria

- [ ] Rust unit tests with mock summarizer that returns synthetic `SummaryResult`
- [ ] `CompactionEvent.tier = CompactionTier::Summary`
- [ ] Instruction text preserved verbatim in `SummaryRequest.instruction`
- [ ] Session after `submit_summary`: summarized range removed, single `CompactSummary` message inserted
- [ ] Returns `NotApplicable` when `summarization_allowed = false`

## Files

- `third_party/agent_core/src/compact/summary.rs`
