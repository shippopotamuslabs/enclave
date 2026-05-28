# Spec: Append-Only Provider Context (Pi Invariant)

**Ticket ID:** `FEAT-append-only-provider-context`  
**Module scope:** `:feature:inference` (internal types only; no `:core:domain` persistence or Room schema changes)

---

## 1. Feature Summary

Enclave remote BYOK inference (OpenAI, xAI, OpenRouter, Anthropic, Google GenAI) must satisfy the **oh-my-pi (Pi) append-only provider context invariant** so provider prefix caches stay valid across Rust agent-loop turns.

### 1.1 Pi invariant (mapped to Enclave)

| Pi concept | Enclave meaning |
|---|---|
| **StablePrefix** | Frozen byte sequence for `systemPrompt` + normalized `availableTools` (tool schemas). Computed once per `sendPrompt` assistant turn unless fingerprint changes: `toolsFingerprint` change (tool disclosure), `GenerationOptions` tool catalog or active-tool set change, or MCP reconnect. |
| **AppendOnlyLog** | Provider-normalized conversation messages only grow; committed provider messages are never re-serialized or overwritten in place. Compaction is the only legal `replaceTail` path. |
| **Volatile tail** | Rust `buildContextSidecars()` output (state ledger, tool/file indexes, references, cache hints) is **not** a single mutable early system message. Each `completeModelTurn` delivers sidecars only on the **live tail** via provider-specific `VolatileContextDelivery`. Prior sidecar snapshots already committed to the log remain frozen. |
| **syncMessages digest** | After mapping `sessionMessages` to provider wire form, a rolling digest over synced prefix detects in-place rewrites (same length, changed bytes) and **clears** the append-only log so cache safety is restored. |

### 1.2 Why index-1 sidecar pin must be reverted

`BaseOpenAiSdkInferenceService` currently inserts volatile sidecars at fixed offset `insertAt = 1` (immediately after the stable system message). That placement:

- Puts sidecars **before** committed history when the session grows (tool loop: U1, A1, tool result append **after** the sidecar slot).
- **Rewrites** the provider-visible prefix when `stateLedgerJson` updates between `completeModelTurn` calls in the same assistant turn.
- Breaks xAI `x-grok-conv-id` / OpenAI `prompt_cache_key` prefix stability documented in `plans/prompt-caching/spec.md` tail-volatile rules.

**Required behavior:** sidecars as a separate system message **immediately before the live tail anchor** (last `sessionMessages` entry for OpenAI-compatible providers), with append-only versioning across turns.

### 1.3 Local inference exception

`LlamaInferenceServiceImpl` (LiteRT / on-device) has no remote prefix cache. It **continues** merging `systemPrompt` + `contextSidecars` into one system string (unchanged).

### 1.4 Rust loop interaction

`RustLoopInferenceService.completeModelTurn` may run **multiple times** per user `sendPrompt` (pre-tool, post-tool). `buildContextSidecars()` may return different text each call. Kotlin providers must **append** new tail sidecar delivery without mutating earlier committed provider bytes.

### 1.5 Sidecar accumulation tradeoff

Each `completeModelTurn` in a tool loop may add another frozen SYSTEM sidecar on the wire (see `AOC-04`). Bytes in the provider prefix **before** each snapshot stay cache-stable, but total token cost grows **O(turns)** with one additional sidecar message per turn. This is **accepted for v1**. An optional future compaction hook may collapse or drop older frozen sidecars; that hook is escalation-only and **not in scope** for this ticket.

**Regression (2026-05-26):** The Kotlin cap-at-2 + `priorFrozenSidecarsForWire` front-prepend mitigation **breaks** xAI/OpenAI prefix cache (see § Regression). Wire cap must not swap index-1 sidecar content; see tasks `AOC-18`–`AOC-20` / `PC-XAI-*`.

---

## 2. Architecture Flow

