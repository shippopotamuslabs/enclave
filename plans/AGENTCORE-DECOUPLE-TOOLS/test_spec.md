# AGENTCORE-DECOUPLE-TOOLS Test Spec

## Test framework and exact run command

Rust tests:

```bash
cd third_party/agent_core && cargo test
```

Rust tests with long-loop feature coverage:

```bash
cd third_party/agent_core && cargo test --features lalr-harness
```

Android domain and agent unit tests:

```bash
./gradlew :core:domain:testDebugUnitTest :core:agent:testDebugUnitTest
```

Android compile smoke after UniFFI binding updates:

```bash
./gradlew :core:agent:assembleDebug
```

## Scenario index

| ID | Title |
|---|---|
| TS-001 | Agnostic default contains no Enclave tool semantics |
| TS-002 | Enclave compatibility config preserves existing semantic names |
| TS-003 | Catalog builds from host semantics without global catalog |
| TS-004 | Duplicate canonical tool names are rejected |
| TS-005 | Alias collisions are rejected |
| TS-006 | Invalid artifact config is rejected |
| TS-007 | Global catalog remains compatibility-only |
| TS-008 | Custom alias normalizes artifact id field |
| TS-009 | Custom completion alias normalizes completion field |
| TS-010 | Custom default injects completion false |
| TS-011 | Stringified custom blocks array is accepted |
| TS-012 | Malformed stringified custom blocks array is rejected |
| TS-013 | Custom scalar query normalizes to queries array |
| TS-014 | Existing repair wrapper remains Enclave-compatible |
| TS-015 | Semantics-aware repair accepts custom tool names |
| TS-016 | Provider parse uses supplied custom catalog |
| TS-017 | Custom disclosure tool is always callable |
| TS-018 | Deferred custom tool rejection names configured disclosure tool |
| TS-019 | Missing disclosure semantics fails closed |
| TS-020 | Custom artifact start result records pending id |
| TS-021 | Custom incomplete append keeps pending id |
| TS-022 | Custom complete append removes pending id |
| TS-023 | Synthetic finalize uses custom append tool and fields |
| TS-024 | Custom append result skips truncation |
| TS-025 | Non-exempt large result is truncated |
| TS-026 | Micro compaction preserves configured tool |
| TS-027 | Custom greedy tool extends guardrail cap |
| TS-028 | No greedy config keeps base guardrail cap |
| TS-029 | Custom artifact squash collapses configured append loop |
| TS-030 | Domain Enclave default maps configured names |
| TS-031 | Domain agnostic config maps empty semantics |
| TS-032 | Mutation defense: hard-coded append_blocks repair fails custom-only config |
| TS-033 | Mutation defense: hard-coded disclosure name fails custom-only config |
| TS-034 | Mutation defense: hard-coded artifact squash misses custom append tool |

## Happy path scenarios

Feature: Configurable tool semantics for agent_core

Scenario: TS-001 Agnostic default contains no Enclave tool semantics
  Given a Rust `AgentConfig::default()`
  When the test reads `config.tool_semantics`
  Then `config.tool_semantics.disclosure` is `None`
  And `config.tool_semantics.artifact` is `None`
  And `config.tool_semantics.greedy_extension_tools.len()` is `0`
  And `config.greedy_append_blocks` is `false`

Scenario: TS-002 Enclave compatibility config preserves existing semantic names
  Given a Rust `ToolSemanticsConfig::enclave_compat()`
  When the test reads its disclosure and artifact records
  Then `disclosure.tool_name` is `"disclose_tool_schemas"`
  And `disclosure.request_field_name` is `"tool_names"`
  And `artifact.start_tool_name` is `"start_artifact"`
  And `artifact.append_tool_name` is `"append_blocks"`
  And `artifact.id_field` is `"artifactId"`
  And `artifact.completion_field` is `"isComplete"`

Scenario: TS-003 Catalog builds from host semantics without global catalog
  Given a `ToolSemanticsConfig` with catalog tools `"begin_doc"` and `"write_doc"`
  When `ToolCatalog::from_semantics` is called
  Then `catalog.resolve("begin_doc").unwrap().name` is `"begin_doc"`
  And `catalog.resolve("write_doc").unwrap().name` is `"write_doc"`
  And `catalog.resolve("append_blocks")` is `None`

Scenario: TS-007 Global catalog remains compatibility-only
  Given `ToolCatalog::global()`
  When the test resolves `"append_blocks"`
  Then `catalog.resolve("append_blocks").unwrap().name` is `"append_blocks"`
  And `ToolSemanticsConfig::agnostic().catalog.tools.len()` is `0`

