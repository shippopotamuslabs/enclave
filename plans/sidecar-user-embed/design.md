# Design: sidecar-user-embed

Grounded in `plans/sidecar-user-embed/research.md` (findings F1–F14) and the
invariants INV-1..INV-9 in `planning-prompt.md`. No implementation code below.

## Current state (from research.md)

Today, OpenAI-compatible providers (DeepSeek direct, xAI, OpenRouter OpenAI-
compat, generic OpenAI) emit every sidecar — frozen replay, live tail,
pre-retrieval — as a separate `role: system` message inserted into `messages[]`
by `OpenAiCompatibleVolatilePlacement.buildMappedBodyWithSidecars`
(`OpenAiCompatibleVolatilePlacement.kt:24-66`). The single configured system
prompt is the only stable `role: system` message, prepended at index 0
(`BaseOpenAiSdkInferenceService.kt:536-549`). DeepSeek's chat template hoists
all `role: system` content into the leading token block, so any newly inserted
sidecar rewrites the cached prefix and collapses the cache hit rate from
~80–95% to 0–10%.

`FrozenSidecarSnapshot(text, mappedInsertIndex)` stores the **raw sidecar
body** (no wrapper) and is frozen by `endTurnFreezeSidecar` after a live
sidecar ships (`ProviderAppendOnlyContext.kt:128-140`). The dedupe gate
`resolveContextSidecarsForWire` compares trimmed raw bodies and suppresses
live shipping when unchanged (`ProviderAppendOnlyContext.kt:95-100`).

Anthropic and Google already embed sidecars **inside** message content via
`SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex`, but they
**prepend** raw sidecar text (no `[Sidecar:]` delimiter) before the user
content, joined by `\n\n` (`AnthropicInferenceServiceImpl.kt:231-246`,
`GoogleInferenceServiceImpl.kt:156-179`). They avoid the system-hoist problem
but do not match the metadata-before-sidecar ordering or the suffix placement
that INV-2 prescribes.

`[Metadata: Sent at ...]` is wire-only: added to the live prompt by
`ChatInferenceRequestPreparer.promptWithTemporalMetadata`
(`ChatInferenceRequestPreparer.kt:485-488`) and re-added for historical turns
by `ChatHistoryRehydrator` (`ChatHistoryRehydrator.kt:93-95`). The message DB
stores clean user-visible text; metadata lives in the `committedInferencePrompts`
port and on the wire only, so the chat UI never sees it (research F9).

`SearchToolPromptComposer` already emits a stable metadata rule in
`metadataRules(compact)` describing `[Metadata: Sent at ...]` and instructing
the model not to mention it (`SearchToolPromptComposer.kt:154-164`) — the
natural injection point for the INV-7 `[Sidecar:]` annotation rule.

The LiteRT path (`LlamaInferenceServiceImpl.injectSidecarIntoLastUserMessage`,
`LlamaInferenceServiceImpl.kt:657-672`) already embeds sidecars as a
`[context]...[/context]` **prefix** on the last user message, with no freeze/
replay — confirming user-content embedding is template-stable. It is out of
scope (INV-9).

The Responses API path (`OpenAiCompatibleResponseSidecarPlacement`, research
F3) emits sidecars as `ResponseWireSegment.SystemText` segments and is
OpenAI-provider-only; user content flows through `ResponseWireSegment.Input`
wrapping a `ResponseInputItem.Message` with `addInputTextContent(content)`
(`ResponsesInputMessageSupport.kt:34-44`).

## Desired end state

1. On OpenAI-compatible chat-completions wire, sidecars appear **only** as a
   suffix on `role: user` message content, in the form
   `\n\n[Sidecar: {body}]`, appended **after** any existing `[Metadata:]`
   suffix. No `role: system` message is emitted for sidecar content. The
   single configured system prompt remains the only `role: system` message.
