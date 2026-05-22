# fix-artifact-agent-loop — spec.md
# Feature: Fix Artifact Generation Agent Loop Failures

> **Ticket ID:** `fix-artifact-agent-loop`  
> **Related tickets:** `centralized-leaked-tool-call-recovery` (stream holdback; do not re-implement full leak policy here), `fix-inference-stream-error` (strict schema / adapter logging; orthogonal), `FEAT-llm-tool-self-heal` (orchestrator soft-failure; out of scope).  
> **Contracts read:** `AGENTS.md`, `INFERENCE_RULES.md`, `ARCHITECTURE_RULES.md`, `DATA_LAYER_RULES.md`, `UNIT_TESTING.md`.

---

## 1. Feature Summary

### Problem statement

Artifact-generation turns through the Rust agent loop (OpenRouter / OpenAI SDK → `RustAgentEngineAdapter` → `ToolRepairPipeline` → `ArtifactToolExecutor`) fail in four coupled ways:

| # | Symptom | Root cause (verified) |
|---|---------|------------------------|
| P1 | `append_blocks` rejected at docgen validation; tool retry loop exhausts | Cloud providers send `"blocks": "[{...}]"` (JSON **string**) on **native structured** tool calls. `ToolRepairPipeline::repair_arguments` uses `repair/mod.rs::coerce_field`, which only coerces bool/number strings—not arrays/objects. Full string→array coercion exists in `tool_parse/schema_coerce.rs` but is wired only through `coerce_provider_arguments` (provider FFI) and text `parse_tool_calls`, **not** through the execution trust boundary for OpenRouter. |
| P2 | User sees truncated assistant text (e.g. `"Let me try with a simpler"`) during artifact turns | `ToolStreamGate::finish(has_native_tool_calls = true)` drops safe preamble when a tool marker is present (`sg_04`). Held text never reaches UI via `ModelTurnStreamGate` → `AgentLoopEvent.TextToken`. |
| P3 | Artifact card shows title but export says **"Document is still generating"**; `documentJson` is `""` | Placeholder `ArtifactGenerationRequest(content = "")` on `start_artifact`; incomplete `append_blocks`; turn-end `persistAllMessageData` wipes `DocumentEntity` rows from accumulator placeholders. |
| P4 | Room `CursorWindow` crash on chat open (secondary) | Oversized / leaked tool syntax in `message.content`. Minimal defensive sanitization at persist—do not duplicate full `centralized-leaked-tool-call-recovery`. |

### Design goals

1. **Canonicalization pipeline in Rust** with explicit policy modes: one shared argument path, separate execution policy—not two parallel repair implementations.
2. **Provider ingress** may canonicalize early for UX; **execution gate** re-runs canonicalization idempotently before tools run (trust boundary).
3. **Visible assistant preamble** on native-tool turns (stream gate fix).
4. **Durable artifact documents** and export hydration when snapshot content is blank.
5. **Schema lock:** no Room migrations.

### Non-goals

- Kotlin-side envelope parsing or `ToolStreamGateSession` in `feature/inference` (TOOL-PARSE-002).
- Full leaked-tool recovery policy (`plans/centralized-leaked-tool-call-recovery/spec.md`).
- snake_case tool keys (`artifactId`, `isComplete` remain camelCase).
- Provider-specific coercion forks outside shared canonicalizer.

### Prerequisite / coordination

- **P2** overlaps `centralized-leaked-tool-call-recovery`. If merged first, keep preamble-flush tests (`FAAL-SG-*`) only.

---

## 2. Tool-call canonicalization pipeline (architecture — required)

### Target flow

```text
Provider Adapter
  → adapt provider output to neutral RawToolCall { id, name, arguments_json }
  → canonicalize_tool_call(raw, ProviderIngress)     // early; optional per adapter
  → AgentModelTurnResult / submitModelResponse

Agent engine (trust boundary)
  → canonicalize_tool_call(raw, ExecutionStrict)     // idempotent re-run
  → validate + execution policy (reject, fuzzy name, events)
  → build ToolCallRecord
  → Kotlin Tool Executor
```

