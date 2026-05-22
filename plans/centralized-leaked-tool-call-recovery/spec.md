# centralized-leaked-tool-call-recovery — spec.md
# Feature: Centralized Leaked Tool-Call Recovery (TOOL-PARSE-002 follow-on)

> **Ticket ID:** `centralized-leaked-tool-call-recovery`  
> **Builds on:** TOOL-PARSE-002 (`tool_stream_gate`, `envelope_ingest`, UniFFI adapters).  
> **Contracts read:** `AGENTS.md`, `INFERENCE_RULES.md`, `ARCHITECTURE_RULES.md`, `CODE_STYLE_RULES.md`, `UNIT_TESTING.md`, `DATA_LAYER_RULES.md` (persistence is read-only for this ticket; no schema changes).

---

## 1. Feature Summary

### Problem

Cloud providers (notably OpenRouter) sometimes emit **tool-call syntax inside streamed assistant text** instead of (or in addition to) structured provider tool-call deltas. A large `append_blocks` leak was streamed to the UI, **persisted as assistant message content**, and later caused a **CursorWindow / Room crash** when opening the chat.

TOOL-PARSE-002 added Rust `tool_stream_gate` holdback and `engine::envelope_ingest` at `submit_model_response`, but two gaps remain:

1. **Streaming leak path (primary bug):** `BaseOpenAiSdkInferenceService` (and thus OpenRouter / xAI / API OpenAI) sets `ToolStreamGateSession(allowToolCall = false)` to avoid false positives from the weak `\{"name":` marker regex. Leaked tool syntax is therefore **emitted immediately** via `onTextToken` → `AgentLoopEvent.TextToken` → `InferenceEvent.PartialResponse` → chat persistence **before** Rust engine ingest runs.
2. **Turn-end policy gap:** `ingest_model_response_envelopes` **merges** parsed leaked tools with native tools whenever markers are detected; the design target requires **preferring structured native tool calls** and not double-executing leaked envelopes when native tools are present.
3. **False-positive regression risk:** Re-enabling holdback with the current weak JSON marker would suppress normal assistant JSON prose on turns where `allowToolCall = true` but the model writes JSON examples in text.

### Correction (artifact schema)

- Canonical steering and tool schema for `append_blocks` remain **camelCase** (`artifactId`, `isComplete`) per `ToolDefinition.APPEND_BLOCKS` and `GenerateArtifactParamsTest`.
- OpenRouter correctly used `artifactId`; the incident was **not** caused by wrong attribute naming.
- Rust `tool_parse::normalize_parameter_aliases` must **keep** fuzzy `artifact_id` / `is_complete` → camelCase coercion; do not change schema steering to snake_case.

### Design target

| # | Requirement |
|---|-------------|
| 1 | Structured provider tool calls remain the preferred execution path. |
| 2 | While streaming assistant text, `agent_core` holds back **strong** leaked-tool sentinels only: `<tool_call>…</tool_call>`, `[TOOL_CALLS]`, DSML blocks, `call:tool_name{…}`, and raw JSON envelopes **only when strong enough** to avoid normal prose false positives. |
| 3 | Safe visible text streams to the UI normally. |
| 4 | Suspicious leaked-tool text is buffered, not displayed or persisted, until turn-end classification. |
| 5 | At model turn completion: if native structured tool calls exist → **prefer native**; else if buffered text parses into valid/self-healed tool calls → emit recovered tools and **do not** display/persist marker bodies; else flush buffered text as normal assistant content. |
| 6 | Kotlin/provider adapters **do not** implement provider-specific leak policy; they stream **raw** text and structured tool deltas; Rust owns holdback + turn-end resolution. |
| 7 | Reuse existing `tool_parse` fuzzy envelope + alias self-heal behavior. |
| 8 | Do **not** reintroduce the failure mode where final turns with normal JSON prose are suppressed solely because `allowToolCall = true`. |

### Non-goals

- Room schema changes (`DATA_LAYER_RULES.md` schema lock).
- Kotlin-side envelope parsing in `feature/inference` (forbidden by TOOL-PARSE-002).
- Data-layer “repair on read” as the primary fix (optional defensive read filter is out of scope unless a separate ticket is opened).
- Provider-specific regex forks in `OpenRouterInferenceServiceImpl` vs `BaseOpenAiSdkInferenceService`.

