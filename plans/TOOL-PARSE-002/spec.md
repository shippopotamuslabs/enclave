# TOOL-PARSE-002 — spec.md
# Feature: Rust-Owned Stream Gate + Engine Envelope Recovery (Greenfield)

> **Depends on:** TOOL-PARSE-001 (canonical `parse_tool_calls` in `third_party/agent_core`).
> **Supersedes:** Kotlin-side leaked-envelope handling in `AgentTurnTextGate` and throw-on-parse-failure
> in the agent-loop path. No legacy paths, dual-parse, or compatibility shims.

---

## 1. Feature Summary

**Problem.** Canonical envelope parsing already lives in Rust (`tool_parse::parse_tool_calls`, UniFFI).
Kotlin still owns two behaviors that abort or short-circuit the Rust engine before it can recover:

1. **`AgentTurnTextGate`** (~165 LOC) — streaming holdback for partial DSML/tool markers so leaked syntax
   does not appear in chat during token streaming; also calls `ToolEnvelopeParserPort.parse` at turn end
   to reify leaked tools and **throws** on `ToolEnvelopeBatch.failures`.
2. **Inference `completeSingleTurn` paths** — merge `leakedToolCalls` into `AgentModelTurnResult.toolCalls`
   and submit partially cleaned assistant text. When parse fails, Kotlin throws → agent loop ends; when parse
   is skipped and the model returns `Stop` with empty native tools, the turn terminates with DSML still in
   session text — no Gemini-style “tell model and retry.”

**Rust engine today** already recovers from **post-parse repair rejects** (`RepairOutcome::Reject` → synthetic
tool-role error JSON → `next_action` → `CallModel`) and from **soft-failure streaks** via
`GuardrailState::consecutive_soft_failure_turns` and `tool_retry_limit` (default 3). It does **not** ingest
leaked envelopes from `assistant_text` or retry envelope parse failures.

**Target.** One shared implementation in `third_party/agent_core` for Android and a future Rust desktop client:

| Concern | Owner after migration |
|--------|------------------------|
| Streaming holdback (partial markers, 64-char tail) | Rust `tool_stream_gate` |
| Turn-end envelope detect / parse / reify | Rust `engine::submit_model_response` |
| Parse failure → model-facing error + `CallModel` retry | Rust engine (same JSON shape as repair reject) |
| Envelope failure streak → `allow_tool_call = false` at limit | Rust `GuardrailState` |
| Persisted assistant content | Stripped visible prefix/suffix only (no raw DSML) |
| Kotlin inference | Raw buffered text + **native** `tool_calls` only; stream gate via FFI |

**Non-goals:** Keeping `AgentTurnTextGate`, Kotlin throw-on-parse in agent loop, dual-parse fallback,
or re-parsing envelopes in Kotlin after engine submission.

---

## 2. File Structure Table

