# Test Spec: LLM tool self-heal

## Test Framework & Run Command

Framework: JUnit 5 + MockK + kotlinx-coroutines-test. Run: `./gradlew :core:domain:testDebugUnitTest --no-daemon`

## Scenario Index

- S-ORCH-1 — Soft budget disables further tool calls
- S-ORCH-2 — Hard failure resets soft streak
- S-ORCH-3 — Parallel all-error exhaustion warning
- S-ORCH-4 — Finished count excludes auto-finalize
- S-ORCH-5 — Trimmed padded tool name reaches executor
- S-ENV-1 — Single-quoted tool_call JSON
- S-ENV-2 — Smart quotes in query string normalized in argumentsJson

## Happy Path Scenarios

### S-ORCH-5

- **Given:** A `ToolCallRequest` whose `toolName` is `" web_search "` (padded) and valid arguments JSON
- **When:** `LlmToolingOrchestrator.execute` runs one inference pass with that tool call
- **Then:** `ToolExecutorPort.execute` receives exactly one request whose `toolName` equals `web_search` (no surrounding whitespace)

### S-ENV-1

- **Given:** Raw assistant text `<tool_call> {'name': 'web_search', 'arguments': {'query': 'weather in London'}} </tool_call>`
- **When:** `ToolEnvelopeParser.extractLocalToolEnvelope` is invoked
- **Then:** Non-null envelope with `toolName` `web_search` and `argumentsJson` exactly `{"query":"weather in London"}`

### S-ENV-2

- **Given:** Raw assistant text with JSON `"query"` value visually `cost of “bread” in Paris` using Unicode U+201C and U+201D around `bread`
- **When:** `ToolEnvelopeParser.extractLocalToolEnvelope` is invoked
- **Then:** `argumentsJson` exactly `{"query":"cost of \"bread\" in Paris"}`

## Error & Edge Case Scenarios

### S-ORCH-1

- **Given:** Stub executor always returns `ToolExecutionResult` whose `resultJson` is `{"error":"validation_failed"}` without throwing
- **When:** Four sequential inference passes each detect the same single tool call and the fourth pass returns a response with no tool calls
- **Then:** The fourth `onInferencePass` invocation receives `allowToolCall == false`

### S-ORCH-2

- **Given:** Stub executor returns soft error JSON for calls 1,2, throws on call 3, then returns soft error JSON for calls 4,5,6
- **When:** Seven inference passes occur (six with tools, seventh without)
- **Then:** The seventh `onInferencePass` receives `allowToolCall == false`

### S-ORCH-3

- **Given:** Stub executor always returns `{"error":"invalid_arguments"}` for two distinct `web_search` calls in parallel
- **When:** Three passes each emit both calls, then a fourth pass emits none
- **Then:** `LoggingPort.warning` is invoked at least once with message containing `validation retry exhausted` (case-insensitive) for tag `SelfHealTag`

### S-ORCH-4

- **Given:** A flow that calls `start_artifact` then `append_blocks` with `isComplete=false`, then ends without further tools so auto-finalize runs
- **When:** `onFinished` runs
- **Then:** `onToolResultsMapped` was invoked exactly twice and `onFinished` receives `toolCallCount == 2` and `lastResponse` indicating the terminal no-tool pass

## Mutation Defense

### Lazy Implementation Risk

`allowToolCall` ignores soft streaks and only mirrors recursion/context flags.

### M1: Defense Scenario

- **Given:** Three consecutive single-tool turns each returning only `{"error":"x"}` JSON from the executor
- **When:** The fourth `onInferencePass` runs
- **Then:** `allowToolCall` is `false`
