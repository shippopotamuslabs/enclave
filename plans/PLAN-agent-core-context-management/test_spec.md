# Test Spec: Full-Spectrum Context Management for `third_party/agent_core`

## Test Framework & Run Command
Framework: Rust built-in `#[test]` unit and integration tests, plus `insta` JSON snapshot assertions where existing coverage already uses snapshots. Run: `cd /home/sean/Code/pocket-crew/third_party/agent_core && cargo test`.

## Scenario Index
`S1` — Preflight below threshold skips compaction.
`S2` — Preflight summary request carries trigger metadata and selected message IDs.
`S3` — Mid-turn summary compaction resumes pending tool execution.
`S4` — Post-tool-result compaction runs before the next model call.
`S5` — Artifact squash is preferred over FIFO for append-block loops.
`S6` — `kv_fixed` sessions stay on synchronous compaction and still invalidate KV state.
`S7` — Summary requests include history reference and state-ledger snapshot.
`S8` — Oversized tool output spills to a context reference.
`S9` — Raw spilled tool output can be read by reference ID.
`S10` — Summarized history range remains searchable as a history snapshot.
`S11` — State ledger records objective, constraints, decisions, and open questions.
`S12` — Model requests include state-ledger anchors after compaction.
`S13` — Tool schemas are deferred into a compact tool context index.
`S14` — Deferred tool schema metadata can be searched and loaded.
`S15` — Artifact squash persists artifact state into the state ledger.
`S16` — FIFO requires a history snapshot before destructive deletion.
`S17` — Large file context condenses to outline and chunk references.
`S18` — Manual compact uses focus instruction and emits summary request.
`S19` — End-to-end mixed pressure preserves semantic continuity.
`S20` — Hosted mutable policy enables summary, references, and tool deferral.
`S21` — Fixed-KV policy disables host summary and history rewrite.
`S22` — High-context policy raises effective pressure thresholds and emits cache hints.
`S23` — Terminal output references share the context-store retrieval path.
`S24` — PreCompact and PostCompact lifecycle events bracket compaction.
`E1` — Summary tier is not applicable when only protected tail messages remain.
`E2` — `submit_summary()` fails on a mismatched `last_message_id`.
`E3` — Over-threshold pressure with every eligible tier unavailable fails loudly.
`E4` — Missing context reference read returns a typed not-found error.
`E5` — FIFO fails when required history snapshot cannot be created.
`E6` — Tool-schema deferral disabled falls back to full tool schema JSON.
`E7` — Oversized unparseable file context becomes filename-only, not full content.
`E8` — Fixed-KV rejects a history-rewrite compaction plan.
`E9` — Manual compact disabled by policy returns a no-op result.
`E10` — Prompt-cache hints are omitted when provider support is false.
`M1` — Lazy preflight-only wiring is rejected by a post-tool-result trigger test.
`M2` — FIFO-before-summary ordering is rejected.
`M3` — Tool spillover must preserve full raw output, not only a preview.
`M4` — State ledger cannot be a static placeholder.
`M5` — Artifact continuation cannot rely only on synthetic system text.
`M6` — Tool deferral cannot drop retrievability for omitted schemas.
`M7` — File condensation cannot pretend omitted file content is available.

## Happy Path Scenarios

### S1: Preflight Below Threshold Skips Compaction
- **Given:** `AgentConfig.threshold_ratio = 0.80`, `AgentConfig.mid_turn_threshold_ratio = 0.90`, `AgentConfig.post_tool_result_threshold_ratio = 0.85`, `SessionCapabilities { summarization_allowed: true, kv_fixed: false, context_policy_tier: hosted_mutable }`, and a session whose computed pressure ratio is `0.79`
- **When:** `AgentEngine::next_action()` is called from `EnginePhase::Idle`
- **Then:** the returned action kind is `CallModel`, the drained `CompactionApplied` event count is `0`, and the drained `ContextPressure` event count is `0`