2. Frozen sidecars replay byte-identically at their original user-message
   anchor every turn; live sidecars ship only on the tail user message and
   only when the dedupe gate passes; deduped turns ship no live suffix.
3. The model is told once, in the stable system prompt, that `[Metadata:]`
   and `[Sidecar:]` blocks are host-injected context it must use silently and
   never quote or acknowledge.
4. Anthropic and Google prepend paths align to the same `[Sidecar:]`
   delimiter and suffix-after-content placement, with metadata-before-sidecar
   ordering preserved (INV-9 "should align").
5. Diagnostics classify sidecars by `[Sidecar:` spans inside user content,
   not by `role: system` slots.
6. UI shows no `[Sidecar:]` or `[Metadata:]` (already true for metadata;
   sidecars follow the same wire-only model). LiteRT, Room schema, and the
   append-only digest path are unchanged.
7. After release-build emulator validation against DeepSeek, hitPct is
   ≥80% on turns 1–4 and on the turn where a new live sidecar is added.

## Patterns to follow

- **Wire-only annotation, DB-stores-clean-text** — mirror the existing
  `[Metadata:]` model (F9). Sidecar bytes are composed at wire-assembly time
  from `FrozenSidecarSnapshot` + the live resolved sidecar, never persisted to
  `Message.content` or `committedInferencePrompts`.
- **Single stable system prompt** — keep the index-0 `role: system` message
  as the only system message (F2, F10). Add the INV-7 rule through
  `SearchToolPromptComposer.metadataRules` / the full `# TEMPORAL CONTEXT:`
  block so it composes once per turn from stable inputs and stays byte-stable.
- **Append-only state machine unchanged** — keep `ProviderAppendOnlyContext`,
  `FrozenSidecarSnapshot`, `resolveContextSidecarsForWire`, and
  `endTurnFreezeSidecar` semantics intact (INV-3, INV-6). Only the *wire
  rendering* of a snapshot changes, not its storage or freeze conditions.
- **Shared helper for embedding** — extract one unit (working name
  `SidecarUserContentEmbedder`) that takes a base user content string plus an
  ordered list of `(markerId, body)` sidecar entries (frozen-at-this-anchor
  then live) and produces the suffixed content with UUID-stamped
  `[Sidecar-<markerId>: <body>]` blocks (D7). Used by the OpenAI
  chat-completions path and the OpenAI Responses path (D3), so the wrapper
  format is defined in exactly one place. The follow-up Anthropic/Google
  slice will adopt the same helper.
- **Metadata-first ordering** — preserve the existing `[Metadata:]` suffix
  that may already be present in user content, and append `[Sidecar:]` after
  it. The embedder must not reorder or re-emit metadata.

## Patterns to avoid

- **Do not** emit sidecars as `role: system` messages on any OpenAI-compatible
  path (the exact bug being fixed; F1, F3).
- **Do not** prepend sidecars before user content on the OpenAI path
  (`AnthropicInferenceServiceImpl.kt:231-246` does this today; aligning it is
  an explicit behavior change, not a pattern to copy for OpenAI).
- **Do not** collapse, re-compact, or reposition frozen snapshots on replay
  (INV-1). The `[Sidecar:]` wrapper applied at embed time must be byte-identical
  across turns for the same frozen body.
- **Do not** add `cache_control` markers for DeepSeek (non-goal §7); keep
  `supportsExplicitCacheControl` semantics as-is.
- **Do not** touch the LiteRT prefix-embed path or its `[context]...[/context]`
  wrapper (INV-9, F13).
- **Do not** change `ProviderMessageDigest` / `AppendOnlyMessageLog` (INV-6,
  F14). The digest is computed over normalized session messages before
  sidecar embedding; embedding must happen downstream of digest computation.
- **Do not** persist `[Sidecar:]` bytes to `Message.content` or
  `committedInferencePrompts` (INV-5, F9). The rehydrator's
  `substringBefore("\n\n[Metadata:")` matching must not accidentally treat a
  sidecar suffix as part of the clean prompt.

