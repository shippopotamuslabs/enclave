# Structure: sidecar-user-embed

Vertical-slice execution outline. Signatures and types only — no
implementation bodies. Honors the approved decisions D1–D8 in `design.md`
and the invariants INV-1..INV-9 in `planning-prompt.md`.

Scope of this ticket (per approved design): **OpenAI-compatible providers
only** — chat-completions path **and** Responses path. Anthropic/Google
alignment is a follow-up slice (D4). LiteRT is untouched (INV-9, F13).

## Slice index

| # | Slice | One-line goal |
|---|-------|---------------|
| 1 | Sidecar marker + embedder primitive | Introduce `SidecarMarker`/`ResolvedSidecar` types, UUID generation, and `SidecarUserContentEmbedder` with unit tests — no wire path changes yet. |
| 2 | Snapshot shape + freeze/dedupe threading | Extend `FrozenSidecarSnapshot` with `markerId`; thread `markerId` through `resolveContextSidecarsForWire` / `endTurnFreezeSidecar` / `appendVolatileSidecar` without changing wire emission. |
| 3 | OpenAI chat-completions wire migration | Replace `OpenAiCompatibleVolatilePlacement` system-message insertion with `SidecarUserContentEmbedder` suffix injection on user message params; remove sidecar system messages. |
| 4 | OpenAI Responses wire migration | Replace `OpenAiCompatibleResponseSidecarPlacement` `SystemText` sidecar segments with embedder-based user input content suffix; keep index remapping. |
| 5 | INV-7 system-prompt rule | Add the UUID-aware `[Sidecar-<uuid>:]` annotation rule to `SearchToolPromptComposer.metadataRules` (full + compact). |
| 6 | Diagnostics reclassification | Update `PromptCacheSidecarDiagnostics` to classify `[Sidecar-<uuid>:` spans in user content; keep `extractSystemJsonSpans`/`formatBlock0` as the INV-4 invariant check. |
| 7 | Validation + test rewrite | Rewrite affected `:feature:inference` tests to assert the new wire shape; add INV-1/INV-2/INV-5/INV-7 unit tests; release-build emulator 5-turn DeepSeek validation. |

Slices 1–2 are foundations. Slices 3 and 4 are the two wire paths (chat then
Responses) and can land in either order but 3 before 4 keeps the dominant
path shippable first. Slice 5 can land in parallel with 3/4 (independent
file) but must be present before validation (7). Slice 6 must follow 3/4
(diagnostics depend on the new wire shape). Slice 7 is the integration
checkpoint.

## Per-slice checklist

### Slice 1 — Sidecar marker + embedder primitive

- **Components touched:** new file under
  `feature/inference/.../appendonly/`.
- **New types/signatures:**
  - `data class SidecarMarker(val markerId: String, val body: String)` —
    `markerId` is a UUID string (lowercase, hyphenated, 36 chars).
  - `object SidecarMarkerFactory { fun newMarker(body: String): SidecarMarker }`
    — generates a fresh UUID for a live sidecar body.
  - `fun SidecarMarker.wireBlock(): String` — returns
    `"[Sidecar-$markerId: $body]"`.
  - `object SidecarUserContentEmbedder`:
    - `fun embed(baseContent: String, markers: List<SidecarMarker>): String` —
      appends `"\n\n" + marker.wireBlock()` for each marker, in order,
      preserving any existing `[Metadata:]` suffix in `baseContent`.
    - `fun stripSidecarBlocks(content: String): String` — removes every
      `[Sidecar-<uuid>: ...]` block keyed on the UUID form regex
      `\[Sidecar-[0-9a-fA-F-]{36}:` (D7), leaving `[Metadata:]` and all other
      content intact.
  - `object SidecarMarkerRegex { val SPAN: Regex }` — the canonical UUID-form
    pattern used by `stripSidecarBlocks` and (later) diagnostics.
- **Checkpoint:** `./gradlew :feature:inference:testDebugUnitTest` green for
  a new `SidecarUserContentEmbedderTest` covering: empty markers returns
  base; single marker appended after metadata; multiple markers in order;
  strip removes only UUID-form sidecar blocks and leaves a user-typed
  `[Sidecar:` literal and `[Metadata:]` intact.

### Slice 2 — Snapshot shape + freeze/dedupe threading

