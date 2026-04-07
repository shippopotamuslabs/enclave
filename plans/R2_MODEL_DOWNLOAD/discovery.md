# Discovery: R2 Model Download Routing

## 1. Goal Summary
Update the model download pipeline to support dynamic download sources (Hugging Face or Cloudflare R2) driven by `model_config.json`. This allows the app to download gated models directly from a self-hosted Cloudflare R2 bucket without shipping API keys in the APK, while keeping the database schema intact by treating the source as a transient download property.

## 2. Target Module Index

### Existing Data Models
- **RemoteModelConfig** (`core/domain/.../RemoteModelConfig.kt`): Represents model configuration fetched from remote server.
- **LocalModelMetadata** (`core/domain/.../LocalModelMetadata.kt`): Metadata for locally stored models.
- **LocalModelAsset** (`core/domain/.../LocalModelAsset.kt`): Aggregates metadata and configuration.
- **ModelType** (`core/domain/.../ModelType.kt`): Enum for model slots (VISION, MAIN, etc.).

### Dependencies & API Contracts
- **OkHttp**: Used in `ModelConfigFetcherImpl` for network requests.
- **WorkManager**: Used in `DownloadWorkScheduler` and `ModelDownloadWorker` for background tasks.
- **JSONObject**: Used for manual JSON parsing and serialization in `ModelConfigFetcherImpl`, `DownloadWorkScheduler`, and `ModelDownloadWorker`.
- **ModelUrlProviderPort**: Interface defining `getConfigUrl()` and `getModelDownloadUrl()`.
- **Hilt**: Dependency injection via `@Inject` and `@Singleton`.

### Utility/Shared Classes
- **ModelConfigFetcherImpl**: Fetches and parses `model_config.json`.
- **DownloadWorkScheduler**: Prepares and enqueues `ModelDownloadWorker` with serialized JSON data.
- **ModelDownloadWorker**: Handles the actual file download logic using `FileDownloaderPort`.
- **HuggingFaceModelUrlProvider**: Current implementation for HF-based URLs.
- **R2ModelUrlProvider**: Existing implementation for R2-based URLs (currently used for config fetching).

### Impact Radius
- **Domain Layer**: `RemoteModelConfig` and `LocalModelMetadata` need a new `source` field. `InitializeModelsUseCase` needs to handle source mapping for soft-deleted models.
- **Data Layer**: `ModelConfigFetcherImpl` needs to parse the new field. `DownloadWorkScheduler` and `ModelDownloadWorker` need to propagate the field through WorkManager `Data`.
- **Provider Layer**: `HuggingFaceModelUrlProvider` needs to be renamed to `DynamicModelUrlProvider` and support both HF and R2 sources. `DataModule` bindings need updating.
- **Assets**: `model_config.json` needs to be updated with the `source` field for specific models.

## 3. Cross-Probe Analysis
### Overlaps Identified
- **JSON Serialization**: Both `DownloadWorkScheduler` and `ModelDownloadWorker` rely on a shared manual JSON serialization/deserialization pattern for passing complex data to WorkManager. This is a critical point of synchronization.
- **ModelUrlProviderPort**: Consumed by both `ModelConfigFetcherImpl` and `ModelDownloadWorker`.

### Gaps & Uncertainties
- **DownloadSource Enum Location**: The plan suggests creating `DownloadSource` in `core/domain`. This is correct as it's used by domain models.
- **Fallback Logic**: The plan specifies defaulting to `HUGGING_FACE`. This should be explicitly handled in `DownloadSource.fromSourceName`.

### Conflicts (if any)
- *None identified. Probes are consistent with the provided strategic plan.*

## 4. High-Impact Clarifying Questions
*None identified. Proceeding to Spec phase.*

## 5. Probe Coverage Summary
| Layer/Directory | Probe Agent | Key Findings |
|----------------|------------|-------------|
| Domain (Config Models) | Domain Probe | Identified models needing `source` field and use case needing logic update. |
| Data (Download) | Data Probe | Identified serialization/deserialization points and provider renaming requirement. |
