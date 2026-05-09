# Fix FirestoreDataSource Test Compilation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix compilation errors in `:core:data` unit tests caused by the new `chatSummaryDao` parameter in `FirestoreDataSource`.

**Architecture:** Update `FirestoreDataSource` instantiation in all failing tests to include a mocked `ChatSummaryDao` and ensure all parameters are correctly ordered and typed.

**Tech Stack:** Kotlin, MockK, JUnit 5, Gradle

---

### Task 1: Fix ApiKeyManagerSyncAdapterTest

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/ApiKeyManagerSyncAdapterTest.kt`

- [ ] **Step 1: Update FirestoreDataSource instantiation**

```kotlin
        dataSource = FirestoreDataSource(
            firestore = firestore,
            chatDao = mockk(relaxed = true),
            messageDao = mockk(relaxed = true),
            memoriesDao = mockk(relaxed = true),
            webSearchSourceDao = mockk(relaxed = true),
            ttsProviderDao = mockk(relaxed = true),
            mediaProviderDao = mockk(relaxed = true),
            apiModelConfigurationsDao = mockk(relaxed = true),
            localModelsDao = mockk(relaxed = true),
            apiCredentialsDao = mockk(relaxed = true),
            localModelConfigurationsDao = mockk(relaxed = true),
            defaultModelsDao = mockk(relaxed = true),
            studioMediaDao = mockk(relaxed = true),
            studioAlbumDao = mockk(relaxed = true),
            messageFileDao = mockk(relaxed = true),
            messageVisionAnalysisDao = mockk(relaxed = true),
            chatSummaryDao = mockk(relaxed = true),
            embeddingDao = mockk(relaxed = true),
            apiKeyManager = apiKeyManager,
            cryptoManager = cryptoManager,
            loggingPort = loggingPort,
            externalScope = testScope,
            userIdProvider = { "test-user-id" },
            embeddingCoordinator = mockk(relaxed = true)
        )
```

- [ ] **Step 2: Commit**

```bash
git add core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/ApiKeyManagerSyncAdapterTest.kt
git commit -m "test: fix FirestoreDataSource instantiation in ApiKeyManagerSyncAdapterTest"
```

### Task 2: Fix FirestoreDataSourceTest

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt`

- [ ] **Step 1: Update FirestoreDataSource instantiation (multiple locations)**

Add `chatSummaryDao = mockk(relaxed = true)` to all named-parameter instantiations.

- [ ] **Step 2: Commit**

```bash
git add core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt
git commit -m "test: fix FirestoreDataSource instantiation in FirestoreDataSourceTest"
```

### Task 3: Fix StudioMediaSyncMergeTest

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/StudioMediaSyncMergeTest.kt`

- [ ] **Step 1: Update FirestoreDataSource instantiation**

```kotlin
        dataSource = FirestoreDataSource(
            firestore = firestore,
            chatDao = mockk(relaxed = true),
            messageDao = mockk(relaxed = true),
            memoriesDao = mockk(relaxed = true),
            webSearchSourceDao = mockk(relaxed = true),
            ttsProviderDao = mockk(relaxed = true),
            mediaProviderDao = mockk(relaxed = true),
            apiModelConfigurationsDao = mockk(relaxed = true),
            localModelsDao = mockk(relaxed = true),
            apiCredentialsDao = mockk(relaxed = true),
            localModelConfigurationsDao = mockk(relaxed = true),
            defaultModelsDao = mockk(relaxed = true),
            studioMediaDao = studioMediaDao,
            studioAlbumDao = mockk(relaxed = true),
            messageFileDao = mockk(relaxed = true),
            messageVisionAnalysisDao = mockk(relaxed = true),
            chatSummaryDao = mockk(relaxed = true),
            embeddingDao = embeddingDao,
            apiKeyManager = mockk(relaxed = true),
            cryptoManager = cryptoManager,
            loggingPort = loggingPort,
            externalScope = testScope,
            userIdProvider = { "test-user" },
            embeddingCoordinator = embeddingCoordinator
        )
```

- [ ] **Step 2: Commit**

```bash
git add core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/StudioMediaSyncMergeTest.kt
git commit -m "test: fix FirestoreDataSource instantiation in StudioMediaSyncMergeTest"
```

### Task 4: Fix SyncReconciliationTest

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/SyncReconciliationTest.kt`

- [ ] **Step 1: Update FirestoreDataSource instantiation**

```kotlin
        dataSource = FirestoreDataSource(
            firestore = firestore,
            chatDao = chatDao,
            messageDao = messageDao,
            memoriesDao = mockk(relaxed = true),
            webSearchSourceDao = mockk(relaxed = true),
            ttsProviderDao = mockk(relaxed = true),
            mediaProviderDao = mockk(relaxed = true),
            apiModelConfigurationsDao = mockk(relaxed = true),
            localModelsDao = mockk(relaxed = true),
            apiCredentialsDao = mockk(relaxed = true),
            localModelConfigurationsDao = mockk(relaxed = true),
            defaultModelsDao = mockk(relaxed = true),
            studioMediaDao = mockk(relaxed = true),
            studioAlbumDao = mockk(relaxed = true),
            messageFileDao = mockk(relaxed = true),
            messageVisionAnalysisDao = mockk(relaxed = true),
            chatSummaryDao = mockk(relaxed = true),
            embeddingDao = mockk(relaxed = true),
            apiKeyManager = mockk(relaxed = true),
            cryptoManager = cryptoManager,
            loggingPort = loggingPort,
            externalScope = testScope,
            userIdProvider = { "test-user-id" },
            embeddingCoordinator = mockk(relaxed = true)
        )
```

- [ ] **Step 2: Commit**

```bash
git add core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/SyncReconciliationTest.kt
git commit -m "test: fix FirestoreDataSource instantiation in SyncReconciliationTest"
```

### Task 5: Final Validation

- [ ] **Step 1: Run compilation**

Run: `./gradlew :core:data:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests in :core:data**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: BUILD SUCCESSFUL