- **Components touched:** `FrozenSidecarSnapshot.kt`,
  `ProviderAppendOnlyContext.kt`, and their test
  `ProviderAppendOnlyContextTest.kt`.
- **New/changed signatures:**
  - `FrozenSidecarSnapshot` becomes
    `data class FrozenSidecarSnapshot(val text: String, val markerId: String, val mappedInsertIndex: Int)`.
  - `ProviderAppendOnlyContext.resolveContextSidecarsForWire(built: String?): ResolvedSidecar?`
    where `data class ResolvedSidecar(val text: String, val markerId: String)`.
    Dedupe comparison stays text-only (`built.trim() == lastShippedSidecarText`,
    INV-3). The `markerId` is generated **inside** this call on the non-deduped
    path and remembered so the subsequent `endTurnFreezeSidecar` for the same
    live sidecar reuses the **same** id (D7 live→frozen id continuity).
  - `ProviderAppendOnlyContext.endTurnFreezeSidecar(sidecarText: String, markerId: String, mappedInsertIndex: Int)`.
  - `ProviderAppendOnlyContext.appendVolatileSidecar(sidecarText: String, markerId: String, delivery: VolatileContextDelivery)`.
  - `hasFrozenPreRetrievalSidecar()` unchanged (still keys on
    `PreRetrievalContextSidecar.isSidecarText(it.text)` — the marker prefix
    lives in the wrapper, not the body, F7).
- **Checkpoint:** `ProviderAppendOnlyContextTest` updated and green; dedupe,
  freeze, clear, and reanchor semantics unchanged; new tests assert
  live→frozen `markerId` continuity and that dedupe does not allocate a new
  id for a suppressed turn.

### Slice 3 — OpenAI chat-completions wire migration

- **Components touched:** `OpenAiCompatibleVolatilePlacement.kt`,
  `BaseOpenAiSdkInferenceService.kt` (the `completeSingleTurn` wire-assembly
  block, F2), `AppendOnlyOpenAiSidecarPlacementTest.kt`.
- **Changed signatures:**
  - `OpenAiCompatibleVolatilePlacement` loses
    `buildMappedBodyWithSidecars(...)` and `systemSidecarMessage(...)` (D8 —
    removed, not flagged). It is replaced by:
    - `fun embedSidecarsIntoUserMessages(
        mappedBody: List<ChatCompletionMessageParam>,
        frozenSidecars: List<FrozenSidecarSnapshot>,
        preRetrievalSidecar: ResolvedSidecar?,
        preRetrievalInsertIndex: Int?,
        liveSidecar: ResolvedSidecar?,
        tailInsertIndex: Int,
      ): List<ChatCompletionMessageParam>` — returns a body of the same
      length as `mappedBody` (no inserted system messages), with each user
      param at a sidecar-bearing index rewritten to carry the embedded
      suffix via `SidecarUserContentEmbedder`. Trailing-index frozen sidecars
      (indices >= `mappedBody.size`) attach to the last user message in the
      body, preserving the existing trailing-anchor semantics from F1.
  - `BaseOpenAiSdkInferenceService.completeSingleTurn`:
    - `wireSidecar` becomes `ResolvedSidecar?` from the new
      `resolveContextSidecarsForWire` signature.
    - `preRetrievalSidecarText: String?` becomes
      `preRetrievalSidecar: ResolvedSidecar?` (built from
      `PreRetrievalContextSidecar.format(...)` + a fresh marker; frozen
      immediately via the new `endTurnFreezeSidecar(text, markerId, idx)`).
    - The `messages` buildList no longer prepends sidecar system messages;
      it calls `embedSidecarsIntoUserMessages(...)` and prepends only the
      stable `stableSystemMessage` (INV-4).
    - `finalizeSidecarFreeze` threads `wireSidecar.markerId` into
      `endTurnFreezeSidecar`.
  - `buildUserMessage(content, useCacheControl)` / `buildUserMessageWithImages`
    are **not** changed here; the embedder operates on already-built user
    params by replacing the text content (and, for image messages, appending
    to the first text content part — F15/D3). A small internal helper
    `replaceUserTextContent(param, newContent)` is introduced to avoid
    rebuilding the whole param.
