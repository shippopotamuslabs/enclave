# FEAT-inference-telemetry-stats Test Specification

## Framework And Run Commands

Test stack:
- JUnit 5
- Turbine
- MockK only where a fake is impractical
- Compose UI tests with `createComposeRule()`
- Robolectric only where Android framework behavior is required

Exact module run command:

```bash
./gradlew :core:domain:testDebugUnitTest :core:data:testDebugUnitTest :feature:inference:testDebugUnitTest :feature:chat:testDebugUnitTest :feature:settings:testDebugUnitTest
```

Functions run command:

```bash
npm --prefix functions test
```

Merge-sized run command:

```bash
./gradlew testDebugUnitTest
```

## Scenario Index

| ID | Purpose |
|---|---|
| `TEL-001` | Response stats sheet shows exact remote-provider telemetry values. |
| `TEL-002` | Response stats sheet shows local-model timing values and `Not available` for unsupported fields. |
| `TEL-003` | Historical assistant messages with no telemetry render a stable unavailable state. |
| `TEL-004` | Chat top bar overflow menu preserves new-chat behavior and adds chat stats. |
| `TEL-005` | Chat stats summary aggregates totals, weighted speed, average TTFT, cache hit rate, and breakdowns correctly. |
| `TEL-006` | Historical provider/model breakdown uses persisted telemetry, not the current default model assignment. |
| `TEL-007` | Settings `Usage & Spend` lifetime view shows provider spend totals. |
| `TEL-008` | Settings `Current Month` filter excludes prior-month telemetry rows. |
| `TEL-009` | Exact versus estimated cost is labeled correctly at the response level. |
| `TEL-010` | Persisted telemetry survives Room rehydrate and feeds response/chat/settings surfaces. |
| `TEL-011` | Zero-data settings view renders a deterministic empty state. |
| `TEL-012` | Missing-cost rows do not get coerced to zero in totals and are counted explicitly. |
| `TEL-013` | Multiple model-request telemetry rows aggregate into one response and chat summary. |
| `TEL-014` | Telemetry rows persist app version and schema version for fleet diagnostics. |
| `TEL-015` | Provider daily rollup detects cache-hit-rate changes by app version. |

## Shared Fixture Values

Unless a scenario states otherwise, use these persisted model-request telemetry rows:

| Row | Message | Sequence | Completed At | Provider | Model | Input | Output | Reasoning | Cache Read | Cache Write | Cache Create | TTFT | Speed | Cost | Cost Status | App Version |
|---|---|---:|---|---|---|---:|---:|---:|---:|---:|---:|---|---|---|---|---|
| `tel-openai-1` | `m-openai-1` | `0` | `2026-05-10T14:00:05Z` | `OpenAI` | `gpt-4.1` | `1000` | `200` | `80` | `400` | `0` | `0` | `400 ms` | `50.0 tok/s` | `$0.002500` | `Exact` | `1.4.0` |
| `tel-anthropic-1` | `m-anthropic-1` | `0` | `2026-05-10T14:02:02Z` | `Anthropic` | `claude-sonnet-4-20250514` | `800` | `100` | `50` | `0` | `0` | `300` | `600 ms` | `50.0 tok/s` | `$0.003000` | `Estimated` | `1.4.0` |
| `tel-litert-1` | `m-litert-1` | `0` | `2026-05-10T14:04:02Z` | `LiteRT` | `DeepSeek-R1-0528-Qwen3-8B` | `Not available` | `60` | `Not available` | `Not available` | `Not available` | `Not available` | `200 ms` | `30.0 tok/s` | `Unknown` | `Unavailable` | `1.4.0` |
| `tel-openrouter-1` | `m-openrouter-1` | `0` | `2026-04-14T09:30:03Z` | `OpenRouter` | `openrouter/deepseek-r1` | `500` | `80` | `20` | `100` | `20` | `0` | `300 ms` | `40.0 tok/s` | `$0.001200` | `Exact` | `1.3.0` |

Derived aggregate values for `chat-1` using `m-openai-1`, `m-anthropic-1`, and `m-litert-1`:
- Total cost: `$0.005500`
- Average TTFT: `400 ms`
- Weighted output speed: `45.0 tok/s`
- Output tokens: `360`
- Reasoning tokens: `130`
- Cache hit rate: `22.2%`

Derived aggregate values for lifetime settings using all four rows:
- Total cost: `$0.006700`
- Current month total on `2026-05-25`: `$0.005500`
- Lifetime OpenRouter spend: `$0.001200`

## Happy Path Scenarios

### Scenario: `TEL-001` Response stats sheet shows exact remote-provider telemetry values
Given assistant message `m-openai-1` is complete and has persisted telemetry
When the user taps the stats icon on that assistant response
Then the bottom sheet title is `Response statistics`
And the `Provider` row value is `OpenAI`
And the `Model` row value is `gpt-4.1`
And the `Time to first token` row value is `400 ms`
And the `Output speed` row value is `50.0 tok/s`
And the `Output tokens` row value is `200`
And the `Reasoning tokens` row value is `80`
And the `Input tokens` row value is `1000`
And the `Cache read input tokens` row value is `400`
And the `Cost` row value is `$0.002500`
And the `Cost status` row value is `Exact`

