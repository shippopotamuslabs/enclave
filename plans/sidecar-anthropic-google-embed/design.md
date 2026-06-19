# Design: sidecar-anthropic-google-embed

Grounded in `plans/sidecar-anthropic-google-embed/research.md` (findings F1–F15)
and the invariants INV-1..INV-9 in `plans/sidecar-user-embed/planning-prompt.md`.
This is the realization of `sidecar-user-embed` design decision **D4** (Anthropic/
Google alignment deferred to a follow-up slice) and must honor D1 (snapshot
stores raw body; wrapper at embed time), D3 (shared `SidecarUserContentEmbedder`),
D7 (UUID marker + live→frozen continuity), and INV-2 (metadata before sidecar).
No runnable implementation code below — decisions and high-level seams only.

## Current state

Both providers embed sidecars as a **raw-text PREPEND before user content**,
`\n\n`-joined, with **no `[Sidecar:]` delimiter**, and both **discard the
`markerId`** and **mint a throwaway marker at freeze** — breaking D7.

- **Anthropic** (`AnthropicInferenceServiceImpl.kt`): `wireSidecar =
  appendOnly.resolveContextSidecarsForWire(contextSidecars)?.text` drops the
  `ResolvedSidecar.markerId` (`:230`, F3). `prependIndexSidecars(index,
  baseContent)` (`:232-247`) calls `SessionIndexVolatilePlacement
  .sidecarTextsBeforeSessionIndex(...)` and returns
  `texts.joinToString("\n\n") + "\n\n" + baseContent` (`:245-246`, F1). The
  result `messageContent = prependIndexSidecars(index, msg.content)` (`:260`)
  feeds the single USER `TextBlockParam` (`:285`), the image-bearing USER text
  block (`:320`), compact-summary (`:338`), tool-result (`:353`), and
  assistant-text. Freeze `freezeContextSidecarIfPresent` (`:484-488` →
  `:555-567`) mints a fresh UUID at freeze: `markerId =
  SidecarMarkerFactory.newMarker(contextSidecars).markerId` (`:563`, F4).
- **Google** (`GoogleInferenceServiceImpl.kt`): `wireSidecar = ...?.text`
  (`:155`, F3). `sidecarPrependTextForIndex(index)` (`:157-167`) `\n\n`-joins the
  raw texts and is passed as `prependedText` into `GoogleAgentLoopContentMapper
  .mapMessage(...)` (`:169-180`), which builds `contentText =
  "$prependedText\n\n${message.content}"` (`GoogleAgentLoopContentMapper.kt
  :59-64`, F2). Freeze (`:324-330`) mints a fresh UUID: `markerId =
  SidecarMarkerFactory.newMarker(wireSidecar).markerId` (`:327`, F4).

Because the prepend renders raw `.text` only, `markerId` currently has **zero
wire effect** (F3); the `[Sidecar-<uuid>:]` wrapper is never applied.
`SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex` returns
`List<String>` and is called **only** by these two providers (F6, F12). The
shared markerId machinery, dedupe gate, and embedder are already migrated and
reachable in-module (F5): `resolveContextSidecarsForWire` returns
`ResolvedSidecar(text, markerId)` (`ProviderAppendOnlyContext.kt:96-106`),
`endTurnFreezeSidecar(text, markerId, mappedInsertIndex)` reuses
`pendingLiveMarkerId` only when passed a blank markerId (`:136-155`),
`FrozenSidecarSnapshot` already carries `markerId` (F5), and
`SidecarUserContentEmbedder.embed(baseContent, markers)` appends `"\n\n" +
marker.wireBlock()` per marker (`SidecarUserContentEmbedder.kt:19-27`).

`[Metadata: Sent at …]` is already the tail of `msg.content` reaching the mappers
(live content built by `InferencePromptContentBuilder` as
`{volatileTurnContext}\n\n{prompt}` with the metadata suffix preserved at the
tail; F10 — marked inference for the Rust round-trip). Pre-retrieval context is
**merged upstream** into `msg.content` and never delivered as a sidecar on these
providers (both pass `preRetrievalSidecarText = null`; F11). The INV-7 model
rule is already in the shared `SearchToolPromptComposer` system prompt and
reaches both providers (F15). LiteRT is independent and out of scope (F14).

## Desired end state

Observable wire behavior for **both** providers after the change:

- **Frozen replay:** each historical user-message anchor renders its frozen
  sidecar(s) as `…content…\n\n[Sidecar-<uuid>: {body}]` byte-identically every
  turn, with the **same `<uuid>`** as the live turn that first shipped it
  (D7/INV-1). The wrapper comes from `SidecarMarker.wireBlock()`
  (`SidecarMarker.kt:24`) via the shared embedder.
