# Spec: sidecar-anthropic-google-embed

Tactical implementation plan. Expands the human-approved
`plans/sidecar-anthropic-google-embed/structure.md` (4 vertical slices, approved
2026-06-19) into ordered tasks + a file map + signature contracts. Honors the
human-approved decisions DA1–DA8 in `design.md` (approved 2026-06-19) and the
invariants INV-1..INV-9 carried forward from
`plans/sidecar-user-embed/planning-prompt.md`. Scenario IDs referenced below
(S-SIP-xx / S-ANTH-xx / S-GOOG-xx / S-VAL-xx) are defined in
`plans/sidecar-anthropic-google-embed/test_spec.md`.

This document contains **no runnable code** — only declarations/signatures and
prose. Constitution: `AGENTS.md` §3.1 (clean architecture), §3.3 (test-coverage
mandate), §3.4 (schema lock), §3.5 (BYOK isolated from on-device), §9
(executable validation, detekt `maxIssues: 0`).

---

## 1. Feature summary

Bring **Anthropic** and **Google** sidecar wire placement into alignment with the
already-shipped OpenAI suffix pattern. Today both providers embed context
sidecars as a **raw-text PREPEND before user content** (`\n\n`-joined, no
delimiter), **discard** the `ResolvedSidecar.markerId` (`?.text`), and **mint a
throwaway marker at freeze** — breaking live→frozen continuity (D7). After this
change, both providers:

- Embed sidecars as `[Sidecar-<uuid>: {body}]` **suffix** annotations on the
  user message text, appended **after** the user-visible content and after any
  `[Metadata:]` suffix (INV-2), via the shared `SidecarUserContentEmbedder`
  (D3 — the wire-block format lives in exactly one place).
- Keep the full `ResolvedSidecar` and thread `wireSidecar.markerId` into
  `endTurnFreezeSidecar`, so a frozen snapshot replays byte-identically with the
  **same** `<uuid>` the live turn shipped (D7/INV-1).
- Leave dedupe (INV-3), the append-only Pi digest (INV-6), Anthropic
  `cache_control` breakpoints (DA4), and Google `CachedContent`/`CacheSignature`
  (DA5) **unchanged**.

Sidecars stay wire-only — not persisted, not shown in UI (INV-5). LiteRT
(`LlamaInferenceServiceImpl`) is out of scope (INV-9). Pre-retrieval context is
merged upstream into `msg.content` on these providers and is never delivered as a
sidecar; both providers continue to pass `null` (DA7). The INV-7 model rule is
already in the shared `SearchToolPromptComposer` system prompt and reaches both
providers — no new rule is added (F15).

---

## 2. Vertical slices

| # | Slice | One-line goal | Scenarios covered |
|---|-------|---------------|-------------------|
| 1 | `SessionIndexVolatilePlacement` marker-returning sibling | Add `sidecarMarkersForSessionIndex(...) : List<SidecarMarker>` (DA1) returning markers (snapshot/live `markerId` preserved) instead of raw texts; first-ever direct unit test. Old `sidecarTextsBeforeSessionIndex` retained so the module still compiles; no provider wired yet. | S-SIP-01, S-SIP-02, S-SIP-03, S-SIP-04, S-SIP-05, S-SIP-06, S-SIP-07 |
| 2 | Anthropic suffix-embed + markerId continuity | Migrate `AnthropicInferenceServiceImpl` to embed sidecars as a suffix via the shared embedder at its existing content seam, keep the full `ResolvedSidecar`, and thread `markerId` into freeze (DA1/DA2/DA3/DA6). Rewrite `AnthropicInferenceServiceImplCachePrefixTest` to the suffix shape. Google still uses the old helper. | S-ANTH-01, S-ANTH-02, S-ANTH-03, S-ANTH-04, S-ANTH-05, S-ANTH-06, S-ANTH-07, S-ANTH-08 |
| 3 | Google suffix-embed + markerId continuity | Migrate `GoogleInferenceServiceImpl` + `GoogleAgentLoopContentMapper` (`prependedText: String?` → `sidecarMarkers: List<SidecarMarker>`) to suffix-embed, keep the full `ResolvedSidecar`, thread `markerId` into freeze (DA1/DA2/DA3/DA6); **delete** the now-unused `sidecarTextsBeforeSessionIndex` to complete the rename (DA1/DA8). Add a NEW Google sidecar-wire-byte-shape test (none exists today — F12). | S-GOOG-01, S-GOOG-02, S-GOOG-03, S-GOOG-04, S-GOOG-05, S-GOOG-06 |
| 4 | Validation (Open Q1/Q2) | CI-aligned checks green across the changed modules, plus the release-build emulator cache-validation protocol for Anthropic `cache_control` (Open Q1) and Google `CachedContent` reuse (Open Q2). No production code. | S-VAL-01, S-VAL-02, S-VAL-03 |

