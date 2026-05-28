# Spec: Long Agent Loop Robustness (`agent_core` + Host Bridge)

**Ticket ID:** `FEAT-long-agent-loop-robustness`  
**Primary scope:** `third_party/agent_core` (Rust) + UniFFI DTO contract in `third_party/agent_core/src/ffi/*`  
**Host integration scope:** Documented follow-up in `:core:agent`, `:core:domain`, `:feature:inference` (no production Kotlin in this ticket phase)

---

## 1. Feature Summary

Enclave’s Rust agent loop (`third_party/agent_core`) already runs trigger-aware compaction, context-store spillover, state ledger, and tool/file indexes — but **long tool-heavy sessions** still depend on Kotlin mitigations (`appendonly/*`, cap-at-2 frozen sidecars, JSON compaction, digest-based log clear). Those Kotlin layers cannot enforce transcript integrity or bounded sidecar **generation** at the source.

This ticket delivers a **focused v1 foundation** so Android and future desktop hosts share one policy surface and receive **explicit compaction boundaries** instead of inferring rewrites from `sessionMessages` shape alone.

### 1.1 In scope (v1 slice)

| Capability | v1 deliverable |
|---|---|
| **Explicit compaction contract** | `CompactionBoundary` + monotonic `transcript_revision` on every destructive rewrite; `PostCompact` carries `transcript_revision_after`. |
| **Append-only transcript** | No silent in-place content edits; micro-tier clears become tombstone + replacement messages with new IDs and `rewrite_of` metadata. |
| **Bounded volatile sidecars** | `SidecarBuilder` emits ledger/index **deltas** and refs under per-call and per-session byte budgets; `ProviderContextSnapshot` on `ModelRequest`. |
| **Long-loop policy** | `LongLoopPolicy` embedded in `AgentConfig`: `max_tool_turns`, sidecar budgets, `summarize_after_tool_turns`, `compact_every_n_tool_turns`. |
| **Long-loop tests** | Rust integration test harness simulating **25** tool turns with byte/token/message bounds and fact preservation. |
| **FFI / bindings contract** | New UniFFI records and `AgentEvent` variant; binding regen listed as follow-up task. |

### 1.2 Out of scope (escalation — see §10)

- Full `PLAN-agent-core-context-management` vision (retrieval search UI, manual compact UX, file condensation host IO).
- Full `PLAN-agent-core-forgecode-gap` (doom-loop hooks, request transformer pipeline, read-before-edit engine gates) except **turn-count compaction triggers** already partially present.
- Kotlin provider placement changes (`FEAT-append-only-provider-context` — **done**).
- Remote prompt-cache breakpoint rules (`plans/prompt-caching/spec.md` — unchanged).

### 1.3 Relationship to landed Kotlin work

| Artifact | Role |
|---|---|
| `plans/FEAT-append-only-provider-context/spec.md` | Provider wire append-only + digest safety (**done**). Host must **invalidate intentionally** on Rust boundaries, not only `digest_mismatch`. |
| `plans/prompt-caching/spec.md` | Tail-volatile sidecar placement; Rust budgets must not break stable-prefix bytes Kotlin freezes. |
| `plans/PLAN-agent-core-context-management/spec.md` | Mega-plan; v1 takes **revision boundaries + bounded sidecars + long-loop policy** only. |
| `plans/PLAN-agent-core-forgecode-gap/spec.md` | Mega-plan; v1 takes **turn/message threshold compaction** wiring only (fields exist; behavior made deterministic for long loops). |

### 1.4 Architecture flow

