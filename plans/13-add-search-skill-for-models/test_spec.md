# Test Specification: Search Skill for Models

## 1. Happy Path Scenarios

### Scenario: GenerateChatResponseUseCase adds shared search capability without changing persistence behavior
- **Given:** a FAST API model configuration with `isLocal = false`, Tavily search enabled, and an existing persisted chat history containing only user and assistant messages
- **When:** `GenerateChatResponseUseCase` builds `GenerationOptions` for a new assistant turn
- **Then:** `GenerationOptions.availableTools` contains a single `tavily_web_search` definition, `toolPromptContract` is `null`, and no tool trace fields are written to `Message`, `MessageEntity`, or `PocketCrewDatabase`

### Scenario: GenerateChatResponseUseCase composes a local tool envelope without mutating stored prompt templates
- **Given:** a local FAST model configuration with `systemPrompt = "Be concise."` and search enabled
- **When:** `GenerateChatResponseUseCase` builds `GenerationOptions` for LiteRT, MediaPipe, or llama
- **Then:** the effective runtime prompt includes `<tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>` instructions, while `SystemPromptTemplates` and the stored configuration prompt remain `"Be concise."`

### Scenario: InferenceFactoryImpl injects one shared tool executor into every search-capable service
- **Given:** active configurations for OpenAI, OpenRouter, xAI, Anthropic, Google, LiteRT, MediaPipe, and llama-backed models
- **When:** `InferenceFactoryImpl` resolves each `LlmInferencePort`
- **Then:** every constructed inference service receives the same `ToolExecutorPort` dependency and no provider constructs its own private executor

### Scenario: OpenAI-family providers complete a native tool-call round trip with the stub executor
- **Given:** `ApiInferenceServiceImpl`, `OpenRouterInferenceServiceImpl`, and `XaiInferenceServiceImpl` all inherit the shared loop from `BaseOpenAiSdkInferenceService`, the first provider response requests `tavily_web_search` with `{"query":"latest android tool calling"}`, and the stub executor returns the deterministic Step 1 JSON
- **When:** a user prompt asks for recent Android tool-calling news
- **Then:** each provider submits the tool result back to the model and emits only the final assistant text, never the raw tool JSON

### Scenario: Anthropic completes a tool_use and tool_result round trip
- **Given:** `AnthropicInferenceServiceImpl` receives an assistant `tool_use` block for `tavily_web_search` with `query = "latest android tool calling"` and the stub executor returns the deterministic Step 1 JSON
- **When:** the service continues the conversation with a `tool_result` message
- **Then:** the model returns normal assistant text and the emitted UI stream contains only assistant-facing content

### Scenario: Google uses a tool-capable execution path instead of the streaming-only path
- **Given:** `GoogleInferenceServiceImpl` receives a prompt that triggers `tavily_web_search`
- **When:** the feature is enabled
- **Then:** the service uses the tool-capable Google path with function declarations, executes the shared tool executor once, and returns final assistant text after replaying the tool result

### Scenario: LiteRT detects the local tool envelope and continues the same conversation
- **Given:** LiteRT emits partial text that contains a complete `<tool_call>{"name":"tavily_web_search","arguments":{"query":"best folding phones 2026"}}</tool_call>` block
- **When:** `LiteRtInferenceServiceImpl` handles the streamed response
- **Then:** the raw envelope is not emitted to the UI, `ToolExecutorPort` executes exactly once with query `"best folding phones 2026"`, and the tool result is injected back into the same conversation before final assistant text is emitted

### Scenario: MediaPipe detects the local tool envelope and resumes generation
- **Given:** `MediaPipeInferenceServiceImpl` receives a partial response stream that contains the complete search envelope and the tool executor returns the deterministic Step 1 JSON
- **When:** the service replays the tool result using `addQueryChunk(...)` and invokes response generation again
- **Then:** the final visible output contains only assistant text based on the tool result and no raw envelope JSON reaches the user

### Scenario: llama.cpp pauses native decode, executes the tool, and resumes
- **Given:** `token_processor.hpp` detects a complete `<tool_call>` block during `llama-jni.cpp` token decoding, `JniLlamaEngine` forwards the parsed tool request to Kotlin, and Kotlin returns the deterministic Step 1 JSON
- **When:** `LlamaInferenceServiceImpl` continues generation
- **Then:** the tool result is injected into the active KV-cache-backed conversation and assistant generation resumes without resetting the session

