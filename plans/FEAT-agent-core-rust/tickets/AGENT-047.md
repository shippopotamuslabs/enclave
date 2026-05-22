# AGENT-047 — `AgentEvent::KvCacheInvalidated` + kv_fixed compaction integration test

| Field | Value |
|-------|--------|
| **Type** | Feature + Test |
| **Epic** | E3 — Tool repair & guardrails |
| **Depends on** | AGENT-017, AGENT-011, AGENT-012 |
| **Blocks** | AGENT-033, AGENT-034 |

## Description

Codifies the kv_fixed compaction policy (per ADR AGENT-001):

### Engine behavior for `kv_fixed = true` sessions

When any compaction tier runs and modifies session history (`CompactionOutcome::Applied`):
1. Engine emits `AgentEvent::KvCacheInvalidated { reason: "Compacted: <tier name>" }`
2. Engine continues the loop normally (does not abort or warn)
3. Host transport is responsible for detecting this event and rebuilding the KV cache prompt

For `NeedsHostCall` (Summary tier): if `kv_fixed = true`, Summary tier returns `NotApplicable` immediately (cannot rebuild KV around a round-trip summarization). FIFO + Micro + ArtifactSquash are still available.

### Integration test

Add fixture `tests/fixtures/kv_fixed_compaction.json`:

- `capabilities: { kv_fixed: true, summarization_allowed: false }`
- Session pre-populated at 82% token usage with reproducible tool results
- Expected: Micro-compact fires, `KvCacheInvalidated` event emitted, loop continues to `CallModel`
- Expected: Summary tier does NOT fire (NotApplicable for kv_fixed)

Add a separate Rust integration test (not just fixture) that asserts:
- `drain_events()` contains `KvCacheInvalidated` after compaction on kv_fixed session
- `drain_events()` does NOT contain `KvCacheInvalidated` on non-kv_fixed session under same pressure

## Acceptance criteria

- [ ] `AgentEvent::KvCacheInvalidated` defined in AGENT-006 (backfill if not already done)
- [ ] Engine emits event on any compaction (`Applied`) for `kv_fixed = true` sessions
- [ ] Summary tier returns `NotApplicable` for `kv_fixed = true`
- [ ] Fixture `kv_fixed_compaction.json` runs in `cargo test --test fixtures`
- [ ] Integration test asserts event presence/absence

## Files

- `third_party/agent_core/src/engine/state.rs` (emit event on compact)
- `third_party/agent_core/tests/fixtures/kv_fixed_compaction.json`
- `third_party/agent_core/tests/kv_fixed_test.rs`
