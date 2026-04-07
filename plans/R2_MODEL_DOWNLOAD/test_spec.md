# Test Specification: R2 Model Download Routing

## 1. Happy Path Scenarios

### Scenario: Parse R2 Source from JSON
- **Given:** `model_config.json` containing a model with `"source": "R2"`.
- **When:** `ModelConfigFetcherImpl.fetchRemoteConfig()` is called.
- **Then:** The resulting `RemoteModelConfig` for that model has `source == DownloadSource.CLOUDFLARE_R2`.

### Scenario: Default to Hugging Face
- **Given:** `model_config.json` containing a model with no `"source"` field.
- **When:** `ModelConfigFetcherImpl.fetchRemoteConfig()` is called.
- **Then:** The resulting `RemoteModelConfig` for that model has `source == DownloadSource.HUGGING_FACE`.

### Scenario: Routing to Cloudflare R2
- **Given:** `LocalModelAsset` with `source == DownloadSource.CLOUDFLARE_R2`.
- **When:** `DynamicModelUrlProvider.getModelDownloadUrl(asset)` is called.
- **Then:** The returned URL starts with `https://config.pocketcrew.app/`.

### Scenario: Routing to Hugging Face
- **Given:** `LocalModelAsset` with `source == DownloadSource.HUGGING_FACE`.
- **When:** `DynamicModelUrlProvider.getModelDownloadUrl(asset)` is called.
- **Then:** The returned URL starts with `https://huggingface.co/`.

### Scenario: Reconcile Soft-Deleted Model Source
- **Given:** A soft-deleted asset loaded from the database (defaults to `HUGGING_FACE`) and a fresh `RemoteModelConfig` with `source == DownloadSource.CLOUDFLARE_R2`.
- **When:** `InitializeModelsUseCase` reconciles the models.
- **Then:** The `availableToRedownload` asset for that model has `source == DownloadSource.CLOUDFLARE_R2`.

## 2. Error Path & Edge Case Scenarios

### Scenario: Invalid Source Name in JSON
- **Given:** `model_config.json` containing a model with `"source": "INVALID_VALUE"`.
- **When:** `ModelConfigFetcherImpl.fetchRemoteConfig()` is called.
- **Then:** The resulting `RemoteModelConfig` for that model has `source == DownloadSource.HUGGING_FACE` (fallback).

### Scenario: Missing HF Model Name for HF Source
- **Given:** `LocalModelAsset` with `source == DownloadSource.HUGGING_FACE` but empty `huggingFaceModelName`.
- **When:** `DynamicModelUrlProvider.getModelDownloadUrl(asset)` is called.
- **Then:** A `MalformedURLException` or `IllegalStateException` is thrown (depends on implementation, but must fail).

## 3. Mutation Defense
### Lazy Implementation Risk
A lazy implementation might always use Hugging Face for downloads if it forgets to propagate the `source` property through `DownloadWorkScheduler` and `ModelDownloadWorker`.

### Defense Scenario
- **Given:** `DownloadWorkScheduler` enqueues a model with `source == DownloadSource.CLOUDFLARE_R2`.
- **When:** `ModelDownloadWorker` parses the input data and calls `modelUrlProvider.getModelDownloadUrl(asset)`.
- **Then:** The `asset` passed to the provider MUST have `source == DownloadSource.CLOUDFLARE_R2` and the URL must be an R2 URL.
