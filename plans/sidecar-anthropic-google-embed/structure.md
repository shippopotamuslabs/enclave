# Structure: sidecar-anthropic-google-embed

Vertical-slice execution outline. Signatures and types only — no implementation
bodies. Honors the human-approved decisions DA1–DA8 in `design.md` (approved
2026-06-19) and the invariants INV-1..INV-9 carried forward from
`plans/sidecar-user-embed/planning-prompt.md`.

Scope of this ticket: bring **Anthropic** and **Google** sidecar wire placement
into alignment with the already-shipped OpenAI suffix pattern — embed sidecars as
`[Sidecar-<uuid>: {body}]` suffixes via the shared `SidecarUserContentEmbedder`,
thread `ResolvedSidecar.markerId` through the live→frozen transition, and replace
the current raw-text prepend. The shared embedder/marker primitives and
`ProviderAppendOnlyContext` (already migrated by the OpenAI slice) are **reused
unchanged**; only the two providers and the shared
`SessionIndexVolatilePlacement` selection helper change. LiteRT is untouched
(INV-9, F14). No feature flag; direct replacement (DA8).

## Slice index

| # | Slice | One-line goal |
|---|-------|---------------|
| 1 | `SessionIndexVolatilePlacement` marker-returning sibling | Add `sidecarMarkersForSessionIndex(...) : List<SidecarMarker>` (DA1) returning markers (snapshot/live `markerId` preserved) instead of raw texts; first-ever direct unit test. No provider wired yet; old `sidecarTextsBeforeSessionIndex` retained so the module still compiles. |
| 2 | Anthropic suffix-embed + markerId continuity | Migrate `AnthropicInferenceServiceImpl` to embed sidecars as a suffix via the shared embedder at its existing content seam, keep the full `ResolvedSidecar`, and thread `markerId` into freeze (DA1/DA2/DA3/DA6). Rewrite `AnthropicInferenceServiceImplCachePrefixTest` to the suffix shape. Google still uses the old helper. |
| 3 | Google suffix-embed + markerId continuity | Migrate `GoogleInferenceServiceImpl` + `GoogleAgentLoopContentMapper` (`prependedText: String?` → `sidecarMarkers: List<SidecarMarker>`) to suffix-embed, keep the full `ResolvedSidecar`, thread `markerId` into freeze (DA1/DA2/DA3/DA6); **delete** the now-unused `sidecarTextsBeforeSessionIndex` to complete the rename (DA1). Add a NEW Google sidecar-wire-byte-shape test (none exists today — F12). |
| 4 | Validation (Open Q1/Q2) | CI-aligned checks green across the changed modules, plus the release-build emulator cache-validation protocol for Anthropic `cache_control` (Open Q1) and Google `CachedContent` reuse (Open Q2). No production code. |

Slice 1 is the shared foundation. Slices 2 and 3 are the two provider wire paths
and share the dependency that the marker-returning sibling (Slice 1) lands before
either provider consumes it. Slice 2 (Anthropic) before Slice 3 (Google) keeps a
single migrated provider shippable first; Google's slice also performs the
old-helper deletion (it is Google that migrates last). Slice 4 is the integration
+ live-cache validation checkpoint.

## Per-slice checklist

### Slice 1 — `SessionIndexVolatilePlacement` marker-returning sibling (DA1)

- **Components touched:**
  `feature/inference/.../appendonly/SessionIndexVolatilePlacement.kt`;
  new test `feature/inference/src/test/.../appendonly/SessionIndexVolatilePlacementTest.kt`.
