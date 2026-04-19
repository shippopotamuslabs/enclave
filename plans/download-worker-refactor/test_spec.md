# Test Specification: Download Worker Refactor
## TDD Red Phase for the Two-Worker Background Pipeline

---

## 1. Test Strategy Summary

The refactor changes the system from:

- one worker
- one fixed observed `workId`
- two inconsistent business paths

to:

- a chained two-worker pipeline
- session-aware unique-work observation
- one transfer worker and two finalization branches

That means the minimum sufficient test surface is not just “does the worker parse JSON.”

We need coverage for:

1. structured request serialization
2. transfer worker behavior
3. finalizer branch behavior
4. scheduler chain construction
5. parser stage awareness
6. repository chain observation
7. startup orchestration behavior
8. re-download behavior without premature restore
9. UI progress behavior for both flows

The most important principle:

- tests must verify the background-owned finalization model, not screen-owned side effects

---

## 2. Unit Tests — `ModelDownloadWorker`

### 2.1 File

`core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/ModelDownloadWorkerTest.kt`

This is the primary worker-behavior suite.

The legacy helper-only tests in `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/worker/ModelDownloadWorkerTest.kt` should either be removed or folded into the real worker suite once the new request contract is in place.

### 2.2 Parsing Tests

**Scenario: parses valid `DownloadFileSpec` input**
- Given input `Data` with a serialized `DownloadFileSpec`
- When the worker parses request input
- Then it reconstructs the expected descriptor values

**Scenario: parses multi-artifact descriptor**
- Given a descriptor with mmproj fields populated
- When the worker parses request input
- Then main and mmproj artifact fields are both preserved

**Scenario: fails on missing request context**
- Given input without `sessionId` or `requestKind`
- When `doWork()` starts
- Then the worker returns terminal failure with an error message

**Scenario: fails on malformed download spec payload**
- Given invalid serialized descriptor data
- When the worker parses input
- Then it returns terminal failure with parse error output

### 2.3 Download Behavior Tests

**Scenario: downloads a single descriptor successfully**
- Given a valid `DownloadFileSpec`
- And the downloader reports success
- When `doWork()` runs
- Then the file is downloaded
- And output includes the SHA in `downloaded_shas`

**Scenario: downloads multi-artifact asset successfully**
- Given a descriptor with main artifact and mmproj artifact
- When `doWork()` runs
- Then both artifacts are downloaded
- And the descriptor SHA is included exactly once in `downloaded_shas`

**Scenario: deduplicates duplicate physical assets by SHA**
- Given two descriptors with the same SHA but different logical callers
- When `doWork()` runs
- Then the physical file is downloaded once
- And the output contains that SHA once

**Scenario: skips already complete artifact**
- Given target files already exist at expected sizes
- When `doWork()` runs
- Then the downloader is not invoked for that artifact
- And the SHA is still included in `downloaded_shas`

**Scenario: resumes from temp file length**
- Given a temp file already exists with partial bytes
- When `doWork()` runs
- Then the worker passes `existingBytes` into the downloader

### 2.4 Progress Tests

**Scenario: emits throttled progress during active download**
- Given a long-running download
- When progress callbacks fire rapidly
- Then the worker throttles `setProgress()` calls
- And progress output still moves forward

**Scenario: progress output includes session and stage metadata**
- Given a running request
- When the worker emits progress
- Then the progress payload includes the expected request/session markers needed by the parser

### 2.5 Terminal Output Tests

**Scenario: success output includes session, request kind, stage, and downloaded SHAs**
- Given a successful download
- When `doWork()` returns success
- Then output includes:
  - `sessionId`
  - `requestKind`
  - `workerStage = DOWNLOAD`
  - `downloaded_shas`

**Scenario: failure output includes session, request kind, and stage**
- Given an unrecoverable download failure
- When `doWork()` returns failure
- Then output includes:
  - `sessionId`
  - `requestKind`
  - `workerStage = DOWNLOAD`
  - `error_message`

**Scenario: transient IO failure returns retry**
- Given a retryable network/IO failure
- When `doWork()` runs
- Then it returns `Result.retry()`

**Scenario: cancellation is rethrown**
- Given the downloader throws `CancellationException`
- When `doWork()` runs
- Then the worker rethrows cancellation instead of swallowing it

---

## 3. Unit Tests — `DownloadFinalizeWorker`