```
AgentEngine.run_compaction_pass(trigger)
  ├─ emit PreCompact { trigger }
  ├─ for each tier (ToolSpill → Micro → … → Summary → FIFO)
  │     ├─ if tier mutates transcript destructively:
  │     │     ├─ bump transcript_revision
  │     │     ├─ apply tier via append-only rewrite helpers (new message IDs)
  │     │     └─ record CompactionBoundary { boundary_id, revision_before, revision_after, tiers[] }
  │     └─ emit CompactionApplied { tier, tokens_before, tokens_after, trigger }
  └─ emit PostCompact { trigger, summary_ref, history_ref, transcript_revision_after }

AgentEngine.step_idle → CallModel(ModelRequest)
  ├─ session_messages (append-only IDs; no silent edits)
  ├─ provider_context: ProviderContextSnapshot { transcript_revision, sidecar_generation, byte_len, … }
  ├─ state_ledger_delta / tool_context_index_delta / file_context_index_delta (bounded)
  └─ context_references (unchanged store refs)

UniFFI → Kotlin AgentEventMapper → RustLoopInferenceService
  └─ [follow-up] invalidate ProviderAppendOnlyContext on CompactionBoundary / PostCompact
```

---

## 2. File Structure Table

| Action | Path | Responsibility |
|---|---|---|
| **Create** | `third_party/agent_core/src/long_loop_policy.rs` | `LongLoopPolicy` defaults and validation; serde on `AgentConfig`. |
| **Create** | `third_party/agent_core/src/transcript_revision.rs` | `TranscriptRevision` counter, `CompactionBoundary` record, append-only rewrite helpers. |
| **Create** | `third_party/agent_core/src/sidecar_builder.rs` | Bounded delta sidecars for ledger, tool index, file index; generation counters. |
| **Modify** | `third_party/agent_core/src/session.rs` | Add `long_loop: LongLoopPolicy` to `AgentConfig`; track `transcript_revision` on `AgentSession`. |
| **Modify** | `third_party/agent_core/src/message.rs` | Document `metadata.rewrite_of`, `metadata.compaction_tier` conventions for replacement messages. |
| **Modify** | `third_party/agent_core/src/events.rs` | Add `CompactionBoundary` payload struct; extend `AgentEvent::CompactionBoundary`. |
| **Modify** | `third_party/agent_core/src/turn_io.rs` | Add `ProviderContextSnapshot` to `ModelRequest`; extend `PostCompact` payload in events (via events.rs). |
| **Modify** | `third_party/agent_core/src/compact/micro.rs` | Route clears through `transcript_revision::replace_tool_output` (new ID + tombstone metadata). |
| **Modify** | `third_party/agent_core/src/compact/fifo.rs` | Snapshot + drain via revision bump; no silent `Vec` swap without boundary. |
| **Modify** | `third_party/agent_core/src/compact/summary.rs` | `CompactSummary` insert increments revision; boundary lists `summary` tier. |
| **Modify** | `third_party/agent_core/src/compact/tool_spill.rs` | Spill replacements use new message IDs when content envelope changes. |
| **Modify** | `third_party/agent_core/src/engine/state.rs` | `InnerSession`: `transcript_revision`, `sidecar_generation`, `last_shipped_ledger_hash`. |
| **Modify** | `third_party/agent_core/src/engine/mod.rs` | Emit boundaries; build `SidecarBuilder` per `CallModel`; enforce `max_tool_turns`; turn-count triggers. |
| **Modify** | `third_party/agent_core/src/budget.rs` | `should_compact_for_long_loop(policy, metrics)` for turn/message thresholds. |
| **Modify** | `third_party/agent_core/src/ffi/types.rs` | `FfiLongLoopPolicy`, `FfiProviderContextSnapshot`, `FfiCompactionBoundary`, extend `FfiAgentEvent`, `FfiModelRequest`, `FfiAgentConfig`. |
| **Modify** | `third_party/agent_core/src/ffi/mod.rs` | Export new types; map events in `drain_events`. |
| **Modify** | `third_party/agent_core/src/lib.rs` | `pub mod long_loop_policy`, `transcript_revision`, `sidecar_builder`. |
| **Modify** | `third_party/agent_core/Cargo.toml` | Semver minor bump (`0.x` → next minor) for FFI contract change. |
| **Create** | `third_party/agent_core/tests/long_agent_loop_test.rs` | 25-turn simulated loop: bounds + fact preservation (`LALR-01`, `LALR-11`, `LALR-12`, `LALR-20`). |
| **Create** | `third_party/agent_core/tests/transcript_revision_test.rs` | Revision monotonicity, micro rewrite IDs, mutation-defense (`LALR-02`, `LALR-04`, `LALR-17`). |
| **Create** | `third_party/agent_core/tests/sidecar_budget_test.rs` | Byte budgets, deltas, generations (`LALR-05`, `LALR-06`, `LALR-10`, `LALR-13`, `LALR-18`). |
| **Create** | `third_party/agent_core/tests/compaction_boundary_test.rs` | Boundary emission, FFI roundtrip, PostCompact revision (`LALR-03`, `LALR-09`, `LALR-15`, `LALR-16`). |
| **Modify** | `third_party/agent_core/tests/fixtures.rs` | Helpers: `run_tool_turn_loop`, `inject_semantic_fact`, pressure fixtures for 25 turns. |
| **Modify** | `third_party/agent_core/tests/compaction_triggers_test.rs` | Turn-count and `summarize_after_tool_turns` paths (`LALR-07`, `LALR-08`). |
| **Follow-up** | `core/domain/.../AgentLoopAgentConfig.kt` | Mirror `LongLoopPolicy` fields from FFI. |
| **Follow-up** | `core/agent/.../AgentEventMapper.kt` | Map `CompactionBoundary`, `ProviderContextSnapshot`. |
| **Follow-up** | `feature/inference/.../RustLoopInferenceService.kt` | Invalidate append-only on boundary; pass `transcriptRevision` into provider session. |
| **Follow-up** | `feature/inference/.../BaseOpenAiSdkInferenceService.kt` | Accept optional `transcriptRevision` for logging and assert monotonic per prompt. |
| **Follow-up** | `scripts/build-agent-bindings.sh` | Regenerate `agent_core.kt` after FFI change. |

