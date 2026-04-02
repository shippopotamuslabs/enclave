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
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabase.kt` — ADD Room migration for `isSystemPreset` column

**Domain Layer (2 files):**
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/ModelRegistryPort.kt` — ADD `getSoftDeletedModels()`, MODIFY `deleteModel()`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/download/DeleteLocalModelUseCase.kt` — NEW use case

**UI Layer (3 files):**
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsUiState.kt` — ADD `availableToDownloadModels`, deletion safety state
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/LocalModelsBottomSheet.kt` — ADD "Available for Download" section
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt` — ADD deletion flow state machine, reassignment handling

## 3. Data Models & Schemas

### Schema Changes

**LocalModelConfigurationEntity — ADD `isSystemPreset` field:**
```kotlin
@ColumnInfo(name = "is_system_preset")
val isSystemPreset: Boolean = false
```
- `isSystemPreset = true` → config came from R2 download (read-only in UI)
- `isSystemPreset = false` → user-created config (editable in UI)
- User can NEVER change `isSystemPreset`
- **NOTE**: Renamed from `isDefault` to avoid confusion with `DefaultModelEntity`. `isSystemPreset` means "is a factory-provided preset", not "is the currently active default".

**No other schema changes needed.**

### State Model

| State | LocalModelEntity | LocalModelConfigurationEntity | DefaultModelEntity |
|-------|-----------------|-------------------------------|-------------------|
| Active | EXISTS | EXISTS (isSystemPreset may be true) | Points to one of the configs |
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

### CRITICAL: Fix `LocalModelsDao` to Exclude Soft-Deleted Models

The current `observeAllCurrent()` and `getAllCurrent()` queries return models where `model_status = 'CURRENT'`. **This includes soft-deleted models**, because soft-deleted models retain `model_status = 'CURRENT'`. This causes `ModelRegistryImpl.observeAssets()` to emit soft-deleted models as if they were active (showing without configs).

**Fix — update both queries:**
```kotlin
// OLD (broken — includes soft-deleted models):
@Query("SELECT * FROM local_models WHERE model_status = 'CURRENT'")
fun observeAllCurrent(): Flow<List<LocalModelEntity>>

// NEW (correct — excludes soft-deleted):
@Query("""
    SELECT m.* FROM local_models m
    WHERE m.model_status = 'CURRENT'
    AND EXISTS (SELECT 1 FROM local_model_configurations c WHERE c.local_model_id = m.id)
""")
fun observeAllCurrent(): Flow<List<LocalModelEntity>>
```

Apply the same fix to `getAllCurrent()`.

## 4. Deletion Flow (Detailed)

### Step 1: User initiates delete on LocalModelEntity (id=42)

### Step 2: Check if reassignment needed
```kotlin
val modelConfigIds = localModelConfigurationsDao.getAllForAsset(42).map { it.id }
val defaultConfigIds = defaultModelsDao.getAll().mapNotNull { it.localConfigId }
val intersection = modelConfigIds.intersect(defaultConfigIds.toSet())

if (intersection.isNotEmpty()) {
    // Model has a config that IS a default → must reassign FIRST
    // Show ReassignDefaultModelDialog with configs from OTHER models AND API configs
    // User picks either a local config from another model OR an API config
    replacementLocalConfigId = ... // from LocalModelConfigurationEntity
    replacementApiConfigId = ...  // from ApiModelConfigurationEntity
} else {
    // No config is a default → proceed directly to soft-delete
    replacementLocalConfigId = null
    replacementApiConfigId = null
}
```

### Step 3: If reassignment needed
```kotlin
// User selected either a local config from another model OR an API config
val modelTypeToUpdate = defaultModelsDao.getAll().find { 
    it.localConfigId in intersection 
}?.modelType

// UPDATE DefaultModelEntity to point to replacement (either local or API)
defaultModelsDao.upsert(DefaultModelEntity(
    modelType = modelTypeToUpdate,
    localConfigId = replacementLocalConfigId,
    apiConfigId = replacementApiConfigId
))
```

**Important**: The reassignment dialog MUST offer configs from OTHER local models AND existing API model configurations as replacement options. If the only other active model is an API model, the user can reassign a local default to an API config. This is why `DeleteLocalModelUseCase` must accept both `replacementLocalConfigId: Long?` and `replacementApiConfigId: Long?`.

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