---

## 2. Current Call Flow (verified in codebase)

### 2.1 OpenRouter / OpenAI SDK (BYOK cloud)

```text
ChatViewModel / GenerateChatResponseUseCase
  → DirectChatInferenceExecutor.sendPrompt
  → RustLoopInferenceService.sendPrompt
  → RustAgentEngineAdapter.execute (UniFFI submit-style loop)
  → host.completeModelTurn (Kotlin callback)
  → OpenRouterInferenceServiceImpl.completeSingleTurn
  → BaseOpenAiSdkInferenceService.completeSingleTurn
       • SDK streaming: delta.content → InferenceEvent.PartialResponse
       • ToolStreamGateSession(allowToolCall = false)  ← holdback DISABLED
       • onTextToken emits every chunk immediately
       • SDK delta.toolCalls → AgentRawToolCall list (may be empty on leak)
       • Returns AgentModelTurnResult(assistantText = full buffer, toolCalls = native only)
  → RustAgentEngineAdapter: AgentLoopEvent.TextToken(chunk) → UI PartialResponse
  → Chat layer persists streaming assistant content (appendMessageContent)
  → engine.submit_model_response → ingest_model_response_envelopes (turn-end only)
```

**Leak emit/persist today:** `BaseOpenAiSdkInferenceService` lines 108–132 (`allowToolCall = false` on gate) + `RustLoopInferenceService` lines 201–203 (`PartialResponse` per `TextToken`) + chat accumulator persistence on `GeneratingText`.

### 2.2 Anthropic SDK

Same agent loop; `AnthropicInferenceServiceImpl.completeSingleTurn` uses `ToolStreamGateSession(allowToolCall = allowToolCall)` (holdback **enabled** when tools allowed). Native tool use comes from `response.toolUse`; text gate uses `finish(hasNativeToolCalls = toolCalls.isNotEmpty())`.

### 2.3 LiteRT / Llama (on-device)

`LlamaInferenceServiceImpl.completeSingleTurn` uses `ToolStreamGateSession(allowToolCall = allowToolCall)` and always returns `toolCalls = emptyList()` (tools reified from text at engine ingest). LiteRT may emit `call:append_blocks{…}` in text (see `tool_stream_gate_test.rs` SG-09).

### 2.4 Google GenAI SDK

`GoogleInferenceServiceImpl` mirrors Anthropic-style gating + `coerceProviderArguments` for native tools.

### 2.5 Rust agent engine (post-submit, already centralized)

`AgentEngine::submit_model_response` → `ingest_model_response_envelopes` → repair pipeline → session messages. This path **does not** prevent UI/persistence of leaked bytes during streaming.

---

## 3. Proposed Architecture

### 3.1 Ownership

| Concern | Owner after this ticket |
|--------|-------------------------|
| Strong-sentinel streaming holdback | Rust `tool_stream_gate` (marker tiers) |
| Per-model-turn gate lifecycle | Rust `engine::leaked_tool_recovery` + Kotlin `RustAgentEngineAdapter` only |
| Turn-end classify / parse / flush | Rust `engine::leaked_tool_recovery::resolve_model_turn_text` |
| Native-vs-leaked precedence | Rust `envelope_ingest` + turn resolver |
| Provider I/O | Kotlin `feature/inference` — **raw** chunks, native tool deltas only |
| Fuzzy parse / alias heal | Existing `tool_parse` (unchanged contract) |

### 3.2 New turn-resolution pipeline (Rust)

Introduce a single entry point used by both streaming finish and `submit_model_response`:

```rust
pub struct ModelTurnTextResolution {
    pub visible_assistant_text: String,
    pub native_tool_calls: Vec<RawToolCall>,
    pub recovered_tool_calls: Vec<RawToolCall>,
    pub had_strong_tool_marker: bool,
}

pub fn resolve_model_turn_text(
    buffered_text: &str,
    native_tool_calls: &[RawToolCall],
    allow_tool_call: bool,
) -> ModelTurnTextResolution;
```

**Semantics:**

