# Discovery Report: Download Worker Refactor
## Unifying Startup Downloads and Re-Download Through a Background-Owned Work Chain

### Executive Summary

The previous version of this plan moved post-download repository mutation into screen-specific ViewModels. That is not viable in Pocket Crew.

Why it is not viable:

- The startup download screen is transient. Once the app reaches `READY`, the route is popped and the screen ViewModel is destroyed.
- The settings re-download flow lives in a bottom sheet and a screen-scoped ViewModel. It is even less reliable as the owner of critical persistence work.
- Repository mutation must survive navigation changes, process death, and UI teardown.

The correct architecture is:

1. `ModelDownloadWorker` remains the pure byte-transfer worker.
2. A new `DownloadFinalizeWorker` runs immediately after successful download work.
3. The finalizer owns all background-safe repository mutation and filesystem cleanup.
4. Both startup downloads and re-downloads share the same download worker, and differ only in finalization strategy.

This design keeps the UI responsible for progress presentation and user actions, while keeping stateful persistence work in the background where it belongs.

---

## 1. Current Architecture

### 1.1 Startup Flow Today

`MainViewModel`
  → `InitializeModelsUseCase`
    → fetch remote config
    → detect soft-deleted models
    → `CheckModelsUseCase`
    → immediately `SyncLocalModelRegistryUseCase` for assets already present on disk
    → `ModelDownloadOrchestratorPort.initializeWithStartupResult(result)`
  → `AppStartupState.Ready(modelsResult)`
  → navigation starts on `MODEL_DOWNLOAD` when files are missing

`DownloadViewModel`
  → asks `ModelDownloadOrchestratorPort.startDownloads(modelsResult, wifiOnly)`
  → `ModelDownloadOrchestratorImpl`
    → creates `sessionId`
    → serializes full `LocalModelAsset` payloads into WorkManager input
    → `DownloadWorkScheduler.enqueue(...)`

`ModelDownloadWorker`
  → parses model JSON back into `LocalModelAsset`
  → downloads one or more unique physical assets
  → emits progress through `setProgress()`
  → returns `Result.success(sessionId)` or `Result.retry()` / `Result.failure()`

`DownloadViewModel.observeWorkProgress()`
  → gets a single `workId`
  → observes `DownloadWorkRepository.observeDownloadProgress(workId)`
  → parses `WorkInfo` with `WorkProgressParser`
  → sends `DownloadProgressUpdate` into `ModelDownloadOrchestratorImpl.updateFromProgressUpdate()`

`ModelDownloadOrchestratorImpl.updateFromProgressUpdate()`
  → applies state updates
  → on terminal success, it also:
    → activates newly downloaded models via `SyncLocalModelRegistryUseCase`
    → cleans orphaned files from disk
  → on terminal failure, it attempts fallback logic and snackbar emission

### 1.2 Re-Download Flow Today

`ModelReDownloadViewModel`
  → calls `ReDownloadModelUseCase(modelId)`

`ReDownloadModelUseCase`
  → loads soft-deleted asset from repository
  → fetches remote config
  → matches asset by SHA
  → re-creates system preset configurations
  → calls `localModelRepository.restoreSoftDeletedModel(...)` before bytes are downloaded
  → schedules download work with `ModelType.UNASSIGNED`

`ModelReDownloadViewModel.observeDownloadProgress()`
  → polls for a single global `workId`
  → watches WorkManager directly
  → infers progress by string-matching `"unassigned"` in `files_progress`
  → marks UI complete on `SUCCEEDED`
  → performs no repository finalization after the bytes land

### 1.3 Observation Pipeline Today

There are two important mechanics in the current implementation:

- Session filtering is done in `WorkProgressParser` using `DownloadSessionManager.isSessionStale(workSessionId)`.
- The UI observes a single concrete work UUID, not the unique work chain as a whole.

That observation model is acceptable for one worker. It is not sufficient for a two-worker chain unless the repository and parser are updated accordingly.

---

## 2. What We Learned After Reviewing the Code

### 2.1 Screen ViewModels Cannot Own Finalization

This is the key correction to the previous plan.

`ModelDownloadScreen` navigates away when `downloadState.status == READY`. The route is intentionally popped as part of normal startup completion. Any critical repository mutation placed in `DownloadViewModel` would be coupled to a screen that is expected to disappear.

`LocalModelsBottomSheet` injects `ModelReDownloadViewModel` directly into a sheet-scoped UI. That ViewModel is appropriate for progress UI, but not for the only copy of restore/finalization logic.

Conclusion:

