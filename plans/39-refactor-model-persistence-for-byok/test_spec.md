# Test Specification: 39-refactor-model-persistence-for-byok

## 1. Happy Path Scenarios

### Scenario: Delete model that is NOT a default (no reassignment needed)
- **Given:** LocalModelEntity(id=42) with LocalModelConfigurationEntity(id=99) and isSystemPreset=true
- **And:** NO DefaultModelEntity points to config 99
- **When:** User deletes model 42
- **Then:** Physical file is deleted
- **And:** Config 99 is hard-deleted
- **And:** LocalModelEntity(42) is preserved (soft-delete)
- **And:** Model appears in "Available for Download"

### Scenario: Delete model that IS a default (reassignment required)
- **Given:** LocalModelEntity(id=42) with configs 99 (isSystemPreset=true) and 100 (isSystemPreset=false)
- **And:** DefaultModelEntity(FAST, localConfigId=99) exists
- **When:** User initiates delete on model 42
- **Then:** ReassignDefaultModelDialog is shown with configs from OTHER models AND API configs
- **And:** User selects replacementConfigId=77 (from a DIFFERENT model)
- **Then:** DefaultModelEntity(FAST) is UPDATED to point to config 77
- **And:** Physical file for model 42 is deleted
- **And:** Configs 99 and 100 are ALL hard-deleted
- **And:** LocalModelEntity(42) is preserved (soft-delete)

### Scenario: Re-download a soft-deleted model
- **Given:** LocalModelEntity(id=42) exists (soft-deleted, zero configs)
- **And:** Remote config expects ModelType.FAST with sha256="abc123"
- **When:** User clicks Download on model 42
- **Then:** New LocalModelConfigurationEntity is created with isSystemPreset=true
- **And:** DefaultModelEntity(FAST) is created pointing to new config
- **And:** File is downloaded and LocalModelEntity(42) is reused

### Scenario: InitializeModelsUseCase skips active models with intact files
- **Given:** LocalModelEntity(id=42) exists with 1 config
- **And:** Physical file for model 42 exists with correct size
- **When:** InitializeModelsUseCase() is invoked
- **Then:** FAST is NOT added to modelsToDownload
- **And:** FAST is NOT added to availableToRedownload

### Scenario: InitializeModelsUseCase adds to Available when soft-deleted
- **Given:** LocalModelEntity(id=42) exists with 0 configs (soft-deleted)
- **And:** Remote config expects ModelType.FAST with matching sha256
- **When:** InitializeModelsUseCase() is invoked
- **Then:** FAST is NOT added to modelsToDownload
- **And:** Model 42 appears in availableToRedownload

### Scenario: InitializeModelsUseCase downloads when never downloaded
- **Given:** No LocalModelEntity with matching sha256
- **When:** InitializeModelsUseCase() is invoked
- **Then:** VISION is added to modelsToDownload

### Scenario: InitializeModelsUseCase excludes soft-deleted from scanner
- **Given:** LocalModelEntity(id=42) exists with 0 configs (soft-deleted)
- **And:** Physical file for model 42 does NOT exist (was deleted)
- **When:** InitializeModelsUseCase() is invoked
- **Then:** CheckModelsUseCase is NOT called for ModelType.FAST
- **And:** FAST is NOT added to modelsToDownload (file is not flagged as missing by scanner)
- **And:** Model 42 appears in availableToRedownload

### Scenario: isSystemPreset config is read-only in UI
- **Given:** LocalModelConfigurationEntity(id=99, isSystemPreset=true)
- **When:** User views config 99 on LocalModelConfigurationScreen
- **Then:** All fields are read-only (displayName, temperature, topK, etc.)
- **And:** User cannot edit any tuning parameters

### Scenario: Non-system-preset config is editable in UI
- **Given:** LocalModelConfigurationEntity(id=100, isSystemPreset=false)
- **When:** User views config 100 on LocalModelConfigurationScreen
- **Then:** Fields are editable

## 2. Error Path & Edge Case Scenarios

### Scenario: Cannot delete last model when only one exists
- **Given:** One LocalModelEntity exists with one config
- **And:** Zero API models configured
- **When:** User attempts to delete the model
- **Then:** showCannotDeleteLastModelAlert = true
- **And:** AlertDialog shown: "You must have at least one local or API model..."
- **And:** No deletion occurs