- **Checkpoint:** `AppendOnlyOpenAiSidecarPlacementTest` rewritten in place
  (D8) — assertions move from `messages[i].isSystem()` + sidecar text to:
  exactly one `role: system` message (the stable prompt); sidecar bodies
  appear as `[Sidecar-<uuid>: ...]` suffixes inside the correct user
  message's text content; frozen replay is byte-identical across turns;
  live sidecar lands only on the tail user message after `[Metadata:]`;
  deduped turns carry no live suffix; image-bearing user messages keep the
  sidecar in the text part. `./gradlew :feature:inference:testDebugUnitTest`
  green.

### Slice 4 — OpenAI Responses wire migration

- **Components touched:** `OpenAiCompatibleResponseSidecarPlacement.kt`,
  `AgentLoopOpenAiResponseMapper.kt` (only if the user-input content
  mutation needs a hook), Responses-side tests if present.
- **Changed signatures:**
  - `OpenAiCompatibleResponseSidecarPlacement.buildSegmentsWithSidecars(...)`
    stops emitting `ResponseWireSegment.SystemText(frozenText /
    preRetrieval / live)`. It instead returns `mappedSegments` with user
    `ResponseWireSegment.Input` segments at sidecar-bearing indices
    rewritten so their `ResponseInputItem.Message` content text carries the
    embedded suffix. Signature stays the same; behavior changes.
  - `remapFrozenSidecarsForResponses(...)` unchanged (still maps
    `mappedInsertIndex` across the two mappings).
  - A small internal helper rewrites the text content of a
    `ResponseWireSegment.Input`'s `ResponseInputItem.Message` (via
    `addInputTextContent` rebuild — F3/`ResponsesInputMessageSupport.kt:34-44`).
- **Checkpoint:** Responses-path tests (and `AgentLoopOpenAiResponseMapperTest`
  if it asserts sidecar segments) updated; no `ResponseWireSegment.SystemText`
  sidecar segments emitted; sidecar suffix present in user input content.
  `./gradlew :feature:inference:testDebugUnitTest` green.

### Slice 5 — INV-7 system-prompt rule

- **Components touched:** `SearchToolPromptComposer.kt` (the
  `metadataRules(compact)` block, F10), `SearchToolPromptComposerTest.kt`.
- **Changed signatures:** none (pure text addition to the composed prompt).
- **Content:** the full `# TEMPORAL CONTEXT:` block gains a stable,
  UUID-aware paragraph teaching the model that blocks of the form
  `[Sidecar-<uuid>: ...]` are host-injected session state (tool ledger,
  reference index, routing context), must be used silently for tool routing
  and reference lookups, and must never be quoted, summarized, or
  acknowledged. The compact one-line variant (approved, D6) is the
  equivalent single sentence. The rule text contains no UUIDs or timestamps
  itself (byte-stable).
- **Checkpoint:** `SearchToolPromptComposerTest` asserts the rule text is
  present in both compact and full variants and contains no per-turn
  variable content. `./gradlew :core:domain:testDebugUnitTest` green.

### Slice 6 — Diagnostics reclassification

- **Components touched:** `PromptCacheSidecarDiagnostics.kt` (F11),
  `PromptCacheSidecarDiagnosticsTest` if present, and the
  `logPromptCacheDiagnostics` call site in `BaseOpenAiSdkInferenceService`
  (only if the `SidecarWireCapture`/`SidecarManifest` shape changes).
- **Changed signatures:**
  - `PromptCacheSidecarDiagnostics.captureFrom(...)` gains a classification
    pass over `role: user` message content using `SidecarMarkerRegex.SPAN`,
    producing `UserContentSidecarSpan(wireIndex, markerId, bodyHash, length)`
    entries. `SidecarManifest` gains `userContentSpans: List<UserContentSidecarSpan>`;
    `wirePlacements` / `wireSystemCount` remain (now expected to contain
    only `stable_system`) as the INV-4 invariant check.
  - `SidecarDelta` gains a `userContentSpanDelta` summary (new/unchanged/
    removed by `markerId`).
  - `extractSystemJsonSpans` and `formatBlock0` unchanged (criterion 3
    instrumentation).
- **Checkpoint:** diagnostics tests assert sidecar spans are detected in
  user content, not system slots; `PROMPT_CACHE_SIDECAR` manifest reports
  the user-content spans. `./gradlew :feature:inference:testDebugUnitTest`
  green.

### Slice 7 — Validation + test rewrite (integration checkpoint)

