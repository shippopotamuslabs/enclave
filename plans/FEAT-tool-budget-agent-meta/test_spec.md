# Test Spec: Proactive Tool-Call Budget Metadata (`_agent`)

**Ticket ID:** `FEAT-tool-budget-agent-meta`  
**Companion spec:** `plans/FEAT-tool-budget-agent-meta/spec.md`

---

## Test Framework and Run Commands

| Layer | Framework | Location |
|-------|-----------|----------|
| Rust budget module (unit) | `cargo test` (libtest, `#[cfg(test)]` in module) | `third_party/agent_core/src/agent_tool_budget.rs` |
| Rust guardrail snapshot | `cargo test` (libtest) | `third_party/agent_core/src/guardrails.rs` (`#[cfg(test)]`) |
| Rust engine integration | `cargo test` (libtest) | `third_party/agent_core/tests/tool_budget_agent_meta_test.rs` |
| Mutation defense (grep) | shell `rg` gate | CI / T-7 in spec |

### Targeted red/green command

```bash
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_budget_agent_meta_test
cargo test --manifest-path third_party/agent_core/Cargo.toml agent_tool_budget
cargo test --manifest-path third_party/agent_core/Cargo.toml tool_budget_snapshot
```

### Completion gate for implementation executor

```bash
cargo test --manifest-path third_party/agent_core/Cargo.toml

# Kotlin/inference must not own _agent injection (must print zero matches)
! rg '"_agent"' feature/inference/src/main core/agent/src/main
```

**No Gradle tests required** for this ticket.

---

## Scenario Index

| ID | Category | Description |
|----|----------|-------------|
| TB-01 | Happy | Single object tool result merges `_agent` with default budget mid-loop |
| TB-02 | Happy | Parallel batch injects `_agent` only on last tool message |
| TB-03 | Happy | First tool call of session reports used=1 remaining=19 limit=20 |
| TB-04 | Edge | Budget exhausted → allow false + reason `tool_call_budget_exhausted` |
| TB-05 | Edge | Soft-failure streak exhausted → reason `soft_failure_streak_exhausted` |
| TB-06 | Edge | Envelope-parse streak exhausted → reason `envelope_parse_streak_exhausted` |
| TB-07 | Edge | Plain-text tool result → synthetic budget-only tool message |
| TB-08 | Edge | JSON array tool result → synthetic budget-only tool message |
| TB-09 | Edge | Greedy `append_blocks` extends limit to 35 when base cap reached |
| TB-10 | Edge | `skip_count` auto-finalize batch still injects snapshot |
| TB-11 | Edge | Invalid-phase `submit_tool_results` does not inject |
| TB-12 | Edge | Reject-only synthetic tool turn merges `_agent` before `CallModel` |
| MD-01 | Mutation | At most one tool message per batch contains `_agent` |
| MD-02 | Mutation | No `_agent` literals in Kotlin inference/agent production sources |
| MD-03 | Mutation | Merge preserves pre-existing tool payload keys |
| MD-04 | Mutation | `reason` absent when `allow_tool_call` is true |
| MD-05 | Mutation | Injector not called from `next_action` / transport layer |
| MD-06 | Mutation | Pre-existing executor `_agent` keys preserved on merge |

**Total scenarios: 18**

---

## Shared Test Harness Constants

Unless a scenario overrides them:

| Constant | Value |
|----------|-------|
| `AgentConfig.max_turns` | `20` |
| `AgentConfig.tool_retry_limit` | `3` |
| `AgentConfig.greedy_append_blocks` | `true` |
| Default tool name for harness | `web_search` |
| Default success tool JSON | `{"results":[]}` |

Harness pattern: in-process `AgentEngine` with `PanicTransport` + `RecordingExecutor` (same as `engine/mod.rs` tests). Drive `next_action` → `submit_model_response` → `next_action` → `submit_tool_results` → inspect `session.messages` via `SessionHandle` lock or `next_action` `ModelRequest.session_messages`.

---

## Happy Path Scenarios

### TB-01 — Single object result merges budget mid-loop

```gherkin
Scenario: TB-01 single tool result merges _agent mid-loop
  Given an AgentEngine session with AgentConfig.max_turns 20
  And guardrails.tool_calls_executed is 16 before submit_tool_results
  And the pending tool batch has one call with call_id "c1"
  When submit_tool_results is called with one ToolResultRecord
    | call_id | result_json        |
    | c1      | {"results":[]}     |
  And guardrails count increments by 1 for this batch
  Then the last Tool role message content parses as JSON object
  And JSON._agent.tool_calls_used equals 17
  And JSON._agent.tool_calls_limit equals 20
  And JSON._agent.tool_calls_remaining equals 3
  And JSON._agent.allow_tool_call equals true
  And JSON._agent.reason is absent
  And JSON.results is present
```

### TB-02 — Parallel batch: only last message gets `_agent`

