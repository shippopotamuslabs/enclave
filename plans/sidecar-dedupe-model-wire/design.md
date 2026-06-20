# Design: sidecar-dedupe-model-wire

Alignment artifact for human review before Structure and Plan. Grounded in `research.md`, `ticket.md`, and re-verified code paths.

---

## Current state

### Sidecar composition (`RustLoopInferenceService.buildContextSidecars`)

`completeModelTurn` builds a single prose block from Rust callback inputs (`contextReferences`, compacted ledger/index JSON, `cacheHints`, `currentTurnIndex`) and passes it as `contextSidecars` into `RustCompatibleInferenceService.completeSingleTurn`.

Today's emitted shape (in order):

1. `Context management sidecars:` header
2. `CONTEXT_REFERENCE_POLICY_PREAMBLE` (static one-liner)
3. Optional offline/back-online network status
4. Optional `References:` block with lines from `formatContextReferenceLine` — includes `|turn:N|`, `|fresh|`, or `|stale|` derived from Kotlin `currentTurnIndex` and `isStaleContextReference`
5. Optional `State ledger JSON: …`, `Tool context index JSON: …`, `File context index JSON: …` (via `ContextSidecarCompaction`)
6. Optional `Cache hints: …` from `ModelRequest.cacheHints`

Returns `null` when references, JSON blobs, and `cacheHints` are all empty **and** network status is blank. A non-empty `cacheHints` alone can force a non-null sidecar today.

`cacheHints` flows Rust → Kotlin on the `completeModelTurn` callback and through FFI on `ModelRequest`; its only model-visible rendering is inside the built sidecar string.

### Provider wire assembly and freeze

Three BYOK families consume `contextSidecars`:

| Provider family | Wire injection | Frozen replay on wire | Freeze call site |
|-----------------|----------------|----------------------|------------------|
| OpenAI / xAI / OpenRouter | Tail system sidecar via `OpenAiCompatibleVolatilePlacement` | Yes — `frozenSidecarsForWireReplay()` interleaved by `mappedInsertIndex` | After wire build, before API stream |
| Anthropic | Prepend to latest user message via `VolatileTurnContextWireSupport` | **No** — frozen list is bookkeeping only | After stream completes |
| Google | Prepend to latest user content in mapper | **No** — same as Anthropic | After stream returns |

Every non-blank `contextSidecars` today triggers `endTurnFreezeSidecar` / `freezeContextSidecarIfPresent` on every hop. No string-equality dedupe exists (`lastShippedSidecarText` absent).

Pre-retrieval sidecars (`PreRetrievalContextSidecar`, `options.volatileTurnContext`) are a separate path with one-shot freeze via `hasFrozenPreRetrievalSidecar()` on OpenAI family; unchanged by this ticket unless identical-string dedupe is needed there (out of scope).

### Session state (`ProviderAppendOnlyContext`)

Holds stable prefix, append-only message log, `frozenSidecarSnapshots`, digest cursor, reanchor flag. Cleared on `invalidate()`, `clearLogState` (digest mismatch, compaction, shorter array, rust compaction boundary). `markTurnBoundaryForReanchor` preserves frozen sidecars across user-turn boundaries on OpenAI family; Anthropic/Google call `invalidate()` on new prompt instead.

`appendVolatileSidecar` exists but is referenced only in tests — production uses `endTurnFreezeSidecar` only.

### Reference TTL today

- **Wire (Kotlin):** `fresh`/`stale` labels on reference lines change as `sessionTurnIndex` advances, causing sidecar string churn without semantic payload change.
- **Store (Rust):** `DEFAULT_TOOL_OUTPUT_TTL_TURNS = 2` in `tool_spill.rs`; spilled entries get `expires_after_turn`. `InnerSession::step_idle` calls `context_store.expire_after_turn(turn_index)` at each new user turn (`engine/mod.rs:1812–1813`). Expired entries are removed from the store.
- **Retrieval (Rust):** `read_context_reference` does a store `get`; expired refs surface as `ContextReferenceNotFound` once purged. No separate Kotlin TTL gate in `handleReadContextReference`.

