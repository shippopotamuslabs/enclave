# Fix: Inference Stream Error — Test Specification

**Ticket:** `fix-inference-stream-error`

---

## 1. Test Framework and Run Commands

| Concern | Command |
|---------|---------|
| `:core:agent` unit tests (Bug 1) | `./gradlew :core:agent:testDebugUnitTest` |
| `:feature:inference` unit tests (Bug 2) | `./gradlew :feature:inference:testDebugUnitTest` |
| `:core:domain` unit tests (Bug 3) | `./gradlew :core:domain:testDebugUnitTest` |
| All three together | `./gradlew :core:agent:testDebugUnitTest :feature:inference:testDebugUnitTest :core:domain:testDebugUnitTest` |

Framework: JUnit 5 (`org.junit.jupiter.api`). Fakes preferred over mocks. MockK permitted only where
a fake is impractical (e.g., `OpenAIClient`). Coroutine tests use `runTest`. No Turbine required
for the specific scenarios below (all assertions are on return values or recording fakes, not Flows).

---

## 2. Scenario Index

| ID | Title | File |
|----|-------|------|
| FSIE-01 | Exception in model turn logs to `loggingPort.error` with throwable | `RustAgentEngineAdapterCallModelExceptionTest` |
| FSIE-02 | Exception in model turn emits `AgentLoopEvent.Failed` with correct message | `RustAgentEngineAdapterCallModelExceptionTest` |
| FSIE-03 | `BaseOpenAiSdkInferenceService` defaults `useStrictToolSchemas` to `false` | `StrictToolSchemaFlagTest` |
| FSIE-04 | `completeSingleTurn` passes `strict=false` to `toChatCompletionTool` when flag is `false` | `StrictToolSchemaFlagTest` |
| FSIE-05 | `ToolSchemaGenerator` emits `"items": {"type":"object"}` for `List<JsonElement>` | `ToolSchemaGeneratorTest` (extended) |
| FSIE-06 | Exception logging includes the original throwable, not just the message string | `RustAgentEngineAdapterCallModelExceptionTest` |
| FSIE-07 | Mutation defense — a subclass overriding `useStrictToolSchemas=true` produces `strict=true` in built params | `StrictToolSchemaFlagTest` |
| FSIE-08 | Mutation defense — reverting to `buildJsonObject {}` causes `blocks.items` to be empty | `ToolSchemaGeneratorTest` (extended) |

---

## 3. Scenarios

### FSIE-01 — Exception in model turn logs to `loggingPort.error` with throwable

**File:** `core/agent/src/test/kotlin/com/browntowndev/pocketcrew/core/agent/RustAgentEngineAdapterCallModelExceptionTest.kt`

```gherkin
Scenario: FSIE-01 — model turn exception is forwarded to loggingPort.error
  Given a RecordingLoggingPort that records every call to error(tag, message, throwable)
  And a RecordingAgentAdapter that mirrors the RustAgentEngineAdapter CallModel catch block
  And the host's completeModelTurn callback throws RuntimeException("upstream timeout")
  When the flow is collected
  Then RecordingLoggingPort.errorCalls contains exactly one entry
  And that entry's tag equals "RustAgentEngineAdapter"
  And that entry's message equals "model turn failed: upstream timeout"
  And that entry's throwable is the same RuntimeException instance that was thrown
```

**Test class note:** Because `RustAgentEngineAdapter` depends on the JNI `FfiAgentEngine` which
cannot be loaded on the JVM, this scenario is exercised through a thin `RecordingAgentAdapter`
test double that reproduces only the catch-block behavior of `RustAgentEngineAdapter`. The double
is a `channelFlow`-based Flow that mirrors the production catch block exactly: it calls
`loggingPort.error(TAG, "model turn failed: ${e.message}", e)` before emitting `Failed`.
This approach matches the existing `RustAgentEngineAdapterLeakTest` pattern.

---

### FSIE-02 — Exception in model turn emits `AgentLoopEvent.Failed` with correct message