```
sendPrompt(options)
  │
  ├─► RustLoopInferenceService
  │     └─► AgentLoopHostCallbacks.completeModelTurn (0..N per assistant turn)
  │           ├─ buildContextSidecars()  ──► volatile string (changes per call)
  │           └─ inner.completeSingleTurn(sessionMessages, systemPrompt, contextSidecars, …)
  │
  └─► Remote provider service (per model)
        ├─ ProviderAppendOnlyContext (session-scoped, one per sendPrompt)
        │     ├─ StablePrefixSnapshot  (system + tools fingerprint)
        │     ├─ AppendOnlyLog<ProviderNormalizedMessage>
        │     └─ syncMessages(digest) / appendVolatileSidecar()
        │
        └─ VolatileContextDelivery (provider strategy)
              ├─ OpenAiCompatibleTailSystemMessage  (OpenAI, xAI, OpenRouter)
              ├─ PrependToLatestUserText            (Anthropic)
              ├─ PrependToLatestGenerateContent     (Google)
              └─ MergeIntoSystemPrompt              (Llama — no append-only log)
```

**Session scoping**

Each remote inference service holds `private var appendOnlyContext: ProviderAppendOnlyContext?` (mandatory instance field — **not** `ThreadLocal`; unsafe under coroutine dispatch). Set on first `completeSingleTurn` of a prompt; cleared in `closeSession()`.

| Boundary | `ProviderAppendOnlyContext` lifetime |
|---|---|
| `sendPrompt` start | Create or reset context on first `completeSingleTurn` for that prompt |
| `completeModelTurn` (same prompt) | Reuse context; extend log; deliver new volatile tail |
| `closeSession` / prompt completion | `invalidate()` — clear prefix snapshot + log + digest cursor |
| New `sendPrompt` | Fresh context |

`GenerationOptions.telemetrySequenceIndex` is **out of scope** unless session lifecycle hooks are touched for other reasons; do not couple append-only state to telemetry.

---

## 3. Relationship to `plans/prompt-caching/spec.md`

| Area | Authority |
|---|---|
| Anthropic ephemeral breakpoints + prepend sidecars to latest user | **Unchanged** — `prompt-caching/spec.md` remains source of truth |
| Google `CachedContent` lifecycle, delta reuse, 1024-token thresholds | **Unchanged** |
| Tool array stability (`allowToolCall = false` → `toolChoice` / `toolConfig` NONE) | **Unchanged** — still required |
| OpenAI / xAI / OpenRouter **message placement** for volatile sidecars | **Superseded by this spec** — tail-volatile + append-only, not index-1 |
| `plans/prompt-caching/spec.md` OpenAI/xAI “latest user message” wording | **Clarified by this spec** — tail anchor is `sessionMessages.last()` (tool-result tail uses `AOC-15` order) |
| `PromptCachingTest` `PC-01` message order expectations | **Updated** by this feature to match tail placement |

---

## 4. File Structure Table

