# Test Spec: X Pre-Retrieval Query Shaping

**Ticket ID:** `FEAT-x-pre-retrieval-query-shaping`
**Date:** `2026-05-25`
**Test Framework:** JUnit 5 + MockK
**Exact Run Command:** 
```bash
./gradlew :core:domain:testDebugUnitTest --tests "com.browntowndev.enclave.domain.usecase.inference.DeriveXSearchParamsFromPromptUseCaseTest" --tests "com.browntowndev.enclave.domain.usecase.chat.ChatInferenceRequestPreparerXSearchPreRetrievalTest" --tests "com.browntowndev.enclave.domain.util.WebSearchResultParserTest"
```

---

## 1. Scenario Index

| Scenario ID | Category | Title |
|-------------|----------|-------|
| **XPRE-01** | Happy Path | Parseable X status URL triggers deterministic thread retrieval, bypassing classifier. |
| **XPRE-02** | Happy Path | Parseable Twitter status URL triggers deterministic thread retrieval, bypassing classifier. |
| **XPRE-03** | Happy Path | Prompt shapes keyword query and triggers recent search pre-retrieval when intent classifier fires. |
| **XPRE-04** | Edge Case | Long conversational prompt without entities skips pre-retrieval. |
| **XPRE-05** | Edge Case | X URL is present but X search skill is not configured, skipping pre-retrieval. |
| **XPRE-06** | Edge Case | Prompt has search intent but classifier resolves to false, skipping pre-retrieval. |
| **XPRE-07** | Edge Case | X status URL containing query parameters extracts the exact tweet ID. |
| **XPRE-08** | Edge Case | X status URL with trailing slash extracts the exact tweet ID. |
| **XPRE-09** | Happy Path | `WebSearchResultParser` parses `XThreadResponse` JSON and populates `WebSearchSource`. |
| **XPRE-10** | Mutation-Defense | Prompt has non-status X URL, skipping thread fetch and falling back to search. |

---

## 2. Test Scenarios

### XPRE-01: Parseable X status URL triggers deterministic thread retrieval, bypassing classifier
* **Given** a prompt: `"Check this thread: https://x.com/HarshithLucky3/status/2058763478853456162?s=20"`
* **And** X search is configured (`xSearchEnabled = true`, `xApiKeyPresent = true`)
* **And** the search intent classifier is mocked to return `requiresSearch = false`
* **When** `ChatInferenceRequestPreparer` is invoked
* **Then** the `x_get_thread` tool executor is executed exactly once
* **And** the executor arguments are: `{"tweet_url":"https://x.com/HarshithLucky3/status/2058763478853456162?s=20"}`
* **And** `preRetrievalXResultJson` contains the exact JSON value: `{"requested_post":{"id":"2058763478853456162","text":"Target post","user_name":"Harshith"},"ancestor_posts":[],"data":[{"id":"2058763478853456162","text":"Target post","user_name":"Harshith"}],"anchor_tweet_id":"2058763478853456162"}`

### XPRE-02: Parseable Twitter status URL triggers deterministic thread retrieval, bypassing classifier
* **Given** a prompt: `"Check this: https://twitter.com/user/status/987654321"`
* **And** X search is configured (`xSearchEnabled = true`, `xApiKeyPresent = true`)
* **When** `ChatInferenceRequestPreparer` is invoked
* **Then** the `x_get_thread` tool executor is executed exactly once
* **And** the executor arguments are: `{"tweet_url":"https://twitter.com/user/status/987654321"}`

### XPRE-03: Prompt shapes keyword query and triggers recent search pre-retrieval when intent classifier fires
* **Given** a prompt: `"Find latest breaking news on #AI and OpenAI @sama"`
* **And** X search is configured (`xSearchEnabled = true`, `xApiKeyPresent = true`)
* **And** the search intent classifier is mocked to return `requiresSearch = true`
* **When** `ChatInferenceRequestPreparer` is invoked
* **Then** the `search_x_recent` tool executor is executed exactly once
* **And** the composed query has keywords, hashtags, and mentions shaped from prompt:
  * `keywords` includes `"news"` and `"breaking"`
  * `hashtags` includes `"AI"`
  * `mentions` includes `"sama"`
* **And** the final query constructed via `BuildXQueryUseCase` does not exceed 128 characters
* **And** `preRetrievalXResultJson` contains the mock response array of size 1

