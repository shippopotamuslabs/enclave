# Test Specification - Network Error Handling

This test specification describes scenarios to verify the graceful handling of network errors in tool executors and remote model API inference services.

## Scenarios

### SCENARIO_TOOL_01: Web Search Tool Executor catches network exceptions
- **Given** `SearchToolExecutorImpl` with mocked `tavilySearchRepository` and `exaSearchRepository`
- **And** Search provider is configured as Tavily
- **And** `tavilySearchRepository.search` throws `IOException("Connection timed out")`
- **When** the search tool executor is executed with query "Kotlin Multiplatform"
- **Then** it should log a warning
- **And** return a `ToolExecutionResult` with `resultJson` containing exactly:
  ```json
  {"success":false,"error":"Network error: Connection timed out"}
  ```

### SCENARIO_TOOL_02: Web Extract Tool Executor catches network exceptions
- **Given** `ExtractToolExecutorImpl` with mocked `tavilySearchRepository` and `exaSearchRepository`
- **And** Search provider is configured as Exa
- **And** `exaSearchRepository.extract` throws `IOException("DNS resolution failed")`
- **When** the extract tool executor is executed with URL "https://example.com"
- **Then** it should log a warning
- **And** return a `ToolExecutionResult` with `resultJson` containing exactly:
  ```json
  {"success":false,"error":"Network error: DNS resolution failed"}
  ```

### SCENARIO_TOOL_03: X Search Tool Executor catches network exceptions
- **Given** `XSearchToolExecutorImpl` with mocked `xSearchRepository`
- **And** `xSearchRepository.search` throws `IOException("Connection refused")`
- **When** the executor is executed with query keywords "Compose"
- **Then** it should log a warning
- **And** return a `ToolExecutionResult` with `resultJson` containing exactly:
  ```json
  {"success":false,"error":"Network error: Connection refused"}
  ```

### SCENARIO_TOOL_04: X Get Thread Tool Executor catches network exceptions
- **Given** `XGetThreadToolExecutor` with mocked `xSearchRepository`
- **And** `xSearchRepository.getThread` throws `IOException("Read timeout")`
- **When** the executor is executed with tweet URL "https://x.com/tweet/123"
- **Then** it should log a warning
- **And** return a `ToolExecutionResult` with `resultJson` containing exactly:
  ```json
  {"success":false,"error":"Network error: Read timeout"}
  ```

### SCENARIO_TOOL_05: X Trending Tool Executor catches network exceptions
- **Given** `XTrendingToolExecutor` with mocked `xSearchRepository`
- **And** `xSearchRepository.getTrending` (or search endpoint for trends) throws `IOException("SSL handshake failed")`
- **When** the executor is executed
- **Then** it should log a warning
- **And** return a `ToolExecutionResult` with `resultJson` containing exactly:
  ```json
  {"success":false,"error":"Network error: SSL handshake failed"}
  ```

### SCENARIO_TOOL_06: X User Tweets Tool Executor catches network exceptions
- **Given** `XUserTweetsToolExecutor` with mocked `XUserTimelineFetcher`
- **And** Fetcher throws `IOException("API rate limit exceeded or connection reset")`
- **When** the executor is executed
- **Then** it should log a warning
- **And** return a `ToolExecutionResult` with `resultJson` containing exactly:
  ```json
  {"success":false,"error":"Network error: API rate limit exceeded or connection reset"}
  ```

### SCENARIO_TOOL_07: X Mention Search Tool Executor catches network exceptions
- **Given** `XMentionSearchToolExecutor` with mocked `xSearchRepository`
- **And** `xSearchRepository` search throws `IOException("Socket closed")`
- **When** the executor is executed
- **Then** it should log a warning
- **And** return a `ToolExecutionResult` with `resultJson` containing exactly:
  ```json
  {"success":false,"error":"Network error: Socket closed"}
  ```

### SCENARIO_TOOL_08: Generate Image Tool Executor catches network exceptions
- **Given** `MediaToolExecutor` with mocked `GenerateMediaUseCase`
- **And** Use case throws `IOException("Service Unavailable")` when generating an image
- **When** the executor is executed for GENERATE_IMAGE
- **Then** it should log a warning
- **And** return a `ToolExecutionResult` with `resultJson` containing exactly:
  ```json
  {"success":false,"error":"Network error: Service Unavailable"}
  ```

### SCENARIO_TOOL_09: Generate Video Tool Executor catches network exceptions
- **Given** `MediaToolExecutor` with mocked `GenerateMediaUseCase`
- **And** Use case throws `IOException("Server unreachable")` when generating a video
- **When** the executor is executed for GENERATE_VIDEO
- **Then** it should log a warning
- **And** return a `ToolExecutionResult` with `resultJson` containing exactly:
  ```json
  {"success":false,"error":"Network error: Server unreachable"}
  ```

### SCENARIO_TOOL_10: Generate Music Tool Executor catches network exceptions
- **Given** `MediaToolExecutor` with mocked `GenerateMediaUseCase`
- **And** Use case throws `IOException("Gateway timeout")` when generating music
- **When** the executor is executed for GENERATE_MUSIC
- **Then** it should log a warning
- **And** return a `ToolExecutionResult` with `resultJson` containing exactly:
  ```json
  {"success":false,"error":"Network error: Gateway timeout"}
  ```

### SCENARIO_TOOL_DEFENSE: Mutation defense for Tool Executors error messages
- **Given** any of the network-reliant tool executors
- **And** the underlying HTTP repository throws `IOException` with a dynamic message like `"Random HTTP error 502: Bad Gateway"`
- **When** the executor runs
- **Then** it must not return a hardcoded/static error string like `"Network error: Connection timed out"`
- **And** the returned `resultJson` must dynamically incorporate the exact message:
  ```json
  {"success":false,"error":"Network error: Random HTTP error 502: Bad Gateway"}
  ```

---

### SCENARIO_INF_01: Google Inference Service maps network errors
- **Given** `GoogleInferenceServiceImpl` with mocked `GoogleGenAiSdkClient`
- **And** `GoogleGenAiSdkClient.generateContentStream` throws `IOException("Gemini API server down")`
- **When** `completeSingleTurn` is called
- **Then** it must catch the exception
- **And** throw `InferenceNetworkException` with `provider = "GOOGLE"`, containing the message and cause

### SCENARIO_INF_02: Anthropic Inference Service maps network errors
- **Given** `AnthropicInferenceServiceImpl` with mocked client/network stack
- **And** the client throws `IOException("Anthropic API rate limit or socket error")`
- **When** `completeSingleTurn` is called
- **Then** it must catch the exception
- **And** throw `InferenceNetworkException` with `provider = "ANTHROPIC"`, containing the message and cause

### SCENARIO_INF_03: OpenAI Inference Service maps network errors
- **Given** `BaseOpenAiSdkInferenceService` (OpenAI model configuration) with mocked client/network stack
- **And** the client throws `IOException("OpenAI endpoint connection reset")`
- **When** `completeSingleTurn` is called
- **Then** it must catch the exception
- **And** throw `InferenceNetworkException` with `provider = "OPENAI"`, containing the message and cause

### SCENARIO_INF_DEFENSE: Mutation defense for Inference service exceptions
- **Given** any network-reliant inference service implementing `RustCompatibleInferenceService`
- **And** the client throws `IOException` with a dynamic message like `"Fatal proxy connection failure"`
- **When** `completeSingleTurn` is called
- **Then** it must throw `InferenceNetworkException` where the message contains `"Fatal proxy connection failure"` and the cause is the original `IOException`
