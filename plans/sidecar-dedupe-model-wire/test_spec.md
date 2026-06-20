# Test Spec: sidecar-dedupe-model-wire

**Ticket ID:** `sidecar-dedupe-model-wire`  
**Framework:** JUnit 5 + MockK + `runTest` (per `contracts/UNIT_TESTING.md`)  
**Module:** `:feature:inference`

---

## 1. Run Commands

| Scope | Command |
|-------|---------|
| Slice 1 format | `./gradlew :feature:inference:testDebugUnitTest --tests "*RustLoopInferenceServiceTest*"` |
| Slice 2 dedupe | `./gradlew :feature:inference:testDebugUnitTest --tests "*ProviderAppendOnlyContextTest*" --tests "*AppendOnlyOpenAiSidecarPlacementTest*" --tests "*XaiPromptCacheToolAndSessionStabilityTest*"` |
| Slice 3 A/G replay | `./gradlew :feature:inference:testDebugUnitTest --tests "*PromptCachingTest*"` |
| Full acceptance | `./gradlew :feature:inference:testDebugUnitTest` |
| Static analysis | `./gradlew detekt` |

---

## 2. Scenario Index

| Scenario ID | Type | Slice | Primary test home |
|-------------|------|-------|-------------------|
| `SC-FMT-01` | Happy Path | 1 | `RustLoopInferenceServiceTest` |
| `SC-FMT-02` | Happy Path | 1 | `RustLoopInferenceServiceTest` |
| `SC-FMT-03` | Happy Path | 1 | `RustLoopInferenceServiceTest` |
| `SC-FMT-04` | Happy Path | 1 | `RustLoopInferenceServiceTest` |
| `SC-FMT-05` | Edge Case | 1 | `RustLoopInferenceServiceTest` |
| `SC-FMT-MUT` | Mutation-Defense | 1, 4 | `RustLoopInferenceServiceTest`, `XaiPromptCacheLiveContextTest` |
| `SC-DEDUPE-RESOLVE-01` | Happy Path | 2 | `ProviderAppendOnlyContextTest` |
| `SC-DEDUPE-RESOLVE-02` | Happy Path | 2 | `ProviderAppendOnlyContextTest` |
| `SC-DEDUPE-RESOLVE-03` | Happy Path | 2 | `ProviderAppendOnlyContextTest` |
| `SC-DEDUPE-01` | Happy Path | 2, 3 | `AppendOnlyOpenAiSidecarPlacementTest`, `PromptCachingTest` |
| `SC-DEDUPE-02` | Happy Path | 2 | `AppendOnlyOpenAiSidecarPlacementTest` |
| `SC-DEDUPE-03` | Edge Case | 2 | `ProviderAppendOnlyContextTest`, `AppendOnlyOpenAiSidecarPlacementTest` |
| `SC-DEDUPE-04` | Happy Path | 2 | `ProviderAppendOnlyContextTest`, `AppendOnlyOpenAiSidecarPlacementTest`, `XaiPromptCacheToolAndSessionStabilityTest` |
| `SC-DEDUPE-MUT` | Mutation-Defense | 2, 3 | `AppendOnlyOpenAiSidecarPlacementTest`, `PromptCachingTest` |
| `SC-AG-01` | Happy Path | 3 | `PromptCachingTest` |
| `SC-AG-02` | Happy Path | 3 | `PromptCachingTest` |
| `SC-AG-03` | Happy Path | 3 | `PromptCachingTest` |
| `SC-AG-04` | Happy Path | 3 | `PromptCachingTest` |
| `SC-AG-05` | Happy Path | 3 | `SessionIndexVolatilePlacementTest` (new) or `PromptCachingTest` |
| `SC-VOL-01` | Happy Path | 4 | `XaiPromptCacheLiveContextTest` |
| `SC-VOL-02` | Edge Case | 4 | `XaiPromptCacheLiveContextTest` |
| `SC-REG-01` | Regression | 4 | `PromptCachingTest` |
| `SC-REG-02` | Regression | 4 | `AppendOnlyOpenAiSidecarPlacementTest` |

