# Technical Specification: Download Worker Refactor
## Shared Background Pipeline for Startup Downloads and Soft-Deleted Model Re-Download

---

## 1. Objective

Refactor the download system into a two-worker WorkManager chain:

1. `ModelDownloadWorker`
2. `DownloadFinalizeWorker`

The download worker remains a pure transfer worker.
The finalize worker becomes the durable owner of all repository mutation and filesystem reconciliation.

This architecture must support two scenarios with one shared byte-transfer engine:

- startup downloads initiated from `InitializeModelsUseCase` / `ModelDownloadOrchestratorImpl`
- single-model re-download initiated for a soft-deleted local model

---

## 2. Acceptance Criteria

The refactor is complete when all of the following are true:

- `ModelDownloadWorker` no longer owns repository updates, slot activation, or orphan cleanup.
- `DownloadFinalizeWorker` exists and runs after successful download work through a WorkManager chain.
- Both startup downloads and re-downloads use the same `DownloadFileSpec`-based transfer worker.
- Startup finalization happens in background and activates downloaded model slots through `SyncLocalModelRegistryUseCase`.
- Re-download finalization happens in background and restores soft-deleted configurations only after bytes are present.
- `ReDownloadModelUseCase` no longer calls `restoreSoftDeletedModel()` before the download succeeds.
- Work observation is chain-aware and session-aware instead of pinned to a single worker UUID.
- Terminal success and terminal failure both clear the active session.
- The design remains compatible with the current single-active-pipeline behavior.

---

## 3. Architectural Summary

### 3.1 High-Level Design

```text
UI / Use Case / Orchestrator
    -> DownloadWorkSchedulerPort.enqueue(DownloadWorkRequest)
        -> WorkManager unique chain
            -> ModelDownloadWorker
            -> DownloadFinalizeWorker
```

### 3.2 Ownership Boundaries

**`:domain`**

- owns the request and descriptor models
- owns scheduler port
- owns use cases such as `SyncLocalModelRegistryUseCase`
- owns repository ports

**`:data`**

- owns both workers
- owns WorkManager integration
- owns download progress parsing and chain observation
- owns any helper used for orphaned file cleanup

**`:app`**

- owns progress display and user actions
- does not own final persistence side effects

### 3.3 Clean Architecture Intent

This plan preserves the dependency rule:

- `:domain` declares ports and pure request models
- `:data` implements the WorkManager-backed scheduling and worker execution
- `:app` presents state without owning repository mutation

The design explicitly avoids placing repository mutation in screen-scoped ViewModels.

---

## 4. Target Files

| File | Change Type | Description |
|------|-------------|-------------|
| `core/domain/.../DownloadFileSpec.kt` | New | Pure transfer descriptor used by `ModelDownloadWorker`. |
| `core/domain/.../DownloadRequestKind.kt` | New | Enumerates `INITIALIZE_MODELS` and `RESTORE_SOFT_DELETED_MODEL`. |
| `core/domain/.../DownloadWorkRequest.kt` | New | Scheduler request model containing files, request kind, session id, target model id, and constraints. |
| `core/domain/.../ScheduledDownload.kt` | New | Optional return model for callers that need the scheduled session id. Useful for re-download UI observation. |
| `core/domain/.../DownloadWorkSchedulerPort.kt` | Major refactor | Replace ad hoc enqueue helpers with a structured request entry point. |
| `core/domain/.../ReDownloadModelUseCase.kt` | Major refactor | Stop restoring soft-deleted configs before download; schedule the chain instead. |
| `core/data/.../download/ModelDownloadWorker.kt` | Major refactor | Pure transfer worker; emit request metadata and `downloaded_shas`. |
| `core/data/.../download/DownloadFinalizeWorker.kt` | New | Own background repository mutation and request-specific finalization. |
| `core/data/.../download/DownloadWorkScheduler.kt` | Major refactor | Build and enqueue chained work instead of a single worker. |
| `core/data/.../download/ModelDownloadOrchestratorImpl.kt` | Major refactor | Remove repository finalization logic; remain startup state machine and scheduling coordinator. |
| `core/data/.../download/WorkProgressParser.kt` | Major refactor | Parse chain stage and treat finalizer completion as terminal success. |
| `core/data/.../repository/DownloadWorkRepository.kt` | Major refactor | Observe the active unique work chain, not only a single worker UUID. |
| `core/data/.../download/OrphanedFileCleaner.kt` or similar helper | New or extracted | Encapsulate orphan cleanup used by finalizer. |
| `feature/download/.../DownloadViewModel.kt` | Moderate | Observe chain-aware startup progress and stop assuming screen-owned finalization. |
| `feature/settings/.../ModelReDownloadViewModel.kt` | Moderate | Observe re-download progress by scheduled session rather than brittle string matching. |
| `core/data/.../DataModule.kt` | Moderate | Provide new worker/helper dependencies and remove obsolete orchestrator dependencies. |

