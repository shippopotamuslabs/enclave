# Plan: SearchToolExecutor Architectural Cleanup

## Objective
Remove the "stubbing" mechanism and architectural anti-patterns from `StubSearchToolExecutor`. According to Clean Architecture, the implementation of `ToolExecutorPort` that orchestrates data layer components (e.g., `TavilySearchRepository`) belongs in the `core:data` module. The manual parsing logic for Llama/MediaPipe should remain in `feature:inference` where it belongs. Furthermore, `GenerateChatResponseUseCase` must correctly populate `availableTools` and `toolingEnabled` for local models when search is enabled in settings.

## Key Files & Context
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/StubSearchToolExecutor.kt`
- `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/StubSearchToolExecutorTest.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/SearchToolExecutorImpl.kt` (New)
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GenerateChatResponseUseCase.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/DataModule.kt`
- `app/src/main/kotlin/com/browntowndev/pocketcrew/app/EngineModule.kt`

## Implementation Steps

### 1. Extract Parsing Logic (`feature:inference`)
- Move `SearchToolSupport` out of `StubSearchToolExecutor.kt` into a dedicated `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/SearchToolSupport.kt` file.

### 2. Create `SearchToolExecutorImpl` (`core:data`)
- Create `SearchToolExecutorImpl` in `core:data` implementing `ToolExecutorPort`.
- Remove the `stubResultJson` fallback mechanism. If search is disabled or the repository is missing, it should throw an `IllegalStateException`.
- Move and rename the unit tests to `core:data`'s test directory (`SearchToolExecutorImplTest.kt`).
- Provide `SearchToolExecutorImpl` in `DataRepositoryModule` via a `@Binds` method.

### 3. Update DI Configurations (`app` and `feature:inference`)
- Remove the provider for `ToolExecutorPort` from `EngineModule.kt` because `DataRepositoryModule` will bind it.
- Update `InferenceFactoryImpl.kt` to inject `ToolExecutorPort` naturally (no manual instantiation).

### 4. Fix `GenerateChatResponseUseCase` (`core:domain`)
- Update `toolingEnabled` and `availableTools` to rely solely on `searchEnabled` instead of combining it with `config?.isLocal == false`. Local models must have tools advertised correctly in `GenerationOptions` for LiteRT to function properly and for architectural consistency.

### 5. Cleanup
- Remove the old `StubSearchToolExecutor.kt` and `StubSearchToolExecutorTest.kt` using `git rm` (or by emptying the files if shell execution isn't possible, then manually deleting).

## Verification & Testing
- Build the app and run the unit tests.
- Verify `SearchToolExecutorImplTest` covers the execution errors when search is disabled.
- Verify the local search tool continues to work in the device environment without any stub fallbacks.