### XPRE-04: Long conversational prompt without entities skips pre-retrieval
* **Given** a prompt: `"Hello assistant! I was wondering if you could help me compose a nice birthday invitation for my friend who turns 30 next week. They like hiking and pasta, so maybe something with that?"`
* **And** X search is configured
* **And** the search intent classifier is mocked to return `requiresSearch = true`
* **When** `DeriveXSearchParamsFromPromptUseCase` is invoked
* **Then** the result has `params` set to `null`
* **And** the `skipReason` is `"Conversational prompt too long without entities"`
* **And** the search executor is NOT executed

### XPRE-05: X URL is present but X search skill is not configured, skipping pre-retrieval
* **Given** a prompt: `"Check this out: https://x.com/user/status/123"`
* **And** X search is disabled (`xSearchEnabled = false`)
* **When** `ChatInferenceRequestPreparer` is invoked
* **Then** the `x_get_thread` executor is NOT executed
* **And** `preRetrievalXResultJson` is `null`

### XPRE-06: Prompt has search intent but classifier resolves to false, skipping pre-retrieval
* **Given** a prompt: `"Tell me a joke about keyboards"`
* **And** X search is configured
* **And** the search intent classifier is mocked to return `requiresSearch = false`
* **When** `ChatInferenceRequestPreparer` is invoked
* **Then** the `search_x_recent` executor is NOT executed
* **And** `preRetrievalXResultJson` is `null`

### XPRE-07: X status URL containing query parameters extracts the exact tweet ID
* **Given** a prompt: `"Check this thread: https://x.com/HarshithLucky3/status/2058763478853456162?s=20&t=abcdef"`
* **When** parsed using the internal extractor or helper `XSearchRepository.parseTweetIdFromUrl`
* **Then** the extracted tweet ID is exactly `"2058763478853456162"`

### XPRE-08: X status URL with trailing slash extracts the exact tweet ID
* **Given** a prompt: `"Check this: https://twitter.com/some_user/status/1122334455/"`
* **When** parsed using the internal extractor or helper `XSearchRepository.parseTweetIdFromUrl`
* **Then** the extracted tweet ID is exactly `"1122334455"`

### XPRE-09: `WebSearchResultParser` parses `XThreadResponse` JSON and populates `WebSearchSource`
* **Given** a JSON string representing an `XThreadResponse` payload:
  ```json
  {
    "requested_post": {
      "id": "123",
      "text": "Target post content",
      "user_name": "John Doe",
      "username": "johndoe",
      "url": "https://x.com/johndoe/status/123",
      "impact_score": 10.0
    },
    "ancestor_posts": [
      {
        "id": "122",
        "text": "Parent post content",
        "user_name": "Jane Smith",
        "username": "janesmith",
        "url": "https://x.com/janesmith/status/122",
        "impact_score": 5.0
      }
    ],
    "data": [
      {
        "id": "122",
        "text": "Parent post content",
        "user_name": "Jane Smith",
        "username": "janesmith",
        "url": "https://x.com/janesmith/status/122",
        "impact_score": 5.0
      },
      {
        "id": "123",
        "text": "Target post content",
        "user_name": "John Doe",
        "username": "johndoe",
        "url": "https://x.com/johndoe/status/123",
        "impact_score": 10.0
      }
    ],
    "anchor_tweet_id": "123"
  }
  ```
* **And** an assistant message ID of `"msg_assistant_1"`
* **When** `WebSearchResultParser.parse` is invoked
* **Then** the returned list has size exactly `2`
* **And** the first source has title `"Jane Smith"`, URL `"https://x.com/janesmith/status/122"`, content `"Parent post content"`, and score `5.0`
* **And** the second source has title `"John Doe"`, URL `"https://x.com/johndoe/status/123"`, content `"Target post content"`, and score `10.0`

---

## 3. Mutation-Defense Risk & Scenario

### Mutation Risk (Lax URL Checking)
A lazy implementation might perform X status URL detection using a simple substring check, such as `contains("x.com")` or `contains("twitter.com")`. This would cause false-positive detections on prompts containing standard profile links (e.g. `https://x.com/profile`) or links to trends (e.g. `https://x.com/explore`), triggering `x_get_thread` and throwing exceptions because no numeric tweet ID can be extracted.

### XPRE-10: Prompt has non-status X URL, skipping thread fetch and falling back to search
* **Given** a prompt: `"Check out the profile https://x.com/OpenAI and search for AI news"`
* **And** X search is configured (`xSearchEnabled = true`, `xApiKeyPresent = true`)
* **And** the search intent classifier returns `requiresSearch = true`
* **When** `ChatInferenceRequestPreparer` is invoked
* **Then** the `x_get_thread` executor is NOT executed (since it is not a status URL)
* **And** `DeriveXSearchParamsFromPromptUseCase` is executed to shape the query
* **And** the `search_x_recent` executor is executed with query shaped from prompt (`"news OpenAI AI"`)
