# centralized-leaked-tool-call-recovery — test_spec.md
# Test Specification: Centralized Leaked Tool-Call Recovery

---

## Test Framework and Run Commands

| Layer | Framework | Location |
|-------|-----------|----------|
| Rust stream gate | `cargo test` (libtest) | `third_party/agent_core/tests/tool_stream_gate_test.rs` |
| Rust turn resolver | `cargo test` (libtest) | `third_party/agent_core/tests/leaked_tool_recovery_test.rs` |
| Rust engine ingest | `cargo test` (libtest) | `third_party/agent_core/tests/engine_envelope_retry_test.rs` |
| Rust tool parse | `cargo test` (libtest) | `third_party/agent_core/tests/tool_parse_integration.rs` |
| Kotlin agent bridge | JUnit 5 + `kotlinx.coroutines.test.runTest` | `core/agent/src/test/kotlin/.../ModelTurnStreamGateTest.kt`, `RustAgentEngineAdapterLeakTest.kt` |
| Kotlin inference | JUnit 5 + fakes | `feature/inference/src/test/kotlin/.../BaseOpenAiSdkInferenceServiceTest.kt`, `CompleteSingleTurnRawSubmitTest.kt`, `LeakedToolRecoveryMutationTest.kt` |
| Domain schema regression | JUnit 5 | `core/domain/src/test/kotlin/.../GenerateArtifactParamsTest.kt` |
| Mutation defense | Rust + Kotlin source guards | `leaked_tool_recovery_test.rs`, `LeakedToolRecoveryMutationTest.kt` |

### Run Commands

```bash
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_stream_gate_test
cargo test --manifest-path third_party/agent_core/Cargo.toml --test leaked_tool_recovery_test
cargo test --manifest-path third_party/agent_core/Cargo.toml --test engine_envelope_retry_test
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_parse_integration

./gradlew :core:agent:testDebugUnitTest --tests "*ModelTurnStreamGateTest"
./gradlew :core:agent:testDebugUnitTest --tests "*RustAgentEngineAdapterLeakTest"
./gradlew :feature:inference:testDebugUnitTest --tests "*BaseOpenAiSdkInferenceServiceTest"
./gradlew :feature:inference:testDebugUnitTest --tests "*CompleteSingleTurnRawSubmitTest"
./gradlew :feature:inference:testDebugUnitTest --tests "*LeakedToolRecoveryMutationTest"
./gradlew :core:domain:testDebugUnitTest --tests "*GenerateArtifactParamsTest"

./gradlew ktlintCheck
./gradlew detekt
```

---

## Scenario Index

