# ADR: Rust `agent_core` — Agent Loop Unification

**Status:** Accepted  
**Approver:** @sheen.brown  
**Date:** 2026-05-15

**Status amendment (2026-05-15):** AGENT-030/AGENT-036 feature-flag rollout was cut; this is a single-shot migration. The Summary tier is executed through Kotlin `RollingSummarizer` on the FAST model via `RustLoopInferenceService.summarize`. LiteRT keeps the native SDK loop and uses the Rust policy bridge through the domain `AgentPolicyPort`, preserving AGENT-026 layering while sharing repair/truncation/guardrail policy.

---

## Context

`LlmToolingOrchestrator.kt` is duplicated across every inference provider (OpenAI, Anthropic, Google, Llama, LiteRT). Each provider has its own `onContextExceeded` lambda, its own compaction logic, and its own tool-result truncation. Bugs and improvements must be ported to each independently. A Rust desktop application will need the same loop — a Kotlin implementation is not shareable.

---

## Decision

Move the entire agent tool loop into a shared Rust crate (`third_party/agent_core`). Expose it to Android Kotlin via UniFFI (same pattern as `docgen_core` → `:core:docgen`). Design around native Rust host traits so a desktop app can consume the same crate directly without UniFFI.

---

## Architecture

### Host / engine split

| Layer | Responsibility | Lives in |
|-------|---------------|----------|
| **Engine** (`agent_core`) | Turn state machine, compaction, repair, guardrails, token ledger | Rust crate |
| **Transport** | One provider inference pass (mapping `ModelRequest` → `ModelResponse`) | Kotlin (`:feature:inference`) |
| **Tool executor** | Execute repaired tool calls via `CompositeToolExecutor` | Kotlin (`:core:data`) |
| **Summarizer** | Invoke `RollingSummarizer` for LLM summary compaction tier | Kotlin (`:feature:chat-inference-service`) |

### State machine API

```
create_session(initial_messages, config, capabilities, session_context)
  → SessionHandle

loop:
  next_action(handle) → AgentAction
  match action:
    CallModel(request)       → host calls transport → submit_model_response(handle, response)
    ExecuteTools(calls)      → host calls executor  → submit_tool_results(handle, results)
    RequestSummary(request)  → host calls summarizer → submit_summary(handle, result)
    Finished | Failed        → done

drain_events(handle) → Vec<AgentEvent>   // call after each submit_*
cancel(handle)
```

### Native Rust host traits (defined in `src/host.rs`, AGENT-046)

```rust
trait AgentTransport: Send + Sync { fn complete_model_turn(&self, req: ModelRequest) -> Result<ModelResponse, AgentError>; }
trait AgentToolExecutor: Send + Sync { fn execute_tools(&self, calls: Vec<ToolCallRecord>) -> Result<Vec<ToolResultRecord>, AgentError>; }
trait AgentSummarizer: Send + Sync { fn summarize(&self, req: SummaryRequest) -> Result<SummaryResult, AgentError>; }
```

Android uses a `UniFfiHostAdapter` shim (AGENT-022) that implements these via UniFFI callback interfaces.  
Desktop uses direct native implementations (no UniFFI layer).

---

## Locked constants

| Decision | Value | Replaces |
|----------|-------|---------|
| Context threshold | `threshold_ratio = 0.80` | Legacy 0.75 (OpenAI) / 0.85 (Anthropic) |
| Fuzzy repair threshold | Levenshtein normalized ≥ 0.85 | No fuzzy match existed |
| Cloud tool result budget | 4 096 bytes/result | `NativeToolResultFormatter` per-provider values |
| Local tool result budget | `SessionCapabilities.tool_result_budget_tokens * ~4 bytes` | `LocalToolResultPolicy` |
| JNI churn budget | < 5 ms/turn p50 overhead (AGENT-045 verifies) | N/A |
| Max compaction log | 32 entries (ring buffer) | N/A |
| Micro-compact keep-recent | 2 results per tool type (configurable) | N/A |
| Artifact snippet length | 200 chars (last appended block content) | N/A |

---

## Compaction pipeline

**Tier order:** Micro (1) → FIFO (2) → ArtifactSquash (3) → Summary (4)

**Stop policy:** run tiers in order; stop after first tier that returns `Applied` **and** brings `Pressure.ratio < 0.80`. If `Applied` but still over budget, continue to next tier. `NeedsHostCall` (Summary) short-circuits to engine immediately.

**Compaction outcome enum:**

```rust
enum CompactionOutcome {
    Applied(AgentSession, CompactionEvent),
    NeedsHostCall(SummaryRequest),
    NotApplicable,
}
```

Sync tiers (Micro, FIFO, ArtifactSquash) return `Applied` or `NotApplicable` synchronously.  
Summary tier returns `NeedsHostCall` → engine yields `AgentAction::RequestSummary` → host calls `submit_summary` → engine resumes pipeline.

**Behavior change from legacy:** no path throws `IllegalStateException` for context pressure. Engine compacts first, then continues the loop.

### `kv_fixed = true` (Llama, LiteRT)

KV caches invalidate on history rewrite. **Policy:** compaction is allowed; transport accepts the KV cache reset cost (preferred over warn-then-crash).

