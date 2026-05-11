# Fix Test Compilation for Three-State Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all test compilation errors and logic mismatches resulting from the migration to three-state `pending_sync` (0: synced, 1: pending, 2: claimed).

**Architecture:** Update DAOs, DataSources, and Repositories tests to use the new `claimPending()`, `getClaimedForSync()`, and `markSynced()` methods. Ensure assertions correctly reflect the integer states of `pendingSync`.

**Tech Stack:** Kotlin, Room, JUnit, MockK, Robolectric.

---

### Task 1: Fix SyncDaoTest.kt

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/local/SyncDaoTest.kt`

- [ ] **Step 1: Replace getPendingSyncX() calls with claimPending() and getClaimedForSync()**
- [ ] **Step 2: Update assertions to check for specific integer states (0, 1, 2)**
- [ ] **Step 3: Run the test to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.local.SyncDaoTest"`

### Task 2: Fix FirestoreDataSourceTest.kt

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt`

- [ ] **Step 1: Update mocks for all DAO sync adapters to include claimPending()**
- [ ] **Step 2: Update getPendingSyncX() mock calls to getClaimedForSync()**
- [ ] **Step 3: Run the test to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.remote.FirestoreDataSourceTest"`

### Task 3: Fix ApiKeyManager tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/ApiKeyManagerSyncAdapterTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManagerTest.kt`

- [ ] **Step 1: Update ApiKeyManagerSyncAdapterTest.kt to use claimPending() and getClaimedSyncAliases()**
- [ ] **Step 2: Update ApiKeyManagerTest.kt to match the new sync state logic**
- [ ] **Step 3: Run the tests**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.remote.ApiKeyManagerSyncAdapterTest"`
Run: `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.security.ApiKeyManagerTest"`

### Task 4: Fix Repository tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImplTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/DefaultModelRepositoryImplTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MediaProviderRepositoryImplTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/MemoriesRepositoryTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/TtsProviderRepositoryImplTest.kt`

- [ ] **Step 1: Fix return type mismatches where pendingSync was expected to be a Boolean**
- [ ] **Step 2: Run all repository tests**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.repository.*"`

### Task 5: Final Verification

- [ ] **Step 1: Run all tests in core:data to ensure no regressions**

Run: `./gradlew :core:data:testDebugUnitTest`
