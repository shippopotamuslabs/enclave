# Spec: Prompt Caching for Remote API Providers

## 1. Feature Summary
This feature enables prompt caching for remote API providers (OpenAI, xAI, Anthropic, and Google GenAI) to reduce latency and costs.
To prevent the volatile context management sidecars (like `stateLedgerJson`, `toolContextIndexJson`, `fileContextIndexJson`, and `cacheHints`) from invalidating the cache on every turn, they must be separated from the stable system prompt and placed at the very end of the message sequence (appended right before the latest user message or embedded in it).

Additionally, to prevent cache invalidations between the first turn (where tool calls are allowed) and the continuation turn (where tool results are processed and tool calls are disallowed), the list of tools must remain identical in the request payload. Disabling tool calls is handled via `toolChoice`/`toolConfig` parameters rather than omitting the tools array.

* **OpenAI & xAI:** 
  * The stable system prompt is prepended as the first system message.
  * The volatile context sidecars are appended as a separate system message at the very end of the history list, immediately preceding the latest user message.
  * If `allowToolCall` is false, the tools list is still included in the request, but `tool_choice` is set to `"none"` (or `toolChoice` parameter none) to prevent cache invalidation from changing the payload structure.
  * This keeps the prefix (system prompt + tools list + entire conversation history up to the last turn) 100% stable and cache-hit eligible.
* **Anthropic:** 
  * The stable system prompt is set in the `system` parameter.
  * The volatile context sidecars are prepended to the text content of the latest user message.
  * If `allowToolCall` is false, the tools list is still included, but `tool_choice` is set to `{"type": "none"}`.
  * Ephemeral cache control breakpoints are set on:
    1. The system prompt block.
    2. The last tool definition (if tools are present).
    3. The last block of the last message in the stable history.
  * This keeps the system prompt, tool definitions, and prior history stable and cached.
* **Google GenAI:** 
  * The server-side `CachedContent` resource is created using only the stable system prompt, stable tools, and the stable message history prefix (excluding the new turn and volatile sidecars) once the token count exceeds `1024` tokens.
  * The volatile context sidecars are prepended to the latest user message sent in the live generate content request, so they are never written into the cache and do not cause cache invalidation.
  * If `allowToolCall` is false, tools are still supplied to the generate request (which references the CachedContent that has tools), but `toolConfig` mode is set to `NONE`.
  * Old cache is reused on subsequent turns if the cached messages form a prefix of the history. If the delta exceeds `1024` tokens, the old cache is deleted and recreated.
  * Delete the active cache on `closeSession()`.

---

## 2. File Structure Table

| Action | Path | Responsibility |
|---|---|---|
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustCompatibleInferenceService.kt` | Update `completeSingleTurn` signature to accept separate `systemPrompt: String` and `contextSidecars: String?`. |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceService.kt` | Separate system prompt and volatile sidecars when invoking `completeSingleTurn`. |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/BaseOpenAiSdkInferenceService.kt` | Map system prompt (first message) and place volatile sidecars at the end of the history list. Maintain tool array stability using `toolChoice`. |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicInferenceServiceImpl.kt` | Prepend volatile sidecars to the latest user message and apply `CacheControlEphemeral` breakpoints. Maintain tool array stability using `toolChoice`. |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicRequestMapper.kt` | Update `sendPrompt` helper mapping to support static system prompt, last tool caching, and prepending sidecars to the prompt block. |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleGenAiSdkClient.kt` | Expose cached content APIs for creation and deletion. |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleInferenceServiceImpl.kt` | Implement stable cache creation, delta-based reuse, deletion on close, and prepending volatile sidecars to the latest message. Maintain tool array stability. |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/InferenceFactoryImpl.kt` | Adjust provider wiring. |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/LlamaInferenceServiceImpl.kt` | Merge stable system prompt and sidecars for local inference. |
| **Modify** | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/OpenRouterInferenceServiceImpl.kt` | Separate stable system prompt and end-of-list sidecars for OpenRouter. |
| **Modify** | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceServiceTest.kt` | Update signature in tests. |

---

## 3. API Contracts and Interfaces

### 3.1 `RustCompatibleInferenceService`
```kotlin
interface RustCompatibleInferenceService : LlmInferencePort {
    suspend fun completeSingleTurn(
        sessionMessages: List<AgentLoopMessage>,
        systemPrompt: String,
        contextSidecars: String?,
        availableTools: List<ToolDefinition>,
        allowToolCall: Boolean,
        onTextToken: suspend (String) -> Unit,
        onThinkingToken: suspend (String) -> Unit,
        options: GenerationOptions,
    ): AgentModelTurnResult
}
```

### 3.2 `GoogleGenAiSdkClient`
```kotlin
interface GoogleGenAiSdkClient {
    suspend fun generateContentStream(
        modelId: String,
        contents: List<Content>,
        config: GenerateContentConfig,
        allowToolCall: Boolean = false,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): GoogleSdkResult