| ID | Layer | Description |
|----|-------|-------------|
| CL-SG-01 | Stream gate | Chunked `<tool_call>` never emitted to UI prefix |
| CL-SG-02 | Stream gate | Partial `<tool` holdback emits preamble only |
| CL-SG-03 | Stream gate | Native tools flush preamble, suppress marker tail |
| CL-SG-04 | Stream gate | Weak JSON prose `{"name":"example"}` streams in full |
| CL-SG-05 | Stream gate | `allow_tool_call = false` passes through `<tool_call>` |
| CL-SG-06 | Stream gate | Plain text finish flushes `All done.` |
| CL-SG-07 | Stream gate | `buffered_text` retains full raw leak for engine |
| CL-SG-08 | Stream gate | `[TOOL_CALLS][` holdback emits `See ` only |
| CL-SG-09 | Stream gate | `call:append_blocks{` LiteRT leak never emitted |
| CL-TR-01 | Turn resolver | Empty native + valid `<tool_call>` → recovered `append_blocks` |
| CL-TR-02 | Turn resolver | Native `web_search` preferred; leaked DSML not executed |
| CL-TR-03 | Turn resolver | Empty native + spaced DSML → recovered `web_search` |
| CL-TR-04 | Turn resolver | JSON prose-only buffer flushes verbatim |
| CL-TR-05 | Turn resolver | `allow_tool_call = false` returns full buffer |
| CL-TR-06 | Turn resolver | `normalize_spaced_dsml` collapses `< \| DSML \|` |
| CL-TR-07 | Tool parse | `artifact_id` alias coerces to `artifactId` |
| CL-TR-08 | Tool parse | `is_complete` alias coerces to `isComplete` |
| CL-E-01 | Engine | Leaked `append_blocks` executes without persisting marker body |
| CL-E-02 | Engine | Native + leaked: only native `ExecuteTools` |
| CL-E-03 | Engine | Malformed leak → `CallModel` not `Finished` |
| CL-E-04 | Engine | Assistant content after leak is exactly `Done.` |
| CL-K-01 | Kotlin | `ModelTurnStreamGate` holdbacks DSML prefix to empty string |
| CL-K-02 | Kotlin | `bufferedText()` equals full raw provider stream |
| CL-K-03 | Kotlin | `RustAgentEngineAdapter` emits no `TextToken` for held-back DSML |
| CL-K-04 | Kotlin | `BaseOpenAiSdkInferenceService` does not call `ToolStreamGatePort.push` |
| CL-K-05 | Kotlin | `AnthropicInferenceServiceImpl` does not call `ToolStreamGatePort.push` |
| CL-K-06 | Kotlin | `completeSingleTurn` never calls `ToolEnvelopeParserPort.parse` |
| CL-MD-01 | Mutation | Resolver must not return recovered tools when native non-empty |
| CL-MD-02 | Mutation | `ToolStreamGateSession.kt` absent from `feature/inference` |
| CL-MD-03 | Mutation | Weak `\{"name"` regex absent from stream gate sources |

**Total scenarios: 30**

---

## Mutation-Defense Risk

| Risk | Scenario | Expected failure when lazy |
|------|----------|----------------------------|
| Engine merges leaked tools despite native SDK calls | CL-MD-01 | Test expects `ExecuteTools` length `1` with name `web_search` only; lazy merge executes `append_blocks` second tool → length `2` → **FAIL** |
| Inference layer reintroduces Kotlin holdback / dual policy | CL-MD-02 | Test expects file `ToolStreamGateSession.kt` missing; lazy re-add → **FAIL** |
| Weak JSON marker re-added to stream gate | CL-MD-03, CL-SG-04 | Prose buffer `Here is {"name":"demo"} ok` emits empty prefix with weak marker enabled → **FAIL** |

---

## Happy Path Scenarios

### CL-SG-01 — Chunked tool_call never emitted

```gherkin
Feature: Strong-sentinel stream holdback
  Scenario: CL-SG-01 chunked tool_call never emitted
    Given a ToolStreamGate with allow_tool_call true
    And raw text is exactly:
      """
      <tool_call>{"name":"append_blocks","arguments":{"artifactId":"art-1","blocks":[],"isComplete":false}}</tool_call>
      """
    When the raw text is pushed in chunks of 16 characters
    And finish is called with has_native_tool_calls false
    Then the concatenation of all PushResult.emitted_prefix values is ""
    And finish.trailing_flush is ""
    And finish.has_tool_marker is true
```

### CL-SG-03 — Native tools flush preamble only

```gherkin
  Scenario: CL-SG-03 native tools flush preamble
    Given a ToolStreamGate with allow_tool_call true
    When push receives exactly "Let me search."
    And finish is called with has_native_tool_calls true
    Then finish.trailing_flush is "Let me search."
    And finish.has_tool_marker is false
```

### CL-SG-09 — LiteRT call: syntax held back

```gherkin
  Scenario: CL-SG-09 call append_blocks held back
    Given a ToolStreamGate with allow_tool_call true
    And raw text begins with exactly:
      """
      call:append_blocks{artifactId:"art-1",blocks:[{"type":"paragraph","text":"x"}]}
      """
    When the raw text is pushed in chunks of 11 characters
    And finish is called with has_native_tool_calls false
    Then the concatenation of all PushResult.emitted_prefix values is ""
    And finish.has_tool_marker is true
```

