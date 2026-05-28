# pc-cache-stability — Test Specification

## Test Framework

**Engine:** JUnit 5 (`org.junit.jupiter.api`).
**Coroutines:** `kotlinx.coroutines.test.runTest` with `StandardTestDispatcher`.
**Mocking:** MockK for third-party SDK boundaries (OpenAI client, Anthropic client, Google SDK).
**Fakes:** Domain ports use lightweight in-memory fakes consistent with `UNIT_TESTING.md`.
**Style:** State-based assertions; no `coVerify` call-count assertions unless the observable
state cannot be checked any other way.

**Run command (narrowest):**
```
./gradlew :feature:inference:testDebugUnitTest :core:domain:testDebugUnitTest
```
**Run command (full affected):**
```
./gradlew testDebugUnitTest
```

---

## Scenario Index

| ID | Title | Module | Class |
|---|---|---|---|
| CSPC-01 | N+1 turn, no tools, no RAG, no memories — wire user content byte-identical | `feature/inference` | `RustLoopVolatileTurnContextCommitTest` |
| CSPC-02 | N+1 turn with mid-turn tool calls — wire user content byte-identical | `feature/inference` | `RustLoopVolatileTurnContextCommitTest` |
| CSPC-03a | Turn 1 fires web pre-retrieval — turn 2 wire reproduces turn 1 user content with same RAG prefix | `feature/inference` | `RustLoopVolatileTurnContextCommitTest` |
| CSPC-03b | Turn 1 fires X pre-retrieval — turn 2 wire reproduces same RAG prefix | `feature/inference` | `RustLoopVolatileTurnContextCommitTest` |
| CSPC-03c | Turn 1 fires web + X pre-retrieval — turn 2 wire reproduces combined RAG prefix | `feature/inference` | `RustLoopVolatileTurnContextCommitTest` |
| CSPC-04a | Deferred tool, no activation — catalog block bytes identical across turns | `core/domain` | `ChatInferenceRequestPreparerCacheStabilityTest` |
| CSPC-04b | Deferred tool flips to intent-activated on turn 2 — catalog stable (monotonic union) | `core/domain` | `ChatInferenceRequestPreparerCacheStabilityTest` |
| CSPC-04c | `disclose_tool_schemas` runtime activation within a turn — next-turn catalog stable | `core/domain` | `ChatInferenceRequestPreparerCacheStabilityTest` |
| CSPC-05 | Retrieved memories only (no search) — turn 2 wire reproduces turn 1 user content with memory prefix | `feature/inference` | `RustLoopVolatileTurnContextCommitTest` |
| CSPC-06 | Compaction boundary fires — log clears, prior frozen sidecars absent from wire | `feature/inference` | `RustLoopVolatileTurnContextCommitTest` |
| CSPC-MUTDEF-01 | Monotonic store returning identity for new names fails the CSPC-04b scenario | `core/domain` | `ChatInferenceRequestPreparerCacheStabilityTest` |
| CSPC-ANTHRO-03a | Anthropic: turn 2 wire reproduces turn 1 user content with same RAG prefix | `feature/inference` | `AnthropicVolatileTurnContextCommitTest` |
| CSPC-ANTHRO-05 | Anthropic: retrieved memories only — turn 2 user block identical | `feature/inference` | `AnthropicVolatileTurnContextCommitTest` |
| CSPC-GOOGLE-03a | Google: turn 2 wire reproduces turn 1 user content with same RAG prefix | `feature/inference` | `GoogleVolatileTurnContextCommitTest` |
| CSPC-GOOGLE-05 | Google: retrieved memories only — turn 2 user content identical | `feature/inference` | `GoogleVolatileTurnContextCommitTest` |

---

## Scenarios

---

### CSPC-01 — N+1 turn, no tools, no RAG, no memories — wire user content byte-identical

**File:** `feature/inference/src/test/.../RustLoopVolatileTurnContextCommitTest.kt`