---

## 3. API Contracts and Interfaces

### 3.1 `LongLoopPolicy` (`long_loop_policy.rs`)

```rust
pub struct LongLoopPolicy {
    /// Hard cap on tool rounds per user sendPrompt (distinct from legacy max_turns guardrail).
    pub max_tool_turns: u32,
    /// Max UTF-8 bytes for all sidecar JSON fields combined on one CallModel.
    pub sidecar_byte_budget_per_call: usize,
    /// Max UTF-8 bytes retained in SidecarBuilder rolling cache per session.
    pub sidecar_total_byte_budget: usize,
    /// After this many completed tool rounds, force Summary tier if policy allows.
    pub summarize_after_tool_turns: u32,
    /// Run compaction preflight every N tool rounds even when ratio is low.
    pub compact_every_n_tool_turns: u32,
}
```

**Defaults (cloud / `HOSTED_MUTABLE` and `HIGH_CONTEXT`):**

| Field | Default |
|---|---|
| `max_tool_turns` | `40` |
| `sidecar_byte_budget_per_call` | `8192` |
| `sidecar_total_byte_budget` | `16384` |
| `summarize_after_tool_turns` | `12` |
| `compact_every_n_tool_turns` | `4` |

**Defaults (`FIXED_KV` / local LiteRT):**

| Field | Default |
|---|---|
| `max_tool_turns` | `20` |
| `sidecar_byte_budget_per_call` | `4096` |
| `sidecar_total_byte_budget` | `8192` |
| `summarize_after_tool_turns` | `8` |
| `compact_every_n_tool_turns` | `3` |

`AgentConfig` gains `#[serde(default)] pub long_loop: LongLoopPolicy`. Existing `max_turns` remains the soft guardrail for tool retry / finish logic; when `long_loop.max_tool_turns` is set, engine uses `min(max_turns, long_loop.max_tool_turns)` for tool-stop (`LALR-12`).

