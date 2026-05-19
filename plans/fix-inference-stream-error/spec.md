# Fix: Inference Stream Error — Implementation Spec

**Ticket:** `fix-inference-stream-error`
**Date:** 2026-05-19
**Modules affected:** `:core:agent`, `:feature:inference`, `:core:domain`

---

## 1. Feature Summary

Three bugs conspire to make artifact creation silently fail with `event=Error` in logcat and no
actionable stack trace:

| # | Location | Symptom |
|---|----------|---------|
| Bug 1 | `RustAgentEngineAdapter` — `CallModel` catch block | Exception swallowed; only a bare `event=Error` appears in logcat. |
| Bug 2 | `BaseOpenAiSdkInferenceService.completeSingleTurn` | `strict=true` propagated unconditionally to OpenRouter/DeepSeek via the shared `toChatCompletionTool()` helper, which hardcodes `.strict(true)`. |
| Bug 3 | `ToolSchemaGenerator.generateSchemaForClass` | `List<JsonElement>` resolves to `"items": {}` — empty schema forbidden by OpenAI strict mode, causing the `append_blocks` tool to be rejected. |

The fix:

1. Log the exception (with stack trace) before emitting `AgentLoopEvent.Failed` in `RustAgentEngineAdapter`.
2. Add `protected open val useStrictToolSchemas: Boolean = false` to `BaseOpenAiSdkInferenceService`
   and honour it in `completeSingleTurn` when building `ChatCompletionFunctionTool`.
3. Fix `ToolSchemaGenerator.generateSchemaForClass` so that `JsonElement` / `JsonObject` / `JsonArray`
   item types emit `"items": { "type": "object" }` instead of `"items": {}`, making the schema
   unambiguous and accepted by providers that validate schemas.

No `strict=true` changes are made to any existing subclass at this time because the `append_blocks`
schema bug means no provider can safely use strict mode until Bug 3 is also fixed. The flag defaults
to `false` so all subclasses are safely non-strict unless they explicitly override.

---

## 2. File Structure Table

| File | Create / Modify | Responsibility |
|------|-----------------|----------------|
| `core/agent/src/main/kotlin/…/core/agent/RustAgentEngineAdapter.kt` | Modify | Add `loggingPort.error(TAG, …, e)` before the `send(AgentLoopEvent.Failed(…))` call in the `catch (e: Exception)` block of the `CallModel` handler. Add `companion object { private const val TAG = "RustAgentEngineAdapter" }`. |
| `feature/inference/src/main/kotlin/…/feature/inference/BaseOpenAiSdkInferenceService.kt` | Modify | Add `protected open val useStrictToolSchemas: Boolean = false`. Replace `it.toChatCompletionTool()` in `completeSingleTurn` with `it.toChatCompletionTool(strict = useStrictToolSchemas)`. |
| `feature/inference/src/main/kotlin/…/feature/inference/OpenAiRequestMapper.kt` | Modify | Add `strict: Boolean = false` parameter to `internal fun ToolDefinition.toChatCompletionTool()`. Pass it through to `FunctionDefinition.Builder.strict(strict)`. |
| `core/domain/src/main/kotlin/…/domain/model/inference/ToolParameters.kt` | Modify | In `generateSchemaForClass`, change the `JsonElement` / `JsonObject` / `JsonArray` early-return from `buildJsonObject {}` to `buildJsonObject { put("type", "object") }`. |
| `core/agent/src/test/kotlin/…/core/agent/RustAgentEngineAdapterCallModelExceptionTest.kt` | Create | Unit tests verifying that a model-turn exception both logs via `loggingPort.error` (with throwable) and emits `AgentLoopEvent.Failed("model turn failed: <message>")`. |
| `feature/inference/src/test/kotlin/…/feature/inference/StrictToolSchemaFlagTest.kt` | Create | Unit tests verifying the `useStrictToolSchemas` flag controls strict mode in `toChatCompletionTool` and that non-strict subclasses produce `strict=false` in the built params. |
| `core/domain/src/test/kotlin/…/domain/model/inference/ToolSchemaGeneratorTest.kt` | Modify | Add scenario verifying that `AppendBlocksParams.blocks` emits `"items": { "type": "object" }` rather than `"items": {}`. |

---

## 3. API Contracts and Interfaces

### 3.1 `toChatCompletionTool` signature (OpenAiRequestMapper.kt)

**Before:**
```
internal fun ToolDefinition.toChatCompletionTool(): ChatCompletionFunctionTool
```

**After:**
```
internal fun ToolDefinition.toChatCompletionTool(strict: Boolean = false): ChatCompletionFunctionTool
```

`FunctionDefinition.Builder.strict(strict)` receives the parameter directly. Callers that currently
call `toChatCompletionTool()` with no argument continue to compile unchanged and receive `strict=false`.

### 3.2 `BaseOpenAiSdkInferenceService` capability flag

```
protected open val useStrictToolSchemas: Boolean = false
```

Located immediately after the existing `protected open val maxImages: Int = 20` property.
`completeSingleTurn` replaces:
```
builder.addTool(it.toChatCompletionTool())
```
with:
```
builder.addTool(it.toChatCompletionTool(strict = useStrictToolSchemas))
```

