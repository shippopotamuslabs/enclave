# Settings ViewModel Refactor Plan

Date: 2026-04-08
Target: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt`

## Goal

Simplify the oversized settings ViewModel by:

- moving cross-entity and business workflow logic into domain use cases
- keeping only presentation coordination and draft state in the ViewModel
- replacing the monolithic `SettingsUiState` with route-sized state objects
- reducing duplicated and dead state/handlers

## Current Problems

- `SettingsViewModel` is simultaneously acting as:
  - settings command dispatcher
  - local model editor
  - BYOK provider editor
  - model discovery orchestrator
  - deletion/reassignment planner
  - default assignment dialog controller
  - domain-to-UI mapper
- `TransientState` is a single bag containing unrelated screen state.
- `SettingsUiState` is flat and mixes:
  - home screen state
  - local model sheet state
  - local preset editor state
  - API provider list state
  - API provider editor state
  - API model discovery state
  - deletion safety state
  - model assignment state
- Some state is duplicated between persisted settings and transient draft state.
- Some handlers are stubbed or appear unused.

## Logical Groupings And Ownership

### Keep In ViewModel

These are presentation concerns and should remain in the ViewModel or be delegated to feature-layer reducers/factories:

- sheet/dialog visibility toggles
- selected local/API asset and selected config draft references
- API key handling in a separate `StateFlow`
- route back/reset behaviors
- forwarding simple one-shot commands to existing use cases

### Move To Feature-Layer Helpers

These do not need to be domain use cases, but they should not live inline in the ViewModel:

- `SettingsUiState` assembly from multiple flows
- domain-to-UI mapping for local assets/configs and API assets/configs
- discovered model filtering/sorting for UI
- UI formatting of local model names and reassignment option labels

Recommended helper names:

- `SettingsUiStateFactory`
- `LocalModelAssetUiMapper`
- `ApiModelAssetUiMapper`
- `ApiDiscoveryUiFilter`
- `ReassignmentOptionUiMapper`

### Move To Domain Use Cases

These groupings represent business workflows or cross-repository orchestration and should move to the domain layer.

1. Opening the local models sheet and loading soft-deleted models
   - Current area: `onShowModelConfigSheet`
   - Proposed use case: `GetRestorableLocalModelsUseCase`

2. Resolving a model role assignment into the selected asset/config
   - Current area: `onSelectModelType`
   - Proposed use case: `ResolveAssignedModelSelectionUseCase`

3. Saving a local model preset
   - Current area: `onSaveLocalModelConfig`
   - Proposed use case: `SaveLocalModelPresetUseCase`
   - Responsibility:
     - validate and normalize numeric fields
     - map UI draft input into `LocalModelConfiguration`
     - persist through existing repository-facing use case/repository

4. Planning local model preset deletion with reassignment
   - Current area: `onDeleteLocalModelConfig`
   - Proposed use case: `PrepareModelDeletionUseCase`
   - Responsibility:
     - determine impacted model roles
     - determine whether vision-compatible replacements are required
     - build replacement candidates
     - return a structured deletion plan

5. Planning local model asset deletion with reassignment
   - Current area: `onDeleteLocalModelAsset`
   - Proposed use case: `PrepareModelDeletionUseCase`
   - Same use case as above with a different input target type

6. Executing deletion after reassignment selection
   - Current area: `onConfirmDeletionWithReassignment`
   - Proposed use case: `ExecuteModelDeletionWithReassignmentUseCase`
   - Responsibility:
     - apply reassignment if needed
     - perform deletion for local asset, local preset, API provider, or API preset

7. Saving API provider credentials and auto-creating the first preset
   - Current area: `onSaveApiCredentials`
   - Proposed use case: `SaveApiProviderDraftUseCase`
   - Responsibility:
     - generate a unique alias
     - save credentials
     - optionally create the initial preset
     - return the persisted asset and created preset if applicable

8. Saving an API preset
   - Current area: `onSaveApiModelConfig`
   - Proposed use case: `SaveApiPresetUseCase`
   - Responsibility:
     - normalize defaults
     - sanitize custom headers
     - normalize OpenRouter-only routing state
     - persist the configuration

9. Discovering API provider models and merging detail metadata
   - Current area: `onFetchApiModels`
   - Proposed use case: `DiscoverApiModelsUseCase`
   - Responsibility:
     - fetch provider models
     - fetch xAI detail fallback when needed
     - merge selected model placeholders/details
     - return discovered models plus discovery scope

10. Applying provider/model metadata defaults to an API preset draft
   - Current area:
     - `defaultReasoningEffort`
     - `withProviderDefaults`
     - `withDiscoveredModelMetadata`
     - `shouldAdoptSuggestedMaxTokens`
     - `suggestedDefaultMaxTokens`
   - Proposed use case: `ApplyApiModelMetadataDefaultsUseCase`

11. Preparing API preset deletion with reassignment
   - Current area: `onDeleteApiModelConfig`
   - Proposed use case: `PrepareModelDeletionUseCase`

12. Preparing API provider deletion with reassignment
   - Current area: `onDeleteApiModelAsset`
   - Proposed use case: `PrepareModelDeletionUseCase`

13. Memory/data cleanup and feedback flows once implemented
   - Current areas:
     - `onDeleteAllConversations`
     - `onDeleteAllMemories`
     - `onDeleteMemory`
     - `onSubmitFeedback`
   - Proposed use cases:
     - `DeleteAllConversationsUseCase`
     - `DeleteAllMemoriesUseCase`
     - `DeleteMemoryUseCase`
     - `SubmitFeedbackUseCase`

## Proposed State Split

Replace the monolithic `SettingsUiState` with a composed root state that contains route-sized slices.

### `SettingsHomeUiState`

Properties:

- `theme: AppTheme`
- `hapticPress: Boolean`
- `hapticResponse: Boolean`
- `isLocalModelsSheetOpen: Boolean`
- `isApiProvidersSheetOpen: Boolean`
- `isDataControlsSheetOpen: Boolean`
- `isMemoriesSheetOpen: Boolean`
- `isFeedbackSheetOpen: Boolean`

### `CustomizationUiState`

Keep only if the customization UI is still a live feature.

Properties:

- `isSheetOpen: Boolean`
- `enabled: Boolean`
- `selectedPromptOption: SystemPromptOption`
- `customPromptText: String`

### `DataControlsUiState`

Properties:

- `isSheetOpen: Boolean`
- `allowMemories: Boolean`

### `MemoriesUiState`

Properties:

- `isSheetOpen: Boolean`
- `memories: List<StoredMemory>`

### `FeedbackUiState`

Properties:

- `isSheetOpen: Boolean`
- `feedbackText: String`

### `LocalModelsSheetUiState`

Properties:

- `isVisible: Boolean`
- `models: List<LocalModelAssetUi>`
- `availableDownloads: List<LocalModelAssetUi>`
- `selectedAsset: LocalModelAssetUi?`

### `LocalModelEditorUiState`

Properties:

- `selectedAsset: LocalModelAssetUi?`
- `configDraft: LocalModelConfigUi?`

Note:

- `availableHuggingFaceModels` should be removed unless a real consumer is added.

### `ApiProvidersSheetUiState`

Properties:

- `isVisible: Boolean`
- `assets: List<ApiModelAssetUi>`
- `selectedAsset: ApiModelAssetUi?`

### `ApiModelDiscoveryUiState`

Properties:

- `models: List<DiscoveredApiModelUi>`
- `filteredModels: List<DiscoveredApiModelUi>`
- `isLoading: Boolean`
- `searchQuery: String`
- `providerFilter: String?`
- `sortOption: ModelSortOption`

### `ApiProviderEditorUiState`

Properties:

- `assetDraft: ApiModelAssetUi?`
- `selectedReusableCredential: ReusableApiCredentialUi?`
- `presetDraft: ApiModelConfigUi?`
- `parameterSupport: ApiModelParameterSupport`
- `discovery: ApiModelDiscoveryUiState`

### `ModelAssignmentsUiState`

Properties:

- `assignments: List<DefaultModelAssignmentUi>`
- `isDialogOpen: Boolean`
- `editingSlot: ModelType?`

### `DeletionFlowUiState`

Properties:

- `showLastModelAlert: Boolean`
- `pendingTarget: PendingDeletionTarget?`
- `modelTypesNeedingReassignment: List<ModelType>`
- `reassignmentOptions: List<ReassignmentOptionUi>`

Use a sealed target instead of multiple nullable ids:

- `PendingDeletionTarget.LocalModelAsset`
- `PendingDeletionTarget.LocalModelPreset`
- `PendingDeletionTarget.ApiProvider`
- `PendingDeletionTarget.ApiPreset`

## ViewModel And UI Touchpoints To Update

### Root Settings Screen

File:

- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsScreen.kt`

