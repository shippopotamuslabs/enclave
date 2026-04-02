# Discovery: PR Review Follow-ups

## 1. Goal Summary
Address PR review feedback by refining the model upgrade lifecycle, registry demotion/cleanup, soft-delete handling, inference caching, and BYOK alias persistence. The primary focus is deferring model activation until successful download to ensure reliable fallback states.

## 2. Target Module Index

### Existing Data Models
- `LocalModelAsset` (`core/domain`): Represents a local model file and its configurations.
- `LocalModelMetadata` (`core/domain`): Metadata for a local model file (SHA, size, format).
- `LocalModelConfiguration` (`core/domain`): Inference settings for a model.
- `DownloadModelsResult` (`core/domain`): Result of checking models at startup.
- `ModelStatus` (`core/domain`): Enum (CURRENT, OLD).
- `LocalModelEntity` (`core/data`): DB entity for local models.
- `LocalModelConfigurationEntity` (`core/data`): DB entity for configurations.
- `DefaultModelEntity` (`core/data`): DB entity for model type assignments.
- `ApiCredentialsEntity` (`core/data`): DB entity for BYOK credentials.

### Dependencies & API Contracts
- `ModelRegistryPort` (`core/domain`): Interface for managing registered models and configurations.
- `ModelDownloadOrchestratorPort` (`core/domain`): Interface for coordinating model downloads.
- `ModelConfigFetcherPort` (`core/domain`): Interface for fetching remote model configurations.
- `LocalModelsDao` (`core/data`): DAO for `LocalModelEntity`.
- `LocalModelConfigurationsDao` (`core/data`): DAO for `LocalModelConfigurationEntity`.
- `ApiCredentialsDao` (`core/data`): DAO for `ApiCredentialsEntity`.

### Utility/Shared Classes
- `InitializeModelsUseCase` (`core/domain`): Handles app startup model initialization.
- `CheckModelsUseCase` (`core/domain`): Scans filesystem and compares with expected models.
- `PocketCrewDatabase` (`core/data`): Room database definition.
- `ConversationManagerImpl` (`feature/inference`): Manages active conversation state.
- `InferenceFactoryImpl` (`feature/inference`): Creates inference service instances.
- `SettingsViewModel` (`feature/settings`): Manages settings UI state.

### Impact Radius
- `InitializeModelsUseCase.kt`: Major rework to defer registration for changed-SHA models.
- `ModelRegistryPort.kt`: Remove `getAssetsPreferringOld()`.
- `ModelRegistryImpl.kt`: Fix `setRegisteredModel()` demotion logic; implement `clearOld()`.
- `ModelDownloadOrchestratorImpl.kt`: Trigger registry updates post-download; emit fallback snackbar on failure.
- `LocalModelsDao.kt`: Add `deleteOld()` method.
- `ApiCredentialsEntity.kt`: Add unique index on `credential_alias`.
- `PocketCrewDatabase.kt`: Add Room migration for `api_credentials`.
- `ConversationManagerImpl.kt`: Add synchronous read-through in `getConversation()`.
- `InferenceFactoryImpl.kt`: Update cache key to include asset SHA.
- `SettingsViewModel.kt`: Generate deterministic aliases for new BYOK credentials.

## 3. Cross-Probe Analysis
### Overlaps Identified
- `ModelRegistryPort` and its implementation `ModelRegistryImpl` are central to most changes, appearing in Domain and Data layer probes.
- `LocalModelAsset` and related config models are used across Domain, Data, and Feature layers.

### Gaps & Uncertainties
- **Activation Trigger**: While `ModelDownloadOrchestratorImpl` is the logical place for post-download activation, we need to ensure all relevant information (which models to activate) is available at that point.
- **Migration Path**: The BYOK alias backfill needs a robust strategy to handle existing duplicates before enforcing the unique constraint.

### Conflicts (if any)
*None identified.*

## 4. High-Impact Clarifying Questions
*None identified. Proceeding to Spec phase.*

## 5. Probe Coverage Summary
| Layer/Directory | Probe Agent | Key Findings |
|----------------|------------|-------------|
| core/domain | Domain Probe | Identified use cases and ports needing rework for deferred activation. |
| core/data | Data Probe | Identified DAO and repository changes for demotion and cleanup. Identified DB migration needs. |
| feature/inference | Inference Probe | Identified cache invalidation and read-through issues. |
| feature/settings | Settings Probe | Identified BYOK alias generation logic in ViewModel. |