- **New/changed types & signatures:**
  - New (same `internal object`, same package — no import needed for
    `SidecarMarker`/`ResolvedSidecar`/`FrozenSidecarSnapshot`):
    ```kotlin
    fun sidecarMarkersForSessionIndex(
        sessionIndex: Int,
        frozenSidecars: List<FrozenSidecarSnapshot>,
        preRetrievalSidecar: ResolvedSidecar?,
        preRetrievalInsertIndex: Int?,
        liveSidecar: ResolvedSidecar?,
        tailInsertIndex: Int,
    ): List<SidecarMarker>
    ```
    Emits `SidecarMarker(snapshot.markerId, snapshot.text)` for each frozen
    snapshot whose `mappedInsertIndex == sessionIndex`, then the pre-retrieval
    marker if its index matches, then `SidecarMarker(liveSidecar.markerId,
    liveSidecar.text)` when `sessionIndex == tailInsertIndex`. Mirrors the
    selection order of `OpenAiCompatibleVolatilePlacement
    .embedSidecarsIntoUserMessages` (frozen → pre-retrieval → live) but keyed on
    raw session index (no re-anchoring). Empty when nothing matches.
  - `resolvePreRetrievalInsertIndex(...)` / `resolveTailInsertIndex(...)`
    unchanged.
  - Old `sidecarTextsBeforeSessionIndex(...) : List<String>` is **retained for
    this slice only** (deleted in Slice 3) so both providers keep compiling.
- **Runnable checkpoint:** `./gradlew :feature:inference:testDebugUnitTest`
  green. New `SessionIndexVolatilePlacementTest` covers, in isolation: live
  marker emitted only at `tailInsertIndex` with the resolved `markerId`+text;
  frozen markers emitted at their `mappedInsertIndex` carrying each snapshot's
  `markerId`+text (order preserved); pre-retrieval path with `null`
  `preRetrievalSidecar` emits nothing (DA7 parity params); empty list for a
  non-matching index. (No provider behavior changed; existing suites stay green
  because providers still call the old helper.)

### Slice 2 — Anthropic suffix-embed + markerId continuity (DA1/DA2/DA3/DA6)

- **Components touched:**
  `feature/inference/.../AnthropicInferenceServiceImpl.kt`; reuses
  `SidecarUserContentEmbedder` and the Slice 1 sibling; test rewrite
  `feature/inference/src/test/.../AnthropicInferenceServiceImplCachePrefixTest.kt`.
- **New/changed types & signatures:**
  - `AnthropicInferenceServiceImpl.kt:230` — keep the full `ResolvedSidecar`:
    `val wireSidecar: ResolvedSidecar? = appendOnly.resolveContextSidecarsForWire(contextSidecars)`
    (drop `?.text`, DA2).
  - The local `prependIndexSidecars(index: Int, baseContent: String): String`
    (`:232-247`) becomes a suffix-embed helper (working name
    `embedIndexSidecars(index: Int, baseContent: String): String`) that calls
    `SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(...)` with
    `liveSidecar = wireSidecar`, `preRetrievalSidecar = null`,
    `preRetrievalInsertIndex = null`, `currentSidecarText` removed, and returns
    `SidecarUserContentEmbedder.embed(baseContent, markers)` (suffix). The five
    `messageContent` consumers (`:285/:320/:338/:353/…`) are unchanged; empty
    marker lists return `baseContent` untouched, so non-user and engine-internal
    rows are unaffected (DA3/DA6).
  - `freezeContextSidecarIfPresent(appendOnly: ProviderAppendOnlyContext,
    contextSidecars: ResolvedSidecar?, tailInsertIndex: Int)` — the
    `contextSidecars` param type changes from `String?` to `ResolvedSidecar?`;
    body threads the resolved id:
    `endTurnFreezeSidecar(sidecarText = contextSidecars.text,
    markerId = contextSidecars.markerId, mappedInsertIndex = tailInsertIndex)`,
    removing the throwaway mint at `:563` (DA2). Call site `:484-488` passes
    `contextSidecars = wireSidecar`.
  - `cache_control` placement (`AnthropicPromptCacheSupport.stableHistoryTailIndex`,
    `shouldAttachMessageCacheBreakpoint`, breakpoint attachment at `:256-258`,
    `:286/:321/…`) is **unchanged** (DA4). Image-bearing branch (`:296-326`)
    untouched; the suffix already lives inside the single `TextBlockParam`
    (DA6).
