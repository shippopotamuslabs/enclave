# AGENT-039 — Agent events → logging / UI

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E9 — Observability |
| **Depends on** | AGENT-026, AGENT-030 |
| **Blocks** | — |

## Description

Map Rust `AgentEvent` to:

- `LoggingPort` (tag `AgentCore`, compaction tier, repair metadata)
- Optional `InferenceEvent` extensions for debug UI (`CompactionApplied`, `ToolRepaired`)

Drain events from adapter after each `next_action` cycle.

## Acceptance criteria

- [ ] Debug build logcat shows compaction tier when pressure relieved
- [ ] No message content or tool arguments in compaction logs (ids/metadata only)
- [ ] Events testable with fake logging port

## Files

- `core/agent/.../AgentEventMapper.kt`
- `feature/inference/...` event wiring if needed