```gherkin
Scenario: TB-02 parallel batch injects once on last tool message
  Given an AgentEngine session with default AgentConfig
  And a pending batch of three parallel tools with call_ids "c1","c2","c3"
  When submit_tool_results is called with three ToolResultRecords
    | call_id | result_json              |
    | c1      | {"results":[{"id":1}]}   |
    | c2      | {"results":[{"id":2}]}   |
    | c3      | {"results":[{"id":3}]}   |
  Then exactly 3 Tool role messages exist from this batch
  And the Tool message for call_id "c1" content has no "_agent" key
  And the Tool message for call_id "c2" content has no "_agent" key
  And the Tool message for call_id "c3" content has "_agent" object
  And JSON._agent.tool_calls_used equals 3
  And JSON._agent.tool_calls_remaining equals 17
  And JSON._agent.tool_calls_limit equals 20
  And JSON._agent.allow_tool_call equals true
```

### TB-03 — First tool call of session

```gherkin
Scenario: TB-03 first tool call reports full remaining budget
  Given a fresh AgentEngine session with guardrails.tool_calls_executed 0
  And one pending tool call "c1"
  When submit_tool_results is called with result_json {"status":"ok"}
  Then the budget-bearing Tool message JSON._agent.tool_calls_used equals 1
  And JSON._agent.tool_calls_limit equals 20
  And JSON._agent.tool_calls_remaining equals 19
  And JSON._agent.allow_tool_call equals true
```

---

## Error and Edge Scenarios

### TB-04 — Tool-call budget exhausted

```gherkin
Scenario: TB-04 budget exhausted disables tools with reason
  Given AgentConfig.max_turns 1
  And greedy_append_blocks false
  And guardrails.tool_calls_executed equals 0
  And one pending tool call "c1" after the first CallModel
  When submit_tool_results completes with result_json {"results":[]}
  Then JSON._agent.tool_calls_used equals 1
  And JSON._agent.tool_calls_limit equals 1
  And JSON._agent.tool_calls_remaining equals 0
  And JSON._agent.allow_tool_call equals false
  And JSON._agent.reason equals "tool_call_budget_exhausted"
  And the next ModelRequest.allow_tool_call equals false
```

### TB-05 — Soft-failure streak exhausted

```gherkin
Scenario: TB-05 soft failure streak sets reason
  Given AgentConfig.tool_retry_limit 3
  And guardrails.consecutive_soft_failure_turns equals 3
  And guardrails.tool_calls_executed equals 5
  And allow_tool_call would be false solely due to soft-failure streak
  When submit_tool_results completes one tool with result_json {"error":"validation failed"}
  Then JSON._agent.allow_tool_call equals false
  And JSON._agent.reason equals "soft_failure_streak_exhausted"
  And JSON._agent.tool_calls_used equals 6
  And JSON._agent.tool_calls_remaining equals 14
  And JSON._agent.tool_calls_limit equals 20
```

### TB-06 — Envelope-parse streak exhausted

```gherkin
Scenario: TB-06 envelope parse streak sets reason
  Given AgentConfig.tool_retry_limit 3
  And guardrails.consecutive_envelope_parse_failures equals 3
  And guardrails.consecutive_soft_failure_turns equals 0
  And guardrails.tool_calls_executed equals 2
  When submit_tool_results completes one tool with result_json {"results":[]}
  Then JSON._agent.allow_tool_call equals false
  And JSON._agent.reason equals "envelope_parse_streak_exhausted"
  And JSON._agent.tool_calls_used equals 3
  And JSON._agent.tool_calls_remaining equals 17
  And JSON._agent.tool_calls_limit equals 20
```

### TB-07 — Plain-text tool result → synthetic message

```gherkin
Scenario: TB-07 non-JSON tool result appends synthetic budget message
  Given one pending tool call "c1"
  When submit_tool_results is called with result_json plain string "OK: 3 files"
  Then the Tool message for call_id "c1" content equals "OK: 3 files"
  And an additional Tool message exists with metadata.agent_tool_budget true
  And that synthetic message content equals {"_agent":{"tool_calls_used":1,"tool_calls_limit":20,"tool_calls_remaining":19,"allow_tool_call":true}}
  And the synthetic message id equals "tool-agent-budget-1"
```

### TB-08 — JSON array tool result → synthetic message

```gherkin
Scenario: TB-08 JSON array result appends synthetic budget message
  Given one pending tool call "c1"
  When submit_tool_results is called with result_json [1,2,3]
  Then the Tool message for call_id "c1" content equals [1,2,3]
  And exactly one additional Tool message with metadata.agent_tool_budget true exists
  And synthetic JSON._agent.tool_calls_used equals 1
  And synthetic JSON._agent.allow_tool_call equals true
  And synthetic JSON._agent.reason is absent
```

### TB-09 — Greedy append_blocks extends effective limit

```gherkin
Scenario: TB-09 greedy append_blocks raises limit to 35
  Given AgentConfig.max_turns 20
  And greedy_append_blocks true
  And last_model_response_had_append_blocks true
  And guardrails.tool_calls_executed equals 20 before this batch
  When submit_tool_results completes one tool with result_json {"results":[]}
  Then JSON._agent.tool_calls_limit equals 41
  And JSON._agent.tool_calls_used equals 21
  And JSON._agent.tool_calls_remaining equals 20
  And JSON._agent.allow_tool_call equals true
```

