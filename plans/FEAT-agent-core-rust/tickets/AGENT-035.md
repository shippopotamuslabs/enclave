# AGENT-035 — CREW / catalog alignment (deferred)

| Field | Value |
|-------|--------|
| **Type** | Design |
| **Epic** | E7 — Inference migration |
| **Depends on** | AGENT-005 |
| **Blocks** | — |
| **Status** | deferred |

## Description

CREW pipeline (`InferenceServicePipelineExecutor`) runs sequential `sendPrompt` without the full agent loop today. CREW is a chat **mode** (Mixture-of-Agents), not a tool catalog.

**Decision: Option A — deferred.** `tool_catalog.json` is source of truth for the agent loop; CREW's non-tool pipeline is out of scope for v1 migration. If CREW steps ever use tools in the future, they will gain repair automatically via the shared catalog.

No code change required. ADR documents this deferral.

## Acceptance criteria

- [ ] ADR section (AGENT-001) states CREW deferred and why
- [ ] Ticket closed with `deferred` label

## Files

- `plans/FEAT-agent-core-rust/ADR.md` (AGENT-001 adds the ADR section)