- Engine emits `AgentEvent::KvCacheInvalidated { reason }` when any compaction occurs on a `kv_fixed` session
- Transport adapter drops the `LlamaSession` / LiteRT context and rebuilds the prompt from compacted history
- Summary tier (Tier 4) returns `NotApplicable` for `kv_fixed` sessions (round-trip too costly)
- Perf cost accepted and documented in AGENT-033/034 MRs

---

## Tool repair pipeline

Steps (in order):
1. Trim whitespace
2. Strip prefixes (`functions.`, `tools.`)
3. Normalize to `snake_case`
4. Exact catalog lookup
5. Static alias lookup (`websearch` → `web_search`, `x_search` → `search_x_recent`, etc.)
6. Fuzzy match via `strsim::normalized_levenshtein` — accept ≥ 0.85, reject below

Argument JSON repair: strip prose/code-fence wrapping, parse JSON, inject schema hint defaults for missing required fields, coerce obvious type mismatches.

---

## Guardrails

| Policy | Value |
|--------|-------|
| Max turns | `AgentConfig.max_turns` |
| Greedy `append_blocks` extension | `max_turns + 20` when `greedy_append_blocks = true` and last turn had `append_blocks` |
| Soft-failure streak | 3 consecutive all-soft turns → `allow_tool_call = false` |
| Streak reset | on hard exception or any non-soft result |

**Behavior change from legacy:** recursion/context-full no longer throws. Engine returns `AgentAction::Failed(GuardrailExceeded)` as a terminal action instead of raising `IllegalStateException`.

---

## UniFFI + Android

Session handle: UniFFI **object reference** (`Arc<SessionState>`) — Drop semantics handled by Rust, no manual `u64` id in Kotlin.

`AgentException` mapping:

| `AgentError` | `AgentException` |
|---|---|
| `RepairFailed` | `AgentException.RepairFailed(name, reason)` |
| `GuardrailExceeded` | `AgentException.GuardrailExceeded(kind)` |
| `CompactionFailed` | `AgentException.CompactionFailed(tier, reason)` |
| `InvalidTransition` | `AgentException.InvalidTransition(from, to)` |
| `UnsupportedTool`, `Internal` | `AgentException.Internal(message)` |

UniFFI callbacks run on caller thread — Kotlin must use `Dispatchers.IO`, never Main.

---

## Migration strategy

1. **AGENT-030:** Feature flag `UseRustAgentLoop` (DataStore, `core/data`), default off
2. **AGENT-031–034:** Migrate each provider behind the flag
3. **AGENT-036:** Flip default on; remove routing branch
4. **AGENT-043:** Delete `LlmToolingOrchestrator.kt`, dead lambdas, `LocalToolResultPolicy` overlap
5. `ToolEnvelopeParser.kt` **retained** for v1 Llama envelope parsing

## Deprecation list

Files targeted for removal after cutover (AGENT-043):

- `core/domain/.../usecase/inference/LlmToolingOrchestrator.kt`
- `core/domain/.../model/inference/ContextExceededResult` (data class in same file)
- Dead `onContextExceeded` lambdas in all `*InferenceServiceImpl.kt` files
- `ArtifactContextCompressor` usage in `BaseOpenAiSdkInferenceService.onContextExceeded`
- Overlapping `LocalToolResultPolicy` (cloud path only; LiteRT shim retained for AGENT-034)

---

## Desktop framing

`agent_core` is **desktop-ready**: native Rust host traits require no UniFFI for direct consumers. Desktop app integration deferred — no desktop application exists in this repo today. When it does, add `agent_core = { path = "../../third_party/agent_core" }` and implement `AgentTransport`, `AgentToolExecutor`, `AgentSummarizer` in Rust.

---

## Deferred

- **CREW pipeline:** CREW is a Mixture-of-Agents chat mode, not a tool-calling loop. Out of v1 scope. Future CREW tool calls will gain repair via shared catalog automatically.
- **Compaction boundary persistence (AGENT-040):** UI "memory was compressed" indicator — deferred to v1.1 if desired.
- **LiteRT artifact auto-finalize (AGENT-034):** LiteRT native loop retains auto-calling; auto-finalize parity deferred post-release.

---

## One-turn sequence diagram

```
Host (Kotlin)                    AgentEngine (Rust)
     |                                  |
     |-- create_session(msgs, cfg) ---> |
     |                                  |-- estimate pressure
     |                                  |-- if >= 0.80: run CompactionPipeline
     |                                  |     Micro -> FIFO -> ArtifactSquash -> (Summary if needed)
     |                                  |-- if kv_fixed + compacted: emit KvCacheInvalidated
     |                                  |-- apply guardrails -> set allow_tool_call
     |<-- AgentAction::CallModel ------|
     |-- transport.complete_model_turn()|
     |-- submit_model_response ------> |
     |                                  |-- repair pipeline (normalize, fuzzy, arg-fix)
     |<-- AgentAction::ExecuteTools ---|
     |-- executor.execute_tools()      |
     |-- submit_tool_results --------> |
     |                                  |-- truncate results to budget
     |                                  |-- append to session, update ledger
     |                                  |-- check auto-finalize (pending_artifact_ids)
     |<-- AgentAction::CallModel ------|  (loop continues)
     ...
     |<-- AgentAction::Finished -------|
     |-- drain_events() ------------> |  CompactionApplied, ToolRepaired, etc.
```
