# fix-artifact-agent-loop — test_spec.md
# Test Specification: Fix Artifact Generation Agent Loop Failures

---

## Test framework and run commands

| Layer | Framework | Location |
|-------|-----------|----------|
| Rust canonicalization | `cargo test` | `third_party/agent_core/src/canonicalize/`, `third_party/agent_core/tests/tool_call_canonicalize_test.rs` |
| Rust repair (wrapper) | `cargo test` | `third_party/agent_core/src/repair/mod.rs` |
| Rust tool_parse / envelope | `cargo test` | `third_party/agent_core/tests/tool_parse_integration.rs` |
| Rust stream gate | `cargo test` | `third_party/agent_core/tests/tool_stream_gate_test.rs` |
| Kotlin agent bridge | JUnit 5 + `runTest` | `core/agent/src/test/kotlin/.../ModelTurnStreamGate*.kt` |
| Kotlin data / artifact | JUnit 5 + fakes preferred; MockK only for impractical boundaries | `ChatRepositoryImplArtifactPersistTest.kt`, `ArtifactToolExecutorImplTest.kt` |
| Kotlin inference / chat | JUnit 5 | `RustLoopInferenceService*`, `ChatViewModelTest.kt` |

### Run commands

```bash
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_call_canonicalize_test
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_parse_integration
cargo test --manifest-path third_party/agent_core/Cargo.toml --test tool_stream_gate_test
cargo test --manifest-path third_party/agent_core/Cargo.toml canonicalize::
cargo test --manifest-path third_party/agent_core/Cargo.toml repair::

./gradlew :core:agent:testDebugUnitTest --tests "*ModelTurnStreamGate*"
./gradlew :core:data:testDebugUnitTest --tests "*ChatRepositoryImplArtifactPersist*"
./gradlew :core:data:testDebugUnitTest --tests "*ArtifactToolExecutor*"
./gradlew :feature:inference:testDebugUnitTest --tests "*RustLoop*"
./gradlew :feature:chat:testDebugUnitTest --tests "*ChatViewModelTest*onConfirmArtifactExport*"
./gradlew :core:domain:testDebugUnitTest --tests "*AppendBlocksToolResultParser*"

./gradlew ktlintCheck
./gradlew detekt
```

---

## Scenario index

### Canonicalization pipeline (happy + error + policy)

| ID | Type | Description |
|----|------|-------------|
| FAAL-CP-01 | Happy | `canonicalize_raw_arguments` / `canonicalize_arguments_value` coerces stringified `blocks` to array |
| FAAL-CP-02 | Happy | `ExecutionStrict` rejects unknown tool; `ProviderIngress` does not reject |
| FAAL-CP-03 | Happy | `coerce_provider_arguments` and `canonicalize_tool_call(ExecutionStrict)` produce identical `arguments_json` |
| FAAL-CP-04 | Happy | `canonicalize_arguments_value` is idempotent (two passes → same JSON) |
| FAAL-CP-05 | Happy | `parse_tool_calls` envelope path uses shared canonicalizer for `append_blocks` |

### Repair / execution gate (happy + error)

| ID | Type | Description |
|----|------|-------------|
| FAAL-RC-01 | Happy | `ToolRepairPipeline::repair` coerces stringified `blocks` via canonicalizer |
| FAAL-RC-02 | Error | Unparseable `blocks` string → `RepairOutcome::Reject` under `ExecutionStrict` |
| FAAL-RC-03 | Happy | Ingress FFI and execution gate produce identical canonical `arguments_json` |
| FAAL-RC-04 | Happy | Canonicalized args pass `validateAppendBlocksArgs` in `ArtifactToolExecutor` |

### Stream gate / UI preamble

| ID | Type | Description |
|----|------|-------------|
| FAAL-SG-01 | Happy | Native tools + partial `<tool` → user-visible text is `"Let me try with a simpler "` |
| FAAL-SG-02 | Happy | Native tools + `<tool_call>` tail → preamble `"Summary."` visible, marker not |
| FAAL-SG-03 | Happy | Kotlin gate emits preamble tokens before turn end |
| FAAL-SG-04 | Happy | `ModelTurnStreamGate.finish(true)` invokes emit with non-empty flush |
| FAAL-SG-05 | Edge | Leak-only turn (no native tools) still holds back `<tool_call>` body |

### Artifact persistence / export

| ID | Type | Description |
|----|------|-------------|
| FAAL-AR-01 | Happy | `persistAllMessageData` with blank artifact does not erase DAO json |
| FAAL-AR-02 | Happy | `mergeArtifactGenerationRequests` prefers non-blank content over placeholder |
| FAAL-AR-03 | Happy | Successful `append_blocks` with `fullDocument` emits `Artifacts` with non-empty `content` |
| FAAL-AR-04 | Error | Failed `append_blocks` + persist retains prior `DocumentEntity.json` |
| FAAL-AR-05 | Happy | Export hydrates from repository when card `documentJson` is blank |