| File Path | Status | Responsibility |
|-----------|--------|----------------|
| `third_party/agent_core/src/tool_stream_gate/mod.rs` | **Create** | Stateful stream gate: buffer tokens, compute safe emit prefix, final flush for UI, expose full raw buffer for engine submit. No envelope parsing. |
| `third_party/agent_core/src/tool_stream_gate/markers.rs` | **Create** | Marker detection (`first_marker_start`, partial-prefix holdback, `TOOL_MARKER_HOLDBACK_CHARS = 64`). Port regex/angle-bracket logic from `AgentTurnTextGate.kt`. |
| `third_party/agent_core/src/tool_stream_gate/result.rs` | **Create** | `StreamGateFinish { trailing_flush, has_tool_marker }`, `PushResult { emitted_prefix }`. |
| `third_party/agent_core/src/lib.rs` | **Modify** | `pub mod tool_stream_gate;` |
| `third_party/agent_core/src/guardrails.rs` | **Modify** | Add `consecutive_envelope_parse_failures: u32`; include in `allow_tool_call`; reset rules on successful envelope tool execution or non-envelope model turn. |
| `third_party/agent_core/src/engine/envelope_ingest.rs` | **Create** | `ingest_model_response_envelopes(response) -> IngestOutcome` — calls `parse_tool_calls` when needed, builds `RawToolCall` list, strips DSML from assistant content, maps failures to `reject_json`-shaped errors. |
| `third_party/agent_core/src/engine/mod.rs` | **Modify** | `submit_model_response`: run envelope ingest before repair loop; on envelope-only failure path schedule reject-only synthetic turn (mirror repair reject); increment envelope streak; do not terminal-fail on parse failure while under `tool_retry_limit`. |
| `third_party/agent_core/src/tool_parse/envelope_error.rs` | **Create** | `envelope_failure_json(failure: &ToolParseFailure) -> String` — same top-level shape as `repair::reject_json` (`error`, `available_tools`). |
| `third_party/agent_core/src/ffi/types.rs` | **Modify** | `FfiToolStreamGate`, `FfiStreamGatePushResult`, `FfiStreamGateFinish`; optional `FfiEnvelopeParseFailure` event record if surfaced. |
| `third_party/agent_core/src/ffi/stream_gate.rs` | **Create** | UniFFI exports: `create_tool_stream_gate`, `tool_stream_gate_push`, `tool_stream_gate_finish`, `tool_stream_gate_buffered_text`, `tool_stream_gate_reset`. |
| `third_party/agent_core/src/ffi/mod.rs` | **Modify** | Re-export stream-gate symbols. |
| `third_party/agent_core/tests/tool_stream_gate_test.rs` | **Create** | Rust unit/integration tests for SG-XX scenarios. |
| `third_party/agent_core/tests/engine_envelope_retry_test.rs` | **Create** | Engine harness tests for E-XX scenarios (malformed DSML retry, streak limit, successful reify). |
| `core/domain/.../port/inference/ToolStreamGatePort.kt` | **Create** | Pure-domain port: `create(allowToolCall)`, `push(chunk)`, `finish(hasNativeToolCalls)`, `bufferedText()`, `reset()`. |
| `core/domain/.../model/inference/ToolStreamGateModels.kt` | **Create** | `ToolStreamGateHandle`, `ToolStreamGatePushResult`, `ToolStreamGateFinish`. |
| `core/domain/.../testFixtures/.../FakeToolStreamGatePort.kt` | **Create** | Fake for tests (Fakes over Mocks). |
| `core/agent/.../RustToolStreamGate.kt` | **Create** | `@Singleton` adapter implementing `ToolStreamGatePort` via UniFFI. |
| `core/agent/.../AgentModule.kt` | **Modify** | `@Binds` `ToolStreamGatePort` → `RustToolStreamGate`. |
| `core/agent/.../agent_core.kt` | **Regenerate** | UniFFI bindings (not hand-edited). |
| `feature/inference/.../BaseOpenAiSdkInferenceService.kt` | **Modify** | Replace `AgentTurnTextGate` with `ToolStreamGatePort`; `completeSingleTurn` returns raw `bufferedText()` + native tools only; remove `leakedToolCalls` merge. |
| `feature/inference/.../GoogleInferenceServiceImpl.kt` | **Modify** | Same as BaseOpenAI. |
| `feature/inference/.../AnthropicInferenceServiceImpl.kt` | **Modify** | Same as BaseOpenAI. |
| `feature/inference/.../LlamaInferenceServiceImpl.kt` | **Modify** | Same as BaseOpenAI for `completeSingleTurn` and streaming paths. |
| `feature/inference/.../InferenceFactoryImpl.kt` | **Modify** | Inject `ToolStreamGatePort` into provider services that stream assistant text. |
| `feature/inference/src/test/.../RustToolStreamGateContractTest.kt` | **Create** | JNI contract tests mirroring former `AgentTurnTextGateTest` streaming behavior (SG via Kotlin). |
| `feature/inference/src/test/.../CompleteSingleTurnRawSubmitTest.kt` | **Create** | Asserts `AgentModelTurnResult` submits raw buffer, native tools only (K-XX, MD-XX). |
| `core/agent/src/test/.../RustAgentEngineAdapterEnvelopeTest.kt` | **Create** | Optional adapter-level test that malformed envelope does not surface `AgentLoopEvent.Failed` from Kotlin throw. |
| `feature/inference/.../AgentTurnTextGate.kt` | **Delete** | Replaced by Rust `tool_stream_gate` + engine ingest. **No fallback.** |
| `feature/inference/.../ToolEnvelopeFailureMapper.kt` | **Delete** | Only consumer was `AgentTurnTextGate` throw path. **No fallback.** |
| `feature/inference/src/test/.../AgentTurnTextGateTest.kt` | **Delete** | Replaced by `tool_stream_gate_test.rs` + `RustToolStreamGateContractTest.kt`. |
| `feature/inference/src/test/.../AgentTurnTextGatePortContractTest.kt` | **Delete** | Obsolete (port contract was Kotlin gate → parser). |

**Unchanged but still used:** `ToolEnvelopeParserPort` / `RustToolEnvelopeParser` for
`coerceProviderArguments`, `hasLocalToolContract`, and `ToolEnvelopeParser` facade helpers —
**not** for agent-loop envelope ingest or streaming holdback.

