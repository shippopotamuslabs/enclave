# Status: sidecar-dedupe-model-wire

## Phase Checklist
- [x] Research
- [x] Design — approval: yes (2026-06-17)
- [x] Structure — approval: yes (2026-06-17)
- [x] Plan — approval: yes (2026-06-17)
- [x] TDD Red
- [x] Red Review — result: **pass** (loop 3, 2026-06-17)
- [x] Implementation
- [x] Final Review — result: **pass** (loop 1, 2026-06-18)

## Current Gate
none

## Next Phase
complete — ready for merge/handoff

## Loop Counts
- Red Review: 3/3
- Final Review: 1/3

## Implementation (2026-06-18)
- **Test command:** `./gradlew :feature:inference:testDebugUnitTest --tests "*SC_*"`
- **Result:** 24/24 SC scenario tests pass
- **Full module:** `./gradlew :feature:inference:testDebugUnitTest` — 973/973 pass (after XaiPromptCacheRoutingTest assertion fix)
- **Static analysis:** `./gradlew detekt` — pass
- **Production changes:**
  - `RustLoopInferenceService.kt` — model-only sidecars, TTL preamble, `PROMPT_CACHE` DEBUG log, `{}` blob skip
  - `ProviderAppendOnlyContext.kt` — `resolveContextSidecarsForWire` dedupe gate, `lastShippedSidecarText`
  - `BaseOpenAiSdkInferenceService.kt` — wire dedupe, conditional freeze, volatile matcher cleanup
  - `AnthropicInferenceServiceImpl.kt`, `GoogleInferenceServiceImpl.kt` — index-interleaved frozen replay; live sidecar at `lastUserIndex`
  - `PromptCachePrefixDiagnostics.kt` — `wireText()` for multi-part system/user content
- **Test corrections (Red-phase fixture gaps):**
  - `SC-AG-03` padding 300→1200 (stable prefix must exceed 1024-token Google cache threshold for delta wire layout)
  - `AnthropicInferenceServiceImplCachePrefixTest` — turn-2 U1 expects frozen sidecar replay per OQ-1
  - `XaiPromptCacheRoutingTest` — compare assistant placeholder via `content.asText()` not raw `Content`

## Notes
- Research complete: `research.md` (18 questions, 18 findings).
- Design complete: `design.md` — human approved 2026-06-17.
- Structure complete: `structure.md` — 4 vertical slices; approved 2026-06-17.
- Plan complete: `spec.md` (22 tasks, 4 slices), `test_spec.md` (23 scenarios) — approved 2026-06-17.
- **Design approvals (2026-06-17):**
  - OQ-1: **C** — index-interleaved frozen replay for Anthropic/Google via `SessionIndexVolatilePlacement` (not latest-user bundle; revised after cache review).
  - OQ-2: Preamble TTL line approved as proposed ("Tool-output references expire after 2 user turns." or equivalent clear wording).
  - OQ-3: `cacheHints` DEBUG log with `PROMPT_CACHE` tag when non-empty.
- Rust TTL re-verified: `expire_after_turn` at turn start in `engine/mod.rs`; `read_context_reference` returns not-found after purge (D4).
- **Handoff:** Unrelated `core/domain` telemetry and `feature/chat` mapper changes in working tree should be split into a separate PR from this ticket.
