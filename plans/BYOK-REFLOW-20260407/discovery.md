# Discovery: BYOK-REFLOW-20260407

## 1. Goal Summary
Redesign the BYOK provider/preset save flow so it is simple, reliable, and testable. Current bugs show that creating a new API model while reusing an existing API key can mutate the wrong provider row, save presets onto the wrong asset, and splice persisted state into draft state. The user wants a design where Room-backed persisted state remains the source of truth, new inserts simply create new records, existing edits update existing records, reusable keys are straightforward, duplicates are allowed, and the ViewModel stops conflating draft/editor state with persisted list state. Provider/model/baseUrl duplicates must be allowed; uniqueness is not required there. If schema changes are needed, make them part of the design instead of patching around the current behavior.

## 2. Target Module Index

### Existing Data Models
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsModels.kt`
  Owns the BYOK-facing UI state types: `SettingsUiState`, `ApiModelAssetUi`, `ApiModelConfigUi`, `ReusableApiCredentialUi`, and related sheet/dialog models. This is the current boundary where persisted list data and editor-facing DTOs meet.
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt`
  Defines `TransientState` and `ApiModelDiscoveryScope`, and currently recombines Room-backed flows with in-progress BYOK edits before exposing `SettingsUiState`.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/ApiCredentials.kt`
  Canonical domain model for a persisted BYOK provider row.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/ApiModelConfiguration.kt`
  Canonical domain model for a persisted BYOK preset row.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/ApiModelAsset.kt`
  Aggregate domain model that pairs one `ApiCredentials` row with its child `ApiModelConfiguration` rows for UI consumption.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/DefaultModelAssignment.kt`
  Represents slot-to-config assignments that must remain valid when credentials or presets are edited/deleted.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ApiProvider.kt`
  Defines provider identity and provider-specific behavior used across persistence, discovery, and UI.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/DiscoveredApiModel.kt`
  Represents remote catalog metadata that the editor can blend into draft configuration defaults.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsEntity.kt`
  Room entity for `api_credentials`; currently enforces unique `(provider, model_id, base_url)` and unique `credential_alias`.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiModelConfigurationEntity.kt`
  Room entity for `api_model_configurations`; currently enforces unique `(api_credentials_id, display_name)` and cascades on parent credential delete.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabase.kt`
  Registers the BYOK tables in the Room schema and is the entry point for any schema/index change.

### Dependencies & API Contracts
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/ApiModelRepositoryPort.kt`
  Persistence contract for observing, saving, deleting, and loading BYOK credentials/configurations.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/GetApiModelAssetsUseCase.kt`
  Combines credential and configuration flows into persisted `ApiModelAsset` snapshots consumed by the settings UI.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/SaveApiCredentialsUseCase.kt`
  Save contract for provider rows; currently just forwards to repository save behavior.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/SaveApiModelConfigurationUseCase.kt`
  Save contract for presets; validates that the parent credential exists, then persists the configuration.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/DeleteApiCredentialsUseCase.kt`
  Handles credential deletion, last-model protection, and default-slot reassignment checks.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/DeleteApiModelConfigurationUseCase.kt`
  Handles preset deletion and reassignment checks for default slots.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/GetDefaultModelsUseCase.kt`
  Supplies the persisted default-slot assignments that the settings UI displays and mutates.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/FetchApiProviderModelsUseCase.kt`
  Remote catalog discovery contract; fetches candidate models using either a typed API key or a stored credential alias.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/FetchApiProviderModelDetailUseCase.kt`
  Fetches provider-specific model detail used to normalize draft defaults.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsDao.kt`
  DAO for loading and upserting `api_credentials`, including identity and alias lookups.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiModelConfigurationsDao.kt`
  DAO for loading and upserting `api_model_configurations`.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImpl.kt`
  Concrete save/delete implementation where Room upserts, alias-based key copying, and parent/child mapping converge.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManager.kt`
  Encrypted key store keyed by `credentialAlias`, not by credential row id.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyProviderImpl.kt`
  Domain-facing adapter that resolves API keys by alias for inference/catalog fetches.
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/navigation/SettingsNavigation.kt`
  Navigation contract for the BYOK list, configure form, custom headers screen, and model assignment flow.

