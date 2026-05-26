# Test Spec: Append-Only Provider Context

**Ticket ID:** `FEAT-append-only-provider-context`  
**Framework:** JUnit 5 + MockK (+ Turbine only if Flow assertions added)  
**Module:** `:feature:inference`

---

## 1. Run Commands

| Scope | Command |
|---|---|
| Narrow (module) | `./gradlew :feature:inference:testDebugUnitTest` |
| Targeted append-only unit | `./gradlew :feature:inference:testDebugUnitTest --tests "com.browntowndev.enclave.feature.inference.appendonly.*"` |
| Targeted OpenAI placement | `./gradlew :feature:inference:testDebugUnitTest --tests "com.browntowndev.enclave.feature.inference.AppendOnlyOpenAiSidecarPlacementTest"` |
| Targeted xAI placement | `./gradlew :feature:inference:testDebugUnitTest --tests "com.browntowndev.enclave.feature.inference.XaiPromptCacheSidecarPlacementTest"` |
| Rust loop integration | `./gradlew :feature:inference:testDebugUnitTest --tests "com.browntowndev.enclave.feature.inference.RustLoopInferenceServiceTest"` |
| Prompt caching regression | `./gradlew :feature:inference:testDebugUnitTest --tests "com.browntowndev.enclave.feature.inference.PromptCachingTest"` |
| CI-aligned | `./gradlew testDebugUnitTest` |

---

## 2. Scenario Index

| Scenario ID | Type | Description |
|---|---|---|
| `AOC-01` | Happy Path | Stable prefix fingerprint unchanged across two `completeSingleTurn` calls with same system + tools. |
| `AOC-02` | Happy Path | OpenAI single-turn: sidecar system message immediately before sole user message (tail, not index 1 when history exists). |
| `AOC-03` | Happy Path | Tool loop: two `completeSingleTurn` calls preserve identical bytes for committed prefix messages. |
| `AOC-04` | Happy Path | Sidecar v1 frozen in provider list when sidecar v2 appended on second call. |
| `AOC-05` | Mutation-Defense | `syncMessages` digest detects in-place rewrite of synced prefix and clears log. |
| `AOC-06` | Happy Path | `allowToolCall = false` still includes full tools array with `toolChoice` NONE (OpenAI). |
| `AOC-07` | Regression | Anthropic prepends sidecars to latest user; cache breakpoints unchanged (`PC-02`, `PC-03`). |
| `AOC-08` | Regression | Google prepends sidecars on latest content only; cache lifecycle unchanged (`PC-04`–`PC-08`). |
| `AOC-09` | Happy Path | Llama merges sidecars into system prompt (no separate sidecar message). |
| `AOC-10` | Happy Path | OpenRouter uses OpenAI tail placement (inherits `BaseOpenAiSdkInferenceService`). |
| `AOC-11` | Edge Case | `closeSession` invalidates append-only context; next turn rebuilds prefix version. |
| `AOC-12` | Mutation-Defense | OpenAI mapping must not place sidecar at index `1` when `sessionMessages.size >= 3`. |
| `AOC-13` | Edge Case | Compaction (shorter normalized array) clears append-only log. |
| `AOC-14` | Edge Case | Null/blank `contextSidecars` skips volatile system message insertion. |
| `AOC-15` | Happy Path | xAI tool-continuation message order: sidecar before tool-result tail, not after system. |
| `AOC-16` | Regression | OpenRouter: prior stable USER (not tool-result) still receives `cache_control` after tail sidecar insertion (`anthropic/claude-3.5-sonnet`). |
| `AOC-17` | Happy Path | `toolsFingerprint` change between two `ensureStablePrefix` calls increments `StablePrefixSnapshot.version`. |
| `AOC-18` | Fix verification | Post-tool third `completeSingleTurn`: `messages[0..4]` prefix bytes match second call (xAI cache regression). |
| `AOC-19` | Fix verification | Frozen sidecar replay at original mapped index, not front-prepend to index 1. |
| `AOC-20` | Fix verification | Cap-at-2 does not swap index-1 sidecar text on turn 4+. |
| `PC-01` | Regression (updated) | OpenAI tail-volatile order replaces prior index-1 expectation. |
| `PC-XAI-01`–`04` | Cross-ref | See `plans/prompt-caching/spec.md` § Regression (2026-05-26). |

