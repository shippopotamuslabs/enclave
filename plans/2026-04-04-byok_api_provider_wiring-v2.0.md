# BYOK API Provider Support Implementation Plan

## Objective

Wire up BYOK (Bring Your Own Key) provider support through the current architecture, enabling the application to route inference requests to external APIs (like OpenAI) using the official Java library when an API model is assigned to a specific `ModelType` slot, falling back to on-device models otherwise.

## Architecture Analysis

Currently, `InferenceFactoryImpl` and `GenerateChatResponseUseCase` depend directly on `ModelRegistryPort`, which is hardcoded to return `LocalModelAsset` and `LocalModelConfiguration`. 

To support both API and Local models dynamically, we need a new abstraction layer (`ActiveModelProviderPort`) that consults `DefaultModelRepositoryPort` to determine the active assignment for a `ModelType` and fetches the appropriate configuration (Local or API).

## Implementation Plan

### 1. Security & Key Management

- [ ] Task 1. Create `ApiKeyProviderPort` interface in `core:domain` (`domain/port/security/ApiKeyProviderPort.kt`) with method `getApiKey(credentialAlias: String): String?`. *Rationale: The inference layer needs secure access to decrypted API keys without depending directly on the data layer's `EncryptedSharedPreferences` implementation.*
- [ ] Task 2. Create `ApiKeyProviderImpl` in `core:data` (`core/data/security/ApiKeyProviderImpl.kt`) that implements `ApiKeyProviderPort` by delegating to the existing `ApiKeyManager`.
- [ ] Task 3. Bind `ApiKeyProviderImpl` to `ApiKeyProviderPort` in `DataModule.kt`.

### 4. Active Model Abstraction (Domain Layer)

- [ ] Task 4. Create a unified `ActiveModelConfiguration` interface or data class in `core:domain` that encapsulates the shared properties of `LocalModelConfiguration` and `ApiModelConfiguration` needed for generation (temperature, topK, topP, maxTokens, systemPrompt, thinkingEnabled/isReasoning).
- [ ] Task 5. Create `ActiveModelProviderPort` interface in `core:domain` with method `suspend fun getActiveConfiguration(modelType: ModelType): ActiveModelConfiguration?`.
- [ ] Task 6. Create `ActiveModelProviderImpl` in `core:data` that implements `ActiveModelProviderPort`. It should:
  1. Call `DefaultModelRepositoryPort.getDefault(modelType)`
  2. If `apiConfigId` is set, fetch from `ApiModelRepositoryPort` and map to `ActiveModelConfiguration`.
  3. If `localConfigId` is set, fetch from `ModelRegistryPort` and map to `ActiveModelConfiguration`.
- [ ] Task 7. Bind `ActiveModelProviderImpl` to `ActiveModelProviderPort` in `DataModule.kt`.

### 5. Use Case Updates

- [ ] Task 8. Update `GenerateChatResponseUseCase` to inject and use `ActiveModelProviderPort` instead of `ModelRegistryPort` for fetching generation options. *Rationale: This decouples the chat generation logic from assuming all models are local.*
- [ ] Task 9. Update `GenerationOptions` mapping in `GenerateChatResponseUseCase` to use the unified `ActiveModelConfiguration`.

### 6. API Inference Service Implementation

- [ ] Task 10. Create `ApiInferenceServiceImpl` in `feature:inference` implementing `LlmInferencePort`. *Rationale: This service will wrap the official OpenAI Java library to execute API requests.*
- [ ] Task 11. Inject the OpenAI Java library client into `ApiInferenceServiceImpl`.
- [ ] Task 12. Implement `setHistory` in `ApiInferenceServiceImpl` to maintain conversation context.
- [ ] Task 13. Implement `sendPrompt` using the OpenAI Java library's streaming chat completions API. Map the stream chunks to domain `InferenceEvent` types (`PartialResponse`, `Finished`, `Error`).
- [ ] Task 14. Implement `closeSession` to properly cancel any active API requests to prevent leaking connections or consuming unnecessary tokens.

### 7. Inference Factory Integration

- [ ] Task 15. Inject `DefaultModelRepositoryPort`, `ApiModelRepositoryPort`, and `ApiKeyProviderPort` into `InferenceFactoryImpl`.
- [ ] Task 16. Update `InferenceFactoryImpl.resolveService(modelType)` to first query `DefaultModelRepositoryPort.getDefault(modelType)`.
- [ ] Task 17. Add logic: If the default assignment has an `apiConfigId`, fetch the configuration and credentials. Use `ApiKeyProviderPort` to get the API key. Instantiate and return `ApiInferenceServiceImpl` configured with the key and base URL.
- [ ] Task 18. Add fallback logic: If `apiConfigId` is null, or if fetching the API key fails, fall back to the existing local model resolution logic (`modelRegistry.getRegisteredAsset`).
- [ ] Task 19. Update the `activeIdentity` tracking in `InferenceFactoryImpl` to properly identify API models (e.g., `"api-${apiConfig.id}"`) to ensure the service is reused correctly across requests.

## Verification Criteria

- [ ] `ActiveModelProviderImpl` correctly resolves the active configuration regardless of whether it is a Local or API model.
- [ ] `GenerateChatResponseUseCase` successfully builds `GenerationOptions` using the new abstraction.
- [ ] `InferenceFactoryImpl` routes to `ApiInferenceServiceImpl` when an API model is assigned to a slot.
- [ ] `ApiInferenceServiceImpl` successfully uses the official OpenAI Java library to stream responses.
- [ ] The system gracefully falls back to on-device models if API credentials or configurations are missing.

## Potential Risks and Mitigations

1. **Risk: OpenAI library blocking the main thread or causing coroutine deadlocks.**
   *Mitigation:* Ensure all blocking calls or synchronous streams from the Java library are wrapped in `Dispatchers.IO` using `flowOn(Dispatchers.IO)` or `withContext`.
2. **Risk: Missing API keys causing silent failures.**
   *Mitigation:* If `ApiKeyProviderPort` returns null for an assigned API model, log a clear error and throw an exception that the UI can catch to prompt the user to re-enter their key, rather than silently falling back to a local model which might confuse the user.

## Alternative Approaches

1. **Alternative 1:** Instead of creating `ActiveModelProviderPort`, modify `ModelRegistryPort` to handle API models as well. *Trade-off: This would bloat `ModelRegistryPort` and violate the Single Responsibility Principle, as it currently cleanly manages local file assets and metadata.*
2. **Alternative 2:** Have `InferenceFactoryImpl` construct the `GenerationOptions` internally instead of doing it in `GenerateChatResponseUseCase`. *Trade-off: This would require changing the `LlmInferencePort` contract, but it would centralize all configuration fetching within the inference layer.*