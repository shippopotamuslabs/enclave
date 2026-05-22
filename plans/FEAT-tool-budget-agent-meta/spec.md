# Spec: Proactive Tool-Call Budget Metadata (`_agent`)

**Ticket ID:** `FEAT-tool-budget-agent-meta`  
**Date:** 2026-05-22  
**Contracts read:** `AGENTS.md`, `INFERENCE_RULES.md`, `CODE_STYLE_RULES.md`, `UNIT_TESTING.md`

---

## 1. Feature Summary

### Problem

The Rust agent engine (`third_party/agent_core`) tracks tool-loop guardrails in `GuardrailState` — executed count, effective cap, soft-failure streak, envelope-parse streak — and exposes `allow_tool_call` on the next `ModelRequest`. The model only learns that tools are disabled **reactively** (tools stripped from the provider request, optional `tools_blocked` nudge JSON on DSML leak). During normal multi-turn tool loops the model has no structured signal of how many tool calls remain before shutdown.

### Target

After each tool batch is submitted (`AgentEngine::submit_tool_results`), augment the tool-role messages the model will see on the next `CallModel` with a namespaced `_agent` JSON object reporting budget state **proactively**:

```json
{
  "_agent": {
    "tool_calls_remaining": 3,
    "tool_calls_used": 17,
    "tool_calls_limit": 20,
    "allow_tool_call": true
  }
}
```

When `allow_tool_call` is `false`, also include a `reason` string (see §3.2) so the model learns **before** attempting another tool call.

### Design decisions (locked)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Injection strategy** | Once per tool batch, immediately after guardrail accounting in `submit_tool_results`, before auto-finalize / compaction / `TurnCompleted` | Matches ticket scope; guardrail counters are final at this point |
| **Merge vs synthetic message** | **Merge** `_agent` into the **last** tool message of the batch when its `content` parses as a JSON **object**; otherwise append a **synthetic** tool message whose `content` is only `{"_agent":{...}}` | Avoids duplicating budget metadata on every parallel result; preserves existing object payloads; non-object JSON (arrays, strings, scalars) cannot safely absorb a sibling field |
| **Reason enum** | Three snake_case string values derived from the first failing guardrail predicate (§3.2) | Aligns with existing `GuardrailState::allow_tool_call` ordering; stable for tests |
| **Layer boundary** | Rust-only; metadata lives in tool message `content` JSON | No UniFFI / Kotlin / inference-layer changes; `_agent` rides existing `AgentMessage.content` into `ModelRequest.session_messages` |
| **Complements DSML fix** | Does not replace `tools_blocked_nudge_json` or `allow_tool_call=false` tool stripping | Proactive countdown during healthy loops; reactive nudge remains for post-shutdown syntax leaks |

### Non-goals

- Changing `GuardrailState` threshold math or `ModelRequest.allow_tool_call` semantics
- Surfacing `_agent` in Kotlin UI, events, or Firestore persistence
- Adding `_agent` to assistant or user messages
- Provider-specific prompt injection outside session message JSON
- Regenerating UniFFI bindings (no FFI signature changes)

### Verified injection point (current code)

`third_party/agent_core/src/engine/mod.rs` — `submit_tool_results`:

1. Zip pending tool calls with host results; truncate/spill; push each `AgentRole::Tool` message.
2. If `!skip_count`: `count_tool_executed` + `on_tool_turn_finished`.
3. Auto-finalize / compaction / `TurnCompleted`.

**New step:** between (2) and (3), call shared budget injector (§3.3).

---

## 2. File Structure Table

| Action | Path | Responsibility |
|--------|------|----------------|
| **Create** | `third_party/agent_core/src/agent_tool_budget.rs` | `AgentToolBudgetMeta`, shutdown reason resolution, JSON merge / synthetic message builder, `inject_tool_budget_into_session` |
| **Modify** | `third_party/agent_core/src/lib.rs` | `pub mod agent_tool_budget;` |
| **Modify** | `third_party/agent_core/src/guardrails.rs` | Add `GuardrailState::tool_budget_snapshot(&self, config, last_model_response_had_append_blocks) -> AgentToolBudgetMeta` delegating limit/allow math to existing methods |
| **Modify** | `third_party/agent_core/src/engine/mod.rs` | Invoke injector at end of `submit_tool_results` after guardrail block; invoke same helper for reject-only synthetic tool messages pushed inside `submit_model_response` (repair rejects + envelope parse failures) so budget metadata precedes every `CallModel` that follows tool-role content |
| **Create** | `third_party/agent_core/tests/tool_budget_agent_meta_test.rs` | Integration scenarios TB-01 … TB-12, MD-01 … MD-06 |
| **Modify** | `third_party/agent_core/src/agent_tool_budget.rs` (`#[cfg(test)]`) | Pure unit tests for snapshot math, reason priority, merge fallbacks |
| **Modify** | `third_party/agent_core/tests/snapshots/fixtures__events_guardrail_stop.snap` | Update only if fixture harness message content assertions change (review with `cargo insta review`) |

**No changes:**

