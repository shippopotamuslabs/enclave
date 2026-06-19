# Spec: sidecar-user-embed

Tactical plan derived from the approved `research.md`, `design.md` (D1–D8),
and `structure.md` (7 vertical slices). No runnable code. Signatures match
`test_spec.md` exactly.

Scope: OpenAI-compatible chat-completions **and** Responses wire paths.
Anthropic/Google/LiteRT untouched (follow-up slice, D4 / INV-9).

## Feature summary

Stop emitting context sidecars as `role: system` messages on OpenAI-compatible
wire. Embed them as UUID-stamped `[Sidecar-<uuid>: <body>]` suffix annotations
on `role: user` message content, appended after any existing `[Metadata:]`
suffix, so DeepSeek's chat template no longer hoists sidecars into the leading
system token block and the cached prefix stays byte-stable when a new live
sidecar is added. Frozen snapshots replay byte-identically at their original
user-message anchor; the dedupe gate is unchanged in semantics; the single
configured system prompt remains the only `role: system` message and gains a
stable rule teaching the model that `[Sidecar-<uuid>:]` blocks are host
context. Diagnostics classify sidecar spans inside user content. No Room
schema changes; sidecars remain wire-only.

## Vertical slice table

| # | Slice | Goal | Scenarios covered |
|---|-------|------|-------------------|
| 1 | Sidecar marker + embedder primitive | `SidecarMarker`, `SidecarUserContentEmbedder`, UUID regex; unit-tested in isolation. | S-EMBED-01..06 |
| 2 | Snapshot shape + freeze/dedupe threading | `FrozenSidecarSnapshot.markerId`, `ResolvedSidecar`, live→frozen id continuity; no wire change. | S-FREEZE-01..05 |
| 3 | OpenAI chat-completions wire migration | Replace system-message insertion with user-content suffix embed. | S-WIRE-01..08 |
| 4 | OpenAI Responses wire migration | Drop `SystemText` sidecar segments; embed into user `Input` content. | S-RESP-01..04 |
| 5 | INV-7 system-prompt rule | UUID-aware rule in `SearchToolPromptComposer.metadataRules`. | S-RULE-01..03 |
| 6 | Diagnostics reclassification | Classify `[Sidecar-<uuid>:` spans in user content; keep system-span check as INV-4 invariant. | S-DIAG-01..04 |
| 7 | Validation + test rewrite | Rewrite F12 tests in place; add invariant tests; emulator 5-turn DeepSeek validation. | S-VAL-01 (emulator, manual) |

## File structure table

### Create

