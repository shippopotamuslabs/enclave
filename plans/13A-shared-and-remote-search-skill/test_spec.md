# Test Specification: Shared and Remote Search Skill

## 1. Happy Path Scenarios

### Scenario: GenerateChatResponseUseCase exposes search only for non-local models
- **Given:** `ActiveModelConfiguration(id = 7, isLocal = false, name = "OpenAI Fast", systemPrompt = "Be concise.", reasoningEffort = null, temperature = 0.7, topK = 40, topP = 0.95, maxTokens = 512, minP = 0.0, repetitionPenalty = 1.1, contextWindow = 4096, thinkingEnabled = false)`
- **When:** `GenerateChatResponseUseCase` builds `GenerationOptions`
- **Then:** `toolingEnabled` is `true`, `availableTools` contains exactly one entry named `tavily_web_search`, and `MessageEntity` is unchanged

### Scenario: InferenceFactoryImpl injects one shared executor into the supported remote providers
- **Given:** provider selections for OpenAI, OpenRouter, xAI, and Anthropic
- **When:** `InferenceFactoryImpl` resolves an inference service for each provider
- **Then:** each constructed service receives the same `ToolExecutorPort` instance and none creates a provider-private executor

### Scenario: OpenAI-compatible providers complete one stubbed tool round trip
- **Given:** the first provider response requests `tavily_web_search` with `{"query":"latest android tool calling"}` and `StubSearchToolExecutor` returns the deterministic JSON payload
- **When:** `ApiInferenceServiceImpl`, `OpenRouterInferenceServiceImpl`, or `XaiInferenceServiceImpl` handles the turn
- **Then:** the tool result is replayed to the provider and the UI stream contains only the final assistant text

### Scenario: Anthropic completes one tool_use and tool_result round trip
- **Given:** Anthropic emits a `tool_use` block for `tavily_web_search` with `query = "latest android tool calling"` and the executor returns the deterministic JSON payload
- **When:** `AnthropicInferenceServiceImpl` continues the conversation
- **Then:** the service sends one `tool_result` message and emits only final assistant-facing text

### Scenario: StubSearchToolExecutor preserves the fixed Tavily-like contract
- **Given:** `ToolCallRequest(toolName = "tavily_web_search", argumentsJson = "{\"query\":\"latest android tool calling\"}", provider = "openai", modelType = ModelType.FAST)`
- **When:** `StubSearchToolExecutor.execute` is called
- **Then:** the returned JSON contains top-level keys `query` and `results`, and `results[0].url` equals `"https://example.invalid/stub"`

## 2. Error Path & Edge Case Scenarios

### Scenario: Unknown tool name is rejected
- **Given:** the provider emits `toolName = "weather_lookup"` and `{"query":"Boston"}`
- **When:** the remote tool loop validates the request
- **Then:** the service emits `InferenceEvent.Error(cause = IllegalArgumentException("Unsupported tool: weather_lookup"), modelType = ModelType.FAST)`

### Scenario: Missing query is rejected before executor invocation
- **Given:** the provider emits `toolName = "tavily_web_search"` and `{}`
- **When:** the remote tool loop validates the request
- **Then:** the service emits `InferenceEvent.Error(cause = IllegalArgumentException("Tool argument 'query' is required"), modelType = ModelType.FAST)`

### Scenario: Recursive second tool request is rejected
- **Given:** the first tool call succeeds and the provider emits another `tavily_web_search` request in the same turn
- **When:** the loop handles the second request
- **Then:** the service emits `InferenceEvent.Error(cause = IllegalStateException("Search skill recursion limit exceeded"), modelType = ModelType.FAST)`

### Scenario: Tool traces are not persisted
- **Given:** a search-enabled remote conversation completes successfully
- **When:** the final assistant turn is saved
- **Then:** only the final assistant text is written to the stored message and no tool request or tool result JSON is persisted

## 3. Mutation Defense
### Lazy Implementation Risk
The most likely lazy implementation is to add the tool schema to outgoing requests but skip the replay loop, either returning raw tool payloads to the UI or stopping after the first provider response.

### Defense Scenario
- **Given:** the first provider response contains only a `tavily_web_search` request, no visible assistant text, and the stub executor returns the fixed JSON payload
- **When:** the remote inference service completes the turn
- **Then:** no assistant chunk is emitted before the tool result replay, the executor runs exactly once, and the only visible response chunk is the final assistant text produced after replay