**Invariant:** `canonicalize_raw_arguments` / `canonicalize_arguments_value` are the **only** implementation of JSON repair, alias normalization, schema coercion (`coerce_to_schema`), and special-value normalization. Both `coerce_provider_arguments` (FFI) and `ToolRepairPipeline` delegate to them—no parallel `coerce_field` bool/number path.

### Why two entry points remain (wrappers, not duplicate logic)

| Entry | Layer | Responsibilities |
|-------|--------|------------------|
| `coerce_provider_arguments` (FFI) | Provider ingress wrapper | Adapt to `RawToolCall` → `canonicalize_tool_call(..., ProviderIngress)` → map to `ToolParseField` for Kotlin adapters (Anthropic, Google). **No** execution rejects, **no** `ToolCallRecord`, **no** fuzzy unknown-tool reject. |
| `ToolRepairPipeline::repair` | Execution gate wrapper | `canonicalize_tool_call(..., ExecutionStrict)` → apply execution policy (unknown tool reject, fuzzy suggest, soft-failure events) → `ToolCallRecord` + `repair_metadata`. |

Today’s smell: both wrappers embed overlapping argument logic. After this ticket, they share `canonicalize/` internals.

### Responsibility split (four concerns)

| # | Concern | Module / function | ProviderIngress | ExecutionStrict |
|---|---------|-------------------|---------------|-----------------|
| 1 | Name normalization | `resolve_tool_name(name, catalog, mode)` | Strip `functions.` / `tools.` prefix; catalog exact resolve; **no** fuzzy reject (pass raw name through if unknown—engine will strict-reject) | Full fuzzy repair (`web_seach` → `web_search`), reject/suggest unknown |
| 2 | Argument canonicalization | `canonicalize_raw_arguments(tool_name, raw, catalog, mode)` → `canonicalize_arguments_value(...)` | **Shared** | **Shared** (idempotent) |
| 3 | Execution policy | `apply_execution_policy(...)` in `repair/` | **Skipped** | Reject missing required fields post-canonicalize; unknown tool; emit `AgentEvent` |
| 4 | Runtime bookkeeping | `build_tool_call_record(...)` | **Skipped** | `call_id`, `repair_metadata`, `ToolCallRecord` |

### Policy modes

```rust
pub enum ToolCallCanonicalizeMode {
    /// Provider mapping time: canonicalize name (light) + arguments; never reject to model.
    ProviderIngress,
    /// Immediately before ExecuteTools: full name policy + arguments + validation.
    ExecutionStrict,
}
```

```rust
pub struct CanonicalizedToolCall {
    pub resolved_name: String,
    pub arguments_json: String,
    pub arguments_value: serde_json::Value,
    pub repair_metadata: Option<RepairRecord>,
    pub argument_repairs: Vec<String>,
}

pub enum CanonicalizeToolCallOutcome {
    Ok(CanonicalizedToolCall),
    /// ExecutionStrict only: model-facing reject JSON
    Reject { error_json_for_model: String },
    /// ExecutionStrict only: ambiguous tool name
    Suggest { original: String, closest_name: String },
}
```

```rust
pub fn canonicalize_tool_call(
    raw: &RawToolCall,
    catalog: &ToolCatalog,
    mode: ToolCallCanonicalizeMode,
) -> (CanonicalizeToolCallOutcome, Vec<AgentEvent>);
```

**Argument canonicalization contract:**