### Volatile sidecar detection

`BaseOpenAiSdkInferenceService.isVolatileOrEngineSystemMessage()` matches prefixes including `Cache hints:`, `Context management sidecars:`, ledger/index JSON markers, and pre-retrieval marker. Test duplicate in `XaiPromptCacheLiveContextTest`.

---

## Desired end state

### Observable behavior — dedupe (Part 1)

Per `completeModelTurn` / `completeSingleTurn` hop:

1. Build sidecar text as today (after Part 2 cleanup).
2. At the **provider wire boundary**, compare built string to `lastShippedSidecarText` for the session's `ProviderAppendOnlyContext`.
3. **Unchanged (exact match):** pass `null` as live `contextSidecars`; do **not** call `endTurnFreezeSidecar` / `freezeContextSidecarIfPresent`.
4. **Changed or first ship:** inject live sidecar with existing placement rules; freeze after wire build (OpenAI) or after stream (Anthropic/Google); set `lastShippedSidecarText`.
5. **OpenAI family unchanged hops:** model still sees prior sidecar content via `frozenSidecarsForWireReplay()` — no new frozen entry, replay count stable.
6. **Reset:** `invalidate()` and `clearLogState` clear `lastShippedSidecarText` along with frozen snapshots.

Acceptance: identical sidecar across N hops → at most one freeze for that string per session; semantic ledger/index/reference changes still ship and freeze.

### Observable behavior — model-facing cleanup (Part 2)

Sidecar string contains **only**:

| Content | Notes |
|---------|-------|
| `CONTEXT_REFERENCE_POLICY_PREAMBLE` | May include one-line TTL note (see decisions) |
| Network status | Only when offline / back-online message is non-blank |
| Reference lines | Simplified format (no header, no fresh/stale, no `\|turn:N\|` bracket segment) |
| Ledger / tool index / file index JSON lines | Unchanged compaction |

Sidecar string **never** contains `Cache hints:`, `Context management sidecars:`, or `|fresh|` / `|stale|`.

`cacheHints` continues to flow on `ModelRequest` / `completeModelTurn` callback unchanged (no FFI break).

`buildContextSidecars` returns `null` when references, JSON blobs, and network status are all absent — preamble alone does not force a hop. `cacheHints` excluded from emptiness calculation.

Volatile detection helpers updated to match new format; dead prefix patterns removed.

### Out of scope (ticket non-goals)

Frozen-sidecar replay mechanics, interleaving, tail placement, append-only log, digest, freeze-on-ship timing model, and pre-retrieval one-shot freeze behavior — except the dedupe gate coupling freeze to resolved non-null sidecar.

---

## Patterns to follow

| Pattern | Where | Reuse for this work |
|---------|-------|---------------------|
| Freeze only on ship | `endTurnFreezeSidecar` after non-blank sidecar | Gate freeze on resolved non-null output of dedupe helper |
| Position-aware frozen replay | `FrozenSidecarSnapshot` + `OpenAiCompatibleVolatilePlacement` | Unchanged; dedupe reduces duplicate snapshots |
| JSON compaction before assembly | `ContextSidecarCompaction` | Keep; semantic changes still change sidecar string |
| Provider wire boundary as authority | OpenAI builds messages then freezes; A/G prepend then freeze post-stream | Dedupe helper invoked at same boundary, before injection and before freeze |
| Session clear semantics | `clearLogState` clears `frozenSidecarSnapshots` | Extend to clear `lastShippedSidecarText` |
| Test conventions | JUnit 5, `runTest`, MockK, state assertions (`UNIT_TESTING.md`) | SC-DEDUPE-01–04 plus updated format expectations |
| `cacheHints` on host path | `AgentLoopHostCallbacks`, FFI `ModelRequest` | Preserve; stop rendering into prose only |

---

## Patterns to avoid

