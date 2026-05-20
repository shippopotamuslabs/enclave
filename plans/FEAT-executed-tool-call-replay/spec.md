# Spec: Executed Tool Call Replay (Provider-Facing Metadata)

**Ticket ID:** `FEAT-executed-tool-call-replay`  
**Date:** 2026-05-20  
**Contracts read:** `AGENTS.md`, `ARCHITECTURE_RULES.md`, `CODE_STYLE_RULES.md`, `DATA_LAYER_RULES.md` (no schema change), `UNIT_TESTING.md`, `INFERENCE_RULES.md`

---

## 1. Feature Summary

### Problem

`AgentEngine::submit_model_response` in `third_party/agent_core/src/engine/mod.rs` persists assistant message metadata `raw_tool_calls` from **`merged_tool_calls`** (pre-repair model output) **before** `ToolRepairPipeline::repair` runs. Execution uses repaired `ToolCallRecord` values, but BYOK cloud replay reads `raw_tool_calls` verbatim:

| Layer | Reads | Gets |
|-------|--------|------|
| Rust `ExecuteTools` | `ToolCallRecord` after repair | Canonical `name` + `arguments_json` |
| Kotlin `BaseOpenAiSdkInferenceService.toOpenAiChatParam()` | `metadata.raw_tool_calls` | Unrepaired model output |

On the next `CallModel`, OpenAI-compatible providers receive assistant tool-call messages with invalid names or non-JSON argument strings (markdown fences, wrong keys). Providers respond with HTTP 400 (`Invalid tool call in messages`). `RustAgentEngineAdapter` surfaces `AgentLoopEvent.Failed("model turn failed: …")` and the loop terminates instead of letting the model recover from tool error messages.

### Design target

| # | Requirement |
|---|-------------|
| 1 | After repair classification, **`raw_tool_calls` must contain only tool calls that reached `RepairOutcome::Repaired`**, using post-repair canonical `name` and `arguments_json`. |
| 2 | **`RepairOutcome::Reject` and `RepairOutcome::Suggest` must not appear** in `raw_tool_calls` (no broken provider-facing args). |
| 3 | Reject-only turns must persist **`raw_tool_calls` as an empty JSON array `[]`**, not the pre-repair merged list. |
| 4 | Partial parallel turns (some repaired, some rejected) must persist **only the repaired subset**, in repair-loop order. |
| 5 | `call_id` in metadata must match `ToolCallRecord.call_id` used for `ExecuteTools` and tool-result correlation (`raw.id` or fallback `"call"`). |
| 6 | Kotlin inference adapters remain **pass-through** for `raw_tool_calls`; the fix is authoritative in Rust. |
| 7 | No Room / domain schema changes; metadata stays opaque JSON on session messages. |

### Non-goals

- Re-parsing or re-repairing tool args in Kotlin at replay time (defense-in-depth sanitization is explicitly out of scope for this ticket).
- Changing `ToolRepairPipeline` or canonicalize rules (reuse existing repair behavior).
- UniFFI API surface changes (metadata shape is unchanged; content becomes canonical).
- Migrating historical persisted chats with bad metadata (old rows may still 400 until overwritten by new turns).

### Current call flow (verified)

```text
submit_model_response
  → ingest_model_response_envelopes → merged_tool_calls
  → push Assistant { metadata: { raw_tool_calls: merged_tool_calls } }   ← bug
  → for raw in merged_tool_calls: ToolRepairPipeline::repair
       → Repaired → pending ExecuteTools
       → Reject/Suggest → tool-err-* Tool messages
  → (next turn) CallModel.session_messages includes Assistant with bad raw_tool_calls
  → Kotlin toOpenAiChatParam replays bad tool calls → provider 400
```

---

## 2. File Structure Table

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `third_party/agent_core/src/engine/mod.rs` | Defer or patch `raw_tool_calls` after repair; invoke helper; reject-only empty array |
| Create | `third_party/agent_core/src/engine/provider_tool_calls.rs` | Pure helper: `ToolCallRecord` → `Vec<RawToolCall>` for provider replay metadata |
| Modify | `third_party/agent_core/src/engine/mod.rs` (`mod provider_tool_calls`) | Export helper for unit tests |
| Create | `third_party/agent_core/tests/executed_tool_call_replay_test.rs` | Integration tests via `integration_test_snapshot` |
| Modify | `third_party/agent_core/Cargo.toml` | Register `[[test]] name = "executed_tool_call_replay_test"` |
| Modify | `feature/inference/src/test/kotlin/.../BaseOpenAiSdkInferenceServiceTest.kt` | Regression: canonical `raw_tool_calls` replays to OpenAI SDK params |
| Modify | `third_party/agent_core/src/engine/mod.rs` (`#[cfg(test)]` module) | Unit tests for helper edge cases (optional colocate in provider_tool_calls.rs) |

