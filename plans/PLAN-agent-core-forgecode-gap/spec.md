# Spec: Forgecode Gap Plan for `third_party/agent_core`

## Feature Summary

Build the next planning target for `third_party/agent_core` by comparing verified Forgecode behaviors against the current local Rust core and closing only the real gaps. The plan must preserve `agent_core` strengths that already exceed Forgecode: deterministic host-yielding loop control, typed context references, policy-tiered compaction, KV-aware rewrite safety, artifact-state preservation, and canonical local tool-call repair/parsing.

Verified local baseline already present in `agent_core`:
- Deterministic loop actions: `CallModel`, `ExecuteTools`, `RequestSummary`, `ManualCompactCompleted`, `Finished`, `Failed`
- Trigger-aware compaction entry points: `Preflight`, `MidTurn`, `PostToolResult`, `Manual`
- Context store references for tool spill, history snapshots, artifact snapshots, file condensation, and deferred tool schemas
- State ledger anchors, prompt cache hints, policy tiers, and manual compact support
- Local-tool repair, soft-failure handling, artifact finalize behavior, and FFI/native accessors for reading or searching stored context refs

Verified Forgecode advantages worth importing or surpassing:
- Lifecycle hooks that can inject bounded reminders at request/response/tool-start/tool-end/end phases
- Doom-loop detection based on repeated tool-call signatures
- Structured retry metadata and policy/timeout denial reporting in tool-result surfaces
- Compaction trigger inputs beyond token ratio: absolute token threshold, turn count, message count, turn-end, retention window, eviction window
- Provider/model request transformer pipeline
- Dynamically rendered tool catalog views filtered by runtime capabilities and policy
- Read-before-edit protection based on tracked file-read metrics
- Task/agent-as-tool delegation semantics
- Richer conversation metrics including pending todos and active safety reminders

The implementation phase should raise `agent_core` above Forgecode by keeping the deterministic host-yielding core intact while adding explicit, typed, mutation-resistant contracts instead of stringly templated reminders or host-specific orchestration logic.

## Scope Boundaries

- Direct code phase scope: Rust files and Rust tests under `third_party/agent_core`
- No Kotlin or Android production changes in this ticket phase
- No changes to `AGENTS.md` or `/contracts/`
- No persistent database or disk schema changes in Pocket Crew modules
- UniFFI/Kotlin binding regeneration is a later integration concern if the Rust host-facing surface changes

## File Structure

| Action | Path | Responsibility |
|---|---|---|
| Modify | `third_party/agent_core/src/session.rs` | Extend `AgentConfig` and `SessionCapabilities` with hook toggles, compaction count thresholds, retention/eviction controls, tool-timeout policy, read-before-edit policy, and delegation capability flags. |
| Create | `third_party/agent_core/src/loop_hooks.rs` | Define bounded lifecycle hook contracts and built-in hook implementations for doom-loop reminders and pending-work reminders. |
| Create | `third_party/agent_core/src/loop_metrics.rs` | Track tool attempt counts, repeated tool signatures, files read this turn/session, todo-like pending work, and turn/message counters used by compaction and safety checks. |
| Modify | `third_party/agent_core/src/engine/state.rs` | Store hook registry, loop metrics, and pending reminder state without breaking deterministic phase transitions. |
| Modify | `third_party/agent_core/src/engine/mod.rs` | Invoke hooks at deterministic checkpoints, evaluate new compaction triggers, enrich tool-call/result metadata, and gate finish transitions on pending-work policies. |
| Modify | `third_party/agent_core/src/events.rs` | Add explicit hook, timeout, denial, retry, doom-loop, and pending-work events. |
| Modify | `third_party/agent_core/src/message.rs` | Add structured tool attempt/result metadata carried with tool calls and tool results instead of burying everything in untyped JSON strings. |
| Modify | `third_party/agent_core/src/turn_io.rs` | Extend `ModelRequest` with a runtime-rendered tool view, hook-produced reminder messages, and provider-transform diagnostics. |
| Modify | `third_party/agent_core/src/host.rs` | Add optional native Rust hook/transformer traits and policy callbacks while keeping existing transport/executor/summarizer roles stable. |
| Modify | `third_party/agent_core/src/budget.rs` | Support absolute token thresholds, turn-count thresholds, message-count thresholds, turn-end compaction, and retention/eviction-aware pressure planning. |
| Modify | `third_party/agent_core/src/compact/policy.rs` | Convert the new thresholds and windows into ordered compaction plans without regressing existing trigger semantics. |
| Modify | `third_party/agent_core/src/compact/pipeline.rs` | Respect retention windows, eviction windows, and turn-end compaction while preserving current policy-tier ordering guarantees. |
| Modify | `third_party/agent_core/src/state_ledger.rs` | Track pending-work anchors, loop-break reminders, and read-before-edit evidence in typed fields. |
| Modify | `third_party/agent_core/src/context_store.rs` | Persist reminder snapshots or policy evidence when the prompt-visible form must stay compact but retrievable. |
| Modify | `third_party/agent_core/src/catalog.rs` | Expand tool catalog metadata for edit safety, delegation, timeout override eligibility, dynamic visibility, and tool-family rendering. |
| Modify | `third_party/agent_core/resources/tool_catalog.json` | Add per-tool policy metadata such as `requires_prior_read`, `supports_delegation`, `default_timeout_ms`, `deferrable`, and `visibility_tags`. |
| Create | `third_party/agent_core/src/request_pipeline.rs` | Normalize provider/model request transformations behind a typed pipeline independent of host-specific DTO rendering. |
| Modify | `third_party/agent_core/src/lib.rs` | Re-export new loop hook, loop metrics, and request pipeline modules for native Rust consumers. |
| Modify | `third_party/agent_core/tests/fixtures.rs` | Add shared fixtures for repeated tool signatures, pending-work reminders, read-before-edit histories, and dynamic tool-view rendering. |
| Create | `third_party/agent_core/tests/loop_hooks_test.rs` | Cover lifecycle hook ordering, doom-loop reminders, and pending-work reminder injection. |
| Create | `third_party/agent_core/tests/loop_metrics_test.rs` | Cover repeated tool signatures, read-before-edit evidence, and retry-attempt accounting. |
| Modify | `third_party/agent_core/tests/compaction_triggers_test.rs` | Extend trigger coverage to absolute token, turn-count, message-count, and turn-end compaction. |
| Create | `third_party/agent_core/tests/request_pipeline_test.rs` | Verify provider/model transformer ordering and dynamic tool-view rendering. |
| Create | `third_party/agent_core/tests/tool_policy_test.rs` | Verify timeout metadata, policy denial surfacing, read-before-edit protection, and delegation-capable tool filtering. |
| Modify | `third_party/agent_core/tests/context_management_end_to_end_test.rs` | Prove mixed compaction, hooks, retries, and pending-work guards behave together without loop drift. |