- **Live tail:** when the dedupe gate passes, the live sidecar is appended as a
  suffix to the tail user message (`lastUserIndex`) **after** any `[Metadata:]`
  (INV-2), using the markerId minted by `resolveContextSidecarsForWire`.
- **Deduped turn:** no live `[Sidecar-…]` on the tail; all frozen anchors still
  replay unchanged (INV-3).
- **Image messages:** the suffix lands inside the single user text block
  (Anthropic) / leading text part (Google); image blocks/parts are untouched
  (DA6).
- **markerId continuity:** the throwaway-marker mint at freeze is removed; the
  resolved live markerId is threaded into `endTurnFreezeSidecar` so frozen bytes
  match the live ship (DA2).
- **Cache correctness:** Anthropic `cache_control` breakpoint placement is
  unchanged and remains correct (DA4); Google `CachedContent` keying is
  unchanged (DA5).
- **UI/persistence:** unchanged — sidecars stay wire-only; `stripSidecarBlocks`
  already keys on the UUID form (INV-5).

The prepend-encoding test (`AnthropicInferenceServiceImplCachePrefixTest`, F12)
is rewritten to the suffix shape in a later phase; this design does not author
tests.

## Patterns to follow

- **Reuse the shared embedder and marker types (D3, F5/F13).** Render every
  sidecar through `SidecarUserContentEmbedder.embed(baseContent,
  List<SidecarMarker>)` so the `[Sidecar-<uuid>: {body}]` format lives in one
  place. Build frozen markers as `SidecarMarker(snapshot.markerId,
  snapshot.text)` and the live marker as `SidecarMarker(wireSidecar.markerId,
  wireSidecar.text)` — exactly as `OpenAiCompatibleVolatilePlacement
  .embedSidecarsIntoUserMessages` does (`OpenAiCompatibleVolatilePlacement.kt
  :57-72`).
- **Thread `ResolvedSidecar` end-to-end (F13).** Keep the full
  `ResolvedSidecar` (drop `?.text`) and pass `wireSidecar.markerId` into the
  freeze, mirroring `finalizeSidecarFreeze`
  (`BaseOpenAiSdkInferenceService.kt:460-465`).
- **Keep freeze/dedupe semantics intact (INV-3/INV-6).** No change to
  `ProviderAppendOnlyContext` resolve/freeze/clear logic or the append-only
  digest. Only the **wire rendering** of a snapshot changes.
- **Metadata stays the content tail (F10).** Embed onto `msg.content` so the
  embedder appends **after** `[Metadata:]` (INV-2); the embedder is
  metadata-agnostic and needs no metadata detection.

## Patterns to avoid