| Action | Path | Responsibility |
|---|---|---|
| **Create** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/StablePrefixSnapshot.kt` | Immutable frozen `systemPrompt`, normalized tool fingerprint, version counter. |
| **Create** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/ProviderNormalizedMessage.kt` | Provider-agnostic normalized message record used for digest + log (role, text, tool call ids, metadata hash). |
| **Create** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/ProviderMessageDigest.kt` | FNV-style rolling digest over normalized fields (Pi-compatible: role, content, tool_calls, tool_call_id, name, id). |
| **Create** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/AppendOnlyMessageLog.kt` | Append-only list; `replaceTail` only for compaction hook; `clear()`. |
| **Create** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/VolatileContextDelivery.kt` | Sealed strategy / interface: how to attach `contextSidecars` for a turn without rewriting committed bytes. |
| **Create** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/OpenAiCompatibleVolatilePlacement.kt` | Compute insertion index = mapped index of `sessionMessages.lastIndex`; insert system sidecar immediately before that entry (not index 1). |
| **Create** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/ProviderAppendOnlyContext.kt` | Orchestrates `StablePrefix`, log, `syncMessages`, `appendVolatileSidecar`, `invalidate`. |
| **Create** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/ProviderAppendOnlyContextFactory.kt` | `fun create(): ProviderAppendOnlyContext` — no `ThreadLocal`; each inference service holds `private var appendOnlyContext: ProviderAppendOnlyContext?`. |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/BaseOpenAiSdkInferenceService.kt` | **Remove** `insertAt = 1` sidecar pin; `private var appendOnlyContext` set on first `completeSingleTurn` of a prompt, cleared in `closeSession()`; integrate tail placement; freeze prior sidecar messages in log. |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/OpenRouterInferenceServiceImpl.kt` | Inherits OpenAI path (no extra logic unless factory wiring needed). |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceService.kt` | Document / ensure `inner.closeSession()` invalidates append-only state; optional explicit invalidation after prompt completes. |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicInferenceServiceImpl.kt` | Wire session `invalidate()` on `closeSession`; **no** placement change. |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleInferenceServiceImpl.kt` | Wire session `invalidate()` on `closeSession`; **no** placement change. |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/LlamaInferenceServiceImpl.kt` | Confirm merge path; skip append-only log. |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/PromptCachingTest.kt` | Update `PC-01` OpenAI message order to tail-volatile contract. |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/XaiPromptCacheSidecarPlacementTest.kt` | Replace index-1 assertions with `AOC-*` tail placement expectations (or delete file if merged into new suite). |
| **Create** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/appendonly/ProviderAppendOnlyContextTest.kt` | Unit tests for log, digest, prefix freeze (`AOC-01`, `AOC-04`, `AOC-05`, `AOC-13`, `AOC-17`). |
| **Create** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/AppendOnlyOpenAiSidecarPlacementTest.kt` | OpenAI/xAI/OpenRouter wire order tests (`AOC-02`, `AOC-03`, `AOC-15`). |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceServiceTest.kt` | Two `completeModelTurn` calls assert monotonic sidecar forward to provider without prefix shift (`AOC-03`). |

---

## 5. API Contracts

### 5.1 `StablePrefixSnapshot`

```kotlin
data class StablePrefixSnapshot(
    val systemPrompt: String,
    val toolsFingerprint: String,
    val version: Int,
)
```

- Built on first `completeSingleTurn` when `systemPrompt` or normalized tool list fingerprint differs from cached snapshot.
- Rebuild triggers (increment `version`): `toolsFingerprint` change from tool disclosure, `GenerationOptions` tool catalog or active-tool set change, MCP reconnect — not only a generic “tool catalog change” label (`AOC-17`).
- `version` increments on rebuild; exposed for tests only via package-visible accessor if needed.

### 5.2 `ProviderAppendOnlyContext`

```kotlin
class ProviderAppendOnlyContext {
    fun ensureStablePrefix(systemPrompt: String, toolsFingerprint: String): StablePrefixSnapshot
    fun syncMessages(normalized: List<ProviderNormalizedMessage>)
    fun committedMessageCount(): Int
    fun appendVolatileSidecar(sidecarText: String, delivery: VolatileContextDelivery)
    fun invalidate()
}
```

**`syncMessages` rules (Pi-aligned):**

Input to `syncMessages` / `ProviderMessageDigest` is **only** messages mapped from `sessionMessages` (pre-sidecar). Sidecar SYSTEM messages are applied **after** sync via `appendVolatileSidecar` / placement helper and are **excluded** from digest and log sync input.

1. If `lastSyncCount > 0` and digest of `normalized.take(lastSyncCount)` ≠ stored digest → `log.clear()`, reset cursor.
2. If `normalized.size < lastSyncCount` (compaction) → `log.clear()`, reset cursor.
3. Append `normalized.drop(lastSyncCount)` to log.
4. Update `lastSyncCount` and digest over full `normalized`.

### 5.3 `VolatileContextDelivery`

```kotlin
sealed interface VolatileContextDelivery {
    /** OpenAI / xAI / OpenRouter: system message immediately before live tail index. */
    data class OpenAiTailBeforeLastSessionMessage(val sessionLastIndex: Int) : VolatileContextDelivery

    /** Anthropic / Google: prepend to latest user / latest content part (existing behavior). */
    data object PrependToLatestUserContent : VolatileContextDelivery

