# AGENT-013 — Tier 3: Artifact squash strategy

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E2 — Budget & compaction |
| **Depends on** | AGENT-009, AGENT-005 |
| **Blocks** | AGENT-017 |

## Description

Port `ArtifactContextCompressor` to Rust. Runs as tier 3 (after FIFO, before Summary) because it is synchronous and may significantly reduce tokens when an artifact loop is in progress.

- Detect `append_blocks` tool calls and their bulky success results in session history
- Remove them from model-visible history
- Insert a system summary message: "Artifact generation in progress. [N] blocks written so far. Last block: `[last 200 chars of last appended block content]`. Continue appending."

Returns `CompactionOutcome::Applied(session, event)` if any `append_blocks` results were squashed; `CompactionOutcome::NotApplicable` if no artifact loop detected.

Replaces the Kotlin special-case in `BaseOpenAiSdkInferenceService.onContextExceeded` after migration (AGENT-043).

**Snippet length:** last 200 chars of last appended block content (truncated from end if longer, empty string if no blocks).

## Acceptance criteria

- [ ] Tests based on `ArtifactContextCompressor.kt` behavior
- [ ] `CompactionEvent.tier = CompactionTier::ArtifactSquash`
- [ ] Snippet capped at 200 chars
- [ ] `start_artifact` calls preserved in history (only `append_blocks` results squashed)
- [ ] Returns `NotApplicable` when no `append_blocks` in session

## Files

- `third_party/agent_core/src/compact/artifact_squash.rs`
