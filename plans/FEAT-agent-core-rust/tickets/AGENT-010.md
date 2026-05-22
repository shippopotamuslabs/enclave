# AGENT-010 — Tier 1: Micro-compact reproducible tool results

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E2 — Budget & compaction |
| **Depends on** | AGENT-009, AGENT-005 |
| **Blocks** | AGENT-017 |

## Description

Replace **result** messages for tools marked `reproducible = true` in the catalog with placeholder:

`"[Previous tool output cleared]"`

Keep the most recent `AgentConfig.micro_keep_recent` (default `2`) results per tool type. Older results beyond that count are replaced. Never touch user/assistant conversational turns.

Inspired by Claude Code micro-compact (reproducible tools only).

Non-reproducible tools (`start_artifact`, `append_blocks`, `manage_memories`, `generate_image`, `generate_video`, `generate_music`) are **never** micro-cleared. `append_blocks` results are handled by tier 3 (ArtifactSquash).

Returns `CompactionOutcome::Applied(session, event)` if any results were replaced; `CompactionOutcome::NotApplicable` if nothing to clear.

## Acceptance criteria

- [ ] `web_search`, `web_extract`, `search_x_recent` results cleared when older than `micro_keep_recent`; most-recent N preserved
- [ ] `append_blocks` results **not** cleared by this tier
- [ ] `CompactionEvent.tier = CompactionTier::Micro`
- [ ] Tests prove token estimate from ledger drops after apply
- [ ] `micro_keep_recent = 0` clears all reproducible results (edge case test)

## Files

- `third_party/agent_core/src/compact/micro.rs`
