# Test Specification: Real Tavily and Settings

## 1. Happy Path Scenarios

### Scenario: TavilySearchRepository maps a live response into the stable contract
- **Given:** Tavily returns a successful response for query `"latest android tool calling"`
- **When:** `TavilySearchRepository` maps the response
- **Then:** the mapped payload contains top-level `query = "latest android tool calling"` and a `results` array whose first element includes `title`, `url`, `content`, and `score`

### Scenario: The executor switches from stub output to repository-backed output without changing the contract
- **Given:** live search is enabled and `TavilySearchRepository` returns one mapped result for `"latest android tool calling"`
- **When:** the search executor handles the tool request
- **Then:** the returned payload keeps the same top-level shape used by the Step 1 stub executor

### Scenario: SettingsViewModel saves search enablement and Tavily key state
- **Given:** the user enables search and enters a Tavily key in the BYOK configuration flow
- **When:** `SettingsViewModel` saves the updated settings
- **Then:** `SettingsRepository` stores the enabled flag, `ApiKeyManager` stores the key under alias `tavily_web_search`, and the UI state reports that a key is present

### Scenario: Existing chat persistence behavior remains unchanged
- **Given:** a search-enabled conversation completes successfully using live Tavily-backed search
- **When:** the final assistant message is stored
- **Then:** only the final assistant text is persisted and no tool request or tool result payload is written to chat storage

## 2. Error Path & Edge Case Scenarios

### Scenario: Tavily timeout surfaces as an IO failure
- **Given:** `TavilySearchRepository` executes a search request and the HTTP call throws `IOException("timeout")`
- **When:** the executor handles the failure
- **Then:** the calling inference service emits `InferenceEvent.Error(cause = IOException("timeout"), modelType = ModelType.FAST)`

### Scenario: Live search enabled without a stored key is rejected
- **Given:** search is enabled in settings and the `tavily_web_search` alias has no stored key
- **When:** the executor attempts a live search
- **Then:** the calling path fails with `IllegalStateException("Tavily API key is required when search is enabled")`

### Scenario: Clearing the key preserves the disabled setting state
- **Given:** search was previously enabled with a stored key
- **When:** the user clears the Tavily key and disables search in the settings UI
- **Then:** the key alias is removed, the search-enabled flag is `false`, and no stale key-present state remains in the UI model

## 3. Mutation Defense
### Lazy Implementation Risk
The most likely lazy implementation is to keep returning stubbed data while wiring only the settings toggle, which would make the UI appear complete but never actually use live Tavily responses.

### Defense Scenario
- **Given:** live search is enabled, a valid Tavily key is stored, and the repository returns a non-stub URL different from `"https://example.invalid/stub"`
- **When:** the executor handles a search request
- **Then:** the emitted payload reflects the repository response, not the fixed stub payload, while preserving the same top-level result shape
