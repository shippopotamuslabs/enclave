# Technical Specification: Real Tavily and Settings

## 1. Objective
Replace the stubbed search executor with a Tavily-backed implementation and expose the feature through persisted settings. This ticket must add the Tavily repository, network wiring, secure key alias storage, settings repository fields, settings workflow models, and BYOK settings UI needed to enable and configure search without changing the response contract or chat persistence model.

Acceptance criteria:
- `TavilySearchRepository` maps live Tavily responses into the same top-level JSON shape returned by the stub executor.
- The existing executor path switches from stubbed output to repository-backed output without changing provider-side tool loops.
- `ApiKeyManager` stores the Tavily key under a dedicated alias.
- `SettingsRepository`, `SettingsWorkflowModels`, `SettingsViewModel`, and the BYOK UI expose search enablement and Tavily key state.
- Chat persistence still stores only final assistant text and never tool traces.

## 2. System Architecture

### Target Files
- Create `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/TavilySearchRepository.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/StubSearchToolExecutor.kt`
- Modify `app/src/main/kotlin/com/browntowndev/pocketcrew/app/NetworkModule.kt`
- Modify `core/data/build.gradle.kts`
- Modify `gradle/libs.versions.toml`
- Modify `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManager.kt`
- Modify `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/SettingsRepository.kt`
- Modify `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/SettingsRepositoryImpl.kt`
- Modify `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/settings/SettingsWorkflowModels.kt`
- Modify `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt`
- Modify `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureScreen.kt`
- Modify `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureForms.kt`
- Create `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/TavilySearchRepositoryTest.kt`
- Modify `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/SettingsRepositoryTest.kt`
- Modify `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManagerTest.kt`
- Modify `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModelTest.kt`

### Component Boundaries
`core/data` owns live Tavily access and mapping. `TavilySearchRepository` encapsulates request construction, response parsing, and error handling while using the shared `OkHttpClient` from `NetworkModule`. The inference executor remains in `:feature:inference` but delegates to `TavilySearchRepository` instead of returning a fixed stub. `ApiKeyManager`, `SettingsRepository`, and `SettingsRepositoryImpl` own persisted configuration. `SettingsWorkflowModels`, `SettingsViewModel`, `ByokConfigureScreen`, and `ByokConfigureForms` expose the feature to the UI without leaking repository or credential details into presentation code.

## 3. Data Models & Schemas
Reuse the `ToolExecutionResult` contract and the fixed search result shape:
- top-level `query`
- top-level `results`
- each result contains `title`, `url`, `content`, and `score`

Add settings-facing fields for:
- search enabled state
- Tavily key present state
- Tavily key update actions

Do not add any new `Message`, `Role`, `MessageEntity`, or Room table fields. `ApiKeyManager` must store the Tavily key under a dedicated alias such as `tavily_web_search`.

## 4. API Contracts & Interfaces
`TavilySearchRepository` must expose one search method that accepts a query string and returns data that can be serialized into the fixed JSON contract. `StubSearchToolExecutor` must keep its public executor interface but delegate to the repository-backed implementation when live search is enabled.

`SettingsRepository` and `SettingsWorkflowModels` must expose:
- a boolean search-enabled field
- a boolean key-present field
- actions for setting or clearing the Tavily key

`SettingsViewModel` and the BYOK UI must:
- render the search-enabled toggle
- render Tavily key entry state
- persist changes through the settings workflow

Typed failures:
- Tavily HTTP failure -> `IOException`
- invalid or missing Tavily key when live search is enabled -> `IllegalStateException`

## 5. Permissions & Config Delta
No Android runtime permissions or manifest changes. Config changes are required:
- `core/data/build.gradle.kts` and `gradle/libs.versions.toml` may add the JSON support needed to parse Tavily responses
- `app/src/main/kotlin/com/browntowndev/pocketcrew/app/NetworkModule.kt` must expose the shared `OkHttpClient` used by `TavilySearchRepository`
- `ApiKeyManager` must add a dedicated Tavily key alias
- No ProGuard, Room schema, or app-permission changes are part of this ticket

## 6. Constitution Audit
This design adheres to the project's core architectural rules by keeping live HTTP and secure storage in `:core:data`, feature flags and workflow state in `:core:domain`, UI controls in `:feature:settings`, and leaving inference loops and chat persistence contracts untouched.

## 7. Cross-Spec Dependencies
Depends on `13A-shared-and-remote-search-skill`. This ticket can land independently of `13B-google-and-local-envelope-search-skill` and `13C-llama-search-tool-bridge` as long as the shared executor contract from `13A` is already in place.
