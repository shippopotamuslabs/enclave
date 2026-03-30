# Technical Specification: 39-refactor-model-persistence-for-byok

## 1. Objective

Implement a soft-delete mechanism for local AI models that satisfies four invariants:

1. **Re-downloadability**: When a local model is deleted, the user can re-download it from the remote R2 bucket
2. **No auto re-download on startup**: `InitializeModelsUseCase` must NOT add soft-deleted models to the download queue
3. **Minimum model count**: At least one BYOK or local model must always exist; deletion of the last model is blocked with a modal alert
4. **Default model reassignment**: When deleting a model that IS a default, the user must pick a replacement from a DIFFERENT model before deletion

**Core Strategy**: 
- Soft-delete = preserve `LocalModelEntity` row (metadata for re-download), hard-delete configs
- XOR constraint means reassignment updates `DefaultModelEntity`, never deletes it
- `InitializeModelsUseCase` simplified: if `DefaultModelEntity` exists for ModelType, skip (don't auto-download)

## 2. System Architecture

### Entity Relationships (Critical)

- `LocalModelEntity` = the model FILE (sha256, size, filename, etc.) — ONE per downloaded model
- `LocalModelConfigurationEntity` = a tuning CONFIG attached to a model — MANY per LocalModelEntity
- `DefaultModelEntity` = maps ModelType → configId (XOR: local OR api, never both)

**One LocalModelEntity can have MULTIPLE configs (different tuning presets).**

### Target Files (10 files)

**Data Layer (5 files):**
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/DefaultModelsDao.kt` — ADD `getModelIdsWithDefaults()`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelsDao.kt` — ADD `getSoftDeletedModels()`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelConfigurationsDao.kt` — ADD `getAllForAsset()`, `deleteAllForAsset()`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ModelRegistryImpl.kt` — ADD `getSoftDeletedModels()`, MODIFY `deleteModel()`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabase.kt` — ADD Room migration for `isDefault` column

**Domain Layer (2 files):**
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/ModelRegistryPort.kt` — ADD `getSoftDeletedModels()`, MODIFY `deleteModel()`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/download/DeleteLocalModelUseCase.kt` — NEW use case

**UI Layer (3 files):**
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsUiState.kt` — ADD `availableToDownloadModels`, deletion safety state
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/LocalModelsBottomSheet.kt` — ADD "Available for Download" section
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt` — ADD deletion flow state machine, reassignment handling

## 3. Data Models & Schemas

### Schema Changes

**LocalModelConfigurationEntity — ADD `isDefault` field:**
```kotlin
@ColumnInfo(name = "is_default")
val isDefault: Boolean = false
```
- `isDefault = true` → config came from R2 download (read-only in UI)
- `isDefault = false` → user-created config (editable in UI)
- User can NEVER change `isDefault`

**No other schema changes needed.**

### State Model

| State | LocalModelEntity | LocalModelConfigurationEntity | DefaultModelEntity |
|-------|-----------------|-------------------------------|-------------------|
| Active | EXISTS | EXISTS (isDefault may be true) | Points to one of the configs |
| Soft-deleted | EXISTS (metadata preserved) | DELETED (hard deleted all configs) | NOT points to this model |
| Never downloaded | DOES NOT EXIST | DOES NOT EXIST | DOES NOT EXIST |

### Query: "Available for Download"

```kotlin
// LocalModelsDao
@Query("""
    SELECT m.* FROM local_models m
    WHERE m.id NOT IN (
        SELECT c.local_model_id FROM local_model_configurations c
    )
    AND m.model_status = 'CURRENT'
""")
suspend fun getSoftDeletedModels(): List<LocalModelEntity>
```

Returns `LocalModelEntity` rows where NO configs exist. Since soft-deletion hard-deletes all configs, a model with zero configs is definitively soft-deleted.

### Query: "Does this model have any config that is a default?"

```kotlin
// DefaultModelsDao
@Query("""
    SELECT local_model_id FROM local_model_configurations
    WHERE id IN (SELECT local_config_id FROM default_models WHERE local_config_id IS NOT NULL)
""")
suspend fun getModelIdsWithDefaults(): List<Long>
```

Returns LocalModelEntity IDs that have at least one config pointed to by DefaultModelEntity.

## 4. Deletion Flow (Detailed)

### Step 1: User initiates delete on LocalModelEntity (id=42)

### Step 2: Check if reassignment needed
```kotlin
val modelConfigIds = localModelConfigurationsDao.getAllForAsset(42).map { it.id }
val defaultConfigIds = defaultModelsDao.getAll().mapNotNull { it.localConfigId }
val intersection = modelConfigIds.intersect(defaultConfigIds.toSet())

if (intersection.isNotEmpty()) {
    // Model has a config that IS a default → must reassign FIRST
    // Show ReassignDefaultModelDialog with configs from OTHER models
    // User picks replacementConfigId
} else {
    // No config is a default → proceed directly to soft-delete
    replacementConfigId = null
}
```

### Step 3: If reassignment needed
```kotlin
// User selected replacementConfigId (from a DIFFERENT model)
val modelTypeToUpdate = defaultModelsDao.getAll().find { 
    it.localConfigId in intersection 
}?.modelType

// UPDATE DefaultModelEntity to point to replacement
defaultModelsDao.upsert(DefaultModelEntity(
    modelType = modelTypeToUpdate,
    localConfigId = replacementConfigId,
    apiConfigId = null
))
```

### Step 4: Soft-delete the model
```kotlin
// Delete physical file
modelFileScanner.deleteModelFile(42)

// Hard delete ALL configs for this model
localModelConfigurationsDao.deleteAllForAsset(42)

// Preserve LocalModelEntity (soft-delete - metadata for re-download)
modelRegistry.preserveModelMetadata(modelId = 42)
```

### Step 5: On re-download
```kotlin
// Reuse existing LocalModelEntity row
val modelId = modelRegistry.reuseModel(modelId = 42, newSha256 = remoteConfig.sha256)

// Create new config with isDefault = true
val configId = localModelConfigurationsDao.upsert(
    LocalModelConfigurationEntity(
        localModelId = modelId,
        displayName = remoteConfig.displayName,
        isDefault = true,  // NEW - marks this as R2 config
        // ... other fields ...
    )
)

// Create DefaultModelEntity
defaultModelsDao.upsert(DefaultModelEntity(
    modelType = remoteConfig.modelType,
    localConfigId = configId,
    apiConfigId = null
))
```

## 5. InitializeModelsUseCase — Simplified (No SHA256)

### New Logic:
```kotlin
suspend fun checkModelsResult(): DownloadModelsResult {
    // 1. Fetch remote configs
    val remoteConfigs = modelConfigFetcher.fetchRemoteConfig().getOrElse { ... }

    // 2. For each remote config:
    //    - Check if LocalModelEntity exists by SHA256
    //    - If exists and has configs (active) -> check file integrity
    //    - If exists and has NO configs -> soft-deleted, add to Available for Download
    //    - If does NOT exist -> never downloaded (or new version), add to download queue
    
    val modelsToDownload = mutableListOf<LocalModelAsset>()
    val availableToRedownload = mutableListOf<LocalModelAsset>()

    for ((modelType, remoteAsset) in remoteConfigs) {
        val existingModel = modelsDao.getBySha256(remoteAsset.metadata.sha256)
        
        if (existingModel != null) {
            val configs = localModelConfigurationsDao.getAllForAsset(existingModel.id)
            if (configs.isEmpty()) {
                // Soft-deleted! Do not auto-download.
                availableToRedownload.add(remoteAsset)
            } else {
                // Active model - verify file exists and size matches
                val file = modelFileScanner.getModelFile(remoteAsset.metadata.localFileName)
                if (file == null || file.length() != remoteAsset.metadata.sizeInBytes) {
                    // File missing or wrong size → redownload
                    modelsToDownload.add(remoteAsset)
                }
            }
        } else {
            // Never downloaded (or remote updated to new sha256) → add to download queue
            modelsToDownload.add(remoteAsset)
        }
    }
    
    // Return result with both lists
    return DownloadModelsResult(
        modelsToDownload = modelsToDownload,
        availableToRedownload = availableToRedownload,
        // ...
    )
}
```

### Key Simplification:
- NO SHA256 computation on startup
- We completely decoupled auto-download logic from `DefaultModelEntity`. Whether a model is the default or not doesn't matter for its file integrity.
- Soft-deleted models are perfectly identified by having 0 configs.
- If a model is active (has configs) but its file is missing, it is queued for re-download.

## 6. SettingsViewModel State Machine

```kotlin
data class SettingsUiState {
    val downloadedModels: List<LocalModelAssetUi> = emptyList()
    val availableToDownloadModels: List<LocalModelAssetUi> = emptyList()  // soft-deleted
    val showCannotDeleteLastModelAlert: Boolean = false
    val pendingDeletionModelId: Long? = null
    val modelTypesNeedingReassignment: List<ModelType> = emptyList()
    val reassignmentOptions: List<ReassignmentOption> = emptyList()  // configs from OTHER models
}
```

## 7. API Contracts & Interfaces

### ModelRegistryPort — MODIFY/ADD

```kotlin
/**
 * Returns models that user downloaded but deleted (LocalModelEntity exists, no DefaultEntity).
 */
suspend fun getSoftDeletedModels(): List<LocalModelAsset>

/**
 * Deletes a model: hard-deletes configs, preserves LocalModelEntity for re-download.
 */
suspend fun deleteModel(modelId: Long, replacementConfigId: Long?): Result<Unit>

/**
 * Reuses existing LocalModelEntity row for re-download.
 */
suspend fun reuseModelForRedownload(modelId: Long, newAsset: LocalModelAsset): Long
```

### DeleteLocalModelUseCase (NEW)

```kotlin
class DeleteLocalModelUseCase @Inject constructor(
    private val modelRegistry: ModelRegistryPort,
    private val modelFileScanner: ModelFileScannerPort,
    private val defaultModelsDao: DefaultModelsDao,
    private val localModelConfigurationsDao: LocalModelConfigurationsDao
) {
    suspend operator fun invoke(modelId: Long, replacementConfigId: Long?): Result<Unit>
}
```

## 8. Permissions & Config Delta

**No AndroidManifest changes required.**
**No new permissions required.**
**Room migration:** Add `is_default` column to `local_model_configurations` table.
**No ProGuard rules changes required.**

## 9. Constitution Audit

This design adheres to the project's core architectural rules:
- **Single Responsibility**: `DeleteLocalModelUseCase` handles deletion logic
- **Dependency Inversion**: UI depends on domain interfaces, not implementations
- **Repository Pattern**: `ModelRegistryPort` abstracts data access
- **No Layer Boundary Breaches**: All database logic stays in data layer

## 10. Cross-Spec Dependencies

No cross-spec dependencies. This feature is self-contained.

### InitializeModelsUseCase — MODIFIED Logic (CRITICAL)

**BUG FIX REQUIRED**: The current `InitializeModelsUseCase` calls `setRegisteredModel` for ALL `remoteConfigs` unconditionally, which would re-create `DefaultModelEntity` entries for soft-deleted models on every startup, violating Invariant #2.

The revised logic in section 5 completely resolves this by decoupling the auto-download decision from the `DefaultModelEntity` and relying strictly on `LocalModelEntity` existence + config count.

### SettingsViewModel State Machine

```kotlin
data class SettingsUiState {
    val downloadedModels: List<LocalModelAssetUi> = emptyList()
    val availableToDownloadModels: List<LocalModelAssetUi> = emptyList()  // soft-deleted
    val showCannotDeleteLastModelAlert: Boolean = false
    val pendingDeletionModelId: Long? = null
    val modelTypesNeedingReassignment: List<ModelType> = emptyList()
}
```

## 5. Permissions & Config Delta

**No AndroidManifest changes required.**
**No new permissions required.**
**No Room migration needed** (soft-delete uses row deletion, not schema changes).
**No ProGuard rules changes required.**

## 6. Constitution Audit

This design adheres to the project's core architectural rules:

- **Single Responsibility**: `SoftDeleteLocalModelUseCase` handles only soft deletion logic
- **Dependency Inversion**: UI depends on domain interfaces, not implementations
- **Repository Pattern**: `ModelRegistryPort` abstracts data access
- **No Layer Boundary Breaches**: All database logic stays in data layer
- **XOR Constraint as Signal**: Leverages existing database constraint for state management

## 7. Cross-Spec Dependencies

No cross-spec dependencies. This feature is self-contained within the model deletion flow.