```gherkin
Feature: Cache-stable user content across turns
  Scenario: CSPC-01 — no volatile context, turn 2 user message equals turn 1 user message bytes
    Given a RustLoopInferenceService wrapping a CapturingRustCompatibleService
    And a FakeAgentLoopPort that emits [TurnStarted, TurnCompleted, Finished] with sessionMessages
      containing [AgentLoopMessage(id="u1", role=USER, content="Hello")]
    And GenerationOptions with volatileTurnContext = null and no activeTools and no toolCatalog

    When sendPrompt(prompt="Hello", options=options) is collected

    Then the CapturingRustCompatibleService records a completeSingleTurn call
    And sessionMessages[0].content equals "Hello"

    When a second sendPrompt(prompt="Hello", options=options) is collected
    And the FakeAgentLoopPort replays sessionMessages =
      [AgentLoopMessage(id="u1", role=USER, content="Hello"),
       AgentLoopMessage(id="a1", role=ASSISTANT, content="World"),
       AgentLoopMessage(id="u2", role=USER, content="Hello")]

    Then sessionMessages[0].content in the second completeSingleTurn call equals exactly "Hello"
    And sessionMessages[0].content == sessionMessages[0].content from turn 1
```

**Literal expected values:**
- `sessionMessages[0].content` in turn 2 == `"Hello"` (identical to turn 1)

---

### CSPC-02 — N+1 turn with mid-turn tool calls — wire user content byte-identical

**File:** `feature/inference/src/test/.../RustLoopVolatileTurnContextCommitTest.kt`

```gherkin
  Scenario: CSPC-02 — mid-turn tool calls do not corrupt user content in next turn
    Given a RustLoopInferenceService with CapturingRustCompatibleService
    And GenerationOptions with volatileTurnContext = null

    When turn 1 executes with sessionMessages containing a tool-call exchange:
      [USER("What time is it?"),
       ASSISTANT(metadataJson={"raw_tool_calls":[{"id":"c1","name":"web_search","arguments_json":"{}"}]}),
       USER(content="{\"result\":\"3pm\"}", metadataJson={"call_id":"c1"}),
       ASSISTANT(content="It is 3pm")]
    And turn 2 sendPrompt(prompt="Tell me more") is collected
    And FakeAgentLoopPort provides turn 2 sessionMessages:
      [USER("What time is it?"),
       ASSISTANT with tool call for web_search,
       USER(tool_result),
       ASSISTANT("It is 3pm"),
       USER("Tell me more")]

    Then sessionMessages.last() in the turn 2 completeSingleTurn call has
      role = USER
      content = "Tell me more"
    And sessionMessages[0].content = "What time is it?"
```

**Literal expected values:**
- `sessionMessages.last().content` == `"Tell me more"` (no RAG prefix inserted)
- `sessionMessages[0].content` == `"What time is it?"` (unchanged from turn 1)

---

### CSPC-03a — Turn 1 fires web pre-retrieval — turn 2 wire reproduces RAG prefix

**File:** `feature/inference/src/test/.../RustLoopVolatileTurnContextCommitTest.kt`

```gherkin
  Scenario: CSPC-03a — web RAG prefix stored in Rust history; turn 2 replays identical bytes
    Given a RustLoopInferenceService with CapturingRustCompatibleService
    And GenerationOptions turn1 with volatileTurnContext =
      "# RELEVANT WEB SEARCH RESULTS:\nresult-text-abc"
    And GenerationOptions turn2 with volatileTurnContext = null

    When turn 1 executes sendPrompt(prompt = "search query")
    And the FakeAgentLoopPort captures the initialMessages passed to agentLoopPort.execute
    Then initialMessages.last().content equals
      "# RELEVANT WEB SEARCH RESULTS:\nresult-text-abc\n\nsearch query"

    When turn 2 executes sendPrompt(prompt = "follow up")
    And FakeAgentLoopPort provides sessionMessages including:
      USER("# RELEVANT WEB SEARCH RESULTS:\nresult-text-abc\n\nsearch query")
      as the committed user message from turn 1
    Then sessionMessages[0].content in completeSingleTurn call ==
      "# RELEVANT WEB SEARCH RESULTS:\nresult-text-abc\n\nsearch query"
    And this matches the initialMessages.last().content from turn 1 exactly
```

**Literal expected values:**
- Turn 1 `initialMessages.last().content` == `"# RELEVANT WEB SEARCH RESULTS:\nresult-text-abc\n\nsearch query"`
- Turn 2 `sessionMessages[0].content` (replayed by Rust) == `"# RELEVANT WEB SEARCH RESULTS:\nresult-text-abc\n\nsearch query"`

---

### CSPC-03b — Turn 1 fires X pre-retrieval — turn 2 replays same RAG prefix