- **Components touched:** test files enumerated in research F12; no further
  production code beyond what slices 1–6 require.
- **Tasks:**
  - Final pass over `AppendOnlyOpenAiSidecarPlacementTest`,
    `XaiPromptCacheLiveContextTest`, `ProviderAppendOnlyContextTest`,
    `AgentLoopOpenAiResponseMapperTest`, and any test asserting the old
    `role: system` sidecar shape — all rewritten to the new shape (D8).
  - Add explicit invariant tests: INV-1 (frozen replay byte-identical at
    original anchor across ≥3 turns), INV-2 (live suffix after `[Metadata:]`
    on tail only), INV-4 (exactly one `role: system`), INV-5
    (`stripSidecarBlocks` leaves user content + `[Metadata:]` intact and
    removes only UUID-form blocks), INV-7 (system prompt carries the rule).
  - Release-build emulator validation per planning-prompt §8: 5-turn DeepSeek
    direct API run, no `pm clear`, vault unlocked; logcat filters
    `PROMPT_CACHE`, `PROMPT_CACHE_JSON_BREAK`, `PROMPT_CACHE_SIDECAR`;
    success = ≥80% hitPct on turns 1–4 and on the new-live-sidecar turn.
- **Checkpoint:** `./gradlew :feature:inference:testDebugUnitTest`,
  `./gradlew :core:domain:testDebugUnitTest`, `./gradlew ktlintCheck`,
  `./gradlew detekt` all green; emulator validation success criteria met.

## Public contracts (signatures only)

New:
```kotlin
data class SidecarMarker(val markerId: String, val body: String)
object SidecarMarkerFactory { fun newMarker(body: String): SidecarMarker }
fun SidecarMarker.wireBlock(): String
object SidecarMarkerRegex { val SPAN: Regex }

object SidecarUserContentEmbedder {
    fun embed(baseContent: String, markers: List<SidecarMarker>): String
    fun stripSidecarBlocks(content: String): String
}

data class ResolvedSidecar(val text: String, val markerId: String)
```

Changed:
```kotlin
data class FrozenSidecarSnapshot(
    val text: String,
    val markerId: String,
    val mappedInsertIndex: Int,
)

// ProviderAppendOnlyContext
fun resolveContextSidecarsForWire(built: String?): ResolvedSidecar?
fun endTurnFreezeSidecar(sidecarText: String, markerId: String, mappedInsertIndex: Int)
fun appendVolatileSidecar(sidecarText: String, markerId: String, delivery: VolatileContextDelivery)

// OpenAiCompatibleVolatilePlacement (renamed behavior, same call site shape)
fun embedSidecarsIntoUserMessages(
    mappedBody: List<ChatCompletionMessageParam>,
    frozenSidecars: List<FrozenSidecarSnapshot>,
    preRetrievalSidecar: ResolvedSidecar?,
    preRetrievalInsertIndex: Int?,
    liveSidecar: ResolvedSidecar?,
    tailInsertIndex: Int,
): List<ChatCompletionMessageParam>
```

Removed (D8 — no flag, direct replacement):
```kotlin
OpenAiCompatibleVolatilePlacement.buildMappedBodyWithSidecars(...)
OpenAiCompatibleVolatilePlacement.systemSidecarMessage(...)
OpenAiCompatibleResponseSidecarPlacement SystemText sidecar emissions
```

No Room schema changes (INV-5, F9). No `:core:domain` → `:feature:inference`
dependency introduced (`SearchToolPromptComposer` is already in `:core:domain`
and only gains static rule text — D6). `SidecarUserContentEmbedder` lives in
`:feature:inference` and is not referenced from `:core:domain`.

## File map preview

**Create:**
- `feature/inference/.../appendonly/SidecarMarker.kt` — `SidecarMarker`,
  `SidecarMarkerFactory`, `wireBlock()`, `SidecarMarkerRegex`.
- `feature/inference/.../appendonly/SidecarUserContentEmbedder.kt` —
  `embed(...)` / `stripSidecarBlocks(...)`.
- `feature/inference/.../appendonly/ResolvedSidecar.kt` — small value type
  (or co-located in `SidecarMarker.kt`).
- `feature/inference/src/test/.../appendonly/SidecarUserContentEmbedderTest.kt`
  — Slice 1 unit tests.