### S2: Preflight Summary Request Carries Trigger Metadata
- **Given:** `AgentConfig.threshold_ratio = 0.80`, `AgentConfig.allow_fifo_on_mid_turn = false`, `AgentConfig.allow_fifo_on_post_tool_result = false`, `SessionCapabilities { summarization_allowed: true, kv_fixed: false, context_policy_tier: hosted_mutable }`, and session message IDs `["u0", "a0", "u1"]` with pressure ratio `0.91`
- **When:** `AgentEngine::next_action()` is called from `EnginePhase::Idle`
- **Then:** the returned action kind is `RequestSummary`, `SummaryRequest.trigger` equals `preflight`, `SummaryRequest.last_message_id` equals `"a0"`, and `SummaryRequest.messages_to_summarize` contains exactly the message IDs `["u0", "a0"]`

### S3: Mid-Turn Summary Resumes Pending Tool Execution
- **Given:** `AgentConfig.mid_turn_threshold_ratio = 0.90`, `SessionCapabilities { summarization_allowed: true, kv_fixed: false, context_policy_tier: hosted_mutable }`, an assistant model response with one repaired tool call named `"web_search"`, and a post-response pressure ratio of `0.93`
- **When:** `submit_model_response()` is called and the engine later receives `submit_summary()` with `SummaryResult { summary_text: "Summary: tool choice retained.", last_message_id: "a1" }`
- **Then:** the next action kind is `ExecuteTools`, the pending tool call count equals `1`, the first `CompactionApplied` event has `trigger = mid_turn`, and the inserted compact-summary message content equals `"Summary: tool choice retained."`

### S4: Post-Tool-Result Compaction Runs Before The Next Model Call
- **Given:** `AgentConfig.post_tool_result_threshold_ratio = 0.85`, `SessionCapabilities { summarization_allowed: true, kv_fixed: false, context_policy_tier: hosted_mutable }`, and one processed tool result large enough to move the pressure ratio to `0.90`
- **When:** `submit_tool_results()` is called
- **Then:** a drained `CompactionApplied` event is present before any later `next_action()` call, that event has `trigger = post_tool_result`, and its count is exactly `1`

### S5: Artifact Squash Is Preferred Over FIFO
- **Given:** `AgentConfig.post_tool_result_threshold_ratio = 0.85`, `AgentConfig.allow_fifo_on_post_tool_result = false`, `SessionCapabilities { summarization_allowed: true, kv_fixed: false, context_policy_tier: hosted_mutable }`, and an append-block loop whose latest successful tool result reports `"totalBlocks": 3` and whose latest assistant block text is `"gamma"`
- **When:** post-tool-result compaction runs
- **Then:** the first `CompactionApplied` event has `tier = artifact_squash`, and the inserted system message content equals `Artifact generation in progress. 3 blocks written so far. Last block: \`gamma\`. Continue appending.`

### S6: `kv_fixed` Sessions Stay On Synchronous Compaction
- **Given:** `AgentConfig.threshold_ratio = 0.80`, `SessionCapabilities { summarization_allowed: true, kv_fixed: true, context_policy_tier: fixed_kv, history_rewrite_supported: true }`, and a preflight pressure ratio of `0.91` with no reproducible tool outputs and no append-block loop
- **When:** `AgentEngine::next_action()` is called
- **Then:** the returned action kind is not `RequestSummary`, the first `CompactionApplied` event has `tier = fifo`, and one `KvCacheInvalidated.reason` value equals `"Compacted: fifo"`

### S7: Summary Request Includes History Reference And Ledger Snapshot
- **Given:** `AgentConfig.enable_context_store = true`, `AgentConfig.enable_state_ledger = true`, `SessionCapabilities { summarization_allowed: true, kv_fixed: false, context_policy_tier: hosted_mutable }`, state ledger objective `"Implement context manager"`, and message IDs `["u0", "a0", "u1"]`
- **When:** preflight summary compaction requests host summarization for IDs `["u0", "a0"]`
- **Then:** `SummaryRequest.history_ref` starts with `"history-"`, `SummaryRequest.state_ledger_snapshot.objective` equals `"Implement context manager"`, and `SummaryRequest.trigger` equals `preflight`

