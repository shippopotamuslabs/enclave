# TOOL-PARSE-002 — test_spec.md
# Test Specification: Rust Stream Gate + Engine Envelope Recovery

---

## Test Framework

| Layer | Framework | Location |
|-------|-----------|----------|
| Rust stream gate | `cargo test` (libtest) | `third_party/agent_core/tests/tool_stream_gate_test.rs` |
| Rust engine ingest | `cargo test` (libtest) | `third_party/agent_core/tests/engine_envelope_retry_test.rs` |
| Rust guardrails | `cargo test` (unit in `guardrails.rs`) | `third_party/agent_core/src/guardrails.rs` (`#[cfg(test)]`) |
| Kotlin stream gate contract | JUnit 5 + `kotlinx.coroutines.test.runTest` | `feature/inference/.../RustToolStreamGateContractTest.kt` |
| Kotlin submit contract | JUnit 5 + MockK/fakes | `feature/inference/.../CompleteSingleTurnRawSubmitTest.kt` |
| Mutation defense | Rust integration + Kotlin grep/architecture tests | `engine_envelope_retry_test.rs`, `CompleteSingleTurnRawSubmitTest.kt` |

### Run Commands

```bash
# Rust — all TOOL-PARSE-002 tests
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_stream_gate_test
cargo test --manifest-path third_party/agent_core/Cargo.toml --test engine_envelope_retry_test
cargo test --manifest-path third_party/agent_core/Cargo.toml

# Kotlin — stream gate JNI contract + submit wiring
./gradlew :feature:inference:testDebugUnitTest --tests "*.RustToolStreamGateContractTest"
./gradlew :feature:inference:testDebugUnitTest --tests "*.CompleteSingleTurnRawSubmitTest"
./gradlew :core:agent:testDebugUnitTest

# Broader regression (post Phase D delete)
./gradlew :feature:inference:testDebugUnitTest
./gradlew :core:domain:testDebugUnitTest

# CI-aligned (before merge)
./gradlew detekt
./gradlew ktlintCheck
./gradlew testDebugUnitTest
cargo test --manifest-path third_party/agent_core/Cargo.toml
```

---

## Scenario Index

| ID | Layer | Description |
|----|-------|-------------|
| SG-01 | Stream gate | Chunked DSML never emitted to UI prefix |
| SG-02 | Stream gate | Partial `<` holdback in 64-char tail |
| SG-03 | Stream gate | Native tools flush preamble only |
| SG-04 | Stream gate | Native tools + DSML in buffer — DSML not flushed |
| SG-05 | Stream gate | `allow_tool_call = false` passes through full stream |
| SG-06 | Stream gate | Plain text — finish flushes entire buffer |
| SG-07 | Stream gate | `buffered_text` retains full raw DSML for engine |
| SG-08 | Stream gate | Mistral `[TOOL_CALLS]` marker holdback |
| E-01 | Engine | Malformed DSML → synthetic error → `CallModel` |
| E-02 | Engine | Malformed DSML error JSON shape |
| E-03 | Engine | Valid leaked DSML → reified tool → `ExecuteTools` |
| E-04 | Engine | Persisted assistant content strips DSML |
| E-05 | Engine | Empty native tools + `Stop` + malformed DSML does not `Finished` |
| E-06 | Engine | Third envelope failure → `allow_tool_call = false` |
| E-07 | Engine | Successful reified tool resets envelope streak |
| K-01 | Kotlin | `RustToolStreamGate` chunked DSML parity with SG-01 |
| K-02 | Kotlin | Stream gate `bufferedText` equals full raw input |
| K-03 | Kotlin | `completeSingleTurn` does not call `ToolEnvelopeParserPort.parse` |
| K-04 | Kotlin | `completeSingleTurn` returns native tools only |
| K-05 | Kotlin | `sendPrompt` streaming uses `ToolStreamGatePort` not `AgentTurnTextGate` |
| MD-01 | Mutation | Kotlin agent-loop path never calls `parse` on assistant text |
| MD-02 | Mutation | Engine must schedule `CallModel` after envelope failure (not terminal) |
| MD-03 | Mutation | `AgentTurnTextGate` source file absent |
| MD-04 | Mutation | No `toInferenceException` in `feature/inference` production sources |