- UI may observe progress.
- UI may display completion and failure.
- UI must not be the only owner of background completion logic.

### 2.2 The Real Problem Is Not “Worker vs ViewModel”

The real problem is:

How do we let one generic download engine support two different post-download business outcomes?

Those outcomes are:

- Startup path: activate downloaded models into their role slots, then cleanup orphaned files.
- Re-download path: restore a soft-deleted asset’s configurations after bytes are present again, without implicitly reassigning it to a role slot.

That is a classic “shared transport, different finalizers” problem.

### 2.3 A Worker Chain Fits the Android Model

Android’s WorkManager explicitly supports chaining one-time work. Parent output becomes child input, merged using an `InputMerger`; `OverwritingInputMerger` is the default.

That is exactly what we need:

- worker 1 transfers bytes and outputs `downloaded_shas`
- worker 2 consumes `downloaded_shas` plus request metadata and applies business finalization

### 2.4 The Current Failure Path Has a Hidden Gap

`ModelDownloadOrchestratorImpl` only performs failure-side follow-up when `update.clearSession` is true.

`WorkProgressParser.parseFailed()` currently returns `DownloadProgressUpdate(status = ERROR, ...)` without `clearSession = true`.

That means the current failure follow-up path is incomplete:

- stale session cleanup does not happen on failed terminal work
- failure-side orchestrator logic is not reached through the same gate used for success

The rewritten plan needs to normalize terminal success and terminal failure handling.

---

## 3. Problems in the Current Design

| ID | Problem | Why it matters |
|----|---------|----------------|
| P1 | Startup repository finalization currently lives in `ModelDownloadOrchestratorImpl`. | It couples transfer orchestration with persistence side effects and prevents the download pipeline from being reused cleanly. |
| P2 | Re-download restores repository state before download starts. | The DB can claim a model is restored while the physical file is still missing or the download later fails. |
| P3 | Re-download completion performs no background finalization after bytes land. | Startup and re-download paths do not obey the same consistency rules. |
| P4 | The previous plan moved repository work into ViewModels. | That breaks lifecycle safety and makes completion dependent on UI presence. |
| P5 | Worker input is bloated with full config/preset data even though the byte-transfer worker does not use it. | This adds coupling and increases pressure on WorkManager `Data` payload size. |
| P6 | UI observation is tied to a single `workId`, not the unique work chain. | A two-worker chain would not be tracked correctly unless observation is updated. |
| P7 | Re-download progress currently depends on parsing `"unassigned"` from progress strings. | It is brittle and tied to an implementation detail instead of explicit request metadata. |
| P8 | Session handling differs by path: startup uses a session, re-download currently does not. | The staleness model is split across two incompatible flows. |
| P9 | Terminal failure handling is incomplete because failed work does not currently request session clearing through the parser. | Retry/error state can drift from actual background state. |

---

## 4. Target Architecture

### 4.1 Core Principle

The system should be modeled as a single background-owned pipeline:

`DownloadRequest`
  → `ModelDownloadWorker`
  → `DownloadFinalizeWorker`

The shared worker does one thing:

- ensure the requested artifacts exist on disk and pass integrity validation

The finalizer does the request-specific business work:

- startup finalization
- soft-delete restoration finalization

### 4.2 Request Kinds

The pipeline needs a durable request type so the finalizer can branch without UI involvement.

Recommended domain model:

```kotlin
enum class DownloadRequestKind {
    INITIALIZE_MODELS,
    RESTORE_SOFT_DELETED_MODEL,
}

data class DownloadWorkRequest(
    val files: List<DownloadFileSpec>,
    val sessionId: String,
    val requestKind: DownloadRequestKind,
    val targetModelId: LocalModelId? = null,
    val wifiOnly: Boolean = true,
)
```

Important note:

- `targetModelId` is required only for `RESTORE_SOFT_DELETED_MODEL`
- startup finalization does not need a model id; it derives work from `downloaded_shas`

### 4.3 Worker Responsibilities

#### `ModelDownloadWorker`

Owns:

- parse minimal `DownloadFileSpec` input
- deduplicate physical assets by SHA
- download artifacts and optional mmproj files
- stream progress through WorkManager `Data`
- return terminal output:
  - `sessionId`
  - `requestKind`
  - optional `targetModelId`
  - `downloaded_shas`
  - `worker_stage = DOWNLOAD`

Must not own:

- repository mutation
- role-slot activation
- restoration of soft-deleted configs
- orphaned file cleanup

#### `DownloadFinalizeWorker`

Owns:

- consume request metadata plus `downloaded_shas`
- execute request-specific repository/file finalization
- emit terminal output:
  - `sessionId`
  - `requestKind`
  - optional `targetModelId`
  - optional `user_message`
  - `worker_stage = FINALIZE`

This worker is the durable owner of all post-download business effects.

### 4.4 Finalization Strategy by Request Kind

#### `INITIALIZE_MODELS`

The finalizer should:

1. Read `downloaded_shas` from upstream worker output.
2. Fetch the latest remote config.
3. Select the remote model slots whose asset SHA is in `downloaded_shas`.
4. Call `SyncLocalModelRegistryUseCase(modelType, asset)` for each matching slot.
5. Run orphan cleanup after registry sync.

Reasoning:

- The startup path is slot-oriented, not asset-id-oriented.
- `SyncLocalModelRegistryUseCase` is already the correct domain entry point for slot activation.
- Orphan cleanup should remain in background execution because it is part of persistence reconciliation, not UI.

#### `RESTORE_SOFT_DELETED_MODEL`

The finalizer should:

1. Require `targetModelId`.
2. Load the soft-deleted asset metadata from `LocalModelRepositoryPort.getAssetById(modelId)`.
3. Fetch remote config.
4. Find the remote asset that matches the target asset SHA.
5. Rebuild system preset configurations for that `modelId`.
6. Call `restoreSoftDeletedModel(modelId, restoredConfigs)`.

What it should not do by default:

- It should not automatically call `SyncLocalModelRegistryUseCase`.

Reason:

- Re-download restores a previously deleted asset to availability.
- That is not the same thing as assigning it to a role slot.
- Startup activation and re-download restoration are different business actions and should stay separate unless product requirements explicitly change.

### 4.5 Scheduling Model

The scheduler should enqueue a chain, not a single worker:

```kotlin
val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
    .setInputData(downloadInput)
    .setConstraints(downloadConstraints)
    .build()

val finalizeRequest = OneTimeWorkRequestBuilder<DownloadFinalizeWorker>()
    .setInputData(finalizeInput)
    .build()

workManager
    .beginUniqueWork(ModelConfig.WORK_TAG, ExistingWorkPolicy.REPLACE, downloadRequest)
    .then(finalizeRequest)
    .enqueue()
```

Important implementation detail:

- Parent output is made available to child input by WorkManager chaining.
- The child should also receive explicit static request metadata in its own input so it does not rely only on parent output.

### 4.6 Observation Must Move From “Single UUID” to “Active Chain”

This is the second major correction to the prior plan.

Today:

- `DownloadWorkRepository.getWorkId()` picks a single concrete UUID
- `observeDownloadProgress(workId)` only watches that UUID

That does not work once the chain advances from the download worker to the finalize worker.

Required redesign:

- observe the unique work chain itself via `getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG)`
- select the active or terminal `WorkInfo` for the current `sessionId`
- parse worker stage explicitly

Selection rules should prefer:

1. running finalizer for current session
2. running downloader for current session
3. enqueued/blocked chain member for current session
4. terminal finalizer for current session
5. terminal downloader only if no finalizer exists

### 4.7 Parser Must Distinguish Worker Stage

Once the chain exists, the parser cannot treat every `SUCCEEDED` as “downloads are ready.”

It must differentiate:

- `DOWNLOAD` worker success:
  - intermediate success
  - not terminal for business state
- `FINALIZE` worker success:
  - terminal success
  - emit `READY`
- `DOWNLOAD` worker failure:
  - terminal download failure
  - emit `ERROR` and clear session
- `FINALIZE` worker failure:
  - terminal finalization failure
  - emit `ERROR` and clear session

Recommended extra output/input keys:

- `worker_stage`
- `request_kind`
- `target_model_id`
- `downloaded_shas`
- `user_message`

### 4.8 Session Model Must Be Unified

Both request kinds must use the same session model.

Rules:

- every new pipeline creates a `sessionId`
- both workers carry that `sessionId`
- parser uses the same stale-session check for both request kinds
- terminal success and terminal failure both clear the session

This removes the current split where startup is session-aware and re-download is not.

### 4.9 Concurrency Scope

The current app effectively behaves as a single active download pipeline.

This plan keeps that invariant:

- one unique work chain at a time
- startup downloads and re-downloads are mutually exclusive
- a caller that wants a new pipeline while one is active must explicitly cancel or wait

This is deliberate. It avoids broadening the design into “multiple simultaneous download pipelines” while the rest of the UI, repository observation, and pause/cancel semantics are still single-pipeline.

---

## 5. Detailed File Impact

