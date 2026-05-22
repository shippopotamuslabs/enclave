# Test Spec: Executed Tool Call Replay

**Ticket ID:** `FEAT-executed-tool-call-replay`  
**Companion spec:** `plans/FEAT-executed-tool-call-replay/spec.md`

---

## 1. Test Framework and Run Commands

| Concern | Command |
|---------|---------|
| Rust integration tests (primary) | `cd third_party/agent_core && cargo test --test executed_tool_call_replay_test` |
| Full `agent_core` regression | `cd third_party/agent_core && cargo test` |
| Rust unit tests in `engine` module (if helper tests colocated) | `cd third_party/agent_core && cargo test provider_facing` |
| Kotlin inference replay regression | `./gradlew :feature:inference:testDebugUnitTest --tests "com.browntowndev.pocketcrew.feature.inference.BaseOpenAiSdkInferenceServiceTest" --no-daemon` |
| Android modules compile (if bindings rebuilt) | `./gradlew :core:agent:assembleDebug :feature:inference:assembleDebug` |

**Frameworks:**

- Rust: `cargo test`, `#[test]` in `tests/executed_tool_call_replay_test.rs`, helpers `AgentEngine` + `SessionHandle::integration_test_snapshot()`.
- Kotlin: JUnit 5 + `runTest`, capture `ChatCompletionCreateParams` via test double (existing `BaseOpenAiSdkInferenceServiceTest` pattern).

**Shared test harness (Rust):** Mirror `engine_envelope_retry_test.rs` — `PanicTransport`, `RecordingExecutor`, `io_large()` with `context_window_tokens: 200_000`, `new_session()` with single user message `"hi"`.

**Snapshot helpers (implement in test file):**

```rust
fn last_assistant_message(snapshot: &SessionTestSnapshot) -> &AgentMessage;
fn raw_tool_calls_array(snapshot: &SessionTestSnapshot) -> &[serde_json::Value];
```

---

## 2. Scenario Index

| ID | Category | Title |
|----|----------|-------|
| ETR-R-01 | Happy | Fence-wrapped arguments canonicalized in `raw_tool_calls` |
| ETR-R-02 | Happy | Fuzzy tool name `web_seach` stored as `web_search` in metadata |
| ETR-R-03 | Happy | `append_blocks` `complete` alias stored as `isComplete` in metadata |
| ETR-R-04 | Happy | Valid JSON tool call metadata matches `ExecuteTools` record |
| ETR-R-05 | Error/edge | Reject-only turn persists empty `raw_tool_calls` |
| ETR-R-06 | Error/edge | Partial parallel: one reject, one repair — metadata length 1 |
| ETR-R-07 | Error/edge | Invalid JSON arguments reject — empty `raw_tool_calls`, no `ExecuteTools` |
| ETR-R-08 | Error/edge | Unknown tool reject — empty `raw_tool_calls` |
| ETR-R-09 | Error/edge | Envelope ingest co-metadata preserved after patch |
| ETR-K-01 | Happy | Kotlin replays canonical `raw_tool_calls` to OpenAI assistant tool_calls |
| ETR-K-02 | Happy | Kotlin replays fence-free `arguments_json` verbatim to SDK |
| ETR-M-01 | Mutation defense | Rust snapshot must canonicalize; metadata equals `ExecuteTools` args |
| ETR-M-02 | Mutation defense | Kotlin-only fence strip cannot satisfy Rust snapshot test |
| ETR-M-03 | Mutation defense | Metadata must not retain pre-repair typo name `web_seach` |

---

## 3. Happy Path Scenarios

### ETR-R-01 — Fence-wrapped arguments canonicalized in `raw_tool_calls`

**File:** `third_party/agent_core/tests/executed_tool_call_replay_test.rs`

```gherkin
Scenario: ETR-R-01 — post-repair metadata strips markdown fences from arguments_json
  Given a new agent session and CallModel has been issued
  When submit_model_response is called with tool_calls containing exactly one RawToolCall:
    | field            | value                                      |
    | id               | call-fence                                 |
    | name             | web_search                                 |
    | arguments_json   | ```json\n{"query":"rust"}\n```             |
    | finish_reason    | tool_use                                   |
  And next_action returns ExecuteTools with one call
  And integration_test_snapshot is taken
  Then the last Assistant message metadata raw_tool_calls is a JSON array of length 1
  And raw_tool_calls[0].name equals "web_search"
  And raw_tool_calls[0].id equals "call-fence"
  And raw_tool_calls[0].arguments_json equals "{\"query\":\"rust\"}"
  And raw_tool_calls[0].arguments_json does not contain "```"