### Scenario: `TEL-004` Chat top bar overflow menu preserves new-chat behavior and adds chat stats
Given the chat top bar is visible for `chat-1`
When the user taps the overflow icon
Then the popup menu items are `New chat` and `View chat stats` in that order
When the user taps `New chat`
Then `viewModel.createNewChat()` is invoked exactly once
When the user taps `View chat stats`
Then the bottom sheet title is `Chat statistics`

### Scenario: `TEL-005` Chat stats summary aggregates totals and breakdowns correctly
Given `chat-1` contains `m-openai-1`, `m-anthropic-1`, and `m-litert-1`
When the user opens `Chat statistics`
Then the `Total cost` row value is `$0.005500`
And the `Average TTFT` row value is `400 ms`
And the `Average output speed` row value is `45.0 tok/s`
And the `Output tokens` row value is `360`
And the `Reasoning tokens` row value is `130`
And the `Cache hit rate` row value is `22.2%`
And the provider breakdown contains the row `OpenAI | $0.002500 | 1 turn`
And the provider breakdown contains the row `Anthropic | $0.003000 | 1 turn`
And the provider breakdown contains the row `LiteRT | Unknown | 1 turn`
And the model breakdown contains the row `gpt-4.1 | $0.002500 | 200 output tokens`
And the model breakdown contains the row `claude-sonnet-4-20250514 | $0.003000 | 100 output tokens`
And the model breakdown contains the row `DeepSeek-R1-0528-Qwen3-8B | Unknown | 60 output tokens`

### Scenario: `TEL-007` Settings lifetime view shows provider spend totals
Given telemetry rows `m-openai-1`, `m-anthropic-1`, `m-litert-1`, and `m-openrouter-1` are persisted
When the user opens `Usage & Spend`
Then the selected period label is `Lifetime`
And the `Total spend` row value is `$0.006700`
And the provider list contains the row `OpenAI | $0.002500 | 1 turn`
And the provider list contains the row `Anthropic | $0.003000 | 1 turn`
And the provider list contains the row `OpenRouter | $0.001200 | 1 turn`
And the provider list contains the row `LiteRT | Unknown | 1 turn`

### Scenario: `TEL-008` Settings current-month filter excludes prior-month rows
Given the injected clock instant is `2026-05-25T12:00:00Z`
And the injected device zone is `America/New_York`
And telemetry rows `m-openai-1`, `m-anthropic-1`, `m-litert-1`, and `m-openrouter-1` are persisted
When the user switches the `Usage & Spend` period to `Current Month`
Then the `Total spend` row value is `$0.005500`
And the provider list does not contain the row `OpenRouter | $0.001200 | 1 turn`
And the provider list contains the row `OpenAI | $0.002500 | 1 turn`
And the provider list contains the row `Anthropic | $0.003000 | 1 turn`

## Error And Edge Scenarios

### Scenario: `TEL-002` Response stats sheet shows local-model timing values and unavailable fields
Given assistant message `m-litert-1` is complete and has persisted local telemetry
When the user taps the stats icon on that assistant response
Then the `Provider` row value is `LiteRT`
And the `Model` row value is `DeepSeek-R1-0528-Qwen3-8B`
And the `Time to first token` row value is `200 ms`
And the `Output speed` row value is `30.0 tok/s`
And the `Output tokens` row value is `60`
And the `Input tokens` row value is `Not available`
And the `Reasoning tokens` row value is `Not available`
And the `Cache read input tokens` row value is `Not available`
And the `Cost` row value is `Unknown`
And the `Cost status` row value is `Unavailable`

### Scenario: `TEL-003` Historical assistant messages with no telemetry render a stable unavailable state
Given assistant message `m-legacy-1` is complete and has no telemetry row
When the user taps the stats icon on that assistant response
Then the bottom sheet title is `Response statistics`
And the availability message is `Telemetry unavailable for this response`
And the `Provider` row value is `Not available`
And the `Cost` row value is `Not available`

### Scenario: `TEL-009` Exact versus estimated cost is labeled correctly
Given assistant message `m-anthropic-1` is complete and its cost comes from the local pricing catalog
When the user taps the stats icon on that assistant response
Then the `Cost` row value is `$0.003000`
And the `Cost status` row value is `Estimated`
And the `Cost source` row value is `Local pricing table`

### Scenario: `TEL-010` Persisted telemetry survives Room rehydrate and feeds all three surfaces
Given telemetry rows for `chat-1` are persisted in Room
And the app process is destroyed and recreated
When `ChatViewModel` reloads `chat-1`
Then assistant message `m-openai-1` still opens `Response statistics` with `Cost` value `$0.002500`
When the user opens `Chat statistics`
Then the `Total cost` row value is `$0.005500`
When the user opens settings `Usage & Spend`
Then the `Total spend` row value is `$0.006700`