**No changes required (verified consumers, behavior improves automatically):**

| Path | Why |
|------|-----|
| `feature/inference/.../BaseOpenAiSdkInferenceService.kt` | Already replays `raw_tool_calls`; fixed upstream metadata is sufficient |
| `feature/inference/.../GoogleInferenceServiceImpl.kt` | Same metadata contract |
| `feature/inference/.../AnthropicInferenceServiceImpl.kt` | Same metadata contract |
| `core/agent/.../RustAgentEngineAdapter.kt` | Forwards session messages; no metadata mutation |
| `third_party/agent_core/src/compact/artifact_squash.rs` | Reads `name` + `arguments_json`; benefits from canonical args |
| `scripts/build-agent-bindings.sh` | No UniFFI signature change |

---

## 3. API Contracts

### 3.1 `raw_tool_calls` metadata shape (unchanged wire format)

Stored on the **Assistant** `AgentMessage.metadata` key `raw_tool_calls` as a JSON **array** of objects. Each object serializes `turn_io::RawToolCall`:

| Field | Type | Rules |
|-------|------|--------|
| `id` | `string` or omitted | Must equal repaired `ToolCallRecord.call_id` when present; omit only if repair used fallback id and provider tolerates missing id (prefer always emit `id` for BYOK replay) |
| `name` | `string` | Post-repair catalog name (e.g. `web_search`, not `web_seach`) |
| `arguments_json` | `string` | Post-repair canonical JSON string (valid JSON object, no markdown fences) |

**Example (fuzzy name + fence repair):**

```json
{
  "raw_tool_calls": [
    {
      "id": "call-1",
      "name": "web_search",
      "arguments_json": "{\"query\":\"rust\"}"
    }
  ]
}
```

**Reject-only turn:**

```json
{
  "raw_tool_calls": []
}
```

Other metadata keys on the same assistant message (e.g. `envelope_ingest: "reified"`) are preserved; only `raw_tool_calls` is overwritten.

### 3.2 Helper contract (new, internal)

```rust
/// Builds provider-replay tool calls from executed/repaired records only.
pub fn provider_facing_raw_tool_calls(repaired: &[ToolCallRecord]) -> Vec<RawToolCall>;
```

Mapping:

- `call_id` → `RawToolCall.id` as `Some(call_id)` (always `Some` in practice).
- `name` → `RawToolCall.name`.
- `arguments_json` → `RawToolCall.arguments_json` (unchanged from repair output).

### 3.3 Session patch contract

```rust
/// Updates the most recently pushed Assistant message in-session.
fn patch_last_assistant_raw_tool_calls(session: &mut AgentSession, calls: Vec<RawToolCall>);
```

- Target: last message in `session.messages` where `role == Assistant`.
- Sets `metadata["raw_tool_calls"]` to `serde_json::to_value(calls)`.
- If `metadata` is not an object, replace with `{"raw_tool_calls": …}` while preserving other keys when possible.

**Invocation points in `submit_model_response`:**

1. After the repair `for` loop when `!repaired.is_empty()` — before `run_compaction_pass` / `ExecuteTools` path.
2. In the reject-only synthetic turn branch (`repaired.is_empty()` after loop) — set `[]`.
3. When `merged_tool_calls.is_empty()` — no patch (no assistant tool metadata needed).

### 3.4 Reject / Suggest handling

| Repair outcome | In `raw_tool_calls` | Session side effects (unchanged) |
|----------------|---------------------|----------------------------------|
| `Repaired` | Included | `ExecuteTools` |
| `Reject` | **Excluded** | `tool-err-*` Tool message with `error_json_for_model` |
| `Suggest` | **Excluded** | `tool-err-*` Tool message with ambiguity JSON |

### 3.5 Kotlin replay contract (unchanged)

`AgentLoopMessage.metadataJson` → `toOpenAiChatParam()`:

- Iterates `raw_tool_calls` array.
- Maps each entry to `ChatCompletionMessageFunctionToolCall` with `function.arguments(argsJson)` **without mutation**.

Provider replay correctness is therefore **entirely dependent** on Rust writing canonical metadata.

---

## 4. Data Model / Schema Changes

