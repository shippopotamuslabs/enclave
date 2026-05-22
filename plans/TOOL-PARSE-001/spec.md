# TOOL-PARSE-001 — spec.md
# Feature: Canonical Tool-Call Parsing Migration to Rust `agent_core`

> Supersedes the previous Kotlin-only TOOL-PARSE-001 spec. This is a deliberate
> rewrite that moves canonical model-emitted tool-call parsing, JSON repair,
> schema coercion, and validation out of Android/Kotlin and into shared Rust
> `third_party/agent_core`, so Android and forthcoming desktop clients share
> identical behavior.

---

## 1. Feature Summary

Today, canonical parsing of model-emitted tool calls lives in Kotlin:

- `core/domain/.../ToolEnvelopeParser.kt` owns envelope detection (DSML, GLM,
  CDATA, `<tool_call>`, Mistral `[TOOL_CALLS]`, raw JSON), JSON repair, schema
  coercion, and validation.
- `feature/inference/.../AgentTurnTextGate.kt` calls
  `ToolEnvelopeParser.extractLocalToolEnvelope` to gate streamed assistant text
  and reify leaked tool calls.
- `feature/inference/.../LlamaInferenceServiceImpl.kt` calls
  `extractLocalToolEnvelopes` on buffered local-model output.
- `feature/inference/.../GoogleInferenceServiceImpl.kt` and
  `AnthropicInferenceServiceImpl.kt` call `buildArgumentsJson(toolName, map)`
  to canonicalize provider-native tool-call argument maps.
- `core/agent/.../LiteRtAgentPolicyBridge.kt` already exposes a thin call site
  for the existing Rust `repair_tool` UniFFI export, which only normalizes
  *names* and applies catalog schema-hint coercion to parsed JSON objects.

`agent_core` already owns a partial pipeline (`src/repair/mod.rs`,
`src/catalog.rs`, `resources/tool_catalog.json`) but it operates on
`RawToolCall { name, arguments_json }` after the Kotlin side has already
extracted the envelope.

This feature relocates the canonical pipeline. After migration:

```
raw model/provider output
  -> normalize safe delimiter confusables   (Rust)
  -> strip markdown fences & extract structured blocks from prose (Rust)
  -> parse provider/tool envelopes          (Rust: envelope.rs)
       - DSML <||DSML||tool_calls>
       - fullwidth DSML <｜｜DSML｜｜tool_calls>
       - DSML invoke/parameter blocks
       - GLM/OpenAI-ish <tool_call> JSON (object or array payload)
       - CDATA <![CDATA[<tool>{...}</tool>]]>
       - Mistral [TOOL_CALLS]
       - raw JSON object/array shapes
       - provider function_call / tool_calls compat shapes
  -> repair JSON-like argument payloads     (Rust: json_repair.rs)
  -> look up expected tool schema           (Rust: catalog.rs)
  -> coerce primitive/stringified values    (Rust: schema_coerce.rs)
  -> validate complete call                 (Rust: tool_parse/mod.rs)
  -> return structured success / partial recovery with repair metadata
     / structured failure                   (Rust: result.rs)
```

Kotlin `ToolEnvelopeParser` becomes a thin compatibility wrapper that
delegates to a UniFFI-exported Rust entry point. Kotlin provider services
pass provider-native arguments (or leaked-text envelopes) into the same Rust
pipeline. `AgentTurnTextGate` keeps the existing streaming hold-back behavior
but delegates parse semantics to Rust through the wrapper.

The migration is **behavior-preserving** for every case currently covered by
`ToolEnvelopeParserTest` and adds explicit handling for the additional
Forge-style edge cases enumerated in §5.

---

## 2. File Structure Table