### Scenario: `TEL-011` Zero-data settings view renders a deterministic empty state
Given no telemetry rows are persisted
When the user opens `Usage & Spend`
Then the `Total spend` row value is `$0.000000`
And the empty-state message is `No usage recorded`
And the provider list item count is `0`

### Scenario: `TEL-012` Missing-cost rows do not get coerced to zero in totals and are counted explicitly
Given telemetry rows `m-openai-1`, `m-anthropic-1`, and `m-litert-1` are persisted
When the user opens settings `Usage & Spend` for `Current Month`
Then the `Total spend` row value is `$0.005500`
And the `Missing cost turns` row value is `1`
And the provider row `LiteRT | Unknown | 1 turn` is present

### Scenario: `TEL-013` Multiple model-request telemetry rows aggregate into one response and chat summary
Given assistant message `m-agent-1` is complete
And telemetry row `tel-agent-1` has `Message = m-agent-1`, `Sequence = 0`, `Provider = OpenAI`, `Model = gpt-4.1`, `Output = 40`, `TTFT = 300 ms`, `Speed = 20.0 tok/s`, and `Cost = $0.001000`
And telemetry row `tel-agent-2` has `Message = m-agent-1`, `Sequence = 1`, `Provider = OpenAI`, `Model = gpt-4.1`, `Output = 60`, `TTFT = 500 ms`, `Speed = 30.0 tok/s`, and `Cost = $0.002000`
When the user taps the stats icon on assistant response `m-agent-1`
Then the `Model requests` row value is `2`
And the `Output tokens` row value is `100`
And the `Cost` row value is `$0.003000`
And the `Average TTFT` row value is `400 ms`
And the `Output speed` row value is `25.0 tok/s`
When the user opens `Chat statistics` for the chat containing `m-agent-1`
Then the `Total cost` row value is `$0.003000`
And the provider breakdown contains the row `OpenAI | $0.003000 | 1 turn | 2 requests`

### Scenario: `TEL-014` Telemetry rows persist app version and schema version for fleet diagnostics
Given the app version name is `1.4.0`
And the app version code is `140`
And the telemetry schema version is `1`
When telemetry row `tel-openai-1` is persisted
Then the stored `app_version_name` value is `1.4.0`
And the stored `app_version_code` value is `140`
And the stored `telemetry_schema_version` value is `1`
And the synced Firestore document field `appVersionName` is `1.4.0`
And the synced Firestore document field `appVersionCode` is `140`
And the synced Firestore document field `telemetrySchemaVersion` is `1`

### Scenario: `TEL-015` Provider daily rollup detects cache-hit-rate changes by app version
Given raw telemetry row `tel-google-old-1` has `Provider = Google`, `Model = gemini-2.5-flash`, `App Version = 1.3.0`, `Input = 1000`, and `Cache Read = 700`
And raw telemetry row `tel-google-old-2` has `Provider = Google`, `Model = gemini-2.5-flash`, `App Version = 1.3.0`, `Input = 1000`, and `Cache Read = 500`
And raw telemetry row `tel-google-new-1` has `Provider = Google`, `Model = gemini-2.5-flash`, `App Version = 1.4.0`, `Input = 1000`, and `Cache Read = 100`
And raw telemetry row `tel-google-new-2` has `Provider = Google`, `Model = gemini-2.5-flash`, `App Version = 1.4.0`, `Input = 1000`, and `Cache Read = 0`
When the provider daily rollup runs for date `2026-05-10`
Then the rollup for `Google | gemini-2.5-flash | 1.3.0` has `requestCount = 2`
And the rollup for `Google | gemini-2.5-flash | 1.3.0` has `inputTokens = 2000`
And the rollup for `Google | gemini-2.5-flash | 1.3.0` has `cacheReadInputTokens = 1200`
And the rollup for `Google | gemini-2.5-flash | 1.3.0` has `cacheHitRatePercent = 60.0`
And the rollup for `Google | gemini-2.5-flash | 1.4.0` has `requestCount = 2`
And the rollup for `Google | gemini-2.5-flash | 1.4.0` has `inputTokens = 2000`
And the rollup for `Google | gemini-2.5-flash | 1.4.0` has `cacheReadInputTokens = 100`
And the rollup for `Google | gemini-2.5-flash | 1.4.0` has `cacheHitRatePercent = 5.0`

## Mutation-Defense Scenario

### Mutation Risk
Lazy implementations may derive provider/model labels from the current active model assignment instead of the persisted telemetry row. That would rewrite historical stats after the user changes providers.

### Scenario: `TEL-006` Historical provider/model breakdown uses persisted telemetry, not the current default model assignment
Given message `m-openai-1` was generated with persisted telemetry `Provider = OpenAI` and `Model = gpt-4.1`
And the current default model assignment is changed to `Provider = xAI` and `Model = grok-4.3`
When the user opens `Response statistics` for `m-openai-1`
Then the `Provider` row value is `OpenAI`
And the `Model` row value is `gpt-4.1`
When the user opens `Chat statistics`
Then the provider breakdown contains the row `OpenAI | $0.002500 | 1 turn`
And the provider breakdown does not contain the row `xAI | $0.002500 | 1 turn`
