# Test Specification: Llama Search Tool Bridge

## 1. Happy Path Scenarios

### Scenario: token_processor detects one complete tool envelope incrementally
- **Given:** llama token decoding yields the string `<tool_call>{"name":"tavily_web_search","arguments":{"query":"latest android tool calling"}}</tool_call>` across multiple token chunks
- **When:** `token_processor.hpp` processes the chunks in order
- **Then:** it reports exactly one complete envelope and no partial raw envelope is emitted as visible assistant text

### Scenario: JniLlamaEngine bridges one tool request to Kotlin and replays the result
- **Given:** native code sends one parsed `tavily_web_search` request with `query = "latest android tool calling"` and the shared executor returns the fixed JSON payload
- **When:** `JniLlamaEngine` handles the callback
- **Then:** it returns one tool result payload to native code and does not request a second tool execution in the same turn

### Scenario: LlamaChatSessionManager resumes the same session after tool replay
- **Given:** a llama session has emitted a valid tool envelope, received one tool result payload, and still has active conversation state
- **When:** `LlamaChatSessionManager` resumes generation
- **Then:** final assistant text is produced without resetting the session or losing prior chat context

### Scenario: LlamaInferenceServiceImpl hides raw tool JSON from the UI
- **Given:** a search-enabled llama turn triggers one tool request and one replay
- **When:** the full turn completes
- **Then:** the user-visible output contains only assistant-facing text and never includes the raw `<tool_call>` block or tool result JSON

## 2. Error Path & Edge Case Scenarios

### Scenario: Malformed envelope is surfaced as a typed failure
- **Given:** token decoding produces `<tool_call>{"name":"tavily_web_search","arguments":</tool_call>`
- **When:** the parser attempts to finalize the envelope
- **Then:** `LlamaInferenceServiceImpl` emits `InferenceEvent.Error(cause = IllegalStateException("Malformed tool_call envelope"), modelType = ModelType.FAST)`

### Scenario: Unknown tool name is rejected
- **Given:** native parsing yields `toolName = "weather_lookup"` with `{"query":"Boston"}`
- **When:** the Kotlin bridge validates the request
- **Then:** `LlamaInferenceServiceImpl` emits `InferenceEvent.Error(cause = IllegalArgumentException("Unsupported tool: weather_lookup"), modelType = ModelType.FAST)`

### Scenario: Resume failure surfaces as a typed failure
- **Given:** tool execution succeeds but native replay cannot restore decoding
- **When:** the llama stack attempts to resume generation
- **Then:** `LlamaInferenceServiceImpl` emits `InferenceEvent.Error(cause = IllegalStateException("Failed to resume llama generation after tool replay"), modelType = ModelType.FAST)`

## 3. Mutation Defense
### Lazy Implementation Risk
The most likely lazy implementation is to detect the envelope in native code but return the raw tool text to the UI or restart the session after tool replay, masking the fact that context continuity was lost.

### Defense Scenario
- **Given:** a single llama turn emits only a tool envelope before replay and the shared executor returns the fixed JSON payload
- **When:** the full turn completes
- **Then:** exactly one tool execution occurs, no raw envelope text reaches the UI, and the final assistant response is produced from the same session state that existed before replay