---

## 5. New Domain Models

### 5.1 `DownloadFileSpec`

```kotlin
data class DownloadFileSpec(
    val remoteFileName: String,
    val localFileName: String,
    val sha256: String,
    val sizeInBytes: Long,
    val huggingFaceModelName: String,
    val source: String,
    val modelFileFormat: String,
    val mmprojRemoteFileName: String? = null,
    val mmprojLocalFileName: String? = null,
    val mmprojSha256: String? = null,
    val mmprojSizeInBytes: Long? = null,
)
```

Purpose:

- describe only what the transfer worker needs
- remove preset and configuration noise from worker input

Must not include:

- `modelType`
- prompt/system preset fields
- temperature/top-p/top-k/etc.
- any repository-only state

### 5.2 `DownloadRequestKind`

```kotlin
enum class DownloadRequestKind {
    INITIALIZE_MODELS,
    RESTORE_SOFT_DELETED_MODEL,
}
```

Purpose:

- tells the finalizer which business path to execute

### 5.3 `DownloadWorkRequest`

```kotlin
data class DownloadWorkRequest(
    val files: List<DownloadFileSpec>,
    val sessionId: String,
    val requestKind: DownloadRequestKind,
    val targetModelId: LocalModelId? = null,
    val wifiOnly: Boolean = true,
)
```

Rules:

- `targetModelId` must be null for `INITIALIZE_MODELS`
- `targetModelId` must be non-null for `RESTORE_SOFT_DELETED_MODEL`

### 5.4 Optional `ScheduledDownload`

This is recommended for callers that must render progress for a newly scheduled request.

```kotlin
data class ScheduledDownload(
    val sessionId: String,
    val requestKind: DownloadRequestKind,
    val targetModelId: LocalModelId? = null,
)
```

Use cases:

- `ModelReDownloadViewModel` can track the session it just scheduled
- startup flow can ignore it if not needed

---

## 6. Scheduler Contract

### 6.1 Port Contract

```kotlin
interface DownloadWorkSchedulerPort {
    fun enqueue(request: DownloadWorkRequest)
    fun cancel()
    suspend fun cleanupTempFiles()
}
```

This replaces the old contract that accepted a raw `Map<ModelType, LocalModelAsset>`.

Reason:

- the old contract exposes startup-specific shape
- the new contract is request-oriented and reusable by both flows

### 6.2 Scheduler Responsibilities

`DownloadWorkScheduler` must:

1. serialize `DownloadFileSpec` list into input `Data`
2. serialize request metadata:
   - `sessionId`
   - `requestKind`
   - `targetModelId`
3. create `ModelDownloadWorker` request with network/storage constraints
4. create `DownloadFinalizeWorker` request with request metadata
5. enqueue them as a unique sequential chain

### 6.3 WorkManager Chain

Recommended implementation:

```kotlin
val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
    .setConstraints(downloadConstraints)
    .setInputData(downloadInput)
    .addTag(ModelConfig.WORK_TAG)
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .build()

val finalizeRequest = OneTimeWorkRequestBuilder<DownloadFinalizeWorker>()
    .setInputData(finalizeInput)
    .addTag(ModelConfig.WORK_TAG)
    .build()

workManager
    .beginUniqueWork(ModelConfig.WORK_TAG, ExistingWorkPolicy.REPLACE, downloadRequest)
    .then(finalizeRequest)
    .enqueue()
```

