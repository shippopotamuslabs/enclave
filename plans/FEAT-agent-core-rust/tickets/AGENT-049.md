# AGENT-049 — Engine event drain API + Kotlin `Flow<AgentLoopEvent>` plumbing

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E6 — Kotlin adapters |
| **Depends on** | AGENT-017, AGENT-026 |
| **Blocks** | — |

## Description

Resolves the `AgentAction::EmitEvent` vs accumulated-events conflict from AGENT-007 design.

### Rust side (`agent_core`)

Add `drain_events(handle) -> Vec<AgentEvent>` to `AgentEngine` (already declared in AGENT-017 description). Called by the Kotlin adapter after each `next_action` / `submit_*` cycle. Events are removed from internal buffer on drain (fire-and-forget).

Export via UniFFI in AGENT-021 (backfill the method export if not already included).

### Kotlin side (`core/agent`)

In `RustAgentEngineAdapter` (AGENT-026):
- After each `next_action` or `submit_*` call, drain events via `engine.drainEvents(handle)`
- Map each `AgentEvent` to `AgentLoopEvent` (defined in AGENT-026 sealed interface)
- Emit to the `Flow<AgentLoopEvent>` collector

**Event mapping:**

| Rust `AgentEvent` | Kotlin `AgentLoopEvent` |
|---|---|
| `CompactionApplied(event)` | `AgentLoopEvent.CompactionApplied(tier, tokensBefore, tokensAfter)` |
| `ToolRepaired { ... }` | `AgentLoopEvent.ToolRepaired(originalName, resolvedName)` |
| `TurnStarted`, `TurnCompleted` | `AgentLoopEvent.TurnStarted(index)` / `TurnCompleted(index, toolCallCount)` |
| `KvCacheInvalidated` | (handle internally — trigger transport KV rebuild; not emitted to UI) |
| `ToolSoftFailure`, `ContextPressure` | Logged to `LoggingPort` (not emitted to UI) |

## Acceptance criteria

- [ ] `drain_events` is idempotent (second call returns empty list)
- [ ] Kotlin Flow emits `CompactionApplied` event during a mid-loop compaction test
- [ ] `KvCacheInvalidated` handled internally by adapter, not leaked to UI
- [ ] Unit test: drain after 3 emitted events → list size 3; drain again → size 0

## Files

- `third_party/agent_core/src/engine/mod.rs` (drain_events method)
- `core/agent/.../RustAgentEngineAdapter.kt` (drain + map loop)