### 3.1 File

`core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadFinalizeWorkerTest.kt`

This is the most important new suite in the refactor.

### 3.2 Common Parsing and Guard Tests

**Scenario: fails when `requestKind` is missing**
- Given input without `requestKind`
- When `doWork()` runs
- Then the worker returns terminal failure

**Scenario: fails when `sessionId` is missing**
- Given input without `sessionId`
- When `doWork()` runs
- Then the worker returns terminal failure

**Scenario: success output includes finalizer stage**
- Given finalization succeeds
- When `doWork()` returns
- Then output includes `workerStage = FINALIZE`

### 3.3 Startup Finalization Tests

**Scenario: startup finalizer activates downloaded slots by SHA**
- Given `requestKind = INITIALIZE_MODELS`
- And `downloaded_shas` contains SHA A and SHA B
- And remote config contains slots whose assets match A and B
- When the finalizer runs
- Then `SyncLocalModelRegistryUseCase` is invoked for those slots only

**Scenario: startup finalizer ignores remote assets not downloaded in this session**
- Given remote config includes assets A, B, and C
- And `downloaded_shas` contains only A
- When the finalizer runs
- Then only A-backed slots are activated

**Scenario: startup finalizer runs orphan cleanup after registry sync**
- Given startup finalization succeeds
- When the finalizer finishes
- Then the cleanup helper runs after activation, not before

**Scenario: startup finalizer is idempotent under retry**
- Given the same startup finalizer input is executed twice
- When both runs complete
- Then registry state converges without duplicates
- And orphan cleanup remains safe

**Scenario: startup finalizer retries on remote config fetch IO failure**
- Given `ModelConfigFetcherPort.fetchRemoteConfig()` fails transiently
- When the finalizer runs
- Then it returns `Result.retry()`

### 3.4 Soft-Deleted Restore Finalization Tests

**Scenario: restore finalizer requires target model id**
- Given `requestKind = RESTORE_SOFT_DELETED_MODEL`
- And `targetModelId` is null
- When the finalizer runs
- Then it returns terminal failure

**Scenario: restore finalizer restores configs only after successful download**
- Given a soft-deleted asset exists in the repository
- And remote config contains a SHA-matching asset
- When the finalizer runs
- Then `restoreSoftDeletedModel(modelId, rebuiltConfigs)` is called

**Scenario: restore finalizer rebuilds system presets from remote config**
- Given remote config has multiple configurations for the matching asset
- When the finalizer runs
- Then the restored config list uses:
  - `localModelId = targetModelId`
  - `isSystemPreset = true`

**Scenario: restore finalizer does not call `SyncLocalModelRegistryUseCase`**
- Given restore finalization succeeds
- When the finalizer runs
- Then role-slot assignment is not performed

**Scenario: restore finalizer fails if soft-deleted asset no longer exists**
- Given `getAssetById(targetModelId)` returns null
- When the finalizer runs
- Then it returns terminal failure

**Scenario: restore finalizer fails if remote config no longer contains matching SHA**
- Given the target asset exists locally but remote config has no matching SHA
- When the finalizer runs
- Then it returns terminal failure

**Scenario: restore finalizer is idempotent under retry**
- Given the same restore finalizer input is executed twice
- When both runs complete
- Then restored repository state converges without duplicated configs

### 3.5 Terminal Output Tests

**Scenario: finalizer success output includes session and request metadata**
- Given finalization succeeds
- When output data is inspected
- Then output includes:
  - `sessionId`
  - `requestKind`
  - `workerStage = FINALIZE`

**Scenario: finalizer failure output includes error metadata**
- Given finalization fails permanently
- When output data is inspected
- Then output includes:
  - `sessionId`
  - `requestKind`
  - `workerStage = FINALIZE`
  - `error_message`

**Scenario: cancellation is rethrown**
- Given a dependency throws `CancellationException`
- When the finalizer runs
- Then cancellation is rethrown

---

## 4. Unit Tests — `DownloadWorkScheduler`

### 4.1 File

`core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadWorkSchedulerTest.kt`

### 4.2 Request Construction Tests

**Scenario: enqueues a two-worker chain for startup request**
- Given a `DownloadWorkRequest` with `requestKind = INITIALIZE_MODELS`
- When `enqueue(request)` is called
- Then WorkManager receives a unique work chain with:
  - `ModelDownloadWorker`
  - followed by `DownloadFinalizeWorker`