- When `allow_tool_call == false`: `visible_assistant_text = buffered_text`; no holdback classification; `recovered_tool_calls` empty; return native tools unchanged.
- When `allow_tool_call == true`:
  - Run `first_strong_tool_marker_start` (new API; excludes weak JSON-in-prose marker).
  - If **no** strong marker → flush full buffer as visible text; recovered empty.
  - If strong marker and `native_tool_calls` **non-empty** → visible text = preamble only (gate finish semantics with `has_native_tool_calls = true`); **do not** append `parse_tool_calls` successes to execution list; still strip marker regions from `visible_assistant_text` for persistence.
  - If strong marker and `native_tool_calls` **empty** → `parse_tool_calls(buffered_text)`; on success, `recovered_tool_calls` populated, `visible_assistant_text` = `visible_prefix + visible_suffix`; on failure, delegate to existing `EnvelopeIngestOutcome::ParseFailed` path (synthetic tool error + retry), visible text = stripped prefix/suffix or empty per batch.
  - Weak JSON-like substrings alone must **not** trigger holdback (regression guard for CL-TR-04).

`ingest_model_response_envelopes` becomes a thin wrapper: call `resolve_model_turn_text`, then map to `EnvelopeIngestOutcome` + merged tool list = `native_tool_calls` if non-empty else `recovered_tool_calls`.

### 3.3 Streaming gate policy (Rust)

- Split markers in `tool_stream_gate/markers.rs`:
  - **Strong** (streaming holdback): DSML, `<tool_call`, `[TOOL_CALLS]`, `call:tool_name{`, CDATA tool, partial prefixes (`<tool_`, `<|DSM`, etc.).
  - **Weak** (parse-only): bare `\{\s*["']name["']\s*:` — used only inside `tool_parse::extract_raw_json` after strong context, **removed** from `TOOL_MARKER_REGEXES`.
- `ToolStreamGate` continues to use strong markers only.
- `normalize_model_text` in `tool_parse` already folds `｜` → `|`; extend normalization to collapse **spaced** DSML spellings (`< | DSML |` → `<|DSML|`) before marker detect and parse (CL-TR-06).

### 3.4 Kotlin / FFI integration

- **Remove** `ToolStreamGateSession` usage from all `feature/inference` `completeSingleTurn` implementations (BaseOpenAI, Anthropic, Google, Llama).
- Add `ModelTurnStreamGate` helper in `core/agent` (not `feature/inference`):
  - Holds `ToolStreamGatePort` handle + `allowToolCall` for one model turn.
  - `onProviderTextChunk(raw)` → `port.push` → emit only `emittedPrefix` to `onTextToken`.
  - `finish(nativeToolCalls)` → `port.finish` → emit `trailingFlush` → return `bufferedText()`.
- `RustAgentEngineAdapter.completeModelTurn` callback wraps `onTextToken`:
  - Create gate with `action.request.allowToolCall`.
  - Pass wrapper to host; on return, build `AgentModelTurnResult` with `assistantText = gate.bufferedText()` (raw buffer for engine; visible tokens already streamed).
- Providers pass chunks **directly** from SDK/LiteRT to the wrapper without local holdback.

No new domain port required if `ToolStreamGatePort` + engine ingest suffice; leak policy stays in Rust sources under `third_party/agent_core`.

### 3.5 Data model / schema changes

**None.** No new Room columns or tables. Assistant content may shrink when leaks are stripped at turn-end; existing `MessageDao` / `ChatRepositoryImpl` APIs remain unchanged.

---

## 4. File Structure Table

