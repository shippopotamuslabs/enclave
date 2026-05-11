# Pending Sync Three-State Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the migration of `pendingSync` from `Boolean` to `Int` (0 = Clean, 1 = Dirty/Pending, 2 = Remote Deleted) across the entire codebase.

**Architecture:** Replace all boolean assignments and assertions with integer equivalents.

**Tech Stack:** Kotlin, Room, JUnit

---

### Task 1: Batch Replace Boolean Assignments

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/mapper/ChatMappers.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImpl.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ChatRepositoryImpl.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/DefaultModelRepositoryImpl.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/LocalModelRepositoryImpl.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/MediaProviderRepositoryImpl.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/MemoriesRepositoryImpl.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/MessageRepositoryImpl.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/TtsProviderRepositoryImpl.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/MediaCloudSyncWorker.kt`

- [ ] **Step 1: Replace `pendingSync = true` with `pendingSync = 1`**
- [ ] **Step 2: Replace `pendingSync = false` with `pendingSync = 0`**
- [ ] **Step 3: Commit**

```bash
git add .
git commit -m "refactor: migrate pendingSync boolean assignments to int (0/1)"
```

### Task 2: Update Data Layer Tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/local/SyncDaoTest.kt`

- [ ] **Step 1: Replace `pendingSync = true` with `pendingSync = 1` and `pendingSync = false` with `pendingSync = 0` in `SyncDaoTest.kt`**
- [ ] **Step 2: Update assertions**
  - `assertTrue(entity.pendingSync)` -> `assertEquals(1, entity.pendingSync)`
  - `assertFalse(entity.pendingSync)` -> `assertEquals(0, entity.pendingSync)`
- [ ] **Step 3: Verify tests pass**
  - Run: `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.local.SyncDaoTest"`
- [ ] **Step 4: Commit**

```bash
git add core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/local/SyncDaoTest.kt
git commit -m "test: update SyncDaoTest for pendingSync int migration"
```

### Task 3: Update Remote Layer Tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt`

- [ ] **Step 1: Search for `pendingSync` usages in `FirestoreDataSourceTest.kt` and update to `0` or `1`**
- [ ] **Step 2: Update assertions as in Task 2**
- [ ] **Step 3: Verify tests pass**
  - Run: `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.remote.FirestoreDataSourceTest"`
- [ ] **Step 4: Commit**

```bash
git add core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt
git commit -m "test: update FirestoreDataSourceTest for pendingSync int migration"
```

### Task 4: Final Verification and Clean up

- [ ] **Step 1: Run all tests in `core:data`**
  - Run: `./gradlew :core:data:testDebugUnitTest`
- [ ] **Step 2: Run build to ensure no compilation errors**
  - Run: `./gradlew assembleDebug`
- [ ] **Step 3: Final commit**

```bash
git commit -m "chore: complete pendingSync migration and verification"
```
