# AGENT-043 — Remove deprecated Kotlin compaction/repair code

| Field | Value |
|-------|--------|
| **Type** | Cleanup |
| **Epic** | E10 — Desktop & documentation |
| **Depends on** | AGENT-036, AGENT-037 |
| **Blocks** | AGENT-044 |

## Description

Sole owner of legacy deletion (AGENT-036 does not delete code — it only flips the flag and removes routing).

Delete or reduce to thin wrappers:

- `LlmToolingOrchestrator.kt` — delete entirely
- `LlmToolingOrchestratorTest.kt` — migrate critical scenarios to Rust fixtures (AGENT-020) or delete (scenarios already ported in AGENT-015, AGENT-016, AGENT-020)
- `ContextExceededResult` — delete (no longer used after routing branch removed)
- Duplicated `ArtifactContextCompressor` usage in `BaseOpenAiSdkInferenceService.onContextExceeded` lambda — delete
- Dead `onContextExceeded` lambdas in all inference services — delete
- Overlapping `LocalToolResultPolicy` — delete cloud path; keep only the LiteRT-specific shim required by AGENT-034

**Keep:** `ToolEnvelopeParser.kt` — still used by `LlamaTransportAdapter` for local envelope parsing in v1.

Run AGENT-045 benchmarks **before** merging this ticket.

## Acceptance criteria

- [ ] `rg LlmToolingOrchestrator` returns zero production references
- [ ] `ktlintCheck` + `detekt` clean for all touched files
- [ ] No dead `onContextExceeded` lambdas remain in inference services
- [ ] `ToolEnvelopeParser.kt` retained
- [ ] AGENT-045 benchmark results exist before this MR merges

## Files

- `core/domain/.../LlmToolingOrchestrator.kt` (delete)
- `core/domain/.../LlmToolingOrchestratorTest.kt` (delete or migrate)
- Various under `feature/inference`
