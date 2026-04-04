# Technical Specification: Remove ModelStatus (OLD/CURRENT)

## 1. Objective
Completely remove the `ModelStatus` enum (`CURRENT` / `OLD`) from the codebase. The `ModelRegistryImpl` repository will be simplified into a pure data access layer by breaking apart `setRegisteredModel` into granular CRUD methods (`upsertLocalAsset`, `upsertLocalConfiguration`, `setDefaultLocalConfig`). The orchestration of safe updates and lifecycles will be explicitly managed by `ModelDownloadOrchestratorImpl`. `getRegisteredSelection` will be added to return a strictly resolved `SlotResolvedLocalModel`.

## 2. System Architecture

### Target Files
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/ModelStatus.kt` (DELETE)
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/SlotResolvedLocalModel.kt` (CREATE)
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/ModelRegistryPort.kt` (MODIFY)
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelEntity.kt` (MODIFY)
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelsDao.kt` (MODIFY)
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ModelRegistryImpl.kt` (MODIFY)
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/download/InitializeModelsUseCase.kt` (MODIFY)
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/download/ModelDownloadOrchestratorImpl.kt` (MODIFY)
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabase.kt` (MODIFY)
- `core/testing/src/main/kotlin/com/browntowndev/pocketcrew/core/testing/FakeModelRegistry.kt` (MODIFY)

### Component Boundaries
- **Repository Layer (`ModelRegistryImpl`)**: Becomes a thin wrapper over DAOs. It exposes granular `upsert` and `setDefault` methods instead of a monolithic state-machine method. It exposes a strictly typed `getRegisteredSelection` to resolve a specific slot's assigned config and asset.
- **Orchestrator Layer (`ModelDownloadOrchestratorImpl`)**: Takes over responsibility for the "safe replace" workflow. Upon download success, it will sequentially insert the new asset, insert the new config, and atomically update the default slot.
- **Use Case Layer (`InitializeModelsUseCase`)**: No longer passes `ModelStatus` or `markExistingAsOld`. It relies on the granular CRUD methods for tuning-only (same SHA) startup updates.

## 3. Data Models & Schemas

### Modified Models
- **`LocalModelEntity`**:
  - REMOVE `@ColumnInfo(name = "model_status") val modelStatus: ModelStatus`.

### New Models
- **`SlotResolvedLocalModel`** (in `core/domain`):
```kotlin
package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.inference.ModelType

data class SlotResolvedLocalModel(
    val modelType: ModelType,
    val asset: LocalModelAsset,
    val selectedConfig: LocalModelConfiguration
)
```

## 4. API Contracts & Interfaces

### `ModelRegistryPort`
**REMOVE:**
- `suspend fun setRegisteredModel(modelType: ModelType, asset: LocalModelAsset, status: ModelStatus, markExistingAsOld: Boolean)`
- `suspend fun clearOld()`
- `suspend fun reuseModelForRedownload(...)`

**ADD:**
- `suspend fun upsertLocalAsset(asset: LocalModelAsset): Long`
- `suspend fun upsertLocalConfiguration(config: LocalModelConfiguration): Long`
- `suspend fun setDefaultLocalConfig(modelType: ModelType, configId: Long)`
- `suspend fun getRegisteredSelection(modelType: ModelType): SlotResolvedLocalModel?`

### `LocalModelsDao`
**MODIFY:**
- `observeAllCurrent()`: Change `WHERE m.model_status = 'CURRENT'` to simply `WHERE EXISTS (SELECT 1 FROM local_model_configurations c WHERE c.local_model_id = m.id)`. (Function name can optionally be renamed to `observeAllActive()`).
- `getAllCurrent()`: Similar change to `observeAllCurrent()`.
- `getSoftDeletedModels()`: Remove `AND m.model_status = 'CURRENT'`.
**REMOVE:**
- `deleteOld()` (if it exists).

## 5. Permissions & Config Delta
- **Database Schema**: Destructive migration will handle dropping the `model_status` column since the app is unreleased. Room schema version remains the same or bumps if required by existing conventions. TypeConverters for `ModelStatus` (if any in `PocketCrewDatabase.kt` or `ByokConverters.kt`) must be removed.
- No Android permissions changes.
- No ProGuard changes.

## 6. Constitution Audit
This design adheres to the project's core architectural rules, specifically moving orchestration and workflow policy OUT of the Repository layer and into the Use Case / Orchestrator layers, establishing the Repository as a pure data-access boundary.

## 7. Cross-Spec Dependencies
No cross-spec dependencies.