**Total scenarios: 24**

---

## Stream Gate Scenarios (SG-XX)

### SG-01 — Chunked DSML never emitted

```gherkin
Feature: Tool stream gate holdback
  Scenario: SG-01 chunked DSML never emitted
    Given a ToolStreamGate with allow_tool_call true
    And raw text is exactly:
      """
      <|DSML|tool_calls><|DSML|invoke name="web_search">
      <|DSML|parameter name="query" string="true">kotlin</|DSML|parameter>
      </|DSML|invoke></|DSML|tool_calls>
      """
    When the raw text is pushed in chunks of 17 characters
    And finish is called with has_native_tool_calls false
    Then the concatenation of all PushResult.emitted_prefix values is ""
    And finish.trailing_flush is ""
    And finish.has_tool_marker is true
```

### SG-02 — Partial angle-bracket holdback

```gherkin
  Scenario: SG-02 partial angle bracket holdback
    Given a ToolStreamGate with allow_tool_call true
    When push is called with chunk "Hello <tool"
    Then PushResult.emitted_prefix is "Hello "
    And buffered_text is "Hello <tool"
```

### SG-03 — Native tools flush preamble

```gherkin
  Scenario: SG-03 native tools flush preamble
    Given a ToolStreamGate with allow_tool_call true
    When push is called with "Let me search."
    And finish is called with has_native_tool_calls true
    Then finish.trailing_flush is "Let me search."
    And finish.has_tool_marker is false
```

### SG-04 — Native tools with DSML in buffer

```gherkin
  Scenario: SG-04 native tools do not flush DSML tail
    Given a ToolStreamGate with allow_tool_call true
    When push is called with "Summary.<|DSML|tool_calls></|DSML|tool_calls>"
    And finish is called with has_native_tool_calls true
    Then finish.trailing_flush is ""
    And finish.has_tool_marker is true
```

### SG-05 — Tools disabled

```gherkin
  Scenario: SG-05 allow_tool_call false
    Given a ToolStreamGate with allow_tool_call false
    When push is called with "<tool_call>{\"name\":\"web_search\"}"
    Then PushResult.emitted_prefix is "<tool_call>{\"name\":\"web_search\"}"
```

### SG-06 — Plain text full flush

```gherkin
  Scenario: SG-06 plain text finish flush
    Given a ToolStreamGate with allow_tool_call true
    When push is called with "All done."
    And finish is called with has_native_tool_calls false
    Then finish.trailing_flush is "All done."
    And finish.has_tool_marker is false
```

### SG-07 — Buffered text retains DSML

```gherkin
  Scenario: SG-07 buffered_text is raw
    Given a ToolStreamGate with allow_tool_call true
    When push is called with "<|DSML|tool_calls></|DSML|tool_calls>"
    Then buffered_text is "<|DSML|tool_calls></|DSML|tool_calls>"
```

### SG-08 — Mistral marker holdback

```gherkin
  Scenario: SG-08 Mistral TOOL_CALLS holdback
    Given a ToolStreamGate with allow_tool_call true
    When push is called with "See [TOOL_CALLS]["
    Then PushResult.emitted_prefix is "See "
    And finish.has_tool_marker is true after finish with has_native_tool_calls false
```

---

## Engine Envelope Recovery Scenarios (E-XX)

Harness: in-process `AgentEngine` with `RecordingExecutor`, `PanicTransport`, default `AgentConfig`
(`tool_retry_limit = 3`). Submit via `submit_model_response`.

### E-01 — Malformed DSML schedules CallModel

```gherkin
Feature: Engine envelope ingest recovery
  Scenario: E-01 malformed DSML retries model
    Given a new agent session with tool_retry_limit 3
    And next_action returns CallModel
    When submit_model_response is called with:
      | assistant_text | "<|DSML|tool_calls><|DSML|invoke name=\"web_search\"></|DSML|invoke>" |
      | tool_calls     | [] |
      | finish_reason  | stop |
    Then next_action returns CallModel
    And next_action does not return Finished
    And session contains one Tool role message
```