Slice 1 is the shared foundation (both providers consume it). Slice 2 (Anthropic)
lands before Slice 3 (Google); Google migrates last and performs the old-helper
deletion. Slice 4 is the integration + live-cache validation checkpoint.

---

## 3. File structure

All paths relative to `/home/sean/Code/pocket-crew`. Package prefix for source:
`com.browntowndev.enclave.feature.inference`.

### Create

| File | Slice | Responsibility |
|------|-------|----------------|
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/appendonly/SessionIndexVolatilePlacementTest.kt` | 1 | First-ever direct unit test for the marker-returning sibling: marker selection (live at tail, frozen at anchor with snapshot `markerId`, pre-retrieval parity, empty otherwise, order, no-remint). Covers S-SIP-01..07. |
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/GoogleInferenceServiceImplSidecarWireTest.kt` | 3 | NEW Google sidecar-wire-byte-shape test (closes the F12 gap). Captures `List<Content>` via a MockK `slot`, asserts the `[Sidecar-<uuid>: …]` suffix in the tail text part, frozen replay byte-identity, dedupe, image parts untouched, and live→frozen `markerId` continuity. Covers S-GOOG-01..06. |

### Modify

| File | Slice | Responsibility |
|------|-------|----------------|
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/SessionIndexVolatilePlacement.kt` | 1, 3 | Slice 1: add `sidecarMarkersForSessionIndex(...) : List<SidecarMarker>` (DA1); keep `sidecarTextsBeforeSessionIndex` so both providers compile. Slice 3: **delete** `sidecarTextsBeforeSessionIndex(...) : List<String>` once both providers are migrated (completes the DA1 rename). `resolvePreRetrievalInsertIndex` / `resolveTailInsertIndex` unchanged. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicInferenceServiceImpl.kt` | 2 | Keep the full `ResolvedSidecar` at `:230` (drop `?.text`, DA2); replace the local `prependIndexSidecars` (`:232-247`) with a suffix-embed helper calling the Slice 1 sibling + `SidecarUserContentEmbedder.embed` (DA1/DA3/DA6); change `freezeContextSidecarIfPresent` param `String?`→`ResolvedSidecar?` and thread `markerId`, removing the throwaway mint at `:563` (DA2). `cache_control` logic (`:207-221`, `:256-258`, `:286/:321/:339/:354/:373`) and the image branch (`:296-326`) are untouched (DA4/DA6). |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleInferenceServiceImpl.kt` | 3 | Keep the full `ResolvedSidecar` at `:155` (drop `?.text`, DA2); replace the local `sidecarPrependTextForIndex` (`:157-167`) with `sidecarMarkersForIndex(index): List<SidecarMarker>` calling the Slice 1 sibling; pass `sidecarMarkers` into `mapAtIndex` (`:169-180`); thread `markerId` into freeze (`:324-330`), removing the throwaway mint at `:327` (DA2). `GooglePromptCacheSession`/`CacheSignature` interaction unchanged (DA5). |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleAgentLoopContentMapper.kt` | 3 | `mapMessage(...)` signature change: `prependedText: String? = null` → `sidecarMarkers: List<SidecarMarker> = emptyList()`; build `contentText` via `SidecarUserContentEmbedder.embed(message.content, sidecarMarkers)` (suffix) instead of the `"$prependedText\n\n${message.content}"` prefix (`:59-64`); add top-of-file imports for `SidecarMarker` and `SidecarUserContentEmbedder` from the `appendonly` subpackage. The user row still builds `Part.fromText(...)` first then image parts (`:84-89`), so the suffix lands in the leading text part and image parts are untouched (DA6). |
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/AnthropicInferenceServiceImplCachePrefixTest.kt` | 2 | Rewrite in place from the prepend byte shape to the suffix shape: live tail suffix, byte-identical frozen replay with a stable `<uuid>` (live→frozen continuity), metadata-before-sidecar ordering, and unchanged cache-breakpoint placement. Covers S-ANTH-01..08. |

### Do not touch

| File / area | Reason |
|-------------|--------|
| `feature/inference/.../LlamaInferenceServiceImpl.kt` | LiteRT path, out of scope (INV-9, F14). |
| `feature/inference/.../appendonly/SidecarUserContentEmbedder.kt`, `SidecarMarker.kt`, `FrozenSidecarSnapshot.kt`, `ProviderAppendOnlyContext.kt` | Shared embedder/marker/snapshot/append-only primitives, already migrated by the OpenAI slice; reused unchanged (F5). |
| `feature/inference/.../appendonly/OpenAiCompatibleVolatilePlacement.kt`, `feature/inference/.../BaseOpenAiSdkInferenceService.kt` | Already migrated by the OpenAI slice; reference only (F13). |
| `feature/inference/.../AnthropicPromptCacheSupport.kt` | `cache_control` breakpoints unchanged (DA4). |
| `feature/inference/.../GooglePromptCacheSession.kt` and its `CacheSignature` | `CachedContent` keying unchanged (DA5). |
| `ProviderMessageDigest` / `AppendOnlyMessageLog` | Append-only digest / Pi-invariant internals (INV-6). |
| Room entities/DAOs and any persistence schema | Sidecars are wire-only (INV-5 / §3.4). |
| `core/domain/.../SearchToolPromptComposer.kt` | INV-7 model rule already present and shared (F15). |

---

## 4. API contracts and interfaces (signatures only)

Match `structure.md` exactly. No bodies.

### New — Slice 1 (`SessionIndexVolatilePlacement`, `internal object`, package `...appendonly`)

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

Selection order (no re-anchoring; keyed on raw session index, mirroring
`OpenAiCompatibleVolatilePlacement.embedSidecarsIntoUserMessages`): emit
`SidecarMarker(snapshot.markerId, snapshot.text)` for each frozen snapshot whose
`mappedInsertIndex == sessionIndex` (in order), then
`SidecarMarker(preRetrievalSidecar.markerId, preRetrievalSidecar.text)` if
`preRetrievalSidecar != null && preRetrievalInsertIndex == sessionIndex`, then
`SidecarMarker(liveSidecar.markerId, liveSidecar.text)` when
`liveSidecar != null && sessionIndex == tailInsertIndex`. Empty list when nothing
matches. `SidecarMarker`, `ResolvedSidecar`, and `FrozenSidecarSnapshot` are in
the same package — no new imports needed.

### Changed — Slice 2 (Anthropic)

```kotlin
// Resolve seam (was `...resolveContextSidecarsForWire(contextSidecars)?.text`, AnthropicInferenceServiceImpl.kt:230)
val wireSidecar: ResolvedSidecar? = appendOnly.resolveContextSidecarsForWire(contextSidecars)

