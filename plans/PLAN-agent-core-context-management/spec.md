# Spec: Full-Spectrum Context Management for `third_party/agent_core`

## Objective
Evolve `third_party/agent_core` from a preflight-only compaction pipeline into a complete context-management subsystem comparable to Claude Code and Cursor. The target design covers trigger-aware compaction, semantic state preservation, file-backed spillover, retrievable history, tool-schema deferral, file/folder condensation, manual compact lifecycle hooks, provider/runtime policy tiers, and regression tests that prove facts are preserved without relying on destructive FIFO history deletion.

## Evidence Boundaries

Observed local behavior:
- Current default compaction order is `Micro`, `Fifo`, `ArtifactSquash`, `Summary`.
- `should_compact()` uses pressure ratio versus `AgentConfig.threshold_ratio`.
- The engine currently runs compaction automatically only from preflight before a model call.
- `Micro` clears older reproducible non-artifact tool outputs.
- `Fifo` removes old conversational units and can lose early facts.
- `ArtifactSquash` compresses `append_blocks` artifact loops into one synthetic system summary.
- `Summary` is host-mediated and is not available when `summarization_allowed = false` or `kv_fixed = true`.
- `CompactionTrigger` already has `Preflight`, `MidTurn`, and `PostToolResult`, but the loop does not currently run all three automatically.

External behavior used as design input:
- Claude Code documents manual `/compact`, automatic compaction near high context pressure, `PreCompact` and `PostCompact` hooks, subagents with separate context windows, MCP tool schema deferral, oversized MCP output spillover to files, and context re-injection after compaction.
- Cursor documents automatic summarization, manual summarization, smart file/folder condensation, large output spillover to files, retrievable chat-history files, and dynamic tool metadata discovery.
- The public sources do not expose the private Claude Code or Cursor compactor implementation. Any matching behavior in this spec is a recommended `agent_core` design derived from public product behavior, not a claim that their internal code uses the same symbols or exact algorithms.

## File Structure

| Action | Path | Responsibility |
|---|---|---|
| Modify | `third_party/agent_core/src/session.rs:7-117` | Extend `AgentConfig`, `SessionCapabilities`, and `AgentSession` with trigger thresholds, feature flags, policy tier, state ledger, context references, and compaction lifecycle state. |
| Modify | `third_party/agent_core/src/budget.rs:1-269` | Replace global threshold checks with plan-specific budget evaluation, reserves, file-reference accounting, provider corrections, and tool-schema budget accounting. |
| Create | `third_party/agent_core/src/policy.rs` | Define provider/runtime context policy tiers and normalize capabilities into a single `ContextPolicy`. |
| Create | `third_party/agent_core/src/context_store.rs` | Store large tool outputs, terminal outputs, history snapshots, artifact snapshots, and file-condensation payloads behind stable references. |
| Create | `third_party/agent_core/src/state_ledger.rs` | Preserve semantic continuity as durable task state independent of raw chat history. |
| Create | `third_party/agent_core/src/tool_context.rs` | Condense tool schemas and expose deferred tool metadata references/search indices. |
| Create | `third_party/agent_core/src/file_context.rs` | Condense included files/folders into structural references, outlines, and searchable chunks. |
| Create | `third_party/agent_core/src/manual_compact.rs` | Implement manual compact request planning and pre/post compact lifecycle events. |
| Create | `third_party/agent_core/src/compact/policy.rs` | Build trigger-aware compaction plans and tier order per checkpoint. |
| Modify | `third_party/agent_core/src/compact/mod.rs:1-72` | Export the new compaction policy module and the additional tiers. |
| Modify | `third_party/agent_core/src/compact/pipeline.rs:6-260` | Execute planner-selected tiers, stop against plan-specific thresholds, and emit tier skip reasons. |
| Create | `third_party/agent_core/src/compact/tool_spill.rs` | Replace oversized tool outputs with references while preserving raw payloads in `ContextStore`. |
| Create | `third_party/agent_core/src/compact/history_snapshot.rs` | Persist compacted-away transcript ranges as retrievable history references before summary/FIFO. |
| Modify | `third_party/agent_core/src/compact/micro.rs:1-260` | Make micro compaction reference-aware and never erase outputs that lack raw recovery references. |
| Modify | `third_party/agent_core/src/compact/artifact_squash.rs:1-380` | Persist artifact progress into `ContextStore` and `StateLedger` before squashing loop messages. |
| Modify | `third_party/agent_core/src/compact/fifo.rs:1-340` | Gate FIFO by policy, snapshot deleted ranges, and protect state-ledger/history-reference anchors. |
| Modify | `third_party/agent_core/src/compact/summary.rs:1-360` | Add trigger metadata, summary quality requirements, history references, and state-ledger anchoring. |
| Modify | `third_party/agent_core/src/turn_io.rs:1-170` | Extend `ModelRequest`, `SummaryRequest`, and manual compact request/response types with context references and trigger metadata. |
| Modify | `third_party/agent_core/src/events.rs:1-260` | Add policy, lifecycle, context-store, state-ledger, tool-context, and file-context observability events. |
| Modify | `third_party/agent_core/src/action.rs:1-120` | Add manual compact completion action support. |
| Modify | `third_party/agent_core/src/message.rs:1-90` | Add reference metadata conventions for prompt-facing tool output replacement. |
| Modify | `third_party/agent_core/src/engine/state.rs:1-90` | Track pending resume continuations, active context policy, pending manual compact, context store, state ledger, and tool/file context indices. |
| Modify | `third_party/agent_core/src/engine/mod.rs:1-1140` | Invoke context management at all loop boundaries, wire manual compact, resume summary continuations, and include references in model requests. |
| Modify | `third_party/agent_core/src/host.rs:1-120` | Add host callback traits for context storage, context retrieval/search, tool-context discovery, and file/folder condensation where the host owns IO. |
| Modify | `third_party/agent_core/src/ffi/types.rs:1-520` | Mirror new config, request, event, reference, manual compact, and policy DTOs across UniFFI. |
| Modify | `third_party/agent_core/src/ffi/mod.rs:1-105` | Export new context-management FFI methods and callback surfaces. |
| Modify | `third_party/agent_core/src/lib.rs:1-45` | Re-export new modules and FFI DTOs. |
| Modify | `third_party/agent_core/resources/tool_catalog.json` | Add optional tool metadata fields used for reproducibility, deferrability, output caps, and schema-loading hints. |
| Modify | `third_party/agent_core/tests/fixtures.rs:1-220` | Add shared fixtures for pressure, summaries, tool spillover, context references, and file condensation. |
| Modify | `third_party/agent_core/tests/kv_fixed_test.rs:1-120` | Extend fixed-KV coverage across synchronous compaction, reference-only compaction, and summary disallowance. |
| Create | `third_party/agent_core/tests/compaction_triggers_test.rs` | Engine-level trigger and resume-flow integration tests. |
| Create | `third_party/agent_core/tests/context_store_test.rs` | Context-store spillover, retrieval, search, and retention tests. |
| Create | `third_party/agent_core/tests/state_ledger_test.rs` | Semantic continuity and state-ledger merge tests. |
| Create | `third_party/agent_core/tests/tool_context_test.rs` | Tool-schema deferral and metadata discovery tests. |
| Create | `third_party/agent_core/tests/file_context_test.rs` | File/folder condensation tests. |
| Create | `third_party/agent_core/tests/manual_compact_test.rs` | Manual compaction lifecycle and focus-instruction tests. |
| Create | `third_party/agent_core/tests/policy_tiers_test.rs` | Hosted, local, fixed-KV, and high-context policy tests. |
| Create | `third_party/agent_core/tests/context_management_end_to_end_test.rs` | End-to-end context preservation tests across mixed tool, file, artifact, and summary pressure. |
| Modify | `third_party/agent_core/tests/snapshots/fixtures__events_compaction_under_pressure.snap` | Update event snapshots after lifecycle event expansion. |
| Modify | `third_party/agent_core/Cargo.toml:1-26` | Bump crate version for FFI contract changes. |