**File:** `feature/inference/src/test/.../RustLoopVolatileTurnContextCommitTest.kt`

```gherkin
  Scenario: CSPC-03b — X search RAG prefix stored in Rust history; turn 2 replays identical bytes
    Given GenerationOptions turn1 with volatileTurnContext =
      "# RELEVANT X SEARCH RESULTS:\ntweet-data-xyz"
    And GenerationOptions turn2 with volatileTurnContext = null

    When turn 1 executes sendPrompt(prompt = "x query")
    Then initialMessages.last().content equals
      "# RELEVANT X SEARCH RESULTS:\ntweet-data-xyz\n\nx query"

    When turn 2 FakeAgentLoopPort replays sessionMessages[0].content =
      "# RELEVANT X SEARCH RESULTS:\ntweet-data-xyz\n\nx query"
    Then sessionMessages[0].content in completeSingleTurn call ==
      "# RELEVANT X SEARCH RESULTS:\ntweet-data-xyz\n\nx query"
```

**Literal expected values:**
- Turn 1 merge == `"# RELEVANT X SEARCH RESULTS:\ntweet-data-xyz\n\nx query"`
- Turn 2 replay == identical string

---

### CSPC-03c — Turn 1 fires web + X pre-retrieval — turn 2 replays combined RAG prefix

**File:** `feature/inference/src/test/.../RustLoopVolatileTurnContextCommitTest.kt`

```gherkin
  Scenario: CSPC-03c — combined web+X RAG prefix stored in Rust history; turn 2 replays it
    Given GenerationOptions turn1 with volatileTurnContext =
      "# RELEVANT WEB SEARCH RESULTS:\nweb-results\n\n# RELEVANT X SEARCH RESULTS:\nx-results"

    When turn 1 executes sendPrompt(prompt = "combo query")
    Then initialMessages.last().content equals
      "# RELEVANT WEB SEARCH RESULTS:\nweb-results\n\n# RELEVANT X SEARCH RESULTS:\nx-results\n\ncombo query"

    When turn 2 FakeAgentLoopPort replays sessionMessages[0].content with that value
    Then sessionMessages[0].content in the completeSingleTurn call ==
      "# RELEVANT WEB SEARCH RESULTS:\nweb-results\n\n# RELEVANT X SEARCH RESULTS:\nx-results\n\ncombo query"
```

**Literal expected values:**
- Merged content == `"# RELEVANT WEB SEARCH RESULTS:\nweb-results\n\n# RELEVANT X SEARCH RESULTS:\nx-results\n\ncombo query"`

---

### CSPC-04a — Deferred tool, no activation — catalog block bytes identical across turns

**File:** `core/domain/src/test/.../ChatInferenceRequestPreparerCacheStabilityTest.kt`

```gherkin
  Scenario: CSPC-04a — deferred tool never activated; system prompt catalog block identical
    Given a ChatInferenceRequestPreparer with a NoopSearchIntentClassifier
      (requiresSearch = false, getSearchIntentProbability = 0.0f)
    And settings with webSearchConfigured = true
    And MonotonicToolActivationPort = InMemoryMonotonicToolActivationStore

    When invoke(prompt = "hello", chatId = "chat-1") is called for turn 1
    Then options.systemPrompt contains "- search_chat_history [deferred]:"
    And let systemPrompt1 = options.systemPrompt

    When invoke(prompt = "hello again", chatId = "chat-1") is called for turn 2
    Then options.systemPrompt contains "- search_chat_history [deferred]:"
    And let systemPrompt2 = options.systemPrompt
    And systemPrompt1 == systemPrompt2
```

**Literal expected values:**
- `systemPrompt1 == systemPrompt2` (string equality)
- Both contain the substring `"- search_chat_history [deferred]:"`

---

### CSPC-04b — Deferred tool flips to intent-activated on turn 2 — catalog stable (monotonic union)

**File:** `core/domain/src/test/.../ChatInferenceRequestPreparerCacheStabilityTest.kt`

