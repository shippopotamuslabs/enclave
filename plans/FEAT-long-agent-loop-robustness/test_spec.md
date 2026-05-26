# Test Spec: Long Agent Loop Robustness

**Ticket ID:** `FEAT-long-agent-loop-robustness`  
**Framework:** Rust `cargo test` in `third_party/agent_core` (+ Kotlin follow-up scenarios in `:feature:inference`)

---

## 1. Run Commands

| Scope | Command |
|---|---|
| All agent_core tests | `cd /home/sean/Code/pocket-crew/third_party/agent_core && cargo test --features lalr-harness --tests --no-fail-fast` |
| Long-loop integration | `cd /home/sean/Code/pocket-crew/third_party/agent_core && cargo test --features lalr-harness --test long_agent_loop_test --no-fail-fast` |
| Transcript revision | `cd /home/sean/Code/pocket-crew/third_party/agent_core && cargo test --features lalr-harness --test transcript_revision_test --no-fail-fast` |
| Sidecar budgets | `cd /home/sean/Code/pocket-crew/third_party/agent_core && cargo test --features lalr-harness --test sidecar_budget_test --no-fail-fast` |
| Compaction boundary | `cd /home/sean/Code/pocket-crew/third_party/agent_core && cargo test --features lalr-harness --test compaction_boundary_test --no-fail-fast` |
| Trigger extensions | `cd /home/sean/Code/pocket-crew/third_party/agent_core && cargo test --features lalr-harness --test compaction_triggers_test --no-fail-fast` |
| Kotlin follow-up (Task 11) | `./gradlew :feature:inference:testDebugUnitTest --tests "com.browntowndev.enclave.feature.inference.appendonly.*"` |

---

## 2. Scenario Index

| Scenario ID | Type | Description |
|---|---|---|
| `LALR-01` | Happy Path | 25 tool-turn simulated loop completes with sidecar bytes ≤ 8192 per CallModel. |
| `LALR-02` | Happy Path | `transcript_revision` increments by exactly 1 after one destructive micro pass. |
| `LALR-03` | Happy Path | `CompactionBoundary.boundary_id` equals `cb-1-preflight` after first destructive preflight. |
| `LALR-04` | Happy Path | Micro tier replacement message has new `id` and `metadata.rewrite_of` set to prior id. |
| `LALR-05` | Edge Case | Ledger delta truncates to refs when input exceeds `sidecar_byte_budget_per_call` 512. |
| `LALR-06` | Happy Path | Turn 2 ledger delta omits unchanged `objective` key present in turn 1 full snapshot. |
| `LALR-07` | Happy Path | Summary tier eligible at tool round 12 when `summarize_after_tool_turns` equals 12. |
| `LALR-08` | Happy Path | Preflight compaction runs at tool round 4 when `compact_every_n_tool_turns` equals 4. |
| `LALR-09` | Happy Path | `PostCompact.transcript_revision_after` equals session `transcript_revision` 2 after two destructive passes. |
| `LALR-10` | Happy Path | `ProviderContextSnapshot.sidecar_generation` increases 0 → 1 → 2 across three CallModel actions. |
| `LALR-11` | Happy Path | Semantic fact `PROJECT_CODENAME=ENCLAVE` survives 25 tool turns in ledger or CompactSummary. |
| `LALR-12` | Edge Case | Tool execution stops at `max_tool_turns` 5 with `GuardrailExceeded` kind `max_tool_turns`. |
| `LALR-13` | Edge Case | Rolling sidecar cache enforces `sidecar_total_byte_budget` 1024 by dropping oldest generation. |
| `LALR-14` | Happy Path | After summary compaction, `StateLedger.objective` remains `ship long-loop v1`. |
| `LALR-15` | Regression | `kv_fixed` session emits `CompactionBoundary` without Summary tier in `tiers_applied`. |
| `LALR-16` | Happy Path | `FfiCompactionBoundary` JSON roundtrip preserves `boundary_id` `cb-1-mid_turn`. |
| `LALR-17` | Mutation-Defense | In-place `content` mutation on same `AgentMessage.id` without `rewrite_of` fails test harness. |
| `LALR-18` | Edge Case | Oversized `full_schema_ref` replaced with `"<omitted:128 chars>"` in tool index delta. |
| `LALR-19` | Edge Case | HistorySnapshot-only prelude does not bump `transcript_revision` or emit `CompactionBoundary`. |
| `LALR-20` | Happy Path | After 25 turns with compaction, `session.messages.len()` ≤ 40. |
| `LALR-H01` | Host Follow-up | `PostCompact` invalidates append-only log with reason `rust_compaction_boundary`. |
| `LALR-H02` | Host Follow-up | `GenerationOptions.transcriptRevision` equals latest `PostCompact.transcript_revision_after`. |
| `LALR-H03` | Host Follow-up | Digest mismatch still clears log when revision unchanged (safety net). |