Notes:

- WorkManager chaining passes parent output into child input.
- `OverwritingInputMerger` is the default; this is acceptable here because keys are intentionally unique and deterministic.
- `finalizeInput` must still include the static request metadata so finalization does not depend only on inherited output.

### 6.4 Single-Pipeline Scope

This spec keeps a single active unique pipeline.

Implications:

- startup downloads and re-downloads are mutually exclusive
- a second request replaces the current one if `REPLACE` remains policy
- callers must not assume parallel independent download chains

This is acceptable because the current app is already effectively single-pipeline.

---

## 7. Worker Input and Output Contracts

### 7.1 Shared Input Keys

Recommended scheduler keys:

- `KEY_SESSION_ID`
- `KEY_REQUEST_KIND`
- `KEY_TARGET_MODEL_ID`
- `KEY_DOWNLOAD_FILES`
- `KEY_WORKER_STAGE`
- `KEY_DOWNLOADED_SHAS`
- `KEY_USER_MESSAGE`
- `KEY_ERROR_MESSAGE`

### 7.2 `ModelDownloadWorker` Input

- serialized `DownloadFileSpec` array
- `sessionId`
- `requestKind`
- optional `targetModelId`

### 7.3 `ModelDownloadWorker` Output

On success:

```kotlin
workDataOf(
    KEY_SESSION_ID to sessionId,
    KEY_REQUEST_KIND to requestKind.name,
    KEY_TARGET_MODEL_ID to targetModelId,
    KEY_WORKER_STAGE to WorkerStage.DOWNLOAD.name,
    KEY_DOWNLOADED_SHAS to downloadedShas.toTypedArray(),
)
```

On terminal failure:

```kotlin
workDataOf(
    KEY_SESSION_ID to sessionId,
    KEY_REQUEST_KIND to requestKind.name,
    KEY_TARGET_MODEL_ID to targetModelId,
    KEY_WORKER_STAGE to WorkerStage.DOWNLOAD.name,
    KEY_ERROR_MESSAGE to errorMessage,
)
```

### 7.4 `DownloadFinalizeWorker` Input

Static input from scheduler:

- `sessionId`
- `requestKind`
- optional `targetModelId`

Inherited input from chained parent output:

- `downloaded_shas`
- `worker_stage = DOWNLOAD`

The finalizer should not trust `worker_stage = DOWNLOAD` as its own stage. It should set its own output stage explicitly.

### 7.5 `DownloadFinalizeWorker` Output

On success:

```kotlin
workDataOf(
    KEY_SESSION_ID to sessionId,
    KEY_REQUEST_KIND to requestKind.name,
    KEY_TARGET_MODEL_ID to targetModelId,
    KEY_WORKER_STAGE to WorkerStage.FINALIZE.name,
    KEY_DOWNLOADED_SHAS to downloadedShas.toTypedArray(),
)
```

On success with user-facing message:

```kotlin
workDataOf(
    KEY_SESSION_ID to sessionId,
    KEY_REQUEST_KIND to requestKind.name,
    KEY_TARGET_MODEL_ID to targetModelId,
    KEY_WORKER_STAGE to WorkerStage.FINALIZE.name,
    KEY_DOWNLOADED_SHAS to downloadedShas.toTypedArray(),
    KEY_USER_MESSAGE to "Download failed. Using existing model versions.",
)
```

On terminal failure:

```kotlin
workDataOf(
    KEY_SESSION_ID to sessionId,
    KEY_REQUEST_KIND to requestKind.name,
    KEY_TARGET_MODEL_ID to targetModelId,
    KEY_WORKER_STAGE to WorkerStage.FINALIZE.name,
    KEY_ERROR_MESSAGE to errorMessage,
)
```

---

## 8. `ModelDownloadWorker` Responsibilities

### 8.1 Functional Requirements

