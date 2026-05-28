# Specification for Non-PKCE X Platform Tools

This specification defines the architectural design, API integrations, data models, and implementation steps for adding the four new non-PKCE X (Twitter) tools to the Enclave project.

---

## 1. Module Dependencies & Rules
The architecture follows clean architecture guidelines. Dependencies point inward toward `:core:domain`.
- **`:core:domain`** defines the tool metadata, parameters, and preparer logic. It must not depend on any framework or HTTP libraries.
- **`:core:data`** implements the HTTP integrations and executors. It implements `ToolExecutorPort`.
- **`:feature:chat`** maps the tools to UI indicators.

---

## 2. File Map

### [MODIFY]
- `:core:domain` - `com.browntowndev.enclave.domain.model.inference.ToolDefinition.kt`
- `:core:domain` - `com.browntowndev.enclave.domain.model.inference.ToolParameterModels.kt`
- `:core:domain` - `com.browntowndev.enclave.domain.usecase.chat.ChatInferenceRequestPreparer.kt`
- `:core:data` - `com.browntowndev.enclave.core.data.repository.XSearchRepository.kt`
- `:core:data` - `com.browntowndev.enclave.core.data.repository.CompositeToolExecutor.kt`
- `:feature:chat` - `com.browntowndev.enclave.feature.chat.mapping.ToolCallBannerMapper.kt`

### [CREATE]
- `:core:data` - `com.browntowndev.enclave.core.data.repository.XGetThreadToolExecutor.kt`
- `:core:data` - `com.browntowndev.enclave.core.data.repository.XTrendingToolExecutor.kt`
- `:core:data` - `com.browntowndev.enclave.core.data.repository.XUserTweetsToolExecutor.kt`
- `:core:data` - `com.browntowndev.enclave.core.data.repository.XMentionSearchToolExecutor.kt`

---

## 3. Data Structures & DTOs

### 3.1 Domain Parameters (in `:core:domain` - `ToolParameterModels.kt`)
```kotlin
@Serializable
data class XGetThreadParams(
    @ToolParam(description = "The URL or ID of the tweet to fetch the thread for.")
    val tweet_url: String,
) : ToolParameters

@Serializable
data class XTrendingParams(
    @ToolParam(description = "The location to get trending topics for (e.g. 'worldwide', 'united states'). Defaults to 'worldwide'.", required = false)
    val location: String = "worldwide",
) : ToolParameters

@Serializable
data class XUserTweetsParams(
    @ToolParam(description = "The user screen name (e.g. 'elonmusk' or '@elonmusk').")
    val username: String,
    @ToolParam(description = "Optional text query to filter the user's tweets.", required = false)
    val search_text: String? = null,
    @ToolParam(description = "Optional pagination token to retrieve subsequent pages.", required = false)
    val next_token: String? = null,
) : ToolParameters

@Serializable
data class XMentionSearchParams(
    @ToolParam(description = "The user screen name whose mentions to search (e.g. 'elonmusk' or '@elonmusk').")
    val username: String,
    @ToolParam(description = "Keywords to search within the mentions.")
    val search_text: String,
    @ToolParam(description = "Optional pagination token to retrieve subsequent pages.", required = false)
    val next_token: String? = null,
) : ToolParameters
```

### 3.2 Data Layer Models (in `:core:data` - `XSearchRepository.kt`)
```kotlin
@Serializable
data class XTrendItem(
    val trend_name: String,
    val tweet_count: Int? = null,
)

@Serializable
data class XTrendsResponse(
    val data: List<XTrendItem>,
)

@Serializable
data class XSearchPaginatedResult(
    val data: List<XPost>,
    val next_token: String? = null,
)
```

---

## 4. API Endpoints

### 4.1 Tweet Lookup (GET /2/tweets)
Used in `getThread` to look up the target tweet and conversation root tweet.
- **URL**: `https://api.x.com/2/tweets`
- **Query Params**:
  - `ids`: comma-separated tweet IDs
  - `tweet.fields`: `conversation_id,author_id,created_at,text,public_metrics,entities,referenced_tweets`
  - `expansions`: `author_id,attachments.media_keys`
  - `user.fields`: `name,username,verified,public_metrics`
  - `media.fields`: `type,url,alt_text`
- **Headers**:
  - `Authorization: Bearer <API_KEY>`

### 4.2 Conversation Replies Search (GET /2/tweets/search/recent)
Used in `getThread` to find conversation replies.
- **URL**: `https://api.x.com/2/tweets/search/recent`
- **Query Params**:
  - `query`: `conversation_id:<conversation_id> -is:retweet -is:nullcast`
  - `tweet.fields`: `conversation_id,author_id,created_at,text,public_metrics,entities,referenced_tweets`
  - `expansions`: `author_id,attachments.media_keys`
  - `user.fields`: `name,username,verified,public_metrics`
  - `media.fields`: `type,url,alt_text`
  - `max_results`: `100`
- **Headers**:
  - `Authorization: Bearer <API_KEY>`

### 4.3 Trends (GET /2/trends/by/woeid/{woeid})
Used in `getTrends` to look up location-based trends.
- **URL**: `https://api.x.com/2/trends/by/woeid/{woeid}`
- **Path Params**:
  - `woeid`: WOEID (Long)