**Task → scenario mapping**

| Task | Scenarios |
|---|---|
| Task 1 (append-only core) | `AOC-01`, `AOC-04`, `AOC-05`, `AOC-13`, `AOC-14`, `AOC-17` |
| Task 2 (placement helper) | `AOC-02`, `AOC-15` |
| Task 3 (BaseOpenAi revert) | `AOC-02`, `AOC-03`, `AOC-12`, `AOC-15`, `AOC-16`, `PC-01` |
| Task 4 (session invalidation) | `AOC-11` |
| Task 5 (provider regression) | `AOC-07`, `AOC-08`, `AOC-09`, `AOC-10`, `AOC-16` |
| Task 6 (tool stability) | `AOC-06` |
| Task 7 (test migration) | All `AOC-*`, `PC-01` |

---

## 3. Happy Path Scenarios

### Scenario ID: `AOC-01`
**Stable prefix fingerprint unchanged across two turns**

```gherkin
Feature: Stable prefix freeze

  Scenario: AOC-01 stable prefix reuse
    Given a ProviderAppendOnlyContext with no prior state
    And systemPrompt is "You are a helpful assistant."
    And toolsFingerprint is "fp_tools_abc123"
    When ensureStablePrefix is called twice with the same inputs
    Then the first StablePrefixSnapshot.version equals 1
    And the second StablePrefixSnapshot.version equals 1
    And the second StablePrefixSnapshot.toolsFingerprint equals "fp_tools_abc123"
    And the second call returns the same systemPrompt string "You are a helpful assistant."
```

---

### Scenario ID: `AOC-02`
**OpenAI single-turn tail sidecar placement**

```gherkin
Feature: OpenAI tail volatile sidecars

  Scenario: AOC-02 single user message
    Given a BaseOpenAiSdkInferenceService test double with provider "OPENAI" and modelId "gpt-4o"
  When completeSingleTurn is invoked with:
      | field            | value                                      |
      | systemPrompt     | You are a helpful assistant.               |
      | contextSidecars  | State ledger JSON: {"key":"value"}         |
      | sessionMessages  | [USER id=msg1 content=Hello]               |
      | availableTools   | []                                         |
      | allowToolCall    | false                                      |
    Then ChatCompletionCreateParams.messages() has size 3
    And messages[0] is SYSTEM with text "You are a helpful assistant."
    And messages[1] is SYSTEM with text "State ledger JSON: {\"key\":\"value\"}"
    And messages[2] is USER with text "Hello"
```

**Note:** For a single user turn, tail-before-last equals immediately before the user; this matches `PC-01` **updated** order (sidecar still index 1 only because history length is 1).

---

### Scenario ID: `AOC-03`
**Tool loop preserves committed message prefix bytes**