| File Path | Status | Responsibility |
|---|---|---|
| `third_party/agent_core/src/tool_parse/mod.rs` | Create | Public Rust entry point. Defines `parse_tool_calls(raw: &str) -> ToolParseBatch`, `coerce_provider_arguments(tool_name: &str, args_json: &str) -> ToolParseField`, and `has_local_tool_contract(system_prompt: &str) -> bool`. Drives the pipeline: normalize → strip fences → envelope detect → JSON repair → schema lookup → coerce → validate. Re-exports submodule types. |
| `third_party/agent_core/src/tool_parse/envelope.rs` | Create | Envelope detection and extraction. Recognizes Mistral `[TOOL_CALLS]`, DSML (regular and fullwidth `｜`), `<tool_call>` (JSON object, JSON array, and GLM `<arg_key>/<arg_value>` form), CDATA `<![CDATA[<tool>…</tool>]]>`, and raw JSON shapes (`{"name":…,"arguments":…}` or `{"name":…,"parameters":…}`). Produces `Vec<ExtractedEnvelope { tool_name, arguments_raw, visible_prefix, visible_suffix }>` plus a `MalformedEnvelope` error variant when a marker is present but the payload is unparseable. Markdown-fence stripping, fullwidth delimiter folding, and `"parameters"`→`"arguments"` key normalization happen here. |
| `third_party/agent_core/src/tool_parse/json_repair.rs` | Create | Full-coverage JSON repair, feature-parity with Forge's `JsonRepairParser`. Entry points: `repair_json(input: &str) -> Result<serde_json::Value, JsonRepairError>` and `repair_json_value_inplace(value: &mut Value)`. Implements a character-at-a-time state-machine parser modelled on Forge's `crates/forge_json_repair/src/parser.rs`. Repair cases: (1) markdown code fence stripping (opening `\`\`\`` / `[\`\`\`` / `{\`\`\`` and closing variants); (2) trailing commas in objects and arrays; (3) leading commas in objects and arrays; (4) missing commas **between** values (both object entries and array elements); (5) missing colon between object key and value; (6) missing closing `}` or `]` at end of input; (7) missing object value → `null`; (8) redundant extra closing brackets stripped; (9) single-quote and backtick quote delimiters → double-quote; (10) smart/typographic quotes `'` `'` `”` `”` `´` → ASCII `”` or `'`; (11) non-ASCII whitespace (NBSP, thin-space, ideographic space etc.) → ASCII space; (12) unquoted object keys → quoted; (13) unquoted string values and bare URLs preserved and quoted; (14) Python-style keywords: `None` → `null`, `True` → `true`, `False` → `false`; (15) `undefined` → `null`; (16) `/* … */` and `// …` comments stripped; (17) ellipsis `…` / `...` in arrays/objects skipped; (18) string concatenation with `+` operator collapsed; (19) invalid/missing Unicode escape repair; (20) unescaped control characters (`\n` `\r` `\t` `\b` `\f`) inside string literals escaped; (21) unescaped inner double-quotes inside strings escaped with `\”`; (22) malformed bracket-prefixed URL strings (`”[https://x”` → `”https://x”`); (23) stringified-JSON `”{\”k\”:\”v\”}”` arguments unwrapped (Forge `Unparsed` analogue); (24) truncated/missing end-quote repaired; (25) newline-delimited JSON wrapped into an array; (26) invalid leading zeros in number literals quoted as strings; (27) regex literals quoted as strings; (28) function-call expressions (`foo(…)`) treated as their inner value. `JsonRepairError` variants: `UnexpectedEnd { position }`, `UnexpectedCharacter { character, position }`, `ObjectKeyExpected { position }`, `ColonExpected { position }`, `InvalidUnicodeCharacter { chars, position }`, `InvalidCharacter { character, position }`. |
| `third_party/agent_core/src/tool_parse/schema_coerce.rs` | Create | Schema-aware coercion. `coerce_to_schema(value: &mut Value, hint: &ToolSchemaHint) -> Value` and `try_parse_string_as_value(s: &str) -> Option<Value>`. Coercion cases modelled on Forge's `schema_coercion.rs`: (1) `"true"`/`"false"` (case-insensitive) → `bool`; (2) integer strings → `i64`/`u64`; (3) float strings → `f64`; (4) `"null"` (case-insensitive) → `Value::Null`; (5) empty string `""` → `Value::Null` when the catalog field is marked optional/nullable; (6) string → `array` when schema expects `array` — via 3-step parse: `serde_json::from_str` first, then `repair_json` fallback, then `extract_array_from_string` for strings with garbage prefix/suffix (`"[\"x\"]{\\n}"` → `["x"]`, multi-byte UTF-8 safe); (7) string → `object` when schema expects `object` — same 3-step parse; (8) **double-encoded JSON unwrapping**: when the parsed result is itself a JSON string, re-parse up to 4 times until a non-string is reached (handles `"\"[\\\"https://x.com\\\"]\"\""` → `["https://x.com"]`); (9) **Python-style string coercion**: when `serde_json::from_str` fails but `repair_json` succeeds on a single-quoted or unquoted string representation of an array/object (`"[{'key':'val'}]"` → `[{"key":"val"}]`); (10) **recursive array-item coercion**: when schema field is `array` with a typed `item_type`, apply coercion to each element after array construction; (11) catalog defaults injection — fields listed in `ToolSchemaHint.defaults` are inserted only if absent from the value; (12) unknown fields not in catalog schema are preserved as-is (no stripping). `extract_array_from_string(s: &str) -> Option<Value>` finds the first `[` in `s`, tries progressively shorter suffixes from the last valid char boundary to find a parseable array via `repair_json`, returns `None` if none found. Re-uses `FieldHint`/`ToolSchemaHint` from `src/catalog.rs`. |
| `third_party/agent_core/src/tool_parse/result.rs` | Create | Result types. `ToolParseField { tool_name, arguments_json, repaired: bool, repairs: Vec<String> }`, `ToolParseBatch { successes: Vec<ToolParseField>, failures: Vec<ToolParseFailure>, visible_prefix: String, visible_suffix: String }`, and `ToolParseFailure { reason: ToolParseFailureReason, original: String }`. `ToolParseFailureReason` is an enum of `MalformedEnvelope`, `InvalidJson { detail: String }`, `UnknownTool { name: String }`, `UnknownParameter { tool: String, param: String }`, `DuplicateParameter { tool: String, param: String }`, `MissingRequiredParameter { tool: String, param: String }`, `SchemaCoercionFailed { tool: String, param: String, expected: String }`. |
| `third_party/agent_core/src/lib.rs` | Modify | Add `pub mod tool_parse;` next to `pub mod repair;`. |
| `third_party/agent_core/src/ffi/types.rs` | Modify | Add UniFFI `Record` types `FfiToolParseField`, `FfiToolParseFailure`, `FfiToolParseFailureReason` (enum with associated string payloads), and `FfiToolParseBatch`. Provide `From`/`Into` conversions to/from the Rust `tool_parse::result` types. |
| `third_party/agent_core/src/ffi/policy_bridge.rs` | Modify | Add `#[uniffi::export] pub fn parse_tool_calls(raw: &str) -> FfiToolParseBatch`, `#[uniffi::export] pub fn coerce_provider_arguments(tool_name: &str, args_json: &str) -> FfiToolParseField`, and `#[uniffi::export] pub fn has_local_tool_contract(system_prompt: &str) -> bool`. Keep the existing `repair_tool`, `truncate_result`, `should_allow_tool`, and `result_json_looks_soft_failure` exports unchanged so the existing `AgentPolicyPort` is unaffected. |
| `third_party/agent_core/src/ffi/mod.rs` | Modify | Re-export the new free functions and FFI types alongside `repair_tool`. |
| `third_party/agent_core/Cargo.toml` | Modify | If not already enabled, ensure `regex` is a dependency (used by `envelope.rs`). No new external crate is added otherwise. |
| `third_party/agent_core/tests/tool_parse_integration.rs` | Create | Integration tests covering all scenarios `R-01` through `R-30` in `test_spec.md`. |
| `core/agent/src/main/kotlin/com/browntowndev/pocketcrew/core/agent/agent_core.kt` | Auto-regenerated | UniFFI-generated Kotlin bindings for the new free functions and records. Regenerated by the existing Rust build script — not hand-edited. |
| `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/ToolEnvelopeParserPort.kt` | Create | Pure-domain port interface (no Android/UniFFI types) exposing the canonical parser to domain code. Methods: `parse(raw: String): ToolEnvelopeBatch`, `coerceProviderArguments(toolName: String, argsJson: String): ToolEnvelopeField`, `hasLocalToolContract(systemPrompt: String?): Boolean`, and `buildToolResultMessage(resultJson: String): String`. |
| `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolEnvelopeBatch.kt` | Create | Pure-domain immutable data classes `ToolEnvelopeBatch`, `ToolEnvelopeField`, `ToolEnvelopeFailure`, and `ToolEnvelopeFailureReason` (sealed class) mirroring the Rust result types but free of UniFFI/Android types. |
| `core/agent/src/main/kotlin/com/browntowndev/pocketcrew/core/agent/RustToolEnvelopeParser.kt` | Create | Hilt-injected `@Singleton` adapter implementing `ToolEnvelopeParserPort`. Delegates `parse`, `coerceProviderArguments`, and `hasLocalToolContract` to the UniFFI exports. Maps UniFFI records to the domain model. `buildToolResultMessage(resultJson) = "<tool_result>$resultJson</tool_result>"`. |
| `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/util/ToolEnvelopeParser.kt` | Modify | Replace the canonical implementation with a thin facade that requires a `ToolEnvelopeParserPort` (obtained via a `setPort` test seam or DI accessor `Provider<ToolEnvelopeParserPort>`). All public methods (`extractLocalToolEnvelope`, `extractLocalToolEnvelopes`, `buildArgumentsJson(toolName, map)`, `buildArgumentsJson(query)`, `buildSearchChatArgumentsJson`, `extractRequiredUrls`, `requireSupportedTool`, `hasLocalToolContract`, `buildLocalToolResultMessage`, `stubResultJson`) keep identical signatures and semantics by delegating to the port. The 1000-line regex/state-machine body is removed. The shared `LocalToolEnvelope` data class is retained and is now populated from `ToolEnvelopeField` (mapping `toolName`, `argumentsJson`, `visiblePrefix`, `visibleSuffix`). |
| `core/agent/src/main/kotlin/com/browntowndev/pocketcrew/core/agent/di/AgentCoreModule.kt` (or existing Hilt module if present) | Modify | Add a `@Binds @Singleton fun bindToolEnvelopeParserPort(impl: RustToolEnvelopeParser): ToolEnvelopeParserPort` (creating the module if not present). |
| `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/AgentTurnTextGate.kt` | Modify | Inject `ToolEnvelopeParserPort` instead of importing `ToolEnvelopeParser`. The streaming hold-back regex list (`TOOL_MARKER_REGEXES`) is retained because it operates on partial stream chunks — not full envelopes. Parsing of buffered text goes through `port.parse(...).successes.firstOrNull()`. |
| `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LlamaInferenceServiceImpl.kt` | Modify | Replace `ToolEnvelopeParser.extractLocalToolEnvelopes(...)` with `toolEnvelopeParserPort.parse(buffered.fullText).successes`. Replace `ToolEnvelopeParser.buildLocalToolResultMessage(...)` with `toolEnvelopeParserPort.buildToolResultMessage(...)`. |
| `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/GoogleInferenceServiceImpl.kt` | Modify | Replace `ToolEnvelopeParser.buildArgumentsJson(name, args)` with `toolEnvelopeParserPort.coerceProviderArguments(name, jsonOf(args)).argumentsJson`. |
| `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/AnthropicInferenceServiceImpl.kt` | Modify | Replace the existing `canonicalToolArgumentsJson` body with a call to `toolEnvelopeParserPort.coerceProviderArguments(toolName, jsonOf(properties)).argumentsJson`. Existing private helpers (`parseToolInputJson`, etc.) remain. |
| `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/util/ToolEnvelopeParserTest.kt` | Modify | Keep every existing scenario. Add a `@BeforeEach` that injects a `FakeToolEnvelopeParserPort` (records calls, returns canned successes/failures) so the file becomes a contract test for the facade. Existing scenarios `TP-01` through `TP-20` and the `robust DSML …` / `robust provider parser …` blocks are preserved by configuring the fake to return the expected fields. |
| `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/util/FakeToolEnvelopeParserPort.kt` | Create | Pure-Kotlin fake adhering to the project's "Fakes over Mocks" rule. Records each `parse(...)` and `coerceProviderArguments(...)` call and returns programmable canned responses. |
| `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/AgentTurnTextGateTest.kt` | Modify | Re-wire to the `FakeToolEnvelopeParserPort`. Pre-existing scenarios remain unchanged; the fake returns the same `LocalToolEnvelope` payloads they previously expected. |
| `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/RustToolEnvelopeParserContractTest.kt` | Create | Drives the real `RustToolEnvelopeParser` adapter against the live UniFFI surface to confirm the Kotlin facade preserves Kotlin-observable behavior for the 26 Kotlin-side parser tests already in `ToolEnvelopeParserTest`. Loads the agent_core JNI library via the existing test bootstrap used by `ConversationManagerImplPolicyBridgeTest`. |

