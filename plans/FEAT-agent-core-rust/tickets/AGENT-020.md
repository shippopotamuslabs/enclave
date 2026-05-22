# AGENT-020 — Golden / fixture test harness

| Field | Value |
|-------|--------|
| **Type** | Test |
| **Epic** | E4 — Agent loop engine |
| **Depends on** | AGENT-017 |
| **Blocks** | AGENT-044 |

## Description

Add `third_party/agent_core/tests/fixtures/`:

### Fixture file format

Each fixture is a JSON file:

```json
{
  "name": "artifact_loop_happy_path",
  "initial_session": {
    "messages": [...],
    "config": { "max_turns": 20, "threshold_ratio": 0.80, ... },
    "capabilities": { "summarization_allowed": true, "kv_fixed": false },
    "session_context": {}
  },
  "model_responses": [
    { "tool_calls": [...], "finish_reason": "ToolUse" },
    { "tool_calls": [...], "finish_reason": "ToolUse" },
    { "assistant_text": "Done.", "tool_calls": [], "finish_reason": "Stop" }
  ],
  "expected_actions": ["CallModel", "ExecuteTools", "CallModel", "ExecuteTools", "CallModel", "Finished"]
}
```

`model_responses` are replayed in order — the test harness supplies them via a mock transport. `expected_actions` checks the sequence of `AgentAction` variant names returned by `next_action`.

Use `insta` for event drain snapshots (optional but preferred): after each fixture run, snapshot the `drain_events()` output for regression detection.

### Required fixtures (≥5)

1. `artifact_loop_happy_path.json` — multi-turn `start_artifact` + `append_blocks` + auto-finalize
2. `search_loop.json` — 3 `web_search` turns then final answer
3. `compaction_under_pressure.json` — session pre-populated to 79% token usage, next turn triggers micro-compact
4. `fuzzy_repair.json` — model calls `web_seach` → repaired to `web_search`
5. `guardrail_stop.json` — three consecutive all-soft-failure turns → `allow_tool_call=false` on fourth

## Acceptance criteria

- [ ] ≥5 fixtures run via `cargo test --test fixtures`
- [ ] Fixture updates require explicit approval (document: run `cargo insta review` for snapshot changes)
- [ ] Fixtures referenced from AGENT-044 E2E planning (fixture 1 is the basis for the Android integration test)

## Files

- `third_party/agent_core/tests/fixtures/*.json`
- `third_party/agent_core/tests/fixtures.rs`