---

## 3. Slice 1 — Format Scenarios

### Scenario ID: `SC-FMT-01`
**Sidecar forwards model-only content with TTL preamble**

```gherkin
Feature: Model-only sidecar composition

  Scenario: SC-FMT-01 completeModelTurn forwards cleaned sidecar
    Given RustLoopInferenceService with CapturingAgentLoopPort
    And AgentLoopEvent.TurnStarted index=1
    And completeModelTurn inputs:
      | field               | value |
      | contextReferences   | FILE_OUTLINE id=file-outline-src/Main.kt title="outline:src/Main.kt @turn:1" byteLen=10 preview="fun main" |
      | fileContextIndexJson| {"root_ref":"file-context-root"} |
    When sendPrompt("sidecars") completes
    Then inner.lastContextSidecars is non-null
    And lastContextSidecars contains "Reference policy: IDs are exact lookups only—never browse. For conversation history use search_chat_history."
    And lastContextSidecars contains "Tool-output references expire after 2 user turns."
    And lastContextSidecars contains "[FILE_OUTLINE] file-outline-src/Main.kt (10b): outline:src/Main.kt @turn:1"
    And lastContextSidecars contains """preview: "fun main""""
    And lastContextSidecars contains "read_context_reference(\"file-outline-src/Main.kt\")"
    And lastContextSidecars contains "File context index JSON:"
    And lastContextSidecars does not contain "Context management sidecars:"
    And lastContextSidecars does not contain "Cache hints:"
    And lastContextSidecars does not contain "|fresh|"
    And lastContextSidecars does not contain "|stale|"
    And lastContextSidecars does not contain "|turn:1|"
```

**Test anchor:** Update `RustLoopInferenceServiceTest.completeModelTurn_forwardsContextSidecarsToProviderPrompt`.

---

### Scenario ID: `SC-FMT-02`
**Reference lines omit stale/fresh and turn bracket segment**

```gherkin
Feature: Simplified reference line format

  Scenario: SC-FMT-02 tool output reference at turn 3
    Given RustLoopInferenceService with CapturingAgentLoopPort
    And AgentLoopEvent.TurnStarted index=3
    And completeModelTurn contextReferences:
      | kind        | id                 | title                      | byteLen | preview    |
      | TOOL_OUTPUT | tool-output-call-1 | tool_output:call-1 @turn:2 | 200     | BEGIN_RAW  |
    When sendPrompt("refs") completes
    Then inner.lastContextSidecars contains "[TOOL_OUTPUT] tool-output-call-1 (200b): tool_output:call-1 @turn:2"
    And lastContextSidecars does not contain "[TOOL_OUTPUT|turn:2|stale]"
    And lastContextSidecars does not contain "[TOOL_OUTPUT|turn:2|fresh]"
    And lastContextSidecars does not contain "|stale|"
    And lastContextSidecars does not contain "|fresh|"
```

**Test anchor:** Repurpose `RustLoopInferenceServiceTest.completeModelTurn_marksStaleContextReferencesInSidecars`.

---

### Scenario ID: `SC-FMT-03`
**cacheHints-only input yields null sidecar**

```gherkin
Feature: cacheHints excluded from wire and emptiness

  Scenario: SC-FMT-03 cacheHints do not force sidecar hop
    Given RustLoopInferenceService with CapturingAgentLoopPort
    And completeModelTurn inputs:
      | contextReferences    | (empty) |
      | stateLedgerJson      | null    |
      | toolContextIndexJson | null    |
      | fileContextIndexJson | null    |
      | cacheHints           | ["breakpoint:system"] |
    And device network is online with no prior offline in session
    When sendPrompt("hints only") completes
    Then inner.lastContextSidecars is null
    And inner.lastContextSidecars does not contain "Cache hints:"
```

**Test anchor:** New test in `RustLoopInferenceServiceTest`.

---