```gherkin
  Scenario: CSPC-04b — intent classifier activates search_x_recent on turn 2 but not turn 1;
            monotonic store keeps catalog identical on turn 3
    Given a ChatInferenceRequestPreparer with InMemoryMonotonicToolActivationStore
    And settings with xSearchConfigured = true
    And a ControlledSearchIntentClassifier that returns requiresSearch=false for turn 1
      and requiresSearch=true for turn 2
      and requiresSearch=false for turn 3

    When invoke(prompt = "plain text", chatId = "chat-1") for turn 1
    Then options.toolCatalog entry for "search_x_recent" has disclosureMode = DISCLOSE_ON_DEMAND
    And let systemPromptTurn1 = options.systemPrompt

    When invoke(prompt = "breaking news x", chatId = "chat-1") for turn 2
    Then options.toolCatalog entry for "search_x_recent" has disclosureMode = INTENT_ACTIVATED
    And let systemPromptTurn2 = options.systemPrompt

    When invoke(prompt = "plain text again", chatId = "chat-1") for turn 3
    Then options.toolCatalog entry for "search_x_recent" has disclosureMode = INTENT_ACTIVATED
    And let systemPromptTurn3 = options.systemPrompt
    And systemPromptTurn2 == systemPromptTurn3
```

**Literal expected values:**
- Turn 1: `toolCatalog.single { it.name == "search_x_recent" }.disclosureMode` == `ToolDisclosureMode.DISCLOSE_ON_DEMAND`
- Turn 2: same entry `.disclosureMode` == `ToolDisclosureMode.INTENT_ACTIVATED`
- Turn 3: same entry `.disclosureMode` == `ToolDisclosureMode.INTENT_ACTIVATED`
- `systemPromptTurn2 == systemPromptTurn3` (string equality — no byte difference)

---

### CSPC-04c — `disclose_tool_schemas` runtime activation within a turn — next-turn catalog stable

**File:** `core/domain/src/test/.../ChatInferenceRequestPreparerCacheStabilityTest.kt`

```gherkin
  Scenario: CSPC-04c — runtime disclose_tool_schemas does not affect the preparer's catalog output
            on the following turn
    Given a ChatInferenceRequestPreparer with InMemoryMonotonicToolActivationStore
    And settings with webSearchConfigured = false (so start_artifact is deferred)
    And a ControlledSearchIntentClassifier that always returns requiresSearch=false

    When invoke(prompt = "draft a report", chatId = "chat-2") for turn 1
    Then let systemPromptTurn1 = options.systemPrompt
    And systemPromptTurn1 contains "- start_artifact [deferred]:"

    # Simulate the model calling disclose_tool_schemas for start_artifact at runtime.
    # The preparer is NOT called again during the same turn.
    # The InMemoryMonotonicToolActivationStore is NOT updated by disclose_tool_schemas.
    # (Disclosure is handled by ToolSchemaDisclosureResolver inside RustLoopInferenceService.)

    When invoke(prompt = "continue drafting", chatId = "chat-2") for turn 2
    Then let systemPromptTurn2 = options.systemPrompt
    And systemPromptTurn2 contains "- start_artifact [deferred]:"
    And systemPromptTurn1 == systemPromptTurn2
```

**Literal expected values:**
- `systemPromptTurn1` contains `"- start_artifact [deferred]:"` (exact substring)
- `systemPromptTurn2` contains `"- start_artifact [deferred]:"` (exact substring)
- `systemPromptTurn1 == systemPromptTurn2`

**Design note:** The `MonotonicToolActivationPort` is updated only by
`ChatInferenceRequestPreparer` via the intent classifier, not by `ToolSchemaDisclosureResolver`.
Runtime disclosure within a turn has no side-effect on the preparer's catalog output.

---

### CSPC-05 — Retrieved memories only — turn 2 wire reproduces turn 1 user content with memory prefix

**File:** `feature/inference/src/test/.../RustLoopVolatileTurnContextCommitTest.kt`

```gherkin
  Scenario: CSPC-05 — kNN memories only (no search); turn 2 replays turn 1 user content with prefix
    Given GenerationOptions turn1 with volatileTurnContext =
      "# RELEVANT FACTS & CONTEXT:\n- User prefers concise answers\n- User is a developer"
    And GenerationOptions turn2 with volatileTurnContext = null

    When turn 1 executes sendPrompt(prompt = "help me code")
    Then initialMessages.last().content equals
      "# RELEVANT FACTS & CONTEXT:\n- User prefers concise answers\n- User is a developer\n\nhelp me code"

    When turn 2 FakeAgentLoopPort replays sessionMessages[0].content with that value
    Then sessionMessages[0].content in the completeSingleTurn call ==
      "# RELEVANT FACTS & CONTEXT:\n- User prefers concise answers\n- User is a developer\n\nhelp me code"
```