### S8: Oversized Tool Output Spills To Context Reference
- **Given:** `AgentConfig.tool_spill_threshold_bytes = 64`, `AgentConfig.enable_context_store = true`, a tool call `ToolCallRecord { call_id: "call-1", name: "web_search" }`, and a tool result whose `result_json` byte length is `200`
- **When:** `submit_tool_results()` processes the result
- **Then:** the stored tool message content contains `"context_ref"`, the referenced `ContextReference.kind` equals `tool_output`, and the stored `ContextReference.byte_len` equals `200`

### S9: Raw Spilled Tool Output Can Be Read By Reference ID
- **Given:** a context store entry with `reference.id = "tool-output-call-1"` and content `"RAW_PAYLOAD_123"`
- **When:** `AgentEngine::read_context_reference(handle, "tool-output-call-1")` is called
- **Then:** the returned string equals `"RAW_PAYLOAD_123"`

### S10: Summarized History Range Remains Searchable
- **Given:** messages `["u0", "a0", "u1"]` where `"a0"` content contains `"decision: use ToolSpill before Micro"` and `AgentConfig.require_snapshot_before_fifo = true`
- **When:** summary compaction snapshots messages `["u0", "a0"]`
- **Then:** `search_context_references("ToolSpill", 5)` returns exactly `1` reference and that reference has `kind = history_snapshot`

### S11: State Ledger Records Semantic Continuity Facts
- **Given:** `StateLedger::default()` and observed text `"Objective: build context manager. Constraint: do not edit contracts. Decision: put ArtifactSquash before FIFO. Open question: retention policy?"`
- **When:** `StateLedger::observe_user_message()` is called with that content
- **Then:** `objective` equals `"build context manager"`, `constraints` contains exactly `"do not edit contracts"`, `decisions` contains exactly `"put ArtifactSquash before FIFO"`, and `open_questions` contains exactly `"retention policy?"`

### S12: Model Request Includes State Ledger Anchors After Compaction
- **Given:** `AgentConfig.enable_state_ledger = true`, a ledger objective `"preserve semantic continuity"`, and a session that has just applied summary compaction
- **When:** `AgentEngine::next_action()` returns `CallModel`
- **Then:** `ModelRequest.state_ledger.objective` equals `"preserve semantic continuity"` and one request message has role `compact_summary`

### S13: Tool Schemas Are Deferred Into Compact Tool Context Index
- **Given:** `AgentConfig.enable_tool_schema_deferral = true`, `SessionCapabilities { tool_schema_deferral_supported: true, context_policy_tier: hosted_mutable }`, and `tools_schema_json` containing full schemas for `"web_search"` and `"append_blocks"`
- **When:** `AgentEngine::next_action()` returns `CallModel`
- **Then:** `ModelRequest.tool_context_index.advertised_tools` contains exactly `["web_search", "append_blocks"]`, `ModelRequest.tools_schema_json` token estimate is less than `128`, and `ToolContextIndex.full_schema_ref` starts with `"tool-schema-"`

### S14: Deferred Tool Schema Metadata Can Be Searched And Loaded
- **Given:** a deferred tool schema reference containing `"web_search"` with description `"Search the web"`
- **When:** `search_context_references("Search the web", 5)` and `read_context_reference("tool-schema-0")` are called
- **Then:** search returns exactly `1` `tool_schema` reference and the loaded schema string contains `"web_search"`

### S15: Artifact Squash Persists Artifact State Into Ledger
- **Given:** an append-block loop with `artifactId = "art-1"`, latest successful tool result `"totalBlocks": 3`, and latest block text `"gamma"`
- **When:** `ArtifactSquashCompactionTier::apply()` succeeds
- **Then:** `StateLedger.artifact_states[0].artifact_id` equals `"art-1"`, `total_blocks` equals `3`, `last_block_preview` equals `"gamma"`, and `continuation_instruction` equals `"Continue appending."`