- **Runnable checkpoint:** `./gradlew :feature:inference:testDebugUnitTest` and
  `./gradlew :feature:inference:assembleDebug` green. `AnthropicInferenceServiceImplCachePrefixTest`
  rewritten in place to the suffix shape: U1 content is
  `"Hello Turn 1\n\n[Sidecar-<uuid>: Volatile Sidecar 1]"` (suffix, not
  prepend); frozen replay is byte-identical with the **same** `<uuid>` across
  turns (INV-1, live→frozen continuity / DA2); the live sidecar lands only on
  the tail user message after its content (INV-2); a content tail ending in
  `[Metadata: …]` keeps the sidecar appended **after** the metadata (DA3);
  cache-breakpoint placement assertions (system block both turns; stable-tail
  breakpoint at `size-2`) are unchanged (DA4). Google remains on the old prepend
  helper and stays green.

### Slice 3 — Google suffix-embed + markerId continuity (DA1/DA2/DA3/DA6)

- **Components touched:**
  `feature/inference/.../GoogleInferenceServiceImpl.kt`,
  `feature/inference/.../GoogleAgentLoopContentMapper.kt`,
  `feature/inference/.../appendonly/SessionIndexVolatilePlacement.kt` (delete old
  helper); new test
  `feature/inference/src/test/.../GoogleInferenceServiceImplSidecarWireTest.kt`.
- **New/changed types & signatures:**
  - `GoogleInferenceServiceImpl.kt:155` — keep the full `ResolvedSidecar`:
    `val wireSidecar: ResolvedSidecar? = appendOnly.resolveContextSidecarsForWire(contextSidecars)`
    (drop `?.text`, DA2).
  - The local `sidecarPrependTextForIndex(index: Int): String?` (`:157-167`)
    becomes `sidecarMarkersForIndex(index: Int): List<SidecarMarker>` calling
    `SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(...)` with
    `liveSidecar = wireSidecar`, `preRetrievalSidecar = null`,
    `preRetrievalInsertIndex = null`, `tailInsertIndex = liveSidecarInsertIndex`.
  - `mapAtIndex(message, index, attachImages)` (`:169-180`) passes
    `sidecarMarkers = sidecarMarkersForIndex(index)` instead of
    `prependedText = sidecarPrependTextForIndex(index)`.
  - `GoogleAgentLoopContentMapper.mapMessage(...)` signature change:
    ```kotlin
    fun mapMessage(
        message: AgentLoopMessage,
        index: Int,
        lastUserIndex: Int,
        imageUris: List<String>,
        sidecarMarkers: List<SidecarMarker> = emptyList(),
    ): Content?
    ```
    `prependedText: String?` is replaced by `sidecarMarkers: List<SidecarMarker>`;
    `contentText` is built as
    `SidecarUserContentEmbedder.embed(message.content, sidecarMarkers)` (suffix
    instead of the `"$prependedText\n\n${message.content}"` prefix at
    `:59-64`). New top-of-file imports for `SidecarMarker` and
    `SidecarUserContentEmbedder` from the `appendonly` subpackage. User row
    builds one `Part.fromText(...)` first then image parts (`:84-89`), so the
    suffix lands in the leading text part and image parts are untouched (DA6).
  - Freeze (`:324-330`): guard stays `if (wireSidecar != null)`; body becomes
    `endTurnFreezeSidecar(sidecarText = wireSidecar.text,
    markerId = wireSidecar.markerId, mappedInsertIndex = liveSidecarInsertIndex)`,
    removing the throwaway mint at `:327` (DA2).
  - `GooglePromptCacheSession` / `CacheSignature` **unchanged** (DA5): the
    signature hashes raw `message.content`, not the sidecar-mapped `Content`.
  - **Delete** `SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex(...)`
    now that Anthropic (Slice 2) and Google (this slice) are its last consumers —
    completes the DA1 rename.