```gherkin
Feature: Tool loop prefix stability

  Scenario: AOC-03 two completeSingleTurn calls in one sendPrompt
    Given a BaseOpenAiSdkInferenceService test double with provider "XAI" and modelId "grok-4.3"
    And ProviderAppendOnlyContext is active for the session
    When completeSingleTurn is called the first time with:
      | systemPrompt    | You are a helpful assistant. |
      | contextSidecars | State ledger JSON: {"turn":1} |
      | sessionMessages | USER u1 "Search for cats" |
      | allowToolCall   | true |
    And the captured first request messages are stored as FIRST
    When completeSingleTurn is called the second time with:
      | systemPrompt    | You are a helpful assistant. |
      | contextSidecars | State ledger JSON: {"turn":2} |
      | sessionMessages | USER u1 "Search for cats"; ASSISTANT a1 tool_call web_search; USER t1 tool_result call_1 |
      | allowToolCall   | false |
    And the captured second request messages are stored as SECOND
    Then FIRST[0].asSystem().content().asText() equals SECOND[0].asSystem().content().asText()
    And FIRST[0].asSystem().content().asText() equals "You are a helpful assistant."
    And FIRST[1] is SYSTEM with text "State ledger JSON: {\"turn\":1}"
    And FIRST[2].asUser().content().asText() equals "Search for cats"
    And SECOND[2].asUser().content().asText() equals "Search for cats"
    And SECOND[1] is SYSTEM with text "State ledger JSON: {\"turn\":1}" (frozen v1; not rewritten to turn 2)
    And SECOND[1].asSystem().content().asText() does not equal "State ledger JSON: {\"turn\":2}"
    And SECOND contains SYSTEM "State ledger JSON: {\"turn\":2}" at an index greater than 2
    And SECOND messages[1] is not USER
```

**Note:** Turn 1 tail placement is `[SYSTEM stable, SYSTEM sidecar, USER u1]`. Turn 2 adds assistant + new tail sidecar + tool; **u1 stays at wire index 2** so prefix bytes for stable system + frozen sidecar v1 + u1 are unchanged (`AOC-04` compatible).

---

### Scenario ID: `AOC-04`
**Sidecar v1 frozen when v2 appended**

**Scope:** The first scenario below exercises `ProviderAppendOnlyContext` log API in isolation. The OpenAI wire variant is integration coverage under the same scenario ID.

```gherkin
Feature: Append-only sidecar versioning

  Scenario: AOC-04 two sidecar snapshots in one session
    Given a ProviderAppendOnlyContext with an empty log
    When appendVolatileSidecar is called with sidecarText "State ledger JSON: {\"v\":1}"
    And appendVolatileSidecar is called with sidecarText "State ledger JSON: {\"v\":2}"
    Then the committed volatile sidecar texts in order are exactly:
      | index | text                              |
      | 0     | State ledger JSON: {"v":1}      |
      | 1     | State ledger JSON: {"v":2}      |
    And the log does not contain any entry with text "State ledger JSON: {\"v\":3}"
```

**OpenAI integration variant (same scenario ID):**

```gherkin
  Scenario: AOC-04 OpenAI wire carries both sidecars
    Given second completeSingleTurn from AOC-03
    Then SECOND[1] is SYSTEM with text "State ledger JSON: {\"turn\":1}"
    And SECOND contains a SYSTEM message with text "State ledger JSON: {\"turn\":2}" at index 4 (after USER u1 and ASSISTANT a1)
    And the index of SYSTEM "State ledger JSON: {\"turn\":1}" is less than the index of SYSTEM "State ledger JSON: {\"turn\":2}"
```

---

### Scenario ID: `AOC-06`
**Tools remain in payload when tool calls disallowed**

```gherkin
Feature: Tool array stability

  Scenario: AOC-06 OpenAI toolChoice none
    Given a BaseOpenAiSdkInferenceService test double with provider "OPENAI"
    And availableTools contains ToolDefinition.WEB_SEARCH
    When completeSingleTurn is called with allowToolCall false
    Then ChatCompletionCreateParams.tools() is present and has size 1
    And ChatCompletionCreateParams.toolChoice() equals ChatCompletionToolChoiceOption.Auto.NONE
```

---

### Scenario ID: `AOC-09`
**Llama merges sidecars into system**

```gherkin
Feature: Local inference merge

  Scenario: AOC-09 Llama system merge
    Given LlamaInferenceServiceImpl configured for a test session
    When completeSingleTurn is called with:
      | systemPrompt    | Base system. |
      | contextSidecars | State ledger JSON: {"local":true} |
    Then the merged system prompt passed to the local engine equals exactly:
      """
      Base system.

      Context management sidecars:
      State ledger JSON: {"local":true}
      """
    And no separate sidecar ChatMessage role SYSTEM is created
```