### Persistence safety

| ID | Type | Description |
|----|------|-------------|
| FAAL-PS-01 | Happy | `sanitizePersistedContent` removes `<tool_call>` leak from content |
| FAAL-PS-02 | Edge | Content over max chars capped before Room write |

### Mutation defense

| ID | Type | Description |
|----|------|-------------|
| FAAL-MD-01 | Mutation | `ToolRepairPipeline` must call `canonicalize_tool_call`, not bool-only `coerce_field` |
| FAAL-MD-02 | Mutation | `finish(true)` must not drop preamble before tool marker |
| FAAL-MD-03 | Mutation | Persist must not `insertDocument` with empty json over non-empty row |

**Total scenario IDs: 24**

---

## Happy path scenarios — canonicalization pipeline

### FAAL-CP-01 — Shared argument canonicalizer coerces blocks

```gherkin
Feature: Tool argument canonicalization
  Scenario: FAAL-CP-01 stringified blocks become array
    Given tool_name is "append_blocks"
    And raw arguments_json is:
      """
      {"artifactId":"art-cp-1","blocks":"[{\"type\":\"paragraph\",\"text\":\"Hello\"}]","isComplete":false}
      """
    When canonicalize_raw_arguments is called with ToolCatalog::global()
    Then the call succeeds
    And the "blocks" field is a JSON array of length 1
    And blocks[0].text equals "Hello"
    And artifactId equals "art-cp-1"
```

### FAAL-CP-02 — Mode-specific name policy

```gherkin
  Scenario: FAAL-CP-02 ProviderIngress does not reject unknown tool
    Given a RawToolCall with name "totally_unknown_tool_xyz"
    And arguments_json is "{}"
    When canonicalize_tool_call is called with mode ProviderIngress
    Then the outcome is Ok

  Scenario: FAAL-CP-02b ExecutionStrict rejects unknown tool
    Given the same RawToolCall
    When canonicalize_tool_call is called with mode ExecutionStrict
    Then the outcome is Reject
    And error_json_for_model contains "Unknown tool"
```

### FAAL-CP-03 — Ingress vs execution argument parity

```gherkin
  Scenario: FAAL-CP-03 provider ingress matches execution strict arguments
    Given tool_name is "append_blocks"
    And raw arguments_json is exactly:
      """
      {"artifactId":"art-parity-1","blocks":"[{\"type\":\"paragraph\",\"text\":\"Hi\"}]","isComplete":true}
      """
    When coerce_provider_arguments is invoked
    And canonicalize_tool_call is invoked with mode ExecutionStrict on RawToolCall name append_blocks
    Then both canonical arguments_json strings are semantically equal
    And both contain a JSON array for "blocks" not a string
    And both contain "isComplete":true
```

### FAAL-CP-04 — Idempotent argument canonicalization

```gherkin
  Scenario: FAAL-CP-04 double canonicalize is stable
    Given tool_name is "append_blocks"
    And arguments Value after first canonicalize_raw_arguments is stored as snapshot A
    When canonicalize_arguments_value is called again on the same Value
    Then the resulting canonical JSON string equals snapshot A
```

### FAAL-CP-05 — Envelope path uses shared canonicalizer

```gherkin
  Scenario: FAAL-CP-05 parse_tool_calls coerces stringified blocks
    Given raw model text contains a tool_call envelope for append_blocks
    And envelope arguments include blocks as a JSON string containing one paragraph block
    When parse_tool_calls is invoked
    Then successes length is 1
    And successes[0].arguments_json contains a JSON array for blocks not a string value for blocks
```

### FAAL-RC-01 — ToolRepairPipeline delegates to canonicalizer

```gherkin
Feature: Execution gate wrapper
  Scenario: FAAL-RC-01 repair uses canonicalize_tool_call
    Given a RawToolCall with name "append_blocks"
    And arguments_json is exactly:
      """
      {"artifactId":"art-coerce-1","blocks":"[{\"type\":\"paragraph\",\"text\":\"Hello\"}]","isComplete":false}
      """
    When ToolRepairPipeline.repair is called
    Then the outcome is Repaired
    And repaired arguments_json has blocks as JSON array length 1
```

---

## Happy path scenarios — stream gate and artifacts

### FAAL-SG-01 — Native tools flush preamble before partial marker

```gherkin
Feature: Stream gate preamble flush with native tools
  Scenario: FAAL-SG-01 preamble before partial tool marker
    Given a ToolStreamGate with allow_tool_call true
    When push receives exactly "Let me try with a simpler <tool"
    And finish is called with has_native_tool_calls true
    Then the concatenation of emitted_prefix during push is "Let me try with a simpler "
    And the sum of user-visible emitted text equals "Let me try with a simpler "
    And finish.has_tool_marker is true
```

