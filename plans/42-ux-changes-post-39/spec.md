# Implementation Plan — 42-ux-changes-post-39

## Objective
Refactor the Domain models, Use Cases, and Settings UI to properly reflect and manage the new 1:N database hierarchy for model configurations. Introduce a shared `ModelTuningConfiguration` interface, split API and Local models into Asset (1) and Configuration (N) entities, and update the UI to an Expandable Card pattern that cleanly nests presets under their parent assets.

## Acceptance Criteria
- [ ] `ModelTuningConfiguration` interface is created and implemented by both API and Local configurations.
- [ ] `LocalModelAsset` and `LocalModelConfiguration` domain models replace the monolithic `ModelConfiguration`.
- [ ] `ApiModelAsset` encapsulates `ApiCredentials` and a list of `ApiModelConfiguration`.
- [ ] BYOK and Local Model Use Cases are split to handle Credentials/Assets and Configurations independently.
- [ ] `SettingsUiState` is updated to reflect the M:1 nesting (`ApiModelAssetUi` and `LocalModelAssetUi`).
- [ ] `ByokBottomSheet` and `ModelConfigurationScreen` render assets as expandable cards with nested presets.
- [ ] `ByokConfigureScreen` handles creating/editing either Credentials or Configurations based on the current selection context.

## Architecture

### Files to Create
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/ModelTuningConfiguration.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/LocalModelAsset.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/LocalModelMetadata.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/LocalModelConfiguration.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/ApiModelAsset.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/SaveApiCredentialsUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/DeleteApiCredentialsUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/SaveApiModelConfigurationUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/DeleteApiModelConfigurationUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/GetApiModelAssetsUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/modelconfig/SaveLocalModelMetadataUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/modelconfig/DeleteLocalModelMetadataUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/modelconfig/SaveLocalModelConfigurationUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/modelconfig/DeleteLocalModelConfigurationUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/modelconfig/GetLocalModelAssetsUseCase.kt`

### Files to Modify
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/ApiModelConfiguration.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsModels.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokBottomSheet.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureScreen.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ModelConfigurationScreen.kt`

### Files to Delete
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/ModelConfiguration.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/ModelConfigurationUi.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/SaveApiModelUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/GetApiModelsUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/DeleteApiModelUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/modelconfig/GetModelConfigurationsUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/modelconfig/UpdateModelConfigurationUseCase.kt`

## Data Contracts

### New Types
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

data class ApiModelAsset(
    val credentials: ApiCredentials,
    val configurations: List<ApiModelConfiguration>
)

data class LocalModelAsset(
    val metadata: LocalModelMetadata,
    val configurations: List<LocalModelConfiguration>
)

data class LocalModelMetadata(
    val id: Long = 0,
    val huggingFaceModelName: String,
    val remoteFileName: String,
    val localFileName: String,
    val displayName: String,
    val sha256: String,
    val sizeInBytes: Long,
    val modelFileFormat: ModelFileFormat
)

data class LocalModelConfiguration(
    override val id: Long = 0,
    val localModelId: Long,
    override val displayName: String,
    override val maxTokens: Int,
    override val contextWindow: Int,
    override val temperature: Double,
    override val topP: Double,
    override val topK: Int?,
    val minP: Double = 0.0,
    val repetitionPenalty: Double,
    val thinkingEnabled: Boolean = false,
    val systemPrompt: String
) : ModelTuningConfiguration

// --- UI Types Mapping --- //
data class ApiModelAssetUi(
    val credentialsId: Long,
    val displayName: String,
    val provider: ApiProvider,
    val modelId: String,
    val baseUrl: String?,
    val isVision: Boolean,
    val credentialAlias: String,
    val configurations: List<ApiModelConfigUi>
)

data class LocalModelAssetUi(
    val metadataId: Long,
    val displayName: String,
    val huggingFaceModelName: String,
    val remoteFileName: String,
    val sizeInBytes: Long,
    val configurations: List<LocalModelConfigUi>
)
```