### S16: FIFO Requires History Snapshot Before Destructive Deletion
- **Given:** `AgentConfig.require_snapshot_before_fifo = true`, `AgentConfig.allow_fifo_on_preflight = true`, and an over-threshold session where old IDs `["u0", "a0"]` are removable
- **When:** `FifoCompactionTier::apply()` removes `["u0", "a0"]`
- **Then:** `ContextStore` contains exactly `1` `history_snapshot` entry whose `source_message_ids` equal `["u0", "a0"]`

### S17: Large File Context Condenses To Outline And Chunk References
- **Given:** `AgentConfig.enable_file_condensation = true`, `AgentConfig.file_outline_threshold_tokens = 100`, `SessionCapabilities { file_backed_context_supported: true }`, and included file `"src/big.rs"` with token estimate `500`
- **When:** `condense_file_context()` processes the file
- **Then:** `FileContextSummary.path` equals `"src/big.rs"`, `condensation_level` equals `outline`, `outline_ref` starts with `"file-outline-"`, and `chunk_refs.len()` equals `1`

### S18: Manual Compact Uses Focus Instruction And Emits Summary Request
- **Given:** `AgentConfig.enable_manual_compact = true`, `SessionCapabilities { summarization_allowed: true, kv_fixed: false, context_policy_tier: hosted_mutable }`, and `ManualCompactRequest { focus_instruction: "preserve tool decisions", force_summary: true }`
- **When:** `AgentEngine::request_manual_compact()` is called
- **Then:** the returned action kind is `RequestSummary`, `SummaryRequest.trigger` equals `manual`, and `SummaryRequest.focus_instruction` equals `"preserve tool decisions"`

### S19: End-To-End Mixed Pressure Preserves Semantic Continuity
- **Given:** a session with objective `"ship context manager"`, large tool output `"RAW_LONG_OUTPUT"`, append-block artifact `artifactId = "art-9"`, included file `"src/big.rs"`, and pressure ratio `0.93`
- **When:** post-tool-result compaction, artifact squash, summary compaction, and the next preflight complete
- **Then:** the next `ModelRequest.state_ledger.objective` equals `"ship context manager"`, `context_references` contains kinds `["tool_output", "history_snapshot", "artifact_snapshot", "file_outline"]`, and final pressure ratio is less than `0.85`

### S20: Hosted Mutable Policy Enables Summary, References, And Tool Deferral
- **Given:** `SessionCapabilities { context_policy_tier: hosted_mutable, summarization_allowed: true, kv_fixed: false, tool_schema_deferral_supported: true, file_backed_context_supported: true, prompt_cache_supported: true }`
- **When:** `resolve_context_policy()` is called
- **Then:** `summarization_allowed = true`, `history_rewrite_allowed = true`, `tool_schema_deferral_allowed = true`, `file_backed_context_allowed = true`, and `prompt_cache_hinting_allowed = true`

### S21: Fixed-KV Policy Disables Host Summary And History Rewrite
- **Given:** `SessionCapabilities { context_policy_tier: fixed_kv, summarization_allowed: true, kv_fixed: true, history_rewrite_supported: false }`
- **When:** `resolve_context_policy()` is called
- **Then:** `summarization_allowed = false`, `history_rewrite_allowed = false`, `kv_rebuild_required_on_rewrite = true`, and `fifo_allowed_by_default = false`

### S22: High-Context Policy Raises Thresholds And Emits Cache Hints
- **Given:** `SessionCapabilities { context_policy_tier: high_context, prompt_cache_supported: true }`, `context_window_tokens = 200000`, and a stable system/tool prefix token estimate of `10000`
- **When:** `TokenLedger::estimate()` builds request budget metadata
- **Then:** the effective preflight threshold ratio equals `0.90` and `ModelRequest` cache hint count equals `1`

### S23: Terminal Output References Share Retrieval Path
- **Given:** a context store entry with `reference.id = "terminal-1"`, `kind = terminal_output`, and content `"cargo test output line"`
- **When:** `read_context_reference("terminal-1")` is called
- **Then:** the returned string equals `"cargo test output line"`