No other callers of `toChatCompletionTool()` in production code exist at the time of writing
(verified: only `completeSingleTurn` and `OpenAiRequestMapper.mapToChatCompletionParams` call it;
`mapToChatCompletionParams` is on the non-agent legacy path and is not changed).

### 3.3 `RustAgentEngineAdapter` logging on exception

The catch block in the `CallModel` branch:

```
catch (e: Exception) {
    // ADDED:
    loggingPort.error(TAG, "model turn failed: ${e.message}", e)
    send(AgentLoopEvent.Failed("model turn failed: ${e.message}"))
    return@channelFlow
}
```

A `companion object { private const val TAG = "RustAgentEngineAdapter" }` is added to the class.
`loggingPort` is already a constructor-injected field; no new constructor parameters are needed.

### 3.4 `ToolSchemaGenerator.generateSchemaForClass` — item type fix

In the guard at the top of `generateSchemaForClass`:

```kotlin
// Before
if (klass == JsonElement::class || klass == JsonObject::class || klass == JsonArray::class) {
    return buildJsonObject { }
}

// After
if (klass == JsonElement::class || klass == JsonObject::class || klass == JsonArray::class) {
    return buildJsonObject { put("type", "object") }
}
```

This makes the `blocks` array emit `"items": { "type": "object" }` rather than `"items": {}`, which
is valid JSON Schema and accepted by providers that validate the tool schema.

---

## 4. Data Model / Schema Changes

No database columns or domain model fields are added. The only data-shape change is the
`append_blocks` tool schema emitted to the API:

| Property | Before | After |
|----------|--------|-------|
| `blocks.items` | `{}` | `{ "type": "object" }` |

This schema is computed at runtime in `ToolSchemaGenerator` and transmitted in API requests. No
Room entity, migration, or Kotlin serialization class changes are required.

---

## 5. Ordered Implementation Tasks

| Task | File(s) | Scenario IDs satisfied |
|------|---------|------------------------|
| **T-1** Add `companion object { TAG }` to `RustAgentEngineAdapter` | `RustAgentEngineAdapter.kt` | FSIE-01, FSIE-02, FSIE-06 |
| **T-2** Add `loggingPort.error(TAG, "model turn failed: ${e.message}", e)` before `send(AgentLoopEvent.Failed(…))` | `RustAgentEngineAdapter.kt` | FSIE-01, FSIE-02, FSIE-06 |
| **T-3** Write `RustAgentEngineAdapterCallModelExceptionTest` | New test file | FSIE-01, FSIE-02, FSIE-06 |
| **T-4** Add `strict: Boolean = false` parameter to `toChatCompletionTool()` | `OpenAiRequestMapper.kt` | FSIE-03, FSIE-04, FSIE-07 |
| **T-5** Add `protected open val useStrictToolSchemas: Boolean = false` to `BaseOpenAiSdkInferenceService` | `BaseOpenAiSdkInferenceService.kt` | FSIE-03, FSIE-04, FSIE-07 |
| **T-6** Replace `it.toChatCompletionTool()` with `it.toChatCompletionTool(strict = useStrictToolSchemas)` in `completeSingleTurn` | `BaseOpenAiSdkInferenceService.kt` | FSIE-03, FSIE-04, FSIE-07 |
| **T-7** Write `StrictToolSchemaFlagTest` | New test file | FSIE-03, FSIE-04, FSIE-07 |
| **T-8** Fix `generateSchemaForClass` to return `{ "type": "object" }` for `JsonElement` family | `ToolParameters.kt` | FSIE-05, FSIE-08 |
| **T-9** Add scenarios to `ToolSchemaGeneratorTest` verifying `blocks.items` is `{ "type": "object" }` | `ToolSchemaGeneratorTest.kt` | FSIE-05, FSIE-08 |
| **T-10** Run `./gradlew :core:agent:testDebugUnitTest :feature:inference:testDebugUnitTest :core:domain:testDebugUnitTest` | CI | All |
| **T-11** Run `./gradlew :feature:inference:assembleDebug :core:agent:assembleDebug :core:domain:assembleDebug` | CI | All |
| **T-12** Run `./gradlew ktlintCheck detekt` | CI | All |

Tasks T-1 through T-3 are independent of T-4 through T-7, which are in turn independent of T-8
through T-9. Tasks T-10 through T-12 depend on all preceding tasks.

---

## 6. Drift and Escalation Rules

- If a future provider subclass needs strict mode enabled, it must override `useStrictToolSchemas`
  to `true` AND verify that every tool in its tool-set produces a valid strict schema. The schema
  is validated at this point by `ToolSchemaGeneratorTest` for the known tool set.
- If `ToolDefinition.toChatCompletionTool` is called from any path other than `completeSingleTurn`,
  that call site must also be updated to pass the correct `strict` value.
- `mapToChatCompletionParams` in `OpenAiRequestMapper.kt` also calls `toChatCompletionTool()` (line
  98). Since it is on the non-agent legacy path (no strict mode contract), it retains `strict=false`
  (the new default) and requires no change.
- If `AppendBlocksParams.blocks` type is changed from `List<JsonElement>` to a typed sealed class,
  revisit `generateSchemaForClass` and the strict-mode schema tests.