```rust
pub struct CanonicalizedArguments {
    pub arguments_json: String,
    pub value: serde_json::Value,
    pub repaired: bool,
    pub repairs: Vec<String>,
}

pub fn canonicalize_raw_arguments(
    tool_name: &str,
    raw_arguments_json: &str,
    catalog: &ToolCatalog,
    mode: ToolCallCanonicalizeMode,
) -> Result<CanonicalizedArguments, CanonicalizeArgumentsError>;

pub fn canonicalize_arguments_value(
    tool_name: &str,
    value: &mut serde_json::Value,
    catalog: &ToolCatalog,
) -> Result<CanonicalizedArguments, CanonicalizeArgumentsError>;
```

Steps (single implementation, used by both modes):

1. `strip_json_fences` on raw JSON string.
2. `repair_json` when parsing raw string.
3. `normalize_parameter_aliases` (e.g. `artifact_id` → `artifactId`, `is_complete` → `isComplete`).
4. `coerce_to_schema` from catalog `ToolSchemaHint` (includes string → array for `blocks`).
5. `normalize_special_values` (existing `tool_parse` helpers).
6. Strict validation of required fields and unknown parameters only when requested by the caller/mode.
7. `canonical_arguments_json` serialization.

`ProviderIngress` may be lenient, but it must not permanently hide unrecoverable argument JSON. If raw provider arguments cannot be parsed or repaired, the ingress wrapper should preserve the original `args_json` in its returned field and mark the repair list with the failure reason so the `ExecutionStrict` gate can reject the same payload before execution.

**Idempotence requirement:** Calling `canonicalize_arguments_value` twice on the same parsed `Value` yields identical JSON (test `FAAL-CP-04`).

### Current vs target (OpenRouter artifact path)

```text
Today
  OpenRouter SDK tool_calls
    → submitModelResponse (raw args)
    → ToolRepairPipeline::repair (weak coerce_field)
    → Kotlin validateAppendBlocksArgs FAIL

Target
  OpenRouter SDK tool_calls
    → submitModelResponse (raw args)
    → canonicalize_tool_call(ExecutionStrict)   // same args path as Anthropic after ingress
    → Kotlin validateAppendBlocksArgs OK
```

Anthropic/Google today call `coerce_provider_arguments` at ingress; after refactor they call the **same** argument canonicalizer as execution. Engine still re-runs `ExecutionStrict` before execute (safe if idempotent).

### `parse_tool_calls` alignment

`parse_extracted_envelope` must call `canonicalize_raw_arguments` / `canonicalize_arguments_value` (or `canonicalize_tool_call` with `ProviderIngress`) instead of duplicating a third coercion path. Text envelope parsing stays in `tool_parse/`; canonicalization imports from `canonicalize/`.

---

## 3. Target call flow (artifact turn)

```text
ChatViewModel / GenerateChatResponseUseCase
  → RustLoopInferenceService
  → RustAgentEngineAdapter
       CallModel → BaseOpenAiSdkInferenceService
         onTextToken → ModelTurnStreamGate (preamble flush fix)
         native tool_calls → RawToolCall list
       submitModelResponse
       ExecuteTools
         → canonicalize_tool_call(raw, ExecutionStrict) per call
         → host.executeTools(ToolCallRecord)
       emitToolSideEffects → InferenceEvent.Artifacts when complete
  → persistAllMessageData (preserve DocumentDao when placeholder blank)
  → export hydrates from repository when card documentJson blank
```

---

## 4. File structure table

