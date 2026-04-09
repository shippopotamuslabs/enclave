# Test Specification: Google and Local Envelope Search Skill

## 1. Happy Path Scenarios

### Scenario: SearchToolPromptComposer appends the canonical local contract without mutating the base prompt
- **Given:** a configured prompt string `"Be concise."`
- **When:** `SearchToolPromptComposer` composes the runtime prompt for a local model
- **Then:** the returned runtime prompt contains `"Be concise."` and the literal `<tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>` contract, while the original configured prompt remains `"Be concise."`

### Scenario: GenerateChatResponseUseCase applies the prompt composer only for local search-capable models
- **Given:** a local active model configuration with search enabled and a remote active model configuration with search enabled
- **When:** `GenerateChatResponseUseCase` builds `GenerationOptions`
- **Then:** the local configuration receives a composed runtime prompt with the local tool contract and the remote configuration does not

### Scenario: Google completes one tool-capable round trip
- **Given:** `GoogleInferenceServiceImpl` receives a prompt that triggers `tavily_web_search`, the stub executor returns the fixed JSON payload, and the provider returns final assistant text after tool replay
- **When:** the turn is executed with search enabled
- **Then:** the Google path uses function declarations, executes the shared executor exactly once, and emits only the final assistant text

### Scenario: LiteRT completes one local envelope round trip
- **Given:** LiteRT emits `<tool_call>{"name":"tavily_web_search","arguments":{"query":"best folding phones 2026"}}</tool_call>` in streamed output and the executor returns the fixed JSON payload
- **When:** `LiteRtInferenceServiceImpl` handles the turn
- **Then:** the envelope is hidden from the UI, the tool result is injected into the same conversation, and final assistant text is emitted

### Scenario: MediaPipe completes one local envelope round trip
- **Given:** MediaPipe emits a complete local search envelope and the executor returns the fixed JSON payload
- **When:** `MediaPipeInferenceServiceImpl` continues generation after `addQueryChunk(...)`
- **Then:** the visible output contains only assistant text based on the tool result

## 2. Error Path & Edge Case Scenarios

### Scenario: Google tool mode does not silently fall back to the streaming-only path
- **Given:** the tool-capable Google path fails before any final assistant text is produced
- **When:** `GoogleInferenceServiceImpl` handles the failure
- **Then:** the service emits `InferenceEvent.Error(cause = IllegalStateException("Google tool execution failed before final response"), modelType = ModelType.FAST)` and the streaming-only path is not invoked

### Scenario: Malformed local envelope is surfaced as a typed failure
- **Given:** LiteRT or MediaPipe emits `<tool_call>{"name":"tavily_web_search","arguments":</tool_call>`
- **When:** the parser attempts to decode the envelope
- **Then:** the service emits `InferenceEvent.Error(cause = IllegalStateException("Malformed tool_call envelope"), modelType = ModelType.FAST)`

### Scenario: Missing query is rejected before executor invocation
- **Given:** the local or Google tool payload is `{"name":"tavily_web_search","arguments":{}}`
- **When:** the runtime validates the request
- **Then:** the service emits `InferenceEvent.Error(cause = IllegalArgumentException("Tool argument 'query' is required"), modelType = ModelType.FAST)`

### Scenario: Tool traces are not persisted after a local search turn
- **Given:** a LiteRT or MediaPipe search-enabled turn completes successfully
- **When:** the assistant message is saved
- **Then:** the stored message contains only final assistant text and no serialized tool envelope or tool result payload

## 3. Mutation Defense
### Lazy Implementation Risk
The most likely lazy implementation is to append the local envelope instructions but either show the raw envelope to the user or stop after tool execution instead of continuing the same conversation.

### Defense Scenario
- **Given:** LiteRT, MediaPipe, and Google each produce a first step that contains only a tool invocation and no final assistant text
- **When:** each runtime completes one search-enabled turn
- **Then:** the executor runs exactly once per runtime, no raw tool payload reaches the UI, and the only visible text is the final assistant response produced after tool replay