// Local embed helper replacing prependIndexSidecars (AnthropicInferenceServiceImpl.kt:232-247).
// Working name; returns content unchanged when the marker list is empty (DA3/DA6).
fun embedIndexSidecars(index: Int, baseContent: String): String

// Freeze helper param type change (was contextSidecars: String?, AnthropicInferenceServiceImpl.kt:555-559)
private fun freezeContextSidecarIfPresent(
    appendOnly: ProviderAppendOnlyContext,
    contextSidecars: ResolvedSidecar?,
    tailInsertIndex: Int,
)
```

The freeze body threads the resolved id —
`endTurnFreezeSidecar(sidecarText = contextSidecars.text, markerId = contextSidecars.markerId, mappedInsertIndex = tailInsertIndex)`
— removing the throwaway mint at `:563`. The call site (`:484-488`) passes
`contextSidecars = wireSidecar`. `embedIndexSidecars` calls
`sidecarMarkersForSessionIndex(... liveSidecar = wireSidecar, preRetrievalSidecar = null, preRetrievalInsertIndex = null ...)`
and returns `SidecarUserContentEmbedder.embed(baseContent, markers)`.

### Changed — Slice 3 (Google)

```kotlin
// Resolve seam (was `...resolveContextSidecarsForWire(contextSidecars)?.text`, GoogleInferenceServiceImpl.kt:155)
val wireSidecar: ResolvedSidecar? = appendOnly.resolveContextSidecarsForWire(contextSidecars)

