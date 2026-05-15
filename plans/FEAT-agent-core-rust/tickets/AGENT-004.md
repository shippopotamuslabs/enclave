# AGENT-004 — Core session & message types

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E1 — Rust domain model |
| **Depends on** | AGENT-002 |
| **Blocks** | AGENT-008, AGENT-017 |

## Description

Implement pure Rust types (serde-ready, no UniFFI yet):

- `AgentRole`: System, User, Assistant, Tool, CompactSummary
- `AgentMessage { id: String, role: AgentRole, content: String, metadata: serde_json::Value }`
  - `metadata` is opaque host-provided data (e.g., vision asset refs, turn tags); defaults to `Value::Null`
- `RepairRecord { original_name: String, applied_aliases: Vec<String>, confidence: f32 }`
- `ToolCallRecord { call_id: String, name: String, arguments_json: String, repair_metadata: Option<RepairRecord> }`
- `ToolResultRecord { call_id: String, result_json: String, truncated: bool, soft_failure: bool }`
- `AgentSession { messages: Vec<AgentMessage>, tool_trace: Vec<ToolCallRecord>, compaction_log: VecDeque<CompactionEvent>, ledger_snapshot: Option<Pressure>, session_context: serde_json::Value }`
  - `compaction_log` bounded to the last 32 entries (ring buffer via `VecDeque` + truncate-on-push)
  - `session_context` is opaque host-provided context (e.g., `{ "chatId": "...", "userMessageId": "...", "assistantMessageId": "..." }`); Kotlin adapter reads this to plumb IDs through to `ToolCallRequest` (see AGENT-029)
- `AgentConfig { max_turns: u32, threshold_ratio: f32 (default 0.80), micro_keep_recent: u32 (default 2), tool_retry_limit: u32 (default 3), greedy_append_blocks: bool (default true), tool_result_budget_cloud_bytes: usize (default 4096), token_correction: HashMap<String, f32> }`
- `SessionCapabilities { summarization_allowed: bool, kv_fixed: bool, tool_result_budget_tokens: Option<u32> }`
  - `tool_result_budget_tokens`: when `Some`, overrides cloud byte budget for local model per-result truncation
  - `kv_fixed = true` means transport must handle `AgentEvent::KvCacheInvalidated` and rebuild KV cache after compaction (Llama, LiteRT; perf cost accepted over crash — see ADR)

## Acceptance criteria

- [ ] Round-trip `serde_json` tests for all public types
- [ ] `AgentSession.compaction_log` push respects 32-entry cap (test: push 33, assert len==32, oldest dropped)
- [ ] `session_context` serializes/deserializes as opaque `Value` (no required keys)
- [ ] No Android/UniFFI dependencies
- [ ] Module docs explain mapping to Kotlin `ChatMessage` / `ToolCallRequest` (future adapters)

## Files

- `third_party/agent_core/src/session.rs`
- `third_party/agent_core/src/message.rs`