| File | Responsibility |
|------|----------------|
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/SidecarMarker.kt` | `SidecarMarker` data class, `SidecarMarkerFactory.newMarker(body)`, `SidecarMarker.wireBlock()`, `SidecarMarkerRegex.SPAN`, `ResolvedSidecar` data class. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/appendonly/SidecarUserContentEmbedder.kt` | `embed(baseContent, markers)` and `stripSidecarBlocks(content)`; the single owner of the `\n\n[Sidecar-<uuid>: <body>]` wire shape. |
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/appendonly/SidecarUserContentEmbedderTest.kt` | Slice 1 unit tests (S-EMBED-01..06). |

### Modify (production)

| File | Change |
|------|--------|
| `feature/inference/.../appendonly/FrozenSidecarSnapshot.kt` | Add `markerId: String` field (Slice 2). |
| `feature/inference/.../appendonly/ProviderAppendOnlyContext.kt` | `resolveContextSidecarsForWire` returns `ResolvedSidecar?`; `endTurnFreezeSidecar` / `appendVolatileSidecar` accept `markerId`; live→frozen id continuity (Slice 2). |
| `feature/inference/.../appendonly/OpenAiCompatibleVolatilePlacement.kt` | Remove `buildMappedBodyWithSidecars` + `systemSidecarMessage`; add `embedSidecarsIntoUserMessages` (Slice 3). |
| `feature/inference/.../BaseOpenAiSdkInferenceService.kt` | `completeSingleTurn` wire-assembly block uses `embedSidecarsIntoUserMessages`; `wireSidecar` becomes `ResolvedSidecar?`; `finalizeSidecarFreeze` threads `markerId`; pre-retrieval becomes `ResolvedSidecar?`; add `replaceUserTextContent` helper (Slice 3). |
| `feature/inference/.../OpenAiCompatibleResponseSidecarPlacement.kt` | `buildSegmentsWithSidecars` stops emitting `SystemText` sidecar segments; embeds into user `Input` content (Slice 4). |
| `feature/inference/.../AgentLoopOpenAiResponseMapper.kt` | Only if a content-rewrite hook is required for Slice 4 (add `replaceResponseInputText` helper). |
| `core/domain/.../SearchToolPromptComposer.kt` | `metadataRules(compact)` gains the UUID-aware `[Sidecar-<uuid>:]` rule (full + compact) (Slice 5). |

### Modify (tests)

| File | Change |
|------|--------|
| `feature/inference/src/test/.../AppendOnlyOpenAiSidecarPlacementTest.kt` | Rewrite assertions from `role: system` sidecar slots to user-content suffixes (Slice 3/7). |
| `feature/inference/src/test/.../XaiPromptCacheLiveContextTest.kt` | Update wire-shape assertions (Slice 7). |
| `feature/inference/src/test/.../appendonly/ProviderAppendOnlyContextTest.kt` | Update for `markerId` shape + live→frozen continuity (Slice 2/7). |
| `feature/inference/src/test/.../AgentLoopOpenAiResponseMapperTest.kt` | Assert no sidecar `SystemText` segments; sidecar in user input content (Slice 4/7). |
| `core/domain/src/test/.../chat/SearchToolPromptComposerTest.kt` | Assert INV-7 rule present in both compact and full variants (Slice 5/7). |
| `feature/inference/src/test/.../telemetry/PromptCacheSidecarDiagnosticsTest.kt` | (If present) assert user-content span classification (Slice 6/7). |

### Do not touch

`LlamaInferenceServiceImpl.kt`, `AnthropicInferenceServiceImpl.kt`,
`GoogleInferenceServiceImpl.kt`, `SessionIndexVolatilePlacement.kt`,
`ChatHistoryRehydrator.kt`, `ChatInferenceRequestPreparer.kt`, Room
entities/DAOs, `ProviderMessageDigest.kt`, `AppendOnlyMessageLog.kt`.

## API contracts and interfaces

```kotlin
// SidecarMarker.kt
data class SidecarMarker(val markerId: String, val body: String)

object SidecarMarkerFactory {
    fun newMarker(body: String): SidecarMarker
}

fun SidecarMarker.wireBlock(): String   // "[Sidecar-$markerId: $body]"

object SidecarMarkerRegex {
    val SPAN: Regex   // matches "[Sidecar-[0-9a-fA-F-]{36}:"
}

data class ResolvedSidecar(val text: String, val markerId: String)

// SidecarUserContentEmbedder.kt
object SidecarUserContentEmbedder {
    fun embed(baseContent: String, markers: List<SidecarMarker>): String
    fun stripSidecarBlocks(content: String): String
}

// FrozenSidecarSnapshot.kt (modified)
data class FrozenSidecarSnapshot(
    val text: String,
    val markerId: String,
    val mappedInsertIndex: Int,
)

// ProviderAppendOnlyContext.kt (modified signatures)
fun resolveContextSidecarsForWire(built: String?): ResolvedSidecar?
fun endTurnFreezeSidecar(sidecarText: String, markerId: String, mappedInsertIndex: Int)
fun appendVolatileSidecar(sidecarText: String, markerId: String, delivery: VolatileContextDelivery)

