# TDD Red Briefing: Download Worker Refactor

## Phase: RED (Write Failing Tests First)

---

## Executive Summary

This refactor introduces a **two-worker WorkManager chain** to replace the current single-worker download system:

| Component | Before | After |
|-----------|--------|-------|
| Workers | `ModelDownloadWorker` (transfer + finalization) | `ModelDownloadWorker` (pure transfer) + `DownloadFinalizeWorker` (business logic) |
| Finalization owner | `ModelDownloadOrchestratorImpl` (screen-scoped) | Background-owned `DownloadFinalizeWorker` |
| Re-download | Pre-restore before bytes land | Background restore only after bytes present |
| Observation | Single UUID | Session-aware unique-work chain |

---

## 1. Core Problem Statement

**The current design has a critical lifecycle bug:**

- Startup downloads live in `DownloadViewModel` (transient screen scope)
- Re-download lives in `ModelReDownloadViewModel` (bottom sheet scope)
- Both own repository mutation that must survive UI teardown

**The fix:** Move all post-download business logic into a background worker that runs after successful byte transfer.

---

## 2. New Types to Introduce

### Domain Models (`:domain`)

```kotlin
// New enum
enum class DownloadRequestKind {
    INITIALIZE_MODELS,
    RESTORE_SOFT_DELETED_MODEL,
}

// New data class - what the transfer worker needs
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

// New - scheduler entry point
data class DownloadWorkRequest(
    val files: List<DownloadFileSpec>,
    val sessionId: String,
    val requestKind: DownloadRequestKind,
    val targetModelId: LocalModelId? = null,
    val wifiOnly: Boolean = true,
)

// Optional - for callers needing session info
data class ScheduledDownload(
    val sessionId: String,
    val requestKind: DownloadRequestKind,
    val targetModelId: LocalModelId? = null,
)
```

### WorkManager Keys

```kotlin
const val KEY_SESSION_ID = "session_id"
const val KEY_REQUEST_KIND = "request_kind"
const val KEY_TARGET_MODEL_ID = "target_model_id"
const val KEY_DOWNLOAD_FILES = "download_files"
const val KEY_WORKER_STAGE = "worker_stage"
const val KEY_DOWNLOADED_SHAS = "downloaded_shas"
const val KEY_USER_MESSAGE = "user_message"
const val KEY_ERROR_MESSAGE = "error_message"

enum class WorkerStage { DOWNLOAD, FINALIZE }
```

---

## 3. Worker Chain Design

```
[DownloadWorkScheduler]
        |
        v
[ModelDownloadWorker] ──► [DownloadFinalizeWorker]
   (byte transfer)            (business logic)
        |                           |
        v                           v
  Output:                        Branch by requestKind:
  - sessionId                   - INITIALIZE_MODELS:
  - requestKind                    → SyncLocalModelRegistryUseCase
  - downloaded_shas                → OrphanedFileCleaner
  - workerStage=DOWNLOAD         - RESTORE_SOFT_DELETED_MODEL:
                                   → restoreSoftDeletedModel()
```

---

## 4. Minimum Failing Tests (RED Phase)

The following tests **must fail** before implementation begins. Each test encodes a correctness requirement that the current codebase violates.

### 4.1 `DownloadFinalizeWorkerTest`

| Test ID | Scenario | Expected Failure Reason |
|---------|----------|--------------------------|
| `FINALIZE_01` | Startup finalizer activates downloaded slots by SHA | `DownloadFinalizeWorker` does not exist |
| `FINALIZE_02` | Startup finalizer runs orphan cleanup after registry sync | No cleanup in current flow |
| `FINALIZE_03` | Restore finalizer restores configs only after bytes present | Currently restores before download |
| `FINALIZE_04` | Restore finalizer does NOT call `SyncLocalModelRegistryUseCase` | Not yet separated |
| `FINALIZE_05` | Restore finalizer requires `targetModelId` | Not validated |
| `FINALIZE_06` | Both workers set `clearSession = true` on terminal output | Parser doesn't set this |