Scenario: TS-008 Custom alias normalizes artifact id field
  Given custom semantics for tool `"write_doc"` with alias `"artifact_id"` to `"docId"`
  When `canonicalize_raw_arguments_with_semantics` receives `{"artifact_id":"doc-1","chunks":[]}`
  Then the returned `arguments_json` is `{"docId":"doc-1","chunks":[],"done":false}`
  And the returned repairs contain `"normalized parameter alias artifact_id to docId"`

Scenario: TS-009 Custom completion alias normalizes completion field
  Given custom semantics for tool `"write_doc"` with alias `"complete"` to `"done"`
  When `canonicalize_raw_arguments_with_semantics` receives `{"docId":"doc-1","chunks":[],"complete":true}`
  Then the returned `arguments_json` is `{"docId":"doc-1","chunks":[],"done":true}`
  And the returned repairs contain `"normalized parameter alias complete to done"`

Scenario: TS-010 Custom default injects completion false
  Given custom semantics for tool `"write_doc"` with schema default `"done": false`
  When `canonicalize_raw_arguments_with_semantics` receives `{"docId":"doc-1","chunks":[]}`
  Then the returned `arguments_json` is `{"docId":"doc-1","chunks":[],"done":false}`
  And the returned `repaired` flag is `true`

Scenario: TS-011 Stringified custom blocks array is accepted
  Given custom semantics for tool `"write_doc"` with `ValidateStringifiedJsonArray` on `"chunks"`
  When `canonicalize_raw_arguments_with_semantics` receives `{"docId":"doc-1","chunks":"[{\"text\":\"Hello\"}]","done":false}`
  Then the returned `arguments_json` is `{"docId":"doc-1","chunks":[{"text":"Hello"}],"done":false}`
  And the returned repairs contain `"coerced arguments to catalog schema"`

Scenario: TS-013 Custom scalar query normalizes to queries array
  Given custom semantics for tool `"memory_search"` with `ScalarToArray` from `"query"` to `"queries"`
  When `canonicalize_raw_arguments_with_semantics` receives `{"query":"budget"}`
  Then the returned `arguments_json` is `{"queries":["budget"]}`
  And the returned repairs contain `"normalized query to queries array"`

Scenario: TS-014 Existing repair wrapper remains Enclave-compatible
  Given the existing `ToolRepairPipeline::repair` wrapper
  When it receives tool `"append_blocks"` with arguments `{"artifactId":"a","blocks":[]}`
  Then the repaired tool name is `"append_blocks"`
  And the repaired arguments JSON is `{"artifactId":"a","blocks":[],"isComplete":false}`

Scenario: TS-015 Semantics-aware repair accepts custom tool names
  Given a custom semantics catalog containing `"write_doc"`
  When `ToolRepairPipeline::repair_with_semantics` receives tool `"write_doc"` with arguments `{"docId":"doc-1","chunks":[]}`
  Then the repaired tool name is `"write_doc"`
  And the repaired arguments JSON is `{"docId":"doc-1","chunks":[],"done":false}`

Scenario: TS-016 Provider parse uses supplied custom catalog
  Given custom parse semantics containing tool `"lookup_memory"`
  When `parse_tool_calls_with_semantics` receives `<tool_call>{"name":"lookup_memory","arguments":{"query":"alpha"}}</tool_call>`
  Then `batch.successes.len()` is `1`
  And `batch.successes[0].tool_name` is `"lookup_memory"`
  And `batch.failures.len()` is `0`

Scenario: TS-017 Custom disclosure tool is always callable
  Given active tool names contain only `"open_tools"` and `"search_web"`
  And disclosure semantics uses tool `"open_tools"` and request field `"names"`
  When the model calls `"open_tools"` with arguments `{"names":["write_doc"]}`
  Then the next engine action is `AgentAction::ExecuteTools`
  And the first execute call name is `"open_tools"`

Scenario: TS-020 Custom artifact start result records pending id
  Given artifact semantics with start tool `"begin_doc"` and id field `"docId"`
  When `record_artifact_progress_with_semantics` receives tool `"begin_doc"` and result `{"status":"success","docId":"doc-1"}`
  Then the pending id set is `["doc-1"]`

Scenario: TS-021 Custom incomplete append keeps pending id
  Given artifact semantics with append tool `"write_doc"` and completion field `"done"`
  And the pending id set is `["doc-1"]`
  When `record_artifact_progress_with_semantics` receives arguments `{"docId":"doc-1","chunks":[]}` and result `{"status":"success","done":false}`
  Then the pending id set is `["doc-1"]`