// OpenAiCompatibleVolatilePlacement.kt (replaced function)
fun embedSidecarsIntoUserMessages(
    mappedBody: List<ChatCompletionMessageParam>,
    frozenSidecars: List<FrozenSidecarSnapshot>,
    preRetrievalSidecar: ResolvedSidecar?,
    preRetrievalInsertIndex: Int?,
    liveSidecar: ResolvedSidecar?,
    tailInsertIndex: Int,
): List<ChatCompletionMessageParam>
```

## Data model / schema changes

None. `FrozenSidecarSnapshot` is an in-memory, session-scoped struct (not
persisted to Room — research F5/F9). No new tables, columns, or migrations.
`committedInferencePrompts` and `Message.content` continue to store clean
user-visible text only; `[Sidecar-<uuid>:]` bytes are wire-only and never
persisted (INV-5).

## Ordered implementation tasks grouped by slice

### Slice 1 — Sidecar marker + embedder primitive

- **T1.1** Create `SidecarMarker.kt` with `SidecarMarker`, `SidecarMarkerFactory.newMarker` (generates a lowercase hyphenated UUID via `java.util.UUID.randomUUID().toString()`), `wireBlock()` returning `"[Sidecar-$markerId: $body]"`, `SidecarMarkerRegex.SPAN` = `Regex("\\[Sidecar-[0-9a-fA-F-]{36}:")`, and `ResolvedSidecar(text, markerId)`. → S-EMBED-01, S-EMBED-06
- **T1.2** Create `SidecarUserContentEmbedder.kt` with `embed(baseContent, markers)` (append `"\n\n" + m.wireBlock()` per marker in order, never reorder existing `[Metadata:]`) and `stripSidecarBlocks(content)` (remove every block whose opening matches `SidecarMarkerRegex.SPAN` through its matching `]`, leaving `[Metadata:]` and all other content intact). → S-EMBED-02, S-EMBED-03, S-EMBED-04, S-EMBED-05
- **T1.3** Create `SidecarUserContentEmbedderTest.kt` covering S-EMBED-01..06. → all Slice 1 scenarios

**Checkpoint:** `./gradlew :feature:inference:testDebugUnitTest` green; no production wire path changed.

### Slice 2 — Snapshot shape + freeze/dedupe threading

- **T2.1** Add `markerId: String` to `FrozenSidecarSnapshot`. → S-FREEZE-01
- **T2.2** Change `resolveContextSidecarsForWire(built: String?): ResolvedSidecar?`: on the non-deduped path, allocate a `markerId` via `SidecarMarkerFactory.newMarker(trimmed).markerId` and remember it in a private `pendingLiveMarkerId: String?` field so the immediately following `endTurnFreezeSidecar` for the same live sidecar reuses it; on the deduped path return `null` and clear `pendingLiveMarkerId`. Dedupe comparison stays `trimmed == lastShippedSidecarText` (text-only, INV-3). → S-FREEZE-02, S-FREEZE-03
- **T2.3** Change `endTurnFreezeSidecar(sidecarText, markerId, mappedInsertIndex)` to store the `markerId` into the snapshot; if `markerId` is blank, fall back to `pendingLiveMarkerId` (then clear it) so the live→frozen transition preserves the id (D7). Set `lastShippedSidecarText = sidecarText.trim()`. → S-FREEZE-04
- **T2.4** Change `appendVolatileSidecar(sidecarText, markerId, delivery)` to store `markerId`. → S-FREEZE-05
- **T2.5** Update `ProviderAppendOnlyContextTest.kt` for the new shape + live→frozen id continuity + dedupe-does-not-allocate. → S-FREEZE-01..05

**Checkpoint:** `./gradlew :feature:inference:testDebugUnitTest` green; wire emission unchanged (old `OpenAiCompatibleVolatilePlacement` still compiles and is called, but the `markerId` is now threaded).

### Slice 3 — OpenAI chat-completions wire migration

- **T3.1** Add `replaceUserTextContent(param: ChatCompletionMessageParam, newContent: String): ChatCompletionMessageParam` private helper in `BaseOpenAiSdkInferenceService` that rebuilds a user param with the new text content, preserving the image-content-parts structure by appending `newContent` to the **first text part** when parts exist (F15/D3), or rebuilding the plain-text user param otherwise. → S-WIRE-07
- **T3.2** In `OpenAiCompatibleVolatilePlacement`, remove `buildMappedBodyWithSidecars` and `systemSidecarMessage`; add `embedSidecarsIntoUserMessages(...)` that iterates `mappedBody` and, for each **user** param at a sidecar-bearing index, builds the ordered `List<SidecarMarker>` (frozen snapshots at that `mappedInsertIndex` in freeze order, then pre-retrieval if its index matches, then live if `index == tailInsertIndex`), calls `SidecarUserContentEmbedder.embed(currentText, markers)`, and returns the rebuilt param via `replaceUserTextContent`. Non-user params and user params without sidecars pass through unchanged. Trailing-index frozen sidecars (indices >= `mappedBody.size`) attach to the last user message in the body, preserving F1 trailing-anchor semantics. The returned list has the **same length** as `mappedBody` — no system messages inserted. → S-WIRE-01, S-WIRE-02, S-WIRE-03, S-WIRE-04, S-WIRE-08
- **T3.3** In `BaseOpenAiSdkInferenceService.completeSingleTurn`: change `wireSidecar` to `ResolvedSidecar?`; change `preRetrievalSidecarText` to `preRetrievalSidecar: ResolvedSidecar?` (built from `PreRetrievalContextSidecar.format(...)` + `SidecarMarkerFactory`); freeze pre-retrieval via the new `endTurnFreezeSidecar(text, markerId, idx)`; replace the `messages` buildList to call `embedSidecarsIntoUserMessages(...)` after the stable system prepend; update `finalizeSidecarFreeze` to pass `wireSidecar.markerId`. → S-WIRE-01..08
- **T3.4** Rewrite `AppendOnlyOpenAiSidecarPlacementTest.kt` assertions to the new wire shape (exactly one `role: system`; sidecar bodies as `[Sidecar-<uuid>:]` suffixes inside the correct user message; frozen replay byte-identical across turns; live only on tail after `[Metadata:]`; deduped turns carry no live suffix; image-bearing users keep sidecar in the text part). → S-WIRE-01..08

**Checkpoint:** `./gradlew :feature:inference:testDebugUnitTest` green; chat-completions wire no longer emits sidecar system messages.

### Slice 4 — OpenAI Responses wire migration

- **T4.1** Add `replaceResponseInputText(segment: ResponseWireSegment.Input, newContent: String): ResponseWireSegment.Input` helper (in `OpenAiCompatibleResponseSidecarPlacement` or `AgentLoopOpenAiResponseMapper`) that rebuilds the `ResponseInputItem.Message` with `ResponsesInputMessageSupport.textInputItem(role, newContent)` for the text-only case, or rebuilds the multi-part `ResponseInputItem.Message` appending to the first input text content part for the image case (F3). → S-RESP-04
- **T4.2** In `OpenAiCompatibleResponseSidecarPlacement.buildSegmentsWithSidecars`, remove all `result.add(ResponseWireSegment.SystemText(frozenText / preRetrieval / currentSidecar))` sidecar emissions; instead, for each user `ResponseWireSegment.Input` at a sidecar-bearing index, build the ordered `List<SidecarMarker>` (frozen at that remapped index, then pre-retrieval, then live at `tailInsertIndex`) and rewrite the segment's content via `replaceResponseInputText` + `SidecarUserContentEmbedder.embed`. Keep `remapFrozenSidecarsForResponses` unchanged. The prepended `ResponseWireSegment.SystemText(systemPrompt)` (the stable system prompt) remains. → S-RESP-01, S-RESP-02, S-RESP-03
- **T4.3** Update `AgentLoopOpenAiResponseMapperTest.kt` to assert no sidecar `SystemText` segments and sidecar suffix present in user input content. → S-RESP-01..04

**Checkpoint:** `./gradlew :feature:inference:testDebugUnitTest` green; Responses path no longer emits sidecar system segments.

### Slice 5 — INV-7 system-prompt rule

- **T5.1** In `SearchToolPromptComposer.metadataRules(compact)`, add the UUID-aware `[Sidecar-<uuid>:]` annotation rule. Full variant: a stable paragraph (no UUIDs/timestamps in the rule text itself) teaching the model that `[Sidecar-<uuid>: ...]` blocks are host-injected session state (tool ledger, reference index, routing context), must be used silently for tool routing and reference lookups, and must never be quoted, summarized, or acknowledged. Compact variant (approved, D6): the equivalent one-line sentence. → S-RULE-01, S-RULE-02, S-RULE-03
- **T5.2** Update `SearchToolPromptComposerTest.kt` to assert the rule text is present in both variants and contains no per-turn variable content. → S-RULE-01..03

**Checkpoint:** `./gradlew :core:domain:testDebugUnitTest` green.

### Slice 6 — Diagnostics reclassification

- **T6.1** In `PromptCacheSidecarDiagnostics.captureFrom`, add a classification pass over `role: user` message content using `SidecarMarkerRegex.SPAN`, producing `UserContentSidecarSpan(wireIndex, markerId, bodyHash, length)` entries; add `userContentSpans: List<UserContentSidecarSpan>` to `SidecarManifest`. Keep `wirePlacements` / `wireSystemCount` (now expected to contain only `stable_system`) as the INV-4 invariant check. → S-DIAG-01, S-DIAG-02
- **T6.2** Add `userContentSpanDelta` (new/unchanged/removed by `markerId`) to `SidecarDelta` and include it in `formatDelta`. → S-DIAG-03
- **T6.3** Keep `extractSystemJsonSpans` and `formatBlock0` unchanged (criterion-3 instrumentation / INV-4 check). → S-DIAG-04
- **T6.4** Update `PromptCacheSidecarDiagnosticsTest.kt` (if present) or add minimal tests for the new span classification. → S-DIAG-01..04

**Checkpoint:** `./gradlew :feature:inference:testDebugUnitTest` green; diagnostics detect sidecar spans in user content.

### Slice 7 — Validation + test rewrite (integration checkpoint)

- **T7.1** Final pass over all test files enumerated in the Modify (tests) table; ensure every assertion matches the new wire shape (D8 — no parallel old-path tests). → all S-WIRE / S-RESP scenarios
- **T7.2** Add explicit invariant tests: INV-1 (frozen replay byte-identical at original anchor across ≥3 turns), INV-2 (live suffix after `[Metadata:]` on tail only), INV-4 (exactly one `role: system`), INV-5 (`stripSidecarBlocks` leaves user content + `[Metadata:]` intact and removes only UUID-form blocks). → S-WIRE-02, S-WIRE-05, S-WIRE-06, S-EMBED-05
- **T7.3** Run CI-aligned checks: `./gradlew :feature:inference:testDebugUnitTest`, `./gradlew :core:domain:testDebugUnitTest`, `./gradlew ktlintCheck`, `./gradlew detekt` (maxIssues: 0). → S-VAL-01 (automated portion)
- **T7.4** Release-build emulator validation per planning-prompt §8: 5-turn DeepSeek direct API run, no `pm clear`, vault unlocked; logcat filters `PROMPT_CACHE`, `PROMPT_CACHE_JSON_BREAK`, `PROMPT_CACHE_SIDECAR`; success = ≥80% hitPct on turns 1–4 and on the new-live-sidecar turn; UI shows no `[Sidecar-<uuid>:]` or `[Metadata:]`; model does not verbally acknowledge the blocks. → S-VAL-01 (emulator, manual)

**Checkpoint:** all automated checks green; emulator success criteria met.

## Parallel-subagent execution strategy

Based on the inter-slice dependencies in `structure.md`:

- **Wave A (sequential, single agent):** Slice 1 → Slice 2. These are
  foundational primitives and the snapshot-shape change; they touch the same
  files (`SidecarMarker.kt`, `ProviderAppendOnlyContext.kt`,
  `FrozenSidecarSnapshot.kt`) and Slice 2 depends on Slice 1's types. Run as
  one subagent to avoid merge conflicts on the new files.
- **Wave B (parallel, two subagents after Wave A lands):** Slice 3
  (chat-completions wire) and Slice 5 (INV-7 rule) in parallel. Slice 3
  touches `:feature:inference` files; Slice 5 touches
  `core/domain/.../SearchToolPromptComposer.kt` and its test — **zero file
  overlap**, so they can run concurrently. Slice 5 only depends on Slice 1
  (for the marker form used in the rule text), satisfied by Wave A.
- **Wave C (single subagent after Slice 3):** Slice 4 (Responses wire).
  Depends on Slice 2's `ResolvedSidecar`/`markerId` and reuses the
  `replaceUserTextContent` pattern from Slice 3; touches
  `OpenAiCompatibleResponseSidecarPlacement.kt` and
  `AgentLoopOpenAiResponseMapper.kt` (no overlap with Slice 5). Could run in
  parallel with Slice 5 if Wave B is structured as 3+5, but Slice 4 depends on
  Slice 3's helper pattern, so it follows Slice 3.
- **Wave D (single subagent after Slices 3 + 4):** Slice 6 (diagnostics).
  Classifies the new wire shape produced by Slices 3 and 4; touches
  `PromptCacheSidecarDiagnostics.kt` only.
- **Wave E (orchestrator, after all):** Slice 7 (validation + test rewrite
  final pass + emulator). Run by the orchestrator since it includes the
  manual emulator validation and the cross-file test-consistency pass.

Dependency graph:
```
Wave A (S1→S2) ──┬──> Wave B: S3 ──> Wave C: S4 ──┐
                 └──> Wave B: S5 ─────────────────┼──> Wave D: S6 ──> Wave E: S7
