# Spec: sidecar-dedupe-model-wire

**Ticket ID:** `sidecar-dedupe-model-wire`  
**Module scope:** `:feature:inference` only (no `:core:domain` schema changes; no FFI `cacheHints` removal)  
**Grounding:** `ticket.md`, `research.md`, `design.md` (approved 2026-06-17), `structure.md` (approved 2026-06-17)

---

## 1. Feature Summary

Reduce BYOK provider prompt-cache churn and token waste by:

1. **Dedupe gate (D1, D2):** Ship context-management sidecars on the wire only when the built string differs from `lastShippedSidecarText` (exact match). Freeze only when the resolved live sidecar is non-null.
2. **Model-only sidecar text (D3–D6, D8):** Remove engine/host noise (`Cache hints:`, header, `|fresh|/|stale|`, `|turn:N|` bracket segment) from `buildContextSidecars()` output. Preserve `cacheHints` on the host callback / FFI path; log at DEBUG with `PROMPT_CACHE` tag (OQ-3).
3. **Anthropic/Google frozen replay (D9, OQ-1 C):** Index-interleaved replay via `SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex` — frozen bytes stay on the session row where they were committed, not rolled onto the latest user.

**Non-goals:** OpenAI-family `OpenAiCompatibleVolatilePlacement` mechanics, pre-retrieval one-shot freeze, `LlamaInferenceServiceImpl`, Room/schema, Rust TTL enforcement changes.

---

## 2. Verified Symbols and Paths

| Symbol / path | Location | Status |
|---------------|----------|--------|
| `buildContextSidecars` | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceService.kt` | Exists |
| `formatContextReferenceLine` | Same file | Exists |
| `parseTurnFromTitle`, `isStaleContextReference` | Same file | Exists (remove from wire path) |
| `CONTEXT_REFERENCE_POLICY_PREAMBLE` | Same file L88–90 | Exists |
| `ProviderAppendOnlyContext` | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/ProviderAppendOnlyContext.kt` | Exists |
| `endTurnFreezeSidecar`, `frozenSidecarsForWireReplay` | Same file | Exists |
| `resolveContextSidecarsForWire`, `lastShippedSidecarText` | Same file | **New** |
| `SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex` | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/SessionIndexVolatilePlacement.kt` | Exists (unused in A/G production) |
| `BaseOpenAiSdkInferenceService.completeSingleTurn` | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/BaseOpenAiSdkInferenceService.kt` | Exists |
| `isVolatileOrEngineSystemMessage` | Same file L960–983 | Exists |
| `AnthropicInferenceServiceImpl.completeSingleTurn` | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicInferenceServiceImpl.kt` | Exists |
| `GoogleInferenceServiceImpl.completeSingleTurn` | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleInferenceServiceImpl.kt` | Exists |
| `VolatileTurnContextWireSupport.combinedLatestUserPrepends` | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/VolatileTurnContextWireSupport.kt` | Exists (retire for context sidecars on A/G) |
| `ContextSidecarCompaction` | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/ContextSidecarCompaction.kt` | Unchanged |
| `RustLoopInferenceServiceTest` | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceServiceTest.kt` | Exists |
| `ProviderAppendOnlyContextTest` | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/appendonly/ProviderAppendOnlyContextTest.kt` | Exists |
| `AppendOnlyOpenAiSidecarPlacementTest` | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/AppendOnlyOpenAiSidecarPlacementTest.kt` | Exists |
| `PromptCachingTest` | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/PromptCachingTest.kt` | Exists |
| `XaiPromptCacheLiveContextTest` | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/XaiPromptCacheLiveContextTest.kt` | Exists |
| `XaiPromptCacheToolAndSessionStabilityTest` | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/XaiPromptCacheToolAndSessionStabilityTest.kt` | Exists |

---

## 3. Vertical Slice Execution Order

| Slice | Goal | Checkpoint command |
|-------|------|-------------------|
| **1** | Model-only sidecar format | `./gradlew :feature:inference:testDebugUnitTest --tests "*RustLoopInferenceServiceTest*"` |
| **2** | OpenAI-family dedupe + conditional freeze | `./gradlew :feature:inference:testDebugUnitTest --tests "*ProviderAppendOnlyContextTest*" --tests "*AppendOnlyOpenAiSidecarPlacementTest*" --tests "*XaiPromptCacheToolAndSessionStabilityTest*"` |
| **3** | Anthropic/Google index-interleaved replay + dedupe | `./gradlew :feature:inference:testDebugUnitTest --tests "*PromptCachingTest*"` |
| **4** | Volatile detection + full module regression | `./gradlew :feature:inference:testDebugUnitTest` and `./gradlew detekt` |