### Scenario ID: `SC-FMT-04`
**Non-empty cacheHints logged at DEBUG with PROMPT_CACHE tag**

```gherkin
Feature: Host-only cacheHints logging

  Scenario: SC-FMT-04 DEBUG log on cacheHints
    Given RustLoopInferenceService with mock LoggingPort
    And completeModelTurn cacheHints=["breakpoint:system","reuse:prefix"]
    When sendPrompt("log hints") completes
    Then LoggingPort received DEBUG log with tag "PROMPT_CACHE"
    And log message contains "breakpoint:system"
    And log message contains "reuse:prefix"
    And inner.lastContextSidecars does not contain "Cache hints:"
```

**Test anchor:** New test in `RustLoopInferenceServiceTest` with `mockk<LoggingPort>(relaxed = true)`.

---

### Scenario ID: `SC-FMT-05`
**Preamble-only content returns null sidecar**

```gherkin
Feature: Preamble alone is not worth a hop

  Scenario: SC-FMT-05 empty payload except static preamble constant
    Given buildContextSidecars would emit only CONTEXT_REFERENCE_POLICY_PREAMBLE
    And contextReferences is empty
    And all JSON blobs are null or empty after compaction
    And networkStatus is blank
    When buildContextSidecars completes
    Then result is null
```

**Test anchor:** New unit test in `RustLoopInferenceServiceTest` (via package-visible test hook or indirect via `completeModelTurn` with empty inputs).

---

### Scenario ID: `SC-FMT-MUT`
**Mutation-defense: forbidden sidecar substrings fail build**

```gherkin
Feature: Wire format regression guard

  Scenario: SC-FMT-MUT sidecar string forbidden patterns
    Given any production or test duplicate buildContextSidecars output SIDE
    When SIDE is non-null
    Then SIDE does not contain "Context management sidecars:"
    And SIDE does not contain "Cache hints:"
    And SIDE does not match regex "\|(fresh|stale)\|"
    And SIDE does not match regex "\[(TOOL_OUTPUT|FILE_OUTLINE)\|turn:"
```

**Test anchor:** `RustLoopInferenceServiceTest` assertion helper; `XaiPromptCacheLiveContextTest.buildContextSidecars` output checked in Slice 4 sweep.

---

## 4. Slice 2 — Dedupe Scenarios

### Scenario ID: `SC-DEDUPE-RESOLVE-01`
**First ship returns built string and records lastShippedSidecarText**

```gherkin
Feature: resolveContextSidecarsForWire gate

  Scenario: SC-DEDUPE-RESOLVE-01 first ship
    Given ProviderAppendOnlyContext with lastShippedSidecarText unset
    And built equals "State ledger JSON: {\"turn\":1}"
    When resolveContextSidecarsForWire(built) is called
    Then result equals "State ledger JSON: {\"turn\":1}"
    And a second resolveContextSidecarsForWire("State ledger JSON: {\"turn\":1}") returns null
```

**Test anchor:** New test in `ProviderAppendOnlyContextTest`.

---

### Scenario ID: `SC-DEDUPE-RESOLVE-02`
**Changed string ships again**

```gherkin
Feature: resolveContextSidecarsForWire gate

  Scenario: SC-DEDUPE-RESOLVE-02 semantic change
    Given ProviderAppendOnlyContext
    And resolveContextSidecarsForWire("State ledger JSON: {\"turn\":1}") returned non-null
    When resolveContextSidecarsForWire("State ledger JSON: {\"turn\":2}") is called
    Then result equals "State ledger JSON: {\"turn\":2}"
```

**Test anchor:** New test in `ProviderAppendOnlyContextTest`.

---

### Scenario ID: `SC-DEDUPE-RESOLVE-03`
**clearLogState and invalidate reset dedupe memory**

```gherkin
Feature: Dedupe reset on session clear

  Scenario: SC-DEDUPE-RESOLVE-03 post-clear re-ship
    Given ProviderAppendOnlyContext with last shipped "State ledger JSON: {\"v\":1}"
    When clearLogState(reason="digest_mismatch") is invoked
    And resolveContextSidecarsForWire("State ledger JSON: {\"v\":1}") is called
    Then result equals "State ledger JSON: {\"v\":1}"
```