## Data Models & Schemas

Existing models to extend:
- `AgentConfig` in `third_party/agent_core/src/session.rs`
- `SessionCapabilities` in `third_party/agent_core/src/session.rs`
- `AgentSession` in `third_party/agent_core/src/session.rs`
- `Pressure` in `third_party/agent_core/src/session.rs`
- `ModelRequest` and `SummaryRequest` in `third_party/agent_core/src/turn_io.rs`
- `InnerSession` in `third_party/agent_core/src/engine/state.rs`
- `AgentEvent`, `CompactionEvent`, and `CompactionTier` in `third_party/agent_core/src/events.rs`
- `CompactionTrigger` in `third_party/agent_core/src/events.rs`
- `AgentError` in `third_party/agent_core/src/events.rs`
- `AgentAction` in `third_party/agent_core/src/action.rs`
- `FfiAgentConfig`, `FfiSessionCapabilities`, `FfiModelRequest`, `FfiSummaryRequest`, and `FfiAgentEvent` in `third_party/agent_core/src/ffi/types.rs`

New internal models:

```rust
pub enum ContextPolicyTier {
    HostedMutable,
    HostedProxy,
    LocalMutable,
    FixedKv,
    HighContext,
    SmallContext,
}
```

```rust
pub enum CompactionTrigger {
    Preflight,
    MidTurn,
    PostToolResult,
    Manual,
}
```

```rust
pub enum AgentError {
    ContextReferenceNotFound { reference_id: String },
    ContextStoreUnavailable { reason: String },
    PolicyRejected { reason: String },
    // existing variants remain unchanged
}
```

```rust
pub enum AgentAction {
    CallModel(ModelRequest),
    ExecuteTools(Vec<ToolCallRecord>),
    RequestSummary(SummaryRequest),
    ManualCompactCompleted(ManualCompactResult),
    Finished,
    Failed(AgentError),
}
```

```rust
pub struct ContextPolicy {
    pub tier: ContextPolicyTier,
    pub summarization_allowed: bool,
    pub history_rewrite_allowed: bool,
    pub kv_rebuild_required_on_rewrite: bool,
    pub tool_schema_deferral_allowed: bool,
    pub file_backed_context_allowed: bool,
    pub manual_compact_allowed: bool,
    pub prompt_cache_hinting_allowed: bool,
    pub fifo_allowed_by_default: bool,
}
```

```rust
pub struct CompactionPlan {
    pub trigger: CompactionTrigger,
    pub threshold_ratio: f32,
    pub ordered_tiers: Vec<crate::events::CompactionTier>,
    pub allow_history_rewrite: bool,
    pub require_snapshot_before_destructive_drop: bool,
    pub max_prompt_tokens_after_plan: u32,
}
```

```rust
pub enum CompactionOutcome {
    Applied(AgentSession, CompactionEvent),
    NeedsHostCall(SummaryRequest, AgentSession),
    NotApplicable,
    Failed(AgentError),
}
```

```rust
pub enum ContextReferenceKind {
    ToolOutput,
    TerminalOutput,
    HistorySnapshot,
    ArtifactSnapshot,
    FileOutline,
    FileChunk,
    ToolSchema,
    ToolCatalogIndex,
}
```

```rust
pub struct ContextReference {
    pub id: String,
    pub kind: ContextReferenceKind,
    pub title: String,
    pub byte_len: u64,
    pub token_estimate: u32,
    pub preview: String,
    pub retrieval_hint: String,
}
```

```rust
pub struct ContextStoreEntry {
    pub reference: ContextReference,
    pub content: String,
    pub source_message_ids: Vec<String>,
    pub created_turn_index: u32,
    pub expires_after_turn: Option<u32>,
}
```

```rust
pub struct StateLedger {
    pub objective: Option<String>,
    pub constraints: Vec<String>,
    pub decisions: Vec<String>,
    pub open_questions: Vec<String>,
    pub touched_files: Vec<String>,
    pub tool_decisions: Vec<String>,
    pub artifact_states: Vec<ArtifactStateAnchor>,
    pub summary_refs: Vec<String>,
    pub history_refs: Vec<String>,
}
```

