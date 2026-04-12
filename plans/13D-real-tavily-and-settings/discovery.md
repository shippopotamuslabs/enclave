# Implementation Map

## Goal Summary
Replace the Step 1 stub executor with real Tavily-backed search and expose the feature through persisted settings. This ticket adds the repository and network wiring, secure key storage, search enablement settings, and BYOK UI updates while preserving the stable search result contract introduced earlier.

## Target Module Index
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/TavilySearchRepository.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/StubSearchToolExecutor.kt`
- `app/src/main/kotlin/com/browntowndev/pocketcrew/app/NetworkModule.kt`
- `core/data/build.gradle.kts`
- `gradle/libs.versions.toml`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManager.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/SettingsRepository.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/SettingsRepositoryImpl.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/settings/SettingsWorkflowModels.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureScreen.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureForms.kt`
- `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/TavilySearchRepositoryTest.kt`
- `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/SettingsRepositoryTest.kt`
- `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManagerTest.kt`
- `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModelTest.kt`

## Design Notes
- Reuse the `ToolExecutorPort` contract from `13A` and keep the same top-level JSON shape returned by the stub executor.
- `TavilySearchRepository` uses the app `OkHttpClient` rather than building a second networking stack.
- Store the Tavily key in `ApiKeyManager` under a dedicated alias like `tavily_web_search`.
- Add an explicit settings toggle and key-state fields instead of reusing `ApiCredentials`.
- Keep tool traces transient. This ticket changes only search enablement and credential storage, not chat persistence.

## Impact Radius
- Data-layer repository and networking
- Secure key storage
- Domain and data settings models
- Settings view model and BYOK configuration UI

## Non-Goals
- No remote-provider loop changes beyond consuming the new real executor
- No Google, LiteRT, MediaPipe, or llama runtime changes
- No database schema change for persisted tool traces

## Dependencies
- Depends on `13A-shared-and-remote-search-skill`
