# Test Specification — 42-ux-changes-post-39

## Behavioral Scenarios

### Scenario 1: Displaying API Models in BYOK List
**Given** the user navigates to the BYOK Settings Sheet
**And** there are saved `ApiModelAsset`s populated with nested `ApiModelConfiguration`s
**When** the list is rendered in the UI
**Then** an `ApiModelAssetUi` card displays for each top-level credential
**And** clicking to expand a card displays its associated `ApiModelConfigUi` child presets

### Scenario 2: Selecting Editing Modes for API Assets
**Given** the user is viewing the BYOK Settings Sheet
**When** the user taps the "Edit Credential" icon on an `ApiModelAssetUi` card
**Then** the `SettingsUiState` sets `selectedApiModelAsset` to the targeted asset and transitions the configuration screen into Credential Mode
**When** the user taps the "Edit Preset" icon on a nested `ApiModelConfigUi` row
**Then** the `SettingsUiState` sets `selectedApiModelConfig` to the targeted preset and transitions the configuration screen into Preset Mode

### Scenario 3: Saving a new API Configuration Preset
**Given** the user is on the BYOK Configuration Screen in Preset Mode mapped to an existing asset
**When** the user inputs valid tuning parameters (e.g., Temperature = 0.8) and saves
**Then** `SaveApiModelConfigurationUseCase` is invoked exactly once with the updated `ApiModelConfiguration` data
**And** the preset persists correctly without duplicating or deleting the parent credential

### Scenario 4: Deleting an API Credential triggers Cascade
**Given** the user is viewing an `ApiModelAssetUi` card
**When** the user taps "Delete Credential" and confirms
**Then** `DeleteApiCredentialsUseCase` is invoked
**And** all nested `ApiModelConfiguration` items tethered to that credential instantly clear from the `SettingsUiState`

### Scenario 5: Managing Hybrid Default Model Assignments
**Given** the user interacts with the Model Configuration Sheet
**And** the "Default Assignments" section exposes the standardized slots (`FAST`, `THINKING`, `VISION`, `MAIN`)
**When** the user assigns a slot to a specific `LocalModelConfigUi`
**Then** `SetDefaultModelUseCase` maps the slot directly to the explicit `localConfigId`
**And** the `SettingsUiState` propagates the newly committed assignment globally

## Error Paths

### Error 1: Saving a Configuration without a valid Parent Asset
**Given** the system is parsing an incoming `ApiModelConfiguration` save request
**When** the associated `apiCredentialsId` fails to match an existing primary key in the Database
**Then** `SaveApiModelConfigurationUseCase` halts and yields a `Result.failure` typed as an `IllegalArgumentException`

### Error 2: Attempting to edit a deleted Preset
**Given** the user actively engages the BYOK Configuration screen targeting a specific preset
**When** an external sequence deletes that preset, removing it from existence
**Then** saving the stale state triggers a `Result.failure` internally typed as an `IllegalStateException`
**And** the UI emits a standard Snackbar explaining the target was not found
