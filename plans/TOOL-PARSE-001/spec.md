# TOOL-PARSE-001 — spec.md
# Feature: Fix ToolEnvelopeParser for Missing LLM Tool-Call Formats and Multi-Tool Threading

## 1. Feature Summary

`ToolEnvelopeParser` does not recognize several format variants emitted by popular open-weight GGUF
models. It also only returns a single `LocalToolEnvelope` per call, which prevents multi-tool
responses from being consumed end-to-end. This feature adds:

1. **Gap 1 (HIGH)** — Mistral `[TOOL_CALLS]` prefix: parse one or more JSON objects from the array
   following the sentinel.
2. **Gap 2 (MEDIUM-HIGH)** — `"parameters"` key normalization: canonicalize the key to
   `"arguments"` at payload-parse time so all downstream extraction functions continue working
   unchanged.
3. **Gap 3 (BUG)** — `hasLocalToolContract` DSML detection: add DSML marker checks.
4. **Gap 4 (LOW)** — `<tool_call>` wrapping a JSON array payload: parse all objects in the array.
5. **Gap 5 (MEDIUM)** — Multi-tool API and end-to-end wiring: new
   `extractLocalToolEnvelopes(text): List<LocalToolEnvelope>`, backward-compat single-result
   overload, and `LlamaInferenceServiceImpl` mapping the list to `List<AgentRawToolCall>`.

### agent_core Multi-Tool Safety Assessment

`turn_io.rs`, `action.rs`, `message.rs`, `session.rs`, and `guardrails.rs` confirm:

- `ModelResponse.tool_calls` is `Vec<RawToolCall>` — already multi-call by definition.
- `submit_model_response` iterates `response.tool_calls`, routes each through
  `ToolRepairPipeline::repair`, and accumulates `repaired: Vec<ToolCallRecord>`. No single-call
  assumption exists.
- `GuardrailState.count_tool_executed(n: u32)` already accepts a count `n`. The
  `parallel_tool_calls: usize` parameter in `on_tool_turn_finished` is multi-call aware.
- The `turn_io.rs` unit test `model_response_parallel_tool_calls` explicitly exercises a two-call
  response.

**Conclusion: the Rust engine is already multi-call safe. No Rust changes are required.**

The gap exists purely on the Kotlin side: `LlamaInferenceServiceImpl.completeSingleTurn` calls
`ToolEnvelopeParser.extractLocalToolEnvelope` (singular) and wraps the single result in
`listOf(...)`. Replacing that with `extractLocalToolEnvelopes` (plural) and mapping each envelope
to an `AgentRawToolCall` closes the end-to-end gap without touching Rust.

---

## 2. File Structure Table

| File Path | Status | Responsibility |
|---|---|---|
| `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/util/ToolEnvelopeParser.kt` | Modify | Add `extractLocalToolEnvelopes`, fix `hasLocalToolContract`, add Mistral parser, `parameters`-key normalization, array-payload `<tool_call>` handling |
| `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LlamaInferenceServiceImpl.kt` | Modify | Replace single-envelope call with multi-envelope call; map list → `List<AgentRawToolCall>` |
| `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/util/ToolEnvelopeParserTest.kt` | Modify | Add scenarios TP-01 through TP-20 |
| `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/LlamaInferenceServiceImplToolTest.kt` | Create | Add scenarios LI-01 through LI-05 covering multi-tool wiring (no Android deps, pure Kotlin) |

The `AgentTurnTextGate` caller retains its existing call to `extractLocalToolEnvelope` (singular)
— the backward-compat overload is unchanged so no edit is required there.

---

## 3. API Contracts and Interfaces

### 3.1 New public method on `ToolEnvelopeParser`

```kotlin
/**
 * Parses all tool-call envelopes from [text].
 *
 * Supports the following formats in priority order:
 *   1. DSML  (<|DSML| invoke name="...">)
 *   2. GLM   (<tool_call> name <arg_key>...</arg_key> <arg_value>...</arg_value> </tool_call>)
 *   3. CDATA (<![CDATA[<tool>...</tool>]]>)
 *   4. Mistral [TOOL_CALLS] followed by a JSON array
 *   5. <tool_call> wrapping either a JSON object or a JSON array
 *   6. Raw JSON object matching {"name":..., "arguments":...} or {"name":..., "parameters":...}
 *
 * Returns an empty list when no envelope is present.
 * Throws IllegalStateException("Malformed tool_call envelope") when an envelope marker is
 * detected but the payload cannot be parsed.
 * Throws IllegalArgumentException when the payload is valid but a required field is absent.
 *
 * The "parameters" key in any format is normalized to "arguments" before field extraction.
 */
fun extractLocalToolEnvelopes(text: String): List<LocalToolEnvelope>
```