```

---

### ETR-R-02 — Fuzzy tool name `web_seach` stored as `web_search` in metadata

**File:** `executed_tool_call_replay_test.rs`

```gherkin
Scenario: ETR-R-02 — fuzzy-repaired name is persisted for provider replay
  Given a new agent session and CallModel has been issued
  When submit_model_response is called with tool_calls:
    | id               | c1                                         |
    | name             | web_seach                                  |
    | arguments_json   | {"query":"x"}                              |
    | finish_reason    | tool_use                                   |
  And next_action returns ExecuteTools
  And integration_test_snapshot is taken
  Then raw_tool_calls[0].name equals "web_search"
  And raw_tool_calls[0].name does not equal "web_seach"
  And ExecuteTools.calls[0].name equals "web_search"
```

---

### ETR-R-03 — `append_blocks` `complete` alias stored as `isComplete` in metadata

**File:** `executed_tool_call_replay_test.rs`

```gherkin
Scenario: ETR-R-03 — append_blocks argument alias repair reflected in metadata
  Given a new agent session and CallModel has been issued
  When submit_model_response is called with tool_calls:
    | id               | c-ab                                       |
    | name             | append_blocks                              |
    | arguments_json   | {"artifactId":"a","blocks":[],"complete":true} |
    | finish_reason    | tool_use                                   |
  And next_action returns ExecuteTools
  And integration_test_snapshot is taken
  Then raw_tool_calls[0].name equals "append_blocks"
  And raw_tool_calls[0].arguments_json contains "\"isComplete\":true"
  And raw_tool_calls[0].arguments_json does not contain "\"complete\":true"
```

---

### ETR-R-04 — Valid JSON tool call metadata matches `ExecuteTools` record

**File:** `executed_tool_call_replay_test.rs`

```gherkin
Scenario: ETR-R-04 — metadata replay matches execution record for clean model output
  Given a new agent session and CallModel has been issued
  When submit_model_response is called with tool_calls:
    | id               | call-1                                     |
    | name             | web_search                                 |
    | arguments_json   | {"query":"finish boundary"}                |
    | finish_reason    | tool_use                                   |
  And next_action returns ExecuteTools with calls[0]
  And integration_test_snapshot is taken
  Then raw_tool_calls[0].id equals "call-1"
  And raw_tool_calls[0].name equals "web_search"
  And raw_tool_calls[0].arguments_json equals "{\"query\":\"finish boundary\"}"
  And calls[0].call_id equals "call-1"
  And calls[0].name equals "web_search"
  And calls[0].arguments_json equals "{\"query\":\"finish boundary\"}"
```

---

### ETR-K-01 — Kotlin replays canonical `raw_tool_calls` to OpenAI assistant tool_calls

**File:** `feature/inference/src/test/kotlin/.../BaseOpenAiSdkInferenceServiceTest.kt`

```gherkin
Scenario: ETR-K-01 — completeSingleTurn maps repaired metadata to SDK tool calls
  Given a test BaseOpenAiSdkInferenceService that captures ChatCompletionCreateParams
  When completeSingleTurn is invoked with one AgentLoopMessage:
    | id            | assistant-etr-01                                      |
    | role          | ASSISTANT                                             |
    | content       | I'll search.                                          |
    | metadataJson  | {"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"query\":\"rust\"}"}]} |
  Then captured messages has size 1
  And messages[0] is assistant
  And messages[0].toolCalls().get() has size 1
  And toolCalls[0].asFunction().function().name() equals "web_search"
  And toolCalls[0].asFunction().id() equals "call_1"
  And toolCalls[0].asFunction().function().arguments() equals "{\"query\":\"rust\"}"