---

## 3. API Contracts and Interfaces

### 3.1 Rust public API — `tool_stream_gate`

```rust
pub struct ToolStreamGate { /* opaque state */ }

impl ToolStreamGate {
    pub fn new(allow_tool_call: bool) -> Self;
    pub fn reset(&mut self);
    pub fn push(&mut self, chunk: &str) -> PushResult;
    pub fn finish(&mut self, has_native_tool_calls: bool) -> StreamGateFinish;
    pub fn buffered_text(&self) -> &str;
}

pub struct PushResult { pub emitted_prefix: String }
pub struct StreamGateFinish {
    pub trailing_flush: String,
    pub has_tool_marker: bool,
}
```

**Semantics (must match current `AgentTurnTextGate` observable behavior for streaming):**

- When `allow_tool_call == false`: every chunk is emitted immediately; `buffered_text` accumulates all input.
- When `allow_tool_call == true`: emit only the safe prefix before the earliest tool marker; hold back from
  the last ambiguous `<` within the 64-char tail window; support partial markers (`<|`, `<DSM`, `<tool_`, etc.).
- `finish(has_native_tool_calls: true)`: if buffer has **no** tool marker, flush remaining un-emitted text
  to `trailing_flush` (preamble before native tools). If buffer **has** a marker, do **not** flush marker
  region to UI (`trailing_flush` empty).
- `finish(has_native_tool_calls: false)`: flush only safe prefix before first marker; if marker present,
  `has_tool_marker = true` and do not emit marker body.
- **No** calls to `parse_tool_calls` in this module.

### 3.2 Rust engine — envelope ingest

```rust
pub enum EnvelopeIngestOutcome {
    NoMarkers,
    Parsed { tool_calls: Vec<RawToolCall>, visible_assistant_text: String },
    ParseFailed { error_json_for_model: String, visible_assistant_text: String },
}

pub fn ingest_model_response_envelopes(
    assistant_text: Option<&str>,
    native_tool_calls: &[RawToolCall],
) -> (EnvelopeIngestOutcome, Vec<RawToolCall> /* merged native + reified */);
```

**Rules:**

- If `native_tool_calls` non-empty **and** assistant text has no tool marker → skip parse; persist assistant text as-is.
- If assistant text contains a tool marker (same detector as `tool_stream_gate` / `tool_parse` envelope layer):
  - Run `parse_tool_calls(full_text)`.
  - **Success(es):** append reified `RawToolCall` entries (generated ids `text-tool-{n}`); persist
    `visible_prefix + visible_suffix` (trimmed); clear raw DSML from `AgentMessage.content`.
  - **Failure(s) with zero successes:** build `error_json_for_model` via `envelope_failure_json`;
    do **not** propagate failure to Kotlin; push synthetic `AgentRole::Tool` message(s); follow existing
    reject-only branch (count guardrail, `on_tool_turn_finished`, phase → `Idle` → next `CallModel`).
  - **Partial success:** use successes for repair pipeline; include failures as additional synthetic tool
    errors (same as multi-tool repair reject).
- Increment `guardrails.consecutive_envelope_parse_failures` on envelope **parse-failed** synthetic turn;
  reset to `0` when ingest yields at least one repaired+executed tool from reified envelope, or on any
  turn with no envelope marker.
- `allow_tool_call` in next `ModelRequest` is `false` when
  `consecutive_envelope_parse_failures >= config.tool_retry_limit` (default 3), same as soft-failure streak.

**Synthetic error JSON shape** (literal contract for tests):

```json
{"error":"<message>","available_tools":["web_search", "..."]}
```

`<message>` for `MalformedEnvelope` must include substring `Malformed` and, for DSML inputs,
`format_hint == "dsml"` must produce a message containing `DSML` (parity with Kotlin `ToolEnvelopeFailureMapper`).

### 3.3 UniFFI surface

```text
[Object] FfiToolStreamGate
constructor(allow_tool_call: bool)
fun push(chunk: string): FfiStreamGatePushResult
fun finish(has_native_tool_calls: boolean): FfiStreamGateFinish
fun buffered_text(): string
fun reset()
```

Regenerate via `./scripts/build-agent-bindings.sh`. No change to `parse_tool_calls` / `submit_model_response`
signatures — behavior change is internal to Rust engine.

### 3.4 Kotlin port changes

**`ToolStreamGatePort`** (new, `:core:domain`):