**Literal expected values:**
- `"# RELEVANT FACTS & CONTEXT:\n- User prefers concise answers\n- User is a developer\n\nhelp me code"`

---

### CSPC-06 — Compaction fires — log clears, prior frozen sidecars absent from wire

**File:** `feature/inference/src/test/.../RustLoopVolatileTurnContextCommitTest.kt`

```gherkin
  Scenario: CSPC-06 — CompactionBoundaryApplied event clears append-only log;
            prior sidecar not replayed
    Given a RustLoopInferenceService with CapturingRustCompatibleService
    And an inner CapturingBaseOpenAiService that records completeSingleTurn wire payloads

    When turn 1 executes sendPrompt with contextSidecars = "State ledger JSON: {\"turn\":1}"
    Then the sidecar is frozen in appendOnlyContext after endTurnFreezeSidecar

    When the FakeAgentLoopPort emits AgentLoopEvent.CompactionBoundaryApplied(revisionAfter = 1)
    Then inner.clearAppendOnlyOnRustCompactionBoundary() is called
    And appendOnlyContext.committedVolatileSidecarTexts() is empty after clearing

    When turn 2 executes sendPrompt with contextSidecars = "State ledger JSON: {\"turn\":2}"
    Then the wire body passed to completeSingleTurn does NOT contain
      any message with content = "State ledger JSON: {\"turn\":1}"
    And the wire body DOES contain a message with content = "State ledger JSON: {\"turn\":2}"
```

**Literal expected values:**
- No message in `completeSingleTurn` call with `content == "State ledger JSON: {\"turn\":1}"`
- At least one message with `content == "State ledger JSON: {\"turn\":2}"`

---

### CSPC-MUTDEF-01 — Mutation defense: stub returning identity for new names fails CSPC-04b

**File:** `core/domain/src/test/.../ChatInferenceRequestPreparerCacheStabilityTest.kt`

```gherkin
  Scenario: CSPC-MUTDEF-01 — identity MonotonicToolActivationPort causes catalog to shrink on turn 3
    Given a ChatInferenceRequestPreparer with an IdentityMonotonicToolActivationPort
      whose union() always returns the input set unchanged (i.e. it does NOT accumulate)
    And a ControlledSearchIntentClassifier that returns requiresSearch=true for turn 2
      and requiresSearch=false for turn 3

    When invoke for turn 2 with requiresSearch=true
    Then options.toolCatalog entry for "search_x_recent" has disclosureMode = INTENT_ACTIVATED

    When invoke for turn 3 with requiresSearch=false
    Then options.toolCatalog entry for "search_x_recent" has disclosureMode = DISCLOSE_ON_DEMAND
    And let systemPromptTurn2 = result from above turn 2 call
    And let systemPromptTurn3 = result from above turn 3 call
    And systemPromptTurn2 != systemPromptTurn3
```

**Purpose:** This scenario MUST FAIL if the production `MonotonicToolActivationPort` is replaced
with the `IdentityMonotonicToolActivationPort` test stub, proving that the lazy/identity
implementation does not satisfy the cache-stability requirement.

**Literal expected values (for the mutation stub):**
- Turn 2: `disclosureMode == ToolDisclosureMode.INTENT_ACTIVATED`
- Turn 3: `disclosureMode == ToolDisclosureMode.DISCLOSE_ON_DEMAND`
- `systemPromptTurn2 != systemPromptTurn3` (strings differ — confirming the bug that the real code must prevent)

---

### CSPC-ANTHRO-03a — Anthropic: turn 2 wire reproduces turn 1 user content with same RAG prefix

**File:** `feature/inference/src/test/.../AnthropicVolatileTurnContextCommitTest.kt`

```gherkin
  Scenario: CSPC-ANTHRO-03a — Anthropic completeSingleTurn; RAG prefix is not double-prepended
    Given an AnthropicInferenceServiceImpl with a captured MessageCreateParams list
    And turn 1 sessionMessages = [USER("web-prefix\n\noriginal query")]
    And GenerationOptions with volatileTurnContext = "web-prefix"

    When completeSingleTurn is called for turn 1

    Then the first USER MessageParam text block contains exactly
      "web-prefix\n\noriginal query"
    And the text does NOT start with "web-prefix\n\nweb-prefix"

    When completeSingleTurn is called for turn 2 with sessionMessages =
      [USER("web-prefix\n\noriginal query"), ASSISTANT("answer"), USER("follow-up")]
    And GenerationOptions with volatileTurnContext = null

    Then the first USER MessageParam text block contains exactly
      "web-prefix\n\noriginal query"
    And the third USER MessageParam text block contains exactly "follow-up"
```