    suspend fun createCachedContent(
        modelId: String,
        contents: List<Content>,
        systemInstruction: Content?,
        tools: List<com.google.genai.types.Tool>?,
        ttlSeconds: Long,
    ): String

    suspend fun deleteCachedContent(name: String)
}
```

---

## 4. Data Model or Schema Changes
No database schema or persistence changes are required.

---

## 5. Ordered Implementation Tasks

### Task 1: Update API Interfaces and Loop Host
* **Description:** Update `RustCompatibleInferenceService` interface signature for `completeSingleTurn`. Modify `RustLoopInferenceService` to separate the stable system prompt from the volatile context sidecars.
* **Files:**
  * `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustCompatibleInferenceService.kt`
  * `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceService.kt`
* **Scenarios verified:** `PC-01`

### Task 2: Adapt Non-API Services and Test Mocks
* **Description:** Update `LlamaInferenceServiceImpl`, `OpenRouterInferenceServiceImpl`, and `RustLoopInferenceServiceTest.kt` to match the new signature. In Llama, merge the stable prompt and sidecars. In OpenRouter, separate them just like OpenAI.
* **Files:**
  * `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/LlamaInferenceServiceImpl.kt`
  * `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/OpenRouterInferenceServiceImpl.kt`
  * `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceServiceTest.kt`
* **Scenarios verified:** `PC-01`

### Task 3: Implement OpenAI/xAI Cache-Friendly Mapping
* **Description:** Modify `BaseOpenAiSdkInferenceService` to prepend `systemPrompt` as the first system message. Place the volatile `contextSidecars` as a system message at the end of the mapped messages list (immediately before the latest user message). If `allowToolCall` is false but tools are available, include them in the request builder but specify `toolChoice("none")`.
* **Files:**
  * `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/BaseOpenAiSdkInferenceService.kt`
* **Scenarios verified:** `PC-01`

### Task 4: Implement Anthropic Stable Caching
* **Description:** Modify `AnthropicInferenceServiceImpl` and `AnthropicRequestMapper`:
  * Keep the `system` parameter static.
  * Prepend the volatile sidecars to the latest user message text content.
  * Include tools list even if `allowToolCall` is false, setting `tool_choice` to `none` type to ensure tools signature stability.
  * Add ephemeral cache control breakpoints to the system prompt, the last tool definition, and the last history message block (before the new turn).
* **Files:**
  * `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicInferenceServiceImpl.kt`
  * `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicRequestMapper.kt`
* **Scenarios verified:** `PC-02`, `PC-03`

### Task 5: Extend Google GenAI SDK Client
* **Description:** Update `GoogleGenAiSdkClientImpl` to accept a provider for `Client`. Implement `createCachedContent` and `deleteCachedContent` using the Caches API.
* **Files:**
  * `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleGenAiSdkClient.kt`
  * `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/InferenceFactoryImpl.kt`
* **Scenarios verified:** `PC-04`

### Task 6: Implement Google GenAI Stable Caching Lifecycle
* **Description:** Implement `GoogleInferenceServiceImpl` caching logic:
  * Build the cache prefix using only stable history messages (excluding latest user turn/message), static system prompt, and static tools.
  * Check token threshold (>1024 tokens) using `JTokkitTokenCounter` on the stable prefix.
  * Prepend volatile sidecars to the latest user message (which is not cached).
  * Reuse cache if prefix matches and delta is < 1024 tokens. Delete and recreate if delta is >= 1024 tokens.
  * Set `toolConfig` mode to `NONE` in config if `allowToolCall` is false, ensuring tools are preserved in the cached contents signature.
  * Delete cache on `closeSession()`.
* **Files:**
  * `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleInferenceServiceImpl.kt`
* **Scenarios verified:** `PC-04`, `PC-05`, `PC-06`, `PC-07`, `PC-08`

---

## 7. Drift and Escalation Rules
* **Drift Rule:** If the Anthropic SDK is updated and the `CacheControlEphemeral` model class signature changes, update `AnthropicRequestMapper` immediately to match the new builder name.
* **Escalation Rule:** If Google GenAI cache creation fails on the server side with a resource limit error or permission issue, catch the exception, log a warning, and fallback gracefully to standard non-cached generation by setting `cachedContent` to null. Do not block prompt generation.