**Task → scenario mapping**

| Task | Scenarios |
|---|---|
| Task 1 | `LALR-12` |
| Task 2 | `LALR-02`, `LALR-04`, `LALR-17` |
| Task 3 | `LALR-02`, `LALR-04`, `LALR-14`, `LALR-17` |
| Task 4 | `LALR-03`, `LALR-09`, `LALR-15`, `LALR-19` |
| Task 5 | `LALR-05`, `LALR-06`, `LALR-10`, `LALR-13`, `LALR-18` |
| Task 6 | `LALR-07`, `LALR-08` |
| Task 7 | `LALR-12` |
| Task 8 | `LALR-16` |
| Task 9 | `LALR-01`, `LALR-11`, `LALR-20` |
| Task 10 | `LALR-02`–`LALR-06`, `LALR-10`, `LALR-13`–`LALR-18` |
| Task 11 | `LALR-H01`, `LALR-H02`, `LALR-H03` |

---

## 3. Happy Path Scenarios

### Scenario ID: `LALR-01`
**25 tool-turn loop stays within per-call sidecar byte budget**

```gherkin
Feature: Long agent loop bounds

  Scenario: LALR-01 twenty-five tool turns within sidecar budget
    Given AgentConfig.long_loop.sidecar_byte_budget_per_call equals 8192
    And a SimulatedAgentHost with 25 sequential tool round-trips
    When the engine completes a user turn with tool name "noop" each round
    Then every drained ModelRequest.provider_context.sidecar_byte_len is less than or equal to 8192
    And the final AgentAction is Finished
```

---

### Scenario ID: `LALR-02`
**Transcript revision increments on destructive compaction**

```gherkin
Feature: Transcript revision

  Scenario: LALR-02 revision bumps once per destructive pass
    Given a session with transcript_revision 0
    And pressure forces Micro tier to clear one reproducible tool output
    When run_compaction_pass runs with trigger Preflight
    Then session.transcript_revision equals 1
    And exactly one CompactionBoundary event is drained with revision_before 0 and revision_after 1
```

---

### Scenario ID: `LALR-03`
**CompactionBoundary id format**

```gherkin
Feature: Compaction boundary identity

  Scenario: LALR-03 boundary_id literal format
    Given a session with transcript_revision 0
    And the first destructive compaction uses trigger Preflight
    When the CompactionBoundary event is drained
    Then boundary.boundary_id equals "cb-1-preflight"
    And boundary.destructive equals true
```

---

### Scenario ID: `LALR-04`
**Micro tier uses rewrite metadata**

```gherkin
Feature: Append-only micro compaction

  Scenario: LALR-04 micro rewrite creates new message id
    Given tool message id "tool-msg-100" with content "{\"result\":\"x\"}"
    When MicroCompactionTier compacts that message via replace_message_content
    Then the message with id "tool-msg-100" has metadata.superseded true
    And a new message exists with metadata.rewrite_of "tool-msg-100"
    And the new message id does not equal "tool-msg-100"
    And the new message metadata.compaction_tier equals "micro"
```

---

### Scenario ID: `LALR-06`
**Ledger delta omits unchanged fields**

```gherkin
Feature: Sidecar deltas

  Scenario: LALR-06 second turn ledger delta is minimal
    Given StateLedger.objective equals "build feature"
    And turn 1 ships full state_ledger_json containing "\"objective\":\"build feature\""
    When turn 2 ships state_ledger_delta_json with only a new decision "picked approach A"
    Then state_ledger_delta_json contains "\"decisions\":[\"picked approach A\"]"
    And state_ledger_delta_json does not contain "\"objective\":\"build feature\""
```

---

### Scenario ID: `LALR-07`
**Summarize after N tool turns**

```gherkin
Feature: Turn-count summary trigger

  Scenario: LALR-07 summary eligible at turn 12
    Given AgentConfig.long_loop.summarize_after_tool_turns equals 12
    And SessionCapabilities.summarization_allowed equals true
    And tool_round_index equals 12 before preflight
    When should_compact_for_long_loop is evaluated for trigger Preflight
    Then the result equals true
    And the compaction plan includes tier Summary
```

