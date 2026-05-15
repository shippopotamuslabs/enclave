# Spec: LLM tool self-heal (orchestrator + local envelope)

## Objective

Harden the shared tool loop and local-model tool envelope parsing: bounded soft-failure behavior, clearer exhaustion signaling for parallel invalid tool turns, stable finished tool-call accounting for UI, and tolerant parsing of common malformed JSON.

## File Structure

| Action | Path | Responsibility |
|--------|------|------------------|
| Modify | `core/domain/.../LlmToolingOrchestrator.kt` | Soft-failure streak, parallel exhaustion warning, trimmed tool names, finished count excludes auto-finalize |
| Modify | `core/domain/.../ToolEnvelopeParser.kt` | Balanced JSON extraction for `<tool_call>`, payload normalization, smart-quote cleanup in extracted strings |
| Modify | `core/data/.../FirestoreDataSource.kt` | Repair broken `memoriesSyncAdapter` `toFirestore` / `fromFirestore` |
| Modify | Tests under `core/domain/src/test/...` | Already red; turn green with implementation |

## API Contracts & Interfaces

- `LlmToolingOrchestrator.execute` public signature unchanged.
- `onInferencePass(params, allowToolCall)` — `allowToolCall` becomes false after three consecutive turns where every tool in the turn returned a soft failure (executor returned JSON object containing an `error` field, no exception). Resets on any thrown failure during tool handling for that turn or any non-soft tool result in the turn.
- `onFinished(params, toolCallCount, lastResponse)` — `toolCallCount` excludes synthetic `append_blocks` auto-finalize executions (internal loop may still run them).
- `ToolEnvelopeParser.extractLocalToolEnvelope` — tolerates nested JSON in `<tool_call>...</tool_call>`, single-quoted pseudo-JSON, and normalizes curly double quotes in extracted argument strings.

## Config / Permissions Delta

None.

## Constitution Audit

Domain changes stay in `:core:domain` (pure Kotlin); data fix stays in `:core:data`. No schema changes.

## Implementation Tasks

### Task 1: Firestore memories adapter

**Files:** Modify `FirestoreDataSource.kt` (memories sync adapter).

**Change:** Restore valid `toFirestore` map and `fromFirestore` for `MemoriesEntity` after merge corruption.

**Verifies scenarios:** (build health — unblock ktlint/compile).

### Task 2: Tool envelope normalization

**Files:** Modify `ToolEnvelopeParser.kt`.

**Change:** Balanced-brace extraction for `<tool_call>` payloads; normalize single-quoted dict payloads to double-quoted JSON where safe; normalize `\u201c`/`\u201d` in extracted string fields used for `argumentsJson`.

**Verifies scenarios:** S-ENV-1, S-ENV-2.

### Task 3: Orchestrator self-heal

**Files:** Modify `LlmToolingOrchestrator.kt`.

**Change:** Track consecutive all-soft turns; drive `allowToolCall`; log `validation retry exhausted` when three consecutive turns each had ≥2 tools and all outcomes soft; trim `toolName` before validation/execute; do not increment finished `toolCallCount` for auto-finalize synthetic `append_blocks`.

**Verifies scenarios:** S-ORCH-1, S-ORCH-2, S-ORCH-3, S-ORCH-4, S-ORCH-5.