### Scenario: Reassignment must pick config from DIFFERENT model (or API model)
- **Given:** User is deleting model 42 with config 99 as default for FAST
- **When:** ReassignDefaultModelDialog is shown
- **Then:** Only configs from OTHER models are shown as options
- **And:** Configs from model 42 are NOT shown
- **And:** API model configurations are shown as options

### Scenario: Reassignment to API model is valid
- **Given:** LocalModelEntity(id=42) has config 99 as default for FAST
- **And:** ApiModelConfigurationEntity(id=200) exists for an API provider
- **And:** User has no other local models
- **When:** User reassigns FAST to API config 200
- **Then:** DefaultModelEntity(FAST) is UPDATED with apiConfigId=200
- **And:** DefaultModelEntity(FAST).localConfigId is null

### Scenario: Multiple configs — reassignment blocks deletion of all
- **Given:** LocalModelEntity(id=42) with configs 99, 100, 101
- **And:** DefaultModelEntity(FAST, localConfigId=99) exists
- **When:** User deletes model 42 after reassigning to config 77 from model 99
- **Then:** Configs 99, 100, 101 are ALL hard-deleted
- **And:** LocalModelEntity(42) is preserved

### Scenario: Re-download creates isSystemPreset=true config
- **Given:** LocalModelEntity(id=42, sha256="abc123") exists (soft-deleted)
- **When:** User re-downloads model 42
- **Then:** New config created with isSystemPreset = true
- **And:** This config is read-only in UI

### Scenario: File missing for active model — triggers download
- **Given:** LocalModelEntity(id=42) exists with 1 config
- **And:** Physical file for model 42 does NOT exist
- **When:** InitializeModelsUseCase() is invoked
- **Then:** FAST is added to modelsToDownload (file needs redownload)

## 3. Mutation Defense

### Risk #1: Hard-deleting LocalModelEntity instead of soft-deleting
**Defense:**
- **Given:** After delete operation on model 42
- **When:** LocalModelsDao.getById(42) is queried
- **Then:** Result is null — LocalModelEntity does not exist

### Risk #2: Not hard-deleting all configs
**Defense:**
- **Given:** Model 42 has configs 99, 100, 101
- **When:** Delete operation is performed on model 42
- **Then:** LocalModelConfigurationsDao.getAllForAsset(42) returns empty list
- **And:** Configs 99, 100, 101 are all deleted

### Risk #3: Creating new LocalModelEntity on re-download instead of reusing
**Defense:**
- **Given:** LocalModelEntity(id=42) exists (soft-deleted)
- **When:** User re-downloads model 42
- **Then:** LocalModelsDao.getById(42) returns SAME entity with id=42
- **And:** No new LocalModelEntity row is created

### Risk #4: InitializeModelsUseCase re-downloads soft-deleted models
**Defense:**
- **Given:** LocalModelEntity(42) exists with no DefaultModelEntity pointing to it
- **When:** InitializeModelsUseCase() completes
- **Then:** Model 42 is NOT in modelsToDownload
- **And:** Model 42 IS in availableToRedownload

### Risk #5: Reassignment updates wrong DefaultModelEntity
**Defense:**
- **Given:** Model 42 has config 99 as default for FAST
- **And:** Model 99 has config 77 as default for MAIN
- **When:** User reassigns from 99 to 77
- **Then:** DefaultModelEntity(FAST) now points to 77
- **And:** DefaultModelEntity(MAIN) still points to 77 (unchanged)

### Risk #6: isSystemPreset flag can be user-modified
**Defense:**
- **Given:** Config 99 has isSystemPreset=true
- **When:** User attempts to change isSystemPreset via UI
- **Then:** isSystemPreset remains true
- **And:** UI does not provide any control to modify it

### Risk #7: Soft-deleted models leak into active model list via observeAllCurrent
**Defense:**
- **Given:** LocalModelEntity(id=42) is soft-deleted (0 configs)
- **When:** LocalModelsDao.observeAllCurrent() is queried
- **Then:** LocalModelEntity(42) is NOT in the result
- **And:** SettingsUiState.localModels does NOT include model 42