| File Path | Status | Responsibility |
|-----------|--------|----------------|
| `third_party/agent_core/src/tool_stream_gate/markers.rs` | **Modify** | Split `first_strong_tool_marker_start` vs parse-only weak JSON detection; spaced-DSML normalization helper. |
| `third_party/agent_core/src/tool_stream_gate/mod.rs` | **Modify** | Use strong markers in `safe_emit_end` / `finish`; export strong-marker API. |
| `third_party/agent_core/src/tool_parse/mod.rs` | **Modify** | Apply spaced-DSML normalization in `normalize_model_text`; keep alias coercion for `artifact_id` / `is_complete`. |
| `third_party/agent_core/src/engine/leaked_tool_recovery.rs` | **Create** | `resolve_model_turn_text`, `ModelTurnTextResolution`; central turn-end policy. |
| `third_party/agent_core/src/engine/envelope_ingest.rs` | **Modify** | Delegate to `resolve_model_turn_text`; prefer native tool calls when non-empty. |
| `third_party/agent_core/src/engine/mod.rs` | **Modify** | `pub mod leaked_tool_recovery`; wire ingest. |
| `third_party/agent_core/src/lib.rs` | **Modify** | Re-export if needed for tests. |
| `third_party/agent_core/tests/tool_stream_gate_test.rs` | **Modify** | Add CL-SG scenarios; weak JSON prose must not hold back. |
| `third_party/agent_core/tests/leaked_tool_recovery_test.rs` | **Create** | CL-TR and CL-E Rust integration tests. |
| `third_party/agent_core/tests/engine_envelope_retry_test.rs` | **Modify** | Align with native-preference rules; add append_blocks leak cases. |
| `third_party/agent_core/tests/tool_parse_integration.rs` | **Modify** | camelCase + snake_case alias scenarios for `append_blocks`. |
| `core/agent/.../ModelTurnStreamGate.kt` | **Create** | Per-turn streaming gate wrapper over `ToolStreamGatePort`. |
| `core/agent/.../RustAgentEngineAdapter.kt` | **Modify** | Own streaming holdback; wrap `onTextToken`; stop relying on inference-layer gate. |
| `core/agent/src/test/.../ModelTurnStreamGateTest.kt` | **Create** | Adapter unit tests for CL-K-01, CL-K-02. |
| `core/agent/src/test/.../RustAgentEngineAdapterLeakTest.kt` | **Create** | TextToken holdback integration with fake host. |
| `feature/inference/.../BaseOpenAiSdkInferenceService.kt` | **Modify** | Remove `ToolStreamGateSession`; stream raw `delta.content` to `onTextToken`. |
| `feature/inference/.../OpenRouterInferenceServiceImpl.kt` | **Modify** | No leak policy (inherits BaseOpenAI behavior). |
| `feature/inference/.../ApiInferenceServiceImpl.kt` | **Modify** | No leak policy (inherits BaseOpenAI). |
| `feature/inference/.../XaiInferenceServiceImpl.kt` | **Modify** | No leak policy (inherits BaseOpenAI). |
| `feature/inference/.../AnthropicInferenceServiceImpl.kt` | **Modify** | Remove local `ToolStreamGateSession`. |
| `feature/inference/.../GoogleInferenceServiceImpl.kt` | **Modify** | Remove local `ToolStreamGateSession`. |
| `feature/inference/.../LlamaInferenceServiceImpl.kt` | **Modify** | Remove local `ToolStreamGateSession` in `completeSingleTurn`. |
| `feature/inference/.../ToolStreamGateSession.kt` | **Delete** | Replaced by `ModelTurnStreamGate` in `core/agent`. |
| `feature/inference/src/test/.../BaseOpenAiSdkInferenceServiceTest.kt` | **Modify** | Update CL-K-04 expectations (raw stream; no Kotlin gate). |
| `feature/inference/src/test/.../CompleteSingleTurnRawSubmitTest.kt` | **Modify** | Assert raw submit + no `ToolStreamGatePort` in inference module. |
| `feature/inference/src/test/.../LeakedToolRecoveryMutationTest.kt` | **Create** | CL-MD-02, CL-MD-03 architecture guards. |
| `core/domain/.../GenerateArtifactParamsTest.kt` | **Modify** | Add CL-TR-07 regression import path if only comment; prefer Rust integration for alias parse. |
| `scripts/build-agent-bindings.sh` | **Run** | Regenerate UniFFI after Rust public API changes (if any FFI surface changes). |
| `core/agent/.../agent_core.kt` | **Regenerate** | UniFFI output (do not hand-edit). |

**Not modified (reference only):** `ChatRepositoryImpl.kt`, `MessageDao.kt` — persistence path unchanged; fix is upstream suppression of leaked chunks.

---

## 5. API Contracts and Interfaces

### 5.1 Rust — strong markers

```rust
pub fn first_strong_tool_marker_start(text: &str) -> Option<usize>;
pub fn normalize_spaced_dsml(text: &str) -> String;
```