### Utility/Shared Classes
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt`
  Contains BYOK-specific helper logic such as `generateUniqueAlias`, `generateUniqueApiPresetName`, `buildReassignmentOptions`, discovery-scope matching, and provider-default normalization.
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokBottomSheet.kt`
  Defines `ByokSheetView` and delete-target state used to route list/config/reassignment UI.
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ModelConfigurationScreen.kt`
  Defines `AssignmentSelectionView`, which depends on persisted config identity staying stable.
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureComponents.kt`
  Shared configuration-form helpers such as reasoning/routing UI.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/mapper/ApiModelMapper.kt`
  Serializes and deserializes custom headers for persisted API presets.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/RoomTransactionProvider.kt`
  Shared transaction wrapper used by deletion flows and any multi-write persistence redesign.

### Impact Radius
- Feature settings UI/state:
  `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt`,
  `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsModels.kt`,
  `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokBottomSheet.kt`,
  `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureScreen.kt`,
  `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokCustomHeadersScreen.kt`,
  `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsRoute.kt`,
  `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsScreen.kt`,
  `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/navigation/SettingsNavigation.kt`.
- Domain BYOK contracts:
  `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/ApiModelRepositoryPort.kt`,
  `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/SaveApiCredentialsUseCase.kt`,
  `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/SaveApiModelConfigurationUseCase.kt`,
  `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/GetApiModelAssetsUseCase.kt`,
  `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/DeleteApiCredentialsUseCase.kt`,
  `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/DeleteApiModelConfigurationUseCase.kt`.
- Data/schema layer:
  `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsEntity.kt`,
  `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiModelConfigurationEntity.kt`,
  `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsDao.kt`,
  `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiModelConfigurationsDao.kt`,
  `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabase.kt`,
  `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/DataModule.kt`,
  `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImpl.kt`,
  `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManager.kt`,
  `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyProviderImpl.kt`.
- Verification surface:
  `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsDaoTest.kt`,
  `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiModelConfigurationsDaoTest.kt`,
  `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabaseMigrationTest.kt`,
  `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImplTest.kt`,
  `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModelTest.kt`,
  `core/data/schemas/com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase/1.json`.

## 3. Cross-Probe Analysis

### Overlaps Identified
- All three probes identify persisted Room-backed BYOK rows as the intended system of record. The unstable behavior is not caused by a missing persistence layer; it is caused by the UI/editor flow mutating or re-targeting persisted rows without a clean separation between draft intent and stored identity.
- The feature-settings and data probes both point to `SettingsViewModel.kt` plus `ApiModelRepositoryImpl.kt` as the save-flow choke points. The view model decides whether a save is “new” vs. “existing”, while the repository/Room layer decides what row actually survives the write.
- The domain and data probes agree that `GetApiModelAssetsUseCase` already gives the UI a clean persisted snapshot from independent credential/configuration flows. That means draft/editor state can be separated architecturally without inventing a second persisted source of truth.
- The feature-settings and data probes both show reusable-key flow is alias-driven. The UI selects a reusable credential by alias, and the data layer copies or reads secrets by alias through `ApiKeyManager`, so alias identity is part of the persistence contract today.
- The feature-settings and domain probes both show that credential save currently chains into preset creation for new assets. That coupling is central to the current “save provider row, then auto-create preset on whatever row came back” behavior.

### Gaps & Uncertainties
- The probes establish that provider/model/baseUrl duplicates must be supported, but they do not define the final persisted identity model for secrets. The spec must make this explicit rather than continuing to let alias, Room indexes, and UI state each imply identity differently.
- The probes confirm current preset-name uniqueness in `ApiModelConfigurationEntity`, but they do not prove whether that uniqueness should remain. This is adjacent to the wrong-asset bug and must be decided intentionally in spec, not inherited accidentally from the existing index.
- No probe described a dedicated editor-state model for BYOK flows. The current view model only has `selectedApiModelAsset`, `selectedApiModelConfig`, reusable-alias fields, and `_currentApiKey`, so spec must define how “new provider”, “edit provider”, “new preset”, and “edit preset” are represented without reading intent back out of persisted list rows.
- Schema migration is definitely in scope because `PocketCrewDatabase` is still version `1` and destructive migration is disabled in `DataModule.kt`. The probes identified the schema pressure points but did not spell out the migration/test update plan.
- Validation outside the raw probe appendix shows current tests already codify the old behavior, especially:
  `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsDaoTest.kt` expects duplicate credential identity to collapse to one row, and
  `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModelTest.kt` expects a “new” save that resolves to an existing asset to create a fresh preset on that existing row.
  These are not blockers, but they expand the required verification surface for the next phase.