### E-02 — Synthetic error JSON shape

```gherkin
  Scenario: E-02 envelope failure JSON shape
    Given the same malformed DSML response as E-01
    When submit_model_response completes
    Then the Tool role message content parses as JSON
    And JSON field "error" contains "Malformed"
    And JSON field "error" contains "DSML"
    And JSON array "available_tools" contains "web_search"
```

### E-03 — Valid DSML reified to ExecuteTools

```gherkin
  Scenario: E-03 valid leaked DSML executes tool
    Given a new agent session
  When submit_model_response is called with assistant_text containing a valid DSML
    web_search invoke with query "rust" and empty native tool_calls
    Then next_action returns ExecuteTools
    And ExecuteTools has count 1
    And ExecuteTools[0].name is "web_search"
    And ExecuteTools[0].arguments_json contains "\"query\":\"rust\""
```

### E-04 — Assistant content stripped

```gherkin
  Scenario: E-04 persisted assistant strips DSML
    Given E-03 valid DSML with visible prefix "Searching now."
    When submit_model_response completes
    Then the last Assistant message content is "Searching now."
    And the last Assistant message content does not contain "DSML"
```

### E-05 — Stop with empty tools does not finish

```gherkin
  Scenario: E-05 stop plus malformed envelope not terminal
    Given a new agent session
    When submit_model_response has finish_reason stop, tool_calls [], and malformed DSML body
    Then next_action is CallModel
    And terminal_action is None
```

### E-06 — Third failure disables allow_tool_call

```gherkin
  Scenario: E-06 envelope streak disables tools
    Given tool_retry_limit is 3
    And three consecutive submit_model_response calls each with malformed DSML and stop
    When next_action returns CallModel for the fourth time
    Then ModelRequest.allow_tool_call is false
```

### E-07 — Success resets streak

```gherkin
  Scenario: E-07 success resets envelope streak
    Given two malformed DSML submit_model_response calls
    And one valid DSML submit that ExecuteTools accepts
    And submit_tool_results returns non-soft success for web_search
    When next_action returns CallModel again
    Then ModelRequest.allow_tool_call is true
```

**Literal assertions for E-06 (Rust):**

```text
assert_eq!(request.allow_tool_call, false);
assert_eq!(guardrails.consecutive_envelope_parse_failures, 3);
```

**Literal assertions for E-02 (Rust):**

```text
assert!(tool_msg.content.contains("\"error\""));
assert!(tool_msg.content.contains("DSML"));
```

---

## Kotlin Wiring Scenarios (K-XX)

### K-01 — JNI stream gate chunked DSML

```gherkin
Feature: Kotlin Rust stream gate contract
  Scenario: K-01 chunked DSML never emitted via port
    Given RustToolStreamGate loaded via existing agent JNI bootstrap
    And allowToolCall true
    When the DSML sample from SG-01 is pushed in chunks of 17 via ToolStreamGatePort
    Then total emitted prefix length is 0
```

### K-02 — Buffered text raw

```gherkin
  Scenario: K-02 bufferedText retains DSML
    When the DSML sample from SG-01 is fully pushed
    Then bufferedText equals the full DSML sample string
```

### K-03 — completeSingleTurn does not parse

```gherkin
  Scenario: K-03 no parse on completeSingleTurn
    Given FakeToolEnvelopeParserPort recording parse invocations
    And a test double inference service using ToolStreamGatePort
    When completeSingleTurn completes with DSML-only model output
    Then FakeToolEnvelopeParserPort.parseCallCount is 0
```

### K-04 — Native tools only in result

```gherkin
  Scenario: K-04 native tools only
    Given provider returns one native AgentRawToolCall named "web_search"
    And streamed text also contains valid DSML for append_blocks
    When completeSingleTurn returns
    Then result.toolCalls.size is 1
    And result.toolCalls[0].name is "web_search"
```