The pre-existing `core/agent/src/main/kotlin/.../LiteRtAgentPolicyBridge.kt`,
`AgentPolicyPort.kt`, `repair_tool` FFI export, and `tool_catalog.json` are
**not changed** by this feature.

---

## 3. API Contracts and Interfaces

### 3.1 Rust public API — `third_party/agent_core/src/tool_parse/mod.rs`

```rust
pub use envelope::{ExtractedEnvelope, EnvelopeError};
pub use json_repair::{repair_json, JsonRepairError};
pub use schema_coerce::coerce_to_schema;
pub use result::{
    ToolParseBatch,
    ToolParseField,
    ToolParseFailure,
    ToolParseFailureReason,
};

pub fn parse_tool_calls(raw: &str) -> ToolParseBatch;
pub fn coerce_provider_arguments(tool_name: &str, args_json: &str) -> ToolParseField;
pub fn has_local_tool_contract(system_prompt: &str) -> bool;
```

Semantics:

- `parse_tool_calls` runs the full pipeline. When no envelope marker is found
  anywhere in `raw`, it returns `ToolParseBatch::empty()` with `successes`
  empty, `failures` empty, and `visible_prefix == raw`.
- When an envelope marker is found but cannot be parsed, the batch contains
  a single `ToolParseFailure { reason: MalformedEnvelope, original: raw.into() }`
  and no successes.