### 5.1 Files That Must Change

| File | Change | Why |
|------|--------|-----|
| `core/data/.../ModelDownloadWorker.kt` | Refactor | Strip repository-facing payload concerns; emit request metadata and `downloaded_shas`. |
| `core/data/.../DownloadFinalizeWorker.kt` | New | Durable owner of repository/file finalization. |
| `core/data/.../DownloadWorkScheduler.kt` | Refactor | Enqueue a two-step chain instead of one worker. |
| `core/domain/.../DownloadWorkSchedulerPort.kt` | Refactor | Accept a structured `DownloadWorkRequest` instead of raw map parameters. |
| `core/domain/.../ReDownloadModelUseCase.kt` | Refactor | Stop restoring before download; enqueue structured pipeline and return session-aware scheduling result. |
| `core/data/.../ModelDownloadOrchestratorImpl.kt` | Refactor | Remove repository finalization responsibilities; remain the startup state machine and scheduler entry point. |
| `core/data/.../WorkProgressParser.kt` | Refactor | Parse worker stage and chain semantics. |
| `core/data/.../repository/DownloadWorkRepository.kt` | Refactor | Observe the active unique work chain rather than a single fixed UUID. |
| `feature/download/.../DownloadViewModel.kt` | Refactor | Continue to present startup progress, but stop owning repository mutation assumptions. |
| `feature/settings/.../ModelReDownloadViewModel.kt` | Refactor | Track session-scoped re-download progress without owning restoration logic. |
| `core/data/.../download/OrphanedFileCleaner.kt` or equivalent helper | New or extracted | Keep filesystem cleanup logic out of the transport worker while still background-owned. |

### 5.2 Files That Should Not Carry Business Finalization After Refactor

- `DownloadViewModel`
- `ModelReDownloadViewModel`
- `ModelDownloadScreen`
- `LocalModelsBottomSheet`

These components may observe and render state only.

---

## 6. Why This Plan Solves Both Scenarios

### Startup Scenario

The app needs:

- a background-safe way to install bytes
- a background-safe way to activate downloaded slots
- a background-safe way to cleanup filesystem drift

The chain provides all three without coupling them to a screen.

### Re-Download Scenario

The app needs:

- the same byte-transfer worker
- a different post-download business action
- no premature DB mutation

The finalizer branches by `requestKind`, so re-download gets:

- the same transfer engine
- a dedicated restoration path
- no early call to `restoreSoftDeletedModel`

This is the right reuse boundary.

---

## 7. Migration Guidance

Recommended sequence:

1. Introduce `DownloadRequestKind`, `DownloadFileSpec`, and `DownloadWorkRequest`.
2. Refactor `DownloadWorkSchedulerPort` and `DownloadWorkScheduler` to build a two-step chain.
3. Keep `ModelDownloadWorker` focused on transfer and output metadata.
4. Add `DownloadFinalizeWorker`.
5. Move startup finalization from orchestrator to the finalizer worker.
6. Change `ReDownloadModelUseCase` so it schedules the same pipeline and does not restore early.
7. Refactor `DownloadWorkRepository` and `WorkProgressParser` to understand chain stage and session.
8. Update both ViewModels to observe the new chain semantics.

---

## 8. Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Child worker not receiving the right request metadata | Finalizer cannot branch correctly | Always pass static request metadata directly into finalizer input, even though parent output is also merged by chaining. |
| UI observation still pinned to the original download worker UUID | Finalizer stage becomes invisible | Replace UUID-centric observation with unique-work-chain observation. |
| Finalizer retries on remote config fetch create duplicate activation | Duplicate persistence side effects | Finalizer operations must be idempotent and keyed by current SHA/modelId state. |
| Re-download accidentally reassigns role slots | Behavioral regression | Keep restore logic separate from `SyncLocalModelRegistryUseCase` unless product explicitly wants reassignment. |
| Single-pipeline global unique work surprises callers | User-triggered replacement/cancel confusion | Document and enforce “one active pipeline at a time” in the scheduler and ViewModels. |
| Failure path still leaves session uncleared | UI drift and stale progress | Make terminal failure updates set `clearSession = true` through the parser. |

---

## 9. Final Recommendation

Do not move repository updates into ViewModels.

Implement a shared background pipeline with:

- `ModelDownloadWorker` for transfer
- `DownloadFinalizeWorker` for request-specific business finalization

That is the only design in this codebase that is:

- lifecycle-safe
- reusable across startup and re-download
- compatible with Clean Architecture boundaries
- compatible with WorkManager’s persistence model
