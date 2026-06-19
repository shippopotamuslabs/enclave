# Planning Prompt: Sidecar Embedding for Prompt Cache Stability (DeepSeek / OpenAI-Compatible Providers)

## Task

Plan the implementation of a change to how **context sidecars** are delivered on the wire for OpenAI-compatible BYOK providers (DeepSeek direct API, xAI, OpenRouter-routed OpenAI-compat, etc.). The goal is to restore **85%+ prompt cache hit rates** across consecutive chat turns by ensuring that adding a new sidecar does not invalidate the cached prefix server-side.

Do **not** implement yet. Produce a detailed implementation plan: affected files, data flow changes, test strategy, migration/rollout, risks, and open questions.

## Repository Context

**Project:** Enclave — Android Kotlin monorepo with mixed Rust agent core and BYOK cloud inference.

**Relevant modules:**
- `:feature:inference` — all provider wire assembly, sidecar placement, prompt cache diagnostics
- `:core/domain` — `ChatInferenceRequestPreparer`, `ChatHistoryRehydrator` (metadata injection)
- `:core/agent` — Rust `agent_core` bridge; sidecar *content* is built in Kotlin (`RustLoopInferenceService.buildContextSidecars`)

**Branch baseline:** `feat/deepseek`, commit `bd7e01f8` (includes wire JSON break logging and sidecar diagnostics — use as revert point).

**Architecture contracts to respect:**
- `contracts/INFERENCE_RULES.md`, `contracts/ARCHITECTURE_RULES.md`, `contracts/CODE_STYLE_RULES.md`, `contracts/UNIT_TESTING.md`
- `:core:domain` must not depend on `:feature:inference`
- LiteRT on-device inference is out of scope (continues merging sidecars into system prompt string)

**Key existing specs/plans (read for background):**
- `plans/FEAT-append-only-provider-context/spec.md` — Pi append-only invariant
- `plans/sidecar-dedupe-model-wire/design.md` — dedupe gate design
- `plans/cache-troubleshooting-agent-prompt.md` — prior cache investigation
- `feature/inference/.../telemetry/PromptCacheSidecarDiagnostics.kt` — current diagnostics

## 1. The Problem

### 1.1 Symptom

When using DeepSeek (direct API and via OpenRouter), prompt cache hit rates are high (~80–95%) for several consecutive turns while sidecars are **frozen and deduped**, then collapse dramatically (often to 0–10%) the moment a **new live sidecar** is added — even though:

- Our wire JSON diagnostics show the **JSON prefix is stable** through the prior conversation
- The new sidecar appears to be inserted at the **tail** of the `messages[]` array (before the latest user message)
- Only the volatile tail (latest user message, new assistant content) should be uncached

Empirical log pattern (5-turn test, chat `d8f86b40-…`):

| Turn | hitPct | frozenOnWire | Notes |
|------|--------|--------------|-------|
| 1–4 | 62–85% | 1 | Stable frozen sidecar, deduped live |
| 5 | **10%** | 1 + new live | `insert@8107` in JSON — new system message mid-array; `block0Match=100%` but `fullMatch=40%` |

The misleading `block0Match=100%` compares only the first 512 **JSON characters**, which end before the sidecar region (~char 518+). JSON-order stability ≠ tokenization-order stability.

### 1.2 Root Cause Hypothesis (validated)

**DeepSeek (and similar models) do not tokenize the `messages[]` array in JSON order for `role: system` content.**

Instead, the model's **chat template** (Jinja) performs a two-pass transform before inference:

1. **Pass 1:** Collect ALL messages with `role: system` from anywhere in the array and concatenate them into a single `system_prompt` string.
2. **Pass 2:** Emit `{bos_token}{system_prompt}` first, then iterate user/assistant/tool turns. System messages at JSON positions other than index 0 are **not rendered at those positions** — their content is hoisted to token position 0.

Evidence:
- Official vLLM DeepSeek-R1 tool template: first loop collects all system roles; second loop outputs `{{- bos_token }}{{- ns.system_prompt }}` before any user/assistant content
- DeepSeek V3.1 docs: encoded format is always `<|begin_of_sentence|>{system prompt}<|User|>{query}...`
- DeepSeek KV cache docs: cache prefix units are things like "system message + user message" — system is always the leading block
- vLLM PR #44505: serving stacks coalesce mid-array system messages into the leading block before template application (Qwen family; same class of problem)