### FAAL-SG-02 — Native tools flush preamble before tool_call marker

```gherkin
  Scenario: FAAL-SG-02 preamble before tool_call tail
    Given a ToolStreamGate with allow_tool_call true
    When push receives exactly "Summary.<tool_call>{\"name\":\"append_blocks\"}"
    And finish is called with has_native_tool_calls true
    Then emitted_prefix during push is "Summary."
    And finish.has_tool_marker is true
```

### FAAL-SG-03 — Kotlin streaming emits preamble

```gherkin
Feature: ModelTurnStreamGate UI emission
  Scenario: FAAL-SG-03 emits preamble before marker
    Given a ModelTurnStreamGate with allowToolCall true
    When onProviderTextChunk receives "Let me try with a simpler <tool"
    And finish is called with hasNativeToolCalls true
    Then collected emitted tokens join to "Let me try with a simpler "
```

### FAAL-AR-01 — Persist preserves DocumentDao when placeholder blank

```gherkin
Feature: Artifact document durability
  Scenario: FAAL-AR-01 blank accumulator does not wipe DAO
    Given messageId "msg-artifact-1"
    And DocumentDao has artifactId "doc-1" with json containing "Saved"
    And accumulator artifacts list has artifactId doc-1 title Report content empty string
    When ChatRepositoryImpl.persistAllMessageData runs
    Then DocumentDao.getDocumentById("doc-1").json still contains "Saved"
```

### FAAL-AR-03 — append_blocks with fullDocument emits Artifacts

```gherkin
  Scenario: FAAL-AR-03 InferenceEvent Artifacts when fullDocument present
    Given append_blocks result_json contains "fullDocument"
    And result_json contains isComplete true
    And fullDocument contains "\"blocks\":[{\"type\":\"paragraph\",\"text\":\"Done\"}]"
    When emitToolSideEffects runs
    Then exactly one InferenceEvent.Artifacts is emitted
    And artifacts[0].content contains "Done"
```

### FAAL-AR-05 — Export hydrates when card json blank

```gherkin
Feature: Artifact export
  Scenario: FAAL-AR-05 export hydrates from repository
    Given ArtifactCardUiModel documentJson is ""
    And repository returns document json containing "Body" for artifactId "doc-export-1"
    When onConfirmArtifactExport runs
    Then exportArtifactDocumentsUseCase command.documentJson contains "Body"
```

### FAAL-PS-01 — Sanitize strips tool leak

```gherkin
Feature: Defensive message persistence
  Scenario: FAAL-PS-01 sanitize removes tool_call trace
    Given accumulator content is "Hello <tool_call>{\"name\":\"append_blocks\"}</tool_call> world"
    When PersistAccumulatedChatMessagesUseCase runs
    Then persisted content does not contain "<tool_call>"
    And persisted content does not contain "</tool_call>"
```

---

## Error path scenarios

### FAAL-RC-02 — Malformed blocks rejected at execution gate

```gherkin
  Scenario: FAAL-RC-02 reject malformed blocks string
    Given RawToolCall name append_blocks
    And arguments_json is exactly:
      """
      {"artifactId":"art-bad-1","blocks":"[{not valid json","isComplete":false}
      """
    When ToolRepairPipeline.repair is called
    Then the outcome is Reject
    And error_json_for_model contains "Invalid JSON" or "blocks"
```

### FAAL-RC-03 — Provider ingress does not mask unrecoverable JSON

```gherkin
  Scenario: FAAL-RC-03 bad ingress arguments are still rejected at execution
    Given tool_name append_blocks
    And args_json is exactly:
      """
      {"artifactId":"art-bad-ingress","blocks":"[{not valid json","isComplete":false}
      """
    When coerce_provider_arguments is invoked
    Then the returned arguments_json is not silently converted to "{}"
    And the repairs list records the parse failure
    When ToolRepairPipeline.repair runs on the returned tool_name and arguments_json
    Then the outcome is Reject
```

### FAAL-RC-04 — Docgen validation after canonicalization

```gherkin
  Scenario: FAAL-RC-04 canonicalized args pass docgen validation
    Given ToolCallRequest append_blocks with arguments_json:
      """
      {"artifactId":"art-exec-1","blocks":[{"type":"paragraph","text":"Valid"}],"isComplete":false}
      """
    When validateAppendBlocksArgs succeeds and execute runs
    Then result_json status equals "success"
```

### FAAL-AR-04 — Failed append_blocks preserves document

```gherkin
  Scenario: FAAL-AR-04 validation failure preserves document
    Given DocumentDao json for doc-fail-1 contains "block-A"
    When append_blocks returns status error
    And persistAllMessageData runs with placeholder artifact content ""
    Then DocumentDao json for doc-fail-1 still contains "block-A"
```