```

---

### ETR-K-02 — Kotlin replays fence-free `arguments_json` verbatim to SDK

**File:** `BaseOpenAiSdkInferenceServiceTest.kt`

```gherkin
Scenario: ETR-K-02 — Kotlin does not mutate arguments_json during replay
  Given the same capture service as ETR-K-01
  When completeSingleTurn is invoked with metadataJson raw_tool_calls arguments_json exactly:
    "{\"artifactId\":\"art-1\",\"blocks\":[],\"isComplete\":false}"
  Then toolCalls[0].asFunction().function().arguments() equals
    "{\"artifactId\":\"art-1\",\"blocks\":[],\"isComplete\":false}"
```

---

## 4. Error and Edge Case Scenarios

### ETR-R-05 — Reject-only turn persists empty `raw_tool_calls`

**File:** `executed_tool_call_replay_test.rs`

```gherkin
Scenario: ETR-R-05 — unknown tool reject clears provider-facing tool calls
  Given a new agent session and CallModel has been issued
  When submit_model_response is called with tool_calls:
    | name             | totally_unknown_tool_xyz                   |
    | arguments_json   | {}                                         |
    | finish_reason    | tool_use                                   |
  And next_action does not return ExecuteTools
  And integration_test_snapshot is taken
  Then the last Assistant message metadata raw_tool_calls is a JSON array of length 0
  And session contains at least one Tool role message with content containing "error"
```

---

### ETR-R-06 — Partial parallel: one reject, one repair — metadata length 1

**File:** `executed_tool_call_replay_test.rs`

```gherkin
Scenario: ETR-R-06 — only repaired calls appear in metadata for mixed parallel turn
  Given a new agent session and CallModel has been issued
  When submit_model_response is called with two tool_calls:
    | id   | name                      | arguments_json      |
    | c-ok | web_search                | {"query":"ok"}      |
    | c-bad| totally_unknown_tool_xyz  | {}                  |
    | finish_reason | tool_use       |                     |
  And next_action returns ExecuteTools with exactly 1 call
  And integration_test_snapshot is taken
  Then raw_tool_calls has length 1
  And raw_tool_calls[0].id equals "c-ok"
  And raw_tool_calls[0].name equals "web_search"
  And ExecuteTools.calls[0].call_id equals "c-ok"
  And a Tool message exists with id containing "tool-err-c-bad"