- When envelopes are extracted, each one is repaired via `repair_json`, looked
  up in `ToolCatalog::global()`, coerced via `coerce_to_schema`, and validated.
  A passing envelope becomes a `ToolParseField`; a failing envelope becomes a
  `ToolParseFailure` with the most specific reason variant.
- `visible_prefix` / `visible_suffix` always reflect the text before the first
  envelope marker and after the last envelope marker respectively, with
  markdown code fences stripped.
- `coerce_provider_arguments` skips envelope detection: it treats `args_json`
  as already-extracted argument JSON, runs it through `repair_json` and
  `coerce_to_schema`, and returns the canonical argument string. If the tool
  name is unknown it falls back to round-tripping the JSON without coercion
  (matching the existing Kotlin `buildArgumentsJson(toolName, map)` fallback).
- `has_local_tool_contract` returns `true` iff `system_prompt` contains a
  local-tool marker (`<tool_call>`, `{"name"`, `<![CDATA[<tool`, `DSML`,
  `invoke name=`) *and* mentions the canonical name of at least one tool
  registered in `ToolCatalog::global()`.

### 3.2 Rust types — `third_party/agent_core/src/tool_parse/result.rs`

```rust
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct ToolParseBatch {
    pub successes: Vec<ToolParseField>,
    pub failures: Vec<ToolParseFailure>,
    pub visible_prefix: String,
    pub visible_suffix: String,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct ToolParseField {
    pub tool_name: String,
    pub arguments_json: String,
    pub repaired: bool,
    pub repairs: Vec<String>,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct ToolParseFailure {
    pub reason: ToolParseFailureReason,
    pub original: String,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
#[serde(tag = "kind", content = "data", rename_all = "snake_case")]
pub enum ToolParseFailureReason {
    MalformedEnvelope,
    InvalidJson { detail: String },
    UnknownTool { name: String },
    UnknownParameter { tool: String, param: String },
    DuplicateParameter { tool: String, param: String },
    MissingRequiredParameter { tool: String, param: String },
    SchemaCoercionFailed { tool: String, param: String, expected: String },
}
```

### 3.3 UniFFI surface — `third_party/agent_core/src/ffi/types.rs`

```rust
#[derive(uniffi::Record)] pub struct FfiToolParseBatch { …same fields as above… }
#[derive(uniffi::Record)] pub struct FfiToolParseField { …same fields as above… }
#[derive(uniffi::Record)] pub struct FfiToolParseFailure { reason: FfiToolParseFailureReason, original: String }

#[derive(uniffi::Enum)]
pub enum FfiToolParseFailureReason {
    MalformedEnvelope,
    InvalidJson { detail: String },
    UnknownTool { name: String },
    UnknownParameter { tool: String, param: String },
    DuplicateParameter { tool: String, param: String },
    MissingRequiredParameter { tool: String, param: String },
    SchemaCoercionFailed { tool: String, param: String, expected: String },
}
```

UniFFI exports in `ffi/policy_bridge.rs`:

```rust
#[uniffi::export] pub fn parse_tool_calls(raw: &str) -> FfiToolParseBatch;
#[uniffi::export] pub fn coerce_provider_arguments(tool_name: &str, args_json: &str) -> FfiToolParseField;
#[uniffi::export] pub fn has_local_tool_contract(system_prompt: &str) -> bool;
```

### 3.4 Kotlin pure-domain port — `ToolEnvelopeParserPort`

```kotlin
package com.browntowndev.pocketcrew.domain.port.inference

interface ToolEnvelopeParserPort {
    fun parse(raw: String): ToolEnvelopeBatch
    fun coerceProviderArguments(toolName: String, argsJson: String): ToolEnvelopeField
    fun hasLocalToolContract(systemPrompt: String?): Boolean
    fun buildToolResultMessage(resultJson: String): String
}
```

### 3.5 Kotlin pure-domain models — `ToolEnvelopeBatch.kt`