- **Runnable checkpoint:** `./gradlew :feature:inference:testDebugUnitTest` and
  `./gradlew :feature:inference:assembleDebug` green. NEW
  `GoogleInferenceServiceImplSidecarWireTest` (closing the F12 gap) asserts: the
  live sidecar is appended as a `[Sidecar-<uuid>: {body}]` suffix on the tail
  user `Content` text part after the user content/metadata (INV-2/DA3); frozen
  replay is byte-identical with the **same** `<uuid>` across turns (INV-1/DA2); a
  deduped turn omits the live suffix while frozen anchors replay unchanged
  (INV-3); an image-bearing user turn keeps the suffix in the leading text part
  with image parts untouched (DA6). Both providers now route through markers; the
  old helper is gone.

### Slice 4 — Validation (Open Q1/Q2)

- **Components touched:** none (no production or test code beyond what Slices
  1–3 require); validation/CI only.
- **Tasks:**
  - CI-aligned checks across the changed surface:
    `./gradlew :feature:inference:testDebugUnitTest`,
    `./gradlew :core:domain:testDebugUnitTest`,
    `./gradlew :feature:inference:assembleDebug`,
    `./gradlew ktlintCheck`, `./gradlew detekt` (AGENTS.md §9; detekt
    `maxIssues: 0`).
  - **Open Q1 — Anthropic `cache_control` (release-build emulator):** multi-turn
    run (≥3 turns) against the live Anthropic path on a prompt-cache-eligible
    model; confirm the cached prefix hit-rate holds after the one-time
    prefix→suffix migration turn, and that frozen-replay byte stability keeps the
    cached prefix intact turn-over-turn (DA4 stands; no code change).
  - **Open Q2 — Google `CachedContent` reuse (release-build emulator):**
    multi-turn run confirming server-cache create/reuse behaves under the suffix
    byte change — specifically that a session whose cache was created with old
    prefix bytes does not serve mismatched cached content after migration, and
    fresh sessions rebuild cleanly (DA5 stands; no code change).
- **Runnable checkpoint:** all CI-aligned Gradle checks green; emulator
  validation success criteria met for both providers (cache hit-rate holds;
  no mismatched-cache serving).

## Public contracts (signatures only)

New (Slice 1):
```kotlin
// SessionIndexVolatilePlacement (internal object, package ...appendonly)
fun sidecarMarkersForSessionIndex(
    sessionIndex: Int,
    frozenSidecars: List<FrozenSidecarSnapshot>,
    preRetrievalSidecar: ResolvedSidecar?,
    preRetrievalInsertIndex: Int?,
    liveSidecar: ResolvedSidecar?,
    tailInsertIndex: Int,
): List<SidecarMarker>
```

Changed (Slices 2–3):
```kotlin
// AnthropicInferenceServiceImpl — consumer type change at the resolve seam
val wireSidecar: ResolvedSidecar? = appendOnly.resolveContextSidecarsForWire(contextSidecars) // was ...?.text

// AnthropicInferenceServiceImpl — freeze helper param type change (String? -> ResolvedSidecar?)
private fun freezeContextSidecarIfPresent(
    appendOnly: ProviderAppendOnlyContext,
    contextSidecars: ResolvedSidecar?,
    tailInsertIndex: Int,
)

// GoogleInferenceServiceImpl — consumer type change at the resolve seam
val wireSidecar: ResolvedSidecar? = appendOnly.resolveContextSidecarsForWire(contextSidecars) // was ...?.text

// GoogleAgentLoopContentMapper — mapper signature change (prependedText -> sidecarMarkers)
fun mapMessage(
    message: AgentLoopMessage,
    index: Int,
    lastUserIndex: Int,
    imageUris: List<String>,
    sidecarMarkers: List<SidecarMarker> = emptyList(),
): Content?
```