---

### Scenario ID: `LALR-08`
**Compact every N tool turns**

```gherkin
Feature: Periodic compaction

  Scenario: LALR-08 preflight at turn 4
    Given AgentConfig.long_loop.compact_every_n_tool_turns equals 4
    And tool_round_index equals 4
    And pressure.ratio equals 0.10
    When should_compact_for_long_loop is evaluated for trigger Preflight
    Then the result equals true
```

---

### Scenario ID: `LALR-09`
**PostCompact carries revision**

```gherkin
Feature: Post-compact host contract

  Scenario: LALR-09 post compact revision matches session
    Given transcript_revision 0
    And two destructive compaction passes each bump revision by 1
    When PostCompact is drained for the second pass
    Then PostCompact.transcript_revision_after equals 2
    And session.transcript_revision equals 2
```

---

### Scenario ID: `LALR-10`
**Sidecar generation monotonic**

```gherkin
Feature: Provider context snapshot

  Scenario: LALR-10 sidecar generation increments per CallModel
    Given a fresh session
    When CallModel is emitted three times in one user turn
    Then the first ModelRequest.provider_context.sidecar_generation equals 0
    And the second ModelRequest.provider_context.sidecar_generation equals 1
    And the third ModelRequest.provider_context.sidecar_generation equals 2
```

---

### Scenario ID: `LALR-11`
**Semantic fact survives long loop**

```gherkin
Feature: Fact preservation

  Scenario: LALR-11 codename survives 25 tool turns
    Given StateLedger.constraints contains "PROJECT_CODENAME=ENCLAVE"
    And a 25 tool-turn simulated loop with compaction enabled
    When the user turn finishes
    Then the final session transcript or StateLedger still contains the substring "PROJECT_CODENAME=ENCLAVE"
```

---

### Scenario ID: `LALR-14`
**Objective preserved after summary**

```gherkin
Feature: Summary preserves ledger anchors

  Scenario: LALR-14 objective after summary tier
    Given StateLedger.objective equals "ship long-loop v1"
    And Summary tier replaces drained messages with CompactSummary
    When compaction completes
    Then StateLedger.objective equals "ship long-loop v1"
```

---

### Scenario ID: `LALR-16`
**FFI boundary roundtrip**

```gherkin
Feature: UniFFI compaction boundary

  Scenario: LALR-16 ffi boundary json roundtrip
    Given FfiCompactionBoundary with boundary_id "cb-1-mid_turn"
    And revision_before 0 and revision_after 1
    And trigger MidTurn
    When the struct is serialized to JSON and deserialized
    Then boundary_id equals "cb-1-mid_turn"
    And revision_after equals 1
```

---

### Scenario ID: `LALR-19`
**Non-destructive snapshot does not bump revision**

```gherkin
Feature: Non-destructive compaction prelude

  Scenario: LALR-19 history snapshot only leaves revision at 0
    Given transcript_revision 0
    And engine prelude snapshots history to ContextStore without message deletion
    When no destructive tier runs in the pass
    Then transcript_revision equals 0
    And drained events contain zero CompactionBoundary events
```

---

### Scenario ID: `LALR-20`
**Message count bounded after long loop**

```gherkin
Feature: Transcript size bound

  Scenario: LALR-20 message count cap after 25 turns
    Given compaction enabled with default long_loop policy
    And 25 tool rounds with large reproducible tool outputs
    When the user turn finishes
    Then session.messages.len() is less than or equal to 40
```

---

## 4. Error and Edge Case Scenarios

### Scenario ID: `LALR-05`
**Sidecar truncates to refs under tiny budget**

```gherkin
Feature: Sidecar byte budget

  Scenario: LALR-05 ledger truncates at 512 bytes
    Given sidecar_byte_budget_per_call equals 512
    And a StateLedger JSON payload of 4096 bytes
    When SidecarBuilder.build_ledger_delta runs
    Then the returned JSON contains "\"truncated\":true"
    And the returned JSON contains "\"refs\":"
    And the UTF-8 byte length is less than or equal to 512
```

---

### Scenario ID: `LALR-12`
**max_tool_turns guardrail**

```gherkin
Feature: Tool turn cap

  Scenario: LALR-12 stops at five tool rounds
    Given AgentConfig.long_loop.max_tool_turns equals 5
    And tool_round_index equals 5
    When the model response requests another tool call
    Then the next AgentAction is Failed
    And AgentError is GuardrailExceeded with kind "max_tool_turns"
```