```rust
pub struct ArtifactStateAnchor {
    pub artifact_id: String,
    pub total_blocks: u32,
    pub last_block_preview: String,
    pub continuation_instruction: String,
}
```

```rust
pub struct ToolContextIndex {
    pub full_schema_ref: String,
    pub advertised_tools: Vec<ToolContextSummary>,
}
```

```rust
pub struct ToolContextSummary {
    pub name: String,
    pub description_preview: String,
    pub reproducible: bool,
    pub deferrable: bool,
    pub max_result_bytes: Option<usize>,
}
```

```rust
pub struct FileContextIndex {
    pub root_ref: String,
    pub included_paths: Vec<FileContextSummary>,
}
```

```rust
pub struct FileContextSummary {
    pub path: String,
    pub condensation_level: FileCondensationLevel,
    pub outline_ref: Option<String>,
    pub chunk_refs: Vec<String>,
}
```

```rust
pub enum FileCondensationLevel {
    Full,
    Outline,
    FilenameOnly,
    Excluded,
}
```

```rust
pub struct ManualCompactRequest {
    pub focus_instruction: Option<String>,
    pub force_summary: bool,
}
```

```rust
pub struct ManualCompactResult {
    pub summary_ref: Option<String>,
    pub history_ref: Option<String>,
    pub tokens_before: u32,
    pub tokens_after: u32,
}
```

New `AgentConfig` fields:

```rust
pub struct AgentConfig {
    pub threshold_ratio: f32,
    pub mid_turn_threshold_ratio: f32,
    pub post_tool_result_threshold_ratio: f32,
    pub manual_compact_threshold_ratio: f32,
    pub micro_keep_recent: u32,
    pub allow_fifo_on_preflight: bool,
    pub allow_fifo_on_mid_turn: bool,
    pub allow_fifo_on_post_tool_result: bool,
    pub require_snapshot_before_fifo: bool,
    pub enable_context_store: bool,
    pub enable_state_ledger: bool,
    pub enable_tool_schema_deferral: bool,
    pub enable_file_condensation: bool,
    pub enable_manual_compact: bool,
    pub tool_spill_threshold_bytes: usize,
    pub terminal_spill_threshold_bytes: usize,
    pub history_snapshot_threshold_tokens: u32,
    pub file_outline_threshold_tokens: u32,
    pub prompt_cache_anchor_threshold_tokens: u32,
}
```

New `SessionCapabilities` fields:

```rust
pub struct SessionCapabilities {
    pub summarization_allowed: bool,
    pub kv_fixed: bool,
    pub tool_result_budget_tokens: Option<u32>,
    pub context_policy_tier: ContextPolicyTier,
    pub tool_schema_deferral_supported: bool,
    pub file_backed_context_supported: bool,
    pub prompt_cache_supported: bool,
    pub history_rewrite_supported: bool,
}
```

New `SummaryRequest` fields:

```rust
pub struct SummaryRequest {
    pub messages_to_summarize: Vec<AgentMessage>,
    pub instruction: String,
    pub last_message_id: String,
    pub trigger: CompactionTrigger,
    pub focus_instruction: Option<String>,
    pub history_ref: Option<String>,
    pub state_ledger_snapshot: Option<StateLedger>,
}
```

New `ModelRequest` fields:

```rust
pub struct ModelRequest {
    pub session_messages: Vec<AgentMessage>,
    pub system_prompt: String,
    pub tools_schema_json: String,
    pub allow_tool_call: bool,
    pub context_references: Vec<ContextReference>,
    pub state_ledger: Option<StateLedger>,
    pub tool_context_index: Option<ToolContextIndex>,
    pub file_context_index: Option<FileContextIndex>,
}
```

New engine resume enum:

```rust
pub(super) enum PendingCompactionResume {
    PreflightModelCall,
    AfterModelResponse,
    AfterToolResults,
    ManualCompact,
}
```

Behavioral defaults:
- `threshold_ratio` remains `0.80`.
- `mid_turn_threshold_ratio` defaults to `0.90`.
- `post_tool_result_threshold_ratio` defaults to `0.85`.
- `manual_compact_threshold_ratio` defaults to `0.80`.
- `allow_fifo_on_preflight` defaults to `true`.
- `allow_fifo_on_mid_turn` defaults to `false`.
- `allow_fifo_on_post_tool_result` defaults to `false`.
- `require_snapshot_before_fifo` defaults to `true`.
- `enable_context_store`, `enable_state_ledger`, and `enable_manual_compact` default to `true`.
- `enable_tool_schema_deferral` and `enable_file_condensation` default to `true`, but the effective policy must also require the matching session capability.
- `tool_spill_threshold_bytes` defaults to the current cloud tool-result budget when no local token budget is present.
- `terminal_spill_threshold_bytes` defaults to `8192`.
- `history_snapshot_threshold_tokens` defaults to `1024`.
- `file_outline_threshold_tokens` defaults to `2048`.
- `prompt_cache_anchor_threshold_tokens` defaults to `4096`.
- `PendingCompactionResume` is internal only and must not cross the FFI boundary.
- `CompactionTrigger::Manual` is the only new trigger value and is used exclusively for user-requested compaction.
- `AgentAction::ManualCompactCompleted` is returned only by `request_manual_compact()` when no host summary round-trip is required.

## API Contracts & Interfaces

New and changed signatures:

```rust
pub fn resolve_context_policy(
    config: &AgentConfig,
    capabilities: &SessionCapabilities,
    provider: &str,
    context_window_tokens: u32,
) -> ContextPolicy;
```

```rust
pub fn should_compact(pressure: &Pressure, threshold_ratio: f32) -> bool;
```

```rust
pub fn threshold_ratio_for_trigger(
    config: &AgentConfig,
    trigger: CompactionTrigger,
) -> f32;
```

```rust
pub fn build_compaction_plan(
    config: &AgentConfig,
    capabilities: &SessionCapabilities,
    policy: &ContextPolicy,
    trigger: CompactionTrigger,
) -> CompactionPlan;
```