```

---

### ETR-R-07 — Invalid JSON arguments reject — empty `raw_tool_calls`, no `ExecuteTools`

**File:** `executed_tool_call_replay_test.rs`

```gherkin
Scenario: ETR-R-07 — unparseable arguments do not enter provider replay metadata
  Given a new agent session and CallModel has been issued
  When submit_model_response is called with tool_calls:
    | name             | web_search                                 |
    | arguments_json   | {not valid json                            |
    | finish_reason    | tool_use                                   |
  And next_action does not return ExecuteTools
  And integration_test_snapshot is taken
  Then raw_tool_calls has length 0
  And ExecuteTools was not returned
```

---

### ETR-R-08 — Unknown tool reject — empty `raw_tool_calls`

**File:** `executed_tool_call_replay_test.rs`

```gherkin
Scenario: ETR-R-08 — reject path matches ETR-R-05 for explicit unknown tool name
  Given a new agent session and CallModel has been issued
  When submit_model_response is called with name zzzzz_no_real_match_yyyyy and arguments_json {}
  And next_action is CallModel or Idle but not ExecuteTools
  Then raw_tool_calls has length 0
```

*(Note: ETR-R-08 duplicates ETR-R-05 needle; keep both IDs — ETR-R-05 uses `totally_unknown_tool_xyz`, ETR-R-08 uses fuzzy-below-cutoff needle from `repair/mod.rs` tests.)*

**ETR-R-08 literal name:** `zzzzz_no_real_match_yyyyy`

---

### ETR-R-09 — Envelope ingest co-metadata preserved after patch

**File:** `executed_tool_call_replay_test.rs`

```gherkin
Scenario: ETR-R-09 — patching raw_tool_calls does not remove envelope_ingest key
  Given a new agent session and CallModel has been issued
  When submit_model_response is called with assistant_text containing a valid DSML web_search block
    And tool_calls is empty
    And finish_reason is stop
  And next_action returns ExecuteTools for recovered web_search
  And integration_test_snapshot is taken
  Then the last Assistant metadata contains key envelope_ingest with value "reified"
  And raw_tool_calls has length 1
  And raw_tool_calls[0].name equals "web_search"
  And raw_tool_calls[0].arguments_json contains "\"query\":\"rust\""
```

**DSML assistant_text literal:** Use `VALID_DSML_WEB_SEARCH` constant from `engine_envelope_retry_test.rs` (query parameter value `rust`).

---

## 5. Mutation Defense Scenarios

### ETR-M-01 — Rust snapshot must canonicalize; metadata equals `ExecuteTools` args

**File:** `executed_tool_call_replay_test.rs`

```gherkin
Scenario: ETR-M-01 — provider metadata written in Rust matches execution args after fence repair
  Given submit_model_response with fence-wrapped web_search arguments as in ETR-R-01
  When RecordingExecutor captured calls are read
  And integration_test_snapshot raw_tool_calls are read
  Then captured.calls[0].arguments_json equals raw_tool_calls[0].arguments_json
  And both equal "{\"query\":\"rust\"}"
  And neither string contains "```"
```

**Lazy implementation caught:** Repair fixes `ExecuteTools` but metadata still stores pre-repair fence string → `arguments_json` mismatch fails.

---

### ETR-M-02 — Kotlin-only fence strip cannot satisfy Rust snapshot test

**File:** `executed_tool_call_replay_test.rs` (required) + `BaseOpenAiSdkInferenceServiceTest.kt` (documented)

```gherkin
Scenario: ETR-M-02 — Rust integration_test_snapshot is the gate for metadata canonicalization
  Given the codebase state before T-2/T-3 engine patch (red phase) OR a branch that only strips fences in Kotlin toOpenAiChatParam
  When ETR-R-01 submit_model_response runs
  Then raw_tool_calls[0].arguments_json still contains "```" in the Rust snapshot test
  And ETR-M-01 fails
  And no Kotlin unit test alone can make ETR-R-01 pass without modifying engine/mod.rs metadata write
```

**Enforcement:** CI must run `cargo test --test executed_tool_call_replay_test` before merge. Kotlin `ETR-K-*` tests are insufficient alone for merge gate.

---

### ETR-M-03 — Metadata must not retain pre-repair typo name `web_seach`

**File:** `executed_tool_call_replay_test.rs`

```gherkin
Scenario: ETR-M-03 — metadata name must not mirror raw model typo after fuzzy repair
  Given submit_model_response with name web_seach as in ETR-R-02
  When integration_test_snapshot is taken
  Then serde_json::to_string(metadata) does not contain "web_seach"
  And raw_tool_calls[0].name equals "web_search"
```

**Lazy implementation caught:** Only normalizes name in `ExecuteTools` path but serializes `merged_tool_calls` into metadata.

---

## 6. Test File and Task Mapping

| Scenario IDs | Implementation artifact |
|--------------|-------------------------|
| ETR-R-01 … ETR-R-09, ETR-M-01 … ETR-M-03 | `tests/executed_tool_call_replay_test.rs` + `Cargo.toml` `[[test]]` |
| ETR-K-01, ETR-K-02 | `BaseOpenAiSdkInferenceServiceTest.kt` new `@Test` methods |
| T-1 … T-4 (spec) | Production Rust — must turn ETR-R-* green |
| T-6 | Adds Rust tests |
| T-7 | Adds Kotlin tests |

---

## 7. Red-Green Notes (Synthitect TDD)

1. **Red:** Add `executed_tool_call_replay_test.rs` with ETR-R-01 and ETR-M-01 first; confirm failure on current `main` (metadata contains fences).
2. **Green:** Implement T-1 … T-4; ETR-R-01, ETR-M-01, ETR-M-03 pass.
3. **Expand:** Add ETR-R-05 … ETR-R-09, ETR-R-06, ETR-K-01, ETR-K-02.
4. **Regression:** `cargo test` full crate; existing `fuzzy_repair_web_seach` engine unit test remains green.

---

## 8. Self-Review Checklist

- [x] Every behavior has a scenario ID (happy, error, mutation)
- [x] Literal expected values specified (JSON strings, tool names, array lengths)
- [x] Exact `cargo` and `gradlew` commands
- [x] Mutation defense targets Kotlin-only fix
- [x] No placeholders
- [x] Scenario IDs referenced from spec.md tasks T-1 … T-10