Removed (Slice 3 — completes the DA1 rename, direct replacement per DA8):
```kotlin
SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex(...) : List<String>
```

Reused **unchanged** (no signature change — already migrated by the OpenAI slice):
```kotlin
fun resolveContextSidecarsForWire(built: String?): ResolvedSidecar?
fun endTurnFreezeSidecar(sidecarText: String, markerId: String, mappedInsertIndex: Int)
fun SidecarUserContentEmbedder.embed(baseContent: String, markers: List<SidecarMarker>): String
data class SidecarMarker(val markerId: String, val body: String)
data class ResolvedSidecar(val text: String, val markerId: String)
data class FrozenSidecarSnapshot(val text: String, val markerId: String, val mappedInsertIndex: Int)
```

No Room schema changes (INV-5/§3.4). No new `:core:domain` → `:feature:inference`
dependency. No `cache_control` (DA4) or `CachedContent`/`CacheSignature` (DA5)
code change. Pre-retrieval params remain on the new sibling for signature parity
but both providers pass `null` (DA7).

## File map preview

**Create:**
- `feature/inference/src/test/.../appendonly/SessionIndexVolatilePlacementTest.kt`
  — first-ever direct unit test for the marker-returning sibling (Slice 1).
- `feature/inference/src/test/.../GoogleInferenceServiceImplSidecarWireTest.kt`
  — NEW Google sidecar-wire-byte-shape test (closes F12 gap; Slice 3).

**Modify:**
- `feature/inference/.../appendonly/SessionIndexVolatilePlacement.kt` — add
  `sidecarMarkersForSessionIndex(...)` (Slice 1); delete old
  `sidecarTextsBeforeSessionIndex(...)` once both providers are migrated
  (Slice 3).
- `feature/inference/.../AnthropicInferenceServiceImpl.kt` — keep full
  `ResolvedSidecar`, replace `prependIndexSidecars` with a shared-embedder suffix
  helper, thread `markerId` into `freezeContextSidecarIfPresent` (param
  `String?` → `ResolvedSidecar?`), remove throwaway mint (Slice 2).
- `feature/inference/.../GoogleInferenceServiceImpl.kt` — keep full
  `ResolvedSidecar`, replace `sidecarPrependTextForIndex` with
  `sidecarMarkersForIndex`, pass `sidecarMarkers` into `mapAtIndex`, thread
  `markerId` into freeze, remove throwaway mint (Slice 3).
- `feature/inference/.../GoogleAgentLoopContentMapper.kt` — `mapMessage`
  `prependedText: String?` → `sidecarMarkers: List<SidecarMarker>`; build
  `contentText` via `SidecarUserContentEmbedder.embed` (suffix); add the two
  top-of-file imports (Slice 3).
- `feature/inference/src/test/.../AnthropicInferenceServiceImplCachePrefixTest.kt`
  — rewrite from the prepend byte shape to the suffix shape + live→frozen
  `markerId` continuity + metadata-before-sidecar ordering (Slice 2).

**Do not touch:**
- `feature/inference/.../LlamaInferenceServiceImpl.kt` — LiteRT path, out of
  scope (INV-9, F14).
- `feature/inference/.../appendonly/SidecarUserContentEmbedder.kt`,
  `SidecarMarker.kt`, `FrozenSidecarSnapshot.kt`,
  `ProviderAppendOnlyContext.kt` — shared embedder/marker/snapshot/append-only
  primitives, reused unchanged (F5).
- `feature/inference/.../appendonly/OpenAiCompatibleVolatilePlacement.kt`,
  `feature/inference/.../BaseOpenAiSdkInferenceService.kt` — already migrated by
  the OpenAI slice; reference only (F13).
- `feature/inference/.../AnthropicPromptCacheSupport.kt` — `cache_control`
  breakpoints unchanged (DA4).