```rust
impl CompactionPipeline {
    pub fn run_plan(
        &self,
        session: AgentSession,
        ctx: &CompactionContext,
        config: &AgentConfig,
        policy: &ContextPolicy,
        plan: &CompactionPlan,
    ) -> CompactionOutcome;
}
```

```rust
impl ContextStore {
    pub fn insert_entry(&mut self, entry: ContextStoreEntry) -> ContextReference;
    pub fn get(&self, id: &str) -> Option<&ContextStoreEntry>;
    pub fn search(&self, query: &str, limit: usize) -> Vec<ContextReference>;
    pub fn expire_after_turn(&mut self, current_turn_index: u32) -> Vec<String>;
}
```

```rust
pub fn spill_tool_result_if_needed(
    store: &mut ContextStore,
    result: &mut ToolResultRecord,
    call: &ToolCallRecord,
    threshold_bytes: usize,
    turn_index: u32,
) -> Option<ContextReference>;
```

```rust
pub fn snapshot_history_range(
    store: &mut ContextStore,
    messages: &[AgentMessage],
    source: CompactionTrigger,
    turn_index: u32,
) -> Option<ContextReference>;
```

```rust
impl StateLedger {
    pub fn observe_user_message(&mut self, message: &AgentMessage);
    pub fn observe_assistant_message(&mut self, message: &AgentMessage);
    pub fn observe_tool_call(&mut self, call: &ToolCallRecord);
    pub fn observe_tool_result(&mut self, result: &ToolResultRecord);
    pub fn observe_artifact_anchor(&mut self, anchor: ArtifactStateAnchor);
    pub fn merge_summary(&mut self, summary_text: &str, summary_ref: Option<&str>);
    pub fn as_anchor_message(&self) -> Option<AgentMessage>;
}
```

```rust
pub fn build_tool_context_index(
    tools_schema_json: &str,
    catalog_json: &str,
    store: &mut ContextStore,
    config: &AgentConfig,
    capabilities: &SessionCapabilities,
) -> Option<ToolContextIndex>;
```

```rust
pub fn condense_file_context(
    included_files_json: &str,
    store: &mut ContextStore,
    config: &AgentConfig,
    capabilities: &SessionCapabilities,
) -> Option<FileContextIndex>;
```

```rust
impl AgentEngine<T, E, S> {
    pub fn request_manual_compact(
        &self,
        handle: &SessionHandle,
        request: ManualCompactRequest,
    ) -> AgentAction;

    pub fn read_context_reference(
        &self,
        handle: &SessionHandle,
        reference_id: String,
    ) -> Result<String, AgentError>;

    pub fn search_context_references(
        &self,
        handle: &SessionHandle,
        query: String,
        limit: usize,
    ) -> Vec<ContextReference>;
}
```

Contract deltas inside existing methods:
- `create_session()` must resolve `ContextPolicy`, initialize `ContextStore`, initialize `StateLedger` when enabled, build the initial `ToolContextIndex` when allowed, and record policy-selection events.
- `next_action()` must run `Preflight` compaction using `CompactionPlan` and include context references, state ledger anchors, tool-context index, and file-context index in `ModelRequest`.
- `submit_model_response()` must append the assistant message, update the state ledger, then run `MidTurn` compaction before creating pending tool execution or terminal completion.
- `submit_tool_results()` must spill oversized tool results before message insertion, update the state ledger, then run `PostToolResult` compaction before emitting `TurnCompleted`.
- `submit_summary()` must validate `last_message_id`, apply summary output, update the state ledger, emit `PostCompact` lifecycle events, and resume the interrupted continuation.
- `request_manual_compact()` must emit `PreCompact`, snapshot history before summary, request host summary when summary is allowed, and return either `RequestSummary` or `ManualCompactCompleted(ManualCompactResult)` depending on policy.
- `request_manual_compact()` must return `ManualCompactCompleted(ManualCompactResult)` with no summary or history refs when manual compact is disabled as an explicit no-op.
- `build_compaction_plan()` must use this tier order:
  - `Preflight`: `ToolSpill`, `Micro`, `ArtifactSquash`, `HistorySnapshot`, `Summary`, `Fifo`.
  - `MidTurn`: `ToolSpill`, `Micro`, `ArtifactSquash`, `HistorySnapshot`, `Summary`, then `Fifo` only when `allow_fifo_on_mid_turn = true`.
  - `PostToolResult`: `ToolSpill`, `Micro`, `ArtifactSquash`, `HistorySnapshot`, `Summary`, then `Fifo` only when `allow_fifo_on_post_tool_result = true`.
  - `Manual`: `HistorySnapshot`, `Summary`, then `Fifo` only when summary is unavailable and history rewrite is allowed.
- `build_compaction_plan()` must omit `Summary` when `summarization_allowed = false`, `kv_fixed = true`, or `policy.history_rewrite_allowed = false`.
- `build_compaction_plan()` must omit `Fifo` when the selected policy disallows destructive history deletion.
- `Fifo` must never run unless the target range has a `HistorySnapshot` reference when `require_snapshot_before_fifo = true`.
- `ToolSpill` and `HistorySnapshot` must be non-destructive when `kv_fixed = true`; they may replace payload content with references only if the backend can rebuild prompt state from the rewritten history, otherwise they must attach references for the next request without mutating prior messages.
- `ModelRequest.tools_schema_json` must carry either full schemas or a compact tool index depending on `ContextPolicy.tool_schema_deferral_allowed`.
- Prompt-cache support is advisory only. The Rust core must expose stable anchors and cache hints but must not assume a provider cache exists.

## Config / Permissions Delta

- Rust serde and UniFFI DTO changes are required in `session.rs`, `turn_io.rs`, `events.rs`, and `ffi/types.rs`.
- No Android manifest, Gradle, Room, or database schema changes are part of this Rust-core plan.
- `third_party/agent_core/Cargo.toml` requires a version bump because the UniFFI DTO shape and public engine methods change.
- Generated Kotlin/native binding artifacts are intentionally excluded from this spec because their checked-in paths were not verified in this planning pass.
- Context storage is in-memory inside `agent_core` for this plan. Host-backed persistent storage can be added later through the same reference interfaces without schema changes in this spec.