// Create new config with isSystemPreset = true
val configId = localModelConfigurationsDao.upsert(
    LocalModelConfigurationEntity(
        localModelId = modelId,
        displayName = remoteConfig.displayName,
        isSystemPreset = true,  // NEW - marks this as R2 config
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

**Architecture Note**: The current codebase delegates file scanning and eligibility checking to `CheckModelsUseCase` / `CheckModelEligibilityUseCase`. This separation of concerns should be preserved. Instead of rewriting all logic into `InitializeModelsUseCase`, we add a **pre-filter step** that identifies soft-deleted models before invoking `CheckModelsUseCase`. This keeps file scanning logic in `CheckModelsUseCase` where it belongs.

**Revised Logic:**
```kotlin
suspend fun checkModelsResult(): DownloadModelsResult {
    // 1. Fetch remote configs
    val remoteConfigs = modelConfigFetcher.fetchRemoteConfig().getOrElse { ... }

    // 2. Pre-filter: identify soft-deleted models BEFORE passing to CheckModelsUseCase
    //    Soft-deleted models (0 configs) must NOT be passed to the scanner, or the scanner
    //    will see a missing file and force-add them to modelsToDownload.
    val modelsToDownload = mutableListOf<LocalModelAsset>()
    val availableToRedownload = mutableListOf<LocalModelAsset>()

    for ((modelType, remoteAsset) in remoteConfigs) {
        val existingModel = modelsDao.getBySha256(remoteAsset.metadata.sha256)
        
        if (existingModel != null) {
            val configs = localModelConfigurationsDao.getAllForAsset(existingModel.id)
            if (configs.isEmpty()) {
                // Soft-deleted! Do not pass to scanner. Add to availableToRedownload.
                availableToRedownload.add(remoteAsset)
                // Do NOT add to modelsToDownload — we skip the scanner for this model
                continue
            }
        }
        // For active models (has configs) or never-downloaded models, 
        // add to a working map for CheckModelsUseCase
        // ...
    }
    
    // 3. Pass ONLY non-soft-deleted models to CheckModelsUseCase
    val activeRemoteConfigs = remoteConfigs.filterKeys { modelType ->
        // Only include modelTypes that are NOT soft-deleted
        availableToRedownload.none { it.metadata.sha256 == remoteConfigs[modelType]?.metadata?.sha256 }
    }.toMap()

    // CheckModelsUseCase now only sees active + never-downloaded models
    val modelsResult = checkModelsUseCase(
        downloadedModels = currentModels,
        expectedModels = activeRemoteConfigs
    )

    // 4. Merge: modelsResult.modelsToDownload + availableToRedownload
    //    Return a result that includes both
    return DownloadModelsResult(
        allModels = remoteConfigs,
        modelsToDownload = modelsResult.modelsToDownload,
        scanResult = modelsResult.scanResult,
        availableToRedownload = availableToRedownload  // NEW field
    )
}
```

**Key Points:**
- NO SHA256 computation on startup
- Soft-deleted models are intercepted BEFORE `CheckModelsUseCase` so the file scanner never sees them
- `availableToRedownload` is returned separately so the UI can show "Available for Download"
- If a model is active (has configs) but its file is missing, it is still queued for re-download via `CheckModelsUseCase`

### Update DownloadModelsResult

```kotlin
data class DownloadModelsResult(
    val allModels: Map<ModelType, LocalModelAsset>,
    val modelsToDownload: List<LocalModelAsset>,
    val scanResult: ModelScanResult,
    val availableToRedownload: List<LocalModelAsset> = emptyList()  // NEW
)
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
    // Reassignment options: union of configs from other local models AND all API configs
    val reassignmentOptions: List<ReassignmentOption> = emptyList()
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
    /**
     * @param modelId The LocalModelEntity ID to soft-delete
     * @param replacementLocalConfigId Replacement local config ID (from a different model), or null
     * @param replacementApiConfigId Replacement API config ID, or null (mutually exclusive with localConfigId)
     */
    suspend operator fun invoke(
        modelId: Long,
        replacementLocalConfigId: Long? = null,
        replacementApiConfigId: Long? = null
    ): Result<Unit>
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
- **Separation of Concerns Preserved**: File scanning logic remains in `CheckModelsUseCase`; `InitializeModelsUseCase` adds only a pre-filter step for soft-deleted models

## 7. Cross-Spec Dependencies

No cross-spec dependencies. This feature is self-contained within the model deletion flow.

### Cross-Feature Gaps Identified

1. **LocalModelsDao.observeAllCurrent() leaks soft-deleted models** — This query currently returns models with `model_status = 'CURRENT'` without checking for the existence of configs. Soft-deleted models (with 0 configs) would be emitted as if they were active. Fixed in the DAO updates above.

2. **ModelRegistryImpl cache invalidation on soft-delete** — `ModelRegistryImpl` uses an in-memory cache keyed by `ModelType`. When a model is soft-deleted, the cache entry for its `ModelType` slot must be cleared. Ensure `DeleteLocalModelUseCase` or `ModelRegistryImpl.deleteModel()` calls `ModelRegistryImpl`'s cache invalidation method.

3. **No `isSystemPreset` on ApiModelConfigurationEntity** — API model presets do not need a system-preset flag since all API presets are user-created. Not a gap, but documented here for completeness.
