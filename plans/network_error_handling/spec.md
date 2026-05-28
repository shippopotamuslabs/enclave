# Implementation Plan - Network Error Handling

This implementation plan defines the design and files needed to gracefully handle network errors for network-reliant tool executors and external model inference APIs implementing `RustCompatibleInferenceService`.

## 1. Architectural Overview

### 1.1 Tool Executors
Tool executors that rely on external APIs (`web_search`, `web_extract`, `search_x_recent`, `generate_image`, `generate_video`, `generate_music`, `x_get_thread`, `x_trending`, `x_user_tweets`, and `x_mention_search`) should not crash the agent loop when network connection or HTTP errors occur.
- **Current Behavior**: Exceptions like `IOException` propagate out of the tool executor, causing the entire prompt execution run to fail/crash.
- **Target Behavior**: The executors catch `IOException` (or any exception representing network failure), log a warning (instead of an error stacktrace), and return a standardized JSON error response:
  ```json
  {
    "success": false,
    "error": "Network error: <exception message>"
  }
  ```
  This allows the model to see the error output of the tool call and react gracefully (e.g. by telling the user the service is down or trying an alternative approach) instead of crashing.

### 1.2 Model API Inference Services
External model APIs implementing `RustCompatibleInferenceService` make network calls during `completeSingleTurn`.
- **Current Behavior**: Network errors propagate as raw SDK or OkHttp exceptions (like `IOException`, `ConnectException`, `SocketTimeoutException`).
- **Target Behavior**: Implementations of `RustCompatibleInferenceService` (`GoogleInferenceServiceImpl`, `AnthropicInferenceServiceImpl`, `BaseOpenAiSdkInferenceService`) catch network exceptions and wrap/throw a standardized `InferenceNetworkException`.
- This provides a structured, user-friendly exception that the outer agent loop (`RustLoopInferenceService` and UI) can process consistently.

---

## 2. File Map

### 2.1 New Files
| Path | Responsibility |
| --- | --- |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/InferenceNetworkException.kt` | **Create**: Define a unified custom exception class representing network connectivity failures when reaching remote LLM APIs. |

### 2.2 Modified Files
| Path | Responsibility |
| --- | --- |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/SearchToolExecutorImpl.kt` | **Modify**: Wrap search repository calls in `try-catch` blocks, catching `IOException` to return `{"success":false,"error":"Network error: ..."}`. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/ExtractToolExecutorImpl.kt` | **Modify**: Wrap search/extract repository calls in `try-catch` blocks, catching `IOException` to return `{"success":false,"error":"Network error: ..."}`. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/XSearchToolExecutorImpl.kt` | **Modify**: Wrap search repository calls in `try-catch` blocks, catching `IOException` to return `{"success":false,"error":"Network error: ..."}`. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/XGetThreadToolExecutor.kt` | **Modify**: Wrap search repository calls in `try-catch` blocks, catching `IOException` to return `{"success":false,"error":"Network error: ..."}`. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/XTrendingToolExecutor.kt` | **Modify**: Wrap search repository calls in `try-catch` blocks, catching `IOException` to return `{"success":false,"error":"Network error: ..."}`. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/XUserTweetsToolExecutor.kt` | **Modify**: Wrap timeline fetcher/repository calls in `try-catch` blocks, catching `IOException` to return `{"success":false,"error":"Network error: ..."}`. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/XMentionSearchToolExecutor.kt` | **Modify**: Wrap search repository calls in `try-catch` blocks, catching `IOException` to return `{"success":false,"error":"Network error: ..."}`. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/MediaToolExecutor.kt` | **Modify**: Ensure that `generateMediaUseCase` failures or caught network exceptions return the standard `buildErrorJson` response. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleInferenceServiceImpl.kt` | **Modify**: Wrap `generateContentStream` call in `completeSingleTurn` to catch network exceptions and throw `InferenceNetworkException`. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicInferenceServiceImpl.kt` | **Modify**: Wrap streaming/API client call in `completeSingleTurn` to catch network exceptions and throw `InferenceNetworkException`. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/BaseOpenAiSdkInferenceService.kt` | **Modify**: Wrap streaming/API client call in `completeSingleTurn` to catch network exceptions and throw `InferenceNetworkException`. |

---

## 3. Ordered Implementation Tasks

### Task 1: Create `InferenceNetworkException`
- Define the new exception under `:core:domain`:
  - Extends `java.io.IOException`.
  - Accepts `provider: String`, `message: String`, and `cause: Throwable?`.
  - Maps to: `InferenceNetworkException(provider, message, cause)`
- *Satisfies Scenarios: SCENARIO_INF_01, SCENARIO_INF_02, SCENARIO_INF_03, SCENARIO_INF_DEFENSE*

### Task 2: Update Search & Web Extract Tool Executors
- Modify `SearchToolExecutorImpl` and `ExtractToolExecutorImpl`.
- Wrap the repository call inside `execute` in a `try-catch` catching `java.io.IOException` and any provider-specific network exception.
- When caught:
  - Log a warning with the error details.
  - Return `ToolExecutionResult(toolName = request.toolName, resultJson = "{\"success\":false,\"error\":\"Network error: ${e.message}\"}")`.
- Update `SearchToolExecutorImplTest.kt` and `ExtractToolExecutorTest.kt` to verify.
- *Satisfies Scenarios: SCENARIO_TOOL_01, SCENARIO_TOOL_02, SCENARIO_TOOL_DEFENSE*

### Task 3: Update X (Twitter) Tool Executors
- Modify `XSearchToolExecutorImpl`, `XGetThreadToolExecutor`, `XTrendingToolExecutor`, `XUserTweetsToolExecutor`, and `XMentionSearchToolExecutor`.
- Wrap their respective `xSearchRepository` / fetcher calls in `try-catch` catching `java.io.IOException`.
- Return `ToolExecutionResult` with `resultJson = "{\"success\":false,\"error\":\"Network error: ${e.message}\"}"`.
- Update unit tests in `XSearchToolExecutorImplTest.kt` and `XPlatformToolsTest.kt` to verify.
- *Satisfies Scenarios: SCENARIO_TOOL_03, SCENARIO_TOOL_04, SCENARIO_TOOL_05, SCENARIO_TOOL_06, SCENARIO_TOOL_07*

### Task 4: Update Media Tool Executor
- Modify `MediaToolExecutor`.
- Wrap the use case invocation in `execute` with a `try-catch` block to handle exceptions thrown directly as `IOException`.
- Ensure it maps all network-reliant failures to `buildErrorJson` with the `Network error: ` prefix.
- Update `MediaToolExecutorTest.kt` to verify.
- *Satisfies Scenarios: SCENARIO_TOOL_08, SCENARIO_TOOL_09, SCENARIO_TOOL_10*

### Task 5: Update Inference Services
- Modify `GoogleInferenceServiceImpl`, `AnthropicInferenceServiceImpl`, and `BaseOpenAiSdkInferenceService`.
- Wrap the API streaming/call in `completeSingleTurn` with a `try-catch` that captures network exceptions (e.g. `IOException` or known connection exceptions).
- Throw `InferenceNetworkException(provider = "<PROVIDER>", message = e.message ?: "Connection error", cause = e)`.
- Update `GoogleInferenceServiceImplTest.kt`, `AnthropicInferenceServiceImplTest.kt`, and `BaseOpenAiSdkInferenceServiceTest.kt` to verify.
- *Satisfies Scenarios: SCENARIO_INF_01, SCENARIO_INF_02, SCENARIO_INF_03, SCENARIO_INF_DEFENSE*
