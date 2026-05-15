# AGENT-034 — LiteRT: policy bridge (partial unification)

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E7 — Inference migration |
| **Depends on** | AGENT-014, AGENT-015, AGENT-019, AGENT-030, AGENT-047 |
| **Blocks** | AGENT-036 |

## Description

LiteRT SDK retains native `automaticToolCalling` loop for v1. Unify **policies** via Rust FFI helpers exposed as a small `policy_bridge` module (separate from the main `AgentEngine` FFI surface):

```rust
// third_party/agent_core/src/ffi/policy_bridge.rs
pub fn repair_tool(name: &str, args_json: &str) -> RepairResult;
pub fn truncate_result(json: &str, budget_bytes: usize) -> String;
pub fn should_allow_tool(soft_failure_streak: u32, max_streak: u32) -> bool;
```

Implement `LiteRtAgentPolicyBridge` in `:core:agent` calling these exported Rust functions.

Update `ConversationManagerImpl.executeToolSafely` to use bridge instead of duplicated `LocalToolResultPolicy` where overlapping.

**`kv_fixed = true` behavior (same as AGENT-033):** when `ConversationManagerImpl` detects context pressure, it calls `truncate_result` and clears reproducible tool results instead of setting `contextFullWarned` and throwing. Transport is responsible for rebuilding prompt context.

**Artifact auto-finalize:** deferred — `ConversationManagerImpl` artifact loop gap is documented in the ADR but not implemented in v1. File a follow-up ticket if needed post-release.

Document in ADR why full loop unification is deferred for LiteRT (native SDK owns the turn loop).

## Acceptance criteria

- [ ] Fuzzy tool-name repair works for LiteRT native tool calls via `repair_tool`
- [ ] `contextFullWarned` hard behavior replaced with `truncate_result` + reproducible-clear policy where KV allows
- [ ] `ConversationManagerImpl` tests pass
- [ ] Artifact auto-finalize gap noted in ADR as a follow-up item (not implemented here)
- [ ] `policy_bridge.rs` FFI exports compile alongside main engine FFI

## Files

- `feature/inference/.../ConversationManagerImpl.kt`
- `core/agent/.../LiteRtAgentPolicyBridge.kt`
- `third_party/agent_core/src/ffi/policy_bridge.rs`