The worker must:

- parse `DownloadFileSpec` input
- deduplicate physical downloads by SHA
- download all required artifacts for each unique SHA
- support resumable partial downloads using existing temp file size
- continue emitting throttled progress via WorkManager `Data`
- return `downloaded_shas` for all assets that completed in this run

### 8.2 Explicit Non-Responsibilities

The worker must not:

- call `SyncLocalModelRegistryUseCase`
- call `restoreSoftDeletedModel`
- inspect or mutate Room entities
- decide how startup and re-download differ at the business level
- cleanup orphaned model files

### 8.3 Suggested Implementation Shape

```kotlin
override suspend fun doWork(): Result {
    val requestContext = parseRequestContext(inputData) ?: return failure("Missing request context")
    val downloadSpecs = parseDownloadSpecs(inputData) ?: return failure("Missing download specs")

    val uniqueSpecs = downloadSpecs.distinctBy { it.sha256 }
    val completedShas = linkedSetOf<String>()

    try {
        uniqueSpecs.forEach { spec ->
            downloadSpec(spec)
            completedShas += spec.sha256
        }

        return Result.success(
            workDataOf(
                KEY_SESSION_ID to requestContext.sessionId,
                KEY_REQUEST_KIND to requestContext.requestKind.name,
                KEY_TARGET_MODEL_ID to requestContext.targetModelId?.value,
                KEY_WORKER_STAGE to WorkerStage.DOWNLOAD.name,
                KEY_DOWNLOADED_SHAS to completedShas.toTypedArray(),
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        return Result.retry()
    } catch (e: Exception) {
        return failure(e.message ?: "Download failed")
    }
}
```

The exact helper names can differ, but the responsibility split should not.

---

## 9. `DownloadFinalizeWorker` Responsibilities

### 9.1 Functional Requirements

The finalizer must:

- run only after successful byte transfer
- read `requestKind`
- read `downloaded_shas`
- execute the matching business finalization path
- return terminal success/failure output for parser consumption

### 9.2 Startup Finalization Algorithm

For `INITIALIZE_MODELS`:

1. Ensure `downloaded_shas` is not empty.
2. Fetch remote config using `ModelConfigFetcherPort`.
3. Filter remote entries whose asset SHA is in `downloaded_shas`.
4. For each matching `(modelType, asset)`:
   - invoke `SyncLocalModelRegistryUseCase(modelType, asset)`
5. After sync, load all local assets from repository.
6. Run orphan cleanup based on the fully updated registry.

Reasoning:

- slot activation belongs to the startup scenario only
- orphan cleanup must happen after the authoritative local registry is up to date

### 9.3 Soft-Deleted Re-Download Finalization Algorithm

For `RESTORE_SOFT_DELETED_MODEL`:

1. Require `targetModelId`.
2. Load the soft-deleted asset by `targetModelId`.
3. Fetch remote config.
4. Find remote asset whose SHA matches the soft-deleted asset SHA.
5. Rebuild the system preset configurations for the target model id.
6. Invoke `restoreSoftDeletedModel(targetModelId, rebuiltConfigs)`.

Do not:

- automatically assign a role slot
- call `SyncLocalModelRegistryUseCase` unless product requirements are explicitly changed

### 9.4 Suggested Worker Structure

```kotlin
override suspend fun doWork(): Result {
    val requestKind = parseRequestKind(inputData) ?: return failure("Missing request kind")
    val sessionId = inputData.getString(KEY_SESSION_ID) ?: return failure("Missing session id")
    val downloadedShas = inputData.getStringArray(KEY_DOWNLOADED_SHAS)?.toSet().orEmpty()

    return try {
        when (requestKind) {
            DownloadRequestKind.INITIALIZE_MODELS -> finalizeStartup(downloadedShas)
            DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL -> {
                val targetModelId = requireNotNull(inputData.getString(KEY_TARGET_MODEL_ID)) {
                    "Missing target model id"
                }
                finalizeSoftDeletedRestore(LocalModelId(targetModelId))
            }
        }

        Result.success(
            workDataOf(
                KEY_SESSION_ID to sessionId,
                KEY_REQUEST_KIND to requestKind.name,
                KEY_WORKER_STAGE to WorkerStage.FINALIZE.name,
                KEY_DOWNLOADED_SHAS to downloadedShas.toTypedArray(),
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Result.retry()
    } catch (e: Exception) {
        failure(e.message ?: "Finalization failed")
    }
}
```