## Design decisions

### D1. Snapshot stores raw body; wrapper applied at embed time (INV-1 resolution)

`FrozenSidecarSnapshot.text` continues to store the **raw sidecar body** (as
today, F5). The wrapper is applied uniformly by
`SidecarUserContentEmbedder` at wire-assembly time. The wrapper form is the
UUID-stamped block from D7 (`[Sidecar-<markerId>: <body>]`); because it is a
pure function of `(markerId, body)` and `markerId` is frozen for the life of
the snapshot, the same frozen body renders to byte-identical suffix bytes on
every replay, satisfying INV-1 without freezing the wrapper format into the
snapshot text.

Rejected alternative: store the full wrapped suffix bytes in the snapshot.
This would lock the wrapper format into persisted session state and require
a migration whenever the delimiter evolves; it also couples the snapshot to a
wire-format concern that the snapshot otherwise does not own.

### D2. Embedding happens after digest sync, before `messages` list build

In `BaseOpenAiSdkInferenceService.completeSingleTurn`, the ordering is
preserved up to `appendOnly.syncMessages(...)` (`:530-533`) and
`wireSidecar = appendOnly.resolveContextSidecarsForWire(contextSidecars)`
(`:534`). The change replaces the
`OpenAiCompatibleVolatilePlacement.buildMappedBodyWithSidecars(...)` call
(`:594-603`) with a new embed step that mutates the **user** message params in
`mappedBody` rather than inserting system messages. `syncMessages` runs on
the un-embedded normalized messages, so the append-only digest is unaffected
(INV-6, F14). Freeze (`finalizeSidecarFreeze`) is unchanged and still keyed
on `wireSidecar != null` and `tailInsertIndex` (INV-3).

### D3. Shared `SidecarUserContentEmbedder` (OpenAI chat-completions + Responses) — Responses IN SCOPE THIS SLICE

A single internal helper owns the wire shape:

- Input: `baseContent: String`, `sidecarBodies: List<SidecarMarker>` (ordered:
  frozen-at-this-anchor in freeze order, then live if this is the tail and a
  live sidecar resolved non-null). See D7 for the `SidecarMarker` shape.
- Output: `baseContent` +, for each marker, `"\n\n" + marker.wireBlock()`,
  appended in order. Existing `[Metadata:]` suffixes already in `baseContent`
  are left in place and precede the sidecar blocks (INV-2).