**File:** `RustAgentEngineAdapterCallModelExceptionTest`

```gherkin
Scenario: FSIE-02 — model turn exception produces Failed event with verbatim message
  Given the same RecordingAgentAdapter from FSIE-01
  And completeModelTurn throws RuntimeException("upstream timeout")
  When the flow is collected to a list
  Then the collected event list contains AgentLoopEvent.Failed("model turn failed: upstream timeout")
  And the collected event list does NOT contain AgentLoopEvent.Finished
```

**Then literal values:**
- Event type: `AgentLoopEvent.Failed`
- Event message: `"model turn failed: upstream timeout"` (exact string)

---

### FSIE-06 — Exception logging includes the original throwable

**File:** `RustAgentEngineAdapterCallModelExceptionTest`

```gherkin
Scenario: FSIE-06 — loggingPort.error receives the non-null throwable
  Given completeModelTurn throws RuntimeException("network read timeout")
  When the flow is collected
  Then RecordingLoggingPort.errorCalls[0].throwable is not null
  And RecordingLoggingPort.errorCalls[0].throwable is an instance of RuntimeException
  And RecordingLoggingPort.errorCalls[0].throwable.message equals "network read timeout"
```

**Mutation defense target:** A lazy implementation that calls
`loggingPort.error(TAG, message)` (omitting the throwable overload) would produce a null throwable
in the recording port and fail this scenario.

---

### FSIE-03 — `BaseOpenAiSdkInferenceService` defaults `useStrictToolSchemas` to `false`

**File:** `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/StrictToolSchemaFlagTest.kt`

```gherkin
Scenario: FSIE-03 — base class declares useStrictToolSchemas as false
  Given a TestableBaseOpenAiSdkInferenceService that does not override useStrictToolSchemas
  When useStrictToolSchemas is read
  Then the value equals false
```

**Then literal value:** `false`

---

### FSIE-04 — `completeSingleTurn` passes `strict=false` when flag is `false`

**File:** `StrictToolSchemaFlagTest`

```gherkin
Scenario: FSIE-04 — completeSingleTurn builds ChatCompletionFunctionTool with strict=false when flag is false
  Given a TestableBaseOpenAiSdkInferenceService where useStrictToolSchemas == false
  And streamChatCompletions is overridden to capture ChatCompletionCreateParams and return a stub response
  And one ToolDefinition (ToolDefinition.WEB_SEARCH) is passed in availableTools
  And allowToolCall is true
  When completeSingleTurn is called
  Then the captured ChatCompletionCreateParams.tools() list has exactly 1 element
  And the tool's function().strict() is absent OR equals false
```

**Then literal values:**
- `tools().size == 1`
- `tools()[0].asFunction().function().strict()` is `Optional.empty()` or `Optional.of(false)`

---

### FSIE-07 — Mutation defense: subclass overriding `useStrictToolSchemas=true` produces `strict=true` in params

**File:** `StrictToolSchemaFlagTest`

```gherkin
Scenario: FSIE-07 (mutation defense) — service that overrides useStrictToolSchemas=true produces strict=true in tool params
  Given a HardcodedStrictService that extends BaseOpenAiSdkInferenceService
  And overrides useStrictToolSchemas to return true
  And streamChatCompletions captures ChatCompletionCreateParams
  And one ToolDefinition (ToolDefinition.WEB_SEARCH) is passed in availableTools
  And allowToolCall is true
  When completeSingleTurn is called
  Then tools()[0].asFunction().function().strict() equals Optional.of(true)
```

**Mutation defense rationale:** If a future implementor hardcodes `strict=false` unconditionally
inside `toChatCompletionTool`, FSIE-07 catches it — the flag-true override would still produce
`strict=false`, failing the assertion. FSIE-04 catches the opposite lazy revert (hardcoding
`strict=true` ignoring the flag).

---

### FSIE-05 — `ToolSchemaGenerator` emits `"items": {"type":"object"}` for `List<JsonElement>`