```kotlin
data class ToolEnvelopeBatch(
    val successes: List<ToolEnvelopeField>,
    val failures: List<ToolEnvelopeFailure>,
    val visiblePrefix: String,
    val visibleSuffix: String,
)

data class ToolEnvelopeField(
    val toolName: String,
    val argumentsJson: String,
    val repaired: Boolean,
    val repairs: List<String>,
)

data class ToolEnvelopeFailure(
    val reason: ToolEnvelopeFailureReason,
    val original: String,
)

sealed class ToolEnvelopeFailureReason {
    object MalformedEnvelope : ToolEnvelopeFailureReason()
    data class InvalidJson(val detail: String) : ToolEnvelopeFailureReason()
    data class UnknownTool(val name: String) : ToolEnvelopeFailureReason()
    data class UnknownParameter(val tool: String, val param: String) : ToolEnvelopeFailureReason()
    data class DuplicateParameter(val tool: String, val param: String) : ToolEnvelopeFailureReason()
    data class MissingRequiredParameter(val tool: String, val param: String) : ToolEnvelopeFailureReason()
    data class SchemaCoercionFailed(val tool: String, val param: String, val expected: String) : ToolEnvelopeFailureReason()
}
```

### 3.6 Kotlin facade — `ToolEnvelopeParser` (modified)

All currently-exposed methods keep their existing public signatures and
behavior. Their bodies become:

- `extractLocalToolEnvelope(text)` and `extractLocalToolEnvelopes(text)`:
  call `port.parse(text)`. Throw `IllegalStateException("Malformed tool_call envelope")`
  when `batch.failures` contains a `MalformedEnvelope` (or, for the DSML
  block-specific case, the failure message reads `Malformed DSML tool envelope`
  — see §3.7). Throw `IllegalArgumentException(message)` when the failure
  reason is `MissingRequiredParameter`, `UnknownTool`, `UnknownParameter`,
  or `DuplicateParameter`, with the message format defined in §3.7. Return
  `LocalToolEnvelope` records mapped from `ToolEnvelopeField`. The
  singular variant returns `null` only when the input has no envelope
  markers; otherwise it returns the first success or throws.
- `buildArgumentsJson(toolName, map)`: serializes `map` to a JSON string,
  calls `port.coerceProviderArguments(toolName, jsonString).argumentsJson`.
- `buildArgumentsJson(query)`: unchanged convenience that wraps
  `WebSearchParams(query)`.
- `extractRequiredUrls(argumentsJson)`: parses `argumentsJson` directly as
  JSON, falls back to `port.parse` with a synthetic
  `<tool_call>{"name":"web_extract","arguments":$argumentsJson}</tool_call>`
  wrapper, throws `IllegalArgumentException("Tool argument 'urls' is required and must be a non-empty array")`
  on miss. (Kept for source-compat with `LlmToolingOrchestrator` and the
  data-layer Anthropic translator.)
- `requireSupportedTool(name)`: unchanged — delegates to the Kotlin
  `ToolDefinition.fromName` registry (mirrors the catalog).
- `hasLocalToolContract(systemPrompt)`: delegates to
  `port.hasLocalToolContract(systemPrompt)`.
- `buildLocalToolResultMessage(resultJson)`: returns
  `port.buildToolResultMessage(resultJson)`.
- `stubResultJson(query)`: unchanged.

### 3.7 Error-message contract (Kotlin-observable)

The Rust side returns structured `ToolParseFailureReason` variants. The
Kotlin facade maps them to exceptions whose messages preserve the
already-asserted strings in `ToolEnvelopeParserTest`:

| `ToolEnvelopeFailureReason` | Exception type | Message format |
|---|---|---|
| `MalformedEnvelope` (non-DSML) | `IllegalStateException` | `"Malformed tool_call envelope"` |
| `MalformedEnvelope` (DSML block) | `IllegalStateException` | `"Malformed DSML tool envelope"` |
| `MissingRequiredParameter { tool, param }` | `IllegalArgumentException` | `"Tool argument '$param' is required"` (or, when `tool == web_extract && param == urls`, `"Tool argument 'urls' is required and must be a non-empty array"`) |
| `UnknownTool { name }` | `IllegalArgumentException` | `"Unsupported tool: $name"` |
| `UnknownParameter { tool, param }` | `IllegalArgumentException` | `"Unknown parameter '$param' for tool '$tool'"` |
| `DuplicateParameter { tool, param }` | `IllegalArgumentException` | `"Duplicate parameter '$param' for tool '$tool'"` |
| `InvalidJson { detail }` | `IllegalStateException` | `"Malformed tool_call envelope"` (existing Kotlin contract — invalid JSON inside an envelope reports as a malformed envelope) |
| `SchemaCoercionFailed { tool, param, expected }` | `IllegalArgumentException` | `"Tool argument '$param' is required"` (for the `array` → empty case) — full mapping table validated by tests `K-04`/`K-05`. |

The Rust failure variant for the DSML-specific malformed case is signaled
by `ExtractedEnvelope::detect_format == EnvelopeFormat::Dsml` propagating
into the failure; the FFI emits a marker string `"dsml"` inside
`FfiToolParseFailureReason::MalformedEnvelope { /* no payload */ }` is
augmented to a non-payload enum (the discrimination is carried through a
separate UniFFI record `FfiToolParseFailure.format_hint: Option<String>`
holding `Some("dsml")` for DSML blocks).

---

## 4. Data Model / Schema Changes

No database, Room schema, or persisted-config changes. The on-disk
`third_party/agent_core/resources/tool_catalog.json` is **not** modified.
All catalog data is consumed via the existing `ToolCatalog::global()`.

---

## 5. Coverage Matrix — Behaviors the Migration Must Preserve and Add