---

### Scenario ID: `LALR-13`
**Total sidecar cache budget**

```gherkin
Feature: Rolling sidecar cache

  Scenario: LALR-13 drops oldest generation over total budget
    Given sidecar_total_byte_budget equals 1024
    And generation 0 payload size 700 bytes already cached
    When generation 1 payload size 500 bytes is built
    Then SidecarBuilder rolling cache total bytes is less than or equal to 1024
    And generation 0 is not present in the rolling cache
```

---

### Scenario ID: `LALR-15`
**KV-fixed boundary without summary**

```gherkin
Feature: Fixed KV compaction

  Scenario: LALR-15 kv_fixed emits boundary without summary tier
    Given SessionCapabilities.kv_fixed equals true
    And summarization_allowed equals false
    And pressure forces a destructive Micro pass on preflight
    When CompactionBoundary is drained
    Then tiers_applied contains "micro"
    And tiers_applied does not contain "summary"
```

---

### Scenario ID: `LALR-18`
**Tool index omits huge schema ref**

```gherkin
Feature: Tool context index budget

  Scenario: LALR-18 full_schema_ref omitted in delta
    Given tool_context_index.full_schema_ref length 128
    And sidecar_byte_budget_per_call equals 256
    When SidecarBuilder.build_tool_index_delta runs
    Then the delta JSON contains "\"full_schema_ref\":\"<omitted:128 chars>\""
```

---

## 5. Mutation-Defense

### Risk
A lazy implementation keeps **mutating `AgentMessage.content` in place** for Micro tier (current behavior) while still emitting `CompactionBoundary` events. Kotlin append-only digest would clear eventually, but **transcript_revision would lie** and cache prefixes could observe partial rewrites between host callbacks.

### Scenario ID: `LALR-17`
**In-place content mutation without rewrite metadata must fail**

```gherkin
Feature: Mutation defense append-only transcript

  Scenario: LALR-17 same id new content without rewrite_of fails
    Given tool message id "tool-msg-200" with content "AAAA"
    When a test helper mutates content to "BBBB" on the same id without setting metadata.rewrite_of
    Then assert_append_only_transcript_invariant returns false
    And the test case fails with message "append_only_violation:tool-msg-200"
```

**Implementation note for Red phase:** `assert_append_only_transcript_invariant` scans all messages; any id whose content changed from the prior snapshot without `superseded` + replacement row fails. Production code must pass by using `replace_message_content` only.

---

## 6. Host Follow-Up Scenarios (Kotlin — Task 11)

### Scenario ID: `LALR-H01`
**Explicit boundary invalidates append-only context**

```gherkin
Feature: Kotlin host compaction boundary

  Scenario: LALR-H01 post compact clears append-only log
    Given ProviderAppendOnlyContext with committedMessageCount 4
    When RustLoopInferenceService receives AgentLoopEvent.PostCompact with transcript_revision_after 3
    Then ProviderAppendOnlyContext.committedMessageCount equals 0
    And the last onLogCleared reason equals "rust_compaction_boundary"
```

---

### Scenario ID: `LALR-H02`
**Transcript revision threaded to provider**

```gherkin
Feature: Revision observability

  Scenario: LALR-H02 generation options carry revision
    Given PostCompact.transcript_revision_after equals 7
    When the next completeSingleTurn runs
    Then GenerationOptions.transcriptRevision equals 7
```

---

### Scenario ID: `LALR-H03`
**Digest safety net remains**

```gherkin
Feature: Digest fallback

  Scenario: LALR-H03 digest mismatch without boundary still clears
    Given ProviderAppendOnlyContext with committedMessageCount 2
    And transcript_revision unchanged at 1
    When syncMessages detects digest mismatch on normalized prefix
    Then ProviderAppendOnlyContext.committedMessageCount equals 0
    And the last onLogCleared reason equals "digest_mismatch"
```

---

## 7. Self-Review Checklist

- [x] Every v1 requirement has an implementation task and at least one scenario.
- [x] Tasks 1–11 reference scenario IDs.
- [x] `Then` clauses use literal values (boundary ids, byte limits, error kinds).
- [x] Mutation-defense targets in-place micro mutation (named lazy implementation).
- [x] No placeholders (`TBD`, `TODO`, etc.).
- [x] File paths verified against current repo layout (`third_party/agent_core`, `appendonly/*`, `RustLoopInferenceService.kt`).