### 4.2 `DownloadWorkSchedulerTest`

| Test ID | Scenario | Expected Failure Reason |
|---------|----------|--------------------------|
| `SCHED_01` | Enqueues two-worker chain for startup request | Currently enqueues single worker |
| `SCHED_02` | Enqueues two-worker chain for restore request | Not yet implemented |
| `SCHED_03` | Finalizer input includes static metadata before parent merge | Not yet passed |
| `SCHED_04` | `cancel()` cancels the unique chain | Current cancel behavior differs |

### 4.3 `WorkProgressParserTest`

| Test ID | Scenario | Expected Failure Reason |
|---------|----------|--------------------------|
| `PARSER_01` | Succeeded download worker is NOT terminal | Current parser treats all SUCCEEDED as terminal |
| `PARSER_02` | Succeeded finalizer IS terminal (emits READY) | Finalizer doesn't exist |
| `PARSER_03` | Failed download worker clears session | Parser doesn't emit clearSession=true |
| `PARSER_04` | Failed finalizer clears session | Finalizer doesn't exist |

### 4.4 `DownloadWorkRepositoryTest`

| Test ID | Scenario | Expected Failure Reason |
|---------|----------|--------------------------|
| `REPO_01` | Selects running finalizer over succeeded downloader | Chain observation not implemented |
| `REPO_02` | Selects running downloader when finalizer not started | UUID-centric observation |
| `REPO_03` | Ignores work from stale sessions | Not session-aware |

### 4.5 `ReDownloadModelUseCaseTest`

| Test ID | Scenario | Expected Failure Reason |
|---------|----------|--------------------------|
| `REDOWN_01` | Does NOT call `restoreSoftDeletedModel()` before bytes land | Currently calls it immediately |
| `REDOWN_02` | Returns `ScheduledDownload` with session info | Doesn't exist |
| `REDOWN_03` | No repository mutation occurs before bytes are downloaded | Currently mutates eagerly |

### 4.6 `ModelReDownloadViewModelTest`

| Test ID | Scenario | Expected Failure Reason |
|---------|----------|--------------------------|
| `REDOWN_VM_01` | Reaches Complete only after finalizer success | Currently completes on downloader success |
| `REDOWN_VM_02` | No longer depends on `"unassigned"` string matching | Currently brittle string-parse |
| `REDOWN_VM_03` | Shows Failed when finalizer fails | Not chain-aware |

### 4.7 `DownloadViewModelTest`

| Test ID | Scenario | Expected Failure Reason |
|---------|----------|--------------------------|
| `DOWNLOAD_VM_01` | Reaches READY only after finalizer success | Current orchestrator owns finalization |
| `DOWNLOAD_VM_02` | Orchestrator no longer depends on repository ports | Currently coupled |

---

## 5. Test File Locations

| Test Class | Path |
|------------|------|
| `DownloadFinalizeWorkerTest` | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadFinalizeWorkerTest.kt` |
| `DownloadWorkSchedulerTest` | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadWorkSchedulerTest.kt` |
| `WorkProgressParserTest` | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/download/WorkProgressParserTest.kt` |
| `DownloadWorkRepositoryTest` | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/DownloadWorkRepositoryTest.kt` |
| `ReDownloadModelUseCaseTest` | `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/modelconfig/ReDownloadModelUseCaseTest.kt` |
| `ModelReDownloadViewModelTest` | `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/ModelReDownloadViewModelTest.kt` |
| `DownloadViewModelTest` | `feature/download/src/test/kotlin/com/browntowndev/pocketcrew/feature/download/DownloadViewModelTest.kt` |

---

## 6. RED Phase Acceptance Criteria

The RED phase is complete when:

- [ ] All 24 tests above are written and **failing**
- [ ] Tests use MockK for mocking
- [ ] Tests use Turbine for Flow assertions where applicable
- [ ] Each test has clear GIVEN/WHEN/THEN structure
- [ ] No implementation code is written yet (pure test authorship)

