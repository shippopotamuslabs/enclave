# Agent loop benchmarks (AGENT-045)

AGENT-045 is deferred by decision: there are no current users to gate on, so benchmarks are not merge-blocking for this migration.

Targets from the feature README:

| Metric | Target |
|--------|--------|
| JNI / policy bridge | < 5 ms p50 per turn (policy-only calls) |

## How to run (local)

1. Build a debug APK with Rust agent enabled (`useRustAgentLoop` flag in DataStore — internal QA).
2. Capture `SystemClock.elapsedRealtimeNanos()` around `repairTool` / engine `nextAction` hot paths on a physical device.
3. Record p50 / p95 in this file when cutting over from legacy `LlmToolingOrchestrator` (AGENT-043).

## E2E (AGENT-044)

Artifact tool loop instrumented test is pending full provider migration (AGENT-031+). Track in issue tracker when `AgentLoopPort` backs production inference paths.
