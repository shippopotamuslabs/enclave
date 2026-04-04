# Discovery: 98-remove-model-status

## 1. Goal Summary
Completely remove ModelStatus (OLD/CURRENT) logic from ModelRegistryImpl and LocalModelsDao. Break setRegisteredModel into upsertLocalAsset, upsertLocalConfiguration, and setDefaultLocalConfig. Add SlotResolvedLocalModel as the return type for getRegisteredSelection.

## 2. Target Module Index

### Existing Data Models
[To be filled by Discovery Synthesizer based on probe reports]

### Dependencies & API Contracts
[To be filled by Discovery Synthesizer based on probe reports]

### Utility/Shared Classes
[To be filled by Discovery Synthesizer based on probe reports]

### Impact Radius
[To be filled by Discovery Synthesizer based on probe reports]

## 3. Cross-Probe Analysis

### Overlaps Identified
[To be filled by Discovery Synthesizer]

### Gaps & Uncertainties
[To be filled by Discovery Synthesizer]

### Conflicts (if any)
[To be filled by Discovery Synthesizer]

## 4. High-Impact Clarifying Questions
*None identified. Proceeding to Spec phase.*

## 5. Probe Coverage Summary
| Layer/Directory | Probe Agent | Key Findings |
|----------------|------------|-------------|
| [TBD] | [TBD] | [TBD] |

---

## Raw Probe Reports (for Synthesis)

```
# Probe Report: Data Layer

## Layer: Data Layer
## Directory: core/data

### Existing Data Models
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelEntity.kt`: LocalModelEntity — Database entity representing a downloaded model file. Currently contains `modelStatus`.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelConfigurationEntity.kt`: LocalModelConfigurationEntity — Database entity representing a tuning preset for a local model.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/DefaultModelEntity.kt`: DefaultModelEntity — Database entity mapping a `ModelType` slot to a specific config.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabase.kt`: PocketCrewDatabase — Room database definition containing type converters and DAOs.

### Dependencies & API Contracts
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ModelRegistryImpl.kt`: ModelRegistryImpl — Implementation of `ModelRegistryPort` handling registry persistence.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelsDao.kt`: LocalModelsDao — DAO for querying LocalModelEntity.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/download/ModelDownloadOrchestratorImpl.kt`: ModelDownloadOrchestratorImpl — Orchestrator managing model downloads and lifecycle callbacks.

### Utility/Shared Classes
- None relevant to this feature.

### Impact Observations
- `LocalModelEntity` will need to drop the `modelStatus` column entirely.
- `PocketCrewDatabase` may need migration or schema update since `modelStatus` is removed.
- `LocalModelsDao` will need all queries referencing `modelStatus = 'CURRENT'` or `'OLD'` updated to remove the filter. `deleteOld()` should be deleted.
- `ModelRegistryImpl` will need to implement the granular `upsertLocalAsset`, `upsertLocalConfiguration`, `setDefaultLocalConfig`, and `getRegisteredSelection` methods, and remove `setRegisteredModel` and `clearOld`.
- `ModelDownloadOrchestratorImpl` will need to call the granular CRUD methods sequentially upon successful download to atomically register and activate the new model, without relying on `markExistingAsOld`.

### Integration Points
- Interacts with `core/domain` via `ModelRegistryPort` and `ModelDownloadOrchestratorPort`.

---

# Probe Report: Domain Layer

## Layer: Domain Layer
## Directory: core/domain

### Existing Data Models
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/ModelStatus.kt`: ModelStatus — Enum class for CURRENT and OLD.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/LocalModelAsset.kt`: LocalModelAsset — Domain model containing metadata and configurations.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/SlotResolvedLocalModel.kt`: SlotResolvedLocalModel — (To be created) New explicit return type for registry selection.

### Dependencies & API Contracts
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/ModelRegistryPort.kt`: ModelRegistryPort — Interface for the registry. Needs update to add `upsertLocalAsset`, `upsertLocalConfiguration`, `setDefaultLocalConfig`, and `getRegisteredSelection`, and delete `setRegisteredModel`, `clearOld`.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/download/InitializeModelsUseCase.kt`: InitializeModelsUseCase — Use case checking startup model status. Currently calls `setRegisteredModel(..., status = ModelStatus.CURRENT)`.

### Utility/Shared Classes
- None relevant to this feature.

### Impact Observations
- `ModelStatus.kt` will be deleted.
- `ModelRegistryPort.kt` will undergo significant interface signature changes.
- `InitializeModelsUseCase.kt` will be updated to call the new granular CRUD methods for tuning-only changes on startup.
- `SlotResolvedLocalModel.kt` will be created.

### Integration Points
- Consumed by `core/data` (implementations) and UI layers (for display/usage).
```