### 9.5 Idempotency Requirements

The finalizer must be safe to retry.

That means:

- repeated `SyncLocalModelRegistryUseCase` calls for the same slot/SHA must converge
- repeated `restoreSoftDeletedModel(modelId, rebuiltConfigs)` must converge
- orphan cleanup must be safe to run multiple times

---

## 10. Orchestrator Changes

### 10.1 What Leaves `ModelDownloadOrchestratorImpl`

Remove:

- startup registry finalization
- fallback-only post-failure repository inspection
- orphaned file cleanup

These responsibilities move to `DownloadFinalizeWorker`.

### 10.2 What Stays in `ModelDownloadOrchestratorImpl`

Keep:

- startup state machine
- startup `sessionId` creation
- initialization with `DownloadModelsResult`
- pause/resume/cancel/retry orchestration
- conversion from `DownloadModelsResult` to `DownloadWorkRequest`

### 10.3 Startup Request Construction

`ModelDownloadOrchestratorImpl.startDownloads(modelsResult, wifiOnly)` should:

1. create a new `sessionId`
2. build `DownloadFileSpec` list from the subset of assets that physically need download
3. enqueue:
   - `requestKind = INITIALIZE_MODELS`
   - `targetModelId = null`
4. move UI state to `DOWNLOADING`

### 10.4 Resume Behavior

Resume should:

- create a new session
- build the same startup request from `startupModelsResult`
- enqueue the chain again

This preserves the existing “cancel and re-enqueue” mental model.

---

## 11. Re-Download Use Case Changes

### 11.1 Behavioral Change

`ReDownloadModelUseCase` must stop doing this before download:

- rebuilding configs
- calling `restoreSoftDeletedModel`

Instead, it should:

1. load the soft-deleted asset
2. build a `DownloadFileSpec` for that asset
3. create a new `sessionId`
4. enqueue a request with:
   - `requestKind = RESTORE_SOFT_DELETED_MODEL`
   - `targetModelId = modelId`
5. return `ScheduledDownload(sessionId, requestKind, targetModelId)`

### 11.2 Recommended Signature

```kotlin
class ReDownloadModelUseCase @Inject constructor(
    private val localModelRepository: LocalModelRepositoryPort,
    private val downloadWorkScheduler: DownloadWorkSchedulerPort,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(modelId: LocalModelId): Result<ScheduledDownload>
}
```

Remote config lookup is intentionally moved out of this use case and into the finalizer.

Reason:

- the re-download request only needs to know what file to fetch
- restoration policy belongs to post-download finalization

---

## 12. Observation and Parsing Changes

### 12.1 Repository Contract Change

`DownloadWorkRepository` should stop exposing only “observe this UUID.”

It should expose chain-aware observation for the current unique work:

- observe all `WorkInfo` entries under `ModelConfig.WORK_TAG`
- resolve the active or terminal member of the chain for the requested session

### 12.2 Why UUID-Centric Observation Is Wrong After This Refactor

If the UI observes only the UUID of `ModelDownloadWorker`:

- it will never see `DownloadFinalizeWorker` running
- it may treat downloader success as final app-ready success
- it cannot distinguish terminal success of the chain from intermediate success of the first worker

That would produce incorrect READY/ERROR transitions.

### 12.3 Parser Requirements

`WorkProgressParser` must be updated to:

- inspect `worker_stage`
- inspect `request_kind`
- carry `sessionId`
- parse `downloaded_shas` when present
- set `clearSession = true` on both terminal success and terminal failure

### 12.4 Parser Behavior Matrix