Scenario: TS-022 Custom complete append removes pending id
  Given artifact semantics with append tool `"write_doc"` and completion field `"done"`
  And the pending id set is `["doc-1"]`
  When `record_artifact_progress_with_semantics` receives arguments `{"docId":"doc-1","chunks":[]}` and result `{"status":"success","done":true}`
  Then the pending id set is `[]`

Scenario: TS-023 Synthetic finalize uses custom append tool and fields
  Given artifact semantics with append tool `"write_doc"`, id field `"docId"`, blocks field `"chunks"`, completion field `"done"`, and prefix `"auto-close-"`
  And the pending id set is `["doc-7"]`
  When `synthetic_finalize_calls_with_semantics` is called
  Then `calls.len()` is `1`
  And `calls[0].call_id` is `"auto-close-doc-7"`
  And `calls[0].name` is `"write_doc"`
  And `calls[0].arguments_json` is `{"docId":"doc-7","chunks":[],"done":true}`

Scenario: TS-024 Custom append result skips truncation
  Given result policy for tool `"write_doc"` has `skip_result_truncation` set to `true`
  And the tool result JSON is larger than `1024` bytes
  When the engine processes the `"write_doc"` result under a `1024` byte result budget
  Then the stored tool message content length is `2048`
  And the stored tool result `truncated` flag is `false`

Scenario: TS-026 Micro compaction preserves configured tool
  Given result policy for tool `"write_doc"` has `preserve_in_micro_compaction` set to `true`
  And `micro_keep_recent` is `0`
  When `MicroCompactionTier` is applied to a session with one `"write_doc"` tool message containing `"DOCUMENT_BODY"`
  Then the compaction outcome is `CompactionOutcome::NotApplicable`

Scenario: TS-027 Custom greedy tool extends guardrail cap
  Given greedy extension semantics for tool `"write_doc"` with `extra_turns` set to `20`
  And `tool_calls_executed` is `20`
  And `max_turns` is `20`
  When `allow_tool_call` is evaluated after a model response that called `"write_doc"`
  Then `effective_max_tool_calls` is `40`
  And `allow_tool_call` is `true`

Scenario: TS-029 Custom artifact squash collapses configured append loop
  Given artifact squash semantics for append tool `"write_doc"`, id field `"docId"`, blocks field `"chunks"`, and total field `"chunkCount"`
  And a session contains two `"write_doc"` assistant calls and two success tool results with `"chunkCount": 2`
  When `ArtifactSquashCompactionTier` is applied
  Then the compaction outcome is `CompactionOutcome::Applied`
  And the inserted system message content is `"Artifact generation in progress. 2 blocks written so far. Last block: `Part 2`. Continue appending."`
  And no remaining active message content contains `"call:write_doc"`

Scenario: TS-030 Domain Enclave default maps configured names
  Given Kotlin `AgentLoopToolSemanticsConfig.enclaveDefault()`
  When `AgentLoopAgentConfig.toFfi()` maps the default config
  Then `ffi.toolSemantics.disclosure.toolName` is `"disclose_tool_schemas"`
  And `ffi.toolSemantics.disclosure.requestFieldName` is `"tool_names"`
  And `ffi.toolSemantics.artifact.appendToolName` is `"append_blocks"`
  And `ffi.toolSemantics.artifact.completionField` is `"isComplete"`

Scenario: TS-031 Domain agnostic config maps empty semantics
  Given Kotlin `AgentLoopToolSemanticsConfig.agnostic()`
  When `AgentLoopAgentConfig.toFfi()` maps the config
  Then `ffi.toolSemantics.disclosure` is `null`
  And `ffi.toolSemantics.artifact` is `null`
  And `ffi.toolSemantics.greedyExtensionTools.size` is `0`

## Error and edge case scenarios

Scenario: TS-004 Duplicate canonical tool names are rejected
  Given a `ToolSemanticsConfig` catalog with tools `"write_doc"` and `"write-doc"`
  When `ToolSemanticsConfig::validate()` is called
  Then the result is `Err(ToolSemanticsError::DuplicateToolName { normalized_name: "write_doc" })`

Scenario: TS-005 Alias collisions are rejected
  Given a `ToolSemanticsConfig` catalog with tool `"write_doc"` alias `"writer"` and tool `"compose_doc"` alias `"writer"`
  When `ToolSemanticsConfig::validate()` is called
  Then the result is `Err(ToolSemanticsError::AliasCollision { alias: "writer", first_tool: "write_doc", second_tool: "compose_doc" })`