---

## 4. Public Contracts (frozen — do not redesign)

### 4.1 `resolveContextSidecarsForWire(built: String?): String?` (D1)

- Input `built`: output of `buildContextSidecars` (may be `null` or blank).
- Returns non-null only when `built` is non-blank **and** `built != lastShippedSidecarText`.
- On non-null return: sets `lastShippedSidecarText = built`.
- On null return (unchanged or blank built): does not mutate `lastShippedSidecarText`.
- Callers gate live injection and `endTurnFreezeSidecar` / `freezeContextSidecarIfPresent` on non-null result only.

### 4.2 Session reset (D2)

| Method | Clears `lastShippedSidecarText` |
|--------|--------------------------------|
| `clearLogState(reason)` | yes |
| `invalidate()` | yes (via `clearLogState`) |
| `markTurnBoundaryForReanchor()` | no |

### 4.3 `buildContextSidecars` return shape (D3, D5, D6, OQ-2)

**Emits (in order):**

1. `CONTEXT_REFERENCE_POLICY_PREAMBLE` including TTL line: `Tool-output references expire after 2 user turns.`
2. Optional network status (offline / back-online)
3. Optional `References:` block with simplified lines
4. Optional `State ledger JSON: …`, `Tool context index JSON: …`, `File context index JSON: …`

**Never emits:** `Context management sidecars:`, `Cache hints:`, `|fresh|`, `|stale|`, `|turn:N|` inside reference kind brackets.

**Returns `null` when:** references empty, compacted JSON blobs empty, network status blank — preamble alone does not force non-null. `cacheHints` excluded from emptiness check.

**Reference line shape:**

```
- [TOOL_OUTPUT] tool-output-call-1 (200b): tool_output:call-1 @turn:2
  preview: "BEGIN_RAW"
  read_context_reference("tool-output-call-1")
```

### 4.4 A/G index-interleaved replay (D9)

For each `AgentLoopMessage` at session index `i`:

```
prependTexts = SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex(
  sessionIndex = i,
  frozenSidecars = appendOnly.frozenSidecarsForWireReplay(),
  preRetrievalSidecarText = …,
  preRetrievalInsertIndex = …,
  currentSidecarText = resolveContextSidecarsForWire(built),
  tailInsertIndex = SessionIndexVolatilePlacement.resolveTailInsertIndex(sessionMessages),
)
```

Prepend each text in `prependTexts` to that row's content only. Never aggregate all frozen texts onto the latest user row.

---

## 5. Implementation Tasks

### Slice 1 — Model-only sidecar format

#### TASK-S1-01 — Extend preamble with TTL line

**File:** `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceService.kt`  
**Change:** Append to `CONTEXT_REFERENCE_POLICY_PREAMBLE`: ` Tool-output references expire after 2 user turns.` (single clear sentence per OQ-2).  
**Scenarios:** `SC-FMT-01`, `SC-FMT-02`

#### TASK-S1-02 — Simplify `formatContextReferenceLine`

**File:** `RustLoopInferenceService.kt`  
**Change:** Signature becomes `fun formatContextReferenceLine(reference: AgentContextReference): String`. Emit `- [${kind}] ${id} (${byteLen}b): ${title}` plus preview and retrieval hint lines. Remove turn/freshness bracket segments.  
**Scenarios:** `SC-FMT-01`, `SC-FMT-02`, `SC-FMT-MUT`

#### TASK-S1-03 — Remove wire-only turn/TTL helpers

**File:** `RustLoopInferenceService.kt`  
**Change:** Delete `parseTurnFromTitle`, `isStaleContextReference`, and `contextReferenceTurnSuffix` if unused. Remove `currentTurnIndex` parameter from `buildContextSidecars` and its call site in `completeModelTurn`.  
**Scenarios:** `SC-FMT-02`, `SC-FMT-MUT`

#### TASK-S1-04 — Strip engine noise from `buildContextSidecars`

**File:** `RustLoopInferenceService.kt`  
**Change:** Remove `Context management sidecars:` header line and `Cache hints:` append. Do not pass `cacheHints` into string assembly.  
**Scenarios:** `SC-FMT-01`, `SC-FMT-03`, `SC-FMT-04`, `SC-FMT-MUT`

#### TASK-S1-05 — Add `logCacheHintsIfPresent`

