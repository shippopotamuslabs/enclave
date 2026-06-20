# Ticket: sidecar-dedupe-model-wire

## Implementation Prompt: Sidecar Dedupe + Model-Only Wire Content

### Objective

Reduce prompt-cache churn and token waste on BYOK provider wires by:

1. Shipping context sidecars only when the compacted sidecar string changed vs the last sidecar we actually put on the wire (exact string match).
2. Removing model-irrelevant noise from the sidecar string. Host/engine data stays available to Kotlin/Rust/telemetry but is not sent to the model.

**Explicit non-goals:** Do not change frozen-sidecar replay, interleaving, tail placement, append-only message log, digest, or freeze-on-ship semantics beyond the gate described below.

**Read:** contracts/INFERENCE_RULES.md, contracts/CODE_STYLE_RULES.md, contracts/UNIT_TESTING.md, plans/prompt-caching/spec.md, plans/FEAT-append-only-provider-context/spec.md.

### Part 1 — Ship sidecar only when changed

**Behavior (per completeModelTurn / provider equivalent)**

- Build sidecar text as today via `RustLoopInferenceService.buildContextSidecars(...)` (after Part 2 cleanup).
- Compare result to `lastShippedSidecarText` for this `ProviderAppendOnlyContext` session (null if none shipped yet).
- If equal (exact string match):
  - Pass `null` as `contextSidecars` into wire assembly (OpenAI/xAI/OpenRouter/Anthropic/Google) — do not inject live sidecar on this hop.
  - Do not call `endTurnFreezeSidecar` / `freezeContextSidecarIfPresent`.
- If different (or first ship):
  - Inject live sidecar exactly as today (tail placement, pre-retrieval rules unchanged).
  - After building the request (same timing as today), freeze via `endTurnFreezeSidecar`.
  - Set `lastShippedSidecarText = contextSidecars`.

**What stays the same**

- `frozenSidecarsForWireReplay()` — prior frozen snapshots still replay on every hop.
- On unchanged hops, the model still sees the last shipped ledger/index/refs via existing frozen bytes in the prefix.
- Pre-retrieval sidecar one-shot freeze (`hasFrozenPreRetrievalSidecar`) — unchanged unless tests require dedupe there too (out of scope unless identical string repeats).
- `invalidate()`, `clearLogState`, compaction clears — reset `lastShippedSidecarText` along with frozen sidecar list.

**Implementation locus**

- Add `lastShippedSidecarText: String?` to `ProviderAppendOnlyContext`.
- Centralize gate in one helper, e.g. `resolveContextSidecarsForWire(built: String?): String?`, used by:
  - `BaseOpenAiSdkInferenceService.completeSingleTurn`
  - `AnthropicInferenceServiceImpl` (before prepend + freeze)
  - `GoogleInferenceServiceImpl` (before prepend + freeze)
- Freeze only when resolved sidecar is non-null.
- Do not compare in `buildContextSidecars` alone — gate must apply at provider wire boundary so freeze and live injection stay in sync.

**Tests (new / update)**

| ID | Scenario |
|----|----------|
| SC-DEDUPE-01 | 3 completeSingleTurn hops, identical contextSidecars input → wire has one live+freeze cycle; hops 2–3 have no new frozen entry; frozen replay count stays 1. |
| SC-DEDUPE-02 | Hop 1 ledger v1, hop 2 ledger v2 → 2 frozen snapshots; hop 3 same v2 → still 2 frozen. |
| SC-DEDUPE-03 | `invalidate()` / digest clear → `lastShippedSidecarText` reset; next hop ships even if string matches pre-clear frozen (frozen list empty). |
| SC-DEDUPE-04 | Unchanged sidecar across user-turn boundary (`markTurnBoundaryForReanchor`) — still dedupe if string identical; frozen from prior turn still replays. |

Extend `ProviderAppendOnlyContextTest`, `AppendOnlyOpenAiSidecarPlacementTest`, and/or `XaiPromptCacheToolAndSessionStabilityTest` as appropriate.

### Part 2 — Remove model-irrelevant sidecar noise

**Wire content after cleanup**

`buildContextSidecars()` should emit only what the model needs to act:

| Keep on wire | Format |
|--------------|--------|
| Reference policy preamble | `CONTEXT_REFERENCE_POLICY_PREAMBLE` (one line) |
| Network status | Only when offline / back-online message is non-blank |
| Context references | See simplified format below |
| State ledger JSON | `State ledger JSON: {…}` |
| Tool context index JSON | `Tool context index JSON: {…}` |
| File context index JSON | `File context index JSON: {…}` |