| Action | Path | Responsibility |
|--------|------|----------------|
| **Create** | `third_party/agent_core/src/canonicalize/mod.rs` | Public API: `ToolCallCanonicalizeMode`, `canonicalize_tool_call`, `canonicalize_raw_arguments`, `canonicalize_arguments_value`. |
| **Create** | `third_party/agent_core/src/canonicalize/name.rs` | `resolve_tool_name` with mode-specific policy. |
| **Create** | `third_party/agent_core/src/canonicalize/arguments.rs` | `canonicalize_raw_arguments` + `canonicalize_arguments_value`; delegates to `tool_parse::{repair_json, schema_coerce, ...}`. |
| **Create** | `third_party/agent_core/src/canonicalize/policy.rs` | ExecutionStrict validation + mapping to `CanonicalizeToolCallOutcome`. |
| **Modify** | `third_party/agent_core/src/lib.rs` | `pub mod canonicalize;` |
| **Modify** | `third_party/agent_core/src/repair/mod.rs` | `ToolRepairPipeline::repair` becomes thin wrapper over `canonicalize_tool_call(ExecutionStrict)` + `build_tool_call_record`; delete `apply_schema_hint` / bool-only `coerce_field`. |
| **Modify** | `third_party/agent_core/src/tool_parse/mod.rs` | `coerce_provider_arguments` → `canonicalize_tool_call(ProviderIngress)`; `parse_extracted_envelope` uses `canonicalize_raw_arguments` / `canonicalize_arguments_value`. |
| **Modify** | `third_party/agent_core/src/tool_parse/schema_coerce.rs` | No behavior change; used by `canonicalize/arguments.rs`. |
| **Create** | `third_party/agent_core/tests/tool_call_canonicalize_test.rs` | Parity, idempotence, mode behavior (`FAAL-CP-*`, `FAAL-RC-*`). |
| **Modify** | `third_party/agent_core/tests/tool_parse_integration.rs` | Regression: envelope path still coerces stringified `blocks`. |
| **Modify** | `third_party/agent_core/src/tool_stream_gate/mod.rs` | Preamble flush when `has_native_tool_calls` + marker (`FAAL-SG-*`). |
| **Modify** | `third_party/agent_core/tests/tool_stream_gate_test.rs` | Stream gate scenarios. |
| **Modify** | `core/agent/.../ModelTurnStreamGate.kt` | Forward `trailingFlush`; document contract. |
| **Create** | `core/agent/src/test/.../ModelTurnStreamGateNativePreambleTest.kt` | `FAAL-SG-03`, `FAAL-SG-04`. |
| **Modify** | `core/data/.../ChatRepositoryImpl.kt` | Persist preserves non-empty `DocumentEntity` when artifact content blank. |
| **Create** | `core/data/src/test/.../ChatRepositoryImplArtifactPersistTest.kt` | `FAAL-AR-*`, `FAAL-MD-03`. |
| **Modify** | `core/domain/.../PersistAccumulatedChatMessagesUseCase.kt` | Extend existing `sanitizePersistedContent`; add length guard if absent. |
| **Modify** | `feature/inference/.../RustLoopInferenceService.kt` | Progressive `Artifacts` on successful `append_blocks` when `fullDocument` present. |
| **Modify** | `feature/chat/...` (repository port + ViewModel) | Hydrate export `documentJson` when card blank (`FAAL-AR-05`). |
| **Modify** | `core/data/.../artifact/ArtifactToolExecutor.kt` | Errors must not clear `DocumentEntity`. |

**Delete / forbid:** `repair/mod.rs::apply_schema_hint`, `repair/mod.rs::coerce_field` for schema types (remove or hard-fail tests if reintroduced).

---

## 5. API contracts

### 5.1 Shared argument canonicalizer — append_blocks example

| Step | Input field | Output field |
|------|-------------|--------------|
| Input | `"blocks": "[{\"type\":\"paragraph\",\"text\":\"Hi\"}]"` (string) | — |
| After canonicalize | `"blocks": [{"type":"paragraph","text":"Hi"}]` (array) | |
| Aliases | `artifact_id`, `is_complete` | `artifactId`, `isComplete` |

### 5.2 `canonicalize_tool_call` — mode behavior

| Condition | ProviderIngress | ExecutionStrict |
|-----------|-----------------|-----------------|
| Unknown tool name | `Ok` with best-effort resolved name from catalog or passthrough | `Reject` or `Suggest` (existing fuzzy rules) |
| Invalid JSON arguments | Preserve original `args_json` plus repair/failure note when unrecoverable; do not mask it as executable `{}` | `Reject` with `Invalid JSON for tool arguments` |
| Stringified `blocks` | `Ok`, array coerced | `Ok`, array coerced (same JSON as ingress) |
| Missing required field after coerce | Defaults from catalog hint (`isComplete: false`) | `Reject` if still missing |