| Path | Why |
|------|-----|
| `core/agent/.../agent_core.kt` | Metadata merged into existing tool `content` strings; no FFI API change |
| `feature/inference/**` | Engine-owned; Kotlin forwards `session_messages` unchanged |
| `contracts/**` | No contract amendment required |

---

## 3. API Contracts and Interfaces

### 3.1 `_agent` payload (tool message JSON)

When embedded in a tool result object (merge path) or as the sole object (synthetic path):

| Field | Type | Always present | Semantics |
|-------|------|----------------|-----------|
| `_agent` | object | yes | Namespaced engine metadata; MUST NOT be interpreted by tool executors |
| `_agent.tool_calls_used` | u32 | yes | `GuardrailState.tool_calls_executed` **after** current batch guardrail accounting |
| `_agent.tool_calls_limit` | u32 | yes | `GuardrailState::effective_max_tool_calls(config, last_model_response_had_append_blocks)` |
| `_agent.tool_calls_remaining` | u32 | yes | `tool_calls_limit.saturating_sub(tool_calls_used)` |
| `_agent.allow_tool_call` | bool | yes | `GuardrailState::allow_tool_call(config, last_model_response_had_append_blocks)` |
| `_agent.reason` | string | **only when** `allow_tool_call == false` | Shutdown cause enum string (§3.2); MUST be absent when `allow_tool_call == true` |

**Literal JSON key order in serialized output is not constrained.** Tests assert parsed values, not byte-for-byte ordering.

### 3.2 Shutdown reason enum

Rust:

```rust
pub enum ToolBudgetShutdownReason {
    ToolCallBudgetExhausted,
    SoftFailureStreakExhausted,
    EnvelopeParseStreakExhausted,
}
```

JSON `reason` values (exact strings):

| Variant | JSON `reason` value | Guardrail predicate (first match wins when `allow_tool_call == false`) |
|---------|---------------------|------------------------------------------------------------------------|
| `ToolCallBudgetExhausted` | `"tool_call_budget_exhausted"` | `tool_calls_executed >= effective_max_tool_calls(...)` |
| `SoftFailureStreakExhausted` | `"soft_failure_streak_exhausted"` | `consecutive_soft_failure_turns >= config.tool_retry_limit` |
| `EnvelopeParseStreakExhausted` | `"envelope_parse_streak_exhausted"` | `consecutive_envelope_parse_failures >= config.tool_retry_limit` |

When `allow_tool_call == true`, `reason` MUST NOT appear in `_agent`.

### 3.3 Rust public API (new module)

```rust
pub struct AgentToolBudgetMeta {
    pub tool_calls_used: u32,
    pub tool_calls_limit: u32,
    pub tool_calls_remaining: u32,
    pub allow_tool_call: bool,
    pub reason: Option<ToolBudgetShutdownReason>,
}

impl GuardrailState {
    pub fn tool_budget_snapshot(
        &self,
        config: &AgentConfig,
        last_model_response_had_append_blocks: bool,
    ) -> AgentToolBudgetMeta;
}

/// Merge into last batch tool message when possible; else push synthetic tool message.
pub fn inject_tool_budget_into_session(
    session: &mut AgentSession,           // or &mut Vec<AgentMessage> + turn_index for ids
    turn_index: u32,
    meta: &AgentToolBudgetMeta,
    last_batch_tool_message_index: Option<usize>,
) -> InjectOutcome;

pub enum InjectOutcome {
    MergedIntoExistingObject { message_index: usize },
    AppendedSynthetic { message_id: String },
    SkippedNoToolMessages,
}
```

**Merge rules:**

1. Target the **last** tool message index from the current batch (final iteration of the `submit_tool_results` zip loop, or final reject pushed in `submit_model_response`).
2. Parse `content` as JSON `Value::Object`:
   - If top-level `_agent` already exists (executor-provided), **merge** budget fields into that object (engine fields overwrite on key collision).
   - Else insert new `_agent` key alongside existing tool payload keys (`results`, `error`, etc.).
3. If parse fails or value is not an object → append synthetic tool message:
   - `id`: `tool-agent-budget-{turn_index}`
   - `role`: `Tool`
   - `content`: serialized `{"_agent":{...}}` only
   - `metadata`: `{"agent_tool_budget": true}`

**Synthetic message MUST NOT run through tool-result truncation** (appended after truncation loop).

### 3.4 Session defaults used in tests

| Config field | Default (verified `session.rs`) |
|--------------|--------------------------------|
| `max_turns` | `20` |
| `tool_retry_limit` | `3` |
| `greedy_append_blocks` | `true` |

Greedy cap formula (verified `guardrails.rs`): when `greedy_append_blocks && last_model_response_had_append_blocks`, `effective_max = max(config.max_turns, tool_calls_executed.saturating_add(20))`.

### 3.5 Interaction with existing metadata

| Existing payload | Behavior |
|------------------|----------|
| Soft-failure tool JSON `{"error":"..."}` | Merge `_agent` sibling; still soft failure |
| Repair reject JSON | Merge on reject-only path via shared injector |
| `tools_blocked_nudge` synthetic tool | Injector may merge or append; must not remove `error` / `message` keys |
| `append_blocks` stored copy path | Budget injection uses the message actually pushed to `session.messages` (non-`append_blocks` branch messages) |

