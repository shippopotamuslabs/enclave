# AGENT-027 — `AgentTransportAdapter` (inference services)

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E6 — Kotlin adapters |
| **Depends on** | AGENT-026, AGENT-007, AGENT-019 |
| **Blocks** | AGENT-031, AGENT-032, AGENT-033 |

## Description

Implement `AgentTransportCallback` in `:feature:inference`:

- Input: Rust `ModelRequest`
- Perform one provider inference pass (buffer response for v1 — streaming stays in Kotlin event bus but does not change the callback return type)
- Output: `ModelResponse` with `RawToolCall` list

Reuse existing mappers (`OpenAiRequestMapper`, Anthropic mapper, Google mapper) and existing history-to-provider-format conversion.

**Provider boundary for this ticket:** OpenAI-compatible (ApiInferenceServiceImpl, OpenRouterInferenceServiceImpl, XaiInferenceServiceImpl), Anthropic, Google/Gemini. Each as a subclass or strategy of a shared `BaseAgentTransportAdapter`.

**Llama envelope path is out of scope here.** Llama gets its own `LlamaTransportAdapter` subclass in AGENT-033, reusing `ToolEnvelopeParser` for the local envelope contract.

`allow_tool_call = false` must result in tools being stripped from the provider request (do not pass `tools_schema_json` to the provider when false).

Engine owns compaction — transport adapter must **not** replicate any `onContextExceeded` / context-exceeded logic.

## Acceptance criteria

- [ ] Vertical slice test: single tool turn round-trip through adapter (mock inference service)
- [ ] `allow_tool_call = false` → provider request has no tools
- [ ] No `ContextExceededResult` or `ChatHistoryRehydrator` calls in this adapter
- [ ] Anthropic, Google, OpenAI-compatible paths each have a test

## Files

- `feature/inference/.../agent/AgentTransportAdapter.kt`
- Provider-specific helper classes as needed
