# Test Spec: Prompt Caching

## 1. Test Framework and Run Command
Unit tests are written using JUnit 5, Turbine for testing Kotlin Flows, and MockK for mocking dependencies.
Execution Command:
```bash
./gradlew :feature:inference:testDebugUnitTest
```

---

## 2. Scenario Index

| Scenario ID | Type | Description |
|---|---|---|
| `PC-01` | Happy Path | OpenAI/xAI separates stable system prompt and puts volatile sidecars at the end of the history list. |
| `PC-02` | Happy Path | Anthropic attaches ephemeral cache control to the static system prompt, last tool, and last history message block. |
| `PC-03` | Mutation-Defense | Anthropic limits cache control to exactly 3 breakpoints and prepends sidecars to the latest user message. |
| `PC-04` | Happy Path | Google GenAI creates CachedContent with stable prefix when token threshold (1024) is exceeded. |
| `PC-05` | Happy Path | Google GenAI reuses CachedContent and transmits only deltas for subsequent turns. |
| `PC-06` | Happy Path | Google GenAI deletes old cache and recreates a new one when delta exceeds 1024 tokens. |
| `PC-07` | Happy Path | Google GenAI deletes CachedContent from the server on session close. |
| `PC-08` | Edge Case | Google GenAI does not create CachedContent when history token count is below 1024. |

---

## 3. Happy Path Scenarios

### Scenario ID: `PC-01`
**OpenAI/xAI separates stable system prompt and puts volatile sidecars at the end of the history list**
* **Given** an OpenAI inference service with base model "gpt-4o"
* **When** `completeSingleTurn` is called with:
  * `systemPrompt` = "You are a helpful assistant."
  * `contextSidecars` = "State ledger JSON: {\"key\":\"value\"}"
  * `sessionMessages` = [User message "Hello"]
* **Then** the mapped OpenAI ChatCompletionCreateParams messages should be exactly:
  1. Role = SYSTEM, Content = "You are a helpful assistant."
  2. Role = USER, Content = "Hello"
  3. Role = SYSTEM, Content = "State ledger JSON: {\"key\":\"value\"}" (volatile sidecars placed right before the latest user prompt or at the end of history)

### Scenario ID: `PC-02`
**And** a list of tools containing:
* Tool 1 = "web_search"
* Tool 2 = "append_blocks"
* **When** `completeSingleTurn` is called with:
  * `systemPrompt` = "You are a helpful assistant."
  * `availableTools` = [Tool 1, Tool 2]
  * `sessionMessages` = [User message "Hello", Assistant message "Hi!", User message "Explain the plan."]
* **Then** the mapped Anthropic MessageCreateParams should contain:
  * System prompt text block parameter with cache control set to ephemeral.
  * Tool 1 is mapped with NO cache control.
  * Tool 2 (last tool) is mapped WITH cache control set to ephemeral.
  * First history message (User "Hello") blocks have NO cache control.
  * Second history message (Assistant "Hi!") blocks have NO cache control.
  * Third history message (User "Explain the plan.") last block has cache control set to ephemeral.
  * The third history message (User "Explain the plan.") content includes the prepended sidecars.

### Scenario ID: `PC-04`
**Google GenAI creates CachedContent when token threshold (1024) is exceeded**
* **Given** a Google GenAI inference service with model "gemini-2.5-flash"
* **And** the active cache name is initially null
* **And** history messages and system prompt token count is calculated as 1500 tokens (using JTokkitTokenCounter)
* **When** `completeSingleTurn` is called with `sessionMessages` containing 10 messages and the last turn message is "Summarize it"
* **Then** `sdkClient.createCachedContent` is called with:
  * `modelId` = "gemini-2.5-flash"
  * `systemInstruction` containing system prompt (excluding sidecars)
  * `contents` containing first 9 messages (history)
  * `ttlSeconds` = 300
* **And** the returned cache name is stored in `activeCacheName` as "cachedContents/mock-cache-123"
* **And** `sdkClient.generateContentStream` is called with:
  * `cachedContent` = "cachedContents/mock-cache-123" in `GenerateContentConfig`
  * `contents` containing only the 10th message (the last turn message "Summarize it" with sidecars prepended to it)