**File:** `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolSchemaGeneratorTest.kt` (extended)

```gherkin
Scenario: FSIE-05 — AppendBlocksParams blocks property emits items with type object
  Given ToolSchemaGenerator.generateSchema(AppendBlocksParams::class)
  When the schema is converted to JsonObject via toJsonObject()
  And properties["blocks"] is inspected
  Then properties["blocks"]["type"] equals "array"
  And properties["blocks"]["items"] is a JsonObject
  And properties["blocks"]["items"]["type"] equals "object"
  And properties["blocks"]["items"] does NOT equal an empty JsonObject
```

**Then literal values:**
- `properties["blocks"]["type"]` == `"array"`
- `properties["blocks"]["items"]["type"]` == `"object"`
- `properties["blocks"]["items"].size` >= 1 (not empty)

---

### FSIE-08 — Mutation defense: empty items schema is detected

**File:** `ToolSchemaGeneratorTest` (extended)

```gherkin
Scenario: FSIE-08 (mutation defense) — blocks items schema contains at least the type key
  Given ToolSchemaGenerator.generateSchema(AppendBlocksParams::class)
  When properties["blocks"]["items"] is inspected
  Then properties["blocks"]["items"].entries is NOT empty
  And properties["blocks"]["items"].containsKey("type") is true
```

**Mutation defense rationale:** The most plausible lazy implementation leaves
`generateSchemaForClass` returning `buildJsonObject {}` for `JsonElement`, producing
`"items": {}`. FSIE-05 asserts `items["type"] == "object"` (fails if items is empty), and FSIE-08
asserts `items.entries` is not empty (also fails if items is empty). Together they catch the revert.

---

## 4. Test Helper Classes

### `RecordingLoggingPort` (new, `core/agent/src/test/kotlin/…/core/agent/`)

```
data class ErrorCall(val tag: String, val message: String, val throwable: Throwable?)

class RecordingLoggingPort : LoggingPort {
    val errorCalls: MutableList<ErrorCall> = mutableListOf()
    override fun debug(tag: String, message: String) {}
    override fun info(tag: String, message: String) {}
    override fun warning(tag: String, message: String) {}
    override fun error(tag: String, message: String, throwable: Throwable?) {
        errorCalls += ErrorCall(tag, message, throwable)
    }
    override fun recordException(tag: String, message: String, throwable: Throwable) {}
}
```

### `RecordingAgentAdapter` (new, inline in test class)

A `channelFlow`-based stand-in that:
1. Invokes the host's `completeModelTurn`.
2. Catches exceptions (excluding `CancellationException`) and calls
   `loggingPort.error(TAG, "model turn failed: ${e.message}", e)`, then
   `send(AgentLoopEvent.Failed("model turn failed: ${e.message}"))`.

This mirrors the `RustAgentEngineAdapter` catch block without requiring the JNI library, following
the pattern established in `RustAgentEngineAdapterLeakTest`.

### `TestableBaseOpenAiSdkInferenceService` (reuse or extend existing test double)

For FSIE-03 and FSIE-04: use or extend the existing test double pattern in
`BaseOpenAiSdkInferenceServiceTest` with a `TestableSingleTurnService` that captures
`ChatCompletionCreateParams` passed to `streamChatCompletions`.

For FSIE-07: create an inline `HardcodedStrictService` that overrides
`useStrictToolSchemas = true`.

---

## 5. Scenario-to-Task Cross-Reference

| Scenario | Satisfied by Task |
|----------|--------------------|
| FSIE-01 | T-1, T-2, T-3 |
| FSIE-02 | T-1, T-2, T-3 |
| FSIE-06 | T-1, T-2, T-3 |
| FSIE-03 | T-5, T-7 |
| FSIE-04 | T-4, T-5, T-6, T-7 |
| FSIE-07 | T-4, T-5, T-6, T-7 |
| FSIE-05 | T-8, T-9 |
| FSIE-08 | T-8, T-9 |