**Remove from wire (do not send to model)**

| Remove | Why | Where it belongs instead |
|--------|-----|--------------------------|
| `Cache hints: …` (cacheHints from ModelRequest) | Engine/host prompt-cache policy; model does not consume it | Keep `cacheHints` on ModelRequest / FFI for future host use. Log at DEBUG in RustLoopInferenceService or PROMPT_CACHE diagnostics if useful. Never append to `buildContextSidecars()`. |
| `Context management sidecars:` header | Human/debug framing | Remove line entirely |
| `fresh` / `stale` labels in reference lines | Host TTL metadata; changes every hop without semantic payload change | Enforce TTL in `read_context_reference` / Rust `read_context_reference` (return structured error or hint in tool result if ref expired). Document TTL in static preamble if needed: refs may expire after N turns. |
| `\|turn:N\|` in reference bracket (Kotlin-computed) | Redundant with title `@turn:N` from Rust; used only for stale labeling | Simplify reference line; keep title as Rust sends it (may still contain `@turn:N` for human readability — acceptable if stable until ref changes) |

**Simplified reference line format**

Replace `formatContextReferenceLine` output from:

```
- [TOOL_OUTPUT|turn:2|stale] id (200b): title
  preview: "…"
  hint
```

To something stable and minimal, e.g.:

```
- [TOOL_OUTPUT] id (200b): title
  preview: "…"
  retrieval_hint
```

Remove `isStaleContextReference`, `parseTurnFromTitle` usage for wire formatting, and `currentTurnIndex` parameter if no longer needed for sidecar text.

Update `RustLoopInferenceServiceTest` expectations (lines asserting `|fresh|`, `|stale|`, `Context management sidecars`).

**buildContextSidecars empty check**

Update `isAllEmpty` logic: remove `cacheHints` from emptiness calculation (no longer part of built string). Sidecar is null only when references, JSON blobs, network status, and preamble-target content are all absent — preamble is static; if only preamble would remain, omit entire sidecar (preamble alone is not worth a hop).

**Volatile/engine system message detection**

Update `BaseOpenAiSdkInferenceService` helpers that detect sidecar system messages (`startsWith("Cache hints:")`, `Context management sidecars:`, etc.) to match new format. Remove dead patterns; keep `State ledger JSON:`, `Tool context index JSON:`, `File context index JSON:`, pre-retrieval markers.

### Part 3 — Files to touch

| Area | Files |
|------|-------|
| Dedupe state + gate | `ProviderAppendOnlyContext.kt`, optional small `ContextSidecarShipping.kt` helper |
| Wire + freeze | `BaseOpenAiSdkInferenceService.kt`, `AnthropicInferenceServiceImpl.kt`, `GoogleInferenceServiceImpl.kt` |
| Sidecar composition | `RustLoopInferenceService.kt` (`buildContextSidecars`, `formatContextReferenceLine`) |
| Detection / compaction | `BaseOpenAiSdkInferenceService.kt` (volatile system message checks), `ContextSidecarCompaction.kt` (unchanged unless tests need it) |
| Tests | `ProviderAppendOnlyContextTest.kt`, `AppendOnlyOpenAiSidecarPlacementTest.kt`, `RustLoopInferenceServiceTest.kt`, `PromptCachingTest.kt`, `XaiPromptCacheLiveContextTest.kt` (header/cache-hint assertions), any test asserting fresh/stale |
| Docs (optional, only if user asks) | One-line note in `docs/provider-wire-context-spec.html` §cache_hints / sidecar example |

Do not remove `cacheHints` from ModelRequest, FFI, or AgentLoopModels — only stop rendering into model-visible prose.

### Part 4 — Acceptance criteria

- Identical sidecar string across N hops → at most one `endTurnFreezeSidecar` for that string in that session; later hops omit live sidecar.
- Sidecar string never contains `Cache hints:`, `Context management sidecars:`, or `|fresh|` / `|stale|`.
- `cacheHints` still flows Rust → Kotlin on `completeModelTurn` callback (no FFI break).
- Ledger/index/reference semantic changes still produce new sidecar string → ship + freeze.
- Existing append-only / AOC placement / PC-01 / PC-02–08 tests pass after expectation updates.
- `./gradlew :feature:inference:testDebugUnitTest` and detekt pass.

**Mental model:** Sidecar string = model-facing state only. Ship it when it changes; freeze when shipped; replay frozen history unchanged. Host telemetry and cache policy live outside that string.
