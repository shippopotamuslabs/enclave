# Ticket: sidecar-anthropic-google-embed

## Title
Align Anthropic / Google Sidecar Wire Placement to the `[Sidecar-<uuid>:]`
User-Content Suffix Pattern (follow-up to `sidecar-user-embed`)

## Raw Idea
The `sidecar-user-embed` ticket (see `plans/sidecar-user-embed/*.md`) migrated
the OpenAI-compatible wire paths (DeepSeek / xAI / OpenRouter-OpenAI-compat /
OpenAI chat-completions + Responses) to embed context sidecars as UUID-stamped
`[Sidecar-<uuid>: {body}]` **suffix** annotations on `role: user` message
content, appended **after** any existing `[Metadata:]` suffix, via a shared
`SidecarUserContentEmbedder` / `SidecarMarker` / `ResolvedSidecar` primitive
with live→frozen markerId continuity (decision D7).

That ticket **explicitly deferred** aligning the Anthropic and Google paths
(design decision D4 in `plans/sidecar-user-embed/design.md`). This ticket is
that follow-up slice. Bring Anthropic and Google into alignment with the same
pattern:

- Embed sidecars as the same `[Sidecar-<uuid>: {body}]` blocks the embedder
  produces, **after** the user-visible content (and after `[Metadata:]`),
  instead of the current raw-text **prepend** (before content, joined by
  `\n\n`, with no delimiter).
- Use the shared `SidecarUserContentEmbedder` so the wire-block format lives in
  exactly one place (D3 intent: "The follow-up Anthropic/Google slice will
  adopt the same helper").
- Thread the `ResolvedSidecar.markerId` through the live→frozen transition so
  frozen replays are byte-identical (D7), replacing the current throwaway
  markers minted at freeze time (`AnthropicInferenceServiceImpl.kt:563`,
  `GoogleInferenceServiceImpl.kt:327`).
- Preserve Anthropic's `cache_control` breakpoint correctness when sidecar
  bytes move from the front of a user message to its tail (design D4 flagged
  this interaction with `stableHistoryTailIndex`).

LiteRT on-device sidecar merge behavior remains out of scope (INV-9).

## Source Of Truth (read for background)
- `plans/sidecar-user-embed/design.md` — decisions D1–D8 (esp. D4 deferral,
  D3 shared embedder, D7 UUID marker + live→frozen continuity, INV-2 ordering).
- `plans/sidecar-user-embed/research.md` — F8 (Anthropic/Google prepend paths),
  F15 (image-bearing user messages).
- `plans/sidecar-user-embed/spec.md` / `test_spec.md` — established testing
  shape (S-EMBED / S-FREEZE / S-WIRE) and run commands.

## Branch Baseline
`feat/deepseek` @ `d290c27c` ("feat(cache): embed sidecars in user messages" —
the OpenAI-compat slice). Revert point for this follow-up.

## Invariants Carried Forward (from `sidecar-user-embed/planning-prompt.md`)
The same invariants apply to Anthropic/Google where relevant:
- INV-1 frozen replay byte-stable at original anchor.
- INV-2 sidecar suffix after `[Metadata:]` / after user content (ordering change
  vs current prepend).
- INV-3 dedupe gate semantics unchanged.
- INV-5 sidecars wire-only — not persisted, not shown in UI.
- INV-6 Pi append-only invariant preserved.
- INV-7 model rule already present in the system prompt (shared composer).
- INV-8 pre-retrieval sidecars follow the same embedding rules.
- INV-9 provider scope: align Anthropic/Google; must NOT change LiteRT.

## Open Scope Questions (for Design phase)
- Whether the suffix move breaks Anthropic `cache_control` breakpoint placement,
  and how to re-anchor breakpoints so the cacheable prefix still ends before the
  volatile sidecar suffix.
- Whether `SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex` (raw
  texts) is replaced by an embedder-based path that returns `SidecarMarker`s.
- Pre-retrieval sidecar handling on Anthropic/Google (currently passed as
  `null` on both paths — confirm intended behavior).

## Success Criteria
1. Anthropic and Google emit sidecars as `[Sidecar-<uuid>: {body}]` suffixes on
   user-message content (after metadata), via the shared embedder — not raw
   prepend.
2. Frozen sidecars replay byte-identically at their original anchor across turns
   (markerId preserved live→frozen).
3. Anthropic prompt-cache breakpoints remain correct (cacheable prefix unbroken
   by the new suffix).
4. UI shows no `[Sidecar-<uuid>:]`.
5. `:feature:inference` unit tests green (existing Anthropic/Google sidecar tests
   rewritten to the new wire shape; new INV-1/INV-2/D7 tests added).
6. `./gradlew ktlintCheck` and `./gradlew detekt` green.