| ID | Behavior | Driven by scenario(s) |
|---|---|---|
| C-01 | DSML `<\|DSML\|tool_calls>` standard delimiters | R-01, K-01 |
| C-02 | DSML fullwidth `<｜｜DSML｜｜tool_calls>` delimiters | R-02, K-02 |
| C-03 | DSML invoke + parameter blocks with `string="true"` | R-03 |
| C-04 | DSML multiple invoke blocks inside one tool_calls block | R-04 |
| C-05 | DSML multiple tool_calls blocks in one response | R-05 |
| C-06 | DSML missing closing parameter tag → `Malformed DSML tool envelope` | R-06 |
| C-07 | DSML missing closing invoke tag → `Malformed DSML tool envelope` | R-07 |
| C-08 | DSML empty required-parameter body → `Tool argument '<param>' is required` | R-08 |
| C-09 | DSML unknown tool name → `Unsupported tool: <name>` | R-09 |
| C-10 | DSML unknown parameter name → `Unknown parameter '<param>' for tool '<tool>'` | R-10 |
| C-11 | DSML duplicate parameter name → `Duplicate parameter '<param>' for tool '<tool>'` | R-11 |
| C-12 | DSML missing required parameter when only an unknown one is present → unknown-parameter error takes precedence | R-12 |
| C-13 | DSML `string="true"` mismatched with array schema → repair to array | R-13 |
| C-14 | DSML malformed bracket-prefixed URL `["[https://x"]` → `["https://x"]` | R-14 |
| C-15 | DSML trailing comma inside JSON-like parameter | R-15 |
| C-16 | DSML preserves nested brackets, escaped quotes, and newlines inside string parameters | R-16 |
| C-17 | Markdown-fenced tool calls (```` ```xml ```` … ```` ``` ````) | R-17 |
| C-18 | Mistral `[TOOL_CALLS]` single-element | R-18 |
| C-19 | Mistral `[TOOL_CALLS]` multi-element parses both calls | R-19 |
| C-20 | Mistral sentinel present but no JSON array → `Malformed tool_call envelope` | R-20 |
| C-21 | Mistral with visible prefix text captured | R-21 |
| C-22 | `<tool_call>` wrapping JSON object | R-22 |
| C-23 | `<tool_call>` wrapping JSON array, multi-element | R-23 |
| C-24 | GLM `<tool_call> name <arg_key>k</arg_key> <arg_value>v</arg_value> </tool_call>` | R-24 |
| C-25 | CDATA `<![CDATA[<tool>…</tool>]]>` | R-25 |
| C-26 | Raw JSON `{"name":…,"arguments":…}` with surrounding prose | R-26 |
| C-27 | `"parameters"` key in payload → normalized to `"arguments"` | R-27 |
| C-28 | Single-quoted JSON payload `{'name': 'web_search', …}` → repaired to double-quoted | R-28 |
| C-29 | Smart quotes `“…”` inside JSON string values → ASCII `"` | R-29 |
| C-30 | Mutation defense — schema lookup is not bypassed (see §6) | R-30 |
| C-31 | Missing comma between object values `{"a":"b" "c":"d"}` → repaired | R-31 |
| C-32 | Missing comma between array elements `["a" "b"]` → repaired | R-32 |
| C-33 | Python keyword `None` → `null` | R-33 |
| C-34 | Python keywords `True` / `False` → `true` / `false` | R-34 |
| C-35 | `undefined` → `null` | R-35 |
| C-36 | Missing colon `{"key" "value"}` → `{"key":"value"}` | R-36 |
| C-37 | Leading comma in object `{, "k":"v"}` → `{"k":"v"}` | R-37 |
| C-38 | Truncated / missing end-quote on last string value → repaired | R-38 |
| C-39 | `/* … */` and `// …` comments stripped from JSON payload | R-39 |
| C-40 | String concatenation `"foo" + "bar"` collapsed to `"foobar"` | R-40 |
| C-41 | Newline-delimited JSON wrapped into a JSON array | R-41 |
| C-42 | `undefined` in array position → `null` | R-35 |
| C-43 | Python-style string array `"[{'key':'val'}]"` → real array when schema expects `array` | R-42 |
| C-44 | Array string with trailing garbage `"[\"x\"]{\\n}"` → clean array | R-43 |
| C-45 | Double-encoded JSON string `"\"[\\\"https://x.com\\\"]\""`→ `["https://x.com"]` | R-44 |
| C-46 | Empty string `""` → `null` for nullable/optional catalog field | R-45 |
| K-01 | Kotlin facade `extractLocalToolEnvelope` returns single envelope | K-01 |
| K-02 | Kotlin facade `extractLocalToolEnvelopes` returns multi envelopes | K-02 |
| K-03 | Kotlin facade preserves exact `Malformed tool_call envelope` / `Malformed DSML tool envelope` messages | K-03 |
| K-04 | Kotlin facade preserves exact `Tool argument '<param>' is required` message | K-04 |
| K-05 | Kotlin facade preserves exact `Unsupported tool: <name>` / `Unknown parameter…` / `Duplicate parameter…` messages | K-05 |
| K-06 | `buildArgumentsJson(toolName, map)` round-trips through Rust coercion (preserves `@SerialName`/default behavior or falls back to raw JSON for unknown tools) | K-06 |
| K-07 | `LlamaInferenceServiceImpl` maps `parse(text).successes` → `List<AgentRawToolCall>` | LI-01 |
| K-08 | `AgentTurnTextGate.leakedToolCalls` reads from the port, not from `ToolEnvelopeParser`'s old static body | LI-02 |
| K-09 | `RustToolEnvelopeParser` adapter throws no UniFFI-typed exception across the port boundary | LI-03 |

---

## 6. Mutation-Defense Strategy

The most plausible lazy implementation of `parse_tool_calls` is to:

1. Extract the envelope payload.
2. `serde_json::from_str` once.
3. Return the resulting JSON as `arguments_json` verbatim.