### Conflicts (if any)
- Conflict: the BYOK domain probe says duplicate credential saves are effectively allowed because `SaveApiCredentialsUseCase` does not deduplicate, while the BYOK data probe shows the actual Room schema forbids duplicate `(provider, model_id, base_url)` rows and unique aliases. Resolution: the data-layer constraints are the operative behavior today and are the concrete root cause blocking “new insert means new row”.
- Conflict: the feature-settings probe frames reusable-key flow as straightforward alias reuse from the UI, but the data probe shows alias is also the secret-storage key in `ApiKeyManager`. Resolution: “reuse existing key” is not just a UI convenience; it is coupled to persistence identity and must be redesigned alongside schema/save semantics.
- Conflict: the feature-settings probe describes editor state as transient, but the combine logic in `SettingsViewModel.kt` rehydrates selected persisted assets/configurations back into that transient state on every emission. Resolution: the current code has transient containers, but not a true draft boundary.

## 4. High-Impact Clarifying Questions
*None identified. Proceeding to Spec phase.*

## 5. Probe Coverage Summary
| Layer/Directory | Probe Agent | Key Findings |
|----------------|------------|-------------|
| `feature/settings` | Feature Settings Layer probe | `SettingsViewModel.kt` is the orchestration point where persisted BYOK assets are merged with transient editor state, reusable-key selection, discovery metadata, and save/delete callbacks. |
| `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok` | BYOK Domain Layer probe | Domain save/usecase contracts treat persisted credential/configuration flows as canonical and already support explicit save/delete/reassignment operations without requiring uniqueness at the use-case boundary. |
| `core/data` | BYOK Data Layer probe | Current Room schema and alias-keyed secret storage enforce identity in ways that collapse duplicate provider rows, redirect writes, and make wrong-row mutation possible when UI intent is ambiguous. |

---

## Raw Probe Reports (for Synthesis)