## Constitution Audit
This design is planning-only, keeps proposed changes inside verified `third_party/agent_core` paths plus Rust tests, does not modify `AGENTS.md`, does not modify files under `contracts/`, and does not introduce Android, Compose, Hilt, Room, WorkManager, or LiteRT types into domain-layer code.

## Source-Informed Design Mapping

| Source-observed behavior | `agent_core` planned capability |
|---|---|
| Claude Code `/compact` with focus instructions | `ManualCompactRequest.focus_instruction` and manual compaction lifecycle. |
| Claude Code `PreCompact` and `PostCompact` hooks | `AgentEvent::PreCompact` and `AgentEvent::PostCompact`. |
| Claude Code auto-compaction near high context pressure | Trigger-specific automatic compaction thresholds. |
| Claude Code subagents isolate verbose work | Context references and future session/subtask boundaries; this spec does not add subagent execution. |
| Claude Code MCP tool schema deferral | `ToolContextIndex` and deferred tool-schema references. |
| Claude Code oversized MCP output spillover | `ToolSpill` and `ContextStore` references. |
| Cursor automatic/manual summarization | Preflight and manual summary compaction. |
| Cursor chat-history files after summarization | `HistorySnapshot` references and search/read APIs. |
| Cursor file/folder smart condensation | `FileContextIndex`, `FileContextSummary`, and `FileCondensationLevel`. |
| Cursor terminal/tool output files | `ContextReferenceKind::TerminalOutput` and `ToolOutput`. |

## Subagent-Driven Execution

Downstream executors act as coordinators first and implementers second. For each Implementation Task below:

1. Spawn one child subagent scoped only to that task's files and scenario IDs.
2. Review the child output against this document before accepting it.
3. Repeat in order; do not merge unrelated tasks into one child run.

Scenario IDs are the unit of work. Each child subagent must satisfy only the `Verifies scenarios` IDs named on its task before the parent advances.

Per-child review checklist:
- Paths touched match the task `Files` list and the File Structure table.
- Every scenario ID named under `Verifies scenarios` has corresponding source or test coverage in the changed files.
- No task introduces a new symbol that is absent from `Data Models & Schemas` or `API Contracts & Interfaces`.
- No task edits `AGENTS.md`, anything under `contracts/`, or unverified generated binding paths.
- Any FFI-facing schema change is paired with matching Rust-side DTO and version-bump updates before the task is accepted.
- Any destructive history operation proves a history snapshot exists first when `require_snapshot_before_fifo = true`.

## Implementation Tasks

### Task 1: Add Policy Tier And Config Surface

Files:
- Modify: `third_party/agent_core/src/session.rs:7-117`
- Modify: `third_party/agent_core/src/ffi/types.rs:276-414`

Change:
Add `ContextPolicyTier`, trigger thresholds, context-store flags, state-ledger flags, schema-deferral flags, file-condensation flags, manual-compact flags, spill thresholds, FIFO gates, and matching FFI DTO fields with the defaults defined in this spec.

Verifies scenarios:
- `S1`
- `S6`
- `S20`
- `S21`
- `E3`
- `E8`

Commit message:

```text
feat(PLAN-agent-core-context-management): add context policy config surface
```

### Task 2: Resolve Runtime Context Policy

Files:
- Create: `third_party/agent_core/src/policy.rs`
- Modify: `third_party/agent_core/src/lib.rs:1-45`
- Modify: `third_party/agent_core/src/engine/state.rs:1-90`

Change:
Implement `resolve_context_policy()` and store the resolved policy on `InnerSession` so all compaction, summary, tool, and file-context decisions use one normalized policy object.

Verifies scenarios:
- `S20`
- `S21`
- `S22`
- `S23`
- `E8`

Commit message:

```text
feat(PLAN-agent-core-context-management): resolve context policy tiers
```

### Task 3: Introduce Trigger-Aware Compaction Planning

Files:
- Create: `third_party/agent_core/src/compact/policy.rs`
- Modify: `third_party/agent_core/src/budget.rs:1-269`
- Modify: `third_party/agent_core/src/compact/mod.rs:1-72`
- Modify: `third_party/agent_core/src/compact/pipeline.rs:6-260`

Change:
Create `CompactionPlan`, compute thresholds and ordered tiers per trigger, use policy-gated tier inclusion, and make pipeline execution stop against the plan-specific threshold.

Verifies scenarios:
- `S1`
- `S2`
- `S5`
- `S6`
- `E1`
- `E3`
- `M1`

Commit message:

```text
feat(PLAN-agent-core-context-management): add trigger-aware compaction planner
```

### Task 4: Reorder Semantic Compaction Before FIFO

Files:
- Modify: `third_party/agent_core/src/compact/policy.rs`
- Modify: `third_party/agent_core/src/compact/pipeline.rs:6-260`
- Modify: `third_party/agent_core/src/compact/fifo.rs:1-340`

Change:
Ensure `ArtifactSquash`, `HistorySnapshot`, and `Summary` precede `Fifo`, and gate `Fifo` behind policy plus trigger-specific config so destructive deletion is never the first semantic response to pressure.

Verifies scenarios:
- `S5`
- `S16`
- `E3`
- `M2`

Commit message:

```text
feat(PLAN-agent-core-context-management): prefer semantic compaction before fifo
```

### Task 5: Add Summary Trigger Metadata And Quality Contract

Files:
- Modify: `third_party/agent_core/src/turn_io.rs:1-170`
- Modify: `third_party/agent_core/src/compact/summary.rs:1-360`
- Modify: `third_party/agent_core/src/ffi/types.rs:1-520`

Change:
Add `trigger`, `focus_instruction`, `history_ref`, and `state_ledger_snapshot` to `SummaryRequest`, and update `SUMMARY_INSTRUCTION` so summaries must preserve objective, constraints, decisions, tool decisions, artifact state, touched files, and open questions while omitting raw tool output.

