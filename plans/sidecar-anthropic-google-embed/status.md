# Status: sidecar-anthropic-google-embed

## Phase Checklist
- [x] Research
- [x] Design — approval: yes (2026-06-19)
- [x] Structure — approval: yes (2026-06-19)
- [x] Plan — approval: yes (2026-06-19)
- [x] TDD Red
- [x] Red Review — result: pass (2026-06-19)
- [x] Implementation
- [x] Final Review — result: pass (2026-06-19)

## Current Gate
none

## Next Phase
Complete — Slice 4 manual emulator cache validation (S-VAL-02/03) as follow-up

## Plan approval resolution (user, 2026-06-19)
- Plan (spec.md + test_spec.md) APPROVED.
- User chose PLANNING ONLY — stop after plan; do not auto-start TDD Red.
- Resume protocol will pick TDD Red next (plan approved, red tests not written).

## Structure approval resolutions (user, 2026-06-19)
- Structure (4 slices) APPROVED.
- Implementation parallelism: SEQUENTIAL — Slice 2 (Anthropic) then Slice 3
  (Google). Do not parallelize the two provider slices.

## Design approval resolutions (user, 2026-06-19)
- Open Q3: KEEP + RENAME `SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex`
  → `sidecarMarkersForSessionIndex` (do not remove/inline).
- Open Q1/Q2: live Anthropic `cache_control` + Google `CachedContent` validation
  are a VALIDATION SLICE in this ticket (release-build emulator), not blockers.
  DA4/DA5 stand (no code change).

## Loop Counts
- Red Review: 1/3
- Final Review: 1/3

## Notes
- Follow-up slice to `sidecar-user-embed` (which deferred Anthropic/Google per
  design decision D4). OpenAI-compat slice already landed @ `d290c27c`.
- Branch baseline: `feat/deepseek` @ `d290c27c`.
- Shared primitives already exist and are reusable:
  `feature/inference/.../appendonly/SidecarMarker.kt`
  (`SidecarMarker`, `ResolvedSidecar`, `SidecarMarkerFactory`, `wireBlock()`,
  `SidecarMarkerRegex`) and
  `feature/inference/.../appendonly/SidecarUserContentEmbedder.kt`
  (`embed`, `stripSidecarBlocks`).
- Current deferred state (to be fixed):
  - Anthropic (`AnthropicInferenceServiceImpl.kt:230-247`) and Google
    (`GoogleInferenceServiceImpl.kt:155-179`) still raw-text **prepend** via
    `SessionIndexVolatilePlacement.sidecarTextsBeforeSessionIndex`, before
    content, no delimiter.
  - Both discard `ResolvedSidecar.markerId` (`?.text`) and mint throwaway
    markers at freeze (`AnthropicInferenceServiceImpl.kt:563`,
    `GoogleInferenceServiceImpl.kt:327`) — breaks D7 live→frozen continuity.
  - Anthropic `cache_control` breakpoints (`AnthropicPromptCacheSupport`,
    `stableHistoryTailIndex`) interact with sidecar placement.
- Contracts to respect: INFERENCE_RULES, ARCHITECTURE_RULES, CODE_STYLE_RULES,
  UNIT_TESTING.

### Red Review outcomes (pass, 2026-06-19)
- Scenario coverage: 21/21 executable scenarios (S-SIP-01..07, S-ANTH-01..08,
  S-GOOG-01..06); S-VAL-01..03 correctly deferred (manual/CI).
- Test command:
  `./gradlew :feature:inference:testDebugUnitTest --tests "*SessionIndexVolatilePlacementTest*" --tests "*AnthropicInferenceServiceImplCachePrefixTest*" --tests "*GoogleInferenceServiceImplSidecarWireTest*"`
- Result: 20 failed, 1 passed (S-ANTH-05) — failures are intended (S-SIP-*:
  stub `error("TDD Red")`; S-ANTH-*/S-GOOG-*: prepend wire lacks
  `[Sidecar-<uuid>: …]` suffix). S-ANTH-05 passing is acceptable (DA4
  no-change; cache-breakpoint assertions only).
- Findings: none material. Spec fidelity, 1:1 scenario mapping, stub
  discipline, and touched-file drift all verified.
- Decision: proceed to Implementation.

### TDD Red outcomes (complete, 2026-06-19)
- 21 unit tests written (7 S-SIP, 8 S-ANTH, 6 S-GOOG); S-VAL-01/02/03 skipped
  (manual/CI).
