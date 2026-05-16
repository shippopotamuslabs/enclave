# AGENT-045 — Performance & memory benchmark

| Field | Value |
|-------|--------|
| **Type** | Test |
| **Epic** | E11 — QA & release |
| **Depends on** | AGENT-036 |
| **Blocks** | AGENT-043 |

## Description

Benchmark JNI / agent loop overhead **before AGENT-043 (legacy deletion)** so comparison data is captured:

- 20-turn synthetic tool loop with flag on (Rust engine)
- Comparison run with flag off (legacy `LlmToolingOrchestrator`) — must run before AGENT-043 deletes the legacy path
- Verify no main-thread blocking in `AgentTransportAdapter` (transport on `Dispatchers.IO`)

Capture per run: median turn latency (ms), native heap delta (KB), allocation hotspot count.

**JNI churn budget (locked in ADR AGENT-001):** `< 5 ms/turn` p50 JNI overhead. If exceeded: file follow-up tickets before AGENT-043 merges.

## Acceptance criteria

- [ ] Results documented in `plans/FEAT-agent-core-rust/benchmarks.md` with both flag-on and flag-off numbers
- [ ] No ANR in manual checklist for artifact generation on device with flag on
- [ ] Rust median turn latency ≤ legacy + 5 ms (net JNI overhead ≤ budget)
- [ ] AGENT-043 must not merge until this ticket's MR is reviewed

## Files

- `plans/FEAT-agent-core-rust/benchmarks.md` (create with results)