**Test anchor:** New test in `ProviderAppendOnlyContextTest`; mirror for `invalidate()`.

---

### Scenario ID: `SC-DEDUPE-01`
**Three identical sidecar hops → one freeze, frozen replay count 1 (OpenAI)**

```gherkin
Feature: Sidecar dedupe on OpenAI wire

  Scenario: SC-DEDUPE-01 three hops identical contextSidecars
    Given BaseOpenAiSdkInferenceService test double provider OPENAI
    And identicalSidecar equals "State ledger JSON: {\"turn\":1}"
    When completeSingleTurn hop 1 with contextSidecars=identicalSidecar and sessionMessages=[USER "Hello"]
    And completeSingleTurn hop 2 with contextSidecars=identicalSidecar and sessionMessages=[USER "Hello", ASSISTANT "Hi"]
    And completeSingleTurn hop 3 with contextSidecars=identicalSidecar and sessionMessages=[USER "Hello", ASSISTANT "Hi", USER "Follow up"]
    Then appendOnly.frozenSidecarsForWireReplay().size equals 1
    And hop 2 wire messages contain exactly 1 SYSTEM row with text identicalSidecar
    And hop 3 wire messages contain exactly 1 SYSTEM row with text identicalSidecar
    And hop 3 wire does not add a second SYSTEM row with text identicalSidecar at tail
```

**Test anchor:** New test in `AppendOnlyOpenAiSidecarPlacementTest`; A/G variant in `PromptCachingTest` (Slice 3).

---

### Scenario ID: `SC-DEDUPE-02`
**v1 then v2 then v2 → two frozen snapshots**

```gherkin
Feature: Semantic sidecar changes still freeze

  Scenario: SC-DEDUPE-02 ledger version progression
    Given BaseOpenAiSdkInferenceService test double
    When completeSingleTurn hop 1 contextSidecars="State ledger JSON: {\"v\":1}"
    And completeSingleTurn hop 2 contextSidecars="State ledger JSON: {\"v\":2}"
    And completeSingleTurn hop 3 contextSidecars="State ledger JSON: {\"v\":2}"
    Then appendOnly.frozenSidecarsForWireReplay().size equals 2
    And frozenSidecarsForWireReplay()[0].text equals "State ledger JSON: {\"v\":1}"
    And frozenSidecarsForWireReplay()[1].text equals "State ledger JSON: {\"v\":2}"
```

**Test anchor:** New test in `AppendOnlyOpenAiSidecarPlacementTest`.

---

### Scenario ID: `SC-DEDUPE-03`
**invalidate resets lastShippedSidecarText; next hop ships even if string matches pre-clear frozen**

```gherkin
Feature: Dedupe after session invalidation

  Scenario: SC-DEDUPE-03 post-invalidate re-ship
    Given ProviderAppendOnlyContext with last shipped "State ledger JSON: {\"k\":1}"
    And one frozen snapshot with text "State ledger JSON: {\"k\":1}"
    When invalidate() is called
    Then frozenSidecarsForWireReplay() is empty
    And resolveContextSidecarsForWire("State ledger JSON: {\"k\":1}") returns "State ledger JSON: {\"k\":1}"
```

**Test anchor:** `ProviderAppendOnlyContextTest` + wire integration in `AppendOnlyOpenAiSidecarPlacementTest`.

---

### Scenario ID: `SC-DEDUPE-04`
**Unchanged sidecar across markTurnBoundaryForReanchor still dedupes**