| Anti-pattern | Why |
|--------------|-----|
| Dedupe inside `buildContextSidecars` only | Freeze and live injection would desync from builder null/non-null |
| Wire `fresh`/`stale` as TTL enforcement | Labels churn every hop; Rust store already expires refs at turn boundaries |
| Removing `cacheHints` from ModelRequest / FFI | Ticket explicit; host telemetry and future policy stay available |
| Production use of `appendVolatileSidecar` | Test-only dead path; do not copy for dedupe |
| Adding Anthropic/Google frozen replay in this ticket | Violates non-goal; separate feature if needed |
| Shipping preamble-only sidecar | No model value; wastes a hop |
| Provider-specific dedupe logic scattered in three services | Ticket requires centralized gate on `ProviderAppendOnlyContext` |

---

## Design decisions

### D1 — Centralized dedupe gate on `ProviderAppendOnlyContext`

**Choice:** Add `lastShippedSidecarText: String?` and `resolveContextSidecarsForWire(built: String?): String?` on `ProviderAppendOnlyContext` (or a small co-located `ContextSidecarShipping.kt` helper called from context). All three provider families call the helper immediately before wire injection and gate freeze on non-null result.

**Rationale:** Keeps freeze count, live injection, and shipped-text memory in one place; matches ticket locus and acceptance criteria.

**Rejected:** Builder-level comparison — would not control freeze. Per-provider string compare — duplicates state and risks drift.

### D2 — Reset `lastShippedSidecarText` with frozen list clears

**Choice:** Clear in `invalidate()` and `clearLogState()` alongside `frozenSidecarSnapshots.clear()`.

**Rationale:** After digest/compaction invalidation, prior shipped text must not block re-ship when frozen history is empty (SC-DEDUPE-03).

**Rejected:** Persist across clears — would suppress needed re-ship after invalidation.

### D3 — Remove engine noise from model-facing sidecar string

**Choice:** Drop `Cache hints:`, `Context management sidecars:` header, `|fresh|/|stale|`, and `\|turn:N\|` bracket segment from `formatContextReferenceLine`. Simplified line shape:

```
- [TOOL_OUTPUT] id (200b): title
  preview: "…"
  retrieval_hint
```

Keep `title` as Rust sends it (may include `@turn:N` suffix for human readability).

**Rationale:** Eliminates hop-volatile labels and host-only cache policy from prompt-cache prefix; semantic content drives dedupe equality.

**Rejected:** Keep header for debugging — model does not consume it; pollutes cache key.

### D4 — TTL: Rust store expiry + preamble note, not wire labels

**Choice:** Remove Kotlin `isStaleContextReference`, `parseTurnFromTitle` usage for wire formatting, and `currentTurnIndex` parameter from sidecar formatting if unused elsewhere. Rely on existing Rust behavior: `expire_after_turn` at turn start purges expired tool-output refs; `read_context_reference` returns not-found after purge. Extend `CONTEXT_REFERENCE_POLICY_PREAMBLE` with a single line stating tool-output refs expire after 2 user turns (approved OQ-2), e.g. "Tool-output references expire after 2 user turns."

**Rationale:** Re-verified in `third_party/agent_core`: TTL is enforced at store eviction, not at `read_context_reference` inline check. Wire labels duplicated metadata and caused dedupe misses.

**Rejected:** Add Kotlin TTL check before `readContextReference` — redundant with Rust store. Keep stale labels without turn bracket — still hop-volatile for tool-output age window.

### D9 — Anthropic/Google interleaved frozen replay (approved OQ-1: C, revised)

**Choice:** Apply the same `resolveContextSidecarsForWire` gate on Anthropic and Google wire paths as on OpenAI family. **Additionally**, wire frozen sidecars at their stored `mappedInsertIndex` (session index) via existing `SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex` — prepending sidecar text to the message at that index only, not rolling all frozen text onto the latest user.

