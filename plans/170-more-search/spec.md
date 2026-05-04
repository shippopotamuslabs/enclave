# Spec: X Search Tool (search_x_recent)

## Overview

Add a new `search_x_recent` tool enabling provider-agnostic X (Twitter) data retrieval via X API v2. Independent from web search — separate toggle, separate API key.

---

## Implementation Order

### Phase 1: Domain Layer

1. **ToolParameterModels.kt** — Add `XSearchParams`:
   ```kotlin
   @Serializable
   data class XSearchParams(
       @ToolParam(description = "The search query for X (Twitter). Supports standard X search operators.")
       val query: String,
   ) : ToolParameters
   ```

2. **ToolDefinition.kt** — Register `X_SEARCH`:
   ```kotlin
   val X_SEARCH = ToolDefinition(
       name = "search_x_recent",
       description = "Search recent posts on X (formerly Twitter) for near-real-time information, breaking news, trending topics, and public opinions. Returns posts from the last 7 days with engagement metrics.",
       parametersClass = XSearchParams::class,
   )
   ```
   Add to `ALL_TOOLS` list.

3. **SettingsRepository.kt** — Extend `SettingsData` and `SettingsRepository`:
   - `SettingsData`: add `xSearchEnabled: Boolean = false`, `xApiKeyPresent: Boolean = false`
   - `SettingsRepository`: add `updateXSearchEnabled(enabled)`, `saveXApiKey(apiKey)`, `clearXApiKey()`

4. **New use cases** — `UpdateXSearchEnabledUseCase`, `SaveXApiKeyUseCase`, `ClearXApiKeyUseCase` (single-method, delegates to repo)

5. **SettingsUseCases.kt** — Add to `SettingsPreferencesUseCases` interface

6. **SettingsUseCasesImpl** — Wire implementations

7. **SearchToolPromptComposer.kt** — Add `includeXSearchTool` param + `xSearchToolContract()` method

8. **ChatInferenceRequestPreparer.kt** — Gate `X_SEARCH` on `settings.xSearchEnabled`

9. **FakeSettingsRepository.kt** — Add fake implementations for new methods

### Phase 2: Data Layer

10. **ApiKeyManager.kt** — Add `const val X_SEARCH_ALIAS = "x_search"`

11. **XSearchRepository.kt** (NEW) — HTTP adapter for X API v2:
    - `GET https://api.x.com/2/tweets/search/recent`
    - Query params: `query`, `max_results=20`, `sort_order=relevancy`, `tweet.fields=author_id,created_at,public_metrics,text`, `expansions=author_id`, `user.fields=name,username`
    - Auth: `Bearer {apiKeyManager.get(X_SEARCH_ALIAS)}`
    - Response normalization: join `data[].author_id` → `includes.users[]`
    - Return normalized JSON: `{"query":"...","result_count":N,"posts":[{"id","text","author_name","author_handle","created_at","url","metrics"}]}`

12. **XSearchToolExecutorImpl.kt** (NEW) — Implements `ToolExecutorPort`:
    - Validate tool name
    - Check `settings.xSearchEnabled`
    - Decode `XSearchParams`
    - Delegate to `XSearchRepository.search(query)`

13. **CompositeToolExecutor.kt** — Add `xSearchToolExecutor` + routing case

14. **SettingsRepositoryImpl.kt** — Add DataStore keys + implement new methods

15. **DataModule.kt** — Bind new repository and executor in Hilt

### Phase 3: Settings UI

16. **SettingsModels.kt** — Add `XSearchEditorUiState`, `isXSearchSheetOpen`

17. **SettingsUiStateFactory.kt** — Add `XSearchTransientState`, wire into `create()`

18. **ic_x_logo.xml** (NEW) — Vector drawable for X logo

19. **XSearchBottomSheet.kt** (NEW) — Configuration bottom sheet

20. **SettingsScreen.kt** — Add X Search row + bottom sheet invocation

21. **SettingsViewModel.kt** — State management for X Search settings

### Phase 4: ToolEnvelopeParser

22. **ToolEnvelopeParser.kt** — Add `X_SEARCH` case to `extractLocalToolEnvelope()` switch

---

## X API v2 Request/Response

### Request
```
GET https://api.x.com/2/tweets/search/recent
  ?query=AI%20news
  &max_results=20
  &sort_order=relevancy
  &tweet.fields=author_id,created_at,public_metrics,text
  &expansions=author_id
  &user.fields=name,username
Authorization: Bearer xai-key-abc
```

### Raw Response (relevant parts)
```json
{
  "data": [
    {
      "id": "1346889436626259968",
      "text": "...",
      "author_id": "2244994945",
      "created_at": "2026-05-04T18:40:40.000Z",
      "public_metrics": {
        "retweet_count": 10,
        "reply_count": 3,
        "like_count": 42,
        "quote_count": 1
      }
    }
  ],
  "includes": {
    "users": [
      { "id": "2244994945", "name": "X Dev", "username": "XDevelopers" }
    ]
  },
  "meta": { "result_count": 1 }
}
```

### Normalized Output
```json
{
  "query": "AI news",
  "result_count": 1,
  "posts": [
    {
      "id": "1346889436626259968",
      "text": "...",
      "author_name": "X Dev",
      "author_handle": "@XDevelopers",
      "created_at": "2026-05-04T18:40:40.000Z",
      "url": "https://x.com/XDevelopers/status/1346889436626259968",
      "metrics": { "likes": 42, "retweets": 10, "replies": 3, "quotes": 1 }
    }
  ]
}
```

---

## Verification

```bash
./gradlew :domain:assemble
./gradlew :data:assemble
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
```