**Modify:**
- `feature/inference/.../appendonly/FrozenSidecarSnapshot.kt` — add
  `markerId` (Slice 2).
- `feature/inference/.../appendonly/ProviderAppendOnlyContext.kt` —
  `ResolvedSidecar` return, markerId threading, live→frozen id continuity
  (Slice 2).
- `feature/inference/.../appendonly/OpenAiCompatibleVolatilePlacement.kt` —
  replace `buildMappedBodyWithSidecars` with
  `embedSidecarsIntoUserMessages`; remove `systemSidecarMessage` (Slice 3).
- `feature/inference/.../BaseOpenAiSdkInferenceService.kt` —
  `completeSingleTurn` wire-assembly block, `finalizeSidecarFreeze`,
  pre-retrieval sidecar resolution, `replaceUserTextContent` helper (Slice 3).
- `feature/inference/.../OpenAiCompatibleResponseSidecarPlacement.kt` —
  drop `SystemText` sidecar emissions, embed into user `Input` content
  (Slice 4).
- `feature/inference/.../AgentLoopOpenAiResponseMapper.kt` — only if a
  content-rewrite hook is needed for Slice 4.
- `core/domain/.../SearchToolPromptComposer.kt` — INV-7 rule text in
  `metadataRules(compact)` (Slice 5).
- `feature/inference/.../telemetry/PromptCacheSidecarDiagnostics.kt` —
  user-content span classification (Slice 6).

**Modify (tests, Slice 7):**
- `feature/inference/src/test/.../AppendOnlyOpenAiSidecarPlacementTest.kt`
- `feature/inference/src/test/.../XaiPromptCacheLiveContextTest.kt`
- `feature/inference/src/test/.../appendonly/ProviderAppendOnlyContextTest.kt`
- `feature/inference/src/test/.../AgentLoopOpenAiResponseMapperTest.kt`
- `core/domain/src/test/.../chat/SearchToolPromptComposerTest.kt`
- `feature/inference/src/test/.../telemetry/PromptCacheSidecarDiagnosticsTest.kt` (if present)

**Do not touch:** `LlamaInferenceServiceImpl.kt` (INV-9, F13),
`AnthropicInferenceServiceImpl.kt`, `GoogleInferenceServiceImpl.kt`,
`SessionIndexVolatilePlacement.kt` (follow-up slice, D4),
`ChatHistoryRehydrator.kt` / `ChatInferenceRequestPreparer.kt` (metadata
collision is out of scope; sidecars are wire-only and never reach these),
Room entities/DAOs.

## Test checkpoints

| After slice | Runnable check |
|-------------|----------------|
| 1 | `SidecarUserContentEmbedderTest` green; `:feature:inference:testDebugUnitTest` green (no wire path changed yet). |
| 2 | `ProviderAppendOnlyContextTest` green with new `markerId` shape; live→frozen id continuity asserted. |
| 3 | `AppendOnlyOpenAiSidecarPlacementTest` rewritten and green; chat-completions wire carries sidecars as user-content suffixes; exactly one `role: system` message. |
| 4 | Responses-path tests green; no sidecar `SystemText` segments. |
| 5 | `SearchToolPromptComposerTest` green; INV-7 rule present and byte-stable. |
| 6 | Diagnostics tests green; sidecar spans detected in user content. |
| 7 | `:feature:inference:testDebugUnitTest`, `:core:domain:testDebugUnitTest`, `ktlintCheck`, `detekt` green; emulator 5-turn DeepSeek run meets ≥80% hitPct success criteria. |

## Dependencies between slices

- Slice 1 → Slice 2 (snapshot/embedder primitives used by freeze threading).
- Slice 2 → Slices 3 and 4 (wire paths consume `ResolvedSidecar` and the
  new `FrozenSidecarSnapshot` shape).
- Slice 3 and Slice 4 are independent of each other but both depend on 2;
  ship 3 before 4 so the dominant chat-completions path is shippable first.
- Slice 5 depends on nothing but 1 (uses the marker form for the rule text)
  and should land before 7.
- Slice 6 depends on 3 and 4 (classifies the new wire shape).
- Slice 7 depends on 1–6 and is the integration checkpoint.

No horizontal-layer-only phase remains: every slice touches the embedder/
snapshot/wire/diagnostics together with its tests, end-to-end verifiable.