- **Query Params**:
  - `trend.fields`: `tweet_count`
- **Headers**:
  - `Authorization: Bearer <API_KEY>`

---

## 5. Detailed Component Designs

### 5.1 XSearchRepository Extension
Modify `XSearchRepository.kt` to expose the following methods:

#### `getThread`
```kotlin
fun getThread(tweetUrlOrId: String): String
```
- **Behavior**:
  1. Extract the tweet ID from the input string (supporting status URL formats: e.g. `https://x.com/username/status/12345`).
  2. Perform target tweet lookup via `fetchTweetsByIds(listOf(targetTweetId), apiKey)`. If empty, throw `IOException("Tweet not found or inaccessible")`.
  3. Extract `conversation_id` from the target tweet.
  4. If `conversation_id` is present:
     - Search conversation replies using query `"conversation_id:$conversationId -is:retweet -is:nullcast"` via `fetchRecentSearchRaw`.
     - If the target tweet's ID is not the root (`targetTweetId != conversationId`), perform root tweet lookup via `fetchTweetsByIds(listOf(conversationId), apiKey)`.
  5. Assemble all unique tweets (target tweet, root tweet, and replies).
  6. Map all tweets to `XPost` objects.
  7. Sort `XPost` list chronologically ascending using `created_at` (fall back to tweet ID order if timestamps are missing).
  8. If the root tweet's `created_at` is older than 7 days, or replies are empty, append warning literal: `"X recent search API is limited to the last 7 days. Replies older than 7 days or matching spam filters may not be returned."`
  9. Return JSON object structured as: `{"data": [XPost], "warning": "..."}`.

#### `getTrends`
```kotlin
fun getTrends(location: String): String
```
- **Behavior**:
  1. Map location string (lowercase, trimmed) to WOEID:
     - `"worldwide"` -> `1`
     - `"united states"` -> `23424977`
     - `"united kingdom"` -> `23424975`
     - `"canada"` -> `23424960`
     - `"australia"` -> `23424900`
     - `"germany"` -> `23424901`
     - `"france"` -> `23424902`
     - `"japan"` -> `23424903`
     - `"india"` -> `23424904`
     - `"brazil"` -> `23424905`
     - Fallback is `1` (Worldwide).
  2. Fetch trends using GET `/2/trends/by/woeid/{woeid}` with query parameter `trend.fields=tweet_count`.
  3. Parse raw JSON into `XTrendsResponse`.
  4. Return `XTrendsResponse` serialized to JSON string.

#### `searchPaginated`
```kotlin
fun searchPaginated(
    query: String,
    sortOrder: String = "relevancy",
    maxPostsPerAuthor: Int = MAX_POSTS_PER_AUTHOR,
    maxTotalPosts: Int = MAX_TOTAL_POSTS,
    nextToken: String? = null,
): XSearchPaginatedResult
```
- **Behavior**:
  1. Build Cache Key including limits and `nextToken` to avoid cache collisions.
  2. Refine query to append `-is:retweet -is:nullcast` filters.
  3. Fetch raw X API results. If `nextToken` is specified, add query parameter `next_token` to X API.
  4. Apply spam filters and deduplication.
  5. Enforce `maxPostsPerAuthor` limit on author counts (using the parameter instead of hardcoded `MAX_POSTS_PER_AUTHOR`).
  6. Return `XSearchPaginatedResult(data = distinctPosts, next_token = rawResponse.meta?.next_token)`.

Refactor the existing `search` function to delegate:
```kotlin
fun search(query: String, sortOrder: String = "relevancy"): String {
    val result = searchPaginated(query, sortOrder, MAX_POSTS_PER_AUTHOR, MAX_TOTAL_POSTS, null)
    return json.encodeToString(result.data)
}
```

### 5.2 Executors (in `:core:data`)
Create the four new tool executors:
1. `XGetThreadToolExecutor`
   - Maps parameters to `XGetThreadParams`
   - Validates settings configuration (`xSearchEnabled`)
   - Invokes `xSearchRepository.getThread(params.tweet_url)`
   - Returns `ToolExecutionResult(request.toolName, resultJson)`
2. `XTrendingToolExecutor`
   - Maps parameters to `XTrendingParams`
   - Validates settings configuration (`xSearchEnabled`)
   - Invokes `xSearchRepository.getTrends(params.location)`
   - Returns `ToolExecutionResult(request.toolName, resultJson)`
3. `XUserTweetsToolExecutor`
   - Maps parameters to `XUserTweetsParams`
   - Validates settings configuration (`xSearchEnabled`)
   - Cleans username: `val cleanUser = params.username.removePrefix("@").trim()`
   - Builds query: `val query = if (params.search_text.isNullOrBlank()) "from:$cleanUser" else "from:$cleanUser ${params.search_text}"`
   - Invokes `xSearchRepository.searchPaginated(query, "recency", 20, 20, params.next_token)`
   - Returns `ToolExecutionResult(request.toolName, json.encodeToString(result))`