**Rationale:** Human approved OQ-1 option C. "Frozen" means bytes stay at the index where they were committed; moving sidecar prose from an older user/tool row onto the latest user changes the prefix and breaks Anthropic/Google prompt cache (same failure mode as OpenAI front-prepend regression in `AOC-19`). `sidecarTextsBeforeSessionIndex` already implements index-keyed replay for A/G but is unused in production (`research.md` finding).

**Rejected:** Prepend frozen+live bundle on latest user only (cache-hostile). A (accept visibility gap). B (A/G-only dedupe exemption).

**Wire shape (conceptual parity with OpenAI):**

```
for each session message at index i:
  prepend sidecarTextsBeforeSessionIndex(i, frozenReplay, preRetrieval, resolvedLive, tailIndex)
  then map message to provider wire row
```

- `frozenReplay` = `frozenSidecarsForWireReplay()` (same snapshot list as OpenAI)
- `resolvedLive` = output of `resolveContextSidecarsForWire(built)` (null when dedupe skip)
- Live sidecar inserts only at `tailInsertIndex`; frozen snapshots replay only at their stored index

### D5 — `cacheHints` host-only (logging: approved OQ-3)

**Choice:** Stop appending `cacheHints` in `buildContextSidecars`. Log at DEBUG with `PROMPT_CACHE` tag when non-empty. Remove from `isAllEmpty`.

### D6 — Preamble-only returns null

**Choice:** Sidecar is omitted when references, compacted JSON blobs, and network status are all absent, even though preamble constant exists.

**Rationale:** Ticket empty-check rule; static preamble alone is not worth a provider hop.

### D7 — Pre-retrieval path unchanged

**Choice:** Do not apply string dedupe to `PreRetrievalContextSidecar` / `volatileTurnContext` one-shot freeze path.

**Rationale:** Ticket scopes dedupe to context-management sidecars from `buildContextSidecars`; pre-retrieval already suppresses live re-injection after first freeze.

### D8 — Volatile detection prefix update

**Choice:** Update `isVolatileOrEngineSystemMessage` (and test duplicate in `XaiPromptCacheLiveContextTest`) to remove `Cache hints:` and `Context management sidecars:` matchers; retain ledger/index JSON, pre-retrieval, engine-internal, conversation summary matchers.

**Rationale:** Dead patterns after format cleanup; stable JSON prefixes remain valid volatile signals.

### D10 — Llama / on-device path

**Choice:** No dedupe gate on `LlamaInferenceServiceImpl` (no `ProviderAppendOnlyContext`).

**Rationale:** Ticket targets BYOK append-only provider context; local path injects sidecars into user content differently.

---

## Test strategy (design-level)

| ID | Intent |
|----|--------|
| SC-DEDUPE-01 | 3 hops, identical built sidecar → 1 freeze, frozen replay count 1 on OpenAI wire |
| SC-DEDUPE-02 | v1 then v2 then v2 → 2 freezes |
| SC-DEDUPE-03 | `invalidate()` resets `lastShippedSidecarText`; next hop ships |
| SC-DEDUPE-04 | Unchanged sidecar across `markTurnBoundaryForReanchor` still dedupes; prior frozen replays |
| Format | `RustLoopInferenceServiceTest`, `XaiPromptCacheLiveContextTest` — no header/cache-hint/fresh/stale |
| Regression | `AppendOnlyOpenAiSidecarPlacementTest`, `PromptCachingTest` PC-01–08 expectations updated where format touches synthetic sidecar strings |

Validation command: `./gradlew :feature:inference:testDebugUnitTest` and `./gradlew detekt`.

---

## Open questions

**Resolved (2026-06-17):**

- **OQ-1:** C — add minimal frozen prepend replay for Anthropic/Google.
- **OQ-2:** Preamble TTL line approved (clear 2-turn expiry wording).
- **OQ-3:** `PROMPT_CACHE` DEBUG tag for non-empty `cacheHints`.

## Approval gate

Design approved 2026-06-17. Proceed to Structure.