```gherkin
Feature: Dedupe survives user-turn reanchor

  Scenario: SC-DEDUPE-04 reanchor boundary with identical sidecar
    Given BaseOpenAiSdkInferenceService with active appendOnly context
    And hop 1 shipped contextSidecars="State ledger JSON: {\"turn\":1}" and froze
    When markTurnBoundaryForReanchor() is called
    And completeSingleTurn hop 2 with identical contextSidecars and new USER message "Next question"
    Then appendOnly.frozenSidecarsForWireReplay().size equals 1
    And resolveContextSidecarsForWire("State ledger JSON: {\"turn\":1}") returns null
    And hop 2 wire still replays frozen sidecar "State ledger JSON: {\"turn\":1}"
```

**Test anchor:** `ProviderAppendOnlyContextTest`, `AppendOnlyOpenAiSidecarPlacementTest`, `XaiPromptCacheToolAndSessionStabilityTest.PC_XAI_06_resetAppendOnlyForNewPrompt_preserves_same_chat_frozen_sidecars` (adjust freeze count when sidecar identical).

---

### Scenario ID: `SC-DEDUPE-MUT`
**Mutation-defense: unconditional freeze fails identical-hop test**

```gherkin
Feature: Dedupe gate must gate freeze

  Scenario: SC-DEDUPE-MUT three identical hops must not triple-freeze
    Given BaseOpenAiSdkInferenceService wired with resolveContextSidecarsForWire
    And sidecar S equals "State ledger JSON: {\"frozen\":true}"
    When three completeSingleTurn calls each pass contextSidecars=S with growing sessionMessages
    Then appendOnly.frozenSidecarsForWireReplay().size equals 1
    And appendOnly.frozenSidecarsForWireReplay().size does not equal 3
```

**Test anchor:** Same test body as `SC-DEDUPE-01`; fails if dedupe gate removed or freeze not gated on non-null resolve result.

---

## 5. Slice 3 — Anthropic/Google Replay Scenarios

### Scenario ID: `SC-AG-01`
**Anthropic hop 1 ships sidecar at tail session index**

```gherkin
Feature: Anthropic index-interleaved sidecar replay

  Scenario: SC-AG-01 first hop prepends at tail index
    Given AnthropicInferenceServiceImpl test double
    And sessionMessages=[USER index=0 content="Hello"]
    And contextSidecars="State ledger JSON: {\"key\":\"value\"}"
    When completeSingleTurn completes
    Then captured MessageCreateParams user message at index 0 text starts with "State ledger JSON: {\"key\":\"value\"}"
    And user message text ends with "Hello"
    And appendOnly.frozenSidecarsForWireReplay().size equals 1
    And frozenSidecarsForWireReplay()[0].mappedInsertIndex equals 0
```

**Test anchor:** Extend `PromptCachingTest.test_PC_02_AnthropicAttachesEphemeralCacheControl`.

---

### Scenario ID: `SC-AG-02`
**Anthropic hops 2–3 identical sidecar: frozen stays on index 0, no live on latest only**

```gherkin
Feature: Anthropic dedupe with index replay

  Scenario: SC-AG-02 three-hop identical sidecar multi-turn
    Given AnthropicInferenceServiceImpl with appendOnly context from hop 1
    And sidecar S equals "Volatile sidecar content"
    And hop 1 froze S at session index 0
    When hop 2 completeSingleTurn with contextSidecars=S and sessionMessages=[USER "Hello", ASSISTANT "Hi", USER "Explain the plan."]
    And hop 3 completeSingleTurn with contextSidecars=S and same growing history plus ASSISTANT "Done"
    Then appendOnly.frozenSidecarsForWireReplay().size equals 1
    And hop 2 first USER row text starts with S
    And hop 2 last USER row text equals "Explain the plan." with no S prefix
    And hop 3 first USER row text starts with S
    And hop 3 last USER row text does not start with S
```

**Test anchor:** New or extended test in `PromptCachingTest` (revises prior latest-user-only expectation from `test_PC_02` multi-turn variant).

---

### Scenario ID: `SC-AG-03`
**Google hop 1 ships sidecar prepended at tail session index**

