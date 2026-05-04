# Discovery: X Search Tool (search_x_recent)

## Summary

Add a new `search_x_recent` tool that enables any model provider to query near-real-time X (formerly Twitter) data via the X API v2 Recent Search endpoint. The tool operates independently from web search (separate toggle, separate API key).

## Architecture Context

### Existing Patterns

The codebase already has a web search tool (`web_search`) implemented across layers:

- **Domain**: `ToolDefinition.WEB_SEARCH`, `WebSearchParams`, `ToolExecutorPort`
- **Data**: `SearchToolExecutorImpl` → delegates to `TavilySearchRepository` or `ExaSearchRepository`
- **Data**: `CompositeToolExecutor` routes tool calls by name
- **Inference**: `ChatInferenceRequestPreparer` gates tool availability on settings
- **Prompt**: `SearchToolPromptComposer` adds tool contract to system prompt
- **Settings**: `SettingsRepository` stores `searchEnabled`, `tavilyKeyPresent`, etc.
- **UI**: `WebSearchBottomSheet` for configuration

The new X Search tool follows the exact same pattern but is completely independent.

### Key Files

| Layer | File | Role |
|-------|------|------|
| Domain | `ToolParameterModels.kt` | Add `XSearchParams` |
| Domain | `ToolDefinition.kt` | Register `X_SEARCH` tool |
| Domain | `SettingsRepository.kt` | Add `xSearchEnabled`, `xApiKeyPresent` |
| Domain | `SettingsUseCases.kt` | Add use case interfaces |
| Domain | `SearchToolPromptComposer.kt` | Add X search contract |
| Domain | `ChatInferenceRequestPreparer.kt` | Gate tool on settings |
| Data | `XSearchRepository.kt` (NEW) | HTTP client for X API v2 |
| Data | `XSearchToolExecutorImpl.kt` (NEW) | Tool executor adapter |
| Data | `CompositeToolExecutor.kt` | Add routing case |
| Data | `ApiKeyManager.kt` | Add `X_SEARCH_ALIAS` |
| Data | `SettingsRepositoryImpl.kt` | Persist X settings |
| Settings | `SettingsModels.kt` | Add UI state model |
| Settings | `SettingsUiStateFactory.kt` | Wire transient state |
| Settings | `XSearchBottomSheet.kt` (NEW) | Configuration UI |
| Settings | `SettingsScreen.kt` | Add navigation row |
| Settings | `SettingsViewModel.kt` | State management |
| Test | `FakeSettingsRepository.kt` | Add fake implementations |

### X API v2 Details

**Endpoint**: `GET https://api.x.com/2/tweets/search/recent`

**Auth**: Bearer token (`Authorization: Bearer {api_key}`)

**Query params**:
- `query` — search query (1–4096 chars, supports X search operators)
- `max_results=20` — hardcoded
- `sort_order=relevancy` — hardcoded
- `tweet.fields=author_id,created_at,public_metrics,text`
- `expansions=author_id`
- `user.fields=name,username`

**Pricing**: Pay-per-usage, user provides their own API key.

### Normalized Response Shape

The `XSearchRepository` normalizes the X API response into a clean JSON format for LLM consumption:

```json
{
  "query": "AI news",
  "result_count": 3,
  "posts": [
    {
      "id": "1346889436626259968",
      "text": "...",
      "author_name": "X Dev",
      "author_handle": "@XDevelopers",
      "created_at": "2026-05-04T18:40:40.000Z",
      "url": "https://x.com/XDevelopers/status/1346889436626259968",
      "metrics": {
        "likes": 42,
        "retweets": 10,
        "replies": 3,
        "quotes": 1
      }
    }
  ]
}
```

## Clarifying Questions

None — all resolved with the user.