    /** Llama: merge into system prompt string. */
    data object MergeIntoSystemPrompt : VolatileContextDelivery
}
```

### 5.4 `BaseOpenAiSdkInferenceService.completeSingleTurn` (behavioral contract)

**Before (revert):**

```kotlin
// REMOVE — index-1 pin
val insertAt = if (systemPromptPrepended) 1 else 0
messages = messages.take(insertAt) + listOf(sidecarMessage) + messages.drop(insertAt)
```

**After:**

0. **Ordering:** Map `sessionMessages` only → run `syncMessages` / digest on that normalized view (no sidecar-injected messages). Apply volatile sidecars **after** sync.
1. Map `sessionMessages` → OpenAI params (existing `toOpenAiChatParams`).
2. Prepend stable system from `StablePrefixSnapshot` if absent.
3. `syncMessages` with normalized digest view of step-0 mapped conversation (session body only; no volatile sidecar slots).
4. If `contextSidecars` non-blank: insert **one** new system message at `OpenAiCompatibleVolatilePlacement.index(mappedMessages, sessionLastIndex)` — index of last session message in mapped list, **not** hard-coded `1`.
5. When a **new** sidecar string arrives on a subsequent `completeModelTurn` in the same session, **append** a new tail sidecar message; do not replace the prior sidecar message at any fixed offset.
6. Preserve existing tool stability: full `availableTools` in builder; `toolChoice(NONE)` when `!allowToolCall`.

**`AOC-03` index rule:** Assertions compare the **stable system + frozen prior sidecars + first USER** slice. After turn 1 with one USER, `u1` wire index is `2` (`[0]` stable system, `[1]` sidecar v1, `[2]` u1). Turn 2 must preserve `SECOND[0..2]` bytes for that slice; new content appends after index `2`.

### 5.5 `RustCompatibleInferenceService`

Signature unchanged:

```kotlin
suspend fun completeSingleTurn(
    sessionMessages: List<AgentLoopMessage>,
    systemPrompt: String,
    contextSidecars: String? = null,
    availableTools: List<ToolDefinition>,
    allowToolCall: Boolean,
    onTextToken: suspend (String) -> Unit,
    onThinkingToken: suspend (String) -> Unit,
    options: GenerationOptions,
): AgentModelTurnResult
```

---

## 6. Provider-Specific Mapping Table

| Provider | Stable prefix | Conversation body | Volatile sidecars | Tool stability when `!allowToolCall` |
|---|---|---|---|---|
| **OpenAI** | First system message (frozen) | Append-only mapped messages | System message immediately **before** `sessionMessages.last()` mapped entry | `toolChoice` = NONE |
| **xAI** | Same as OpenAI | Same | Same (xAI conv id header unchanged) | Same |
| **OpenRouter** | Same as OpenAI | Same + optional cache_control on prior user | Same | Same |
| **Anthropic** | `system` param (frozen) | Mapped messages + cache breakpoints on stable tail | Prepend to **latest non-tool-result user** text (existing) | `tool_choice` type none |
| **Google GenAI** | `CachedContent` / system instruction | Stable cached prefix + delta contents | Prepend to **latest** generate content only (existing) | `FunctionCallingConfigMode.NONE` |
| **Llama (local)** | N/A | Session rebuild from Rust messages | Merge into `systemPrompt` | N/A |

---

## 7. Data Model Changes

**None** for Room, Firestore, or `:core:domain` persisted models.

All types live under `feature.inference.appendonly` as internal implementation details.

---

## 8. Ordered Implementation Tasks

### Task 1: Append-only core types and digest
- **Description:** Implement `ProviderNormalizedMessage`, `ProviderMessageDigest`, `AppendOnlyMessageLog`, `StablePrefixSnapshot`, `ProviderAppendOnlyContext` with Pi-aligned `syncMessages`.
- **Files:** Create all `appendonly/*.kt` except placement helper.
- **Scenarios:** `AOC-01`, `AOC-04`, `AOC-05`, `AOC-13`, `AOC-14`, `AOC-17`

### Task 2: OpenAI-compatible tail placement helper
- **Description:** Implement `OpenAiCompatibleVolatilePlacement` and `VolatileContextDelivery.OpenAiTailBeforeLastSessionMessage`.
- **Files:** `OpenAiCompatibleVolatilePlacement.kt`, `VolatileContextDelivery.kt`
- **Scenarios:** `AOC-02`, `AOC-15`

### Task 3: Revert index-1 pin in `BaseOpenAiSdkInferenceService`
- **Description:** Delete `insertAt = 1` block; wire `ProviderAppendOnlyContext` instance field; integrate tail placement + append-only sidecar versioning on each `completeSingleTurn`.
- **Files:** `BaseOpenAiSdkInferenceService.kt`
- **Scenarios:** `AOC-02`, `AOC-03`, `AOC-12`, `AOC-15`, `AOC-16`

### Task 4: Session lifecycle invalidation
- **Description:** Reset append-only context in `closeSession()` for OpenAI-base, Anthropic, Google services; ensure `RustLoopInferenceService` prompt completion does not leak state across prompts.
- **Files:** `BaseOpenAiSdkInferenceService.kt`, `AnthropicInferenceServiceImpl.kt`, `GoogleInferenceServiceImpl.kt`, `RustLoopInferenceService.kt`
- **Scenarios:** `AOC-11`

### Task 5: Confirm local + regression providers
- **Description:** Verify `LlamaInferenceServiceImpl` merge path; Anthropic/Google code paths unchanged except invalidation; OpenRouter inherits Task 3.
- **Files:** `LlamaInferenceServiceImpl.kt`, `OpenRouterInferenceServiceImpl.kt`, `AnthropicInferenceServiceImpl.kt`, `GoogleInferenceServiceImpl.kt`
- **Scenarios:** `AOC-07`, `AOC-08`, `AOC-09`, `AOC-10`, `AOC-16`

### Task 6: Tool stability preservation
- **Description:** Re-verify `allowToolCall = false` still sends full tool list with disabled choice (no regression from append-only refactor).
- **Files:** `BaseOpenAiSdkInferenceService.kt`, `AnthropicInferenceServiceImpl.kt`, `GoogleInferenceServiceImpl.kt`
- **Scenarios:** `AOC-06`

### Task 7: Test suite migration
- **Description:** Update `PromptCachingTest.PC-01`; replace `XaiPromptCacheSidecarPlacementTest` expectations; add `ProviderAppendOnlyContextTest`, `AppendOnlyOpenAiSidecarPlacementTest`; extend `RustLoopInferenceServiceTest` for dual `completeModelTurn`. Add one sentence at the top of `plans/prompt-caching/spec.md` §1 OpenAI bullet cross-referencing `FEAT-append-only-provider-context` for tail anchor (`sessionMessages.last()`).
- **Files:** Test files listed in §4; `plans/prompt-caching/spec.md` (one-line cross-ref only).
- **Scenarios:** All `AOC-*`; update `PC-01` assertion only (Anthropic/Google `PC-02`–`PC-08` untouched)

---

## 9. Drift and Escalation Rules

| Rule | Action |
|---|---|
| **Drift — Pi reference** | If `plans/FEAT-append-only-provider-context/references/append-only-context.ts` (vendored) or a local oh-my-pi checkout if present — digest fields expand, mirror new fields in `ProviderMessageDigest` and add matching `AOC-MD-*` scenario before merge. |
| **Drift — Anthropic SDK** | Cache control API changes follow `prompt-caching` drift rule; do not alter sidecar prepend location. |
| **Escalation — Rust emits in-place message edits** | If Rust starts rewriting prior `AgentLoopMessage` content without compaction, escalate to agent-core; Kotlin digest will clear log (safe) but cache hit rate may collapse — document in logs at `WARN`. |
| **Escalation — Domain type needed** | If append-only state must survive process death, stop and request Contract Waiver + schema authorization; default remains in-memory per session only. |
| **Escalation — OpenRouter cache_control** | Tail placement must not move OpenRouter `cacheControlIndex` off the stable prior-user breakpoint; if conflict, stable breakpoint wins over sidecar position. |

---

## 10. Acceptance Criteria (Definition of Done)

1. Index-1 sidecar insertion **removed** from `BaseOpenAiSdkInferenceService`.
2. Tool-loop double `completeModelTurn` preserves byte-identical provider prefix for committed messages (`AOC-03`).
3. Sidecar text v1 remains in provider message list when v2 is delivered (`AOC-04`).
4. In-place normalized message rewrite clears append-only log (`AOC-05`).
5. Anthropic and Google tests `PC-02`–`PC-08` pass without placement changes (`AOC-07`, `AOC-08`).
6. `./gradlew :feature:inference:testDebugUnitTest` passes.
7. `PC-01` multi-turn tail indices match fixed `test_spec.md` order: stable SYSTEM, USER Hello, SYSTEM sidecar, USER Follow up (`messages[2]` sidecar, `messages[3]` last USER).

---

## Regression: xAI cache hits (2026-05-26)

### Symptom
Production xAI sessions report **~0 prompt cache reads** after append-only wire assembly + sidecar mitigations (cap-at-2, `ContextSidecarCompaction`, digest clear). Header routing (`x-grok-conv-id`) is correct; the **messages array prefix** is not stable.

### Root cause
`BaseOpenAiSdkInferenceService` (lines ~196–233) prepends `priorFrozenSidecarsForWire()` **before all mapped history**. That contradicts both:

- `plans/prompt-caching/spec.md` tail-volatile rule (sidecars immediately before live tail, not at fixed index 1 on later turns), and
- The append-only spec §5.4 `AOC-03` slice rule beyond the first tool-loop hop.

**Failure mode A — tail → front replay:** Sidecar frozen at tail-adjacent index on turn *N* (e.g. index 4 before tool result per `AOC-15`) is replayed at index 1 on turn *N+1*. Same UTF-8 text, different prefix position → xAI cache miss on every post-tool model call (including final assistant synthesis).

**Failure mode B — cap-at-2 prefix swap:** `ProviderAppendOnlyContext.priorFrozenSidecarsForWire` + `trimFrozenSidecarsToCap` drop the oldest frozen sidecar from the wire prefix on turn 3+ (`takeLast(1)` when current sidecar present). Index 1 content changes from `s1` to `s2` — guaranteed miss. Test `wire_caps_frozen_sidecars_at_two_on_third_tool_loop_turn` validates cap behavior but **not** prefix stability.

**Scope note:** `ContextSidecarCompaction` and `syncMessages` digest clear are **not** implicated in routine cache misses (compaction is deterministic; digest clear only on rewrite/compaction with `WARN` log).

### Fix tasks

| Priority | Task ID | Description | Files |
|---|---|---|---|
| **P0** | `AOC-18` | Add `FrozenSidecarSnapshot(text, mappedInsertIndex)`; record index in `endTurnFreezeSidecar` from `OpenAiCompatibleVolatilePlacement.insertIndex(sessionLastIndexAtFreeze)`. | `ProviderAppendOnlyContext.kt`, `OpenAiCompatibleVolatilePlacement.kt` |
| **P0** | `AOC-19` | Replace `priorFrozenSidecarsForWire` front-prepend with interleaved replay: merge frozen snapshots into `mappedBody` at stored indices (ascending), then insert current sidecar before live tail. | `BaseOpenAiSdkInferenceService.kt` |
| **P0** | `AOC-20` | Fix cap-at-2: cap may trim **storage** for memory bounds, but wire assembly must **not** replace prefix-slot sidecar text via `takeLast`. Options: (a) omit dropped sidecars from wire entirely, (b) keep all prefix-committed sidecars on wire until session `invalidate()`. Prefer (a) + rely on current tail sidecar for latest ledger. | `ProviderAppendOnlyContext.kt` |
| **P1** | `AOC-21` | Add failing-then-green tests: `AOC-18`/`PC-XAI-01` post-tool third call prefix; `AOC-20`/`PC-XAI-02` turn-4 cap behavior. | `AppendOnlyOpenAiSidecarPlacementTest.kt` |
| **P1** | `AOC-22` | Update §1.5 sidecar accumulation note: cap-at-2 wire eviction is **not** escalation-only — it breaks xAI/OpenAI prefix cache; defer wire cap until position-aware replay lands or cap drops off-wire only. | This spec §1.5 |

### Test scenarios (add to `test_spec.md`)

| ID | Type | Description |
|---|---|---|
| `AOC-18` | Fix verification | Three-call tool loop (pre-tool → post-tool → post-tool assistant): `messages[0..4]` byte-equal between calls 2 and 3. |
| `AOC-19` | Fix verification | Frozen sidecar `s1` stays at original mapped index after `s2` appended (not moved to index 1 on call 3). |
| `AOC-20` | Fix verification | Fourth call with cap: index 1 sidecar text unchanged vs call 3 (no `takeLast` prefix swap). |
| `AOC-21` | Fix verification | With trailing `agent_tool_budget` session message, volatile sidecar stays before tool result (mapped tail anchor). |
| `PC-XAI-01`–`04` | Cross-ref | See `plans/prompt-caching/spec.md` § Regression (2026-05-26). |

### Implementation note (wire assembly target)

```
[stable SYSTEM]
+ interleave frozen sidecars into mappedBody at each snapshot.mappedInsertIndex
+ insert currentSidecar before mappedBody[tailInsertIndex]
+ mappedBody[tailInsertIndex..]
```

Remove the block that prepends `priorFrozenSidecars.forEach { add(system…) }` before `mappedBody.take(tailInsertIndex)`.