```kotlin
interface ToolStreamGatePort {
    fun create(allowToolCall: Boolean): ToolStreamGateHandle
}
interface ToolStreamGateHandle {
    fun push(chunk: String): ToolStreamGatePushResult
    fun finish(hasNativeToolCalls: Boolean): ToolStreamGateFinish
    fun bufferedText(): String
    fun reset()
}
```

**`AgentModelTurnResult` / `completeSingleTurn` contract (agent-loop path only):**

- `assistantText`: full raw `bufferedText()` from stream gate (may contain DSML); engine strips on ingest.
  May be `null` only when buffer is blank (unchanged empty-turn rules).
- `toolCalls`: **provider-native only** — never append Kotlin-reified leaked calls.
- `finishReason`: derived from provider native tools only (leaked DSML with `Stop` + empty native tools is
  valid; engine upgrades to tool/retry path).
- **Remove** all `toolEnvelopeParserPort.parse` calls from inference services on the agent-loop path.
- **Remove** `catch (IllegalArgumentException|IllegalStateException)` blocks that exist solely to map
  tool-envelope throws to `InferenceEvent.Error` in `completeSingleTurn` / rust-loop model turn
  (envelope errors are engine-internal).

**`ToolEnvelopeParserPort`:** unchanged methods; **not** invoked from streaming gate or `completeSingleTurn`.

---

## 4. Data Model Changes

| Location | Field / type | Change |
|----------|----------------|--------|
| `GuardrailState` | `consecutive_envelope_parse_failures: u32` | **Add**, default `0`. |
| `GuardrailState::allow_tool_call` | — | Also require `consecutive_envelope_parse_failures < tool_retry_limit`. |
| `AgentMessage.content` (assistant) | — | After ingest, DSML/tool markers removed when reified or failed; only visible prose retained on failure. |
| `AgentMessage.metadata` | optional `envelope_ingest: "failed"\|"reified"` | **Add** (serde on assistant message metadata) for debugging/tests only; no Room/Firestore schema change. |
| `AgentEvent` | optional `EnvelopeParseFailed { turn_index }` | **Add** if needed for parity with `ToolSoftFailure`; emit on synthetic envelope error. |
| Kotlin domain | `ToolStreamGateModels.kt` | **Add**; no DB entities. |

---

## 5. Ordered Implementation Tasks

### Phase A — Rust stream gate + engine (no Kotlin deletes yet)

| ID | Task | Verifies |
|----|------|----------|
| **T-A1** | Create `tool_stream_gate` + `markers.rs`; port holdback constants and regex behavior from `AgentTurnTextGate.kt`. | SG-01 … SG-08 |
| **T-A2** | Add `tool_stream_gate_test.rs` with chunked-stream cases. | SG-01 … SG-08 |
| **T-A3** | Add `envelope_failure_json` in `tool_parse/envelope_error.rs` reusing catalog tool list from `repair::reject_json`. | E-02, E-03 |
| **T-A4** | Extend `GuardrailState` with `consecutive_envelope_parse_failures` + unit tests. | E-06, E-07 |
| **T-A5** | Implement `engine/envelope_ingest.rs` and wire into `submit_model_response` before repair loop. | E-01 … E-07 |
| **T-A6** | Add `engine_envelope_retry_test.rs` (in-process engine + `RecordingExecutor`). | E-01 … E-07, MD-02 |
| **T-A7** | Run `cargo test --manifest-path third_party/agent_core/Cargo.toml`. | All E-, SG- |

### Phase B — UniFFI + Android bridge

| ID | Task | Verifies |
|----|------|----------|
| **T-B1** | Add `ffi/stream_gate.rs` + types; export from `lib.rs` / `ffi/mod.rs`. | K-01 |
| **T-B2** | Run `./scripts/build-agent-bindings.sh`; commit regenerated `agent_core.kt`. | K-01 |
| **T-B3** | Create `ToolStreamGatePort`, `RustToolStreamGate`, Hilt bind, `FakeToolStreamGatePort`. | K-01, K-02 |

### Phase C — Kotlin migration (parallel gate OK briefly; must delete before complete)

| ID | Task | Verifies |
|----|------|----------|
| **T-C1** | Wire `ToolStreamGatePort` into `BaseOpenAiSdkInferenceService`, Google, Anthropic, Llama (`sendPrompt` + `completeSingleTurn`). | K-01, K-03, SG-01 (Kotlin contract) |
| **T-C2** | Change `completeSingleTurn` to submit `bufferedText()` + native `toolCalls` only; remove `leakedToolCalls` / `finalToolCalls` merge. | K-03, K-04, MD-01 |
| **T-C3** | Remove envelope throw handling from agent-loop inference paths (`ToolEnvelopeFailureMapper` usages). | MD-04 |
| **T-C4** | Add `RustToolStreamGateContractTest` + `CompleteSingleTurnRawSubmitTest`. | K-01 … K-05, MD-01 |
| **T-C5** | Run `./gradlew :feature:inference:testDebugUnitTest :core:agent:testDebugUnitTest`. | K-, MD- |

