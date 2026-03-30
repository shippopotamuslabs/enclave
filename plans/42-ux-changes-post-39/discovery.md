# Discovery Report — 42-ux-changes-post-39

## Executive Summary
This feature addresses the gap left by a recent database schema refactor (PR 39) that introduced a 1:N relationship between model assets and their configurations (`LocalModelEntity` -> N `LocalModelConfigurationEntity` and `ApiCredentialsEntity` -> N `ApiModelConfigurationEntity`). The Domain and UI layers are currently out of sync with this new structure, with broken use case imports (the old `ApiModelConfig` class was removed) and flattened UI states (`ApiModelConfigUi`, `ModelConfigurationUi`) that assume a 1:1 mapping between an asset and its preset. This discovery outlines how to update the Domain models, Use Cases, and Settings UI to properly reflect and manage the 1:N hierarchy without cluttering the user experience.

## Module Structure
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/`

## Data Models
**Current State:**
- **Data Layer:** Properly split into `ApiCredentialsEntity` vs `ApiModelConfigurationEntity` and `LocalModelEntity` vs `LocalModelConfigurationEntity`.
- **Domain Layer:** 
  - `ApiCredentials` and `ApiModelConfiguration` exist, but Use Cases like `SaveApiModelUseCase` still attempt to use the deleted `ApiModelConfig` class.
  - `ModelConfiguration` still groups `Metadata`, `Tunings`, and `Persona` into a single object tied to a `ModelType` (slot).
- **UI Layer (`SettingsModels.kt`):**
  - `ApiModelConfigUi` currently merges credentials and tunings into one flat structure.
  - `ModelConfigurationUi` flattens local asset data and tunings into one structure tied to a `ModelType`.

## API Surface
**Repository Interfaces (`ApiModelRepositoryPort`, `ModelRegistryPort`):**
The repository ports are mostly updated to return lists of configs per credential/asset. `ModelRegistryPort` needs slight adjustments to expose the 1:N structure rather than returning flattened models by `ModelType`.

**Domain Use Cases:**
- `SaveApiModelUseCase`, `GetApiModelsUseCase`, and `DeleteApiModelUseCase` currently fail to compile due to missing `ApiModelConfig` references. They must be updated to handle `ApiCredentials` and `ApiModelConfiguration` separately.

## Dependencies
- Room for database entities.
- Hilt for Use Case injection.
- Jetpack Compose for the Settings UI.

## Utility Patterns
We can utilize Kotlin's interface implementation within data classes to create shared tuning properties across Local and API models. 

**Proposed Shared Interface:**
```kotlin
interface ModelTuningConfiguration {
    val id: Long
    val displayName: String
    val temperature: Double
    val topP: Double
    val topK: Int?
    val maxTokens: Int
    val contextWindow: Int
}
```
This allows UI components to render tuning sliders dynamically regardless of whether the configuration belongs to a Local model or an API model.

## Gap Analysis
- **Domain:** Needs `LocalModelAsset` and `LocalModelConfiguration` domain models to replace the monolithic `ModelConfiguration`.
- **Use Cases:** Need rewriting to handle the two-step save/delete process for Assets and their Configurations.
- **UI State:** `SettingsUiState` must be updated to hold lists of Assets, each containing a list of `Configuration` UI models.
- **UI Screens:** 
  - `ByokBottomSheet` and `ByokConfigureScreen` currently edit a flat model. They need to handle editing an Asset (Provider + ID) and its N nested Presets (Tunings).
  - `ModelConfigurationBottomSheet` assumes models are strictly tied to slots (FAST, THINKING, etc.). It needs to map slots (`DefaultModelAssignment`) to a specific config ID belonging to an Asset.

## Risk Assessment
- **Breaking Changes:** This is a heavy refactor of the Domain and UI layer. The app is currently in a broken compilation state due to the previous Data layer refactor.
- **UI Clutter:** Managing 1:N in a single screen can be overwhelming. We need a clear UX strategy (e.g., Expandable Asset Cards containing rows of Presets, with "Add Preset" buttons inside the expanded area).

## Recommendation
**Proceed.** The scope is contained within the Domain config models, BYOK Use Cases, and Settings UI. It directly fulfills the original feature request. No architectural rules from Pillar I are violated. We should hand off to the SPEC phase to define the exact UI layout (e.g., nested lists vs tabs) and the precise signatures for the updated Domain models.