### Scenario ID: `PC-05`
**Google GenAI reuses CachedContent and transmits only deltas**
* **Given** a Google GenAI inference service with model "gemini-2.5-flash"
* **And** the active cache name is "cachedContents/mock-cache-123"
* **And** the previously cached messages are U1, A1, U2, A2
* **And** the new turn history contains U1, A1, U2, A2, U3, A3
* **And** the delta (U3, A3) has an estimated token count of 200 tokens (below 1024 token recreation threshold)
* **When** `completeSingleTurn` is called with `sessionMessages` = [U1, A1, U2, A2, U3, A3, U4]
* **Then** `sdkClient.createCachedContent` is NOT called
* **And** `sdkClient.generateContentStream` is called with:
  * `cachedContent` = "cachedContents/mock-cache-123" in `GenerateContentConfig`
  * `contents` containing the delta and latest message: [U3, A3, U4 with sidecars prepended to U4]

---

## 4. Error and Edge Case Scenarios

### Scenario ID: `PC-06`
**Google GenAI deletes old cache and recreates a new one when delta exceeds 1024 tokens**
* **Given** a Google GenAI inference service with model "gemini-2.5-flash"
* **And** the active cache name is "cachedContents/mock-cache-123"
* **And** the previously cached messages are U1, A1, U2, A2
* **And** the new turn history contains U1, A1, U2, A2, U3, A3
* **And** the delta (U3, A3) has an estimated token count of 1200 tokens (exceeds 1024 threshold)
* **When** `completeSingleTurn` is called with `sessionMessages` = [U1, A1, U2, A2, U3, A3, U4]
* **Then** `sdkClient.deleteCachedContent` is called with "cachedContents/mock-cache-123"
* **And** `sdkClient.createCachedContent` is called to create a new cache with all history [U1, A1, U2, A2, U3, A3]
* **And** the new cache name is returned as "cachedContents/mock-cache-456"
* **And** `sdkClient.generateContentStream` is called with:
  * `cachedContent` = "cachedContents/mock-cache-456" in `GenerateContentConfig`
  * `contents` containing only the latest message: [U4 with sidecars prepended]

### Scenario ID: `PC-07`
**Google GenAI deletes CachedContent from the server on session close**
* **Given** a Google GenAI inference service with model "gemini-2.5-flash"
* **And** the active cache name is "cachedContents/mock-cache-123"
* **When** `closeSession` is called
* **Then** `sdkClient.deleteCachedContent` is called with "cachedContents/mock-cache-123"
* **And** the stored `activeCacheName` is reset to null
* **And** the stored `cachedMessages` is cleared

### Scenario ID: `PC-08`
**Google GenAI does not create CachedContent when history token count is below 1024**
* **Given** a Google GenAI inference service with model "gemini-2.5-flash"
* **And** the active cache name is null
* **And** history messages and system prompt token count is calculated as 500 tokens (below 1024 threshold)
* **When** `completeSingleTurn` is called with `sessionMessages` = [User message "Hello", Assistant message "Hi", User message "How are you?"]
* **Then** `sdkClient.createCachedContent` is NOT called
* **And** `sdkClient.generateContentStream` is called with:
  * `cachedContent` = null in `GenerateContentConfig`
  * `contents` containing all 3 messages: [User "Hello", Assistant "Hi", User "How are you?" with sidecars prepended]

---

## 5. Mutation-Defense Scenario

### Scenario ID: `PC-03`
**Anthropic limits cache control to exactly 3 breakpoints when system, tools, and messages are present**
* **Given** an Anthropic inference service with model "claude-3-5-sonnet-20241022"
* **And** a tool list with 3 tools registered
* **And** a message history with 5 messages
* **And** a system prompt configured
* **When** `completeSingleTurn` is called
* **Then** the number of content blocks or parameters in `MessageCreateParams` with a non-null `cacheControl` is exactly 3
* **And** the cache breakpoints are located on:
  1. The system prompt block.
  2. The third tool (last tool) in the tools list.
  3. The fifth message (last message) in history.
* **And** the volatile sidecars are prepended to the latest user message block.
* **And** no other blocks contain `cacheControl`.
