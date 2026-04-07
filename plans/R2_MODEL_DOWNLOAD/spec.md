# Technical Specification: R2 Model Download Routing

## 1. Objective
Update the model download pipeline to support dynamic download sources (Hugging Face or Cloudflare R2) driven by `model_config.json`. This allows the app to download gated models directly from a self-hosted Cloudflare R2 bucket without shipping API keys in the APK, while keeping the database schema intact by treating the source as a transient download property.

Acceptance Criteria:
- `model_config.json` supports a `"source"` field (values: `"HF"`, `"R2"`, default: `"HF"`).
- Models with `"source": "R2"` are downloaded from `https://config.pocketcrew.app/{fileName}`.
- Models with `"source": "HF"` (or no source) are downloaded from Hugging Face.
- Soft-deleted models retain their remote source mapping when identified for re-download.
- No Room database migrations are required.

## 2. System Architecture

### Target Files
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/download/DownloadSource.kt` [NEW]
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/RemoteModelConfig.kt` [MODIFY]
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/LocalModelMetadata.kt` [MODIFY]
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/download/InitializeModelsUseCase.kt` [MODIFY]
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/download/ModelConfigFetcherImpl.kt` [MODIFY]
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadWorkScheduler.kt` [MODIFY]
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/download/ModelDownloadWorker.kt` [MODIFY]
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/download/remote/DynamicModelUrlProvider.kt` [RENAME from HuggingFaceModelUrlProvider.kt]
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/DataModule.kt` [MODIFY]
- `model_config.json` [MODIFY]

### Component Boundaries
- **Domain Layer**: Defines `DownloadSource` enum and updates `RemoteModelConfig` and `LocalModelMetadata` to include it. `InitializeModelsUseCase` performs source reconciliation for soft-deleted models.
- **Data Layer**: `ModelConfigFetcherImpl` parses the source from JSON. `DownloadWorkScheduler` and `ModelDownloadWorker` handle the transient propagation of the source property via WorkManager's `Data` (JSON string).
- **Network Layer**: `DynamicModelUrlProvider` implements the routing logic based on the `DownloadSource` property of the `LocalModelAsset`.

## 3. Data Models & Schemas

### New Model: DownloadSource (Enum)
- `HUGGING_FACE("HF")`
- `CLOUDFLARE_R2("R2")`
- Companion function: `fromSourceName(name: String?): DownloadSource` (defaults to `HUGGING_FACE`).

### Modified Models
- **RemoteModelConfig**: Add `val source: DownloadSource = DownloadSource.HUGGING_FACE`.
- **LocalModelMetadata**: Add `val source: DownloadSource = DownloadSource.HUGGING_FACE`.

## 4. API Contracts & Interfaces

### DynamicModelUrlProvider (formerly HuggingFaceModelUrlProvider)
- Implements `ModelUrlProviderPort`.
- `getModelDownloadUrl(asset: LocalModelAsset): String`:
    - If `asset.metadata.source == DownloadSource.CLOUDFLARE_R2` -> `https://config.pocketcrew.app/${asset.metadata.remoteFileName}`
    - Else -> existing Hugging Face URL logic.

### WorkManager Input Data
- `DownloadWorkScheduler` adds `"source"` key to the JSON representation of `LocalModelAsset`.
- `ModelDownloadWorker` parses `"source"` key from the JSON.

## 5. Permissions & Config Delta
- **model_config.json**: Update schema to include `"source": "R2"` for Gemma 4 models.
- No new permissions required.
- No ProGuard or manifest changes.

## 6. Constitution Audit
This design adheres to the project's core architectural rules.
- Follows Clean Architecture by placing the enum in the domain layer.
- Maintains separation of concerns by isolating URL construction in a provider.
- Avoids database bloat by keeping transient properties in memory during the download flow.

## 7. Cross-Spec Dependencies
No cross-spec dependencies.