Strong set must include at minimum: `<tool_call`, `[TOOL_CALLS]`, DSML `tool_calls` / `invoke name=`, `call:identifier{`, `<![CDATA[<tool`. Must **exclude** standalone `\{\s*["']name["']\s*:`.

### 5.2 Rust — turn resolution

```rust
pub struct ModelTurnTextResolution {
    pub visible_assistant_text: String,
    pub native_tool_calls: Vec<RawToolCall>,
    pub recovered_tool_calls: Vec<RawToolCall>,
    pub had_strong_tool_marker: bool,
}

pub fn resolve_model_turn_text(
    buffered_text: &str,
    native_tool_calls: &[RawToolCall],
    allow_tool_call: bool,
) -> ModelTurnTextResolution;
```

**Merged execution list rule:** If `native_tool_calls.len() > 0`, engine executes **only** native calls (post-repair). Recovered calls are ignored for execution but marker text is still stripped from assistant content.

### 5.3 Rust — envelope ingest (updated)

```rust
pub enum EnvelopeIngestOutcome { /* unchanged variants */ }

pub fn ingest_model_response_envelopes(
    assistant_text: Option<&str>,
    native_tool_calls: &[RawToolCall],
) -> (EnvelopeIngestOutcome, Vec<RawToolCall>);
```

When `native_tool_calls` non-empty and strong marker present: `EnvelopeIngestOutcome::Parsed` with `tool_calls` equal to native only; `visible_assistant_text` excludes marker region.

### 5.4 Kotlin — `ModelTurnStreamGate`

```kotlin
internal class ModelTurnStreamGate(
    port: ToolStreamGatePort,
    allowToolCall: Boolean,
) {
    suspend fun onProviderTextChunk(chunk: String, emit: suspend (String) -> Unit)
    suspend fun finish(hasNativeToolCalls: Boolean, emit: suspend (String) -> Unit = {}): String
    fun bufferedText(): String
}
```

### 5.5 Kotlin — provider `completeSingleTurn` contract

- **Input:** Provider streams each text delta to `onTextToken` **without** local holdback.
- **Output:** `AgentModelTurnResult.assistantText` = full raw buffered text from provider stream (same as today for BaseOpenAI).
- **Output:** `AgentModelTurnResult.toolCalls` = **native structured calls only** (never `ToolEnvelopeParserPort.parse` in inference).
- **Streaming UI:** Only `RustAgentEngineAdapter` may apply `ToolStreamGatePort` before emitting `AgentLoopEvent.TextToken`.

### 5.6 Artifact tool schema (unchanged steering)

- `append_blocks` schema properties: `artifactId`, `blocks`, `isComplete` (camelCase).
- Fuzzy parse accepts `artifact_id` / `is_complete` and coerces to camelCase in Rust `tool_parse`.

---

## 6. Ordered Implementation Tasks