---

## Edge scenarios

### FAAL-SG-04 — finish forwards trailingFlush to emit

```gherkin
  Scenario: FAAL-SG-04 trailingFlush forwarded
    Given FakeToolStreamGatePort finish returns trailingFlush "tail text"
    When ModelTurnStreamGate.finish hasNativeToolCalls true
    Then emit lambda received exactly "tail text"
```

### FAAL-SG-05 — Leak-only holdback unchanged

```gherkin
  Scenario: FAAL-SG-05 no native tools hold back tool_call body
    Given allow_tool_call true
    And one-chunk text is a complete tool_call envelope for append_blocks
    When finish has_native_tool_calls false
    Then emitted_prefix is ""
    And finish.has_tool_marker is true
```

### FAAL-AR-02 — mergeArtifact prefers full content

```gherkin
  Scenario: FAAL-AR-02 merge upgrades placeholder
    Given base artifact doc-merge-1 content ""
    And addition artifact doc-merge-1 content "{\"version\":\"1.0\",\"blocks\":[]}"
    When mergeArtifactGenerationRequests runs
    Then result single artifact content equals "{\"version\":\"1.0\",\"blocks\":[]}"
```

### FAAL-PS-02 — Oversized content capped

```gherkin
  Scenario: FAAL-PS-02 content length guard
    Given accumulator content length exceeds MAX_PERSIST_CONTENT_CHARS
    When PersistAccumulatedChatMessagesUseCase runs
    Then persisted content length is less than or equal to MAX_PERSIST_CONTENT_CHARS
```

---

## Mutation-defense scenarios

### FAAL-MD-01 — Execution gate must use canonicalize module

```gherkin
Feature: Mutation defense canonicalization
  Scenario: FAAL-MD-01 repair must not use bool-only coerce_field
    Given ToolRepairPipeline.repair output for stringified blocks append_blocks
    When blocks field in repaired JSON is inspected
    Then blocks must be a JSON array not a string
    And repair/mod.rs must invoke canonicalize_tool_call or the shared argument canonicalizer
```

**Test implementation:** `tool_call_canonicalize_test.rs` asserts array type and parity between `coerce_provider_arguments`, `parse_tool_calls`, and `ToolRepairPipeline::repair`. Avoid brittle source-code grep unless behavior cannot detect the regression.

### FAAL-MD-02 — Native finish must flush preamble

```gherkin
  Scenario: FAAL-MD-02 preamble must reach UI
    Given buffer "Let me try with a simpler <tool"
    And has_native_tool_calls true
    When ToolStreamGate.finish runs
    Then total user-visible text includes "Let me try with a simpler "
```

### FAAL-MD-03 — Persist must not overwrite non-empty document

```gherkin
  Scenario: FAAL-MD-03 empty insert forbidden
    Given existing DocumentEntity json length greater than 50
    When persistAllMessageData receives artifact content ""
    Then insertDocument must not be called with encrypted empty json body
```

---

## Task ↔ scenario matrix

| Task | Scenarios |
|------|-----------|
| T1 | FAAL-CP-01, FAAL-CP-04, FAAL-RC-01 |
| T2 | FAAL-CP-02, FAAL-RC-02 |
| T3 | FAAL-CP-02, FAAL-CP-03, FAAL-RC-02 |
| T4 | FAAL-RC-01, FAAL-RC-04, FAAL-MD-01 |
| T5 | FAAL-CP-03, FAAL-RC-03 |
| T6 | FAAL-CP-05, FAAL-RC-01 |
| T7 | FAAL-CP-03, FAAL-CP-04, FAAL-RC-03, FAAL-MD-01 |
| T8 | FAAL-SG-01, FAAL-SG-02, FAAL-SG-05, FAAL-MD-02 |
| T9 | FAAL-SG-03, FAAL-SG-04 |
| T10 | FAAL-AR-01, FAAL-AR-04, FAAL-MD-03 |
| T11 | FAAL-AR-02, FAAL-AR-05 |
| T12 | FAAL-AR-03 |
| T13 | FAAL-PS-01, FAAL-PS-02 |
| T14 | FAAL-RC-04, FAAL-AR-03, All validation |

---

## Self-review checklist

| Check | Status |
|-------|--------|
| Canonicalization pipeline with explicit modes documented | Yes |
| Four responsibility split (name, args, policy, record) | Yes |
| Ingress + execution trust boundary both specified | Yes |
| Idempotence scenario | Yes |
| All four investigation problems mapped | Yes |
| No Room schema changes | Yes |
| No TBD / TODO placeholders | Yes |
| Every task has scenario IDs | Yes |
| Mutation-defense targets canonicalizer not bool-only coerce | Yes |