**File:** `RustLoopInferenceService.kt`  
**Change:** `private fun logCacheHintsIfPresent(cacheHints: List<String>)` — when non-empty, log at DEBUG with tag `PROMPT_CACHE` and joined hint text. Call from `completeModelTurn` after receiving `cacheHints`, before `buildContextSidecars`.  
**Scenarios:** `SC-FMT-03`, `SC-FMT-04`

#### TASK-S1-06 — Update emptiness check

**File:** `RustLoopInferenceService.kt`  
**Change:** `isAllEmpty = contextReferences.isEmpty() && jsonSidecars.isEmpty()` (no `cacheHints`). Return `null` when `isAllEmpty && networkStatus.isBlank()`.  
**Scenarios:** `SC-FMT-03`, `SC-FMT-05`

#### TASK-S1-07 — Update `RustLoopInferenceServiceTest` format expectations

**File:** `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceServiceTest.kt`  
**Change:** Update `completeModelTurn_forwardsContextSidecarsToProviderPrompt` and `completeModelTurn_marksStaleContextReferencesInSidecars` (rename/repurpose second test to assert simplified reference format without stale label). Add negative assertions for forbidden substrings.  
**Scenarios:** `SC-FMT-01`, `SC-FMT-02`, `SC-FMT-03`, `SC-FMT-05`, `SC-FMT-MUT`

#### TASK-S1-08 — Sync test duplicate builder in `XaiPromptCacheLiveContextTest`

**File:** `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/XaiPromptCacheLiveContextTest.kt`  
**Change:** Update private `buildContextSidecars` helper to match production shape (no header; ledger/index lines only).  
**Scenarios:** `SC-FMT-01`, `SC-VOL-01` (prep for Slice 4)

**Slice 1 checkpoint:** `./gradlew :feature:inference:testDebugUnitTest --tests "*RustLoopInferenceServiceTest*"`

---

### Slice 2 — OpenAI-family dedupe gate

#### TASK-S2-01 — Add dedupe state and resolve gate

**File:** `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/ProviderAppendOnlyContext.kt`  
**Change:** Add `private var lastShippedSidecarText: String? = null` and:

```kotlin
fun resolveContextSidecarsForWire(built: String?): String? {
    val trimmed = built?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (trimmed == lastShippedSidecarText) return null
    lastShippedSidecarText = trimmed
    return trimmed
}
```

**Scenarios:** `SC-DEDUPE-RESOLVE-01`, `SC-DEDUPE-RESOLVE-02`, `SC-DEDUPE-RESOLVE-03`, `SC-DEDUPE-01`, `SC-DEDUPE-02`

#### TASK-S2-02 — Reset `lastShippedSidecarText` on session clear

**File:** `ProviderAppendOnlyContext.kt`  
**Change:** In `clearLogState`, set `lastShippedSidecarText = null` alongside `frozenSidecarSnapshots.clear()`.  
**Scenarios:** `SC-DEDUPE-03`, `SC-DEDUPE-RESOLVE-03`

#### TASK-S2-03 — Wire dedupe gate in `BaseOpenAiSdkInferenceService.completeSingleTurn`

**File:** `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/BaseOpenAiSdkInferenceService.kt`  
**Change:** Before `OpenAiCompatibleVolatilePlacement.buildMappedBodyWithSidecars`, compute `val wireSidecar = appendOnly.resolveContextSidecarsForWire(contextSidecars)`. Pass `wireSidecar` as `currentSidecarText` to both chat and response wire builders. Replace unconditional freeze block with `if (wireSidecar != null) { appendOnly.endTurnFreezeSidecar(wireSidecar, tailInsertIndex) }`.  
**Scenarios:** `SC-DEDUPE-01`, `SC-DEDUPE-02`, `SC-DEDUPE-04`, `SC-DEDUPE-MUT`

#### TASK-S2-04 — Unit tests for resolve gate and reset

**File:** `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/appendonly/ProviderAppendOnlyContextTest.kt`  
**Change:** Add tests for first ship, unchanged skip, changed ship, `clearLogState`/`invalidate` reset, and `markTurnBoundaryForReanchor` preserving dedupe state.  
**Scenarios:** `SC-DEDUPE-RESOLVE-01`, `SC-DEDUPE-RESOLVE-02`, `SC-DEDUPE-RESOLVE-03`, `SC-DEDUPE-03`, `SC-DEDUPE-04`

#### TASK-S2-05 — OpenAI wire dedupe integration tests