- Slice 1 stub: `SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(...)`
  body `error("TDD Red")`; `sidecarTextsBeforeSessionIndex` unchanged.
- Red result: 20/21 fail for intended reasons — S-SIP-* throw `IllegalStateException:
  TDD Red`; S-ANTH-*/S-GOOG-* fail with `IllegalArgumentException: Expected
  [Sidecar-<uuid>: ...] suffix` (production still prepends raw text). S-ANTH-05
  passes (cache-breakpoint assertions only; DA4 unchanged by prepend).
- Test commands:
  - `./gradlew :feature:inference:testDebugUnitTest --tests "*SessionIndexVolatilePlacementTest*"`
  - `./gradlew :feature:inference:testDebugUnitTest --tests "*AnthropicInferenceServiceImplCachePrefixTest*"`
  - `./gradlew :feature:inference:testDebugUnitTest --tests "*GoogleInferenceServiceImplSidecarWireTest*"`
  - `./gradlew :feature:inference:assembleDebug` (green)
- Changed files:
  - `feature/inference/src/main/kotlin/.../appendonly/SessionIndexVolatilePlacement.kt` (stub)
  - `feature/inference/src/test/kotlin/.../appendonly/SessionIndexVolatilePlacementTest.kt` (new)
  - `feature/inference/src/test/kotlin/.../AnthropicInferenceServiceImplCachePrefixTest.kt` (rewritten)
  - `feature/inference/src/test/kotlin/.../GoogleInferenceServiceImplSidecarWireTest.kt` (new)

### Research outcomes (complete)
- `research.md` written: 14 research questions, findings F1–F15.
- Self-review: passed. No production/test code modified.
- Verified current line numbers (ticket hints corrected): Anthropic resolve
  `?.text` `AnthropicInferenceServiceImpl.kt:230`; prepend
  `prependIndexSidecars` `:232-247`; freeze `:484-488` +
  `freezeContextSidecarIfPresent` `:555-567` with throwaway marker `:563`;
  cache config `:207-221`, breakpoint `:256-258`. Google resolve `?.text`
  `GoogleInferenceServiceImpl.kt:155`; `sidecarPrependTextForIndex` `:157-167`;
  freeze `:324-330` with throwaway marker `:327`.
- Decision-relevant findings for Design:
  - Both providers PREPEND raw sidecar text before content (`\n\n`-joined, no
    delimiter); INV-2 wants suffix-after-`[Metadata:]`. Shared
    `SidecarUserContentEmbedder.embed` / `SidecarMarker` / `ResolvedSidecar`
    are already `markerId`-aware and reachable in-module.
  - markerId-continuity gap: both discard `ResolvedSidecar.markerId` (`?.text`)
    and mint throwaway markers at freeze, vs OpenAI which threads
    `wireSidecar.markerId` (`BaseOpenAiSdkInferenceService.kt:463`).
  - Anthropic `cache_control` is block-level at `stableHistoryTailIndex =
    size-2` (`AnthropicPromptCacheSupport.kt:16-26`); live sidecar lands at
    `lastUserIndex` (= size-1 normally, uncached tail). Prefix→suffix move is
    cache-neutral within a block (inference); byte-stable frozen replay is the
    real requirement.
  - Google has no per-message breakpoint — server-side `CachedContent` keyed by
    `CacheSignature` over raw `message.content` (sidecar-mapped content not in
    the signature).
  - Pre-retrieval/volatile context is MERGED upstream into `msg.content` for
    Anthropic/Google (not a separate sidecar); both pass
    `preRetrievalSidecarText = null`. `[Metadata:]` is already the content tail.
  - INV-7 model rule is ALREADY in `SearchToolPromptComposer.kt:156-169` and
    reaches Anthropic/Google via the shared system prompt — no new rule needed.
- Test gaps for the later phases: only `AnthropicInferenceServiceImplCachePrefixTest`
  encodes the prepend byte shape + breakpoints (must be rewritten); NO test
  asserts the Google sidecar wire byte shape; no test covers
  metadata-before-sidecar ordering or live→frozen markerId continuity on these
  providers; `SessionIndexVolatilePlacement` has no direct unit test.
- Open unknowns: emulator/runtime validation of Anthropic cache_control and
  Google CachedContent reuse under the suffix change (not answerable from repo).

### Design outcomes (complete)
- `design.md` written (293 lines): Current state, Desired end state, Patterns to
  follow/avoid, decisions DA1–DA8, 3 open questions, self-review. No code, no
  placeholders.