Updates:

- replace flat `uiState.theme`, `uiState.hapticPress`, `uiState.hapticResponse` with `uiState.home.*`
- replace `uiState.showByokSheet` with `uiState.home.isApiProvidersSheetOpen`
- replace `uiState.showModelConfigSheet` with `uiState.home.isLocalModelsSheetOpen`

### Local Models Bottom Sheet

File:

- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/LocalModelsBottomSheet.kt`

Updates:

- replace `uiState.localModels` with `uiState.localModelsSheet.models`
- replace `uiState.availableToDownloadModels` with `uiState.localModelsSheet.availableDownloads`
- replace `uiState.selectedLocalModelAsset` with `uiState.localModelsSheet.selectedAsset`
- replace `uiState.showCannotDeleteLastModelAlert` with `uiState.deletion.showLastModelAlert`
- replace `uiState.modelTypesNeedingReassignment` and `uiState.reassignmentOptions` with `uiState.deletion.*`

### Local Model Configure Screen

File:

- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/LocalModelConfigureScreen.kt`

Updates:

- replace `uiState.selectedLocalModelConfig` with `uiState.localModelEditor.configDraft`

### BYOK Bottom Sheet

File:

- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokBottomSheet.kt`

Updates:

- replace `uiState.apiModels` with `uiState.apiProvidersSheet.assets`
- replace `uiState.selectedApiModelAsset` with `uiState.apiProvidersSheet.selectedAsset`
- replace deletion-related flat fields with `uiState.deletion.*`

### BYOK Configure Screen

File:

- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureScreen.kt`