### 5.3 `ToolStreamGate::finish` (unchanged from prior plan)

When `allow_tool_call == true` and `has_native_tool_calls == true`:

- `trailing_flush` = safe text from `emitted_length` up to first strong tool marker (exclusive).
- Do not return empty flush when preamble exists before marker.

### 5.4 Kotlin — persist and export (unchanged)

- **Persist:** blank accumulator artifact `content` must not overwrite non-empty `DocumentEntity.json`.
- **Export:** resolve via repository when card `documentJson` is blank; keep card model as primary when non-blank (no forced Room re-read on every click).

### 5.5 UniFFI / public surfaces

| Surface | Change |
|---------|--------|
| `coerce_provider_arguments` | Signature **unchanged**; body delegates to `canonicalize_tool_call(ProviderIngress)`. |
| `repair_tool` | Unchanged; routes through `ToolRepairPipeline` → `ExecutionStrict`. |
| New exports | **None** required on FFI; `canonicalize_*` is crate-internal unless tests need `pub` for integration crate. |

---

## 6. Data model changes

**None.**

---

## 7. Ordered implementation tasks

### Phase A — Canonicalization pipeline (P1)

| # | Task | Scenario IDs |
|---|------|----------------|
| T1 | Add `canonicalize/` module: `ToolCallCanonicalizeMode`, `canonicalize_raw_arguments`, `canonicalize_arguments_value`, error types. Move shared alias/special-value helpers from `tool_parse` or call them without duplication. | FAAL-CP-01, FAAL-CP-04, FAAL-RC-01 |
| T2 | Implement `resolve_tool_name` in `canonicalize/name.rs` with ProviderIngress vs ExecutionStrict policy; migrate logic from `repair_name`. | FAAL-CP-02, FAAL-RC-02 |
| T3 | Implement `canonicalize_tool_call` + `canonicalize/policy.rs` (ExecutionStrict reject/suggest); wire events. | FAAL-CP-02, FAAL-CP-03, FAAL-RC-02 |
| T4 | Refactor `ToolRepairPipeline::repair` to thin wrapper: `canonicalize_tool_call(ExecutionStrict)` → `build_tool_call_record`; remove `apply_schema_hint` / `coerce_field`. | FAAL-RC-01, FAAL-RC-04, FAAL-MD-01 |
| T5 | Refactor `coerce_provider_arguments` to `canonicalize_tool_call(ProviderIngress)` + map to `ToolParseField`; keep FFI stable. | FAAL-CP-03, FAAL-RC-03 |
| T6 | Align `parse_extracted_envelope` to the shared argument canonicalizer (no third coercion path). | FAAL-CP-05, FAAL-RC-01 |
| T7 | Add `tool_call_canonicalize_test.rs`: parity ingress vs execution, idempotence, stringified `blocks`, mutation guards. | FAAL-CP-03, FAAL-CP-04, FAAL-RC-03, FAAL-MD-01 |

### Phase B — Stream gate (P2)

| # | Task | Scenario IDs |
|---|------|----------------|
| T8 | Fix `ToolStreamGate::finish` native preamble flush; update `tool_stream_gate_test.rs`. | FAAL-SG-01, FAAL-SG-02, FAAL-SG-05, FAAL-MD-02 |
| T9 | `ModelTurnStreamGate` + `ModelTurnStreamGateNativePreambleTest.kt`. | FAAL-SG-03, FAAL-SG-04 |

### Phase C — Artifact persist / export (P3)