**Literal expected values:**
- First USER text block in turn 1 == `"web-prefix\n\noriginal query"` (no double-prepend)
- First USER text block in turn 2 == `"web-prefix\n\noriginal query"` (replayed from Rust history)
- The string `"web-prefix\n\nweb-prefix"` does NOT appear in any message text block

---

### CSPC-ANTHRO-05 — Anthropic: retrieved memories only — turn 2 user block identical

**File:** `feature/inference/src/test/.../AnthropicVolatileTurnContextCommitTest.kt`

```gherkin
  Scenario: CSPC-ANTHRO-05 — Anthropic; kNN memory prefix not double-applied
    Given turn 1 sessionMessages = [USER("# RELEVANT FACTS & CONTEXT:\n- fact-a\n\nhelp")]
    And GenerationOptions with volatileTurnContext = "# RELEVANT FACTS & CONTEXT:\n- fact-a"

    When completeSingleTurn is called for turn 1
    Then the first USER MessageParam text block ==
      "# RELEVANT FACTS & CONTEXT:\n- fact-a\n\nhelp"
    And the text does NOT contain "# RELEVANT FACTS & CONTEXT:\n- fact-a\n\n# RELEVANT FACTS"

    When completeSingleTurn is called for turn 2 with sessionMessages =
      [USER("# RELEVANT FACTS & CONTEXT:\n- fact-a\n\nhelp"), ASSISTANT("ok"), USER("more")]
    And GenerationOptions with volatileTurnContext = null
    Then sessionMessages[0] text block == "# RELEVANT FACTS & CONTEXT:\n- fact-a\n\nhelp"
```

**Literal expected values:**
- `"# RELEVANT FACTS & CONTEXT:\n- fact-a\n\nhelp"` (no duplication)

---

### CSPC-GOOGLE-03a — Google: turn 2 wire reproduces turn 1 user content with same RAG prefix

**File:** `feature/inference/src/test/.../GoogleVolatileTurnContextCommitTest.kt`

```gherkin
  Scenario: CSPC-GOOGLE-03a — Google completeSingleTurn; RAG prefix not double-prepended
    Given a GoogleInferenceServiceImpl with a FakeGoogleGenAiSdkClient that captures contents
    And turn 1 sessionMessages = [USER("web-prefix\n\nquery")]
    And GenerationOptions with volatileTurnContext = "web-prefix"
      and contextSidecars = null

    When completeSingleTurn is called for turn 1
    Then the last Content in contentsToSend has a single text Part ==
      "web-prefix\n\nquery"
    And the text does NOT start with "web-prefix\n\nweb-prefix"

    When completeSingleTurn is called for turn 2 with sessionMessages =
      [USER("web-prefix\n\nquery"), ASSISTANT("answer"), USER("follow-up")]
    And GenerationOptions with volatileTurnContext = null and contextSidecars = null
    Then the last Content in contentsToSend has text Part == "follow-up"
    And contentsToSend[0] has text Part == "web-prefix\n\nquery"
```

**Literal expected values:**
- Turn 1 last Content text == `"web-prefix\n\nquery"`
- Turn 2 first Content text == `"web-prefix\n\nquery"` (stable replay)
- Turn 2 last Content text == `"follow-up"`

---

### CSPC-GOOGLE-05 — Google: retrieved memories only — turn 2 user content identical

**File:** `feature/inference/src/test/.../GoogleVolatileTurnContextCommitTest.kt`

```gherkin
  Scenario: CSPC-GOOGLE-05 — Google; kNN memory prefix not double-applied
    Given turn 1 sessionMessages = [USER("# RELEVANT FACTS & CONTEXT:\n- fact-a\n\nhelp")]
    And GenerationOptions with volatileTurnContext = "# RELEVANT FACTS & CONTEXT:\n- fact-a"
      and contextSidecars = null

    When completeSingleTurn is called for turn 1
    Then last Content text Part == "# RELEVANT FACTS & CONTEXT:\n- fact-a\n\nhelp"

    When completeSingleTurn is called for turn 2
    And sessionMessages[0] = USER("# RELEVANT FACTS & CONTEXT:\n- fact-a\n\nhelp")
    Then contentsToSend[0] text Part == "# RELEVANT FACTS & CONTEXT:\n- fact-a\n\nhelp"
```

