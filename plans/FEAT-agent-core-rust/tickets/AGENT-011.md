# AGENT-011 — Tier 2: FIFO turn-pair dropping

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E2 — Budget & compaction |
| **Depends on** | AGENT-009 |
| **Blocks** | AGENT-017, AGENT-018 |

## Description

Port `ChatHistoryCompressor.compressHistory` to Rust on `AgentMessage` list:

- Drop oldest messages until under budget
- Prefer dropping user+assistant pairs together
  - "Pair" = user message + immediately following assistant message (skipping any interleaved tool call/result messages which are dropped alongside the pair they belong to)
- Never drop: system prompt (`AgentRole::System`), compact summary messages (`AgentRole::CompactSummary`), or the latest user turn (the most recent `AgentRole::User` message)

## Acceptance criteria

- [ ] Rust tests match Kotlin edge cases (single message, user-only oldest, empty history)
- [ ] `CompactionEvent.tier = Fifo`
- [ ] Pair-drop behavior documented in module docs

## Files

- `third_party/agent_core/src/compact/fifo.rs`