---

### Scenario ID: `AOC-10`
**OpenRouter inherits OpenAI placement**

```gherkin
Feature: OpenRouter placement

  Scenario: AOC-10 OpenRouter tail sidecar
    Given OpenRouterInferenceServiceImpl test double with modelId "anthropic/claude-3.5-sonnet"
    When completeSingleTurn is called with the same inputs as AOC-02
    Then message order matches AOC-02 exactly (SYSTEM stable, SYSTEM sidecar, USER Hello)
```

---

### Scenario ID: `AOC-16`
**OpenRouter cache_control on stable prior user after tail sidecar**

```gherkin
Feature: OpenRouter cache breakpoint regression

  Scenario: AOC-16 cache_control survives tail sidecar insertion
    Given OpenRouterInferenceServiceImpl test double with provider OPENROUTER and modelId "anthropic/claude-3.5-sonnet"
    And sessionMessages:
      | id | role | content |
      | u1 | USER | Hello   |
      | u2 | USER | Follow up |
    And contextSidecars is "State ledger JSON: {\"key\":\"value\"}"
    When completeSingleTurn is invoked
    Then message order is SYSTEM stable, USER Hello, SYSTEM sidecar, USER Follow up (same indices as PC-01 multi-turn)
    And messages[1] (USER Hello) has cache_control present
    And messages[1] is not a tool-result message
    And messages[3] (USER Follow up) is the live tail
```

---

### Scenario ID: `AOC-17`
**Stable prefix version increments on toolsFingerprint change**

```gherkin
Feature: Stable prefix rebuild

  Scenario: AOC-17 toolsFingerprint change increments version
    Given a ProviderAppendOnlyContext with no prior state
    And systemPrompt is "You are a helpful assistant."
    When ensureStablePrefix is called with toolsFingerprint "fp_tools_v1"
    Then StablePrefixSnapshot.version equals 1
    When ensureStablePrefix is called with toolsFingerprint "fp_tools_v2"
    Then StablePrefixSnapshot.version equals 2
```

---

### Scenario ID: `AOC-15`
**xAI tool-continuation message order**

```gherkin
Feature: xAI tail placement

  Scenario: AOC-15 sidecar before tool result tail
    Given a BaseOpenAiSdkInferenceService test double with provider "XAI" and modelId "grok-4.3"
    When completeSingleTurn is called with:
      | systemPrompt    | You are a helpful assistant. |
      | contextSidecars | State ledger JSON: {"turn":1} |
      | sessionMessages | USER u1 "Search for cats"; ASSISTANT a1 raw_tool_calls; USER t1 tool_result call_1 |
      | allowToolCall   | false |
    Then messages() has size 5
    And messages[0] is SYSTEM with text "You are a helpful assistant."
    And messages[1] is USER with text "Search for cats"
    And messages[2] is ASSISTANT
    And messages[3] is SYSTEM with text "State ledger JSON: {\"turn\":1}"
    And messages[4] is TOOL (tool result for call_1)
    And messages[1] is not SYSTEM
```

**Explicit regression guard:** `messages[1]` must **not** be SYSTEM with sidecar text (reverts `XaiPromptCacheSidecarPlacementTest` index-1 expectation).

---

## 4. Edge Case Scenarios

### Scenario ID: `AOC-11`
**closeSession invalidates context**

```gherkin
Feature: Session invalidation

  Scenario: AOC-11 closeSession resets append-only state
    Given a BaseOpenAiSdkInferenceService with an active ProviderAppendOnlyContext
    And ensureStablePrefix has been called once producing version 1
    When closeSession is called
    And ensureStablePrefix is called again with the same systemPrompt and toolsFingerprint
    Then StablePrefixSnapshot.version equals 1
    And committedMessageCount equals 0
```