**Literal expected values:**
- `"# RELEVANT FACTS & CONTEXT:\n- fact-a\n\nhelp"` (no duplication)

---

## Mutation-Defense Analysis

**Risk:** The simplest wrong implementation of `MonotonicToolActivationPort` is to have `union()`
return `newNames` unchanged instead of the accumulated union. This is the "identity stub" pattern
— it compiles, passes the trivial single-turn tests, but fails across turns because the set
shrinks back to whatever the current classifier says.

**Scenario CSPC-MUTDEF-01** is specifically designed to fail against this stub:
- It requires three turns where the classifier flips (true → false).
- The identity stub returns `emptySet()` on turn 3, causing the catalog to revert
  `search_x_recent` back to `DISCLOSE_ON_DEMAND`.
- The real implementation returns `{"search_x_recent"}` on turn 3 because it was added on turn 2.
- The scenario asserts `systemPromptTurn2 != systemPromptTurn3` on the stub, confirming the
  catalog changed — proving the stub is broken and the real implementation is required.

**Risk:** A naive fix to BUG 1 might concatenate `volatileTurnContext` onto the wire message
but still NOT store it in Rust history (i.e., apply it in `toMappedSessionBody` again without
removing the original `RustLoopInferenceService` path).
Scenarios CSPC-03a, CSPC-03b, CSPC-03c, CSPC-05 detect this by asserting that the
`sessionMessages[0].content` replayed from Rust in turn 2 already contains the RAG prefix —
a double-apply on the wire would cause `sessionMessages[0].content` to be the bare prompt but
the wire to have the prefix twice.

---

## Test Helper Contract

The following test-only helpers are needed and must be created inline in the test files or in
a shared `TestFixtures.kt` file within each module's `src/test` source set:

### `FakeAgentLoopPort` (used in `RustLoopVolatileTurnContextCommitTest`)

```kotlin
class FakeAgentLoopPort(
    private val scenario: suspend (AgentLoopHostCallbacks) -> Unit,
) : AgentLoopPort {
    var capturedInitialMessages: List<ChatMessage> = emptyList()

    override suspend fun execute(
        initialMessages: List<ChatMessage>,
        config: AgentLoopConfig,
        sessionContext: Map<String, String>,
        host: AgentLoopHostCallbacks,
    ): Flow<AgentLoopEvent> = flow {
        capturedInitialMessages = initialMessages
        scenario(host)
    }
}
```

### `CapturingRustCompatibleService` (used in `RustLoopVolatileTurnContextCommitTest`)

```kotlin
class CapturingRustCompatibleService : RustCompatibleInferenceService {
    val capturedTurnArgs = mutableListOf<List<AgentLoopMessage>>()

    override suspend fun completeSingleTurn(
        sessionMessages: List<AgentLoopMessage>,
        ...
    ): AgentModelTurnResult {
        capturedTurnArgs.add(sessionMessages)
        return AgentModelTurnResult(
            assistantText = "ok",
            toolCalls = emptyList(),
            finishReason = AgentFinishReason.Stop,
        )
    }
    ...
}
```

### `IdentityMonotonicToolActivationPort` (used in `CSPC-MUTDEF-01`)

```kotlin
object IdentityMonotonicToolActivationPort : MonotonicToolActivationPort {
    override fun union(chatId: ChatId, newNames: Set<String>): Set<String> = newNames
    override fun clearSession(chatId: ChatId) = Unit
}
```

### `ControlledSearchIntentClassifier` (used in `CSPC-04b`, `CSPC-04c`, `CSPC-MUTDEF-01`)

```kotlin
class ControlledSearchIntentClassifier(
    private val decisions: ArrayDeque<Boolean>,
) : SearchIntentClassifierPort {
    override fun requiresSearch(embedding: FloatArray, threshold: Float): Boolean =
        decisions.removeFirstOrNull() ?: false
    override fun getSearchIntentProbability(embedding: FloatArray): Float =
        if (decisions.firstOrNull() == true) 0.95f else 0.05f
}
```
