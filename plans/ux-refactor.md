# Plan: Fix Dynamic Model Inference Service Resolution Bug

## Background & Motivation
Currently, `EngineModule.kt` provisions the `LlmInferencePort` implementations (`LlamaInferenceServiceImpl`, `MediaPipeInferenceServiceImpl`, or `LiteRtInferenceServiceImpl`) as `@Singleton` dependencies. The decision of which implementation to use for a given `ModelType` (like `FAST`, `MAIN`) is made **once at application startup** based on the file extension of the currently assigned model in the database.

If the user goes to the `ModelConfigurationScreen` and swaps the assigned model (e.g., swapping a `.gguf` model for a `.task` model) while the application is running, the singleton `LlmInferencePort` does not change. The next time the inference service is invoked, the locked-in service (e.g., `LlamaInferenceServiceImpl`) fetches the new model path from the database, receives a path to a `.task` file, and passes it to the underlying engine (`llama.cpp`). The engine fails to load the mismatched file format, resulting in an error bubble in the Chat screen (often reported as a "file not found" or "failed to load model" exception from the native layer). 

## Scope & Impact
- **Target Files**: 
  - `app/src/main/kotlin/com/browntowndev/pocketcrew/app/EngineModule.kt`
  - `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImpl.kt`
  - `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImplTest.kt` (New test file)
- **Impact**: The resolution of inference services will become dynamic at inference time rather than static at dependency injection time, completely resolving format mismatch crashes when swapping models at runtime.

## Proposed Solution

1. **Refactor `InferenceFactoryImpl.kt`**:
   - Update `InferenceFactoryImpl` to depend on `ModelRegistryPort` and the individual service components (or factories for them) rather than taking `Provider<LlmInferencePort>` for each `ModelType`.
   - Maintain a cache of active `LlmInferencePort` instances by `ModelType`.
   - In `getInferenceService(modelType)`:
     - Fetch the current `LocalModelAsset` from `ModelRegistryPort`.
     - Determine the required service type from the file extension (`.gguf` vs `.task`).
     - If the required service type differs from the cached instance for that `ModelType` (or if it's not cached yet):
       - Call `closeSession()` on the old cached instance to free native resources.
       - Instantiate the new correct implementation (`LlamaInferenceServiceImpl`, `MediaPipeInferenceServiceImpl`, or `LiteRtInferenceServiceImpl`).
       - Update the cache.
     - Return the cached instance.

2. **Refactor `EngineModule.kt`**:
   - Remove the `provideMainInferenceService`, `provideFastInferenceService`, etc. methods.
   - Remove the static `createInferenceService` helper function.
   - Update `bindInferenceFactory` / `provideInferenceFactory` to ensure it has the necessary dependencies to dynamically instantiate the services.

## Verification & Testing

### Unit Test Scenario
Create `InferenceFactoryImplTest.kt`:
1. **Setup**: Provide a `FakeModelRegistry` and mock factories for the services.
2. **Given**: The active model for `ModelType.FAST` is set to `model.gguf`.
3. **When**: `getInferenceService(ModelType.FAST)` is called.
4. **Then**: It returns an instance of `LlamaInferenceServiceImpl`.
5. **Given**: The active model for `ModelType.FAST` is changed in the registry to `model.task`.
6. **When**: `getInferenceService(ModelType.FAST)` is called again.
7. **Then**: The factory automatically calls `closeSession()` on the old Llama instance, creates, and returns a new instance of `MediaPipeInferenceServiceImpl` (or `LiteRtInferenceServiceImpl`).

This ensures that the exact bug scenario is prevented, as the factory will adapt to runtime database changes rather than relying on startup state.