**None.** No Room entities, migrations, or `:core:domain` model changes.

- `AgentMessage.metadata` remains opaque `serde_json::Value` / Kotlin `metadataJson` string.
- Persisted chat history written by the app may gradually gain corrected metadata on new agent turns; no backfill.

---

## 5. Constitution Audit

| Rule | Compliance |
|------|------------|
| Clean architecture | Logic in `third_party/agent_core` (Rust); Kotlin inference unchanged |
| Domain isolation | No `:core:domain` dependency |
| Schema lock | No new DB columns |
| Test mandate | Rust integration + Kotlin replay regression tests |
| Inference BYOK | Fixes cloud provider history without touching on-device LiteRT path |

---

## 6. Ordered Implementation Tasks

| Task | File(s) | Scenario IDs |
|------|---------|----------------|
| **T-1** Add `provider_facing_raw_tool_calls` helper | `engine/provider_tool_calls.rs`, `engine/mod.rs` | ETR-R-04, ETR-M-01 |
| **T-2** Add `patch_last_assistant_raw_tool_calls` and call after repair loop when `!repaired.is_empty()` | `engine/mod.rs` | ETR-R-01, ETR-R-02, ETR-R-03, ETR-R-04, ETR-R-06, ETR-M-01, ETR-M-03 |
| **T-3** On reject-only path (`repaired.is_empty()` && `merged_tool_calls` non-empty), patch `raw_tool_calls` to `[]` | `engine/mod.rs` | ETR-R-05, ETR-R-08 |
| **T-4** Preserve co-metadata (`envelope_ingest`, etc.) when patching | `engine/mod.rs` | ETR-R-09 |
| **T-5** Register integration test target | `Cargo.toml` | All ETR-R-* |
| **T-6** Implement `tests/executed_tool_call_replay_test.rs` | New test file | ETR-R-01 … ETR-R-09, ETR-M-01 … ETR-M-03 |
| **T-7** Add Kotlin test for canonical fence-free metadata replay | `BaseOpenAiSdkInferenceServiceTest.kt` | ETR-K-01, ETR-K-02 |
| **T-8** Run `cd third_party/agent_core && cargo test executed_tool_call_replay_test` | CI | All ETR-R-*, ETR-M-* |
| **T-9** Run `./gradlew :feature:inference:testDebugUnitTest --tests "*BaseOpenAiSdkInferenceServiceTest*"` | CI | ETR-K-* |
| **T-10** Run `cd third_party/agent_core && cargo test` (full crate) | CI | Regression guard |
| **T-11** If Android bridge rebuilt in same PR: `./scripts/build-agent-bindings.sh` | Optional | No FFI change expected |

**Dependency order:** T-1 → T-2 → T-3 → T-4 → T-5 → T-6 → T-7 → T-8/T-9 → T-10.

---

## 7. Drift and Escalation Rules

- **Single source of truth:** Provider-facing tool history for agent sessions is `metadata.raw_tool_calls` on Assistant messages written by Rust. Kotlin must not duplicate repair logic for replay; if a provider still 400s, fix Rust repair or metadata patch first.
- **New metadata keys:** Do not add `merged_tool_calls` or `executed_tool_calls` alongside `raw_tool_calls` without a contract waiver — tests assert only `raw_tool_calls` is consumed by Kotlin.
- **Compaction / artifact squash:** Code that reads `raw_tool_calls` (`artifact_squash.rs`, compaction `has_append_context`) assumes canonical names; if append_blocks alias rules change, update ETR-R-03 and squash tests together.
- **ToolRepairPipeline changes:** If repair output shape changes, update helper mapping and ETR-R-01 … ETR-R-04 literals in `test_spec.md`.
- **Persisted history corruption:** If users still see 400s on **old** messages with bad metadata, escalate a separate “metadata repair on read” ticket; out of scope here.
- **Contract waiver:** Any Kotlin-side replay sanitization or second metadata key requires explicit **Contract Waiver** per `AGENTS.md`.

---

## 8. Self-Review Checklist (pre-approval)

- [x] Root cause mapped to `engine/mod.rs` lines 276–277 vs repair loop 325+
- [x] All Kotlin replay sites identified (`BaseOpenAiSdk`, Google, Anthropic)
- [x] Reject-only and partial-parallel edge cases specified
- [x] No placeholders; tasks tied to scenario IDs
- [x] Mutation-defense requires Rust snapshot assertion, not Kotlin-only fix
- [x] No forbidden Room schema changes