For the **OpenAI chat-completions** path, the embedder runs per mapped user
message index, consuming the frozen snapshots grouped by
`mappedInsertIndex` plus the live sidecar at `tailInsertIndex`. For the
**Responses** path (in scope this slice per user followup #2), the embedder
runs on the user `ResponseWireSegment.Input` content before it is wrapped
into a `ResponseInputItem.Message` via
`ResponsesInputMessageSupport.textInputItem` (F3). The Responses path keeps
its existing index remapping (`remapFrozenSidecarsForResponses`) and drops all
`ResponseWireSegment.SystemText` sidecar emissions.

For image-bearing user messages (F15), the sidecar suffix is appended to the
**text part** of the content-parts array, not added as a new part, so the
suffix stays inside the user content token stream and the part structure is
unchanged.

### D4. Anthropic / Google alignment is a separate, follow-up slice (INV-9) — CONFIRMED

Anthropic and Google already embed inside user content and do not suffer the
DeepSeek hoist (F8). Migrating them from prepend-without-delimiter to
suffix-with-`[Sidecar:]`-delimiter is an INV-9 "should align" change, not a
cache-correctness fix. It also interacts with Anthropic's
`cache_control` breakpoints at `stableHistoryTailIndex`
(`AnthropicInferenceServiceImpl.kt:207`), since moving sidecar bytes from the
front of a user message to its tail shifts where the cacheable prefix ends.

Decision (confirmed by user): ship the OpenAI-compatible fix first as a
self-contained slice (D2/D3), validate the DeepSeek hit-rate success criteria,
then align Anthropic/Google in a follow-up slice that re-anchors breakpoints.
The embedder (D3) is written so the follow-up can adopt it without changing
the OpenAI path. The follow-up slice will be a separate ticket once the
OpenAI fix is validated.

Rejected alternative: align all three providers in one slice. Rejected
because it couples a verified-wire-format-bug fix (DeepSeek) to a
behavior-change-with-cache-implications (Anthropic/Google) and enlarges the
validation surface.

### D5. Diagnostics classification moves to user-content spans

`PromptCacheSidecarDiagnostics.captureFrom` (F11) currently classifies
`role: system` wire placements. After D2/D3, sidecars are no longer system
messages, so `wirePlacements` will only contain `stable_system`. The
diagnostics gain a new classification pass that scans `role: user` message
content for `[Sidecar: ` spans and reports each by wire index, body hash, and
length — mirroring the existing `SidecarSlotCapture` shape but sourced from
user content. `extractSystemJsonSpans` and `formatBlock0` remain unchanged and
become the INV-4 invariant check (exactly one system span, the stable
prompt). The `PROMPT_CACHE_SIDECAR_JSON_DELTA` / `sysJsonDelta` fields become
less informative and may report "none" once sidecars leave the system block;
that is the success signal for criterion 3.

### D6. INV-7 rule lives in `SearchToolPromptComposer` — compact variant approved

The annotation rule from INV-7 is added to `SearchToolPromptComposer`'s
non-compact `# TEMPORAL CONTEXT:` block (and a one-line compact variant
approved by user followup #4), alongside the existing metadata rule (F10).
It contains no timestamps or UUIDs, so the composed `systemPrompt` stays
byte-stable across turns as long as the tool set and strategy are stable —
which they are within a chat session absent a tools/disclosure change (which
already triggers `StablePrefixSnapshot` version bump via `ensureStablePrefix`,
F5). The rule is composed once per turn and becomes part of the single
index-0 system message; there is no double-injection risk on rehydrate
because the system prompt is recomposed each turn, not stored in the message
DB.

The rule text must teach the model the **UUID-stamped** marker form from D7
(e.g. `[Sidecar-<uuid>: ...]`) so the model recognizes the host-injected
block regardless of which UUID is used on a given turn, and must stay
byte-stable itself (no UUIDs inside the rule text).

### D7. UUID-stamped sidecar markers for safe, deterministic stripping (INV-1 + INV-5)

The bare `[Sidecar:` delimiter risks mis-stripping user-typed content that
happens to contain that literal (user followup #5). Metadata suffers the same
issue but is explicitly out of scope. For sidecars, we adopt a
**deterministic, per-snapshot UUID stamp** so stripping is unambiguous.

Mechanics:

- Each `FrozenSidecarSnapshot` gains a stable `markerId: String` (a UUID
  generated once at freeze time and stored alongside `text`). The
  `markerId` is **permanent for the life of the snapshot** — it is replayed
  byte-identically on every subsequent turn, preserving INV-1.
- The live (pre-freeze) sidecar also gets a `markerId` generated at the
  moment `resolveContextSidecarsForWire` returns non-null; that same id is
  carried into the `FrozenSidecarSnapshot` created by `endTurnFreezeSidecar`
  so the marker does not change across the live→frozen transition. This is
  critical: the frozen replay must use the **same marker bytes** as the live
  turn that first shipped it, or the prefix would shift on the next turn.
- The wire block is `[Sidecar-<markerId>: <body>]`. The embedder (D3) emits
  this form uniformly.
- Stripping / span detection (UI, diagnostics, and any rehydrate matching)
  keys on the regex `\[Sidecar-[0-9a-fA-F-]{36}:` rather than the bare
  `[Sidecar:` prefix, so a user typing `[Sidecar:` or even
  `[Sidecar-<something>:` (where `<something>` is not a valid UUID) cannot
  trigger a false strip. The UUID form is constrained enough to make
  accidental user collisions effectively impossible.

Why deterministic (UUID chosen at freeze, then fixed) rather than re-randomized
each turn: INV-1 requires byte-stable frozen replay. Re-randomizing the
marker each turn would rewrite historical user-message suffix bytes and
re-invalidate the cached prefix — the exact bug being fixed.

Rejected alternatives:
- **Re-randomize marker per turn.** Rejected — violates INV-1.
- **Bare `[Sidecar:` with no stamp.** Rejected — collision risk with
  user-typed content (the issue raised in followup #5).
- **Content-based delimiter (e.g. wrap sidecar body in `<sidecar>...</sidecar>`
  XML tags).** Rejected — heavier on the wire, harder for the model to learn
  via a stable system-prompt rule, and the UUID stamp already gives us
  unambiguous stripping without changing the body format. Listed here so the
  rejected option is named.

`FrozenSidecarSnapshot` shape change: from `(text, mappedInsertIndex)` to
`(text, markerId, mappedInsertIndex)`. This is an in-memory, session-scoped
struct (not persisted to Room — F5/F9), so the shape change has no schema
impact. `ProviderAppendOnlyContext.endTurnFreezeSidecar` and
`appendVolatileSidecar` accept and store the markerId;
`resolveContextSidecarsForWire` returns a `(text, markerId)` pair (or a small
`ResolvedSidecar` value) instead of a bare `String?` so the call site can
thread the id into the embedder and the freeze call. `lastShippedSidecarText`
comparison stays text-only (INV-3 dedupe semantics unchanged); the markerId is
orthogonal to dedupe.

### D8. No feature flag, no rollback path — direct replacement

Per user followup #3, the app is unreleased with a sole developer/user, so
there is no need for a feature flag or a parallel old/new path. The old
`role: system` sidecar emission in `OpenAiCompatibleVolatilePlacement` and
`OpenAiCompatibleResponseSidecarPlacement` is **removed** and replaced by the
embedder (D3). If the fix regresses, the revert point is commit `bd7e01f8`.

Consequence for the plan: no flag plumbing, no dual-path tests, no
configuration port. The unit test suite is rewritten in place to assert the
new wire shape (see research F12 for the affected tests).

## Open questions — RESOLVED (user followups 1–5)

All five design open questions have been answered by the user and folded into
the decisions above. Recorded here for traceability:

1. **Anthropic/Google alignment scope** — OpenAI-compat fix first; Anthropic/
   Google in a follow-up slice. → D4 (confirmed).
2. **Responses API inclusion** — in scope this slice, same embed pattern. →
   D3 (Responses in scope).
3. **Rollback mechanism** — no flag, no parallel path; direct replacement,
   revert to `bd7e01f8` if needed. → D8.
4. **Compact-context INV-7 rule variant** — one-line compact form is
   acceptable. → D6 (confirmed).
5. **`[Sidecar:` delimiter collision** — adopt UUID-stamped markers
   (`[Sidecar-<uuid>: ...]`), deterministically fixed at freeze time and
   carried live→frozen, with stripping keyed on the UUID form. Metadata's
   equivalent collision risk is acknowledged but out of scope. → D7.

No open questions remain. Ready for Structure.

## Self-Review

- Current state matches `research.md` (F1–F14) without contradictions; each
  claim is cited to a research finding or re-verified file.
- All five open questions are specific and answerable by a human (scope,
  inclusion, rollback, compact-rule, delimiter-collision).
- Rejected alternatives are named: D1 (store wrapped bytes), D3-single-slice
  variant is covered by D4's rejected alternative, D6-double-injection
  avoidance is addressed.
- No runnable implementation code appears; signatures are high-level
  (`SidecarUserContentEmbedder(baseContent, sidecarBodies) -> String`).
- No `TBD`/`TODO`/`similar to` placeholders.
- The design stops before structure/tactical planning, per the briefing.