This would bypass the catalog lookup and schema coercion, which means
`web_extract` calls that arrive with `string="true"` (string scalar) instead
of an array would round-trip a string instead of being coerced to a
single-element array. Scenario `R-30` constructs a DSML `web_extract` call
whose `urls` parameter arrives as a JSON string (not a JSON array) and
asserts that the resulting `arguments_json` contains `"urls":["https://example.com"]`
*and* the catalog-default fields `"extract_depth":"basic"` and
`"format":"markdown"`. The lazy implementation would either emit
`"urls":"https://example.com"` (string scalar) or omit the catalog defaults,
failing the assertion.

A second mutation-defense scenario (`R-12`) sets a DSML `web_search` call
with an unknown parameter `extra` but no required `query`. A lazy
implementation that checks required-fields first and reports
`MissingRequiredParameter` for `query` would fail; the correct precedence is
to fail on the unknown parameter (matching the existing Kotlin contract
asserted by `robust DSML rejects missing required parameter`).

---

## 7. Ordered Implementation Tasks

Each task names the scenarios it satisfies. Scenarios are defined in
`test_spec.md`.

### Phase R — Rust pipeline

1. **R-T1 — Create `tool_parse/result.rs`.** Define `ToolParseField`,
   `ToolParseBatch`, `ToolParseFailure`, `ToolParseFailureReason`. Add
   `serde` round-trip unit tests.
   Verified by: R-01 — R-30 indirectly (all tests deserialize results).
2. **R-T2 — Create `tool_parse/json_repair.rs`.** Implement a full-coverage
   `JsonRepairParser` state machine with feature-parity with Forge's
   `crates/forge_json_repair/src/parser.rs`. Study that file directly — the
   algorithm is a Rust port of josdejong/jsonrepair. Implement all 28 repair
   cases listed in the `json_repair.rs` file-structure entry (§2). Do **not**
   copy Forge code verbatim; implement Pocket Crew-native Rust. Public entries:
   `repair_json(input: &str) -> Result<Value, JsonRepairError>` and
   `repair_json_value_inplace(value: &mut Value)`.
   Verified by: R-14, R-15, R-28, R-29, R-31, R-32, R-33, R-34, R-35,
   R-36, R-37, R-38, R-39, R-40, R-41.
3. **R-T3 — Create `tool_parse/schema_coerce.rs`.** Implement full-coverage
   `coerce_to_schema` modelled on Forge's `crates/forge_json_repair/src/schema_coercion.rs`
   but scoped to `ToolSchemaHint` rather than full JSON Schema. Implement
   all 12 coercion cases listed in the `schema_coerce.rs` file-structure
   entry (§2), including `try_parse_string_as_value` and
   `extract_array_from_string`. Study `forge_json_repair/src/schema_coercion.rs`
   directly for the 3-step parse strategy, double-encoded unwrapping loop,
   and multi-byte-safe garbage extraction algorithm. Re-use existing
   `coerce_field` logic from `repair/mod.rs` where applicable; do not
   delete the repair module (it stays for name fuzzy-matching).
   Verified by: R-13, R-30, R-42, R-43, R-44, R-45.
4. **R-T4 — Create `tool_parse/envelope.rs`.** Implement envelope detection
   in priority order: Mistral `[TOOL_CALLS]`, DSML (regular + fullwidth) via
   normalized delimiters, `<tool_call>` (array, then object, then GLM
   `<arg_key>/<arg_value>` form), CDATA, raw JSON. Strip markdown fences
   first. Normalize `"parameters"` to `"arguments"`. Return
   `Vec<ExtractedEnvelope>` plus an explicit `EnvelopeFormat::Dsml` marker
   on the failure path so the facade can re-format the error message.
   Verified by: R-01 through R-12, R-17 — R-27.
5. **R-T5 — Create `tool_parse/mod.rs`.** Wire the pipeline: normalize →
   envelope extract → json_repair → catalog resolve → schema_coerce → emit
   `ToolParseBatch`. Implement `parse_tool_calls`,
   `coerce_provider_arguments`, and `has_local_tool_contract`. Re-export
   submodule public types.
   Verified by: R-01 — R-30.
6. **R-T6 — Add `pub mod tool_parse;` to `lib.rs`.** No other change.
   Verified by: R-01 (Rust build green).
7. **R-T7 — Add UniFFI records and enum to `ffi/types.rs`.** Provide
   `From`/`Into` between `Ffi*` and `tool_parse::result::*`.
   Verified by: K-09 (no UniFFI exception leaks).
8. **R-T8 — Add UniFFI free-function exports in `ffi/policy_bridge.rs`**
   for `parse_tool_calls`, `coerce_provider_arguments`,
   `has_local_tool_contract`.
   Verified by: K-01, K-02, K-06, LI-03.
9. **R-T9 — Update `ffi/mod.rs`** to re-export the new exports and types.
   Verified by: K-01 (Kotlin facade can call them).
10. **R-T10 — Add `tests/tool_parse_integration.rs`** covering scenarios
    R-01 through R-30. Run `cargo test -p agent_core`.
    Verified by: R-01 — R-30.

### Phase K — Kotlin domain port & models

11. **K-T1 — Create `ToolEnvelopeBatch.kt`** with the four pure-Kotlin
    data classes and the sealed `ToolEnvelopeFailureReason` from §3.5.
    Verified by: K-01 — K-09 (compilation only).
12. **K-T2 — Create `ToolEnvelopeParserPort.kt`** with the four methods
    from §3.4. No Android types.
    Verified by: K-01 — K-09 (compilation only).

### Phase A — Adapter