```
# Probe Report: Feature Settings Layer

## Layer: Feature Settings Layer
## Directory: /home/sean/Code/pocket-crew/feature/settings

### Existing Data Models
- `src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsModels.kt`: `StoredMemory`, `SettingsUiState`, `ReassignmentOptionUi`, `ApiModelAssetUi`, `ReusableApiCredentialUi`, `CustomHeaderUi`, `ApiModelConfigUi`, `DiscoveredApiModelUi`, `LocalModelAssetUi`, `LocalModelConfigUi`, `LocalModelMetadataUi`, `DefaultModelAssignmentUi`, plus `MockSettingsData` — bundles UI-visible state for the Settings screen (persistent preferences, BYOK/local-model lists, assignment dialogs) and typed wrappers for API/local models, presets, headers, and mock data used in previews.
- `src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt`: `TransientState`, `ApiModelDiscoveryScope` — captures draft UI state (selected assets/configs, discovery results, transient flags) and discovery scoping helpers that merge persisted database rows with draft edits before exposing `SettingsUiState`.
- `src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokBottomSheet.kt`: `ByokSheetView`, `ByokDeleteTarget` — sealed hierarchies that drive which BYOK sheet to render (asset list, config list, reassignment form) and which provider/preset is pending deletion.
- `src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/LocalModelsBottomSheet.kt`: `LocalModelsSheetView`, `LocalDeleteTarget` — analogous sealed hierarchies for local-model sheet states and deletion targets.
- `src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ModelConfigurationScreen.kt`: `AssignmentSelectionView` (with `AssetList`, `LocalConfigList`, `ApiConfigList`) — controls the tabbed view for assigning models to slots inside the local/model configuration modal.
- `src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureComponents.kt`: `ReasoningOptionUi` — represents a single reasoning-effort option derived from `ApiReasoningPolicy` used by the BYOK configuration forms.

### Dependencies & API Contracts
- `GetSettingsUseCase`, `UpdateThemeUseCase`, `UpdateHapticPressUseCase`, `UpdateHapticResponseUseCase`, `UpdateCustomizationEnabledUseCase`, `UpdateSelectedPromptOptionUseCase`, `UpdateCustomPromptTextUseCase`, `UpdateAllowMemoriesUseCase` (domain/settings) — provide the persisted preference contracts that `SettingsViewModel` observes and updates for the system/customization flow.
- `GetLocalModelAssetsUseCase`, `SaveLocalModelConfigurationUseCase`, `DeleteLocalModelConfigurationUseCase`, `DeleteLocalModelMetadataUseCase`, `DeleteLocalModelUseCase` (domain/modelconfig) — expose Room-backed contracts that feed the local-model sheets and that the ViewModel calls when saving/deleting local configurations.
- `GetApiModelAssetsUseCase`, `FetchApiProviderModelsUseCase`, `FetchApiProviderModelDetailUseCase`, `SaveApiCredentialsUseCase`, `DeleteApiCredentialsUseCase`, `SaveApiModelConfigurationUseCase`, `DeleteApiModelConfigurationUseCase` (domain/byok) — BYOK persistence contracts used to load credentialed providers, fetch remote catalog data, insert/update credentials/configurations, and delete rows.
- `GetDefaultModelsUseCase`, `SetDefaultModelUseCase` (domain/byok) — default assignment contract that ties provider/configuration rows to the synthesis pipeline slots.
- `JumpFreeModalBottomSheet` (`core.ui.component.sheet`), `PersistentTooltip` (`core.ui.component`), `PocketCrewTheme` (`core.ui.theme`) — shared UI components leveraged repeatedly by the BYOK/local-model bottom sheets and settings forms.
- `SettingsDestination`/`settingsGraph` (under `navigation/SettingsNavigation.kt`) — Compose Navigation contract that wires `SettingsRoute`, `ByokConfigureRoute`, `LocalModelConfigureRoute`, `ByokCustomHeadersRoute`, and `ModelConfigurationRoute` into the app’s nav graph with specific animations and argument handling.

### Utility/Shared Classes
- `SettingsViewModel.generateUniqueAlias`, `generateUniqueApiPresetName`, `buildReassignmentOptions`, `maybeFetchSelectedXaiModelMetadata`, `shouldAdoptSuggestedMaxTokens`, `LocalModelAsset.toUi`/`ApiModelAsset.toUi` helpers — utility logic inside `SettingsViewModel` that deduplicates aliases/preset names, builds model reassignment candidates, enriches configs with discovery metadata, and shapes domain assets for the UI.
- `SettingsModels.MockSettingsData` — preview data provider used by Compose previews across the layer.
- `OpenRouterRoutingCard`, `OpenRouterRoutingSection`, `RoutingSwitchRow` (in `ByokConfigureComponents.kt`) plus `ReasoningOptionUi` — shared UI helpers for the OpenRouter routing and reasoning-effort panel that are reused by the BYOK configuration screen.
- `ByokBottomSheet.ByokSheetView`, `LocalModelsBottomSheet.LocalModelsSheetView`, `ModelConfigurationScreen.AssignmentSelectionView` — sealed view-state helpers that coordinate which sheet content (asset list, config list, reassignment) to show without scattering that logic through the composables.

### Impact Observations
- `SettingsViewModel.kt` is the central site for BYOK/local-model state merging, saving, deletion, discovery, and default-assignment logic; redesigning the BYOK save flow will require revisiting the transient-selection handling (`selectedApiModelAsset`, `selectedApiModelConfig`, `_currentApiKey`, discovery scope) and the `onSaveApiCredentials`/`onSaveApiModelConfig` paths to ensure persisted rows stay immutable except when explicitly edited.
- `SettingsModels.kt` defines every UI-bound DTO/preset value object for API credentials, configurations, headers, and assignment flow; changes to how new records vs. edits are represented (e.g., distinguishing “reuse existing credential” vs. “draft new asset”) will require adjustments here plus any preview data consumed by Previews.
- Bottom-sheet composables (`ByokBottomSheet.kt`, `LocalModelsBottomSheet.kt`, `ModelConfigurationScreen.kt`) embed the navigation points into BYOK/local model edit flows (e.g., `onNavigateToByokConfigure`), so rerouting or splitting draft vs. persisted state will affect these UI density and deletion/reassignment confirmations.
- `settingsGraph` (navigation) and `SettingsRoute/SettingsScreen` orchestrate callbacks (`onShowByokSheet`, `onSelectApiModelAsset`, `onNavigateToByokConfigure`, etc.); ensuring the new BYOK save flow exposes the required hooks (e.g., new “commit draft” vs. “apply existing”) may require signature tweaks in these routes and the `SettingsViewModel` consumers.
- Platform integration points like `ByokConfigureScreen.kt` and `ByokCustomHeadersScreen.kt` consume `SettingsViewModel` callbacks (`onApiModelAssetFieldChange`, `onApiKeyChange`, `onAddCustomHeader`, etc.); any change to the contract for saving or editing credentials/configurations should be reflected there so the UI can distinguish when it can mutate persisted rows versus working with drafts.

### Integration Points
- Upstream domain layer (`domain/usecase/byok`, `domain/usecase/modelconfig`, `domain/usecase/settings`) — this feature reads/writes Room-backed credentials/configurations and must keep the `Save*`/`Delete*` use cases’ contracts honored (e.g., `saveApiCredentialsUseCase` returning credential ID, `deleteApiModelConfigurationUseCase.getModelTypesNeedingReassignment()`).
- `core.ui` tooling (`JumpFreeModalBottomSheet`, `PersistentTooltip`, theme system) — consistent bottom-sheet presentation and tooltips are reused across BYOK/local sheets; the redesign must maintain those shared affordances.
- Compose navigation stack (`navigation/SettingsNavigation.kt`) — `settingsGraph` wires this layer into the app-wide navigation; the BYOK redesign touches multiple destinations (`BYOK_CONFIGURE`, `BYOK_CUSTOM_HEADERS`, `MODEL_CONFIGURE`), so any new screens or flows must register in the same graph or new routes.
- Settings parent screen (`SettingsRoute.kt`/`SettingsScreen.kt`) — exposes callbacks (model selection, deletion, sheet toggles) routed into `SettingsViewModel`; new BYOK behavior (e.g., separate “draft alias” state) must be surfaced through these callbacks so the `ByokConfigure` flow receives the correct context.
- Domain model definitions (`ApiProvider`, `ApiModelAsset`, `ApiModelConfiguration`, `ModelType`, `ModelSource`, etc.) are consumed heavily by this layer; preserving their expectations (IDs, alias uniqueness, provider enums) is critical when ensuring persisted Room rows remain authoritative.

## Probe Report — BYOK Domain Layer (Ticket BYOK-REFLOW-20260407)

### Directory: `/core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok`

#### 1. Existing Data Models
- `ApiCredentials` and `ApiModelConfiguration` represent persisted credentials/configurations managed by `ApiModelRepositoryPort` (used in `SaveApiCredentialsUseCase.kt` and `SaveApiModelConfigurationUseCase.kt`). These models carry metadata for provider credentials, API models, and are the canonical Room-backed rows the new flow should treat as source of truth (`SaveApiCredentialsUseCase.kt:1`, `SaveApiModelConfigurationUseCase.kt:1`).
- `ApiModelAsset` aggregates a single credential plus its configurations for UI consumption, produced by combining the credential/config streams (`GetApiModelAssetsUseCase.kt:1`).
- `DefaultModelAssignment` and `ModelType` capture the declared default model for each inference type; they are surfaced through `GetDefaultModelsUseCase` and updated via `SetDefaultModelUseCase` (`GetDefaultModelsUseCase.kt:1`, `SetDefaultModelUseCase.kt:1`).
- `DiscoveredApiModel` and `ApiProvider` describe remote provider model metadata fetched from catalogs; used by `FetchApiProviderModelsUseCase` and `FetchApiProviderModelDetailUseCase` to populate model lists and details (`FetchApiProviderModelsUseCase.kt:1`, `FetchApiProviderModelDetailUseCase.kt:1`).

#### 2. Dependencies & API Contracts
- Repository ports: `ApiModelRepositoryPort` (credentials, configurations, observe/delete/save operations), `DefaultModelRepositoryPort` (defaults, observe/set), `LocalModelRepositoryPort` (local assets), and `TransactionProvider` coordinate consistency around credential deletion (`DeleteApiCredentialsUseCase.kt:1`).
- External integrations: `ApiModelCatalogPort` and `ApiKeyProviderPort` supply remote model discovery/caching and secret resolution for fetching provider models (`FetchApiProviderModelsUseCase.kt:1`, `FetchApiProviderModelDetailUseCase.kt:1`).
- Contracts assume `ApiModelRepositoryPort` exposes flows for credentials/configurations and synchronous retrieval by ID for validation before saves; callers rely on synchronous transactions (e.g., checking last model, reassigning defaults) to prevent invalid states (`DeleteApiCredentialsUseCase.kt:1`).
- View layer dependencies consume Flow-based outputs (`observeAllCredentials()`, `observeAllConfigurations()`, `observeDefaults()`) to derive UI-ready assets and default assignments (`GetApiModelAssetsUseCase.kt:1`, `GetDefaultModelsUseCase.kt:1`).

#### 3. Utility/Shared Classes
- Flow combinators: `GetApiModelAssetsUseCaseImpl` merges credential and configuration flows into `ApiModelAsset` records, ensuring each asset bundles credential metadata with its configurations for downstream screens (`GetApiModelAssetsUseCase.kt:1`).
- Credential-management helpers: `SaveApiCredentialsUseCaseImpl` delegates directly to repository save logic, while `SaveApiModelConfigurationUseCaseImpl` enforces parent credential existence before saving to avoid orphan configs (`SaveApiCredentialsUseCase.kt:1`, `SaveApiModelConfigurationUseCase.kt:1`).
- Fetch helpers resolve API keys by preferring explicit key, then credential alias, ensuring remote requests always have non-empty credentials (`FetchApiProviderModelsUseCase.kt:1`, `FetchApiProviderModelDetailUseCase.kt:1`).

#### 4. Impact Observations
- Deleting credentials triggers reassignment of defaults and enforces “not last model” constraint, indicating the domain currently forbids removing the final API configuration while requiring clients to supply fallback defaults when deleting a model that was referenced as default (`DeleteApiCredentialsUseCase.kt:1`).
- Credential save flows simply proxy to repository without deduplication, so duplicates are permitted by design; the refinements should ensure new inserts always create new rows and updates target the correct credential ID, aligning with the raw idea’s emphasis on Room as source of truth (`SaveApiCredentialsUseCase.kt:1`).
- Model configuration saves validate the presence of parent credentials and wrap repository invocation in `Result.runCatching`, meaning failures bubble as exceptions but are captured, which may help callers discriminate between new inserts vs. updates (`SaveApiModelConfigurationUseCase.kt:1`).
- Asset flow and defaults exposure rely entirely on repository-provided flows; any ViewModel changes must keep these streams separate from draft/editor state to prevent “splice persisted state into draft state,” as the flows already represent persisted snapshots (`GetApiModelAssetsUseCase.kt:1`, `GetDefaultModelsUseCase.kt:1`).

# Probe Report — BYOK Data Layer

## Existing Data Models
- `ApiCredentialsEntity` maps every saved provider/credential into `api_credentials`, tracking `displayName`, `ApiProvider`, `baseUrl`, `modelId`, `isVision`, unique `credentialAlias`, and timestamps; Room enforces unique indexes on `(provider, model_id, base_url)` plus `credential_alias`, so writes can update by identity or alias rather than inserting duplicates (`core/data/.../ApiCredentialsEntity.kt:9-43`).
- `ApiModelConfigurationEntity` stores preset-specific tuning values, custom headers, and OpenRouter flags per credential, linking via a foreign key to `api_credentials` with cascade delete and a unique `(api_credentials_id, display_name)` index to prevent duplicate preset names for the same API credential (`core/data/.../ApiModelConfigurationEntity.kt:9-82`).
- `ApiModelRepositoryImpl` converts the entities back into domain models (`ApiCredentials` plus `ApiModelConfiguration`) with OpenRouter routing metadata and serialized custom headers, keeping the domain layer in sync with the persisted tables (`core/data/.../ApiModelRepositoryImpl.kt:127-159`).

## Dependencies & API Contracts
- `ApiCredentialsDao` exposes `Flow`/suspend readers ordered by `updated_at`, identity lookups, and an `@Upsert`/delete pathway so the repository can observe and mutate persisted credentials atomically (`core/data/.../ApiCredentialsDao.kt:9-45`).
- `ApiModelConfigurationsDao` mirrors that pattern for preset rows, exposing observe/get/upsert/delete APIs and credential-scoped bulk deletes (`core/data/.../ApiModelConfigurationsDao.kt:9-26`).
- `ApiModelRepositoryImpl` implements `ApiModelRepositoryPort`, wiring the DAOs, `ApiKeyManager`, and `ApiModelMapper` to observe all credentials/configurations, persist new or edited items via `@Upsert`, copy API keys from a `sourceCredentialAlias`, and delete both the database row and the encrypted key (`core/data/.../ApiModelRepositoryImpl.kt:21-125`).
- `ApiModelCatalogRepositoryImpl` implements `ApiModelCatalogPort`, fetching discovery lists for OpenAI, Anthropic, Google, OpenRouter, and xAI by delegating to provider clients plus an OkHttp fallback for OpenRouter/XAI, ensuring the catalog layer depends on this data directory for remote model metadata (`core/data/.../ApiModelCatalogRepositoryImpl.kt:1-194`).
- `ApiKeyManager` persists keys in `EncryptedSharedPreferences` keyed by alias, exposing `save`, `get`, and `delete`, and `ApiKeyProviderImpl` simply forwards reads to that manager so the domain port can rehydrate keys without knowing about Android security APIs (`core/data/.../ApiKeyManager.kt:11-40`, `core/data/.../ApiKeyProviderImpl.kt:1-13`).
- `DataModule` and `DataRepositoryModule` provide the Room DAOs and bind `ApiModelRepositoryImpl`, `ApiModelCatalogRepositoryImpl`, and `ApiKeyProviderImpl` to their respective domain ports, so the rest of the app consumes these implementations via interfaces (`core/data/DataModule.kt:19-223`).
- `RoomTransactionProvider` wraps `PocketCrewDatabase.withTransaction` and fulfills `TransactionProvider`, so higher layers can compose BYOK operations inside consistent transactions (`core/data/.../RoomTransactionProvider.kt:1-35`).

## Utility/Shared Classes
- `ApiModelMapper` serializes/deserializes custom headers into JSON strings so the DAO stays schema-stable while the domain model deals with maps (`core/data/mapper/ApiModelMapper.kt:6-30`).
- `ModelConfigProviderImpl` hides Android `Context` details and just exposes the `modelsDirectory` file path requested by domain download/config helpers (`core/data/repository/ModelConfigProviderImpl.kt:11-27`).
- `FormatUtils.formatBytes` and `FtsSanitizer.sanitize` are repository-level helpers for turning byte counts into readable strings and sanitizing search queries for SQLite FTS respectively, ensuring shared formatting and search logic stays centralized (`core/data/util/FormatUtils.kt:5-11`, `core/data/util/FtsSanitizer.kt:3-35`).

## Impact Observations
- Because `api_credentials` has unique indexes on `(provider, model_id, base_url)` and `credential_alias` while `ApiModelRepositoryImpl.saveCredentials` always calls `apiCredentialsDao.upsert` with the incoming credential data, any new alias that shares the same provider/model/base URL will trigger an update on the existing row rather than insert a distinct provider record, which matches the observed bug where reusing an API key mutates another provider (`.../ApiCredentialsEntity.kt:9-43`, `.../ApiModelRepositoryImpl.kt:44-78`).
- API keys remain tied to alias strings in `ApiKeyManager`, and `saveCredentials` only copies keys from a `sourceCredentialAlias` when provided; if an alias is reused or the wrong row is updated during the upsert, the encrypted key write/read pair will either overwrite the wrong alias or leave the new row without its intended key, exacerbating the wrong-row mutations (`.../ApiKeyManager.kt:11-40`, `.../ApiModelRepositoryImpl.kt:65-85`).
- Model configurations rely on the cascade foreign key plus a unique `(api_credentials_id, display_name)` index and are always persisted through `@Upsert`, so editing a preset simply rewrites the persisted row rather than producing a draft version, meaning any mis-targeted edit will corrupt the persisted state for that `api_credentials_id`/display_name pair (`.../ApiModelConfigurationEntity.kt:9-82`, `.../ApiModelRepositoryImpl.kt:94-125`).
- The UI currently observes credentials/configurations via the DAO flows ordered by `updated_at`, so there is no separate draft or editor list—what the view model touches is the same Room row that feeds the list, making it hard to keep draft/editor state decoupled from persisted state without a new layer between the DAO and the UI (`.../ApiCredentialsDao.kt:9-45`, `.../ApiModelConfigurationsDao.kt:9-26`).
```
