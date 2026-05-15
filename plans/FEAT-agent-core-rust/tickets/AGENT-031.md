# AGENT-031 — Migrate OpenAI-compatible services to Rust loop

| Field | Value |
|-------|--------|
| **Type** | Feature |
| **Epic** | E7 — Inference migration |
| **Depends on** | AGENT-027, AGENT-029, AGENT-030, AGENT-016 |
| **Blocks** | AGENT-036 |

## Description

Replace `BaseOpenAiSdkInferenceService.executeToolingPrompt` implementation:

- When flag on: drive `AgentLoopPort` instead of `LlmToolingOrchestrator.execute`
- Remove duplicated `onContextExceeded`, `ArtifactContextCompressor` hooks from service
- Keep streaming/event emission in Kotlin transport layer

Applies to: `ApiInferenceServiceImpl`, `OpenRouterInferenceServiceImpl`, `XaiInferenceServiceImpl`.

## Acceptance criteria

- [ ] `BaseOpenAiSdkInferenceServiceTest` passes with flag on
- [ ] Mid-loop compaction continues without `IllegalStateException` when model calls more tools
- [ ] `./gradlew :feature:inference:testDebugUnitTest` green for affected tests
- [ ] `ktlintCheck` + `detekt` clean for touched files

## Files

- `feature/inference/.../BaseOpenAiSdkInferenceService.kt`
- Related OpenAI-compatible services