---

### Scenario ID: `AOC-13`
**Compaction clears log**

```gherkin
Feature: Compaction handling

  Scenario: AOC-13 shorter normalized array
    Given ProviderAppendOnlyContext with lastSyncCount 4
    When syncMessages is called with a normalized list of size 2
    Then committedMessageCount equals 2
    And the log contains only the two messages from the new normalized list
    And lastSyncCount after sync equals 2
```

---

### Scenario ID: `AOC-14`
**Blank sidecars skipped**

```gherkin
Feature: Empty volatile payload

  Scenario: AOC-14 null sidecars
    Given a BaseOpenAiSdkInferenceService test double
    When completeSingleTurn is called with contextSidecars null
    Then no SYSTEM message is inserted solely for sidecars
    And message count equals 2 (SYSTEM stable + USER only) for sessionMessages [USER Hello]

  Scenario: AOC-14 blank sidecars
    When completeSingleTurn is called with contextSidecars ""
    Then no SYSTEM message is inserted solely for sidecars
```

---

## 5. Mutation-Defense Scenarios

### Scenario ID: `AOC-05`
**syncMessages digest detects in-place rewrite**

```gherkin
Feature: Digest-based rewrite detection

  Scenario: AOC-05 in-place content change clears log
    Given ProviderAppendOnlyContext
    And normalized messages synced:
      | index | role      | content |
      | 0     | user      | Hello   |
      | 1     | assistant | Hi      |
    And lastSyncCount equals 2
    When syncMessages is called with:
      | index | role      | content        |
      | 0     | user      | Hello altered  |
      | 1     | assistant | Hi             |
    Then committedMessageCount equals 2
    And messages[0].content equals "Hello altered"
    And messages[0].content does not equal "Hello"
    And the log was cleared before append (internal clearCount equals 1)
```

**Literal digest test (unit):**

```gherkin
  Scenario: AOC-05 digest inequality triggers clear
    Given digest D1 computed for normalized prefix [USER Hello]
    And digest D2 computed for normalized prefix [USER Hello altered]
    Then D1 does not equal D2
```

---

### Scenario ID: `AOC-12`
**No index-1 sidecar pin when history length >= 3**

```gherkin
Feature: Reject index-1 pin

  Scenario: AOC-12 multi-message session
    Given BaseOpenAiSdkInferenceService test double
    When completeSingleTurn is called with sessionMessages:
      | id | role      | content      |
      | u1 | USER      | First        |
      | a1 | ASSISTANT | Ack          |
      | u2 | USER      | Second       |
    And contextSidecars is "State ledger JSON: {\"k\":1}"
    Then messages[1].role is not SYSTEM
    Or messages[1].asSystem().content().asText() does not equal "State ledger JSON: {\"k\":1}"
    And the SYSTEM sidecar text appears at an index greater than 1
    And messages[lastIndex - 1] is SYSTEM with sidecar text when last message is USER u2
```

---

## 6. Regression Scenarios (Anthropic / Google)

### Scenario ID: `AOC-07`
**Anthropic behavior unchanged**

```gherkin
Feature: Anthropic regression

  Scenario: AOC-07 delegates to PromptCachingTest PC-02 and PC-03
    Given existing tests test_PC_02_AnthropicAttachesEphemeralCacheControl and test_PC_03_AnthropicLimitsCacheControlToExactly3Breakpoints pass without modification except imports
    Then Anthropic MessageCreateParams still prepends contextSidecars to the latest user message u2
    And u2 text contains both "Volatile sidecar content" and "Explain the plan."
    And exactly 3 cache_control breakpoints remain
    And stable history tail breakpoint remains on assistant message a1 (index 1), not on live u2
```

**Literal expectations from `PC-02` (must still hold):**