### S24: PreCompact And PostCompact Lifecycle Events Bracket Compaction
- **Given:** an over-threshold session with `trigger = preflight`
- **When:** compaction applies summary with `summary_ref = "summary-1"` and `history_ref = "history-1"`
- **Then:** drained events contain `PreCompact` before `CompactionApplied`, `PostCompact` after `CompactionApplied`, `PostCompact.summary_ref` equals `"summary-1"`, and `PostCompact.history_ref` equals `"history-1"`

## Error & Edge Case Scenarios

### E1: Summary Tier Is Not Applicable For Protected Tail Only
- **Given:** `SessionCapabilities { summarization_allowed: true, kv_fixed: false }` and session messages with IDs `["sys0", "u_tail"]` where roles are `System` and `User`
- **When:** `SummaryCompactionTier::apply()` is invoked for trigger `preflight`
- **Then:** the outcome is `NotApplicable`

### E2: `submit_summary()` Rejects A Mismatched `last_message_id`
- **Given:** a pending summary request with `last_message_id = "a0"`
- **When:** `submit_summary()` receives `SummaryResult { summary_text: "bad", last_message_id: "WRONG" }`
- **Then:** the terminal action error type is `CompactionFailed`, `tier` equals `"summary"`, and `reason` equals `"last_message_id mismatch"`

### E3: Over-Threshold Pressure With No Eligible Tier Fails Loudly
- **Given:** `AgentConfig.mid_turn_threshold_ratio = 0.90`, `AgentConfig.allow_fifo_on_mid_turn = false`, `SessionCapabilities { summarization_allowed: false, kv_fixed: false }`, no reproducible tool results, no append-block loop, and a mid-turn pressure ratio of `0.95`
- **When:** mid-turn compaction runs
- **Then:** the terminal action error type is `CompactionFailed`, `tier` equals `"pipeline"`, and `reason` equals `"No compaction tier applied while context remains over threshold"`

### E4: Missing Context Reference Read Returns Not Found
- **Given:** an empty `ContextStore`
- **When:** `read_context_reference("missing-ref")` is called
- **Then:** the returned error type is `AgentError::ContextReferenceNotFound` and `reference_id` equals `"missing-ref"`

### E5: FIFO Fails When Required Snapshot Cannot Be Created
- **Given:** `AgentConfig.require_snapshot_before_fifo = true`, `AgentConfig.enable_context_store = false`, and an over-threshold session where FIFO would remove message IDs `["u0", "a0"]`
- **When:** `FifoCompactionTier::apply()` is invoked
- **Then:** the outcome error type is `CompactionFailed`, `tier` equals `"fifo"`, and `reason` equals `"history snapshot required before fifo"`

### E6: Tool-Schema Deferral Disabled Falls Back To Full Schema JSON
- **Given:** `AgentConfig.enable_tool_schema_deferral = true`, `SessionCapabilities { tool_schema_deferral_supported: false }`, and `tools_schema_json = "[{\"name\":\"web_search\"}]"`
- **When:** `AgentEngine::next_action()` returns `CallModel`
- **Then:** `ModelRequest.tool_context_index` equals `None` and `ModelRequest.tools_schema_json` equals `"[{\"name\":\"web_search\"}]"`

### E7: Oversized Unparseable File Context Becomes Filename-Only
- **Given:** `AgentConfig.enable_file_condensation = true`, an included file `"blob.bin"` with token estimate `50000`, and content that cannot be parsed into symbols
- **When:** `condense_file_context()` processes the file
- **Then:** `FileContextSummary.path` equals `"blob.bin"`, `condensation_level` equals `filename_only`, `outline_ref` equals `None`, and `chunk_refs.len()` equals `0`

### E8: Fixed-KV Rejects History-Rewrite Plan
- **Given:** `SessionCapabilities { context_policy_tier: fixed_kv, kv_fixed: true, history_rewrite_supported: false }` and a compaction plan requesting `Summary`
- **When:** `build_compaction_plan()` is called for `preflight`
- **Then:** `ordered_tiers` does not contain `summary`, `ordered_tiers` does not contain `fifo`, and `allow_history_rewrite` equals `false`