- **Do NOT keep the raw-text prepend** (`prependIndexSidecars`,
  `sidecarPrependTextForIndex`, the mapper's `prependedText` prefix — F1/F2).
  The naive port would copy the prepend; this slice replaces it with a suffix
  embed. Flag any reviewer/implementer that re-introduces
  `texts.joinToString("\n\n") + "\n\n" + baseContent`.
- **Do NOT discard `markerId` via `?.text`** (F3) or **mint a throwaway marker
  at freeze** (`SidecarMarkerFactory.newMarker(...).markerId` passed straight
  into `endTurnFreezeSidecar`, F4/F15).
- **Do NOT touch LiteRT** (`LlamaInferenceServiceImpl`, F14/INV-9), change the
  Room schema, or alter the append-only digest / clear conditions (INV-6).
- **Do NOT collapse, reposition, or re-randomize frozen sidecars** on replay
  (INV-1); the wrapper must be byte-identical across turns for a given snapshot.

## Design decisions

### DA1 — Marker-returning sibling in `SessionIndexVolatilePlacement`, embedded as a suffix

Replace `SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex`
(`List<String>`, prepend) with a marker-returning sibling — working name
`sidecarMarkersForSessionIndex(...) : List<SidecarMarker>` — whose parameter set
mirrors `embedSidecarsIntoUserMessages` (frozen snapshots, optional
`preRetrievalSidecar: ResolvedSidecar?` + index, optional `liveSidecar:
ResolvedSidecar?`, `tailInsertIndex`). It keeps the existing index-anchoring
(frozen at `mappedInsertIndex == sessionIndex`, live at `tailInsertIndex`) but
emits `SidecarMarker(markerId, body)` instead of bare text. Each provider then
embeds at its existing content seam:

- **Anthropic:** `prependIndexSidecars(index, baseContent)` becomes an
  embed helper that calls the sibling and returns
  `SidecarUserContentEmbedder.embed(baseContent, markers)` (suffix). The five
  `messageContent` consumers (`AnthropicInferenceServiceImpl.kt:285/:320/:338/
  :353/…`) are unchanged; empty marker lists return content untouched, so
  non-user rows are unaffected.
- **Google:** the mapper's `prependedText: String?` parameter becomes
  `sidecarMarkers: List<SidecarMarker>`, and `contentText` is built as
  `SidecarUserContentEmbedder.embed(message.content, sidecarMarkers)`
  (`GoogleAgentLoopContentMapper.kt:59-64`) — a suffix instead of a prefix. The
  call site (`GoogleInferenceServiceImpl.kt:157-180`) passes markers from the
  sibling.

**Rationale:** keeps the frozen/live index-selection logic in one provider-
agnostic place (its existing reason to exist) and routes the wire format through
the shared embedder (D3), with the smallest seam change.

**Rejected — (b) inline per-provider:** duplicates the frozen/live anchoring
selection in both services, drifting from single-source-of-truth and re-creating
the duplication `SessionIndexVolatilePlacement` exists to prevent.

**Rejected — (c) a param-list helper analogous to
`embedSidecarsIntoUserMessages`:** the OpenAI helper rebuilds
`ChatCompletionMessageParam`; Anthropic builds `MessageParam`/`TextBlockParam`
inline with cache breakpoints, image blocks, and tool-result blocks (F7/F9), and
Google builds `Content`/`Part` via its mapper. There is no shared param type, so
a param-list helper would need two provider-specific rebuilders that reproduce
the inline complexity — more surface for no extra reuse.

### DA2 — markerId continuity: keep `ResolvedSidecar`, thread markerId into freeze

Both providers keep the full `ResolvedSidecar` (drop `?.text` at
`AnthropicInferenceServiceImpl.kt:230` / `GoogleInferenceServiceImpl.kt:155`) and
pass `wireSidecar.markerId` into `endTurnFreezeSidecar`, removing the throwaway
mint (`:563` / `:327`). For Anthropic, `freezeContextSidecarIfPresent` takes the
`ResolvedSidecar?` instead of a `String?`. This mirrors `finalizeSidecarFreeze`
(`BaseOpenAiSdkInferenceService.kt:460-465`) and satisfies D7.

Frozen snapshots already carry `markerId` (F5), so frozen replay renders
`SidecarMarker(snapshot.markerId, snapshot.text).wireBlock()` byte-identically
each turn; because the freeze now stores the **same** markerId the live turn
shipped, the live→frozen transition is byte-stable. (Threading the explicit
markerId is equivalent to passing blank and letting `endTurnFreezeSidecar` reuse
`pendingLiveMarkerId` at `ProviderAppendOnlyContext.kt:142-146`, but the explicit
form matches the OpenAI reference exactly and is clearer.)

**Rejected — keep minting at freeze but reuse `pendingLiveMarkerId` by passing a
blank markerId:** works mechanically but is implicit, diverges from the OpenAI
reference, and is fragile if `resolveContextSidecarsForWire` is ever bypassed.

### DA3 — INV-2 ordering: suffix after content / after `[Metadata:]`

The embedder appends after whatever `baseContent` ends with
(`SidecarUserContentEmbedder.kt:19-27`); since `[Metadata:]` is already the tail
of `msg.content` (F10), `embed(msg.content, markers)` places the sidecar after
metadata with **no metadata detection needed**. Messages without metadata get
the sidecar appended directly after the user text — still a valid INV-2 suffix.
No special-casing.

**Rejected — explicitly split content into `{text}{metadata}{sidecar}` and
re-insert metadata:** unnecessary; the metadata-agnostic append already yields
the correct order and avoids re-parsing `[Metadata:]`.

### DA4 — Anthropic `cache_control`: no change

No change to `stableHistoryTailIndex` (`= size-2`) or
`shouldAttachMessageCacheBreakpoint` (`AnthropicPromptCacheSupport.kt:16-26`).
The breakpoint is block-level and attached to a message's single text block;
the sidecar bytes live **inside that same block** whether prefixed or suffixed,
so moving them front→tail does not cross the cache boundary (F7, marked
inference). The live sidecar lands at `lastUserIndex` (`= size-1` in a normal
live turn), which is already in the uncached tail past the `size-2` breakpoint,
so there is nothing to protect there. Frozen sidecars in the cached region are
byte-stable across turns (DA2), so the cached prefix is stable after a one-time
migration-turn byte change. Live Anthropic cache behavior is flagged as Open
Question 1.

**Rejected — re-anchor `stableHistoryTailIndex` or split each user message into
a cached-prefix text block + a separate uncached sidecar-suffix block:** adds
wire complexity and changes the cached-prefix byte layout for no benefit, since
the live sidecar is already uncached and frozen sidecars are byte-stable.

### DA5 — Google `CachedContent`: no change

No change to `GooglePromptCacheSession` or `CacheSignature`. The signature hashes
raw `message.content` (`id, role, content, metadataJson`), **not** the
sidecar-mapped `Content` (F8), so the prefix→suffix move is signature-neutral —
cache create/reuse keying is unaffected. The cached `Content` bytes do include
the sidecar (the same `mapAtIndex` maps both the cached stable prefix and the
live delta, F8), and they stay byte-stable across turns via DA2. Live server-
cache reuse under the one-time byte change is flagged as Open Question 2.

**Rejected — fold sidecar-mapped content into `CacheSignature`:** would bust the
server cache whenever a live sidecar changes (defeating dedupe stability) and is
counter to the signature's intentional independence from volatile content.

### DA6 — Image-bearing user messages

Embedding into the single text string is sufficient for both providers because
each already collapses user-visible text into one text block/part with images as
separate blocks/parts (F9): Anthropic adds image blocks first then one
`TextBlockParam.ofText(messageContent)` last (`:297-326`); Google adds
`Part.fromText(contentText)` first then image parts (`GoogleAgentLoopContentMapper
.kt:84-89`). Since the suffix is embedded into `messageContent` / `contentText`,
it lands in that single text block/part and image blocks/parts are untouched —
mirroring the OpenAI "append to the first text part" behavior
(`OpenAiCompatibleVolatilePlacement.kt:116-134`). No separate part handling.

### DA7 — Pre-retrieval stays `null`, in scope only to preserve

Pre-retrieval is **not** delivered as a sidecar on these providers and remains
merged upstream into `msg.content` (F11). The new sibling (DA1) keeps the
`preRetrievalSidecar`/`preRetrievalInsertIndex` parameters for parity with the
OpenAI signature, but both providers continue to pass `null`. INV-8 is satisfied
because pre-retrieval never reaches Anthropic/Google as a sidecar. In scope only
to the extent of confirming the `null` is preserved; no behavior change.

**Rejected — route Anthropic/Google pre-retrieval through the sidecar embedder:**
would double-deliver context already merged upstream (the no-double-prepend
behavior asserted by the volatile-turn-context tests, F11) and is out of scope.

### DA8 — One ticket for both providers, no feature flag

Do **both** providers in this single ticket and replace the prepend directly,
mirroring the OpenAI slice (D8). They share `SessionIndexVolatilePlacement` and
the embedder, and the markerId-continuity fix touches both; a half-migrated
placement (one provider on markers, one on raw texts) is more error-prone than
doing both together. The app is unreleased and single-user; the revert point is
the branch baseline `feat/deepseek @ d290c27c`.

**Rejected — split into Anthropic-only then Google-only tickets:** leaves the
shared `SessionIndexVolatilePlacement` sibling and `endTurnFreezeSidecar` change
half-applied between tickets, increasing integration risk.

**Rejected — add a feature flag / parallel old+new path:** unnecessary plumbing
and dual-path tests for an unreleased single-user app; D8 set the no-flag
precedent.

## Open questions

1. **Live Anthropic `cache_control` validation.** DA4 asserts (per F7, inference)
   that intra-block prefix→suffix movement is cache-neutral. Confirm against live
   Anthropic via the release-build emulator protocol (multi-turn run): does
   hit-rate hold after the one-time migration turn, and does the frozen-replay
   byte stability keep the cached prefix intact turn-over-turn?
2. **Live Google `CachedContent` reuse validation.** DA5 asserts the
   `CacheSignature` is sidecar-independent (F8). Confirm server-cache create/reuse
   behaves under the suffix byte change — specifically that a long-lived session
   whose cache was created with old prefix bytes does not serve mismatched cached
   content after migration (fresh sessions rebuild and are unaffected).
3. **Keep `SessionIndexVolatilePlacement`?** DA1 keeps and renames it
   (`sidecarTextsBeforeSessionIndex` → `sidecarMarkersForSessionIndex`, dropping
   the now-misleading "Texts"/"Before" since the output is markers embedded as a
   suffix). Confirm: keep + rename the shared object, or remove it and inline the
   anchoring per provider? (Recommendation: keep + rename.)

## Open questions — RESOLVED (user, 2026-06-19)

1. **Live Anthropic `cache_control` validation** — Keep as a **validation slice**
   in this ticket (release-build emulator protocol), not a blocker for
   implementation. DA4 stands (no code change).
2. **Live Google `CachedContent` reuse validation** — Keep as a **validation
   slice** in this ticket, not a blocker. DA5 stands (no code change).
3. **Keep `SessionIndexVolatilePlacement`?** — **Keep + rename** to
   `sidecarMarkersForSessionIndex` (per DA1 recommendation). Do not remove/inline.

Design APPROVED 2026-06-19. Ready for Structure.