---

## 7. Key Assertions to Encode

### 7.1 Repository Mutation Timing

```kotlin
@Test
fun `restore finalizer restores configs only after bytes present`() {
    // GIVEN
    val downloadedShas = setOf("abc123")
    val inputData = workDataOf(
        KEY_REQUEST_KIND to DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL.name,
        KEY_TARGET_MODEL_ID to "model-123",
        KEY_DOWNLOADED_SHAS to downloadedShas.toTypedArray(),
    )
    
    // WHEN
    val result = finalizerWorker.doWork(inputData)
    
    // THEN
    verify(localModelRepository, never()).restoreSoftDeletedModel(any(), any())
    verify(syncUseCase, never()).invoke(any())
}
```

### 7.2 Chain Terminal Semantics

```kotlin
@Test
fun `succeeded download worker is intermediate, not terminal`() {
    // GIVEN
    val workInfo = createWorkInfo(state = SUCCEEDED, workerStage = DOWNLOAD)
    
    // WHEN
    val update = parser.parse(workInfo, currentSessionId = "session-1")
    
    // THEN
    assertThat(update?.status).isNotEqualTo(DownloadStatus.READY)
    assertThat(update?.clearSession).isNull()
}

@Test
fun `succeeded finalizer is terminal`() {
    // GIVEN
    val workInfo = createWorkInfo(state = SUCCEEDED, workerStage = FINALIZE)
    
    // WHEN
    val update = parser.parse(workInfo, currentSessionId = "session-1")
    
    // THEN
    assertThat(update?.status).isEqualTo(DownloadStatus.READY)
    assertThat(update?.clearSession).isTrue()
}
```

### 7.3 Scheduler Chain Construction

```kotlin
@Test
fun `enqueues two-worker chain for startup request`() {
    // GIVEN
    val request = DownloadWorkRequest(
        files = listOf(fileSpec1, fileSpec2),
        sessionId = "session-1",
        requestKind = DownloadRequestKind.INITIALIZE_MODELS,
        targetModelId = null,
        wifiOnly = true,
    )
    
    // WHEN
    scheduler.enqueue(request)
    
    // THEN
    verify(workManager).beginUniqueWork(
        eq(ModelConfig.WORK_TAG),
        eq(ExistingWorkPolicy.REPLACE),
        any<OneTimeWorkRequest>(),
    )
    verify(workManager).enqueue(
        argThat { chain ->
            chain.javaClass.name.contains("ExistingWorkChain")
        }
    )
}
```

---

## 8. Migration Order

The RED phase tests should be written in this order:

1. **Week 1:** `ReDownloadModelUseCaseTest` (the core correctness bug)
2. **Week 2:** `DownloadWorkSchedulerTest` (new chain model)
3. **Week 3:** `DownloadFinalizeWorkerTest` (both finalization paths)
4. **Week 4:** `WorkProgressParserTest` (stage awareness)
5. **Week 5:** `DownloadWorkRepositoryTest` (chain observation)
6. **Week 6:** ViewModel tests (`DownloadViewModelTest`, `ModelReDownloadViewModelTest`)

---

## 9. Non-Negotiable Constraints

- **No production code until tests are RED**
- **No mocking of constructors for final classes** — use interfaces
- **All workers must rethrow `CancellationException`**
- **All terminal outputs must include `clearSession = true`**
- **Finalizer must be idempotent under retry**

---

## 10. What This Enables

After GREEN phase (implementation):
- Repository mutation survives process death
- Startup downloads and re-downloads share the same transfer engine
- UI only observes progress; never owns side effects
- Parser correctly distinguishes intermediate vs terminal success
- Single active pipeline maintained (no concurrency expansion)

---

**TASK_STATUS: COMPLETE — TDD Red Briefing generated for `plans/download-worker-refactor`**
