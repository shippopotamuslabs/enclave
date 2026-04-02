# Discovery: 39-refactor-model-persistence-for-byok

## 1. Goal Summary
Implement a soft-delete mechanism for local AI models that satisfies four invariants: (1) allow re-download of deleted models, (2) prevent auto re-download on startup, (3) enforce minimum model count, (4) handle default model reassignment by picking from DIFFERENT models. Core strategy: preserve LocalModelEntity for re-download, hard-delete all configs, simplify InitializeModelsUseCase to just check if DefaultModelEntity exists.

## 2. Entity Relationships (Critical)

- `LocalModelEntity` = the model FILE (sha256, size, filename) — ONE per downloaded model
- `LocalModelConfigurationEntity` = a tuning CONFIG — MANY per LocalModelEntity (different presets)
- `DefaultModelEntity` = maps ModelType → configId (XOR: local OR api, never both)

**One LocalModelEntity can have MULTIPLE configs (different tuning presets).**

## 3. Deletion Flow (Corrected)

### User deletes a LocalModelEntity (a downloaded model file):

**Step 1: Check if reassignment needed**
```
Find all config IDs belonging to model 42
Check if DefaultModelEntity points to any of those config IDs
If YES → must reassign FIRST (show dialog, user picks from OTHER models)
If NO → proceed directly to soft-delete
```

**Step 2: Reassignment (if needed)**
```
User picks replacementConfigId from a DIFFERENT model OR an API model
UPDATE DefaultModelEntity(ModelType.X, newConfigId) — NOT delete, UPDATE
```
**Note**: Reassignment options include BOTH local configs from other models AND API model configurations. If the only alternative is an API model, the user may reassign a local model slot to an API config. `DeleteLocalModelUseCase` accepts both `replacementLocalConfigId` and `replacementApiConfigId`.

**Step 3: Soft-delete model 42**
```
Delete physical file
Hard delete ALL configs belonging to model 42 (including the one that was reassigned away)
Preserve LocalModelEntity(42) row (soft-delete — metadata for re-download)
```

**Key insight**: When reassigning, you pick from a DIFFERENT model. The reassigned config stays intact. Only the original model's configs get hard-deleted.

## 4. InitializeModelsUseCase — Simplified

### New Logic (No SHA256 computation):
```
For each remote config:
  - Check if LocalModelEntity exists by SHA256
  - If exists and has configs (active) -> check file integrity
  - If exists and has NO configs -> soft-deleted, add to Available for Download
  - If does NOT exist -> never downloaded (or new version), add to modelsToDownload
```

### Key Simplification:
- NO SHA256 computation on startup
- We completely decoupled auto-download logic from `DefaultModelEntity`. Whether a model is the default or not doesn't matter for its file integrity.
- Soft-deleted models are perfectly identified by having 0 configs.
- If a model is active (has configs) but its file is missing, it is queued for re-download.

## 5. isSystemPreset Semantics

- `isSystemPreset = true` → config came from R2 download (read-only in UI)
- User can NEVER change `isSystemPreset`
- Used by UI to determine if LocalModelConfigurationScreen should be read-only
- **NOTE**: Renamed from `isDefault` to avoid confusion with `DefaultModelEntity`. `isSystemPreset` means "is a factory-provided preset", NOT "is the currently active default".

## 6. Re-download Flow

```
User clicks Download on soft-deleted model
Reuse existing LocalModelEntity row (same id)
Create new LocalModelConfigurationEntity with isSystemPreset=true
Create DefaultModelEntity pointing to new config
Download file
```

## 7. Target Files to Modify

### Data Layer
- `LocalModelConfigurationEntity` — ADD `isSystemPreset: Boolean = false` column
- `LocalModelsDao` — ADD `getSoftDeletedModels()`, FIX `observeAllCurrent()` and `getAllCurrent()` to exclude soft-deleted models (models with 0 configs)
- `LocalModelConfigurationsDao` — already has `deleteAllForAsset()`
- `DefaultModelsDao` — ADD `getModelIdsWithDefaults()` query
- `ModelRegistryImpl` — ADD `getSoftDeletedModels()`, MODIFY delete logic

### Domain Layer
- `ModelRegistryPort` — ADD `getSoftDeletedModels()` interface method
- `DeleteLocalModelUseCase` — NEW use case

### UI Layer
- `SettingsUiState` — ADD `availableToDownloadModels`, deletion state
- `LocalModelsBottomSheet` — ADD "Available for Download" section
- `SettingsViewModel` — ADD deletion flow, reassignment handling

## 8. State Model

| State | LocalModelEntity | Configs | DefaultModelEntity |
|-------|-----------------|---------|-------------------|
| Active | EXISTS | EXISTS (may have isSystemPreset=true) | Points to one of the configs |
| Soft-deleted | EXISTS (preserved) | DELETED (all hard-deleted) | NOT point to this model |
| Never downloaded | DOES NOT EXIST | DOES NOT EXIST | DOES NOT EXIST |

## 9. Key Queries

### Available for Download
```sql
SELECT m.* FROM local_models m
WHERE m.id NOT IN (
    SELECT c.local_model_id FROM local_model_configurations c
)
AND m.model_status = 'CURRENT'
```

### Does model have any config that is a default?
```sql
SELECT local_model_id FROM local_model_configurations
WHERE id IN (SELECT local_config_id FROM default_models WHERE local_config_id IS NOT NULL)
```

### CRITICAL: LocalModelsDao observeAllCurrent / getAllCurrent
These queries must exclude soft-deleted models (models with 0 configs). Soft-deleted models retain `model_status = 'CURRENT'`, so a naive filter by status alone would include them.

```sql
-- BROKEN (includes soft-deleted):
SELECT * FROM local_models WHERE model_status = 'CURRENT'

-- FIXED (excludes soft-deleted):
SELECT m.* FROM local_models m
WHERE m.model_status = 'CURRENT'
AND EXISTS (SELECT 1 FROM local_model_configurations c WHERE c.local_model_id = m.id)
```