Verifies scenarios:
- `S2`
- `S3`
- `S7`
- `S18`
- `E1`
- `E2`

Commit message:

```text
feat(PLAN-agent-core-context-management): extend summary request contract
```

### Task 6: Add Resumable Summary Continuations

Files:
- Modify: `third_party/agent_core/src/engine/state.rs:1-90`
- Modify: `third_party/agent_core/src/engine/mod.rs:1-1140`

Change:
Record whether host summary interrupted preflight, model-response handling, tool-result handling, or manual compact, and resume the exact pending continuation after `submit_summary()` succeeds.

Verifies scenarios:
- `S3`
- `S4`
- `S18`
- `E2`
- `E3`
- `M1`

Commit message:

```text
feat(PLAN-agent-core-context-management): resume interrupted compaction continuations
```

### Task 7: Wire Automatic Mid-Turn And Post-Tool Checkpoints

Files:
- Modify: `third_party/agent_core/src/engine/mod.rs:136-397,437-535`
- Modify: `third_party/agent_core/src/events.rs:1-260`

Change:
Invoke `MidTurn` after assistant response insertion and before tool execution decisions, and invoke `PostToolResult` after tool-result insertion and before the next model preflight.

Verifies scenarios:
- `S3`
- `S4`
- `M1`

Commit message:

```text
feat(PLAN-agent-core-context-management): run compaction across loop checkpoints
```

### Task 8: Preserve Fixed-KV Synchronous Guarantees

Files:
- Modify: `third_party/agent_core/src/policy.rs`
- Modify: `third_party/agent_core/src/compact/policy.rs`
- Modify: `third_party/agent_core/src/engine/mod.rs:357-397,447-535`
- Modify: `third_party/agent_core/tests/kv_fixed_test.rs:1-120`

Change:
Omit host summary and history-rewrite tiers for `FixedKv` policy unless a synchronous reference-only strategy can run without rewriting prior KV state; preserve `KvCacheInvalidated` for allowed sync rewrites.

Verifies scenarios:
- `S6`
- `S21`
- `E8`

Commit message:

```text
feat(PLAN-agent-core-context-management): preserve fixed-kv compaction limits
```

### Task 9: Implement In-Memory Context Store

Files:
- Create: `third_party/agent_core/src/context_store.rs`
- Modify: `third_party/agent_core/src/session.rs:7-117`
- Modify: `third_party/agent_core/src/lib.rs:1-45`

Change:
Add `ContextStore`, `ContextReference`, and `ContextStoreEntry` with deterministic reference IDs, insert/get/search/expiry operations, source message tracking, and preview generation.

Verifies scenarios:
- `S8`
- `S9`
- `S10`
- `E4`

Commit message:

```text
feat(PLAN-agent-core-context-management): add context store references
```

### Task 10: Spill Oversized Tool Results

Files:
- Create: `third_party/agent_core/src/compact/tool_spill.rs`
- Modify: `third_party/agent_core/src/compact/mod.rs:1-72`
- Modify: `third_party/agent_core/src/engine/mod.rs:240-356`
- Modify: `third_party/agent_core/src/message.rs:1-90`

Change:
Persist oversized tool outputs in `ContextStore`, replace the prompt-facing content with a compact reference and preview, and mark metadata so micro compaction never erases the only recoverable copy.

Verifies scenarios:
- `S8`
- `S9`
- `E4`
- `M3`

Commit message:

```text
feat(PLAN-agent-core-context-management): spill large tool outputs to references
```

### Task 11: Add Context Reference Read And Search APIs

Files:
- Modify: `third_party/agent_core/src/engine/mod.rs:1-1140`
- Modify: `third_party/agent_core/src/ffi/mod.rs:1-105`
- Modify: `third_party/agent_core/src/ffi/types.rs:1-520`
- Modify: `third_party/agent_core/src/host.rs:1-120`

Change:
Expose read and search operations for context references through Rust and FFI so hosts and future model tools can recover raw history, tool output, terminal output, artifact snapshots, and file chunks.

Verifies scenarios:
- `S9`
- `S10`
- `S13`
- `E4`

Commit message:

```text
feat(PLAN-agent-core-context-management): expose context reference retrieval
```

### Task 12: Snapshot History Before Summary And FIFO

Files:
- Create: `third_party/agent_core/src/compact/history_snapshot.rs`
- Modify: `third_party/agent_core/src/compact/summary.rs:1-360`
- Modify: `third_party/agent_core/src/compact/fifo.rs:1-340`
- Modify: `third_party/agent_core/src/compact/policy.rs`

Change:
Persist compacted-away transcript ranges into `ContextStore` before summary or FIFO removes them, attach the resulting `history_ref` to summary requests/events, and fail destructive FIFO when snapshots are required but unavailable.

Verifies scenarios:
- `S10`
- `S16`
- `E5`
- `M2`

Commit message:

```text
feat(PLAN-agent-core-context-management): snapshot history before destructive compaction
```

### Task 13: Implement State Ledger

Files:
- Create: `third_party/agent_core/src/state_ledger.rs`
- Modify: `third_party/agent_core/src/session.rs:7-117`
- Modify: `third_party/agent_core/src/engine/state.rs:1-90`
- Modify: `third_party/agent_core/src/lib.rs:1-45`

Change:
Add `StateLedger` and update session state so objective, constraints, decisions, open questions, touched files, tool decisions, artifact states, summary refs, and history refs survive compaction boundaries.

Verifies scenarios:
- `S11`
- `S12`
- `S15`
- `S19`
- `M4`

Commit message:

```text
feat(PLAN-agent-core-context-management): add semantic state ledger
```

### Task 14: Feed State Ledger From User, Assistant, And Tool Events

Files:
- Modify: `third_party/agent_core/src/engine/mod.rs:120-356`
- Modify: `third_party/agent_core/src/state_ledger.rs`
- Modify: `third_party/agent_core/src/events.rs:1-260`

Change:
Update the ledger after user-visible assistant text, tool calls, tool results, summaries, and artifacts; emit state-ledger update events with counts instead of sensitive content.

