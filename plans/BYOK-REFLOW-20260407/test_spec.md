# Test Specification: BYOK-REFLOW-20260407

## 1. Happy Path Scenarios

### Scenario: Create duplicate provider row while reusing an existing key
- **Given:** A saved provider row already exists for `(provider=OPENAI, modelId="gpt-4.1", baseUrl="https://api.openai.com/v1")`
- **And:** The user starts a new provider draft with the same provider/model/baseUrl values
- **And:** The user selects an existing reusable credential alias instead of typing a new key
- **When:** The user saves the provider draft
- **Then:** A new `api_credentials` row is inserted with a different primary key
- **And:** The existing provider row remains unchanged
- **And:** The selected reusable alias is used only to copy key material into the new credential alias

### Scenario: Edit an existing provider row by primary key
- **Given:** Two saved provider rows exist with the same provider/model/baseUrl but different IDs and aliases
- **When:** The user edits the display name of the second row and saves
- **Then:** Only the second row is updated
- **And:** The first row’s display name, alias, and timestamps remain unchanged

### Scenario: Create a preset under the selected persisted provider
- **Given:** A persisted provider row is selected in the BYOK sheet
- **And:** The user opens preset creation from that provider
- **When:** The user saves a valid preset draft
- **Then:** A new `api_model_configurations` row is inserted with `api_credentials_id` equal to the selected provider ID
- **And:** No other provider row is created or modified

### Scenario: Edit a preset without mutating provider draft identity
- **Given:** A persisted provider row with two presets exists
- **When:** The user edits one preset and saves
- **Then:** Only the targeted preset row changes
- **And:** The parent provider row ID remains the same
- **And:** The sibling preset remains unchanged

### Scenario: Configure screen renders from draft state while list renders from Room
- **Given:** `uiState.apiModels` is populated from Room flows
- **And:** The user opens BYOK configure for an existing provider
- **When:** The user changes draft fields before saving
- **Then:** The configure screen shows the draft edits
- **And:** The saved BYOK sheet list still shows the previously persisted values until save completes

### Scenario: Custom headers screen edits draft headers only
- **Given:** The user is editing a preset draft with custom headers
- **When:** The user adds, updates, and deletes header rows in the custom headers screen
- **Then:** The changes are reflected in `apiConfigDraft.customHeaders`
- **And:** The persisted preset in `uiState.apiModels` is unchanged until save

### Scenario: Save new provider and continue into default preset editing
- **Given:** The user creates a new provider draft successfully
- **When:** `onSaveApiCredentials(...)` completes
- **Then:** The inserted provider is reselected from the Room-backed list by returned ID
- **And:** A default preset draft is prepared against that inserted provider ID

## 2. Error Path & Edge Case Scenarios

### Scenario: Migration preserves existing credentials and allows a new duplicate row
- **Given:** A version 1 database containing one `api_credentials` row
- **When:** The database upgrades to version 2
- **And:** A second row with the same provider/model/baseUrl but a different alias is inserted
- **Then:** The migration succeeds without data loss
- **And:** Both rows exist after the insert

### Scenario: Editing a missing provider ID fails fast
- **Given:** The user enters edit mode for a provider ID that no longer exists
- **When:** Save is attempted
- **Then:** Repository validation fails with a deterministic error
- **And:** No insert fallback occurs

### Scenario: Missing reusable key source blocks key cloning
- **Given:** A new provider draft selects a reusable credential alias
- **And:** No stored key exists for that alias
- **When:** The user saves without typing a direct API key
- **Then:** Save fails
- **And:** No new credential row is inserted

### Scenario: Canceling editor discards unsaved draft edits
- **Given:** The user has modified credential or preset draft fields without saving
- **When:** The user exits back to the BYOK list
- **Then:** Draft state is cleared
- **And:** The Room-backed saved list remains unchanged

### Scenario: Blank custom headers are trimmed from draft on exit
- **Given:** The preset draft contains custom header rows with blank key or value fields
- **When:** The user taps Done on the custom headers screen
- **Then:** Blank rows are removed from the draft
- **And:** No persisted preset row is rewritten during cleanup

## 3. Mutation Defense

### Defense: Wrong-row update with duplicate provider/model/baseUrl
- **Given:** Two rows exist with identical provider/model/baseUrl and different IDs
- **When:** The second row is edited and saved
- **Then:** Assertions verify that only the second row changed
- **And:** The first row still matches its original snapshot

### Defense: Draft-state splicing from Room emissions
- **Given:** A provider draft is open with unsaved field edits
- **And:** The Room flow emits a refreshed `apiModels` list for the same provider ID
- **When:** `SettingsViewModel.uiState` recomputes
- **Then:** The draft retains the unsaved field edits
- **And:** The persisted list reflects the Room emission separately

### Defense: Reusable credential selection does not determine row identity
- **Given:** A new provider draft selects `selectedReusableApiCredential`
- **When:** Save is executed with `credentialsId == 0`
- **Then:** The inserted row ID is new
- **And:** The source credential row ID is not reused or overwritten

### Defense: Preset save cannot borrow parent identity from another selected row
- **Given:** Provider A is selected in the saved list
- **And:** The user opens a preset draft for Provider A
- **And:** The saved list selection later changes to Provider B before save
- **When:** The preset draft is saved
- **Then:** The saved preset still uses Provider A’s parent credentials ID captured in the draft session
- **And:** No config row is inserted under Provider B

### Recommended Test Touchpoints
- `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsDaoTest.kt`
- `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabaseMigrationTest.kt`
- `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImplTest.kt`
- `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModelTest.kt`