**Scenario: enqueues a two-worker chain for restore request**
- Given a `DownloadWorkRequest` with `requestKind = RESTORE_SOFT_DELETED_MODEL`
- When `enqueue(request)` is called
- Then the same two-worker chain is used

### 4.3 Serialization Tests

**Scenario: serializes `DownloadFileSpec` list into download worker input**
- Given a request with two file specs
- When `enqueue(request)` is called
- Then the download worker input contains both descriptors

**Scenario: serializes request context into both workers**
- Given a request with `sessionId`, `requestKind`, and `targetModelId`
- When `enqueue(request)` is called
- Then those values are present in:
  - download worker input
  - finalizer input

**Scenario: finalizer input includes static metadata even before parent output merge**
- Given any request
- When `enqueue(request)` is called
- Then finalizer input already includes enough request metadata to branch correctly

### 4.4 Constraint Tests

**Scenario: startup/re-download request uses unmetered network when `wifiOnly = true`**
- Given a wifi-only request
- When the scheduler builds constraints
- Then `NetworkType.UNMETERED` is required

**Scenario: request uses connected network when `wifiOnly = false`**
- Given a mobile-data-allowed request
- When the scheduler builds constraints
- Then `NetworkType.CONNECTED` is required

### 4.5 Policy Tests

**Scenario: global unique work name remains single-pipeline**
- Given any request
- When `enqueue(request)` is called
- Then the configured unique work name is the shared download pipeline name

**Scenario: cancel delegates to the unique work chain**
- Given active work
- When `cancel()` is called
- Then the unique chain is cancelled

---

## 5. Unit Tests — `WorkProgressParser`

### 5.1 File

`core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/download/WorkProgressParserTest.kt`

This suite must expand beyond file-progress string parsing.

### 5.2 Stage-Aware Running State Tests

**Scenario: running download worker maps to `DOWNLOADING`**
- Given `WorkInfo.State.RUNNING` and `workerStage = DOWNLOAD`
- When parsed
- Then the parser returns `DownloadStatus.DOWNLOADING`

**Scenario: running finalizer does not emit terminal success**
- Given `WorkInfo.State.RUNNING` and `workerStage = FINALIZE`
- When parsed
- Then the parser preserves non-terminal state
- And does not emit `READY`

### 5.3 Terminal Success Tests

**Scenario: succeeded download worker is treated as intermediate, not READY**
- Given `WorkInfo.State.SUCCEEDED` and `workerStage = DOWNLOAD`
- When parsed
- Then the parser does not emit final `READY`

**Scenario: succeeded finalizer emits READY**
- Given `WorkInfo.State.SUCCEEDED` and `workerStage = FINALIZE`
- When parsed
- Then the parser emits `DownloadStatus.READY`
- And `clearSession = true`

**Scenario: succeeded finalizer extracts `downloaded_shas`**
- Given finalizer output includes `downloaded_shas`
- When parsed
- Then the resulting update carries those SHAs for downstream consumers if needed

### 5.4 Terminal Failure Tests

**Scenario: failed download worker emits terminal error and clears session**
- Given `WorkInfo.State.FAILED` and `workerStage = DOWNLOAD`
- When parsed
- Then the parser emits `DownloadStatus.ERROR`
- And `clearSession = true`

**Scenario: failed finalizer emits terminal error and clears session**
- Given `WorkInfo.State.FAILED` and `workerStage = FINALIZE`
- When parsed
- Then the parser emits `DownloadStatus.ERROR`
- And `clearSession = true`

**Scenario: stale session success is ignored**
- Given output `sessionId` does not match current active session
- When a succeeded work item is parsed
- Then the parser returns null

**Scenario: stale session failure is ignored**
- Given output `sessionId` does not match current active session
- When a failed work item is parsed
- Then the parser returns null

### 5.5 File Progress Compatibility Tests

Keep and update the existing parsing tests for:

- six-part file progress strings
- blank `modelTypes`
- merging parsed model types with existing state
- filename-derived model type fallback

Those tests still matter because the running download worker continues to drive the file list UI.

---

## 6. Unit Tests — `DownloadWorkRepository`

### 6.1 File

`core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/DownloadWorkRepositoryTest.kt`

