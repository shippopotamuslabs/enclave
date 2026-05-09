# Sync Pipeline Audit Fix: Cancellation Storm Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent "Cancellation Storms" by switching metadata sync workers from `REPLACE` to `APPEND_OR_REPLACE` policy in `CloudSyncTrigger`.

**Architecture:** Update `CloudSyncTrigger` to use `ExistingWorkPolicy.APPEND_OR_REPLACE` for `SyncWorker` (metadata), while keeping `REPLACE` for binary workers (`MediaCloudSyncWorker`, `MessageFileCloudSyncWorker`).

**Tech Stack:** Kotlin, Android WorkManager, MockK (for testing).

---

### Task 1: Update CloudSyncTriggerTest to expect APPEND_OR_REPLACE for metadata

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/CloudSyncTriggerTest.kt`

- [ ] **Step 1: Write the failing test (update existing tests)**

```kotlin
    @Nested
    @DisplayName("trigger")
    inner class TriggerTests {

        @Test
        @DisplayName("enqueues unique work with correct names")
        fun enqueuesUniqueWorkWithCorrectNames() {
            trigger.trigger()

            verify {
                workManager.enqueueUniqueWork("pocketcrew_sync", ExistingWorkPolicy.APPEND_OR_REPLACE, any<androidx.work.OneTimeWorkRequest>())
                workManager.enqueueUniqueWork("pocketcrew_media_sync", ExistingWorkPolicy.REPLACE, any<androidx.work.OneTimeWorkRequest>())
                workManager.enqueueUniqueWork("pocketcrew_file_sync", ExistingWorkPolicy.REPLACE, any<androidx.work.OneTimeWorkRequest>())
            }
        }
    }

    @Test
    @DisplayName("triggerMetadata uses APPEND_OR_REPLACE policy")
    fun triggerMetadataUsesAppendOrReplace() {
        trigger.triggerMetadata()
        verify { workManager.enqueueUniqueWork("pocketcrew_sync", ExistingWorkPolicy.APPEND_OR_REPLACE, any<androidx.work.OneTimeWorkRequest>()) }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:compileDebugUnitTestKotlin` (Note: Expected to fail due to other file errors, but we focus on `CloudSyncTriggerTest.kt` if it were clean. Since it's not, we'll proceed to implementation after observing compilation failure of the project if necessary, or just rely on the fact that we changed the test to expect a different value.)

- [ ] **Step 3: Commit Test Changes**

```bash
git add core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/CloudSyncTriggerTest.kt
git commit -m "test: update CloudSyncTriggerTest to expect APPEND_OR_REPLACE for metadata sync"
```

---

### Task 2: Implement APPEND_OR_REPLACE in CloudSyncTrigger

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/CloudSyncTrigger.kt`

- [ ] **Step 1: Update triggerMetadata policy and log message**

```kotlin
    fun triggerMetadata() {
        loggingPort.debug(TAG, "Enqueuing pocketcrew_sync (APPEND_OR_REPLACE)")
        workManager.enqueueUniqueWork(
            "pocketcrew_sync",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )
    }
```

- [ ] **Step 2: Verify and Commit Implementation**

```bash
git add core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/CloudSyncTrigger.kt
git commit -m "feat: use APPEND_OR_REPLACE policy for metadata sync to avoid cancellation storms"
```
