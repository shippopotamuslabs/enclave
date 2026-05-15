# AGENT-033 — Migrate Llama (envelope) to Rust loop

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E7 — Inference migration |
| **Depends on** | AGENT-027, AGENT-029, AGENT-030, AGENT-047 |
| **Blocks** | AGENT-036 |

## Description

Migrate `LlamaInferenceServiceImpl` tool path when `ToolEnvelopeParser.hasLocalToolContract`:

- `SessionCapabilities { summarization_allowed: false, kv_fixed: true }`
- Implement `LlamaTransportAdapter` as a subclass/strategy of `AgentTransportAdapter` (AGENT-027)
  - Envelope parsing stays in `LlamaTransportAdapter` via `ToolEnvelopeParser.kt` (Kotlin; not moved to Rust in v1)
  - Parses `<tool_call>...</tool_call>` envelope from model text output, builds `ModelResponse.tool_calls`
- Remove duplicate truncation logic in `onToolResultsMapped` (now handled by Rust AGENT-019)

**`kv_fixed = true` behavior (per ADR):** compaction is allowed; transport accepts KV cache reset cost:
- Engine emits `AgentEvent::KvCacheInvalidated` when compaction occurs on a `kv_fixed` session
- `LlamaTransportAdapter` listens for `KvCacheInvalidated` events (drained via `RustAgentEngineAdapter`) and drops its `LlamaSession` + rebuilds the prompt from the compacted history before the next inference call
- Performance cost accepted over warn-then-crash; document cost in ticket MR description

## Acceptance criteria

- [ ] Local envelope tool loop tests pass with flag on
- [ ] No `ContextExceededResult` hard-stop at 90% — compaction fires instead
- [ ] On `KvCacheInvalidated`: `LlamaSession` is rebuilt (verify via mock in unit test)
- [ ] Tool-result messages still injected via session manager in transport

## Files

- `feature/inference/.../LlamaInferenceServiceImpl.kt`
- `feature/inference/.../agent/LlamaTransportAdapter.kt`