### CL-TR-01 — Recovered append_blocks from tool_call envelope

```gherkin
Feature: Turn-end resolution
  Scenario: CL-TR-01 recover append_blocks without native tools
    Given buffered_text is exactly:
      """
      <tool_call>{"name":"append_blocks","arguments":{"artifactId":"art-99","blocks":[],"isComplete":false}}</tool_call>
      """
    And native_tool_calls is an empty list
    And allow_tool_call is true
    When resolve_model_turn_text runs
    Then recovered_tool_calls has length 1
    And recovered_tool_calls[0].name is "append_blocks"
    And recovered_tool_calls[0].arguments_json contains "\"artifactId\":\"art-99\""
    And visible_assistant_text is ""
```

### CL-TR-02 — Native tools preferred over leaked DSML

```gherkin
  Scenario: CL-TR-02 native preferred over leaked DSML
    Given buffered_text contains substring "<|DSML|tool_calls>"
    And native_tool_calls has length 1
    And native_tool_calls[0].name is "web_search"
    And allow_tool_call is true
    When resolve_model_turn_text runs
    Then recovered_tool_calls has length 0
    And the merged execution tool list for ingest is exactly the native_tool_calls list
```

### CL-TR-03 — Spaced DSML recovered

```gherkin
  Scenario: CL-TR-03 spaced DSML recovers web_search
    Given buffered_text is exactly:
      """
      < | DSML | tool_calls>< | DSML | invoke name="web_search">
      < | DSML | parameter name="query" string="true">kotlin</ | DSML | parameter>
      </ | DSML | invoke></ | DSML | tool_calls>
      """
    And native_tool_calls is an empty list
    When resolve_model_turn_text runs
    Then recovered_tool_calls has length 1
    And recovered_tool_calls[0].name is "web_search"
    And recovered_tool_calls[0].arguments_json contains "\"query\":\"kotlin\""
```

### CL-E-01 — Engine executes recovered append_blocks

```gherkin
Feature: Engine integration
  Scenario: CL-E-01 engine executes recovered append_blocks
    Given an AgentEngine session with allow_tool_call true
    When submit_model_response receives assistant_text:
      """
      <tool_call>{"name":"append_blocks","arguments":{"artifactId":"art-1","blocks":[],"isComplete":false}}</tool_call>
      """
    And tool_calls is an empty list
    And finish_reason is Stop
    Then next_action is ExecuteTools
    And ExecuteTools[0].name is "append_blocks"
    And last assistant message content is ""
```

### CL-K-03 — Adapter suppresses leaked TextToken

```gherkin
Feature: Kotlin agent bridge
  Scenario: CL-K-03 adapter suppresses DSML TextToken
    Given a FakeToolStreamGatePort with allowToolCall true
    And a host streams one PartialResponse chunk equal to "<|DSML|tool_calls>"
    When RustAgentEngineAdapter forwards onTextToken through ModelTurnStreamGate
    Then collected AgentLoopEvent.TextToken texts concatenate to ""
    And FakeToolStreamGatePort.bufferedText ends with "<|DSML|tool_calls>"
```

---

## Error and Edge Case Scenarios

### CL-SG-04 — JSON prose not held back (false-positive guard)

```gherkin
  Scenario: CL-SG-04 JSON prose streams through
    Given a ToolStreamGate with allow_tool_call true
    When push receives exactly "Here is {\"name\":\"demo\"} ok"
    And finish is called with has_native_tool_calls false
    Then push.emitted_prefix is "Here is {\"name\":\"demo\"} ok"
    And finish.trailing_flush is ""
    And finish.has_tool_marker is false
```

### CL-SG-05 — allow_tool_call false passthrough