**Consequence for our sidecar design:**

We currently deliver sidecars as separate `role: system` messages inserted at computed indices in `messages[]` (via `OpenAiCompatibleVolatilePlacement.buildMappedBodyWithSidecars`). Even when we insert a "new" sidecar at the **tail anchor** in JSON, DeepSeek's template **merges it into the front system block at token 0**, changing the entire cached prefix. This explains why:

- Frozen-only turns cache well (one stable system block)
- Adding a new live sidecar breaks cache early, despite appearing to only extend the JSON tail
- Our JSON prefix diagnostics look stable while server-side cache misses occur

**This is a client-side wire-format bug, not provider-side KV eviction.**

### 1.3 What We Must Fix

Stop emitting sidecars as `role: system` messages on OpenAI-compatible wire paths. Embed sidecar content as **suffix annotations on `role: user` message content** instead, so chat template tokenization preserves conversation order and frozen sidecars remain byte-stable at their original positions.

## 2. How Our Current Sidecar System Works

### 2.1 Sidecar Content

Built by `RustLoopInferenceService.buildContextSidecars()` (`feature/inference/.../RustLoopInferenceService.kt`):

```
Reference policy: IDs are exact lookups only—never browse. ...
[optional network status string — volatile]
References: [context reference lines]
State ledger JSON: {...}
Tool context index JSON: {...}
File context index JSON: {...}
```

- Volatile fields (`tool_decisions`, `summary_refs`, etc.) are stripped by `ContextSidecarCompaction` before wire
- `cacheHints` are logged but NOT included in sidecar text (dedupe stability)
- Returns `null` when references, JSON blobs, and network status are all empty
- Native tool calling providers may omit tool context index from wire

### 2.2 Append-Only Context (session-scoped state machine)

`ProviderAppendOnlyContext` (`feature/inference/.../appendonly/ProviderAppendOnlyContext.kt`) per chat session:

| Concept | Purpose |
|---------|---------|
| `StablePrefixSnapshot` | Frozen `systemPrompt` + `toolsFingerprint` — only changes when tools/disclosure change |
| `AppendOnlyMessageLog` | Provider-normalized messages; grows append-only; digest mismatch clears state |
| `frozenSidecarSnapshots` | List of `FrozenSidecarSnapshot(text, mappedInsertIndex)` — replayed every turn |
| `lastShippedSidecarText` | Dedupe gate — if new sidecar text equals this, live sidecar is suppressed |
| `resolveContextSidecarsForWire(built)` | Returns sidecar text for live shipping, or `null` if dedupe skip |
| `endTurnFreezeSidecar(text, mappedInsertIndex)` | Promotes live sidecar to frozen snapshot after turn; sets `lastShippedSidecarText` |

**Lifecycle per turn:**

1. Rust loop calls `completeModelTurn` → `buildContextSidecars()` produces sidecar string
2. `resolveContextSidecarsForWire(sidecarText)` — if text unchanged from last shipped, returns `null` (no live sidecar)
3. Wire assembly inserts frozen replays + optional live sidecar
4. API call
5. If live sidecar was shipped (`wireSidecar != null`), `endTurnFreezeSidecar(wireSidecar, tailInsertIndex)` freezes it

**Clear conditions:** digest mismatch, compaction shorter array, Rust compaction boundary, session invalidate — all wipe frozen sidecars (cache safety over retention).

### 2.3 OpenAI-Compatible Wire Placement (CURRENT — broken)

`OpenAiCompatibleVolatilePlacement.buildMappedBodyWithSidecars()` (`feature/inference/.../appendonly/OpenAiCompatibleVolatilePlacement.kt`):

- Iterates `mappedBody` (OpenAI chat completion message params)
- At each `mappedInsertIndex`, inserts frozen sidecars as **`role: system`** messages **before** `mappedBody[index]`
- Live sidecar inserted at `tailInsertIndex` (typically `mappedBodySize - 1`, before last user message)
- Trailing frozen sidecars handled for indices >= mappedBody.size
- Both frozen and live use identical `systemSidecarMessage(text)` — plain system role, no cache_control for DeepSeek

Used by: `BaseOpenAiSdkInferenceService` (DeepSeek, xAI, OpenRouter OpenAI-compat, etc.)

