# AGENT-009 — Compaction trait & pipeline orchestrator

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E2 — Budget & compaction |
| **Depends on** | AGENT-008 |
| **Blocks** | AGENT-010, AGENT-011, AGENT-012, AGENT-013 |

## Description

### `CompactionOutcome`

```rust
pub enum CompactionOutcome {
    Applied(AgentSession, CompactionEvent),     // tier ran, pressure relieved
    NeedsHostCall(SummaryRequest),              // Summary tier needs host callback
    NotApplicable,                              // tier cannot help (e.g., no reproducible results)
}
```

### `CompactionPipeline`

```rust
pub struct CompactionPipeline { /* ordered Vec<Box<dyn CompactionTier>> */ }

impl CompactionPipeline {
    /// Run strategies in tier order. Stop after first `Applied` that brings pressure
    /// under threshold. If a tier returns `NeedsHostCall`, bubble immediately to engine
    /// (do not run further tiers — engine will resume pipeline after host fulfills).
    /// If all tiers return `NotApplicable`, return `NotApplicable`.
    pub fn run(&self, session: AgentSession, budget: &TokenLedger, config: &AgentConfig) -> CompactionOutcome;
}
```

**Locked tier order (from ADR):** Micro (1) → FIFO (2) → ArtifactSquash (3) → Summary (4).

Registered strategies: `MicroCompact` (AGENT-010), `FifoCompact` (AGENT-011), `ArtifactSquash` (AGENT-013), `SummaryCompact` (AGENT-012).

**Locked stop policy:** first tier that returns `Applied` and brings `Pressure.ratio` below `threshold_ratio` ends the pipeline for that compaction pass. If Applied but still over budget, continue to next tier.

## Acceptance criteria

- [ ] Unit test: over-budget synthetic session → at least one `CompactionEvent` recorded
- [ ] Pipeline idempotent when already under budget (returns `NotApplicable` for all tiers)
- [ ] Strategies independently unit-testable (mock `CompactionPipeline` by passing mock tier impls)
- [ ] `NeedsHostCall` short-circuits (test: Summary tier returns NeedsHostCall → pipeline stops, Micro/FIFO/ArtifactSquash don't re-run)

## Files

- `third_party/agent_core/src/compact/mod.rs`
- `third_party/agent_core/src/compact/pipeline.rs`
