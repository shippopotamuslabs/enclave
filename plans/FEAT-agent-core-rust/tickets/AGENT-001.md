# AGENT-001 — ADR: Rust agent harness & host split

| Field | Value |
|-------|--------|
| **Type** | Design |
| **Epic** | E0 — Planning & repository |
| **Depends on** | — |
| **Blocks** | AGENT-002, all implementation tickets |

## Description

Write `plans/FEAT-agent-core-rust/ADR.md` documenting:

- Why Rust + UniFFI (shared with desktop, no drift vs duplicating in Kotlin)
- State-machine API: `create_session` → `next_action` → host fulfills → `submit_*` → terminal
- Host responsibilities: `AgentTransport` (model I/O), `AgentToolExecutor` (tools), `AgentSummarizer` (LLM summary tier)
- Native Rust host traits (defined in AGENT-046) + UniFFI callback shim (AGENT-022); desktop consumes native traits directly without UniFFI
- What stays in Kotlin: `:feature:inference` provider mappers, streaming, LiteRT JNI
- LiteRT constraint: `kv_fixed` capability → KV cache reset accepted on compaction; transport must rebuild prompt on `AgentEvent::KvCacheInvalidated` (AGENT-047)
- Migration: feature flag, strangle `LlmToolingOrchestrator`
- Compaction tier ordering: Micro (1) → FIFO (2) → ArtifactSquash (3) → Summary (4). Pipeline runs in order; first tier that relieves pressure wins. Summary tier is action-driven (returns `NeedsHostCall`) and runs last because it is the most expensive.
- **Locked:** `threshold_ratio = 0.80` (single value replacing legacy 0.75 / 0.85 fragmentation)
- **Locked:** `CompactionOutcome::{Applied(session, event), NeedsHostCall(SummaryRequest), NotApplicable}` — sync tiers return `Applied` or `NotApplicable`; summary tier returns `NeedsHostCall`; engine translates to `AgentAction::RequestSummary` and resumes pipeline on `submit_summary`
- **Locked:** tool result truncation budgets: cloud providers 4 KB/result; local models read from `SessionCapabilities.tool_result_budget_tokens`
- **Locked:** JNI churn budget: `< 5 ms/turn` p50 JNI overhead (verified by AGENT-045 benchmark)
- Desktop framing: `agent_core` exposes native Rust trait surface; desktop app will consume directly (no UniFFI); desktop integration deferred — crate is "desktop-ready" by design
- Artifact semantics appendix: `start_artifact` returns `{ status, artifactId }`; `append_blocks { artifactId, blocks, isComplete: true/false }`; engine tracks `pending_artifact_ids` and auto-finalizes on terminal (AGENT-016)

Include sequence diagram for one full tool turn: pressure check → compact → model → repair → execute → append → model.

List Kotlin files targeted for deprecation at cutover: `LlmToolingOrchestrator.kt`, `ContextExceededResult`, duplicated `onContextExceeded` lambdas in inference services, `LocalToolResultPolicy` (overlapping logic), `ArtifactContextCompressor` usage in `BaseOpenAiSdkInferenceService`.

## Acceptance criteria

- [ ] ADR committed under `plans/FEAT-agent-core-rust/ADR.md`
- [ ] Linked from `plans/FEAT-agent-core-rust/README.md`
- [ ] Threshold 0.80, compaction outcome enum, dual trait surface, JNI budget all locked (not "recommended")
- [ ] Deprecation list explicit
- [ ] Approver: @sheen.brown (user — no external sign-off required)

## Files

- `plans/FEAT-agent-core-rust/ADR.md` (create)