```gherkin
Feature: Google index-interleaved sidecar replay

  Scenario: SC-AG-03 Google first hop sidecar at freeze index
    Given GoogleInferenceServiceImpl test double
    And sessionMessages with USER "User message content 10" at tail index
    And contextSidecars="Sidecar key value json"
    When completeSingleTurn completes
    Then generateContent request content at tail USER part text contains "Sidecar key value json"
    And tail USER part text contains "User message content 10"
    And frozenSidecarsForWireReplay().size equals 1
```

**Test anchor:** Extend `PromptCachingTest.test_PC_04_GoogleGenAiCreatesCachedContentOnExceededThreshold`.

---

### Scenario ID: `SC-AG-04`
**Google multi-hop: Sidecar 2 frozen at its index; prior frozen not migrated to latest user**

```gherkin
Feature: Google frozen sidecar index stability

  Scenario: SC-AG-04 two distinct sidecars across turns
    Given GoogleInferenceServiceImpl test double
    When hop 1 contextSidecars="Sidecar 1" with USER messages u1..u3
    And hop 2 contextSidecars="Sidecar 2" with USER messages u1..u4
    Then frozenSidecarsForWireReplay().size equals 2
    And u3 row text in hop 2 request contains "Sidecar 1"
    And u3 row text in hop 2 request does not contain "Sidecar 2"
    And u4 row text in hop 2 request contains "Sidecar 2"
```

**Test anchor:** Extend `PromptCachingTest.test_PC_05_GoogleGenAiReusesCachedContentAndTransmitsOnlyDeltas`.

---

### Scenario ID: `SC-AG-05`
**sidecarTextsBeforeSessionIndex unit contract**

```gherkin
Feature: SessionIndexVolatilePlacement helper

  Scenario: SC-AG-05 frozen at index 0 not prepended to index 2
    Given frozenSidecars=[FrozenSidecarSnapshot(text="FROZEN_A", mappedInsertIndex=0)]
    And tailInsertIndex=2
    And currentSidecarText=null
    When sidecarTextsBeforeSessionIndex(sessionIndex=0, ...) is called
    Then result equals ["FROZEN_A"]
    When sidecarTextsBeforeSessionIndex(sessionIndex=2, ...) is called
    Then result is empty
    When sidecarTextsBeforeSessionIndex(sessionIndex=2, currentSidecarText="LIVE_B", ...) is called
    Then result equals ["LIVE_B"]
```

**Test anchor:** New `SessionIndexVolatilePlacementTest.kt` in `feature/inference/src/test/kotlin/.../appendonly/`.

---

## 6. Slice 4 — Volatile Detection and Regression

### Scenario ID: `SC-VOL-01`
**State ledger JSON still classified volatile; removed prefixes not**

```gherkin
Feature: Volatile sidecar detection alignment

  Scenario: SC-VOL-01 prefix set after format cleanup
    Given ChatCompletionMessageParam SYSTEM text="State ledger JSON: {\"k\":1}"
    Then isVolatileOrEngineSystemMessage() is true
    Given ChatCompletionMessageParam SYSTEM text="Context management sidecars:"
    Then isVolatileOrEngineSystemMessage() is false
    Given ChatCompletionMessageParam SYSTEM text="Cache hints: breakpoint:system"
    Then isVolatileOrEngineSystemMessage() is false
```

**Test anchor:** New tests in `XaiPromptCacheLiveContextTest` using production-equivalent `isVolatileSidecar()` mirror.

---

### Scenario ID: `SC-VOL-02`
**Pre-retrieval marker remains volatile**

```gherkin
Feature: Pre-retrieval volatile detection unchanged

  Scenario: SC-VOL-02 pre-retrieval sidecar
    Given SYSTEM text starting with PreRetrievalContextSidecar marker "Pre-retrieval context:"
    Then isVolatileOrEngineSystemMessage() is true
    And isStableSessionSystemMessage() is false
```

**Test anchor:** `XaiPromptCacheLiveContextTest`.

---

### Scenario ID: `SC-REG-01`
**PC-01 OpenAI tail placement with dedupe on first ship**

