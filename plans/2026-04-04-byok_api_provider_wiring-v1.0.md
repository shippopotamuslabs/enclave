# BYOK API Provider Support Implementation Plan

## Objective

Wire up the BYOK (Bring Your Own Key) provider support through the current architecture. This will enable the application to route inference requests to external APIs (Anthropic, OpenAI, Google) when an API model is assigned to a specific slot, falling back to on-device models otherwise.

## Implementation Plan

### 1. Security & Domain Abstraction

- [ ] Task 1. Create `ApiKeyProviderPort` interface in `core:domain` (`core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/security/ApiKeyProviderPort.kt`) with a method `getApiKey(credentialAlias: String): String?`. *Rationale: The domain and inference layers need a secure way to access decrypted API keys without depending directly on `EncryptedSharedPreferences`.*
- [ ] Task 2. Create `ApiKeyProviderImpl` in `core:data` (`core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyProviderImpl.kt`) that implements `ApiKeyProviderPort` by delegating to the existing `ApiKeyManager`.
- [ ] Task 3. Bind `ApiKeyProviderImpl` to `ApiKeyProviderPort` in `DataModule.kt`.

### 2. API Provider Strategies

- [ ] Task 4. Create `ApiProviderStrategy` interface in `feature:inference` to abstract provider-specific logic (building requests, parsing SSE streams, generating headers).
- [ ] Task 5. Implement `AnthropicStrategy` handling the Messages API format, custom headers (`x-api-key`, `anthropic-version`), and parsing Anthropic's specific SSE event types (`content_block_delta`, `message_stop`, etc.).
- [ ] Task 6. Implement `OpenAiStrategy` handling the Chat Completions API format, `Authorization: Bearer` header, and parsing OpenAI's SSE chunk format.
- [ ] Task 7. Implement `GoogleStrategy` handling the Gemini API format, appending the key to the URL or headers, and parsing Gemini's streaming format.
- [ ] Task 8. Create a factory class or function to instantiate the correct `ApiProviderStrategy` based on the `ApiProvider` enum and configuration.

### 3. API Inference Service Implementation

- [ ] Task 9. Create `ApiInferenceServiceImpl` in `feature:inference` implementing `LlmInferencePort`. *Rationale: This is the core service that will execute HTTP requests instead of running local inference.*
- [ ] Task 10. Implement `setHistory` in `ApiInferenceServiceImpl` to maintain the conversation context internally.
- [ ] Task 11. Implement `sendPrompt` using `OkHttpClient`. Build a lightweight SSE reader using Okio's `BufferedSource.readUtf8Line()` to process the stream without adding new library dependencies.
- [ ] Task 12. Map the parsed SSE events from the strategy into pure domain `InferenceEvent` types (`Thinking`, `PartialResponse`, `Finished`, `Error`) and emit them via the Flow.
- [ ] Task 13. Ensure `closeSession` properly cancels any active OkHttp call to prevent leaking connections.

### 4. Inference Factory Integration

- [ ] Task 14. Inject `DefaultModelRepositoryPort`, `ApiModelRepositoryPort`, and `ApiKeyProviderPort` into `InferenceFactoryImpl`.
- [ ] Task 15. Update `InferenceFactoryImpl.resolveService(modelType)` to first query `DefaultModelRepositoryPort.getDefault(modelType)`.
- [ ] Task 16. Add logic: If the default assignment has an `apiConfigId`, fetch the configuration, credentials, and API key. Instantiate and return `ApiInferenceServiceImpl`.
- [ ] Task 17. Add fallback logic: If `apiConfigId` is null, or if fetching the API key fails, fall back to the existing on-device model resolution logic.
- [ ] Task 18. Update the `activeIdentity` tracking in `InferenceFactoryImpl` to properly identify API models (e.g., using a prefix like `"api-${apiConfig.id}"`) to ensure the service is reused correctly across requests.

## Verification Criteria

- [ ] `InferenceFactoryImpl` successfully routes to `ApiInferenceServiceImpl` when an API model is assigned to a slot.
- [ ] `ApiInferenceServiceImpl` correctly maintains conversation history across multiple `sendPrompt` calls.
- [ ] SSE streams from Anthropic, OpenAI, and Google are correctly parsed and emitted as `InferenceEvent`s.
- [ ] Active HTTP connections are successfully cancelled when `closeSession()` is called or the Flow is cancelled.
- [ ] The system gracefully falls back to on-device models if API credentials or configurations are missing.

## Potential Risks and Mitigations

1. **Risk: SSE parsing errors causing dropped tokens or crashes.**
   *Mitigation:* Implement robust error handling in the custom SSE reader. Write exhaustive unit tests for `AnthropicStrategy`, `OpenAiStrategy`, and `GoogleStrategy` using mocked JSON responses to ensure all edge cases (like `[DONE]` messages) are handled.
2. **Risk: Leaking HTTP connections if generation is cancelled.**
   *Mitigation:* Use Kotlin Coroutines `awaitClose` block within the `callbackFlow` or `flow` builder in `sendPrompt` to explicitly cancel the OkHttp `Call` when the collector stops listening.
3. **Risk: Blocking the main thread during HTTP calls.**
   *Mitigation:* Ensure all OkHttp network calls are executed on `Dispatchers.IO` within the `ApiInferenceServiceImpl`.

## Alternative Approaches

1. **Alternative 1:** Add `okhttp-sse` dependency instead of writing a custom SSE reader. *Trade-off: Increases app size and dependency surface area slightly, but reduces the risk of parsing bugs.*
2. **Alternative 2:** Use Ktor Client instead of OkHttp. *Trade-off: Ktor has built-in SSE support and is Kotlin-native, but OkHttp is already present and configured in the project's `NetworkModule`, making it the path of least resistance.*