| # | Task | Scenario IDs |
|---|------|----------------|
| T1 | Add `normalize_spaced_dsml` and `first_strong_tool_marker_start`; remove weak JSON regex from stream gate marker list; keep weak detection inside `extract_raw_json` only. | CL-SG-09, CL-TR-06, CL-TR-04, CL-MD-03 |
| T2 | Update `ToolStreamGate` to call strong-marker APIs only; verify `finish` semantics unchanged for DSML / `<tool_call` / `call:` / `[TOOL_CALLS]`. | CL-SG-01, CL-SG-02, CL-SG-03, CL-SG-04, CL-SG-08, CL-SG-09 |
| T3 | Implement `engine/leaked_tool_recovery.rs` with `resolve_model_turn_text` (native preference, parse-on-empty-native, allow=false passthrough). | CL-TR-01, CL-TR-02, CL-TR-03, CL-TR-04, CL-TR-05 |
| T4 | Refactor `envelope_ingest.rs` to use resolver; fix merge policy to prefer native tool calls. | CL-E-01, CL-E-02, CL-E-03, CL-TR-02 |
| T5 | Add `third_party/agent_core/tests/leaked_tool_recovery_test.rs` and extend `tool_stream_gate_test.rs`, `engine_envelope_retry_test.rs`, `tool_parse_integration.rs`. | All CL-SG-*, CL-TR-*, CL-E-* |
| T6 | Create `ModelTurnStreamGate.kt`; unit-test push/finish delegation to `ToolStreamGatePort`. | CL-K-01, CL-K-02 |
| T7 | Wire `RustAgentEngineAdapter` to gate `onTextToken` with `allowToolCall` from `FfiModelRequest`; drain trailing flush on turn complete. | CL-K-01, CL-K-03, CL-E-04 |
| T8 | Remove `ToolStreamGateSession` from `BaseOpenAiSdkInferenceService`, Anthropic, Google, Llama; delete `ToolStreamGateSession.kt`. | CL-K-04, CL-K-05, CL-MD-02 |
| T9 | Update inference tests (`BaseOpenAiSdkInferenceServiceTest`, `CompleteSingleTurnRawSubmitTest`) for raw provider streaming and zero inference-side `ToolStreamGatePort.push` calls. | CL-K-04, CL-K-06, CL-MD-02 |
| T10 | Add `LeakedToolRecoveryMutationTest.kt` (no `ToolStreamGateSession` in inference main; no `allowToolCall = false` gate hack in BaseOpenAI for holdback). | CL-MD-02, CL-MD-03 |
| T11 | Add `RustAgentEngineAdapterLeakTest.kt` with fake host emitting `call:append_blocks{...}` verifying zero `TextToken` bytes inside marker region. | CL-K-03, CL-TR-01 |
| T12 | Run UniFFI regeneration if Rust exports change; run validation commands (section 7). | All |

---

## 7. Validation Commands

```bash
# Rust — leak recovery + regressions
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_stream_gate_test
cargo test --manifest-path third_party/agent_core/Cargo.toml --test leaked_tool_recovery_test
cargo test --manifest-path third_party/agent_core/Cargo.toml --test engine_envelope_retry_test
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_parse_integration
cargo test --manifest-path third_party/agent_core/Cargo.toml

# Kotlin — agent bridge + inference
./gradlew :core:agent:testDebugUnitTest
./gradlew :feature:inference:testDebugUnitTest --tests "*.ModelTurnStreamGateTest"
./gradlew :feature:inference:testDebugUnitTest --tests "*.RustAgentEngineAdapterLeakTest"
./gradlew :feature:inference:testDebugUnitTest --tests "*.CompleteSingleTurnRawSubmitTest"
./gradlew :feature:inference:testDebugUnitTest --tests "*.LeakedToolRecoveryMutationTest"
./gradlew :core:domain:testDebugUnitTest --tests "*.GenerateArtifactParamsTest"

# CI-aligned before merge
./gradlew ktlintCheck
./gradlew detekt
./gradlew :feature:inference:assembleDebug
./gradlew :core:agent:assembleDebug
```

---

## 8. Drift and Escalation Rules

| Situation | Action |
|-----------|--------|
| Provider adds new leak format not covered by strong sentinels | Add parser + strong marker in `agent_core` only; add CL-SG/CL-TR scenario first (TDD). |
| False positive holdback reported in production | Tighten **strong** marker list; do not re-add weak JSON marker to stream gate. |
| Need provider-specific holdback in Kotlin | **Stop** — requires contract waiver and design revision; violates INFERENCE_RULES isolation + ticket goal. |
| Persisted history already contains leaked giants | Out of scope for primary fix; optional follow-up: one-shot DB sanitize script (explicit schema/ops approval required). |
| UniFFI binding drift after Rust changes | Run `./scripts/build-agent-bindings.sh`; commit generated `agent_core.kt` per `core/agent/README.md`. |
| Engine behavior change without scenario update | Block merge until `test_spec.md` scenario ID is added or updated in same PR. |

---

## 9. Self-Review Checklist (planning)

- Every behavior requirement maps to ≥1 scenario in `test_spec.md`.
- Every implementation task T1–T12 references scenario IDs.
- No `TBD` / `TODO` placeholders.
- camelCase steering + snake_case alias parsing explicitly covered (CL-TR-07, CL-TR-08).
- Mutation-defense scenarios CL-MD-01–CL-MD-03 fail if holdback is skipped or weak JSON is reintroduced to the stream gate.
- Data layer correctly scoped as non-primary.