Scenario: TS-006 Invalid artifact config is rejected
  Given an artifact semantics config with `append_tool_name` set to `""`
  When `ToolSemanticsConfig::validate()` is called
  Then the result is `Err(ToolSemanticsError::InvalidArtifactConfig { message: "artifact append_tool_name must not be empty" })`

Scenario: TS-012 Malformed stringified custom blocks array is rejected
  Given custom semantics for tool `"write_doc"` with `ValidateStringifiedJsonArray` on `"chunks"`
  When `canonicalize_raw_arguments_with_semantics` receives `{"docId":"doc-1","chunks":"[{not valid json","done":false}`
  Then the result is `Err(CanonicalizeArgumentsError { message: "chunks string contains invalid JSON: key must be a string at line 1 column 3" })`

Scenario: TS-018 Deferred custom tool rejection names configured disclosure tool
  Given active tool names contain only `"open_tools"` and `"search_web"`
  And disclosure semantics uses tool `"open_tools"` and request field `"names"`
  When the model calls deferred tool `"write_doc"` before disclosure
  Then the stored tool rejection JSON field `"error"` is `"Tool 'write_doc' is not active. Call open_tools first."`
  And the stored tool rejection JSON field `"available_tools"` is `["open_tools","search_web"]`

Scenario: TS-019 Missing disclosure semantics fails closed
  Given active tool names contain only `"search_web"`
  And disclosure semantics is `None`
  When the model calls deferred tool `"write_doc"`
  Then the stored tool rejection JSON field `"error"` is `"Tool 'write_doc' is not active."`
  And the stored tool rejection JSON field `"available_tools"` is `["search_web"]`

Scenario: TS-025 Non-exempt large result is truncated
  Given no result policy exists for tool `"lookup_memory"`
  And the tool result JSON is larger than `2048` bytes
  When the engine processes the `"lookup_memory"` result under a `1024` byte result budget
  Then the stored tool result `truncated` flag is `true`
  And the stored tool message content contains `"truncated"`

Scenario: TS-028 No greedy config keeps base guardrail cap
  Given `greedy_extension_tools` is empty
  And `tool_calls_executed` is `20`
  And `max_turns` is `20`
  When `allow_tool_call` is evaluated after a model response that called `"write_doc"`
  Then `effective_max_tool_calls` is `20`
  And `allow_tool_call` is `false`

## Mutation-defense risk and scenario

Mutation risk: A lazy implementation may keep literal checks for `"append_blocks"` and `"disclose_tool_schemas"` while adding new config types. That would pass Enclave compatibility tests but fail for custom host tools.

Scenario: TS-032 Mutation defense: hard-coded append_blocks repair fails custom-only config
  Given a custom-only semantics config with catalog tool `"write_doc"` and no tool named `"append_blocks"`
  When `ToolRepairPipeline::repair_with_semantics` receives tool `"write_doc"` with arguments `{"doc_id":"doc-1","chunks":[],"complete":true}`
  Then the repaired tool name is `"write_doc"`
  And the repaired arguments JSON is `{"docId":"doc-1","chunks":[],"done":true}`
  And the repaired arguments JSON does not contain `"artifactId"`
  And the repaired arguments JSON does not contain `"isComplete"`

Scenario: TS-033 Mutation defense: hard-coded disclosure name fails custom-only config
  Given active tool names contain only `"open_tools"`
  And disclosure semantics uses tool `"open_tools"` and request field `"names"`
  And no tool named `"disclose_tool_schemas"` exists in the catalog
  When the model calls `"open_tools"` with arguments `{"names":["write_doc"]}`
  Then the next engine action is `AgentAction::ExecuteTools`
  And the first execute call name is `"open_tools"`
  And the stored rejection message count is `0`

Scenario: TS-034 Mutation defense: hard-coded artifact squash misses custom append tool
  Given artifact squash semantics for append tool `"write_doc"` and no tool named `"append_blocks"` exists in the catalog
  And a session contains a `"write_doc"` assistant call followed by `{"status":"success","chunkCount":1}`
  When `ArtifactSquashCompactionTier` is applied
  Then the compaction outcome is `CompactionOutcome::Applied`
  And the inserted system message content is `"Artifact generation in progress. 1 blocks written so far. Last block: `Only block`. Continue appending."`
  And no active message content contains `"write_doc"`