### 6.2 Chain Observation Tests

**Scenario: selects running finalizer over succeeded downloader in same session**
- Given unique work flow contains:
  - succeeded `ModelDownloadWorker`
  - running `DownloadFinalizeWorker`
- When chain observation resolves active work
- Then the finalizer work item is selected

**Scenario: selects running downloader when finalizer has not started yet**
- Given unique work flow contains only running download work for the current session
- When chain observation resolves active work
- Then that worker is selected

**Scenario: selects terminal finalizer when chain is complete**
- Given unique work flow contains a succeeded finalizer for the current session
- When chain observation resolves terminal work
- Then that finalizer is selected

**Scenario: ignores work from stale sessions**
- Given flow contains work from multiple sessions
- When observing the current session
- Then stale session items are ignored

**Scenario: returns null when no active or terminal chain member matches session**
- Given unique work flow contains no items for the session
- When observation runs
- Then it emits null

### 6.3 Backward-Compatibility Test

**Scenario: startup observation still works while only downloader is running**
- Given the chain has not advanced to finalizer
- When the repository resolves the current work
- Then startup progress remains visible without regressions

---

## 7. Unit Tests — `ModelDownloadOrchestratorImpl`

### 7.1 File

`core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/ModelDownloadOrchestratorImplTest.kt`

### 7.2 Startup Scheduling Tests

**Scenario: startDownloads creates `INITIALIZE_MODELS` request**
- Given startup models require download
- When `startDownloads(modelsResult, wifiOnly)` is called
- Then the scheduler receives a `DownloadWorkRequest` with:
  - `requestKind = INITIALIZE_MODELS`
  - non-null `sessionId`
  - null `targetModelId`

**Scenario: startDownloads converts only missing assets into file specs**
- Given startup result contains already-present assets and missing assets
- When startDownloads builds the request
- Then only the missing assets become `DownloadFileSpec` entries

**Scenario: orchestrator no longer owns repository finalization**
- Given the refactored constructor
- When dependencies are inspected
- Then it no longer depends on:
  - `LocalModelRepositoryPort`
  - `ActiveModelProviderPort`
  - `SyncLocalModelRegistryUseCase`

### 7.3 State Tests

**Scenario: successful chain completion still drives READY state**
- Given parser emits terminal READY from finalizer success
- When `updateFromProgressUpdate()` is called
- Then `downloadState.status` becomes `READY`

**Scenario: terminal failure clears session and updates ERROR state**
- Given parser emits terminal ERROR with `clearSession = true`
- When `updateFromProgressUpdate()` is called
- Then session is cleared
- And state becomes `ERROR`

**Scenario: pause cancels the unique chain**
- Given active download pipeline
- When `pauseDownloads()` is called
- Then scheduler cancel is invoked
- And status becomes `PAUSED`

---

## 8. Unit Tests — `ReDownloadModelUseCase`

### 8.1 File

`core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/modelconfig/ReDownloadModelUseCaseTest.kt`

### 8.2 Behavioral Change Tests

**Scenario: schedules restore request without restoring configs immediately**
- Given a soft-deleted asset exists
- When `invoke(modelId)` is called
- Then `restoreSoftDeletedModel()` is not called
- And the scheduler receives `requestKind = RESTORE_SOFT_DELETED_MODEL`

**Scenario: returns scheduled session metadata**
- Given scheduling succeeds
- When `invoke(modelId)` returns
- Then the result contains:
  - `sessionId`
  - `requestKind = RESTORE_SOFT_DELETED_MODEL`
  - `targetModelId = modelId`

**Scenario: builds file spec from soft-deleted asset**
- Given the repository returns a soft-deleted asset
- When the use case schedules re-download
- Then the request contains the expected `DownloadFileSpec`

**Scenario: fails when soft-deleted asset does not exist**
- Given `getAssetById(modelId)` returns null
- When `invoke(modelId)` is called
- Then the result is failure

### 8.3 Explicit Regression Test

**Scenario: no repository mutation occurs before bytes are downloaded**
- Given a successful scheduling call
- When the use case returns
- Then the repository has not yet been mutated by restoration logic

This test is mandatory. It guards the central correctness bug in the current re-download path.

---

## 9. Unit Tests — `DownloadViewModel`

### 9.1 File