```gherkin
  Scenario: CL-SG-05 allow false passthrough
    Given a ToolStreamGate with allow_tool_call false
    When push receives exactly "<tool_call>{\"name\":\"web_search\"}"
    Then push.emitted_prefix is "<tool_call>{\"name\":\"web_search\"}"
```

### CL-SG-02 — Partial angle holdback

```gherkin
  Scenario: CL-SG-02 partial angle holdback
    Given a ToolStreamGate with allow_tool_call true
    When push receives exactly "Hello <tool"
    Then push.emitted_prefix is "Hello "
    And buffered_text is "Hello <tool"
```

### CL-SG-08 — Mistral sentinel holdback

```gherkin
  Scenario: CL-SG-08 TOOL_CALLS holdback
    Given a ToolStreamGate with allow_tool_call true
    When push receives exactly "See [TOOL_CALLS]["
    Then push.emitted_prefix is "See "
    And finish.has_tool_marker is true
```

### CL-TR-04 — Prose-only buffer flush

```gherkin
  Scenario: CL-TR-04 prose JSON flushes verbatim
    Given buffered_text is exactly "Configure {\"name\":\"value\",\"enabled\":true} carefully."
    And native_tool_calls is an empty list
    And allow_tool_call is true
    When resolve_model_turn_text runs
    Then recovered_tool_calls has length 0
    And visible_assistant_text is "Configure {\"name\":\"value\",\"enabled\":true} carefully."
```

### CL-TR-05 — allow_tool_call false bypass

```gherkin
  Scenario: CL-TR-05 allow false bypass
    Given buffered_text is exactly "<tool_call>{\"name\":\"web_search\"}</tool_call>"
    And allow_tool_call is false
    When resolve_model_turn_text runs
    Then visible_assistant_text is "<tool_call>{\"name\":\"web_search\"}</tool_call>"
    And recovered_tool_calls has length 0
```

### CL-E-03 — Malformed leak retries model

```gherkin
  Scenario: CL-E-03 malformed DSML retries
    Given an AgentEngine session
    When submit_model_response receives assistant_text exactly:
      """
      <|DSML|tool_calls><|DSML|invoke name="web_search"></|DSML|invoke>
      """
    And tool_calls is an empty list
    Then next_action is CallModel
    And next_action is not Finished
    And tool role message content contains "Malformed"
```

### CL-E-04 — Visible prefix preserved, marker stripped

```gherkin
  Scenario: CL-E-04 preamble preserved
    Given an AgentEngine session
    When submit_model_response receives assistant_text:
      """
      Done.<tool_call>{"name":"web_search","arguments":{"query":"x"}}</tool_call>
      """
    And tool_calls is an empty list
    Then last assistant message content is "Done."
```

### CL-TR-07 — artifact_id alias coercion

```gherkin
  Scenario: CL-TR-07 artifact_id alias
    Given raw envelope text is exactly:
      """
      <tool_call>{"name":"append_blocks","arguments":{"artifact_id":"id-7","blocks":[],"is_complete":false}}</tool_call>
      """
    When parse_tool_calls runs on raw envelope text
    Then successes has length 1
    And successes[0].arguments_json contains "\"artifactId\":\"id-7\""
    And successes[0].arguments_json contains "\"isComplete\":false"
    And successes[0].repaired is true
```

### CL-TR-08 — Domain camelCase schema unchanged

```gherkin
  Scenario: CL-TR-08 AppendBlocksParams camelCase serialization
    Given AppendBlocksParams with artifactId "test-uuid-123" and isComplete true
    When serialized to JSON
    Then JSON key artifactId has string value "test-uuid-123"
    And JSON does not contain key artifact_id
```

---

## Mutation-Defense Scenarios

### CL-MD-01 — Native-only execution under merge regression