### TB-10 — skip_count batch still injects snapshot

```gherkin
Scenario: TB-10 skip_count auto-finalize path still injects budget
  Given pending_execute_skip_guardrail_count true
  And guardrails.tool_calls_executed equals 4 before batch
  And one append_blocks auto-finalize tool result
  When submit_tool_results completes without incrementing guardrails
  Then a budget-bearing Tool message exists with JSON._agent.tool_calls_used equals 6
  And JSON._agent.tool_calls_limit equals 26
  And JSON._agent.tool_calls_remaining equals 20
  And JSON._agent.allow_tool_call equals true
```

### TB-11 — Invalid phase: no injection

```gherkin
Scenario: TB-11 invalid phase submit_tool_results does not inject
  Given engine phase Idle (not AwaitingTools)
  When submit_tool_results is called with one ToolResultRecord
  Then engine returns Failed InvalidTransition
  And session.messages contains zero messages with content containing "_agent"
```

### TB-12 — Reject-only synthetic turn merges budget

```gherkin
Scenario: TB-12 repair reject-only turn merges _agent before CallModel
  Given allow_tool_call true
  And submit_model_response returns one repair Reject with error_json {"error":"bad args","available_tools":["web_search"]}
  And guardrails.tool_calls_executed after reject accounting equals 1
  When next_action returns CallModel
  Then the last Tool role message content JSON.error equals "bad args"
  And JSON._agent.tool_calls_used equals 1
  And JSON._agent.tool_calls_limit equals 20
  And JSON._agent.tool_calls_remaining equals 19
  And JSON._agent.allow_tool_call equals true
  And JSON.available_tools is present
```

---

## Mutation-Defense Scenarios

### MD-01 — At most one `_agent` per batch

```gherkin
Scenario: MD-01 only one tool message contains _agent per submit_tool_results
  Given a pending parallel batch of 4 tools
  When submit_tool_results completes all four with object JSON results
  Then the count of Tool messages whose content contains "_agent" equals 1
```

### MD-02 — No Kotlin ownership of `_agent`

```gherkin
Scenario: MD-02 production Kotlin sources do not reference _agent
  When ripgrep runs with pattern "_agent" on feature/inference/src/main and core/agent/src/main
  Then the match count equals 0
```

### MD-03 — Merge preserves tool payload keys

```gherkin
Scenario: MD-03 merge keeps original tool keys
  Given one pending tool call
  When submit_tool_results is called with result_json {"results":[{"title":"A"}],"status":"success"}
  Then parsed content.results[0].title equals "A"
  And parsed content.status equals "success"
  And parsed content._agent.tool_calls_used equals 1
```

### MD-04 — No reason when tools still allowed

```gherkin
Scenario: MD-04 reason key absent when allow_tool_call true
  Given guardrails allow_tool_call true for default config
  When submit_tool_results completes any successful object result
  Then parsed JSON._agent.allow_tool_call equals true
  And parsed JSON._agent object has no key "reason"
```

### MD-05 — Injector not invoked from transport

```gherkin
Scenario: MD-05 budget injection only on submit_tool_results and submit_model_response reject paths
  Given a test AgentTransport that records session_messages on complete_model_turn
  When only next_action CallModel is invoked without prior submit_tool_results
  Then no message in session_messages contains "_agent"
```

### MD-06 — Executor `_agent` keys preserved

```gherkin
Scenario: MD-06 merge preserves executor-provided _agent fields
  Given one pending tool call
  When submit_tool_results is called with result_json {"_agent":{"source":"executor"},"results":[]}
  Then parsed content._agent.source equals "executor"
  And parsed content._agent.tool_calls_used equals 1
  And parsed content._agent.tool_calls_limit equals 20
  And parsed content._agent.tool_calls_remaining equals 19
  And parsed content._agent.allow_tool_call equals true
  And parsed content.results is present
```

---

## Task ↔ Scenario Coverage Matrix

| Task (spec §5) | Scenarios |
|----------------|-----------|
| T-1 | TB-04, TB-05, TB-06, MD-04, MD-06 |
| T-2 | TB-01, TB-03, TB-09 |
| T-3 | TB-01, TB-02, TB-07, TB-08, TB-10, TB-11, MD-01, MD-03 |
| T-4 | TB-12 |
| T-5 | All TB-01 … TB-12, MD-01, MD-03, MD-04, MD-05, MD-06 |
| T-6 | Full suite regression |
| T-7 | MD-02 |

---

## Self-Review Checklist (plan author)

- [x] Every scenario has stable ID and literal expected values in Then clauses
- [x] One scenario per behavior plus edge cases and mutation defense
- [x] Tasks in spec.md reference scenario IDs via coverage matrix
- [x] Run commands are exact and scoped to Rust
- [x] No placeholders (TBD/TODO)
- [x] TB-07 synthetic JSON literal matches spec field set (no reason when allowed)
