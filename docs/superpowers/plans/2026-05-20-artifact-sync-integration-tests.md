# Artifact Sync Integration Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add and verify integration tests for VisionAnalysis, MessageFile, WebSearchSource, TtsProvider, MediaProvider, and StudioAlbum adapters in `ArtifactSyncIntegrationTest.kt`.

**Architecture:** Mock the DAOs and Firestore, set up local entities with `pendingSync = 1`, trigger `syncPendingChanges()`, and verify that `markSynced()` (or equivalent) is called on the DAOs.

**Tech Stack:** Kotlin, JUnit 5, MockK, Robolectric, Firebase Firestore.

---

### Task 1: Add integration test for VisionAnalysis

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/ArtifactSyncIntegrationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `sync VisionAnalysis to firestore`() = runTest {
        val entity = MessageVisionAnalysisEntity(
            id = "vision1",
            messageId = "msg1",
            analysis = "A beautiful sunset",
            pendingSync = 1
        )
        val syncId = UUID.randomUUID().toString()

        coEvery { messageVisionAnalysisDao.claimPending(any()) } returns 1
        coEvery { messageVisionAnalysisDao.getClaimedForSync(any()) } returns listOf(entity)
        coEvery { messageVisionAnalysisDao.markSynced(listOf(entity.id)) } returns Unit

        dataSource.syncPendingChanges(syncId)

        coVerify { messageVisionAnalysisDao.markSynced(listOf(entity.id)) }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.sync.ArtifactSyncIntegrationTest.sync VisionAnalysis to firestore"`
Expected: FAIL (if not implemented) or PASS (if already implemented in `FirestoreDataSource`)

- [ ] **Step 3: Commit**

```bash
git add core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/ArtifactSyncIntegrationTest.kt
git commit -m "test: add VisionAnalysis sync integration test"
```

### Task 2: Add integration test for MessageFile

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/ArtifactSyncIntegrationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `sync MessageFile to firestore`() = runTest {
        val entity = MessageFileEntity(
            id = "file1",
            messageId = "msg1",
            name = "report.txt",
            content = "content",
            fileType = "ARTIFACT",
            syncStatus = "UPLOADED",
            remoteUrl = "https://storage/file1",
            pendingSync = 1
        )
        val syncId = UUID.randomUUID().toString()

        coEvery { messageFileDao.claimPending(any()) } returns 1
        coEvery { messageFileDao.getClaimedForSync(any()) } returns listOf(entity)
        coEvery { messageFileDao.markSynced(listOf(entity.id)) } returns Unit

        dataSource.syncPendingChanges(syncId)

        coVerify { messageFileDao.markSynced(listOf(entity.id)) }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.sync.ArtifactSyncIntegrationTest.sync MessageFile to firestore"`

- [ ] **Step 3: Commit**

```bash
git add core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/ArtifactSyncIntegrationTest.kt
git commit -m "test: add MessageFile sync integration test"
```

### Task 3: Add integration test for WebSearchSource

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/ArtifactSyncIntegrationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `sync WebSearchSource to firestore`() = runTest {
        val entity = WebSearchSourceEntity(
            id = "source1",
            messageId = "msg1",
            title = "Google",
            url = "https://google.com",
            snippet = "Search engine",
            pendingSync = 1
        )
        val syncId = UUID.randomUUID().toString()

        coEvery { webSearchSourceDao.claimPending(any()) } returns 1
        coEvery { webSearchSourceDao.getClaimedForSync(any()) } returns listOf(entity)
        coEvery { webSearchSourceDao.markSynced(listOf(entity.id)) } returns Unit

        dataSource.syncPendingChanges(syncId)

        coVerify { webSearchSourceDao.markSynced(listOf(entity.id)) }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.sync.ArtifactSyncIntegrationTest.sync WebSearchSource to firestore"`

- [ ] **Step 3: Commit**

```bash
git add core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/ArtifactSyncIntegrationTest.kt
git commit -m "test: add WebSearchSource sync integration test"
```

### Task 4: Add integration test for TtsProvider

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/ArtifactSyncIntegrationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `sync TtsProvider to firestore`() = runTest {
        val entity = TtsProviderEntity(
            id = "tts1",
            name = "OpenAI",
            provider = ApiProvider.OPEN_AI,
            pendingSync = 1
        )
        val syncId = UUID.randomUUID().toString()

        coEvery { ttsProviderDao.claimPending(any()) } returns 1
        coEvery { ttsProviderDao.getClaimedForSync(any()) } returns listOf(entity)
        coEvery { ttsProviderDao.markSynced(listOf(entity.id)) } returns Unit

        dataSource.syncPendingChanges(syncId)

        coVerify { ttsProviderDao.markSynced(listOf(entity.id)) }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.sync.ArtifactSyncIntegrationTest.sync TtsProvider to firestore"`

- [ ] **Step 3: Commit**

```bash
git add core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/ArtifactSyncIntegrationTest.kt
git commit -m "test: add TtsProvider sync integration test"
```

### Task 5: Add integration test for MediaProvider

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/ArtifactSyncIntegrationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `sync MediaProvider to firestore`() = runTest {
        val entity = MediaProviderEntity(
            id = "media1",
            name = "DALL-E 3",
            provider = ApiProvider.OPEN_AI,
            capability = MediaCapability.IMAGE_GENERATION,
            pendingSync = 1
        )
        val syncId = UUID.randomUUID().toString()

        coEvery { mediaProviderDao.claimPending(any()) } returns 1
        coEvery { mediaProviderDao.getClaimedForSync(any()) } returns listOf(entity)
        coEvery { mediaProviderDao.markSynced(listOf(entity.id)) } returns Unit

        dataSource.syncPendingChanges(syncId)

        coVerify { mediaProviderDao.markSynced(listOf(entity.id)) }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.sync.ArtifactSyncIntegrationTest.sync MediaProvider to firestore"`

- [ ] **Step 3: Commit**

```bash
git add core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/ArtifactSyncIntegrationTest.kt
git commit -m "test: add MediaProvider sync integration test"
```

### Task 6: Add integration test for StudioAlbum

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/ArtifactSyncIntegrationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `sync StudioAlbum to firestore`() = runTest {
        val entity = StudioAlbumEntity(
            id = "album1",
            name = "My Album",
            pendingSync = 1
        )
        val syncId = UUID.randomUUID().toString()

        coEvery { studioAlbumDao.claimPending(any()) } returns 1
        coEvery { studioAlbumDao.getClaimedForSync(any()) } returns listOf(entity)
        coEvery { studioAlbumDao.markSynced(listOf(entity.id)) } returns Unit

        dataSource.syncPendingChanges(syncId)

        coVerify { studioAlbumDao.markSynced(listOf(entity.id)) }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.sync.ArtifactSyncIntegrationTest.sync StudioAlbum to firestore"`

- [ ] **Step 3: Commit**

```bash
git add core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/ArtifactSyncIntegrationTest.kt
git commit -m "test: add StudioAlbum sync integration test"
```

### Task 7: Final Verification

- [ ] **Step 1: Run all tests in the file**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.sync.ArtifactSyncIntegrationTest"`
Expected: ALL PASS
