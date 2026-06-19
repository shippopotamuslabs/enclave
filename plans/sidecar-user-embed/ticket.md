# Ticket: sidecar-user-embed

## Title
Sidecar Embedding for Prompt Cache Stability (DeepSeek / OpenAI-Compatible Providers)

## Raw Idea
Embed context sidecars as `[Sidecar:]` suffix annotations on `role: user` message
content (instead of separate `role: system` messages) on OpenAI-compatible BYOK
wire paths, so DeepSeek's chat template does not hoist sidecars into the leading
system token block and invalidate the cached prefix. Goal: restore 85%+ prompt
cache hit rates across consecutive chat turns, including the turn where a new
live sidecar is added. Anthropic/Google prepend paths should align to the same
`[Sidecar:]` delimiter + ordering (metadata before sidecar) for consistency.
LiteRT on-device sidecar merge behavior is out of scope.

## Source Planning Prompt
See `planning-prompt.md` in this directory (full self-contained brief with
invariants INV-1..INV-9, target wire shape, file list, success criteria).

## Branch Baseline
`feat/deepseek` @ `bd7e01f8` (revert point).

## Key Invariants
INV-1 frozen replay at original anchor, byte-stable
INV-2 live sidecar on tail user msg after `[Metadata:]`
INV-3 dedupe gate semantics unchanged
INV-4 single `role: system` at JSON index 0 only
INV-5 sidecars wire-only — not persisted, not shown in UI
INV-6 Pi append-only invariant preserved
INV-7 stable, once-only system-prompt rule teaching model `[Sidecar:]`/`[Metadata:]` are host metadata
INV-8 pre-retrieval sidecars follow same embedding rules
INV-9 provider scope: must change OpenAI-compat; align Anthropic/Google; must NOT change LiteRT

## Success Criteria
1. Turns 1–4 stable frozen sidecar: >=80% hitPct
2. Turn with new live sidecar (ledger changed): >=80% hitPct (not 0–10%)
3. PROMPT_CACHE_JSON_BREAK diverges only at tail user suffix
4. UI shows no `[Sidecar:]` / `[Metadata:]`
5. Model does not verbally acknowledge sidecar/metadata blocks
6. `:feature:inference` unit tests green