| Work state | Worker stage | Parser output |
|------------|--------------|---------------|
| `RUNNING` | `DOWNLOAD` | `DOWNLOADING` with file progress |
| `RUNNING` | `FINALIZE` | preserve current file list, keep progress at 100%, show finalization-friendly status text if desired |
| `SUCCEEDED` | `DOWNLOAD` | non-terminal intermediate success; do not emit `READY` |
| `SUCCEEDED` | `FINALIZE` | terminal `READY`, `clearSession = true` |
| `FAILED` | `DOWNLOAD` | terminal `ERROR`, `clearSession = true` |
| `FAILED` | `FINALIZE` | terminal `ERROR`, `clearSession = true` |
| `CANCELLED` | either | `PAUSED` or `IDLE` depending on existing UX contract |

### 12.5 Progress UI Compatibility

This spec intentionally avoids a large UI rewrite.

During finalizer execution, the parser may:

- keep `status = DOWNLOADING`
- preserve the last file list
- keep `overallProgress = 1.0f`
- optionally set `estimatedTimeRemaining = "Finalizing…"`

That preserves current UI shape while still reflecting that the chain is not complete until finalization succeeds.

---

## 13. ViewModel Implications

### 13.1 `DownloadViewModel`

Required changes:

- continue to observe progress
- stop assuming repository mutation is screen-owned
- rely on chain-aware READY/ERROR coming from parser/orchestrator

It remains a startup progress presenter, not a finalizer.

### 13.2 `ModelReDownloadViewModel`

Required changes:

- stop assuming WorkManager success means the only work is done
- track progress using the scheduled `sessionId`
- stop parsing `"unassigned"` as the primary source of truth
- mark completion only when the chain reaches terminal success for that session

It remains a UI progress presenter for re-download, not the owner of restoration logic.

---

## 14. Error Handling

### 14.1 `ModelDownloadWorker`

Return:

- `Result.retry()` for transient IO/network failures
- `Result.failure()` for malformed input or unrecoverable invariants

Always rethrow:

- `CancellationException`

### 14.2 `DownloadFinalizeWorker`

Return:

- `Result.retry()` for transient remote-config fetch failures or retryable IO
- `Result.failure()` for unrecoverable invariants such as:
  - missing `targetModelId` on restore request
  - requested soft-deleted asset not found
  - remote config no longer contains matching asset

Always rethrow:

- `CancellationException`

### 14.3 Failure Output Normalization

Both workers must include:

- `sessionId`
- `requestKind`
- `workerStage`
- `errorMessage`

This ensures the parser has enough context to produce a terminal error update and clear the session deterministically.

---

## 15. Detailed Migration Order

1. Add domain models:
   - `DownloadFileSpec`
   - `DownloadRequestKind`
   - `DownloadWorkRequest`
   - optionally `ScheduledDownload`
2. Refactor `DownloadWorkSchedulerPort`.
3. Refactor `DownloadWorkScheduler` to build the two-step chain.
4. Refactor `ModelDownloadWorker` to emit request metadata and `downloaded_shas`.
5. Add `DownloadFinalizeWorker`.
6. Move startup finalization out of `ModelDownloadOrchestratorImpl`.
7. Refactor `ReDownloadModelUseCase` to schedule the same pipeline and stop restoring early.
8. Refactor `DownloadWorkRepository` to observe the active unique chain.
9. Refactor `WorkProgressParser` to understand chain stage and terminal semantics.
10. Update `DownloadViewModel` and `ModelReDownloadViewModel` to consume session-aware chain progress.
11. Update tests for both request kinds and chain behavior.

---

## 16. Non-Goals

- no Room schema changes
- no move of repository mutation into ViewModels
- no change to the project’s single-active-download assumption
- no new multi-download concurrency model
- no change to on-device inference architecture

---

## 17. Final Recommendation

The system should not be refactored toward “UI-owned completion.”

It should be refactored toward:

- one generic byte-transfer worker
- one background finalizer worker
- one structured request model that covers both startup and re-download
- one chain-aware progress observation model

That is the minimal architecture that solves the actual problem without creating a lifecycle bug.