### Phase D — Delete legacy Kotlin (required for completion)

| ID | Task | Verifies |
|----|------|----------|
| **T-D1** | **Delete** `AgentTurnTextGate.kt`. | MD-03 |
| **T-D2** | **Delete** `ToolEnvelopeFailureMapper.kt`. | MD-04 |
| **T-D3** | **Delete** `AgentTurnTextGateTest.kt`, `AgentTurnTextGatePortContractTest.kt`. | — |
| **T-D4** | Grep repo: zero references to `AgentTurnTextGate`, `leakedToolCalls`, `toInferenceException` in `feature/inference`. | MD-03, MD-04 |
| **T-D5** | Full CI-aligned validation (§8). | All scenarios |

---

## 6. Scenario ID Map (implementation → tests)

| Scenario IDs | Task(s) |
|--------------|---------|
| SG-01 … SG-08 | T-A1, T-A2, T-C1, T-C4 |
| E-01 … E-07 | T-A5, T-A6 |
| K-01 … K-05 | T-B3, T-C1 … T-C5 |
| MD-01 … MD-04 | T-A6, T-C2, T-C3, T-D1 … T-D4 |

---

## 7. Explicit Deletions (no fallback)

| Artifact | Action |
|----------|--------|
| `feature/inference/.../AgentTurnTextGate.kt` | **Delete** — do not deprecate or wrap. |
| `feature/inference/.../ToolEnvelopeFailureMapper.kt` | **Delete**. |
| `AgentTurnTextGateTest.kt`, `AgentTurnTextGatePortContractTest.kt` | **Delete** (coverage moves to Rust + contract test). |
| Kotlin `parseLeakedToolCalls` / throw on `batch.failures` | **Remove** from agent-loop path entirely. |
| `completeSingleTurn` `toolCalls + leakedToolCalls` merge | **Remove**. |
| Dual-parse (Kotlin parse + engine parse) | **Forbidden** — engine is sole ingest authority. |

---

## 8. Validation Commands (Before Declaring Complete)

```bash
cargo test --manifest-path third_party/agent_core/Cargo.toml
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_stream_gate_test
cargo test --manifest-path third_party/agent_core/Cargo.toml --test engine_envelope_retry_test

./scripts/build-agent-bindings.sh

./gradlew :core:domain:testDebugUnitTest
./gradlew :core:agent:testDebugUnitTest
./gradlew :feature:inference:testDebugUnitTest

./gradlew detekt
./gradlew ktlintCheck
./gradlew :app:assembleDebug
```

---

## 9. Drift and Escalation Rules

- **Contract waiver:** Any change that imports Android/UniFFI into `:core:domain` APIs (beyond ports) requires
  explicit **Contract Waiver**.
- **Single authority:** After T-D1, `parse_tool_calls` for agent-loop leaked envelopes must run only in
  `submit_model_response`. If a Kotlin call site still invokes `ToolEnvelopeParserPort.parse` on assistant
  turn text, **stop** and remove it (MD-01).
- **Error-shape drift:** Synthetic envelope errors must remain compatible with `is_soft_failure_result_json`
  (top-level `error` key). Do not reuse Kotlin exception strings in the agent-loop path.
- **Stream-gate drift:** Holdback window (64) and marker regex set must stay aligned between
  `tool_stream_gate` and `tool_parse` envelope detection; if markers diverge, extract shared `markers` module.
- **Guardrail drift:** `consecutive_envelope_parse_failures` and `consecutive_soft_failure_turns` are
  independent counters; do not merge without spec amendment.
- **Binding drift:** Never hand-edit `agent_core.kt`; regenerate via script.
- **Scope creep:** `ToolEnvelopeParser.extractLocalToolEnvelope` may remain for non-agent utilities/tests
  until a follow-up ticket; it must not be called from inference `completeSingleTurn` or stream gate.

---

## 10. Self-Review Checklist (plan author)

- [x] Every requirement mapped to task + scenario ID
- [x] Mutation-defense scenarios included (MD-01 … MD-04)
- [x] Engine retry + `allow_tool_call` at limit covered (E-01 … E-07)
- [x] Stream gate chunked DSML covered (SG-01 … SG-08)
- [x] Deletions explicit; no “keep as fallback” language
- [x] No runnable implementation code in this document