---

## 4. Data Model Changes

| Location | Change |
|----------|--------|
| `AgentMessage.content` (tool role) | May gain `_agent` object (merge or synthetic message) |
| `AgentMessage.metadata` | Synthetic-only path adds `"agent_tool_budget": true` |
| `GuardrailState` | No new persisted fields; optional snapshot helper only |
| `AgentEvent` | No new variants |
| Room / Firestore / Kotlin domain | **None** |

---

## 5. Ordered Implementation Tasks

| ID | Task | Verifies |
|----|------|----------|
| **T-1** | Add `agent_tool_budget.rs` with `AgentToolBudgetMeta`, `ToolBudgetShutdownReason`, JSON merge/synthetic helpers, and unit tests for snapshot math + reason priority | TB-04, TB-05, TB-06, MD-04, MD-06 |
| **T-2** | Add `GuardrailState::tool_budget_snapshot` in `guardrails.rs` reusing `effective_max_tool_calls` and `allow_tool_call` | TB-01, TB-03, TB-09 |
| **T-3** | Wire `inject_tool_budget_into_session` at end of `submit_tool_results` after guardrail block (both `skip_count` true and false paths) | TB-01, TB-02, TB-07, TB-08, TB-10, TB-11, MD-01, MD-03 |
| **T-4** | Call shared injector after reject-only synthetic tool pushes in `submit_model_response` (repair `Reject` branch and envelope parse-failure tool message) | TB-12, MD-01 |
| **T-5** | Add `third_party/agent_core/tests/tool_budget_agent_meta_test.rs` covering all TB-/MD- scenarios with in-process `AgentEngine` + `RecordingExecutor` / `PanicTransport` harness (pattern from `engine/mod.rs` `#[cfg(test)]`) | TB-01 … TB-12, MD-01 … MD-06 |
| **T-6** | Run full Rust suite; review/update `fixtures__events_guardrail_stop.snap` if snapshot diff is message-content-only | All scenarios |
| **T-7** | Grep gate: zero `_agent` string literals in `feature/inference/` and `core/agent/` production sources (metadata must originate in Rust engine only) | MD-02 |

---

## 6. Scenario ID Map

| Scenario IDs | Task(s) |
|--------------|---------|
| TB-01 … TB-03 | T-2, T-3, T-5 |
| TB-04 … TB-06 | T-1, T-5 |
| TB-07 … TB-08 | T-1, T-3, T-5 |
| TB-09 | T-2, T-5 |
| TB-10 | T-3, T-5 |
| TB-11 | T-5 |
| TB-12 | T-4, T-5 |
| MD-01 … MD-06 | T-5, T-7 |

---

## 7. Validation Commands (Before Declaring Complete)

```bash
cargo test --manifest-path third_party/agent_core/Cargo.toml
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_budget_agent_meta_test

# Grep gate (must exit 1 / zero matches in production paths)
! rg '"_agent"' feature/inference/src/main core/agent/src/main
```

No Gradle / UniFFI regeneration required for this ticket.

---

## 8. Drift and Escalation Rules

- **Contract waiver:** Any change that adds `_agent` parsing or budget logic to `:core:domain`, `:feature:inference`, or composables requires explicit **Contract Waiver**.
- **Single injector:** All `_agent` budget metadata MUST flow through `inject_tool_budget_into_session`. Do not duplicate merge logic in Kotlin or inference mappers.
- **Once per batch:** Injector MUST run at most once per `submit_tool_results` invocation and once per reject-only synthetic batch in `submit_model_response`. MD-01 is a merge gate.
- **Reason drift:** `reason` strings are a public JSON contract; rename only with spec amendment + test ID update.
- **Guardrail drift:** Snapshot MUST use `effective_max_tool_calls` and `allow_tool_call` — do not reimplement predicates in the injector.
- **Truncation drift:** `_agent` injection MUST occur **after** `truncate_tool_result_json` so budget fields are never truncated.
- **Executor `_agent` drift:** If a tool executor begins emitting `_agent`, engine merge MUST preserve non-budget keys under `_agent` (MD-06).
- **Scope creep:** Do not add `_agent` to `TurnStarted` events or provider system prompts; tool message JSON only.
- **Binding drift:** If a future ticket exposes budget via FFI, regenerate via `./scripts/build-agent-bindings.sh` — out of scope here.

---

## 9. Self-Review Checklist (plan author)

- [x] Every requirement mapped to file + task + scenario ID
- [x] Injection point anchored to verified `submit_tool_results` guardrail order
- [x] Merge vs synthetic fallback specified for non-object JSON
- [x] Reason enum values literal and tied to `GuardrailState` predicates
- [x] No Kotlin/UniFFI changes; no placeholders
- [x] Mutation-defense scenarios for duplication, Kotlin drift, and merge safety
- [x] Complements existing tools-blocked nudge without replacing it