- DA1: add marker-returning sibling `sidecarMarkersForSessionIndex` to
  `SessionIndexVolatilePlacement` + embed via shared `SidecarUserContentEmbedder`
  as a SUFFIX at each provider seam (Anthropic `prependIndexSidecars`→embed;
  Google mapper `prependedText`→`sidecarMarkers`). Rejected: inline per-provider;
  param-list helper (no shared param type Anthropic MessageParam vs Google Content).
- DA2: keep full `ResolvedSidecar` (drop `?.text`), thread `wireSidecar.markerId`
  into `endTurnFreezeSidecar`, remove throwaway mint (`:563`/`:327`). Mirrors
  `finalizeSidecarFreeze`.
- DA3: embedder is metadata-agnostic; `[Metadata:]` already content tail → suffix
  lands after it; no special-casing for metadata-less messages.
- DA4: NO Anthropic `cache_control` change — block-level breakpoint, intra-block
  move is cache-neutral (inference); live sidecar already uncached; frozen replay
  byte-stable. Live validation = Open Q1.
- DA5: NO Google cache change — `CacheSignature` hashes raw content (sidecar-
  independent). Live reuse validation = Open Q2.
- DA6: suffix into single text block/part; image blocks/parts untouched.
- DA7: pre-retrieval stays `null` (merged upstream); confirm-only, no behavior change.
- DA8: both providers in ONE ticket, no feature flag, direct replacement (mirrors
  D8); revert point `feat/deepseek @ d290c27c`.
- Open questions (3): live Anthropic cache validation; live Google CachedContent
  reuse validation; keep+rename vs remove `SessionIndexVolatilePlacement`.
- Self-review: passed (current state matches research; rejected alternatives
  named per decision; open questions human-answerable; no runnable code).

### Structure outcomes (complete)
- `structure.md` written: 4 thin vertical slices, signatures-only (no bodies,
  no placeholders).
- Slice 1: add `SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(...)
  : List<SidecarMarker>` (DA1) + first-ever direct unit test
  (`SessionIndexVolatilePlacementTest`); old `sidecarTextsBeforeSessionIndex`
  retained so the module compiles.
- Slice 2: Anthropic suffix-embed — keep full `ResolvedSidecar` (drop `?.text`
  `:230`), `prependIndexSidecars`→shared-embedder suffix helper,
  `freezeContextSidecarIfPresent(contextSidecars: ResolvedSidecar?, ...)` thread
  `markerId`, remove throwaway mint (`:563`). Rewrite
  `AnthropicInferenceServiceImplCachePrefixTest` to suffix shape. No
  `cache_control` change (DA4).
- Slice 3: Google suffix-embed — keep full `ResolvedSidecar` (drop `?.text`
  `:155`), `sidecarPrependTextForIndex`→`sidecarMarkersForIndex`,
  `GoogleAgentLoopContentMapper.mapMessage` `prependedText: String?`→
  `sidecarMarkers: List<SidecarMarker>` (embed suffix), thread `markerId`, remove
  throwaway mint (`:327`); DELETE old `sidecarTextsBeforeSessionIndex` (completes
  rename). NEW `GoogleInferenceServiceImplSidecarWireTest` (closes F12 gap). No
  `CachedContent` change (DA5).
- Slice 4: validation — CI-aligned Gradle checks + release-build emulator cache
  validation for Anthropic `cache_control` (Open Q1) and Google `CachedContent`
  reuse (Open Q2). No production code.
- Dependency: Slice 1 (sibling) before Slices 2/3 (providers consume it); Slice 2
  before Slice 3 (Google migrates last and deletes the old helper). Parallel
  split possible if the shared-file deletion is reserved to the last-merged
  provider slice.
- Do-not-touch: `LlamaInferenceServiceImpl`, shared embedder/marker/snapshot/
  append-only primitives, `OpenAiCompatibleVolatilePlacement`/
  `BaseOpenAiSdkInferenceService` (already migrated), `AnthropicPromptCacheSupport`
  (DA4), `GooglePromptCacheSession`/`CacheSignature` (DA5), `ProviderMessageDigest`,
  `AppendOnlyMessageLog`, Room entities/DAOs.
- Self-review: passed (every slice vertically integrated + has a runnable
  checkpoint; file map aligns with DA1–DA8; no horizontal-layer-only phase; no
  reopened decisions; signatures only).