### E9: Manual Compact Disabled By Policy Returns No-Op
- **Given:** `AgentConfig.enable_manual_compact = false` and `ManualCompactRequest { focus_instruction: "anything", force_summary: true }`
- **When:** `AgentEngine::request_manual_compact()` is called
- **Then:** the returned action kind is `ManualCompactCompleted`, `ManualCompactResult.summary_ref` equals `None`, `ManualCompactResult.history_ref` equals `None`, and drained `PreCompact` event count equals `0`

### E10: Prompt-Cache Hints Are Omitted When Unsupported
- **Given:** `SessionCapabilities { prompt_cache_supported: false }` and stable prefix token estimate `10000`
- **When:** `AgentEngine::next_action()` returns `CallModel`
- **Then:** `ModelRequest` cache hint count equals `0`

## Mutation Defense

### Lazy Implementation Risk
A lazy implementation could keep the current preflight-only engine wiring, add DTO fields, and satisfy schema tests while never preserving retrievable raw context or state continuity.

### M1: Post-Tool-Result Trigger Must Fire Without A Later Preflight
- **Given:** `AgentConfig.threshold_ratio = 0.80`, `AgentConfig.post_tool_result_threshold_ratio = 0.85`, `SessionCapabilities { summarization_allowed: true, kv_fixed: false }`, a preflight pressure ratio of `0.79`, and a tool result that raises pressure to `0.90`
- **When:** `submit_tool_results()` is called and no subsequent `next_action()` call has been made yet
- **Then:** exactly one drained `CompactionApplied` event has `trigger = post_tool_result`, and the count of drained `CompactionApplied` events with `trigger = preflight` equals `0`

### M2: FIFO-Before-Summary Ordering Is Rejected
- **Given:** an over-threshold session where `Summary` can reduce pressure below `0.80` while preserving message `"decision: keep fact"` and `Fifo` would remove that message
- **When:** preflight compaction runs
- **Then:** the first `CompactionApplied` event tier equals `summary` and the history snapshot content contains `"decision: keep fact"`

### M3: Tool Spillover Must Preserve Full Raw Output
- **Given:** a tool result with content `"BEGIN_RAW_abcdefghijklmnopqrstuvwxyz_END_RAW"` and spill threshold `10`
- **When:** tool spillover replaces the prompt-facing content with a reference preview
- **Then:** `read_context_reference(reference_id)` returns exactly `"BEGIN_RAW_abcdefghijklmnopqrstuvwxyz_END_RAW"`

### M4: State Ledger Cannot Be A Static Placeholder
- **Given:** user message `"Objective: build A. Decision: choose B."` followed by assistant message `"Decision: revise to C."`
- **When:** the ledger observes both messages
- **Then:** `StateLedger.objective` equals `"build A"` and `StateLedger.decisions` contains exactly `["choose B", "revise to C"]`

### M5: Artifact Continuation Cannot Rely Only On Synthetic System Text
- **Given:** an artifact loop for `artifactId = "art-2"` with last block `"omega"`
- **When:** artifact squash runs and the synthetic system message is later removed by summary compaction
- **Then:** `StateLedger.artifact_states[0].artifact_id` equals `"art-2"` and `StateLedger.artifact_states[0].last_block_preview` equals `"omega"`

### M6: Tool Deferral Cannot Drop Retrievability For Omitted Schemas
- **Given:** full schema JSON containing `"rare_tool"` and deferral is enabled
- **When:** `ModelRequest.tools_schema_json` is condensed
- **Then:** `search_context_references("rare_tool", 5)` returns exactly `1` reference and `read_context_reference(reference_id)` contains `"rare_tool"`

### M7: File Condensation Cannot Pretend Omitted File Content Is Available
- **Given:** file `"huge.generated.rs"` condenses to `filename_only`
- **When:** `ModelRequest.file_context_index` is built
- **Then:** `FileContextSummary.condensation_level` equals `filename_only`, `chunk_refs.len()` equals `0`, and no request message content contains the full file body