```gherkin
Feature: Prompt caching regression PC-01

  Scenario: SC-REG-01 OpenAI separates stable system and sidecar
    Given test_PC_01_OpenAiSeparatesSystemPromptAndSidecars inputs unchanged
    When completeSingleTurn with contextSidecars="State ledger JSON: {\"key\":\"value\"}"
    Then messages().size equals 3
    And messages[0] SYSTEM text equals "You are a helpful assistant."
    And messages[1] SYSTEM text equals "State ledger JSON: {\"key\":\"value\"}"
    And messages[2] USER text equals "Hello"
    And appendOnly.frozenSidecarsForWireReplay().size equals 1
```

**Test anchor:** `PromptCachingTest.test_PC_01_OpenAiSeparatesSystemPromptAndSidecars`.

---

### Scenario ID: `SC-REG-02`
**AOC_22 differing sidecars still produce one freeze per distinct string**

```gherkin
Feature: Tool loop freeze when sidecar changes each hop

  Scenario: SC-REG-02 AOC_22 semantic progression preserved
    Given AppendOnlyOpenAiSidecarPlacementTest AOC_22 three-hop tool loop
    With contextSidecars turn 1 "State ledger JSON: {\"turn\":1}"
    And contextSidecars turn 2 "State ledger JSON: {\"turn\":2}"
    And contextSidecars turn 3 "State ledger JSON: {\"turn\":3}"
    When three completeSingleTurn calls complete
    Then appendOnly.clearCount equals 0
    And appendOnly.frozenSidecarsForWireReplay().size equals 3
```

**Test anchor:** Existing `AppendOnlyOpenAiSidecarPlacementTest.AOC_22_tool_loop_does_not_increment_append_only_clear_count` — unchanged when sidecar strings differ per hop.

---

## 7. Scenario → Task Cross-Reference

| Scenario | Implementation tasks |
|----------|---------------------|
| SC-FMT-01 | TASK-S1-01, TASK-S1-02, TASK-S1-04, TASK-S1-07 |
| SC-FMT-02 | TASK-S1-02, TASK-S1-03, TASK-S1-07 |
| SC-FMT-03 | TASK-S1-04, TASK-S1-05, TASK-S1-06, TASK-S1-07 |
| SC-FMT-04 | TASK-S1-05, TASK-S1-07 |
| SC-FMT-05 | TASK-S1-06, TASK-S1-07 |
| SC-FMT-MUT | TASK-S1-02, TASK-S1-03, TASK-S1-04, TASK-S1-07, TASK-S4-02 |
| SC-DEDUPE-RESOLVE-01 | TASK-S2-01, TASK-S2-04 |
| SC-DEDUPE-RESOLVE-02 | TASK-S2-01, TASK-S2-04 |
| SC-DEDUPE-RESOLVE-03 | TASK-S2-02, TASK-S2-04 |
| SC-DEDUPE-01 | TASK-S2-03, TASK-S2-05, TASK-S3-01, TASK-S3-04 |
| SC-DEDUPE-02 | TASK-S2-03, TASK-S2-05 |
| SC-DEDUPE-03 | TASK-S2-02, TASK-S2-04, TASK-S2-05 |
| SC-DEDUPE-04 | TASK-S2-03, TASK-S2-04, TASK-S2-05, TASK-S2-06 |
| SC-DEDUPE-MUT | TASK-S2-03, TASK-S3-01 |
| SC-AG-01 | TASK-S3-01, TASK-S3-04 |
| SC-AG-02 | TASK-S3-01, TASK-S3-04 |
| SC-AG-03 | TASK-S3-02, TASK-S3-04 |
| SC-AG-04 | TASK-S3-02, TASK-S3-04 |
| SC-AG-05 | TASK-S3-03 |
| SC-VOL-01 | TASK-S4-01, TASK-S4-02 |
| SC-VOL-02 | TASK-S4-01, TASK-S4-02 |
| SC-REG-01 | TASK-S4-03 |
| SC-REG-02 | TASK-S4-03 |

---

## 8. Approval Gate

Test spec complete — awaiting human approval before TDD Red.