4. `XMentionSearchToolExecutor`
   - Maps parameters to `XMentionSearchParams`
   - Validates settings configuration (`xSearchEnabled`)
   - Cleans username: `val cleanUser = params.username.removePrefix("@").trim()`
   - Builds query: `"@$cleanUser ${params.search_text}"`
   - Invokes `xSearchRepository.searchPaginated(query, "recency", 20, 20, params.next_token)`
   - Returns `ToolExecutionResult(request.toolName, json.encodeToString(result))`

---

## 6. Implementation Task List

### Task 1: Add Tool Definition Registrations (`:core:domain`)
- Add the four new tool definitions to `ToolDefinition.kt`.
- Specify:
  - `X_GET_THREAD` (name: `"x_get_thread"`, display: `"Reading X Thread..."`, parameters: `XGetThreadParams::class`, mode: `DISCLOSE_ON_DEMAND`)
  - `X_TRENDING` (name: `"x_trending"`, display: `"Checking X Trends..."`, parameters: `XTrendingParams::class`, mode: `DISCLOSE_ON_DEMAND`)
  - `X_USER_TWEETS` (name: `"x_user_tweets"`, display: `"Searching X User's Tweets..."`, parameters: `XUserTweetsParams::class`, mode: `DISCLOSE_ON_DEMAND`)
  - `X_MENTION_SEARCH` (name: `"x_mention_search"`, display: `"Searching X Mentions..."`, parameters: `XMentionSearchParams::class`, mode: `DISCLOSE_ON_DEMAND`)
- Add them to `ALL_TOOLS`.
- *Satisfies Scenarios: SCENARIO_PREPARER_ELIGIBLE_TOOLS, SCENARIO_COMPOSITE_EXECUTOR_ROUTING*

### Task 2: Define Tool Parameter Classes (`:core:domain`)
- Add `XGetThreadParams`, `XTrendingParams`, `XUserTweetsParams`, and `XMentionSearchParams` to `ToolParameterModels.kt`.
- *Satisfies Scenarios: SCENARIO_GET_THREAD_SUCCESS, SCENARIO_TRENDS_SUCCESS, SCENARIO_USER_TWEETS_SUCCESS, SCENARIO_MUTATION_DEFENSE_USER_TWEETS*

### Task 3: Update ChatInferenceRequestPreparer (`:core:domain`)
- Modify `ChatInferenceRequestPreparer.kt` to include the four new tools in `eligibleTools` list when `xSearchConfigured` is true.
- *Satisfies Scenarios: SCENARIO_PREPARER_ELIGIBLE_TOOLS*

### Task 4: Extend XSearchRepository Models (`:core:data`)
- Define `XTrendItem`, `XTrendsResponse`, and `XSearchPaginatedResult` classes in `XSearchRepository.kt`.
- *Satisfies Scenarios: SCENARIO_TRENDS_SUCCESS, SCENARIO_USER_TWEETS_SUCCESS*

### Task 5: Refactor search & Implement searchPaginated (`:core:data`)
- Implement `searchPaginated` and raw HTTP helpers (`fetchRecentSearchRaw`) in `XSearchRepository.kt`.
- Support cache keys that integrate limitations and token.
- Delegate existing `search` function to `searchPaginated`.
- *Satisfies Scenarios: SCENARIO_USER_TWEETS_SUCCESS, SCENARIO_USER_TWEETS_PAGINATION, SCENARIO_MENTION_SEARCH_SUCCESS, SCENARIO_MUTATION_DEFENSE_USER_TWEETS*

### Task 6: Implement getThread & getTrends (`:core:data`)
- Add `getThread` and `getTrends` implementations in `XSearchRepository.kt`.
- Implement `fetchTweetsByIds` and `fetchTrendsRaw`.
- Add chronological sorting and the 7-day warning logic.
- *Satisfies Scenarios: SCENARIO_GET_THREAD_SUCCESS, SCENARIO_GET_THREAD_AS_REPLY, SCENARIO_GET_THREAD_7DAY_WARNING, SCENARIO_TRENDS_SUCCESS*

### Task 7: Implement New Executors (`:core:data`)
- Create `XGetThreadToolExecutor.kt`, `XTrendingToolExecutor.kt`, `XUserTweetsToolExecutor.kt`, and `XMentionSearchToolExecutor.kt`.
- Sanitize usernames by stripping leading `@` and calling `.trim()`.
- Route through settings validation checks.
- *Satisfies Scenarios: SCENARIO_COMPOSITE_EXECUTOR_ROUTING, SCENARIO_MUTATION_DEFENSE_USER_TWEETS*

### Task 8: Update CompositeToolExecutor (`:core:data`)
- Register the four new executors in `CompositeToolExecutor.kt` dependency constructor and `execute` dispatch routing map.
- *Satisfies Scenarios: SCENARIO_COMPOSITE_EXECUTOR_ROUTING*

### Task 9: Register in ToolCallBannerMapper (`:feature:chat`)
- Modify `ToolCallBannerMapper.kt` to map the four tool names to `ToolCallBannerKind.X_SEARCH` and associate their labels.
- *Satisfies Scenarios: SCENARIO_COMPOSITE_EXECUTOR_ROUTING*