`feature/download/src/test/kotlin/com/browntowndev/pocketcrew/feature/download/DownloadViewModelTest.kt`

### 9.2 Observation Tests

**Scenario: startup view model reacts to chain-aware progress updates**
- Given the repository/parser emit running download updates for the active session
- When the ViewModel observes progress
- Then `downloadState` and `fileProgressList` update correctly

**Scenario: startup view model reaches READY only after finalizer success**
- Given the downloader succeeds but the finalizer is still running
- When chain updates are observed
- Then the ViewModel does not navigate to READY yet

**Scenario: startup view model reaches READY on finalizer success**
- Given parser emits terminal READY from finalizer success
- When observed
- Then the ViewModel reports READY and navigation can proceed

**Scenario: startup view model shows error on finalizer failure**
- Given parser emits terminal ERROR from finalizer failure
- When observed
- Then the ViewModel reports ERROR

### 9.3 Existing UI Mapping Tests

Keep the existing `DownloadViewModelUiModelTest` coverage for:

- single-role display name mapping
- multi-role display name mapping

Those are still valid and should not be deleted.

---

## 10. Unit Tests — `ModelReDownloadViewModel`

### 10.1 File

`feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/ModelReDownloadViewModelTest.kt`

### 10.2 Scheduling Tests

**Scenario: re-download uses scheduled session result**
- Given `ReDownloadModelUseCase` returns `ScheduledDownload(sessionId, ...)`
- When `reDownloadModel(modelId)` is called
- Then the ViewModel tracks that session for progress observation

**Scenario: re-download no longer depends on `"unassigned"` string matching**
- Given chain progress for the scheduled session
- When the ViewModel observes updates
- Then progress is associated by session/request, not by brittle string parsing

### 10.3 Completion Tests

**Scenario: re-download reaches Complete only on finalizer success**
- Given download worker succeeds but finalizer is still running
- When updates are observed
- Then UI state does not reach `Complete`

**Scenario: re-download reaches Complete on restore finalizer success**
- Given finalizer succeeds for the tracked session
- When updates are observed
- Then state becomes `Complete`

**Scenario: re-download shows Failed on finalizer failure**
- Given finalizer fails for the tracked session
- When updates are observed
- Then state becomes `Failed`

### 10.4 Regression Tests

**Scenario: scheduling failure shows Failed**
- Given `ReDownloadModelUseCase` returns failure
- When `reDownloadModel(modelId)` is called
- Then the UI enters `Failed`

**Scenario: work never appears for scheduled session**
- Given a session is returned but the repository never resolves matching work
- When observation times out
- Then the UI enters `Failed`

---

## 11. Additional Helper/Component Tests

### 11.1 `OrphanedFileCleanerTest`

If orphan cleanup is extracted into its own helper:

`core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/OrphanedFileCleanerTest.kt`

Required scenarios:

- deletes files not present in registry-backed asset list
- preserves active files
- ignores temp files
- is safe to run twice

### 11.2 `DownloadSessionManagerTest`

Existing suite should be extended with:

- session created for re-download path
- stale-session logic behaves the same for both request kinds
- session is cleared on terminal failure as well as terminal success

---

## 12. Minimum Required Red Suite Before Production Changes

At minimum, the TDD red phase must contain failing tests for:

1. `DownloadFinalizeWorker` startup finalization
2. `DownloadFinalizeWorker` restore finalization
3. `DownloadWorkScheduler` chain construction
4. `WorkProgressParser` stage-aware terminal semantics
5. `DownloadWorkRepository` chain-aware observation
6. `ReDownloadModelUseCase` no-premature-restore behavior
7. `ModelReDownloadViewModel` completion only after finalizer success
8. `DownloadViewModel` READY only after finalizer success

If any of those are missing, the test plan is incomplete.

---

## 13. Out-of-Scope Tests

The following are not necessary for this refactor:

- Compose preview tests
- unrelated inference tests
- database schema migration tests
- end-to-end multi-download concurrency tests

Those can be added later if the product expands beyond a single active pipeline.

---

## 14. Final Sufficiency Check

This test spec is sufficient only if it proves all four of these:

1. repository mutation no longer depends on UI lifetime
2. startup and re-download both use the same transfer worker
3. the two scenarios diverge only in background finalization logic
4. the UI does not declare success until the full worker chain succeeds

If any one of those is untested, the refactor is under-specified.