### 3.2 `TranscriptRevision` and `CompactionBoundary` (`transcript_revision.rs`)

```rust
pub struct TranscriptRevision(pub u64);

pub struct CompactionBoundary {
    pub boundary_id: String,
    pub revision_before: u64,
    pub revision_after: u64,
    pub trigger: CompactionTrigger,
    pub tiers_applied: Vec<CompactionTier>,
    pub message_count_before: u32,
    pub message_count_after: u32,
    pub destructive: bool,
}
```

**Rules:**

1. `revision_before` starts at `0` for a new session; first destructive tier sets `revision_after` to `1`.
2. `boundary_id` format: `cb-{revision_after}-{trigger}` (e.g. `cb-3-preflight`). Literal format required by tests.
3. Non-destructive tiers (`HistorySnapshot` prelude, `ToolSpill` when only store grows) do **not** bump revision unless message content changes.
4. `AgentEvent::CompactionBoundary(CompactionBoundary)` is emitted **once per compaction pass** that applied at least one destructive tier, after tiers complete, before `PostCompact`.
5. `AgentEvent::PostCompact` gains `transcript_revision_after: u64` (required field).

**Append-only rewrite helper:**

```rust
pub fn replace_message_content(
    session: &mut AgentSession,
    message_id: &str,
    new_content: String,
    tier: CompactionTier,
) -> AgentMessage;
```

Contract: removes no rows silently; inserts replacement `AgentMessage` with new `id` (`msg-{uuid}`), sets `metadata.rewrite_of = message_id`, `metadata.compaction_tier = tier.to_string()`, marks prior message `metadata.superseded = true` (content unchanged for audit). Micro tier uses this instead of mutating `content` in place (`LALR-04`, `LALR-17`).

### 3.3 `ProviderContextSnapshot` (`turn_io.rs`)

```rust
pub struct ProviderContextSnapshot {
    pub transcript_revision: u64,
    pub sidecar_generation: u32,
    pub sidecar_byte_len: u32,
}
```

`ModelRequest` gains:

```rust
pub provider_context: ProviderContextSnapshot,
pub state_ledger_delta_json: Option<String>,
pub tool_context_index_delta_json: Option<String>,
pub file_context_index_delta_json: Option<String>,
```

Legacy optional full objects (`state_ledger`, `tool_context_index`, `file_context_index`) remain for one release; engine populates **either** full snapshot on generation `0` **or** deltas when `sidecar_generation > 0` and bytes fit budget (`LALR-06`). Kotlin `buildContextSidecars` continues to accept full JSON until follow-up consumes deltas.

### 3.4 `SidecarBuilder` (`sidecar_builder.rs`)

```rust
pub struct SidecarBuilder {
    generation: u32,
    bytes_accumulated: usize,
}

impl SidecarBuilder {
    pub fn next_generation(&mut self) -> u32;
    pub fn build_ledger_delta(
        &mut self,
        ledger: &StateLedger,
        last_hash: &str,
        budget: usize,
    ) -> Option<String>;
    pub fn build_tool_index_delta(
        &mut self,
        index: &ToolContextIndex,
        budget: usize,
    ) -> Option<String>;
    pub fn build_file_index_delta(
        &mut self,
        index: &FileContextIndex,
        budget: usize,
    ) -> Option<String>;
    pub fn enforce_total_budget(&mut self, policy: &LongLoopPolicy) -> ();
}
```

When `build_*` exceeds `sidecar_byte_budget_per_call`, truncate to `{"refs":["ctxref-…"],"truncated":true}` with stable ref IDs from `ContextStore` (`LALR-05`, `LALR-18`). `sidecar_generation` increments on every `CallModel` (`LALR-10`).

### 3.5 Long-loop compaction triggers (`engine/mod.rs` + `budget.rs`)

In addition to ratio thresholds, preflight compaction runs when:

- `tool_round_index > 0` and `tool_round_index % long_loop.compact_every_n_tool_turns == 0`, or
- `tool_round_index >= long_loop.summarize_after_tool_turns` and `summarization_allowed` (Summary tier eligible), or
- existing `turn_threshold` / `message_threshold` / absolute token thresholds fire.

`tool_round_index` counts completed tool-result ingestions in the current user turn.

### 3.6 UniFFI mirrors (`ffi/types.rs`)

```rust
pub struct FfiLongLoopPolicy { /* same fields as LongLoopPolicy */ }
pub struct FfiProviderContextSnapshot {
    pub transcript_revision: u64,
    pub sidecar_generation: u32,
    pub sidecar_byte_len: u32,
}
pub struct FfiCompactionBoundary {
    pub boundary_id: String,
    pub revision_before: u64,
    pub revision_after: u64,
    pub trigger: FfiCompactionTrigger,
    pub tiers_applied: Vec<FfiCompactionTier>,
    pub message_count_before: u32,
    pub message_count_after: u32,
    pub destructive: bool,
}
```

`FfiAgentEvent` adds variant `CompactionBoundary { boundary: FfiCompactionBoundary }`.  
`FfiPostCompact` adds `transcript_revision_after: u64`.  
`FfiModelRequest` adds `provider_context`, `state_ledger_delta_json`, `tool_context_index_delta_json`, `file_context_index_delta_json`.

---

## 4. Data Model and Schema Changes

- **Room / Firestore:** none.
- **`:core:domain`:** follow-up only — optional `LongLoopPolicy` on `AgentLoopAgentConfig` (no persistence).
- **`agent_core` semver:** minor bump; regenerate Android bindings in follow-up.

---

## 5. Ordered Implementation Tasks

### Task 1: `LongLoopPolicy` and `AgentConfig` wiring
- **Description:** Add `long_loop_policy.rs`, embed in `AgentConfig` with tier-specific defaults; validate `compact_every_n_tool_turns >= 1`.
- **Files:** `long_loop_policy.rs`, `session.rs`, `lib.rs`
- **Scenarios:** `LALR-12`

### Task 2: `TranscriptRevision` and append-only rewrite helpers
- **Description:** Implement revision counter on `AgentSession`, `CompactionBoundary` struct, `replace_message_content`, supersede metadata conventions.
- **Files:** `transcript_revision.rs`, `message.rs`, `session.rs`
- **Scenarios:** `LALR-02`, `LALR-04`, `LALR-17`

### Task 3: Compaction tier integration (destructive path)
- **Description:** Refactor `micro.rs`, `fifo.rs`, `summary.rs`, `tool_spill.rs` to use rewrite helpers; bump revision once per pass with combined `tiers_applied`.
- **Files:** `compact/micro.rs`, `compact/fifo.rs`, `compact/summary.rs`, `compact/tool_spill.rs`
- **Scenarios:** `LALR-02`, `LALR-04`, `LALR-14`, `LALR-17`

### Task 4: `CompactionBoundary` / `PostCompact` events
- **Description:** Extend `events.rs`; emit from `engine/mod.rs::run_compaction_pass`; include `transcript_revision_after` on `PostCompact`.
- **Files:** `events.rs`, `engine/mod.rs`, `engine/state.rs`
- **Scenarios:** `LALR-03`, `LALR-09`, `LALR-15`, `LALR-19`

### Task 5: `SidecarBuilder` and `ProviderContextSnapshot`
- **Description:** Implement bounded deltas; attach to `ModelRequest` in `step_idle`; increment `sidecar_generation` per model call.
- **Files:** `sidecar_builder.rs`, `turn_io.rs`, `engine/mod.rs`
- **Scenarios:** `LALR-05`, `LALR-06`, `LALR-10`, `LALR-13`, `LALR-18`

### Task 6: Long-loop compaction triggers
- **Description:** Wire `compact_every_n_tool_turns` and `summarize_after_tool_turns` in preflight; extend `budget.rs` helper.
- **Files:** `budget.rs`, `engine/mod.rs`, `tests/compaction_triggers_test.rs`
- **Scenarios:** `LALR-07`, `LALR-08`