- `feature/inference/.../GooglePromptCacheSession.kt` and its `CacheSignature` —
  `CachedContent` keying unchanged (DA5).
- `ProviderMessageDigest` / `AppendOnlyMessageLog` — append-only digest /
  Pi-invariant internals (INV-6).
- Room entities/DAOs and any persistence schema — sidecars are wire-only
  (INV-5/§3.4).

## Test checkpoints

| After slice | Runnable check |
|-------------|----------------|
| 1 | `SessionIndexVolatilePlacementTest` green (marker emission: live at tail, frozen at anchor with snapshot `markerId`, null pre-retrieval, empty otherwise); `./gradlew :feature:inference:testDebugUnitTest` green (no provider wire changed yet). |
| 2 | `AnthropicInferenceServiceImplCachePrefixTest` rewritten and green: suffix shape, byte-identical frozen replay with stable `<uuid>`, sidecar after `[Metadata:]`, unchanged cache breakpoints; `:feature:inference:testDebugUnitTest` + `:feature:inference:assembleDebug` green. |
| 3 | NEW `GoogleInferenceServiceImplSidecarWireTest` green: suffix in tail text part, byte-identical frozen replay with stable `<uuid>`, deduped turn omits live, image parts untouched; old helper deleted; `:feature:inference:testDebugUnitTest` + `:feature:inference:assembleDebug` green. |
| 4 | `:feature:inference:testDebugUnitTest`, `:core:domain:testDebugUnitTest`, `:feature:inference:assembleDebug`, `ktlintCheck`, `detekt` all green; release-build emulator cache validation success criteria met for Anthropic (Open Q1) and Google (Open Q2). |

## Dependencies between slices

- **Slice 1 → Slices 2 and 3** — both providers consume
  `sidecarMarkersForSessionIndex`; the marker-returning sibling must land first
  (the explicit ordering dependency in the brief).
- **Slice 2 (Anthropic) before Slice 3 (Google)** — keeps one migrated provider
  shippable first and serializes the shared-file edit: the old
  `sidecarTextsBeforeSessionIndex` is retained through Slice 2 (Google still uses
  it) and deleted in Slice 3 (Google migrates last). Slices 2 and 3 are otherwise
  independent provider paths and could be authored in parallel by separate
  subagents **provided** Slice 1 has landed and the old-helper deletion is
  reserved to whichever provider slice merges last (Google here); a parallel
  split must coordinate that single shared-file deletion to avoid a merge
  conflict.
- **Slice 4 depends on 1–3** — integration + live-cache validation checkpoint;
  no production code.

No horizontal-layer-only phase remains: Slice 1 ships a directly unit-testable
selection primitive; Slices 2 and 3 each migrate one provider end-to-end (resolve
seam → embed seam → freeze) together with its wire-byte test; Slice 4 validates
the assembled behavior on-device.

## Self-review

- **Vertically integrated:** Slice 1 is a self-contained, directly unit-tested
  primitive (input session index + snapshots/resolved sidecars → output markers);
  Slices 2 and 3 each carry one provider from the resolve seam through the embed
  seam to the freeze, asserted by a wire-byte test; Slice 4 validates end-to-end
  on a release build.
- **Checkpoints after each slice:** every slice row has an explicit runnable
  Gradle/test checkpoint.
- **File map aligns with DA1–DA8:** DA1 sibling + rename (Slices 1/3); DA2 keep
  `ResolvedSidecar` + thread `markerId`, remove throwaway mint (Slices 2/3); DA3
  metadata-agnostic suffix (embed onto `msg.content`); DA4/DA5 no cache code
  change (listed Do-not-touch); DA6 single text block/part (image branches
  untouched); DA7 pre-retrieval `null` with parity params; DA8 one ticket, no
  flag, direct replacement (old helper deleted, not flagged).
- **No reopened decisions, no placeholders, signatures only** — no `TBD`/`TODO`,
  no implementation bodies, no algorithmic detail.