13. **A-T1 — Create `RustToolEnvelopeParser.kt`** in `:core:agent`.
    Implement `ToolEnvelopeParserPort` by calling the UniFFI free
    functions; map UniFFI records to the domain types. Use
    `runCatching { … }.getOrElse { throw IllegalStateException("Rust tool parser unavailable", it) }`
    around each UniFFI call so callers never see UniFFI-typed exceptions.
    Verified by: LI-03.
14. **A-T2 — Bind the adapter in Hilt.** Add or extend the existing
    `:core:agent` Hilt module so `ToolEnvelopeParserPort` is provided.
    Verified by: K-07, K-08 (DI resolves at runtime).

### Phase F — Kotlin facade rewrite

15. **F-T1 — Rewrite `ToolEnvelopeParser.kt`** as a facade. Inject the
    port through a package-private `var portProvider: () -> ToolEnvelopeParserPort`
    seam: production assigns it during DI startup; tests assign a fake.
    Re-implement every existing public method per §3.6. Preserve the
    `LocalToolEnvelope` data class and the `Json` import only for
    `extractRequiredUrls` JSON parsing.
    Verified by: K-01 — K-06.
16. **F-T2 — Wire the production `portProvider`.** Add a startup hook in
    `:core:agent` DI that calls `ToolEnvelopeParser.installPort(impl)` so
    the facade resolves to `RustToolEnvelopeParser` at runtime.
    Verified by: K-07, K-08.

### Phase I — Inference call-site migration

17. **I-T1 — Migrate `LlamaInferenceServiceImpl`.** Inject
    `ToolEnvelopeParserPort` via Hilt. Replace the two `ToolEnvelopeParser`
    call sites: parsing (`extractLocalToolEnvelopes`) and tool-result
    wrapping (`buildLocalToolResultMessage`). Preserve the existing
    `AgentRawToolCall` mapping.
    Verified by: LI-01.
18. **I-T2 — Migrate `AgentTurnTextGate`.** Inject the port. Keep the
    streaming-hold-back regex list. Replace `ToolEnvelopeParser.extractLocalToolEnvelope`
    with `port.parse(text).successes.firstOrNull()` and adapt to
    `ToolEnvelopeField` instead of `LocalToolEnvelope` (preserve return
    type by adapting the existing fields).
    Verified by: LI-02.
19. **I-T3 — Migrate `GoogleInferenceServiceImpl`** to call
    `port.coerceProviderArguments(toolName, jsonOf(args)).argumentsJson`.
    Verified by: K-06.
20. **I-T4 — Migrate `AnthropicInferenceServiceImpl`'s
    `canonicalToolArgumentsJson`** to call
    `port.coerceProviderArguments(toolName, jsonOf(properties)).argumentsJson`.
    Verified by: K-06.

### Phase T — Tests

21. **T-T1 — Create `FakeToolEnvelopeParserPort.kt`** in
    `:core:domain` test source. Pure Kotlin, no MockK.
    Verified by: K-01 — K-09 (test fixture).
22. **T-T2 — Refactor `ToolEnvelopeParserTest`** to install the fake via
    `@BeforeEach` and configure canned responses for every existing
    scenario. All existing assertions remain unchanged.
    Verified by: K-01 — K-06.
23. **T-T3 — Refactor `AgentTurnTextGateTest`** to use the fake.
    Verified by: LI-02.
24. **T-T4 — Create `RustToolEnvelopeParserContractTest`** in
    `:feature:inference` test source. Spin up the JNI library, exercise
    the real adapter against representative inputs, assert
    `LocalToolEnvelope` parity.
    Verified by: LI-03.
25. **T-T5 — Run the full validation suite** listed in §8 and fix any
    regressions before declaring complete.
    Verified by: all scenarios.

---

## 8. Validation Commands (Before Declaring Complete)

```bash
cargo test --manifest-path third_party/agent_core/Cargo.toml
./gradlew :core:domain:testDebugUnitTest
./gradlew :feature:inference:testDebugUnitTest
./gradlew test
./gradlew detekt
./gradlew ktlintCheck
./gradlew :core:domain:assemble
./gradlew :feature:inference:assemble
./gradlew :app:assembleDebug
```

---

## 9. Drift & Escalation Rules

- **Contract drift:** if any step requires changing `:domain` to import
  Android, UniFFI, Compose, or framework types, **stop** and request a
  Contract Waiver. The pure-domain `ToolEnvelopeParserPort` and
  `ToolEnvelopeBatch` exist precisely to keep `:domain` clean.
- **Error-message drift:** the Kotlin-observable exception messages in
  §3.7 are part of the public contract — three pre-existing test classes
  (`ToolEnvelopeParserTest`, `AgentTurnTextGateTest`,
  `LlmToolingOrchestratorTest`) assert exact strings. Any change must
  re-author those tests in the same commit.
- **Catalog drift:** the Rust `tool_catalog.json` is the single source of
  truth for tool names. If a Kotlin call site asserts a tool name not
  present in the catalog, escalate before adding it ad-hoc — the catalog
  is shared with desktop clients.
- **Behavior parity break:** if any Rust-side decision (e.g., precedence
  between `UnknownParameter` and `MissingRequiredParameter`) diverges
  from the Kotlin contract, prefer the Kotlin precedence and document
  the choice in `tool_parse/mod.rs` so desktop clients inherit identical
  behavior.
- **Performance regression:** if Rust parsing is more than 2x slower
  than the Kotlin baseline on the existing
  `core/domain/src/test/.../ToolEnvelopeParserTest` corpus, flag and
  profile before merging. Functional parity takes precedence over micro
  performance for this migration.
- **UniFFI bindings regeneration:** if `core/agent/.../agent_core.kt`
  fails to regenerate, run the existing repo bootstrap as documented in
  `third_party/agent_core/README.md` and re-run gradle. Do not hand-edit
  the generated file.