### 3.2 Backward-compat overload (existing signature — do NOT change)

```kotlin
/**
 * Returns the first envelope from extractLocalToolEnvelopes, or null if the list is empty.
 * Preserves the exact existing error-propagation contract.
 */
fun extractLocalToolEnvelope(text: String): LocalToolEnvelope? =
    extractLocalToolEnvelopes(text).firstOrNull()
```

All existing callers (`AgentTurnTextGate.parseLeakedToolCall`,
`LlamaInferenceServiceImpl.completeSingleTurn` before the upgrade) continue to compile unchanged.

### 3.3 `parameters`-key normalization (private helper)

```kotlin
/**
 * If [payload] contains "parameters" as a top-level key and not "arguments",
 * replaces "parameters" with "arguments" so all downstream field-extraction functions
 * work without modification.
 *
 * Operates on the raw string before JSON parsing to handle both valid and partial JSON.
 * Uses a regex that matches only the top-level key to avoid corrupting nested content.
 */
private fun normalizeParametersKey(payload: String): String
```

Regex: `Regex(""""parameters"\s*:""")` replaced with `"arguments":`. Applied to each payload
string immediately after envelope extraction and before `TOOL_NAME_REGEX` runs.

Also update `extractRequiredUrls` to check `json["parameters"]?.jsonObject?.get("urls")` as a
third fallback path (after `json["urls"]` and `json["arguments"]?.jsonObject?.get("urls")`) so
that any payload that bypasses normalization still extracts correctly.

### 3.4 Mistral format private parser

```kotlin
/**
 * Detects and parses the Mistral [TOOL_CALLS] prefix.
 *
 * Returns a non-empty list when text.contains("[TOOL_CALLS]") and the JSON array
 * following the sentinel can be parsed.
 * Returns an empty list when the sentinel is absent.
 * Throws IllegalStateException("Malformed tool_call envelope") when the sentinel is present
 * but no valid JSON array follows.
 *
 * visiblePrefix for all returned envelopes is the text before [TOOL_CALLS], trimmed.
 * visibleSuffix for all returned envelopes is empty string.
 */
private fun parseMistralToolCalls(text: String): List<LocalToolEnvelope>
```

Implementation: locate the `[` character after the sentinel using a bracket-balanced scan
(analogous to `indexOfMatchingClosingBrace`) to find the closing `]`. Split the JSON array into
individual `{...}` objects. For each: apply `normalizeParametersKey`, extract `name` via
`TOOL_NAME_REGEX`, resolve `argumentsJson` via the existing tool-specific dispatch.

### 3.5 `<tool_call>` array-payload handling

Extended inside `parseWrappedToolCall` (or a peer helper `parseWrappedToolCallList`): when the
first non-whitespace character after `<tool_call>` is `[` rather than `{`, scan forward to the
matching `]`, split inner `{...}` objects, parse each one independently. The `envelopeRange`
covers from `<tool_call>` to `</tool_call>`. Callers inside `extractLocalToolEnvelopes` handle a
list return from this path.

### 3.6 `hasLocalToolContract` fix

```kotlin
fun hasLocalToolContract(systemPrompt: String?): Boolean =
    systemPrompt != null &&
        (systemPrompt.contains("<tool_call>") ||
         systemPrompt.contains("{\"name\"") ||
         systemPrompt.contains("<![CDATA[<tool") ||
         systemPrompt.contains("DSML") ||
         systemPrompt.contains("invoke name=")) &&
        ToolDefinition.ALL_TOOLS.any { systemPrompt.contains(it.name) }
```

### 3.7 `LlamaInferenceServiceImpl.completeSingleTurn` change

Before (lines 292–317):
```kotlin
val envelope = if (allowToolCall) {
    ToolEnvelopeParser.extractLocalToolEnvelope(buffered.fullText)
} else { null }
...
toolCalls = envelope?.let { listOf(AgentRawToolCall(...)) } ?: emptyList()
finishReason = if (envelope != null) AgentFinishReason.ToolUse else AgentFinishReason.Stop
assistantText = buffered.fullText.takeIf { it.isNotBlank() && envelope == null }
```

After:
```kotlin
val envelopes = if (allowToolCall) {
    ToolEnvelopeParser.extractLocalToolEnvelopes(buffered.fullText)
} else { emptyList() }
val toolCalls = envelopes.mapIndexed { index, envelope ->
    AgentRawToolCall(
        id = "llama-tool-${System.nanoTime()}-$index",
        name = envelope.toolName,
        argumentsJson = envelope.argumentsJson,
    )
}
...
toolCalls = toolCalls,
finishReason = if (toolCalls.isNotEmpty()) AgentFinishReason.ToolUse else AgentFinishReason.Stop,
assistantText = buffered.fullText.takeIf { it.isNotBlank() && toolCalls.isEmpty() }
```