| # | Task | Scenario IDs |
|---|------|----------------|
| T10 | `ChatRepositoryImpl.persistAllMessageData` preserve rules + tests. | FAAL-AR-01, FAAL-AR-04, FAAL-MD-03 |
| T11 | Repository hydration for export + `mergeArtifactGenerationRequests` regression. | FAAL-AR-02, FAAL-AR-05 |
| T12 | `RustLoopInferenceService.emitToolSideEffects` emits `Artifacts` when a successful `append_blocks` result contains `fullDocument` **and** completion semantics accepted by `AppendBlocksToolResultParser` (`isComplete` true). Do not emit partial documents as export-ready artifacts unless the domain model gains an explicit partial-artifact state. | FAAL-AR-03 |

### Phase D — Defensive persist (P4)

| # | Task | Scenario IDs |
|---|------|----------------|
| T13 | Verify/extend existing `PersistAccumulatedChatMessagesUseCase.sanitizePersistedContent`; add `MAX_PERSIST_CONTENT_CHARS` guard if absent. Cover `<tool_call>` tags in addition to existing raw JSON / CDATA / `autocomplete` traces. | FAAL-PS-01, FAAL-PS-02 |

### Phase E — Validation

| # | Task | Scenario IDs |
|---|------|----------------|
| T14 | `ArtifactToolExecutor` integration: stringified blocks fail pre-refactor test snapshot, pass post T4. Regenerate UniFFI only if needed. Run section 8 commands. | FAAL-RC-04, FAAL-AR-03, All |

**Task count: 14**

---

## 8. Validation commands

```bash
# Rust — canonicalization
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_call_canonicalize_test
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_parse_integration
cargo test --manifest-path third_party/agent_core/Cargo.toml canonicalize::
cargo test --manifest-path third_party/agent_core/Cargo.toml repair::

# Rust — stream gate
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_stream_gate_test

# Kotlin / Android
./gradlew :core:agent:testDebugUnitTest --tests "*ModelTurnStreamGate*"
./gradlew :core:data:testDebugUnitTest --tests "*ChatRepositoryImplArtifactPersist*"
./gradlew :core:data:testDebugUnitTest --tests "*ArtifactToolExecutor*"
./gradlew :feature:inference:testDebugUnitTest --tests "*RustLoop*"
./gradlew :feature:chat:testDebugUnitTest --tests "*ChatViewModelTest*onConfirmArtifactExport*"
./gradlew :core:domain:testDebugUnitTest --tests "*AppendBlocksToolResultParser*"

./gradlew ktlintCheck
./gradlew detekt
./gradlew :core:agent:assembleDebug :core:data:assembleDebug :feature:inference:assembleDebug
```

---

## 9. Drift / escalation rules

| Condition | Action |
|-----------|--------|
| Shared argument canonicalizer diverges between ingress and execution on same input | **Block merge** — parity test `FAAL-CP-03` / `FAAL-RC-03` failed. |
| Idempotence test `FAAL-CP-04` fails | Block merge; fix double-application bug before adding provider calls. |
| `coerce_field` or `apply_schema_hint` reappear in `repair/mod.rs` | **Block merge** — mutation defense `FAAL-MD-01`. |
| `centralized-leaked-tool-call-recovery` conflicts on stream gate | Rebase; retain preamble tests only. |
| Docgen still rejects after Phase A | Escalate `docgen_core` schema vs coerced shape; do not add provider-specific Kotlin coercion. Kotlin may keep its existing domain validation, but Rust remains the normalization source of truth. |
| Provider sends `blocks` as object not array | Extend shared argument canonicalizer + catalog hint; add scenario literal. |

---

## 10. Constitution audit

| Rule | Compliance |
|------|------------|
| Clean architecture | Coercion/policy in Rust `agent_core`; Kotlin executes + docgen validates only. |
| Schema lock | No migrations. |
| Inference rules | Single canonicalization pipeline; no provider-specific repair forks. |
| Tests mandatory | All tasks map to `FAAL-*` in `test_spec.md`. |