### K-05 — sendPrompt uses stream gate port

```gherkin
  Scenario: K-05 sendPrompt wiring
    Given FakeToolStreamGatePort installed in test service
    When sendPrompt receives PartialResponse chunks
    Then FakeToolStreamGatePort.pushCallCount is greater than 0
```

---

## Mutation-Defense Scenarios (MD-XX)

These scenarios **must fail** on lazy implementations (Kotlin-only retry, Kotlin-only parse, file not deleted).

### MD-01 — No Kotlin parse on assistant turn text

```gherkin
Feature: Mutation defense
  Scenario: MD-01 agent loop never parses assistant text in Kotlin
    Given FakeToolEnvelopeParserPort with parse throwing AssertionError("MD-01")
    And RustLoopInferenceService or BaseOpenAiSdkInferenceService completeSingleTurn path
    When completeSingleTurn runs with DSML in streamed output
    Then no AssertionError is thrown
    And FakeToolEnvelopeParserPort.parseCallCount is 0
```

### MD-02 — Engine CallModel after failure (not Kotlin retry)

```gherkin
  Scenario: MD-02 recovery only in engine
    Given RecordingExecutor and in-process AgentEngine only
    When submit_model_response with malformed DSML
    Then next_action is CallModel before any host completeModelTurn
    And guardrails.consecutive_envelope_parse_failures is 1
```

**MD-02 negative check:** If implementation only catches Kotlin throws and retries in
`RustLoopInferenceService` without engine ingest, `consecutive_envelope_parse_failures` stays `0` — test fails.

### MD-03 — AgentTurnTextGate deleted

```gherkin
  Scenario: MD-03 AgentTurnTextGate absent
    Then file feature/inference/src/main/kotlin/.../AgentTurnTextGate.kt does not exist
    And ripgrep for "class AgentTurnTextGate" in feature/inference returns 0 matches
```

### MD-04 — No throw mapper in inference

```gherkin
  Scenario: MD-04 ToolEnvelopeFailureMapper absent
    Then file feature/inference/.../ToolEnvelopeFailureMapper.kt does not exist
    And ripgrep for "toInferenceException" under feature/inference/src/main returns 0 matches
```

---

## Happy Path Summary

| Flow | Scenarios |
|------|-----------|
| Stream DSML hidden from chat | SG-01, SG-08, K-01 |
| Raw buffer submitted to engine | SG-07, K-02, K-04 |
| Valid leak → tool execution | E-03, E-04 |
| Invalid leak → model retry | E-01, E-02, E-05 |

## Error Path Summary

| Flow | Scenarios |
|------|-----------|
| Malformed envelope synthetic error | E-01, E-02 |
| Retry limit | E-06 |
| Streak reset | E-07 |

## Edge Path Summary

| Flow | Scenarios |
|------|-----------|
| Partial `<` holdback | SG-02 |
| Native + leaked text | SG-03, SG-04, K-04 |
| Tools disabled | SG-05 |
| Plain prose | SG-06 |

---

## Requirement Traceability

| Requirement | Tasks (spec.md) | Scenarios |
|-------------|-----------------|-----------|
| Rust `tool_stream_gate` | T-A1, T-A2 | SG-01 … SG-08 |
| Engine ingest + retry | T-A5, T-A6 | E-01 … E-07 |
| Guardrail counter | T-A4 | E-06, E-07 |
| UniFFI | T-B1, T-B2 | K-01, K-02 |
| Kotlin migration | T-C1 … T-C5 | K-03 … K-05 |
| Delete legacy | T-D1 … T-D4 | MD-03, MD-04 |
| No Kotlin parse/retry | T-C2, T-C3 | MD-01, MD-02, MD-04 |

---

## Self-Review (test author)

- [x] 24 scenarios with stable IDs
- [x] Gherkin + literal `Then` values for stream gate and engine JSON
- [x] Mutation-defense scenarios target real lazy failures
- [x] Engine retry and `allow_tool_call false` at limit covered
- [x] Run commands match AGENTS.md toolchains
- [x] No placeholder scenarios