---

## 4. Data Model / Schema Changes

None. `AgentRawToolCall`, `AgentModelTurnResult`, `LocalToolEnvelope`, and all Rust types are
unchanged.

---

## 5. Ordered Implementation Tasks

### Task T1 — `normalizeParametersKey` helper + `extractRequiredUrls` fallback (Gap 2)
**Satisfies:** TP-05, TP-06, TP-07, TP-08, TP-18

Add private `normalizeParametersKey(payload: String): String` to `ToolEnvelopeParser`. Apply it
inside the existing envelope dispatch in `extractLocalToolEnvelopes` immediately before
`TOOL_NAME_REGEX` extraction, so it applies regardless of format (CDATA, wrapped, raw).

Also add `json["parameters"]?.jsonObject?.get("urls")` as a third fallback in
`extractRequiredUrls` after the existing two paths.

### Task T2 — `parseMistralToolCalls` (Gap 1)
**Satisfies:** TP-01, TP-02, TP-03, TP-04, TP-08, TP-19

Add `parseMistralToolCalls(text: String): List<LocalToolEnvelope>`. Detection sentinel:
`text.contains("[TOOL_CALLS]")`. Extract the JSON array using a bracket-depth scanner, split into
objects, apply `normalizeParametersKey` to each, dispatch through the existing tool-specific
argument builder. Throw `IllegalStateException("Malformed tool_call envelope")` when the sentinel
is present but the array cannot be found or parsed.

### Task T3 — `<tool_call>` array-payload branch (Gap 4)
**Satisfies:** TP-09, TP-10, TP-20

Extend `parseWrappedToolCall`: when the first non-whitespace character after `<tool_call>` is `[`,
scan for the matching `]`, split inner `{...}` objects, parse each independently. Return all
results as a list. The `envelopeRange` covers the entire `<tool_call>...</tool_call>` span.

### Task T4 — `hasLocalToolContract` DSML check (Gap 3)
**Satisfies:** TP-11, TP-12

Extend the boolean expression to also accept `systemPrompt.contains("DSML")` or
`systemPrompt.contains("invoke name=")`.

### Task T5 — `extractLocalToolEnvelopes` multi-result API (Gap 5, part A)
**Satisfies:** TP-13, TP-14, TP-15, TP-16

Rename the current implementation body of `extractLocalToolEnvelope` to
`extractLocalToolEnvelopes`. Returns `List<LocalToolEnvelope>`. Priority order:
1. `parseMistralToolCalls` — if non-empty, return immediately.
2. DSML — returns single-element list or throws.
3. GLM — returns single-element list.
4. CDATA — returns single-element list.
5. Wrapped (object or array from T3) — returns one or more elements.
6. Raw JSON object — returns single-element list.
7. No marker detected — return `emptyList()`.

Rewrite `extractLocalToolEnvelope` as:
```kotlin
fun extractLocalToolEnvelope(text: String): LocalToolEnvelope? =
    extractLocalToolEnvelopes(text).firstOrNull()
```

### Task T6 — `LlamaInferenceServiceImpl` multi-tool wiring (Gap 5, part B)
**Satisfies:** LI-01, LI-02, LI-03, LI-04, LI-05

Update `completeSingleTurn` per §3.7. Each tool call id uses `"llama-tool-${System.nanoTime()}-$index"`.

### Task T7 — Tests: `ToolEnvelopeParserTest` extension
**Satisfies:** TP-01 through TP-20

Add scenarios to `core/domain/src/test/kotlin/.../ToolEnvelopeParserTest.kt`.

### Task T8 — Tests: `LlamaInferenceServiceImplToolTest` (new file)
**Satisfies:** LI-01 through LI-05

Create `feature/inference/src/test/kotlin/.../LlamaInferenceServiceImplToolTest.kt`. No Android
imports. Use hand-written fakes or MockK for JVM-only classes per existing `feature/inference` test
conventions.

---

## 6. Drift and Escalation Rules

| Condition | Action |
|---|---|
| A new GGUF model format not covered by these five gaps is reported | Open a new ticket; do not extend this plan |
| `extractLocalToolEnvelopes` performance degrades beyond 2 ms for a 4096-char response | Profile via JVM microbenchmark; escalate if regressing |
| A Mistral response contains nested `[TOOL_CALLS]` markers | `parseMistralToolCalls` handles only the first occurrence; escalate if nested patterns emerge |
| Any public API listed here conflicts with a concurrent branch | Get explicit approval before deviating; file a new ticket describing the conflict |
| `hasLocalToolContract` change causes false-positives in existing tests | Fix in tests, not by weakening detection logic |