**File:** `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/AppendOnlyOpenAiSidecarPlacementTest.kt`  
**Change:** Add `SC-DEDUPE-01` through `SC-DEDUPE-04` scenarios. Update `AOC_22_tool_loop_does_not_increment_append_only_clear_count` — when sidecar strings differ per hop, behavior unchanged (3 freezes); add new test where identical sidecar string across 3 hops yields 1 freeze and frozen replay count 1.  
**Scenarios:** `SC-DEDUPE-01`, `SC-DEDUPE-02`, `SC-DEDUPE-03`, `SC-DEDUPE-04`

#### TASK-S2-06 — Adjust xAI session stability expectations under dedupe

**File:** `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/XaiPromptCacheToolAndSessionStabilityTest.kt`  
**Change:** Where multi-hop tests use identical `contextSidecars` strings, expect reduced freeze count; preserve cross-chat isolation and reanchor frozen replay assertions.  
**Scenarios:** `SC-DEDUPE-04`, `SC-DEDUPE-01`

**Slice 2 checkpoint:** `./gradlew :feature:inference:testDebugUnitTest --tests "*ProviderAppendOnlyContextTest*" --tests "*AppendOnlyOpenAiSidecarPlacementTest*" --tests "*XaiPromptCacheToolAndSessionStabilityTest*"`

---

### Slice 3 — Anthropic/Google index-interleaved replay + dedupe

#### TASK-S3-01 — Wire index-interleaved sidecars in Anthropic

**File:** `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicInferenceServiceImpl.kt`  
**Change:** In message loop, replace `VolatileTurnContextWireSupport.combinedLatestUserPrepends` for context sidecars with per-index `SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex`. Resolve `val wireSidecar = appendOnly.resolveContextSidecarsForWire(contextSidecars)` once per turn. Prepend sidecar texts to each row's content at its session index. Gate `freezeContextSidecarIfPresent` on non-null `wireSidecar`.  
**Scenarios:** `SC-AG-01`, `SC-AG-02`, `SC-DEDUPE-01`, `SC-DEDUPE-MUT`

#### TASK-S3-02 — Wire index-interleaved sidecars in Google

**File:** `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleInferenceServiceImpl.kt`  
**Change:** Same pattern as TASK-S3-01 in `mapAtIndex` / message mapping loop. Gate `endTurnFreezeSidecar` on non-null resolved live sidecar.  
**Scenarios:** `SC-AG-03`, `SC-AG-04`, `SC-DEDUPE-01`

#### TASK-S3-03 — Add A/G interleaved replay unit coverage

**File:** `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/appendonly/SessionIndexVolatilePlacementTest.kt` (create) or extend `PromptCachingTest.kt`  
**Change:** Direct tests for `sidecarTextsBeforeSessionIndex`: frozen at index 0 not prepended to index 2; live only at `tailInsertIndex`; pre-retrieval at its insert index.  
**Scenarios:** `SC-AG-05`

#### TASK-S3-04 — Update `PromptCachingTest` A/G expectations

**File:** `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/PromptCachingTest.kt`  
**Change:** Revise `test_PC_02_AnthropicAttachesEphemeralCacheControl` and related A/G tests so sidecar text appears prepended on the row at freeze session index, not only latest user. Add 3-hop identical sidecar scenario: hop 1 ships+freezes; hops 2–3 omit live; frozen text remains on freeze-index row. Revise prior "latest user only" baseline per structure.md note on AOC-07/AOC-08.  
**Scenarios:** `SC-AG-01`, `SC-AG-02`, `SC-AG-03`, `SC-AG-04`, `SC-DEDUPE-01`

**Slice 3 checkpoint:** `./gradlew :feature:inference:testDebugUnitTest --tests "*PromptCachingTest*"`

---

### Slice 4 — Volatile detection + module regression

#### TASK-S4-01 — Update production volatile detection prefixes

**File:** `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/BaseOpenAiSdkInferenceService.kt`  
**Change:** In `ChatCompletionMessageParam.isVolatileOrEngineSystemMessage` and `AgentLoopMessage.isVolatileOrEngineSystemMessage`, remove matchers for `Cache hints:` and `Context management sidecars:`. Retain pre-retrieval marker, `State ledger JSON:`, `Tool context index JSON:`, `File context index JSON:`, engine-internal, `[Conversation summary]`.  
**Scenarios:** `SC-VOL-01`, `SC-VOL-02`

#### TASK-S4-02 — Sync test duplicate volatile helper