## Architecture and Boundaries

### Rust Core Boundaries

- `engine/*` remains the single owner of the deterministic agentic loop.
- `loop_hooks.rs` may inject typed reminders or interrupt requests, but it must not directly mutate `AgentSession` or skip engine phases.
- `loop_metrics.rs` is passive state plus pure evaluators; it must not know about provider transport or compaction internals.
- `request_pipeline.rs` transforms `ModelRequest` only; it must not call tools, compact history, or perform provider IO.
- `catalog.rs` and `tool_catalog.json` remain declarative metadata sources; execution policy stays in the engine/host boundary.

### Out-of-Scope Boundaries

- No Android callback bridge changes in this phase
- No generated bindings refresh in this phase
- No host UI behavior changes in Pocket Crew app modules
- No replacement of the existing deterministic action enum with a free-running orchestrator

## API Contracts and Internal Interfaces

### `AgentConfig` additions

Add these fields in `third_party/agent_core/src/session.rs`:

```rust
pub struct AgentConfig {
    pub preflight_token_threshold: Option<u32>,
    pub mid_turn_token_threshold: Option<u32>,
    pub post_tool_result_token_threshold: Option<u32>,
    pub turn_threshold: Option<u32>,
    pub message_threshold: Option<u32>,
    pub compact_on_turn_end: bool,
    pub retention_window_messages: usize,
    pub eviction_window_ratio: f32,
    pub enable_loop_hooks: bool,
    pub enable_doom_loop_guard: bool,
    pub enable_pending_work_guard: bool,
    pub require_read_before_edit: bool,
    pub default_tool_timeout_ms: Option<u64>,
}
```

Contract notes:
- Existing ratio-based thresholds stay authoritative defaults.
- Absolute token thresholds are additive gates, not replacements.
- `retention_window_messages` and `eviction_window_ratio` apply only to destructive tiers (`Summary`, `Fifo`).

### `SessionCapabilities` additions

```rust
pub struct SessionCapabilities {
    pub request_transform_pipeline_supported: bool,
    pub lifecycle_hooks_supported: bool,
    pub delegation_tools_supported: bool,
    pub host_policy_callbacks_supported: bool,
}
```

Contract notes:
- These flags gate optional behavior only.
- `agent_core` must degrade to current behavior when flags are false.

### Lifecycle hook contracts

New file: `third_party/agent_core/src/loop_hooks.rs`