| Check | Expected value |
|---|---|
| System block text | `You are a helpful assistant.` |
| System block cache_control | present |
| Last tool name | `append_blocks` with cache_control present |
| messages[2] user text contains | `Volatile sidecar content` |
| messages[2] user text contains | `Explain the plan.` |
| messages[2] cache_control on text block | absent |

---

### Scenario ID: `AOC-08`
**Google behavior unchanged**

```gherkin
Feature: Google regression

  Scenario: AOC-08 delegates to PromptCachingTest PC-04 through PC-08
    Given tests test_PC_04_GoogleGenAiCreatesCachedContentOnExceededThreshold through test_PC_08 pass
    Then createCachedContent still excludes volatile sidecars from cached prefix
    And generateContentStream still prepends sidecars only on the latest live Content
    And closeSession still calls deleteCachedContent
```

---

## 7. Updated Prompt Caching Scenario

### Scenario ID: `PC-01` (updated)
**OpenAI separates system and tail sidecars — revised order for multi-turn**

```gherkin
Feature: PC-01 updated for append-only tail rules

  Scenario: PC-01 single-turn (unchanged literals)
    Given OpenAI service and sessionMessages [USER Hello]
    When completeSingleTurn with systemPrompt "You are a helpful assistant." and contextSidecars "State ledger JSON: {\"key\":\"value\"}"
    Then messages size equals 3
    And messages[0] SYSTEM text equals "You are a helpful assistant."
    And messages[1] SYSTEM text equals "State ledger JSON: {\"key\":\"value\"}"
    And messages[2] USER text equals "Hello"

  Scenario: PC-01 multi-turn tail placement
    Given sessionMessages [USER u1 Hello; USER u2 Follow up]
    When completeSingleTurn with same system and contextSidecars "State ledger JSON: {\"key\":\"value\"}"
    Then messages size equals 4
    And messages[0] SYSTEM text equals "You are a helpful assistant."
    And messages[1] USER text equals "Hello"
    And messages[2] SYSTEM text equals "State ledger JSON: {\"key\":\"value\"}"
    And messages[3] USER text equals "Follow up"
    And messages[1] is not SYSTEM
    And messages[2] is SYSTEM (sidecar immediately before live tail)
```

**Literal table (PC-01 multi-turn):**

| Index | Role | Text |
|---|---|---|
| 0 | SYSTEM | You are a helpful assistant. |
| 1 | USER | Hello |
| 2 | SYSTEM | State ledger JSON: {"key":"value"} |
| 3 | USER | Follow up |

---

## 8. Rust Loop Integration Scenario

### Scenario ID: `AOC-03` (RustLoopInferenceServiceTest)

```gherkin
Feature: Rust loop forwards sidecars without prefix shift

  Scenario: AOC-03 RustLoop host double completeModelTurn
    Given RecordingRustCompatibleInferenceService wrapped by RustLoopInferenceService
    When sendPrompt "run tool loop" executes a host turn with allowToolCall true
    And a second host turn runs with allowToolCall false and longer sessionMessages
    Then completeSingleTurnCallCount equals 2
    And lastContextSidecars from turn 2 contains "State ledger"
    And first captured request messages[1] role equals USER (not SYSTEM sidecar) when first session had at least one USER before assistant
```

---

## 9. Self-Review Checklist

| Check | Status |
|---|---|
| Every task in spec.md references scenario IDs | Yes (§8 spec + §2 index); Task 1 includes `AOC-01`, `AOC-17` |
| No TBD / TODO placeholders | Yes |
| Index-1 revert explicitly tested (`AOC-12`, `AOC-15`) | Yes |
| Tool loop two-call prefix test (`AOC-03`) | Yes |
| Sidecar v1/v2 append (`AOC-04`) | Yes |
| syncMessages digest clear (`AOC-05`) | Yes |
| allowToolCall false tools stable (`AOC-06`) | Yes |
| Anthropic/Google regression (`AOC-07`, `AOC-08`) | Yes |
| Literal expected values in tables | Yes |
| Run commands narrow + CI | Yes |
