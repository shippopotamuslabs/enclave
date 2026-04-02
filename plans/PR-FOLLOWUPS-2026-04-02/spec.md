# Technical Specification: PR Review Follow-ups

## 1. Objective
Refine the model upgrade lifecycle by deferring model activation until download success, fixing registry demotion/cleanup, correcting soft-delete handling, improving inference reliability, and enforcing BYOK alias uniqueness with deterministic generation.

## 2. System Architecture

### Target Files
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/download/InitializeModelsUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/ModelRegistryPort.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ModelRegistryImpl.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelsDao.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/download/ModelDownloadOrchestratorImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationManagerImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImpl.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsEntity.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabase.kt`

### Component Boundaries
- **Download Lifecycle**: `InitializeModelsUseCase` will identify models needing download but will NOT call `setRegisteredModel` for changed-SHA assets immediately. Instead, it will pass the "pending" activation info to `ModelDownloadOrchestratorImpl`.
- **Registry**: `ModelRegistryImpl` will correctly demote the *previous* slot assignment to `OLD` instead of using the incoming SHA to find the "current" row to demote. `clearOld()` will be implemented to prune `OLD` rows.
- **Inference**: `ConversationManagerImpl` will handle cold-start race conditions by performing a synchronous read-through if its cache is empty. `InferenceFactoryImpl` will use the model SHA in its cache key to ensure service recreation when a model file changes.
- **BYOK**: `SettingsViewModel` will generate deterministic aliases (`provider-model[-n]`). `ApiCredentialsEntity` will enforce uniqueness at the database level.

## 3. Data Models & Schemas
- **`ApiCredentialsEntity`**: Add `UNIQUE` constraint to `credential_alias`.
- **`ModelStatus`**: (Existing) Used to distinguish `CURRENT` from `OLD` models.
- **`LocalModelAsset`**: (Existing) Metadata and configurations.

## 4. API Contracts & Interfaces
- **`ModelRegistryPort`**:
  - DELETE `getAssetsPreferringOld()`.
  - UPDATE `setRegisteredModel()`: Ensure it targets the slot's previous model for demotion.
  - UPDATE `clearOld()`: Implement real DB deletion of `OLD` rows.
- **`LocalModelsDao`**:
  - ADD `deleteOld()`: `DELETE FROM local_models WHERE model_status = 'OLD'`.
- **`ModelDownloadOrchestratorPort`**:
  - (Internal) `updateFromProgressUpdate` will trigger `updateModelRegistry` which now must also commit the "pending" activations.

## 5. Permissions & Config Delta
- **Database Migration**: A Room migration is required for the `api_credentials` table to add the unique index on `credential_alias`. Existing blank or duplicate aliases must be backfilled using the `provider-model` slug pattern.

## 6. Constitution Audit
This design adheres to the project's core architectural rules, specifically the "Unidirectional Data Flow" (deferring registry mutation until success) and "Persistence as Source of Truth" (enforcing unique aliases at the DB level).

## 7. Cross-Spec Dependencies
No cross-spec dependencies.