### Scenario: Step 2 Tavily wiring preserves the Step 1 result contract
- **Given:** `TavilySearchRepository` is wired with the shared `OkHttpClient` and receives the query `"latest android tool calling"`
- **When:** the repository maps the Tavily response for the executor layer
- **Then:** the mapped JSON keeps the same top-level `query` and `results[]` shape established by the Step 1 stub contract

### Scenario: Settings can enable search and store the Tavily key alias
- **Given:** the user enters a Tavily API key and enables search from the BYOK settings flow
- **When:** `SettingsViewModel` saves the configuration
- **Then:** `SettingsRepository` persists the enabled state, `ApiKeyManager` stores the key under the dedicated `tavily_web_search` alias, and subsequent generation requests expose the search capability

## 2. Error Path & Edge Case Scenarios

### Scenario: Unknown tool name is rejected across provider-native loops
- **Given:** a provider returns `toolName = "weather_lookup"` with `{"query":"Boston"}`
- **When:** the shared executor path handles the request
- **Then:** the service emits `InferenceEvent.Error(cause = IllegalArgumentException("Unsupported tool: weather_lookup"), modelType = ModelType.FAST)`

### Scenario: Missing query is rejected before executor invocation
- **Given:** a provider or local runtime emits `tavily_web_search` with `{}` as its arguments payload
- **When:** the tool loop validates the request
- **Then:** the service emits `InferenceEvent.Error(cause = IllegalArgumentException("Tool argument 'query' is required"), modelType = ModelType.FAST)`

### Scenario: Recursive tool calling is rejected
- **Given:** the first tool call succeeds, the tool result is replayed, and the model emits another `tavily_web_search` request instead of final assistant text
- **When:** the search loop handles the second tool request in the same turn
- **Then:** the service emits `InferenceEvent.Error(cause = IllegalStateException("Search skill recursion limit exceeded"), modelType = ModelType.FAST)`

### Scenario: Google tool mode does not silently fall back to the current streaming-only path
- **Given:** search is enabled and the tool-capable Google request path fails before any assistant text is produced
- **When:** `GoogleInferenceServiceImpl` handles the failure
- **Then:** the service emits `InferenceEvent.Error(cause = IllegalStateException("Google tool execution failed before final response"), modelType = ModelType.FAST)` and does not retry through the streaming-only path

### Scenario: Malformed local tool envelope is surfaced as a typed failure
- **Given:** LiteRT, MediaPipe, or llama emits `<tool_call>{"name":"tavily_web_search","arguments":</tool_call>` with incomplete JSON
- **When:** the local parser attempts to complete the envelope
- **Then:** the service emits `InferenceEvent.Error(cause = IllegalStateException("Malformed tool_call envelope"), modelType = ModelType.FAST)`

### Scenario: Tool traces are not persisted after a successful search turn
- **Given:** a search-enabled conversation completes successfully with one tool execution and one final assistant answer
- **When:** the assistant turn is persisted at flow completion
- **Then:** the stored message content contains only the final assistant text, `thinkingRaw` contains only model thinking text when applicable, and no serialized tool payload is written to the database

### Scenario: Tavily network failure surfaces as an IO failure without corrupting settings state
- **Given:** `TavilySearchRepository` is enabled, the stored Tavily key alias resolves successfully, and the HTTP request throws `IOException("timeout")`
- **When:** the executor handles the search request
- **Then:** the calling inference service emits `InferenceEvent.Error(cause = IOException("timeout"), modelType = ModelType.FAST)` and the stored search-enabled setting remains unchanged

## 3. Mutation Defense
### Lazy Implementation Risk
The most likely broken implementation is to add `tavily_web_search` to remote request payloads and local prompts but never actually run a full tool loop, causing providers to emit raw tool JSON to the UI, local runtimes to stop after the envelope, or Tavily wiring to bypass the stable result contract.

### Defense Scenario
- **Given:** one OpenAI-family provider, one Anthropic provider, one Google provider, one LiteRT session, one MediaPipe session, and one llama session all receive prompts that trigger `tavily_web_search`, the stub executor returns the fixed Step 1 JSON, and Step 2 Tavily mapping returns the same result shape
- **When:** each runtime completes one search-enabled turn
- **Then:** every path executes exactly one tool request, replays exactly one tool result, emits only assistant-facing text to the UI, persists no tool traces, and produces the same final result shape regardless of whether the executor is stubbed or backed by Tavily