// Local selection helper replacing sidecarPrependTextForIndex (GoogleInferenceServiceImpl.kt:157-167)
fun sidecarMarkersForIndex(index: Int): List<SidecarMarker>

// Mapper signature change (was prependedText: String? = null, GoogleAgentLoopContentMapper.kt:33-39)
fun mapMessage(
    message: AgentLoopMessage,
    index: Int,
    lastUserIndex: Int,
    imageUris: List<String>,
    sidecarMarkers: List<SidecarMarker> = emptyList(),
): Content?
```

`sidecarMarkersForIndex` calls
`sidecarMarkersForSessionIndex(... liveSidecar = wireSidecar, preRetrievalSidecar = null, preRetrievalInsertIndex = null, tailInsertIndex = liveSidecarInsertIndex)`.
`mapAtIndex` (`:169-180`) passes `sidecarMarkers = sidecarMarkersForIndex(index)`.
The mapper builds `contentText = SidecarUserContentEmbedder.embed(message.content, sidecarMarkers)`.
Freeze (`:324-330`) keeps the `if (wireSidecar != null)` guard and threads
`markerId = wireSidecar.markerId`, removing the throwaway mint at `:327`.

### Removed — Slice 3 (completes the DA1 rename, direct replacement per DA8)

```kotlin
SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex(...) : List<String>
```

### Reused unchanged (already migrated by the OpenAI slice — no signature change)

```kotlin
fun resolveContextSidecarsForWire(built: String?): ResolvedSidecar?
fun endTurnFreezeSidecar(sidecarText: String, markerId: String, mappedInsertIndex: Int)
fun SidecarUserContentEmbedder.embed(baseContent: String, markers: List<SidecarMarker>): String
fun SidecarMarker.wireBlock(): String          // "[Sidecar-$markerId: $body]"
data class SidecarMarker(val markerId: String, val body: String)
data class ResolvedSidecar(val text: String, val markerId: String)
data class FrozenSidecarSnapshot(val text: String, val markerId: String, val mappedInsertIndex: Int)
```

---

## 5. Data model / schema changes

**NONE.** Sidecars are wire-only: not persisted, not surfaced in the UI (INV-5).
No Room entity, DAO, or migration changes (AGENTS.md §3.4 schema lock). No new
`:core:domain` → `:feature:inference` dependency (§3.1). The only state involved
is the in-memory, session-scoped `FrozenSidecarSnapshot` list already owned by
`ProviderAppendOnlyContext` (unchanged).

---

## 6. Ordered implementation tasks

Each behavior-changing task references ≥1 scenario ID (defined in `test_spec.md`).
Tasks are grouped by slice and ordered for sequential execution.

### Slice 1 — `SessionIndexVolatilePlacement` marker-returning sibling (DA1)

- **T1.1 (DA1).** In `SessionIndexVolatilePlacement.kt`, add
  `sidecarMarkersForSessionIndex(...) : List<SidecarMarker>` with the selection
  order frozen → pre-retrieval → live (signature in §4). Keep
  `sidecarTextsBeforeSessionIndex` intact for this slice so both providers still
  compile. → S-SIP-01, S-SIP-02, S-SIP-03, S-SIP-04, S-SIP-05, S-SIP-06, S-SIP-07
- **T1.2 (§3.3).** Author `SessionIndexVolatilePlacementTest` covering marker
  selection in isolation: live at tail only, frozen at anchor with snapshot
  `markerId`, order preservation, `null`-pre-retrieval parity, non-matching index
  empty, and the no-remint mutation guard. → S-SIP-01, S-SIP-02, S-SIP-03,
  S-SIP-04, S-SIP-05, S-SIP-06, S-SIP-07
- **T1.3 (checkpoint).** `./gradlew :feature:inference:testDebugUnitTest` green;
  existing suites unaffected (providers still call the old helper). Build gate —
  no scenario.

### Slice 2 — Anthropic suffix-embed + markerId continuity (DA1/DA2/DA3/DA6)

- **T2.1 (DA2).** At `AnthropicInferenceServiceImpl.kt:230` keep the full
  `ResolvedSidecar` (drop `?.text`):
  `val wireSidecar: ResolvedSidecar? = appendOnly.resolveContextSidecarsForWire(contextSidecars)`.
  → S-ANTH-01, S-ANTH-04
- **T2.2 (DA1/DA3/DA6).** Replace `prependIndexSidecars` (`:232-247`) with the
  suffix-embed helper `embedIndexSidecars(index, baseContent)` that calls
  `sidecarMarkersForSessionIndex(... liveSidecar = wireSidecar, preRetrievalSidecar = null, preRetrievalInsertIndex = null ...)`
  and returns `SidecarUserContentEmbedder.embed(baseContent, markers)`. Keep the
  call site `val messageContent = embedIndexSidecars(index, msg.content)` (`:260`)
  and its five consumers (`:285/:320/:338/:353/:372`) unchanged; an empty marker
  list returns `baseContent` untouched, so engine-internal and non-user rows are
  unaffected. → S-ANTH-01, S-ANTH-02, S-ANTH-03, S-ANTH-06, S-ANTH-07, S-ANTH-08
- **T2.3 (DA2).** Change `freezeContextSidecarIfPresent` (`:555-567`) param
  `contextSidecars: String?` → `ResolvedSidecar?`; thread
  `markerId = contextSidecars.markerId`, removing the throwaway mint at `:563`.
  Update the call site (`:484-488`) to pass `contextSidecars = wireSidecar`.
  → S-ANTH-02, S-ANTH-04, S-ANTH-08
- **T2.4 (DA4).** Confirm no change to `AnthropicPromptCacheSupport`
  (`stableHistoryTailIndex`, `shouldAttachMessageCacheBreakpoint`) or the
  breakpoint-attachment sites; the suffix lives inside the same single
  `TextBlockParam` as before. → S-ANTH-05
- **T2.5 (§3.3).** Rewrite `AnthropicInferenceServiceImplCachePrefixTest` to the
  suffix shape (live tail suffix; frozen replay byte-identity with stable
  `<uuid>`; metadata-before-sidecar ordering; unchanged breakpoints). → S-ANTH-01,
  S-ANTH-02, S-ANTH-03, S-ANTH-04, S-ANTH-05, S-ANTH-06, S-ANTH-07, S-ANTH-08
- **T2.6 (checkpoint).** `./gradlew :feature:inference:testDebugUnitTest` and
  `./gradlew :feature:inference:assembleDebug` green; Google remains on the old
  helper and stays green. Build gate — no scenario.

### Slice 3 — Google suffix-embed + markerId continuity (DA1/DA2/DA3/DA6)

- **T3.1 (DA2).** At `GoogleInferenceServiceImpl.kt:155` keep the full
  `ResolvedSidecar` (drop `?.text`). → S-GOOG-01, S-GOOG-05
- **T3.2 (DA1).** Replace `sidecarPrependTextForIndex(index): String?`
  (`:157-167`) with `sidecarMarkersForIndex(index): List<SidecarMarker>` calling
  `sidecarMarkersForSessionIndex(... liveSidecar = wireSidecar, preRetrievalSidecar = null, preRetrievalInsertIndex = null, tailInsertIndex = liveSidecarInsertIndex)`.
  → S-GOOG-01, S-GOOG-02, S-GOOG-03
- **T3.3 (DA1).** In `mapAtIndex` (`:169-180`), pass
  `sidecarMarkers = sidecarMarkersForIndex(index)` instead of
  `prependedText = sidecarPrependTextForIndex(index)`. → S-GOOG-01, S-GOOG-02,
  S-GOOG-03, S-GOOG-04
- **T3.4 (DA1/DA3/DA6).** Change `GoogleAgentLoopContentMapper.mapMessage`
  (`:33-39`) param `prependedText: String? = null` →
  `sidecarMarkers: List<SidecarMarker> = emptyList()`; build
  `contentText = SidecarUserContentEmbedder.embed(message.content, sidecarMarkers)`
  (`:59-64`); add top-of-file imports for `SidecarMarker` and
  `SidecarUserContentEmbedder`. The user row keeps `Part.fromText(...)` first then
  image parts (`:84-89`). → S-GOOG-01, S-GOOG-04, S-GOOG-06
- **T3.5 (DA2).** In the freeze block (`:324-330`), keep the
  `if (wireSidecar != null)` guard and thread
  `markerId = wireSidecar.markerId`, removing the throwaway mint at `:327`.
  → S-GOOG-02, S-GOOG-05, S-GOOG-06
- **T3.6 (DA1/DA8).** Delete `SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex(...)`
  now that Anthropic (Slice 2) and Google (this slice) no longer call it —
  completes the rename. Build gate — no scenario.
- **T3.7 (DA5).** Confirm no change to `GooglePromptCacheSession`/`CacheSignature`
  (the signature hashes raw `message.content`, not the sidecar-mapped `Content`).
  No scenario (covered observationally by S-GOOG-02 byte-stability).
- **T3.8 (§3.3).** Author NEW `GoogleInferenceServiceImplSidecarWireTest` (closing
  the F12 gap): suffix in the tail text part, frozen replay byte-identity with a
  stable `<uuid>`, deduped turn omits live, image leading text-part untouched,
  live→frozen `markerId` continuity, and the mutation-defense. → S-GOOG-01,
  S-GOOG-02, S-GOOG-03, S-GOOG-04, S-GOOG-05, S-GOOG-06
- **T3.9 (checkpoint).** `./gradlew :feature:inference:testDebugUnitTest` and
  `./gradlew :feature:inference:assembleDebug` green; old helper gone; both
  providers route through markers. Build gate — no scenario.

### Slice 4 — Validation (Open Q1/Q2)

- **T4.1 (§9 / AGENTS.md §9).** Run CI-aligned checks across the changed surface:
  `./gradlew :feature:inference:testDebugUnitTest`,
  `./gradlew :feature:inference:assembleDebug`, `./gradlew ktlintCheck`,
  `./gradlew detekt` (`maxIssues: 0`). `:core:domain` is unchanged by this
  ticket, so `./gradlew :core:domain:testDebugUnitTest` is an optional regression
  guard only. → S-VAL-01
- **T4.2 (Open Q1, manual).** Release-build emulator multi-turn run (≥3 turns)
  against the live Anthropic path on a prompt-cache-eligible model; confirm the
  cached-prefix hit-rate holds after the one-time prefix→suffix migration turn and
  frozen-replay byte stability keeps the cached prefix intact turn-over-turn (DA4
  stands; no code change). → S-VAL-02
- **T4.3 (Open Q2, manual).** Release-build emulator multi-turn run against the
  live Google path; confirm server-cache create/reuse behaves under the suffix
  byte change — a session whose cache was created with old prefix bytes does not
  serve mismatched cached content after migration, and fresh sessions rebuild
  cleanly (DA5 stands; no code change). → S-VAL-03

---

## 7. Execution strategy — SEQUENTIAL

Per the human decision (2026-06-19), the two provider slices are executed
**sequentially**, not in parallel:

1. **Slice 1** lands first (shared `sidecarMarkersForSessionIndex` sibling; old
   helper retained). Both providers depend on it.
2. **Slice 2 (Anthropic)** lands next, fully migrated end-to-end (resolve seam →
   embed seam → freeze) with its rewritten wire-byte test. Google still uses the
   old prepend helper and stays green.
3. **Slice 3 (Google)** lands last, fully migrated end-to-end, **and** deletes the
   now-unused `sidecarTextsBeforeSessionIndex` to complete the rename. The
   shared-file deletion is reserved to this last-merged provider slice to avoid a
   merge conflict.
4. **Slice 4** validates the assembled behavior (CI-aligned checks + release-build
   emulator cache validation).

Do **not** spawn parallel provider subagents. Within a slice, author the test
(TDD Red) before/with the production change, then make it green (Implementation),
per the Synthitect phase order.

---

## 8. Drift & escalation rules

- **Contract-violation escalation (AGENTS.md §3.1).** If any DA1–DA8 decision
  cannot hold without violating a contract (e.g. a clean-architecture boundary, a
  schema change, or a required `cache_control`/`CacheSignature` edit surfaces as
  unavoidable), reply with exactly
  `Contract violation detected in [file]. Request explicit 'Contract Waiver' from user.`
  and stop. Do not silently reopen an approved decision.
- **Scope-creep guard.** Do **not** touch the LiteRT path
  (`LlamaInferenceServiceImpl`, INV-9), the already-migrated OpenAI path
  (`OpenAiCompatibleVolatilePlacement` / `BaseOpenAiSdkInferenceService`, F13),
  Anthropic `cache_control` support (`AnthropicPromptCacheSupport`, DA4), Google
  `CachedContent`/`CacheSignature` (`GooglePromptCacheSession`, DA5), the
  append-only digest/log (`ProviderMessageDigest`/`AppendOnlyMessageLog`, INV-6),
  or Room/persistence (INV-5 / §3.4). The shared embedder/marker/snapshot
  primitives are reused unchanged (F5).
- **Test-weakening guard (§3.3).** When rewriting
  `AnthropicInferenceServiceImplCachePrefixTest`, rewrite assertions to the
  **suffix** byte shape but preserve the original intent: INV-1 (frozen replay
  byte-stable at the original anchor), INV-2 (sidecar after content/metadata), and
  INV-3 (dedupe semantics). Do not delete the cache-breakpoint assertions (DA4) or
  loosen them to `assertNotNull`/`isPresent`-only where a position/value was
  previously asserted. New tests must carry literal expected values (see
  `test_spec.md`), not values derived from production code.
- **Static analysis.** `./gradlew detekt` must pass with `maxIssues: 0` before
  completion (AGENTS.md §9). `./gradlew ktlintCheck` must pass. Fix any new
  lint/detekt findings introduced by these edits; do not suppress without cause.
- **AndroJack (AGENTS.md §3.2).** Pass every generated Kotlin block through
  `mcp_androjack_android_code_validator` (minSdk 34, targetSdk 36) during
  implementation; fix and re-run on a FAIL verdict.

---

## 9. Self-review

- **DA coverage.** DA1 → T1.1/T2.2/T3.2/T3.3/T3.4/T3.6; DA2 → T2.1/T2.3/T3.1/T3.5;
  DA3 → T2.2/T3.4 (metadata-agnostic suffix onto `msg.content`); DA4 → T2.4 (no
  change, out of scope to edit); DA5 → T3.7 (no change, out of scope to edit); DA6
  → T2.2/T3.4 (single text block/part, image branches untouched); DA7 →
  pre-retrieval `null` passed by T2.2/T3.2 with parity params on the sibling; DA8 →
  one ticket, no flag, direct replacement with old-helper deletion T3.6.
- **Slice order.** Matches `structure.md`: Slice 1 (sibling) → Slice 2 (Anthropic)
  → Slice 3 (Google, deletes old helper) → Slice 4 (validation). Sequential
  strategy reflected in §7.
- **Requirement ↔ task ↔ scenario.** Every behavior-changing task references ≥1
  scenario ID; every scenario family in `test_spec.md` maps back to a task.
- **Signatures.** `sidecarMarkersForSessionIndex`, the Anthropic resolve/freeze
  param changes, the Google resolve/`sidecarMarkersForIndex`/mapper changes, the
  removed `sidecarTextsBeforeSessionIndex`, and the reused-unchanged shared
  signatures all match `structure.md` and the verified repo source.
- **No placeholders.** No `TBD`/`TODO`/`similar to`/`as above`/`implement later`.
- **No runnable code.** Only declarations/signatures and prose.