### Task 7: `max_tool_turns` enforcement
- **Description:** Stop emitting `ExecuteTools` when tool round index reaches policy cap; finish with `GuardrailExceeded { kind: "max_tool_turns" }` if model still requests tools.
- **Files:** `engine/mod.rs`, `long_loop_policy.rs`
- **Scenarios:** `LALR-12`

### Task 8: UniFFI contract
- **Description:** Mirror all new types and events in `ffi/types.rs` and `ffi/mod.rs`; update event drain mapping.
- **Files:** `ffi/types.rs`, `ffi/mod.rs`, `Cargo.toml`
- **Scenarios:** `LALR-16`

### Task 9: `long_agent_loop_test.rs`
- **Description:** Simulated host: 25 tool rounds with monotonic pressure; assert `transcript_revision >= 1`, sidecar bytes `<= 8192`, objective string survives in ledger or `CompactSummary`.
- **Files:** `tests/long_agent_loop_test.rs`, `tests/fixtures.rs`
- **Scenarios:** `LALR-01`, `LALR-11`, `LALR-20`

### Task 10: Focused unit/integration tests
- **Description:** Add `transcript_revision_test.rs`, `sidecar_budget_test.rs`, `compaction_boundary_test.rs`.
- **Files:** three new test files
- **Scenarios:** `LALR-02`–`LALR-06`, `LALR-10`, `LALR-13`–`LALR-18`

### Task 11: Host bridge follow-up (Kotlin — separate PR)
- **Description:** Map FFI fields; on `AgentLoopEvent.CompactionBoundary` and `PostCompact`, call `ProviderAppendOnlyContext.invalidate()` or `clearLogState(reason = "rust_compaction_boundary")` before next `completeSingleTurn`; thread `transcript_revision` through `GenerationOptions` for logging.
- **Files:** `AgentEventMapper.kt`, `AgentLoopAgentConfig.kt`, `RustLoopInferenceService.kt`, `BaseOpenAiSdkInferenceService.kt`, run `scripts/build-agent-bindings.sh`
- **Scenarios:** `LALR-H01`, `LALR-H02`, `LALR-H03`

---

## 6. Drift and Escalation Rules

| Rule | Action |
|---|---|
| **Drift — Kotlin append-only** | If `ProviderAppendOnlyContext` changes frozen sidecar cap, keep Rust `sidecar_byte_budget_per_call` ≤ Kotlin wire cap (`MAX_FROZEN_SIDECARS_ON_WIRE = 2` messages × per-message budget). |
| **Drift — `max_turns` vs `max_tool_turns`** | If product owners collapse fields, update `LongLoopPolicy` defaults and `LALR-12` literal in same PR. |
| **Escalation — Full forgecode hooks** | Defer to `PLAN-agent-core-forgecode-gap`; do not block v1 on doom-loop reminders. |
| **Escalation — Cross-session persistence** | Transcript revision is in-memory per session only; persistence requires Contract Waiver. |
| **Escalation — Provider-native structured sidecars** | Anthropic/Google structured blocks remain Kotlin; Rust ships JSON deltas only until provider APIs accept structured refs. |

---

## 7. Acceptance Criteria

1. `cargo test` in `third_party/agent_core` passes all `LALR-*` scenarios.
2. 25-turn integration test proves bounded sidecars and preserved semantic anchor (`LALR-01`, `LALR-11`).
3. Micro tier never leaves same `AgentMessage.id` with changed `content` without `rewrite_of` metadata (`LALR-17`).
4. Every destructive compaction pass emits exactly one `CompactionBoundary` with `boundary_id` matching `cb-{revision_after}-{trigger}` (`LALR-03`).
5. UniFFI structs compile; follow-up binding regen documented in Task 11.
6. No changes to `AGENTS.md` or `/contracts/` in Rust-only phase.
