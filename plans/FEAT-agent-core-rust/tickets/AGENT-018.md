# AGENT-018 — Preflight compaction on session create

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E4 — Agent loop engine |
| **Depends on** | AGENT-017, AGENT-011, AGENT-012 |
| **Blocks** | AGENT-037 |

## Description

On `create_session`, run `CompactionPipeline` once before first `next_action` returns `CallModel`.

Replaces pre-send FIFO/summary work currently in `ChatHistoryRehydrator` (removed in AGENT-037).

Respect `SessionCapabilities.summarization_allowed`.

## Acceptance criteria

- [ ] New session starts under budget OR `compaction_log` non-empty
- [ ] Test: oversized initial history triggers FIFO and/or summary action before first model call
- [ ] No duplicate compaction if session already under threshold

## Files

- `third_party/agent_core/src/engine/preflight.rs`