### Plan outcomes (complete)
- `spec.md` written: 4 slices, 21 ordered tasks (T1.1–T1.3, T2.1–T2.6,
  T3.1–T3.9, T4.1–T4.3). Sections: feature summary, vertical-slice table
  (scenarios-covered column), Create/Modify/Do-not-touch file map, signatures-only
  API contracts, data model = NONE (INV-5/§3.4), ordered tasks per slice (each
  referencing scenario IDs), SEQUENTIAL execution strategy (Slice 2 Anthropic →
  Slice 3 Google; old-helper deletion in Slice 3), drift/escalation + scope-creep +
  test-weakening guards, self-review. No runnable code.
- `test_spec.md` written: 24 scenarios across S-SIP (7), S-ANTH (8), S-GOOG (6),
  S-VAL (3). Framework JUnit5/MockK/`runTest`; verified capture patterns
  (Anthropic `MessageCreateParams` capture via `answers`; Google
  `slot<List<Content>>()` via `coEvery generateContentStream`); literal UUID
  constants + `UUID_REGEX` continuity convention; coverage map + self-review.
- Mutation-defense scenarios: S-SIP-06 (no re-mint in selection), S-ANTH-08 and
  S-GOOG-06 (reverted-to-prepend mutant fails suffix-after-content;
  throwaway-marker-at-freeze mutant — today's `:563`/`:327` — fails cross-turn
  uuid-equality continuity).
- Signatures verified against the working tree: `sidecarTextsBeforeSessionIndex`
  `SessionIndexVolatilePlacement.kt:33-55`; Anthropic resolve `:230`,
  `prependIndexSidecars` `:232-247`, freeze call `:484-488`,
  `freezeContextSidecarIfPresent`/throwaway `:555-567`/`:563`, cache `:207-221`/
  `:256-258`, image branch `:296-326`; Google resolve `:155`,
  `sidecarPrependTextForIndex` `:157-167`, `mapAtIndex` `:169-180`, freeze/throwaway
  `:324-330`/`:327`; mapper `mapMessage(..., prependedText: String? = null)`
  `:33-39`, prefix `:59-64`, user parts `:84-89`; OpenAI freeze reference
  `finalizeSidecarFreeze` `BaseOpenAiSdkInferenceService.kt:454-470`.
- Self-review: passed (DA1–DA8 each mapped to tasks or explicit no-change;
  slice order + sequential strategy reflected; every requirement has a task and a
  scenario; signatures match spec/test_spec/repo; mutation-defense fails the named
  lazy impls; no placeholders).
- Next: TDD Red (after human plan approval).

### Implementation outcomes (complete, 2026-06-19)
- Slice 1: `sidecarMarkersForSessionIndex` implemented (frozen → pre-retrieval →
  live selection; preserves input `markerId`).
- Slice 2: Anthropic migrated to suffix-embed via `embedIndexSidecars` +
  `SidecarUserContentEmbedder.embed`; full `ResolvedSidecar` threaded through
  resolve/freeze (throwaway mint removed).
- Slice 3: Google migrated via `sidecarMarkersForIndex` + mapper
  `sidecarMarkers` param; freeze threads `markerId`; deleted
  `sidecarTextsBeforeSessionIndex`.
- Ticket scenario tests: **21/21 pass**
  (`SessionIndexVolatilePlacementTest` 7/7,
  `AnthropicInferenceServiceImplCachePrefixTest` 8/8,
  `GoogleInferenceServiceImplSidecarWireTest` 6/6).
- Commands:
  - `./gradlew :feature:inference:testDebugUnitTest --tests "*SessionIndexVolatilePlacementTest*"` — green
  - `./gradlew :feature:inference:testDebugUnitTest --tests "*AnthropicInferenceServiceImplCachePrefixTest*"` — green
  - `./gradlew :feature:inference:testDebugUnitTest --tests "*GoogleInferenceServiceImplSidecarWireTest*"` — green
  - `./gradlew :feature:inference:assembleDebug` — green
  - `./gradlew ktlintCheck` — green
  - `./gradlew detekt` — green
- Full module unit tests: **1049/1049 pass** after rewriting legacy
  `PromptCachingTest` SC-AG-01/02/05 to suffix assertions.
- Next: Final Review.

### Final Review outcomes (pass, 2026-06-19)
- Admissible findings: **none**
- Full module: `./gradlew :feature:inference:testDebugUnitTest` — green (1049 tests)
- `./gradlew :feature:inference:ktlintCheck` + `:feature:inference:detekt` — green (0 issues)
- Residual: S-VAL-02/03 manual emulator cache validation outstanding (Slice 4)