```
Maximum parallelism: 2 subagents (Slices 3 and 5) in Wave B.

## Drift and escalation rules

- **Drift from design:** If any task discovers that an approved decision
  (D1–D8) cannot be honored without a contract violation, stop and surface
  the conflict to the user as a `Contract Waiver` request per `AGENTS.md`
  §3.1; do not silently deviate. Do not reopen settled design choices.
- **Test weakening:** A task may not delete or weaken an existing invariant
  test (e.g. INV-1 byte-stability, INV-3 dedupe) to make it pass; it must
  rewrite the assertion to the new wire shape while preserving the invariant
  intent. The Red Review and Final Review loops enforce this.
- **Scope creep:** Anthropic/Google/LiteRT/Room changes are out of scope
  (D4/INV-9). If a task finds it must touch one of those to compile, stop and
  escalate rather than expanding scope.
- **Emulator validation failure:** If the 5-turn DeepSeek run does not meet
  ≥80% hitPct, do not declare complete; report the `PROMPT_CACHE_JSON_BREAK`
  divergence point and treat as a wire-format regression to diagnose, not a
  provider-eviction assumption (non-goal §7).
- **Detekt/ktlint:** `detekt` has `maxIssues: 0`; any new violations must be
  fixed in-slice, not deferred.

## Self-review

- Every approved design decision (D1–D8) appears in tasks: D1 (T2.1/T3.2 —
  raw body + wrapper at embed), D2 (T3.3 — embed after sync), D3 (T1.2/T3.2/
  T4.2 — shared embedder), D4 (Do-not-touch list), D5 (T6.1/T6.2), D6 (T5.1),
  D7 (T1.1/T2.2/T2.3 — UUID markers + live→frozen continuity), D8 (T3.2
  removes old function, no flag).
- Slice order matches `structure.md` checkpoints; each slice has a runnable
  checkpoint.
- Every behavior-changing task references ≥1 scenario ID (see scenario column
  in the slice table and per-task arrows).
- Signatures match between `spec.md` and `test_spec.md` (verified against
  `SidecarMarker.kt` / `SidecarUserContentEmbedder.kt` / `FrozenSidecarSnapshot.kt`
  / `ProviderAppendOnlyContext.kt` / `OpenAiCompatibleVolatilePlacement.kt`
  shapes defined here and in `structure.md`).
- All existing files listed were verified in the codebase during research
  (F1–F14).
- No placeholders (`TBD`/`TODO`/`similar to`/`as above`/`implement later`).