### 2.4 Anthropic / Google Wire Placement (REFERENCE — already prepends to message content)

`SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex()` + provider-specific prepend:

- **Anthropic** (`AnthropicInferenceServiceImpl`): prepends sidecar text(s) to each session message's content at matching session index — NOT separate system messages
- **Google** (`GoogleInferenceServiceImpl`): same pattern via `sidecarPrependTextForIndex`

These providers already avoid the system-role hoisting problem for sidecars. They prepend raw sidecar text without a `[Sidecar:]` delimiter today.

### 2.5 Existing Metadata Pattern (REFERENCE — what we want to mirror)

User messages already carry wire-only metadata the UI does not show:

**Injection (live turn):** `ChatInferenceRequestPreparer.promptWithTemporalMetadata()`:
```
{user visible text}

[Metadata: Sent at Thursday, June 18, 2026 at 3:45 PM]
```

**Rehydration (historical turns):** `ChatHistoryRehydrator` appends `[Metadata: Sent at ...]` based on `createdAt`.

**UI stripping:** Display mappers use `substringBefore("\n\n[Metadata:")` to hide metadata from the user.

Sidecars are currently **wire-only** (not stored in Room/message DB). Frozen snapshots live in session-scoped `ProviderAppendOnlyContext`, not persisted across app restarts (rehydration rebuilds from committed inference state).

### 2.6 Diagnostics (already built)

- `PROMPT_CACHE` — hitPct, frozenSidecarCount, liveSidecarPresent, wire hashes
- `PROMPT_CACHE_JSON_BREAK` — byte-level JSON prefix diff (SDK wire serializer)
- `PROMPT_CACHE_SIDECAR` / `_DELTA` / `_JSON` — sidecar manifest, turn-to-turn delta, system-role JSON spans

After this change, diagnostics must classify sidecars by **user-content suffix spans** (`[Sidecar:...]`), not `role: system` JSON slots.

### 2.7 Multi-Hop Tool Loop

`completeModelTurn` may run **multiple times** per user send (pre-tool, post-tool). Each hop can produce a different sidecar string. The append-only model allows multiple frozen sidecars at different `mappedInsertIndex` values within one assistant turn. Dedupe suppresses live re-shipping when ledger unchanged.

## 3. Invariants (MUST NOT VIOLATE)

### INV-1: Frozen sidecars replay at original anchor — never collapse, never shift

Each `FrozenSidecarSnapshot` is permanently associated with the `mappedInsertIndex` where it first shipped. On every subsequent request, that exact sidecar text must be re-injected at **that same user message anchor** with **byte-identical content** (including the `[Sidecar:]` wrapper once frozen).

**Do NOT** collapse multiple frozen sidecars into one block.
**Do NOT** move a frozen sidecar from an older user message to a newer one.
**Do NOT** re-encode or re-compact frozen snapshot text on replay.

**Why:** Prefix cache stability requires historical user message bytes to never change. Collapsing or repositioning sidecars rewrites the tokenized prefix.

### INV-2: Live sidecars ship only on the tail user message, after `[Metadata:]`

New (non-deduped) sidecar content is appended only to the **latest user message** wire content, in this order:

```
{user-visible text}

[Metadata: Sent at ...]

[Sidecar: {sidecar body}]
```

**Why:** Metadata is already volatile and tail-anchored. Sidecar follows metadata so both stay in the uncached tail region during tokenization (user content stays in conversation order; not hoisted to system block).

### INV-3: Dedupe gate unchanged in semantics

`resolveContextSidecarsForWire` continues to suppress live sidecar shipping when `built.trim() == lastShippedSidecarText`. When deduped:

- No new `[Sidecar:]` on tail user message
- All frozen sidecars still replay at their anchors

Freeze (`endTurnFreezeSidecar`) only when live sidecar was actually shipped (`wireSidecar != null`).

### INV-4: Stable system prompt stays singular and front-only

