# Implementation Specification: TASK-redownload
## Local Model Re-download Mechanism

### Goal
Implement a re-download mechanism for soft-deleted models in the `LocalModelsBottomSheet`, using a dedicated `ModelReDownloadViewModel` and matching models by SHA256 to restore all system presets.

### Technical Architecture

#### 1. Domain Layer Changes
- **`ModelType`**: Add `UNASSIGNED` enum value.
- **`LocalModelRepositoryPort`**: Add `suspend fun restoreSoftDeletedModel(id: LocalModelId, configurations: List<LocalModelConfiguration>): LocalModelAsset`.
- **`ReDownloadModelUseCase`**: **NEW**. Logic:
    1. Get soft-deleted model by ID.
    2. Fetch remote config from `ModelConfigFetcherPort`.
    3. Match local SHA256 to remote SHA256.
    4. Map all remote configurations to `LocalModelConfiguration` instances (setting `isSystemPreset = true` and target `localModelId`).
    5. Call `repository.restoreSoftDeletedModel`.
- **`SettingsLocalModelUseCases`**: Expose `ReDownloadModelUseCase`.

#### 2. Data Layer Changes
- **`LocalModelRepositoryImpl`**: Implement `restoreSoftDeletedModel`. It should loop through the configurations and call `upsertLocalConfiguration`.
- **`LocalModelConfigurationsDao`**: Ensure `upsert` handles re-inserts correctly.

#### 3. Feature/Settings (UI) Changes
- **`ModelReDownloadViewModel`**: **NEW**. Responsibility:
    - Manage state map: `LocalModelId -> ReDownloadProgress`.
    - Trigger `ReDownloadModelUseCase`.
    - Create new download session via `DownloadSessionManager`.
    - Enqueue download work via `DownloadWorkScheduler` (using `ModelType.UNASSIGNED`).
    - Observe work progress via `DownloadWorkRepository.observeDownloadProgress`.
- **`LocalModelsBottomSheet.kt`**:
    - Inject `ModelReDownloadViewModel`.
    - Update `LocalModelAssetListView` to pass progress flows to cards.
    - Update `LocalModelAssetCard`:
        - If model is soft-deleted, show download icon with circular progress bar.
        - Handle IDLE, PREPARING, DOWNLOADING, COMPLETE, and FAILED states.

### Implementation Tasks
1. [ ] Add `ModelType.UNASSIGNED`.
2. [ ] Update `LocalModelRepositoryPort` and `LocalModelRepositoryImpl`.
3. [ ] Implement `ReDownloadModelUseCase`.
4. [ ] Implement `ModelReDownloadViewModel`.
5. [ ] Update `LocalModelsBottomSheet` UI components.
6. [ ] Verify with tests and manual check.

### Verification Plan
- **Unit Tests**:
    - `ReDownloadModelUseCaseTest`: Mock SHA matching and check preset restoration count.
    - `ModelReDownloadViewModelTest`: Observe state transitions during simulated progress updates.
- **Manual**:
    - Build app, soft-delete a model, re-download it, verify it restores all presets and UI shows progress.