**File:** `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/XaiPromptCacheLiveContextTest.kt`  
**Change:** Update `isVolatileSidecar()` prefix set to mirror production (remove dead patterns). Remove header/cache-hint assertions from live context tests.  
**Scenarios:** `SC-VOL-01`, `SC-VOL-02`, `SC-FMT-MUT`

#### TASK-S4-03 — Prompt-cache regression sweep

**File:** `PromptCachingTest.kt`, `AppendOnlyOpenAiSidecarPlacementTest.kt`, any test asserting `|fresh|`, `|stale|`, `Context management sidecars`, `Cache hints:`  
**Change:** Update synthetic sidecar strings and expectations for PC-01–PC-08 compatibility with dedupe + new format. Ensure `test_PC_01_OpenAiSeparatesSystemPromptAndSidecars` and `test_PC_01_multi_turn_tail_placement` still pass with dedupe gate (first ship freezes; identical re-ship skipped).  
**Scenarios:** `SC-REG-01`, `SC-REG-02`

#### TASK-S4-04 — Full module validation

**Command:** `./gradlew :feature:inference:testDebugUnitTest` and `./gradlew detekt`  
**Scenarios:** All scenarios (acceptance gate)

**Slice 4 checkpoint:** Full module tests + detekt zero issues.

---

## 6. Task → Scenario Matrix

| Task | Scenarios |
|------|-----------|
| TASK-S1-01 | SC-FMT-01, SC-FMT-02 |
| TASK-S1-02 | SC-FMT-01, SC-FMT-02, SC-FMT-MUT |
| TASK-S1-03 | SC-FMT-02, SC-FMT-MUT |
| TASK-S1-04 | SC-FMT-01, SC-FMT-03, SC-FMT-04, SC-FMT-MUT |
| TASK-S1-05 | SC-FMT-03, SC-FMT-04 |
| TASK-S1-06 | SC-FMT-03, SC-FMT-05 |
| TASK-S1-07 | SC-FMT-01, SC-FMT-02, SC-FMT-03, SC-FMT-05, SC-FMT-MUT |
| TASK-S1-08 | SC-FMT-01, SC-VOL-01 |
| TASK-S2-01 | SC-DEDUPE-RESOLVE-01, SC-DEDUPE-RESOLVE-02, SC-DEDUPE-01, SC-DEDUPE-02 |
| TASK-S2-02 | SC-DEDUPE-03, SC-DEDUPE-RESOLVE-03 |
| TASK-S2-03 | SC-DEDUPE-01, SC-DEDUPE-02, SC-DEDUPE-04, SC-DEDUPE-MUT |
| TASK-S2-04 | SC-DEDUPE-RESOLVE-01, SC-DEDUPE-RESOLVE-02, SC-DEDUPE-RESOLVE-03, SC-DEDUPE-03, SC-DEDUPE-04 |
| TASK-S2-05 | SC-DEDUPE-01, SC-DEDUPE-02, SC-DEDUPE-03, SC-DEDUPE-04 |
| TASK-S2-06 | SC-DEDUPE-01, SC-DEDUPE-04 |
| TASK-S3-01 | SC-AG-01, SC-AG-02, SC-DEDUPE-01, SC-DEDUPE-MUT |
| TASK-S3-02 | SC-AG-03, SC-AG-04, SC-DEDUPE-01 |
| TASK-S3-03 | SC-AG-05 |
| TASK-S3-04 | SC-AG-01, SC-AG-02, SC-AG-03, SC-AG-04, SC-DEDUPE-01 |
| TASK-S4-01 | SC-VOL-01, SC-VOL-02 |
| TASK-S4-02 | SC-VOL-01, SC-VOL-02, SC-FMT-MUT |
| TASK-S4-03 | SC-REG-01, SC-REG-02 |
| TASK-S4-04 | All |

---

## 7. Acceptance Criteria (ticket Part 4)

- Identical sidecar string across N hops → at most one `endTurnFreezeSidecar` for that string per session.
- Sidecar string never contains `Cache hints:`, `Context management sidecars:`, or `|fresh|` / `|stale|`.
- `cacheHints` still flows Rust → Kotlin on `completeModelTurn` (no FFI break).
- Ledger/index/reference semantic changes produce new sidecar string → ship + freeze.
- A/G frozen sidecars replay at stored session index via `sidecarTextsBeforeSessionIndex`.
- `./gradlew :feature:inference:testDebugUnitTest` and `./gradlew detekt` pass.

---

## 8. Approval Gate

Plan complete — awaiting human approval before TDD Red.