Verifies scenarios:
- `S11`
- `S12`
- `S15`
- `M4`

Commit message:

```text
feat(PLAN-agent-core-context-management): maintain state ledger during turns
```

### Task 15: Inject State Ledger Into Model Requests

Files:
- Modify: `third_party/agent_core/src/turn_io.rs:1-170`
- Modify: `third_party/agent_core/src/engine/mod.rs:500-535`
- Modify: `third_party/agent_core/src/ffi/types.rs:1-520`

Change:
Include compact state-ledger anchors in `ModelRequest` so semantic continuity does not depend only on raw history or lossy summaries.

Verifies scenarios:
- `S12`
- `S19`
- `M4`

Commit message:

```text
feat(PLAN-agent-core-context-management): include state anchors in model requests
```

### Task 16: Persist Artifact State During Squash

Files:
- Modify: `third_party/agent_core/src/compact/artifact_squash.rs:1-380`
- Modify: `third_party/agent_core/src/state_ledger.rs`
- Modify: `third_party/agent_core/src/context_store.rs`

Change:
When `ArtifactSquash` runs, store a retrievable artifact snapshot and update `StateLedger.artifact_states` with artifact ID, total blocks, last block preview, and continuation instruction.

Verifies scenarios:
- `S5`
- `S15`
- `M5`

Commit message:

```text
feat(PLAN-agent-core-context-management): preserve artifact state across squash
```

### Task 17: Implement Tool Schema Deferral

Files:
- Create: `third_party/agent_core/src/tool_context.rs`
- Modify: `third_party/agent_core/resources/tool_catalog.json`
- Modify: `third_party/agent_core/src/turn_io.rs:1-170`
- Modify: `third_party/agent_core/src/engine/mod.rs:1-1140`

Change:
Store full tool schemas behind references, build a compact `ToolContextIndex`, and include full schemas only when policy or capabilities disallow deferral.

Verifies scenarios:
- `S13`
- `S14`
- `E6`
- `M6`

Commit message:

```text
feat(PLAN-agent-core-context-management): defer tool schema context
```

### Task 18: Add Tool Metadata Search And Loading Contracts

Files:
- Modify: `third_party/agent_core/src/tool_context.rs`
- Modify: `third_party/agent_core/src/host.rs:1-120`
- Modify: `third_party/agent_core/src/ffi/types.rs:1-520`
- Modify: `third_party/agent_core/src/ffi/mod.rs:1-105`

Change:
Expose search/load contracts for deferred tool schemas so a host or future model tool can retrieve full metadata when a compact index is insufficient.

Verifies scenarios:
- `S14`
- `E6`

Commit message:

```text
feat(PLAN-agent-core-context-management): expose deferred tool metadata retrieval
```

### Task 19: Implement File And Folder Condensation

Files:
- Create: `third_party/agent_core/src/file_context.rs`
- Modify: `third_party/agent_core/src/turn_io.rs:1-170`
- Modify: `third_party/agent_core/src/engine/mod.rs:1-1140`
- Modify: `third_party/agent_core/src/lib.rs:1-45`

Change:
Represent included files/folders as full content, outline, filename-only, or excluded references based on token size and policy; store outlines and chunks in `ContextStore`.

Verifies scenarios:
- `S17`
- `S18`
- `E7`
- `M7`

Commit message:

```text
feat(PLAN-agent-core-context-management): condense file and folder context
```

### Task 20: Include File Context Index In Model Requests

Files:
- Modify: `third_party/agent_core/src/turn_io.rs:1-170`
- Modify: `third_party/agent_core/src/engine/mod.rs:500-535`
- Modify: `third_party/agent_core/src/ffi/types.rs:1-520`

Change:
Attach `FileContextIndex` to `ModelRequest` so condensed files remain discoverable without loading every token into the static prompt.

Verifies scenarios:
- `S17`
- `S18`
- `M7`

Commit message:

```text
feat(PLAN-agent-core-context-management): attach file context index to requests
```

### Task 21: Add Manual Compact Request Flow

Files:
- Create: `third_party/agent_core/src/manual_compact.rs`
- Modify: `third_party/agent_core/src/engine/mod.rs:1-1140`
- Modify: `third_party/agent_core/src/action.rs:1-120`
- Modify: `third_party/agent_core/src/turn_io.rs:1-170`

Change:
Add `request_manual_compact()` support with optional focus instruction, snapshot-before-summary behavior, and synchronous no-op completion when manual compaction is disabled by policy.

Verifies scenarios:
- `S18`
- `S24`
- `E9`

Commit message:

```text
feat(PLAN-agent-core-context-management): add manual compact flow
```

### Task 22: Add PreCompact And PostCompact Events

Files:
- Modify: `third_party/agent_core/src/events.rs:1-260`
- Modify: `third_party/agent_core/src/engine/mod.rs:1-1140`
- Modify: `third_party/agent_core/src/ffi/types.rs:1-520`

Change:
Emit `PreCompact` before manual or automatic compaction and `PostCompact` after summary/synchronous compaction completes, including trigger, summary ref, history ref, and token counts without raw message content.

Verifies scenarios:
- `S18`
- `S24`
- `E9`

Commit message:

```text
feat(PLAN-agent-core-context-management): emit compaction lifecycle events
```

### Task 23: Add Prompt Cache Anchor Hints

Files:
- Modify: `third_party/agent_core/src/budget.rs:1-269`
- Modify: `third_party/agent_core/src/turn_io.rs:1-170`
- Modify: `third_party/agent_core/src/policy.rs`

Change:
Expose stable prompt-cache anchor hints for large stable system/tool/state segments when `prompt_cache_supported = true`; keep the feature advisory and provider-neutral.

Verifies scenarios:
- `S22`
- `E10`

Commit message:

```text
feat(PLAN-agent-core-context-management): add prompt cache anchor hints
```

### Task 24: Add Terminal Output Reference Kind

Files:
- Modify: `third_party/agent_core/src/context_store.rs`
- Modify: `third_party/agent_core/src/events.rs:1-260`
- Modify: `third_party/agent_core/src/ffi/types.rs:1-520`