```gherkin
Feature: Mutation defense
  Scenario: CL-MD-01 native only execution
    Given an AgentEngine session
    When submit_model_response receives assistant_text containing "<|DSML|invoke name=\"append_blocks\">"
    And native tool_calls has length 1 with name "web_search" and id "call_native_1"
    Then next_action is ExecuteTools
    And ExecuteTools has length 1
    And ExecuteTools[0].name is "web_search"
    And ExecuteTools[0].call_id is "call_native_1"
```

### CL-MD-02 — No inference ToolStreamGateSession

```gherkin
  Scenario: CL-MD-02 ToolStreamGateSession absent in inference
    Given the directory feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference
    Then file ToolStreamGateSession.kt does not exist
    And no Kotlin source in that directory contains the string "class ToolStreamGateSession"
```

### CL-MD-03 — Weak name marker absent from stream gate

```gherkin
  Scenario: CL-MD-03 weak JSON regex absent from stream gate
    Given the file third_party/agent_core/src/tool_stream_gate/markers.rs
    Then file content does not contain the string r#"\{\s*["']name["']\s*:"#
    And first_strong_tool_marker_start on text "Tips: {\"name\":\"x\"}" returns None
```

---

## Kotlin Provider Scenarios

### CL-K-04 — BaseOpenAI does not gate locally

```gherkin
  Scenario: CL-K-04 BaseOpenAI raw stream
    Given a RecordingToolStreamGatePort injected into BaseOpenAiSdkInferenceService
    When completeSingleTurn streams three PartialResponse chunks "A", "B", "C"
    Then RecordingToolStreamGatePort.pushCalls has size 0
    And onTextToken received exactly "ABC"
```

### CL-K-05 — Anthropic does not gate locally

```gherkin
  Scenario: CL-K-05 Anthropic raw stream
    Given a RecordingToolStreamGatePort injected into AnthropicInferenceServiceImpl
    When completeSingleTurn emits one text chunk "Hi"
    Then RecordingToolStreamGatePort.pushCalls has size 0
```

### CL-K-06 — No Kotlin envelope parse on turn submit

```gherkin
  Scenario: CL-K-06 no parse on completeSingleTurn
    Given a RecordingToolEnvelopeParserPort
    When OpenRouter completeSingleTurn completes with DSML-only assistant stream
    Then RecordingToolEnvelopeParserPort.parseCalls has size 0
```

### CL-K-01 — ModelTurnStreamGate DSML holdback

```gherkin
  Scenario: CL-K-01 ModelTurnStreamGate DSML holdback
    Given ModelTurnStreamGate with allowToolCall true backed by RustToolStreamGate
    When onProviderTextChunk receives exactly "<|DSML|tool_calls>"
    Then emitted tokens concatenate to ""
    And bufferedText is exactly "<|DSML|tool_calls>"
```

### CL-K-02 — ModelTurnStreamGate preserves raw buffer

```gherkin
  Scenario: CL-K-02 bufferedText raw
    Given ModelTurnStreamGate with allowToolCall true
    When onProviderTextChunk receives "Hello " then "world"
    Then bufferedText is "Hello world"
```

---

## Scenario-to-Task Traceability

| Scenario IDs | Implementation tasks (spec.md) |
|--------------|------------------------------|
| CL-SG-01, CL-SG-02, CL-SG-03, CL-SG-08, CL-SG-09 | T2 |
| CL-SG-04, CL-SG-05, CL-SG-06, CL-SG-07 | T2, T5 |
| CL-TR-01, CL-TR-02, CL-TR-03, CL-TR-04, CL-TR-05 | T3 |
| CL-TR-06 | T1 |
| CL-TR-07, CL-TR-08 | T1, T5 |
| CL-E-01, CL-E-02, CL-E-03, CL-E-04 | T4, T5 |
| CL-K-01, CL-K-02 | T6 |
| CL-K-03 | T7, T11 |
| CL-K-04, CL-K-05, CL-K-06 | T8, T9 |
| CL-MD-01 | T4, T5 |
| CL-MD-02, CL-MD-03 | T10, T1 |