```rust
pub enum HookCheckpoint {
    BeforeModelRequest,
    AfterModelResponse,
    BeforeToolExecution,
    AfterToolResult,
    BeforeFinish,
}

pub struct HookContext<'a> {
    pub checkpoint: HookCheckpoint,
    pub turn_index: u32,
    pub session: &'a AgentSession,
    pub state_ledger: &'a StateLedger,
    pub loop_metrics: &'a LoopMetrics,
}

pub struct HookEffect {
    pub injected_messages: Vec<AgentMessage>,
    pub emitted_events: Vec<AgentEvent>,
    pub finish_blocked: bool,
}

pub trait AgentLoopHook: Send + Sync {
    fn evaluate(&self, context: &HookContext<'_>) -> Result<HookEffect, AgentError>;
}
```

Contract notes:
- Hooks are pure evaluators from engine-owned state to typed effects.
- Hooks cannot schedule tool execution, mutate compaction plans, or bypass guardrails.
- Engine applies effects in registration order and records all resulting events.

### Loop metrics contract

New file: `third_party/agent_core/src/loop_metrics.rs`

```rust
pub struct LoopMetrics {
    pub turn_count: u32,
    pub message_count: u32,
    pub files_read: BTreeSet<String>,
    pub repeated_tool_signatures: Vec<ToolSignatureWindow>,
    pub tool_attempts: BTreeMap<String, u32>,
    pub pending_work_items: Vec<PendingWorkItem>,
}
```

Required methods:
- `record_tool_call(&mut self, call: &ToolCallRecord) -> ()`
- `record_tool_result(&mut self, result: &ToolResultRecord) -> ()`
- `record_file_read(&mut self, path: &str) -> ()`
- `can_edit_path(&self, path: &str) -> bool`
- `detect_doom_loop(&self, threshold: usize) -> Option<DoomLoopMatch>`
- `has_pending_work(&self) -> bool`

### Tool call and result metadata

Extend `third_party/agent_core/src/message.rs`:

```rust
pub struct ToolAttemptMetadata {
    pub attempt_index: u32,
    pub attempts_remaining: Option<u32>,
    pub timeout_ms: Option<u64>,
    pub policy_denied: bool,
    pub requires_prior_read: bool,
}

pub struct ToolResultMetadata {
    pub duration_ms: Option<u64>,
    pub timed_out: bool,
    pub policy_denied: bool,
    pub prior_read_satisfied: bool,
    pub delegated_agent_name: Option<String>,
}
```

Contract notes:
- This metadata is typed and serialized with tool-call/result records.
- The prompt-visible `result_json` may still contain user-facing text, but tests must assert against structured metadata first.

### Request pipeline contract

New file: `third_party/agent_core/src/request_pipeline.rs`

```rust
pub trait RequestTransformer: Send + Sync {
    fn transform(&self, request: ModelRequest) -> Result<ModelRequest, AgentError>;
}

pub struct RequestPipeline {
    pub transformers: Vec<Arc<dyn RequestTransformer>>,
}
```

Contract notes:
- The pipeline runs once per `CallModel` emission after compaction and hook injection, before the request leaves the engine.
- Pipeline output must remain deterministic for the same input and transformer list.

### Dynamic tool view contract

Extend `ModelRequest` in `third_party/agent_core/src/turn_io.rs`:

```rust
pub struct ModelRequest {
    pub rendered_tool_catalog_json: Option<String>,
}
```

Behavior:
- When enabled, `rendered_tool_catalog_json` contains the filtered, runtime-safe tool view for the current request.
- `tools_schema_json` remains the canonical full schema input unless tool-schema deferral reduces it to a reference-backed form.

## Data / Schema Changes

No Room, SQLite, Gradle, or Android schema changes.

Direct Rust-schema changes:
- New serialized fields on `AgentConfig`, `SessionCapabilities`, `ToolCallRecord`, `ToolResultRecord`, and `ModelRequest`
- New internal structs/enums for hooks, loop metrics, pending work, request transformers, and runtime tool views
- Expanded `resources/tool_catalog.json` schema

Compatibility rules:
- All new fields must be additive and defaultable.
- Existing serialized fixtures must continue to deserialize without migration steps.
- If a future implementation requires UniFFI DTO changes, record that as a follow-up ticket after the Rust phase lands.

## Ordered Implementation Tasks

### Task 1: Add loop metrics and structured tool-attempt metadata

Files:
- `third_party/agent_core/src/message.rs`
- `third_party/agent_core/src/loop_metrics.rs`
- `third_party/agent_core/src/engine/state.rs`
- `third_party/agent_core/src/engine/mod.rs`
- `third_party/agent_core/tests/loop_metrics_test.rs`

Deliverables:
- Track turn count, message count, files read, tool attempts, and repeated signatures
- Add typed tool-attempt/result metadata
- Record file reads and read-before-edit eligibility evidence

Scenarios verified:
- `FCG-001`, `FCG-002`, `FCG-003`, `FCG-013`