Change:
Support `TerminalOutput` context references so host-owned terminal transcripts can be represented the same way as large tool outputs even when the Rust core did not execute the terminal command.

Verifies scenarios:
- `S23`
- `E4`

Commit message:

```text
feat(PLAN-agent-core-context-management): support terminal output references
```

### Task 25: Harden Micro Compaction With Reference Awareness

Files:
- Modify: `third_party/agent_core/src/compact/micro.rs:1-260`
- Modify: `third_party/agent_core/src/context_store.rs`

Change:
Only clear old reproducible tool outputs when the content is either reproducible by catalog contract or recoverable through a context reference; keep artifact and unrecoverable outputs intact.

Verifies scenarios:
- `S8`
- `E4`
- `M3`

Commit message:

```text
feat(PLAN-agent-core-context-management): make micro compaction reference aware
```

### Task 26: Extend Observability Events

Files:
- Modify: `third_party/agent_core/src/events.rs:1-260`
- Modify: `third_party/agent_core/tests/snapshots/fixtures__events_compaction_under_pressure.snap`

Change:
Add safe-to-log events for policy selection, context pressure, skipped tiers, context-store writes, state-ledger updates, tool-schema deferral, file condensation, pre-compact, and post-compact.

Verifies scenarios:
- `S20`
- `S24`
- `E10`

Commit message:

```text
feat(PLAN-agent-core-context-management): expand context observability
```

### Task 27: Add FFI Methods For Manual Compact And Context References

Files:
- Modify: `third_party/agent_core/src/ffi/mod.rs:1-105`
- Modify: `third_party/agent_core/src/ffi/types.rs:1-520`
- Modify: `third_party/agent_core/src/lib.rs:1-45`

Change:
Export `request_manual_compact`, `read_context_reference`, and `search_context_references` plus DTOs for manual compact requests, context references, policy tiers, file context, tool context, and state ledger.

Verifies scenarios:
- `S9`
- `S10`
- `S13`
- `S17`
- `S18`

Commit message:

```text
feat(PLAN-agent-core-context-management): expose context management ffi
```

### Task 28: Add Unit Coverage For Config, Policy, And Planner

Files:
- Modify: `third_party/agent_core/src/session.rs:119-237`
- Modify: `third_party/agent_core/src/budget.rs`
- Modify: `third_party/agent_core/src/policy.rs`
- Modify: `third_party/agent_core/src/compact/policy.rs`
- Modify: `third_party/agent_core/src/compact/pipeline.rs:76-260`
- Modify: `third_party/agent_core/src/ffi/types.rs:1-520`

Change:
Add focused unit tests for defaults, policy resolution, tier order, threshold selection, fixed-KV omissions, and FFI round-trips.

Verifies scenarios:
- `S1`
- `S2`
- `S5`
- `S6`
- `S20`
- `S21`
- `S22`
- `E1`
- `E3`
- `E8`

Commit message:

```text
test(PLAN-agent-core-context-management): cover policy and planner units
```

### Task 29: Add Context Store And Ledger Tests

Files:
- Create: `third_party/agent_core/tests/context_store_test.rs`
- Create: `third_party/agent_core/tests/state_ledger_test.rs`
- Modify: `third_party/agent_core/tests/fixtures.rs:1-220`

Change:
Add integration tests for reference insertion/search/read/expiry, tool spillover, history snapshots, state-ledger merge behavior, and model-request state anchors.

Verifies scenarios:
- `S8`
- `S9`
- `S10`
- `S11`
- `S12`
- `E4`
- `E5`
- `M3`
- `M4`

Commit message:

```text
test(PLAN-agent-core-context-management): cover context store and state ledger
```

### Task 30: Add Tool And File Context Tests

Files:
- Create: `third_party/agent_core/tests/tool_context_test.rs`
- Create: `third_party/agent_core/tests/file_context_test.rs`
- Modify: `third_party/agent_core/tests/fixtures.rs:1-220`

Change:
Add tests for deferred tool schemas, metadata retrieval, disabled deferral fallback, file outline condensation, filename-only condensation, and excluded oversized files.

Verifies scenarios:
- `S13`
- `S14`
- `S17`
- `E6`
- `E7`
- `M6`
- `M7`

Commit message:

```text
test(PLAN-agent-core-context-management): cover tool and file context
```

### Task 31: Add Engine Trigger And Manual Compact Tests

Files:
- Create: `third_party/agent_core/tests/compaction_triggers_test.rs`
- Create: `third_party/agent_core/tests/manual_compact_test.rs`
- Modify: `third_party/agent_core/tests/kv_fixed_test.rs:1-120`

Change:
Add engine-level tests proving mid-turn/post-tool compaction, summary resume, manual compact focus instructions, lifecycle events, and fixed-KV limits.

Verifies scenarios:
- `S3`
- `S4`
- `S6`
- `S18`
- `S21`
- `S24`
- `E2`
- `E8`
- `E9`
- `M1`

Commit message:

```text
test(PLAN-agent-core-context-management): cover engine context triggers
```

### Task 32: Add End-To-End Context Preservation Test

Files:
- Create: `third_party/agent_core/tests/context_management_end_to_end_test.rs`
- Modify: `third_party/agent_core/tests/fixtures.rs:1-220`

Change:
Add a mixed scenario with large tool output, artifact append loop, summary pressure, file condensation, and follow-up request; prove the final model request retains state-ledger anchors and retrievable references while staying below threshold.

Verifies scenarios:
- `S19`
- `S23`
- `S24`
- `M5`

Commit message:

```text
test(PLAN-agent-core-context-management): add end to end preservation coverage
```

### Task 33: Bump Crate Version For Public Contract Changes

Files:
- Modify: `third_party/agent_core/Cargo.toml:1-26`

Change:
Increase the `agent_core` crate version after DTOs, events, actions, request types, and public engine methods change.

Verifies scenarios:
- `S2`
- `S9`
- `S18`
- `S20`

Commit message:

```text
chore(PLAN-agent-core-context-management): bump agent_core version for context api changes
```