Only ONE `role: system` message at JSON index 0 (the app's configured system prompt). Sidecar content must NEVER appear as `role: system` on OpenAI-compatible wire paths.

**Why:** Any additional system role content gets hoisted to token 0 by DeepSeek's chat template.

### INV-5: Sidecars remain wire-only — not persisted in message DB, not shown in UI

Users must not see `[Sidecar:...]` in chat UI. Strip at display time (same pattern as `[Metadata:]`). DB stores clean user-visible text only.

### INV-6: Pi append-only invariant preserved

- Committed provider message bytes for historical turns must not be rewritten in place
- `ProviderAppendOnlyContext` digest mismatch / compaction clear semantics unchanged
- LiteRT local path unchanged (continues merging sidecars into system prompt string)

### INV-7: Model must be told `[Sidecar:]` is internal host metadata, not user speech

Because sidecars appear inside `role: user` messages, the model may otherwise treat them as user input. Add an **explicit, stable, once-only** instruction to the system prompt (index 0):

```
Internal message annotations:
Messages may contain blocks appended by the application, not written by the user:
  [Metadata: ...]  — send timestamp
  [Sidecar: ...]   — internal session state (tool ledger, reference index, routing context)

These blocks are host-injected context. Do not quote them, summarize them, acknowledge
their presence, or respond to them as if the user said them. Use them silently to inform
tool routing and reference lookups only.
```

This must be byte-stable across turns (no timestamps/UUIDs in the rule itself).

### INV-8: Pre-retrieval sidecars follow the same embedding rules

`PreRetrievalContextSidecar` (RAG/volatile turn context) currently also ships as system role on OpenAI path. Must migrate to `[Sidecar:]` embedding at its anchor index, same freeze/replay semantics.

### INV-9: Provider scope

- **Must change:** OpenAI-compatible path (`OpenAiCompatibleVolatilePlacement`, `BaseOpenAiSdkInferenceService`)
- **Should align:** Anthropic/Google prepend paths — adopt same `[Sidecar:]` delimiter and ordering (metadata before sidecar) for consistency; these already prepend to message content
- **Must NOT change:** LiteRT / on-device (`LlamaInferenceServiceImpl` sidecar merge into system prompt)

## 4. New Design (Target Behavior)

### 4.1 Wire content shape per user message

For user message at anchor index `N`:

**Historical frozen anchor (turn where sidecar was born and every turn after):**
```
{user-visible text from DB / mapper}

[Metadata: Sent at {timestamp}]

[Sidecar: {frozen sidecar text exactly as captured at freeze time}]
```

**Current turn (live sidecar, ledger changed):**
```
{user-visible text}

[Metadata: Sent at {now}]

[Sidecar: {fresh compacted ledger from buildContextSidecars}]
```

**Deduped turn (ledger unchanged):**
```
{user-visible text}

[Metadata: Sent at {now}]
```
(no `[Sidecar:]` on tail; frozen anchors unchanged)

### 4.2 Placement logic replacement

Replace `OpenAiCompatibleVolatilePlacement.buildMappedBodyWithSidecars` system-message insertion with **user-content suffix injection**:

1. Map session → `mappedBody` as today (unchanged)
2. For each user message param at index `i`:
   - Start with visible content (+ existing metadata if applicable)
   - If frozen snapshots exist for `mappedInsertIndex == i`: append `\n\n[Sidecar: {text}]` for each (freeze order)
   - If `i == tailInsertIndex` and live sidecar resolved non-null: append `\n\n[Sidecar: {live text}]` **after** metadata
3. Do NOT add any `role: system` sidecar messages
4. Assistant/tool messages: unchanged

Consider extracting a shared helper (e.g. `SidecarUserContentEmbedder`) used by OpenAI-compatible, Anthropic, and Google paths.

### 4.3 Freeze semantics

`endTurnFreezeSidecar(text, mappedInsertIndex)` should store the **full embedded block** or the **raw sidecar body** consistently — pick one and document. Recommended: store raw sidecar body; wrapper `[Sidecar: ...]` applied uniformly at embed time so wrapper format can evolve without invalidating frozen bytes (actually for INV-1 byte stability, freeze should capture exact suffix bytes OR always apply wrapper identically — planner must resolve).

On freeze: `lastShippedSidecarText = sidecarText.trim()` (unchanged).

### 4.4 UI / display stripping

Extend metadata stripping to also remove `[Sidecar:...]`:

```kotlin
content.substringBefore("\n\n[Metadata:")
    .substringBefore("\n\n[Sidecar:")
```

Audit all paths: `ChatScreen` mappers, rehydration display, history, studio.

### 4.5 System prompt addition

Add INV-7 annotation rule to stable system prompt composition (likely `PromptComposer` or provider system prompt assembly — planner to identify exact injection point ensuring byte stability and no double-injection on rehydrate).

### 4.6 Diagnostics update

- `PromptCacheSidecarDiagnostics`: detect `[Sidecar:` spans in user message JSON content fields, not system role slots
- Update tests in `PromptCacheSidecarDiagnosticsTest`, `AppendOnlyOpenAiSidecarPlacementTest`, `XaiPromptCacheLiveContextTest`
- Success metric: 5-turn DeepSeek test with ledger growth → 85%+ hitPct on turns 2–5

## 5. Key Files to Plan Against

| File | Role |
|------|------|
| `feature/inference/.../appendonly/OpenAiCompatibleVolatilePlacement.kt` | **Primary change** — replace system message insertion with user suffix embed |
| `feature/inference/.../appendonly/ProviderAppendOnlyContext.kt` | Freeze/dedupe state machine (likely minimal changes) |
| `feature/inference/.../appendonly/FrozenSidecarSnapshot.kt` | Snapshot shape (may need `embeddedWrapper` decision) |
| `feature/inference/.../appendonly/SessionIndexVolatilePlacement.kt` | Anthropic/Google index logic — align delimiter |
| `feature/inference/.../BaseOpenAiSdkInferenceService.kt` | Wire assembly orchestration, freeze call site, diagnostics |
| `feature/inference/.../AnthropicInferenceServiceImpl.kt` | Prepend path — adopt `[Sidecar:]` |
| `feature/inference/.../GoogleInferenceServiceImpl.kt` | Prepend path — adopt `[Sidecar:]` |
| `feature/inference/.../RustLoopInferenceService.kt` | `buildContextSidecars` content (unchanged likely) |
| `feature/inference/.../telemetry/PromptCacheSidecarDiagnostics.kt` | Update classification |
| `core/domain/.../ChatInferenceRequestPreparer.kt` | Metadata injection reference |
| `core/domain/.../ChatHistoryRehydrator.kt` | Historical metadata — coordinate ordering with sidecar embed |
| `feature/chat/...` UI mappers | Strip `[Sidecar:]` from display |
| `AppendOnlyOpenAiSidecarPlacementTest.kt` | Primary unit test file to rewrite/extend |
| `ProviderAppendOnlyContextTest.kt` | Dedupe/freeze behavior tests |

## 6. Planning Deliverables Expected

1. **Phase breakdown** — minimal shippable slices
2. **Detailed data flow** — before/after diagrams for turn N (live) → turn N+1 (frozen replay) → turn M (new live sidecar)
3. **Test plan** — unit tests per invariant (INV-1 through INV-9); which existing tests break and how to update
4. **Edge cases** — tool result messages at tail anchor, multi-hop freeze in one assistant turn, pre-retrieval sidecar, compaction clearing frozen state, empty sidecar/null dedupe, image-bearing user messages
5. **Rollback strategy** — feature flag vs provider-scoped toggle vs revert commit `bd7e01f8`
6. **Validation protocol** — 5-turn release build on emulator, logcat filters, success criteria (85%+ hit rate turns 2–5)
7. **Risk assessment** — model confusion from user-role sidecars (mitigated by INV-7), token count increase from wrapper overhead, Anthropic cache_control breakpoint shifts

## 7. Explicit Non-Goals

- Do not change LiteRT on-device sidecar merge behavior
- Do not change Room schema or persist sidecars to DB
- Do not collapse/compact/move frozen sidecars
- Do not add `cache_control` markers for DeepSeek (automatic caching only)
- Do not assume provider-side eviction — treat as wire-format bug until proven otherwise after this fix

## 8. Success Criteria

After implementation and release-build emulator testing (DeepSeek direct API, no `pm clear`, vault unlock required):

1. Turns 1–4 with stable frozen sidecar: **≥ 80% hitPct**
2. Turn where new live sidecar added (ledger changed): **≥ 80% hitPct** (not 0–10%)
3. `PROMPT_CACHE_JSON_BREAK` shows divergence only at tail user message suffix, not at system block or mid-array system role insertion
4. UI shows no `[Sidecar:]` or `[Metadata:]` content to user
5. Model does not verbally acknowledge sidecar/metadata blocks in responses (manual spot check)
6. All existing `:feature:inference` unit tests updated and passing; `./gradlew :feature:inference:testDebugUnitTest` green