Updates:

- replace `uiState.apiCredentialDraft` with `uiState.apiProviderEditor.assetDraft`
- replace `uiState.selectedApiModelConfig` with `uiState.apiProviderEditor.presetDraft`
- replace `uiState.selectedReusableApiCredential` with `uiState.apiProviderEditor.selectedReusableCredential`
- replace `uiState.selectedApiModelParameterSupport` with `uiState.apiProviderEditor.parameterSupport`
- replace discovery fields with `uiState.apiProviderEditor.discovery.*`

### BYOK Custom Headers Screen

File:

- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokCustomHeadersScreen.kt`

Updates:

- replace `uiState.selectedApiModelConfig?.customHeaders` with `uiState.apiProviderEditor.presetDraft?.customHeaders`

### Model Configuration Screen

File:

- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ModelConfigurationScreen.kt`

Updates:

- replace `uiState.defaultAssignments` with `uiState.assignments.assignments`
- replace `uiState.showAssignmentDialog` with `uiState.assignments.isDialogOpen`
- replace `uiState.editingAssignmentSlot` with `uiState.assignments.editingSlot`
- local/API asset pools can come from:
  - `uiState.localModelsSheet.models`
  - `uiState.apiProvidersSheet.assets`

### Settings ViewModel

File:

- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt`

Updates:

- replace `TransientState` with smaller state holders per feature area
- move inline mapping logic into feature-layer factories/mappers
- move business workflows into the new domain use cases listed above
- consider exposing smaller `StateFlow`s per route instead of forcing every route to collect the full settings state

## Immediate Cleanup Items

These can be removed or addressed first before deeper refactoring:

- remove unused `SavedStateHandle` injection
- remove unused `DeleteLocalModelMetadataUseCase` injection
- verify whether the following handlers are still used:
  - `onDeleteApiCredentials`
  - `onSaveModelConfig`
  - `onHuggingFaceModelNameChange`
  - `onTemperatureChange`
  - `onTopKChange`
  - `onTopPChange`
  - `onMaxTokensChange`
  - `onContextWindowChange`
- decide whether customization/data/memories/feedback are live features or dead code paths

## Recommended Refactor Order

1. Remove unused injections and dead handlers.
2. Extract feature-layer mappers/factories from the ViewModel combine block.
3. Introduce split state classes without changing behavior.
4. Migrate deletion planning/execution into domain use cases.
5. Migrate API credential save/preset save/model discovery workflows into domain use cases.
6. Migrate local preset save/assignment resolution into domain use cases.
7. If those screens are still active, finish the memories/feedback/data-control flows with real use cases.

## Expected Outcome

After this refactor:

- the ViewModel becomes a presentation coordinator instead of a workflow container
- route state becomes readable and localized
- business rules become testable in domain-layer use cases
- UI files become easier to reason about because each screen reads only the state slice it needs
- deletion, assignment, and provider discovery logic become reusable and easier to validate