### Modified Types
```kotlin
// In ApiModelConfiguration.kt
data class ApiModelConfiguration(
    override val id: Long = 0,
    val apiCredentialsId: Long,
    override val displayName: String,
    override val maxTokens: Int = 4096,
    override val contextWindow: Int = 4096,
    override val temperature: Double = 0.7,
    override val topP: Double = 0.95,
    override val topK: Int? = null,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0,
    val stopSequences: List<String> = emptyList(),
    val customHeadersAndParams: Map<String, String> = emptyMap(),
) : ModelTuningConfiguration

// In SettingsModels.kt
data class SettingsUiState(
    // ... existing generic fields ...
    val showModelConfigSheet: Boolean = false,
    val localModels: List<LocalModelAssetUi> = emptyList(),
    val selectedLocalModelAsset: LocalModelAssetUi? = null,
    val selectedLocalModelConfig: LocalModelConfigUi? = null,
    val availableHuggingFaceModels: List<LocalModelMetadataUi> = emptyList(),

    val showByokSheet: Boolean = false,
    val apiModels: List<ApiModelAssetUi> = emptyList(),
    val selectedApiModelAsset: ApiModelAssetUi? = null,
    val selectedApiModelConfig: ApiModelConfigUi? = null,

    val defaultAssignments: List<DefaultModelAssignmentUi> = emptyList()
)

data class ApiModelConfigUi(
    val id: Long = 0,
    val credentialsId: Long = 0,
    val displayName: String = "",
    val maxTokens: String = "4096",
    val contextWindow: String = "4096",
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
    val topK: Int? = 40,
    val thinkingEnabled: Boolean = false
)

data class LocalModelConfigUi(
    val id: Long = 0,
    val localModelId: Long = 0,
    val displayName: String = "",
    val maxTokens: String = "4096",
    val contextWindow: String = "4096",
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
    val topK: Int? = 40,
    val minP: Double = 0.0,
    val repetitionPenalty: Double = 1.1,
    val systemPrompt: String = ""
)
```

## Permissions & Config Delta
None

## Visual Spec
**Expandable Card Approach (BYOK & Model Configuration Screens):**
- **Parent Level:** Standard Material 3 Elevated Card displaying the Asset.
  - Features the display name, provider logo (if API), and underlying Model ID.
  - Contains an Edit Icon (to modify base credentials/metadata) and a Delete Icon.
- **Child Level:** Tapping the card expands an internal list of Presets (`ModelTuningConfiguration` items).
  - Each preset row shows its `displayName` and summarized core tunings (e.g., "Temp: 0.7 | Tokens: 4096").
  - Each preset row includes its own Edit Icon (to modify tunings) and Delete Icon.
  - A prominent "+ Add Preset" button is pinned at the bottom of the expanded area.

**ByokConfigureScreen Updates:**
- Must pivot to serve two modes effectively using `SettingsUiState`:
  1. **Editing Credentials:** Renders inputs for Provider, Model ID, API Key, Base URL, and Vision capabilities.
  2. **Editing a Preset:** Renders inputs for tuning parameters exclusively (Display Name, Temperature, Top-P, Context Window, etc.) tied to an existing Credential parent.

**ModelConfigurationScreen Updates:**
- Moves away from flat lists and adopts the same Expandable Card architecture (`LocalModelAssetUi`).
- "Default Assignments" section pinned to the top. Tapping a specific inference slot (e.g., `FAST`, `MAIN`) triggers a dialog selection to lock in any Preset configuration originating from either the API or Local asset lists.

## Constitution Audit
- [x] ARCHITECTURE_RULES.md: No Android imports leak into `:domain`. Pure Kotlin types strictly enforced. Layer dependency direction maintained.
- [x] CODE_STYLE_RULES.md: Immutable data classes applied successfully in `SettingsUiState`. Standard UI naming conventions respected.
- [x] DATA_LAYER_RULES.md: Repositories will implement abstract domain interfaces accurately. Database schemas remain untouched as they were already handled in PR 39.
- [x] UI_DESIGN_SPEC.md: M3 Expandable card lists follow Material recommendations without hardcoding raw colors.

## Cross-Spec Dependencies
Depends on the updated database layer delivered via PR 39.

> **Instruction to Implementation Agent:**
> Derive the implementation entirely from the Markdown Spec and Constitution. The test suite is present in the repository. Do not use test assertions to infer expected values or implementation structure — the tests are a post-hoc verifier, not a blueprint.
