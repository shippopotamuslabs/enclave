# BYOK API Provider Support Implementation Plan

## Objective

Wire up BYOK (Bring Your Own Key) provider support through the current architecture, enabling the application to route inference requests to external APIs (like OpenAI) using the official Java library when an API model is assigned to a specific `ModelType` slot. Simultaneously, refactor `ModelRegistryPort` to remove obsolete `ModelType` assignment logic.

## Architecture Analysis

Currently, `InferenceFactoryImpl` and `GenerateChatResponseUseCase` depend directly on `ModelRegistryPort`, which is hardcoded to return `LocalModelAsset` and `LocalModelConfiguration`. Furthermore, `ModelRegistryPort` handles `ModelType` slot assignments, which overlaps with the new `DefaultModelRepositoryPort`.

To fix this:
1. Strip `ModelRegistryPort` of all `ModelType` awareness, making it a pure Local Model CRUD repository.
2. Introduce `ActiveModelProviderPort` to abstract whether a slot is powered by a Local or API model.

## Implementation Plan

### 1. Security & Key Management

- [ ] Task 1. Create `ApiKeyProviderPort` interface in `core:domain` (`domain/port/security/ApiKeyProviderPort.kt`) with method `getApiKey(credentialAlias: String): String?`.
- [ ] Task 2. Create `ApiKeyProviderImpl` in `core:data` (`core/data/security/ApiKeyProviderImpl.kt`) that implements `ApiKeyProviderPort` by delegating to `ApiKeyManager`.
- [ ] Task 3. Bind `ApiKeyProviderImpl` to `ApiKeyProviderPort` in `DataModule.kt`.

### 2. ModelRegistryPort Cleanup & Refactor

- [ ] Task 4. Remove all `ModelType`-aware methods from `ModelRegistryPort` and `ModelRegistryImpl` (`getRegisteredAsset`, `getRegisteredConfiguration`, `getRegisteredSelection`, `observeAsset`, `observeConfiguration`, `setDefaultLocalConfig`, `getRegisteredConfigurations`).
- [ ] Task 5. Rename `getRegisteredAssets` to `getAllLocalAssets` and `observeAssets` to `observeAllLocalAssets` for clarity.
- [ ] Task 6. Add `suspend fun getAssetByConfigId(configId: Long): LocalModelAsset?` to `ModelRegistryPort` and implement it in `ModelRegistryImpl` (fetch config, then fetch associated asset).
- [ ] Task 7. Extract `activateLocalModel` logic from `ModelRegistryImpl` into a new domain use case: `ActivateLocalModelUseCase`. Update DI and callers to use the use case instead.

### 3. Active Model Abstraction (Domain Layer)

- [ ] Task 8. Create `ActiveModelConfiguration` data class in `core:domain` encapsulating shared generation properties (temperature, topK, topP, maxTokens, systemPrompt, thinkingEnabled/isReasoning).
- [ ] Task 9. Create `ActiveModelProviderPort` interface in `core:domain` with method `suspend fun getActiveConfiguration(modelType: ModelType): ActiveModelConfiguration?`.
- [ ] Task 10. Create `ActiveModelProviderImpl` in `core:data` that implements `ActiveModelProviderPort` by querying `DefaultModelRepositoryPort`, then fetching from either `ModelRegistryPort` or `ApiModelRepositoryPort`.
- [ ] Task 11. Bind `ActiveModelProviderImpl` to `ActiveModelProviderPort` in `DataModule.kt`.

### 4. Use Case Updates

- [ ] Task 12. Update `GenerateChatResponseUseCase` to inject and use `ActiveModelProviderPort` instead of `ModelRegistryPort` for fetching generation options.
- [ ] Task 13. Update `GenerationOptions` mapping in `GenerateChatResponseUseCase` to use the unified `ActiveModelConfiguration`.

### 5. API Inference Service Implementation

- [ ] Task 14. Create `ApiInferenceServiceImpl` in `feature:inference` implementing `LlmInferencePort`.
- [ ] Task 15. Inject the official OpenAI Java library client into `ApiInferenceServiceImpl`.
- [ ] Task 16. Implement `setHistory` in `ApiInferenceServiceImpl` to maintain conversation context.
- [ ] Task 17. Implement `sendPrompt` using the OpenAI Java library's streaming chat completions API. Map the stream chunks to domain `InferenceEvent` types.
- [ ] Task 18. Implement `closeSession` to properly cancel any active API requests to prevent leaking connections.

### 6. Inference Factory Integration

- [ ] Task 19. Inject `DefaultModelRepositoryPort`, `ModelRegistryPort`, `ApiModelRepositoryPort`, and `ApiKeyProviderPort` into `InferenceFactoryImpl`.
- [ ] Task 20. Update `InferenceFactoryImpl.resolveService(modelType)` to query `DefaultModelRepositoryPort.getDefault(modelType)`.
- [ ] Task 21. Add logic: If `apiConfigId` is set, fetch credentials, use `ApiKeyProviderPort` to get the key, and return `ApiInferenceServiceImpl`.
- [ ] Task 22. Add fallback logic: If `localConfigId` is set, use `ModelRegistryPort.getAssetByConfigId(localConfigId)` to resolve the local `.gguf` or `.task` file and load the on-device engine.
- [ ] Task 23. Update the `activeIdentity` tracking in `InferenceFactoryImpl` to properly identify API models (e.g., `"api-${apiConfig.id}"`) to ensure the service is reused correctly across requests.

## Verification Criteria

- [ ] `ModelRegistryPort` contains zero references to `ModelType`.
- [ ] `ActiveModelProviderImpl` correctly resolves the active configuration regardless of whether it is a Local or API model.
- [ ] `InferenceFactoryImpl` successfully routes to `ApiInferenceServiceImpl` or the local engine based on `DefaultModelRepositoryPort` assignments.
- [ ] `ApiInferenceServiceImpl` successfully uses the official OpenAI Java library to stream responses.