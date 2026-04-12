# Discovery Report: TASK-redownload
## Local Model Re-download Mechanism

### Overview
Implement a re-download mechanism for soft-deleted local models in the settings bottom sheet.

### Key Findings

#### Domain Layer (`core/domain`)
- `LocalModelAsset`: Combines metadata and configurations.
- `LocalModelMetadata`: Contains `sha256` which is critical for matching against remote config.
- `LocalModelId`: Unique identifier for local models.
- `ModelType`: Enum for model roles. Needs `UNASSIGNED` variant.
- `LocalModelRepositoryPort`: Interface for model persistence. Missing `restoreSoftDeletedModel`.
- `GetRestorableLocalModelsUseCase`: Identifies models with 0 configurations (soft-deleted).

#### Data Layer (`core/data`)
- `LocalModelRepositoryImpl`: Implementation of the repository port.
- `DownloadWorkScheduler`: Handles enqueuing of WorkManager download jobs.
- `DownloadWorkRepository`: Reactive observation of download progress.
- `ModelConfigFetcher`: Fetches remote configuration for matching.
- `ModelDownloadWorker`: The existing worker that handles the physical download and verification.

#### UI Layer (`feature/settings`)
- `LocalModelsBottomSheet`: The main UI component.
- `SettingsViewModel`: The main ViewModel (to avoid further bloat).
- `SettingsModels`: Contains `LocalModelAssetUi` and other UI-specific models.

### Proposed Architecture
- **Isolation**: Create `ModelReDownloadViewModel` to manage re-download states and progress observation.
- **Matching**: Matching soft-deleted models to remote configurations must happen via SHA256.
- **Restoration**: Restore **ALL** system presets found in the remote config.
- **Worker Integration**: Use `DownloadWorkScheduler` with `ModelType.UNASSIGNED`.

### Impact Analysis
- **ModelType**: Adding `UNASSIGNED` ensures downloads don't conflict with current active roles.
- **Repository**: Adding `restoreSoftDeletedModel` allows re-inserting configurations for an existing metadata record.
- **UI**: Adding circular progress indicators around the download icon in the bottom sheet.