### Task 2: Add deterministic lifecycle hooks with built-in doom-loop and pending-work guards

Files:
- `third_party/agent_core/src/loop_hooks.rs`
- `third_party/agent_core/src/events.rs`
- `third_party/agent_core/src/state_ledger.rs`
- `third_party/agent_core/src/engine/mod.rs`
- `third_party/agent_core/tests/loop_hooks_test.rs`

Deliverables:
- Hook checkpoints around request/response/tool-start/tool-end/finish
- Doom-loop reminder injection based on repeated signatures
- Pending-work reminder or finish-block behavior based on ledger/metrics state

Scenarios verified:
- `FCG-004`, `FCG-005`, `FCG-006`, `FCG-014`

### Task 3: Upgrade compaction planning to include absolute token, turn, message, and turn-end triggers plus retention/eviction windows

Files:
- `third_party/agent_core/src/session.rs`
- `third_party/agent_core/src/budget.rs`
- `third_party/agent_core/src/compact/policy.rs`
- `third_party/agent_core/src/compact/pipeline.rs`
- `third_party/agent_core/tests/compaction_triggers_test.rs`

Deliverables:
- Absolute token thresholds per trigger
- Turn-count, message-count, and turn-end compaction gates
- Retention/eviction windows that constrain destructive compaction tiers

Scenarios verified:
- `FCG-007`, `FCG-008`, `FCG-009`, `FCG-015`

### Task 4: Add request transformer pipeline and runtime-rendered tool catalog view

Files:
- `third_party/agent_core/src/request_pipeline.rs`
- `third_party/agent_core/src/turn_io.rs`
- `third_party/agent_core/src/catalog.rs`
- `third_party/agent_core/resources/tool_catalog.json`
- `third_party/agent_core/src/engine/mod.rs`
- `third_party/agent_core/tests/request_pipeline_test.rs`

Deliverables:
- Typed request transformer pipeline
- Runtime tool filtering and rendering based on capabilities and policy
- Delegation-aware tool metadata without changing the deterministic `ExecuteTools` host handoff

Scenarios verified:
- `FCG-010`, `FCG-011`, `FCG-012`, `FCG-016`

### Task 5: Add timeout/policy/read-before-edit enforcement paths and end-to-end integration coverage

Files:
- `third_party/agent_core/src/host.rs`
- `third_party/agent_core/src/context_store.rs`
- `third_party/agent_core/src/engine/mod.rs`
- `third_party/agent_core/tests/tool_policy_test.rs`
- `third_party/agent_core/tests/context_management_end_to_end_test.rs`

Deliverables:
- Host-call timeout metadata surfaced in tool results and events
- Policy denial reporting and read-before-edit rejection
- End-to-end coverage proving hooks, compaction, and tool governance compose without loop drift

Scenarios verified:
- `FCG-017`, `FCG-018`, `FCG-019`, `FCG-020`

## Test Plan

Primary command:

```bash
cd /home/sean/Code/pocket-crew/third_party/agent_core && cargo test
```

Targeted iteration commands allowed during implementation:

```bash
cd /home/sean/Code/pocket-crew/third_party/agent_core && cargo test loop_hooks_test
cd /home/sean/Code/pocket-crew/third_party/agent_core && cargo test loop_metrics_test
cd /home/sean/Code/pocket-crew/third_party/agent_core && cargo test compaction_triggers_test
cd /home/sean/Code/pocket-crew/third_party/agent_core && cargo test request_pipeline_test
cd /home/sean/Code/pocket-crew/third_party/agent_core && cargo test tool_policy_test
```

## Drift and Escalation Rules

- If implementation work would remove or weaken the deterministic `AgentAction` host-yielding loop, stop and escalate.
- If a proposed hook API needs arbitrary session mutation instead of typed `HookEffect`, stop and escalate.
- If a change requires Android/Kotlin callback or generated binding updates, land the Rust-native contract first and record a separate integration ticket.
- If a change requires persistent storage outside `third_party/agent_core`, stop and escalate.
- If comparison findings conflict with the verified local baseline, prefer current code reality over ticket assumptions and update the plan rather than re-planning already-implemented behavior.
- If a tool-governance feature can only be expressed as unstructured prompt text and cannot be asserted structurally in tests, stop and strengthen the contract before implementation.
- If any work would touch `AGENTS.md` or `/contracts/`, require an explicit Contract Waiver.

## Later Integration Concerns

- Mirror any Rust-native host-facing additions into UniFFI DTOs and regenerate bindings after the Rust phase is approved and implemented.
- Decide whether Android host adapters should expose hook registration and request transformers directly or keep them disabled for Pocket Crew initially.
- Revisit whether pending-work reminders should map to app-level task/todo UX, but keep that outside this Rust planning ticket.
