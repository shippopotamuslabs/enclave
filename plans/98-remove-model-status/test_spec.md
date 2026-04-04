# Test Specification: Remove ModelStatus (OLD/CURRENT)

## 1. Happy Path Scenarios

### Scenario: Granular Model Registration
- **Given:** A `LocalModelAsset` and a `ModelType` (e.g., FAST).
- **When:** The orchestrator calls `upsertLocalAsset`, then `upsertLocalConfiguration`, then `setDefaultLocalConfig` on `ModelRegistryImpl`.
- **Then:** The repository strictly creates a `LocalModelEntity`, a `LocalModelConfigurationEntity`, and updates the `DefaultModelEntity`. The `getRegisteredSelection` returns a `SlotResolvedLocalModel` specifically matching the `ModelType` to its active `LocalModelConfiguration`.

### Scenario: Same SHA Update (Tuning-only)
- **Given:** A newly fetched remote config matching an existing local asset's SHA-256.
- **When:** `InitializeModelsUseCase` executes its deferred activation logic.
- **Then:** The use case immediately applies the update via granular upsert methods. The asset row and config row are updated. The default pointer is updated.

### Scenario: Safe Replace upon Download Success
- **Given:** A download for a new model replacement finishes successfully.
- **When:** `ModelDownloadOrchestratorImpl` handles `READY` progress.
- **Then:** It calls `upsertLocalAsset` (for the new file), `upsertLocalConfiguration` (for the new tuning), and atomically sets the new default via `setDefaultLocalConfig`. It no longer calls `clearOld()`.

## 2. Error Path & Edge Case Scenarios

### Scenario: Re-download of a Soft-deleted Asset
- **Given:** A `LocalModelEntity` exists with zero associated configurations.
- **When:** A user clicks re-download, triggering the orchestrator to fetch the model and assign it.
- **Then:** The system uses the existing `LocalModelEntity` by inserting a new config, resolving the download seamlessly, rather than failing on an unexpected state. (This previously relied on `reuseModelForRedownload`, but now it simply happens via `upsertLocalAsset` overriding or retaining the existing DB record, and inserting a new config).

## 3. Mutation Defense

### Lazy Implementation Risk
The most likely broken implementation would be implementing `getRegisteredSelection` by blindly picking the first configuration from `getRegisteredAsset`'s asset.configurations list, completely defeating the explicit slot-resolution goal.

### Defense Scenario
- **Given:** `FAST` and `THINKING` slots are assigned to the SAME `LocalModelAsset` (i.e. same underlying file) but DIFFERENT `LocalModelConfiguration` instances.
- **When:** `getRegisteredSelection(ModelType.FAST)` and `getRegisteredSelection(ModelType.THINKING)` are called.
- **Then:** The FAST call strictly returns the FAST config. The THINKING call strictly returns the THINKING config. They do NOT return the same configuration.