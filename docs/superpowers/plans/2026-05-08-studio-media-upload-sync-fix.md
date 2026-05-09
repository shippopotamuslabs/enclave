# Studio Media Upload Sync Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix two bugs in studio media cloud sync: a race condition where `MediaCloudSyncWorker`'s `insertMedia(REPLACE)` silently clobbers fields set by concurrent SQL UPDATEs (e.g., `albumId`), and a latent data-loss bug in `updateMedia()` that resets `remoteUrl`/`syncStatus` to defaults.

**Architecture:** Add two targeted SQL UPDATE methods (`markUploaded`, `markUploadFailed`) to `StudioMediaDao` — matching the pattern `MessageFileDao` already uses — then update `MediaCloudSyncWorker` to call them instead of `insertMedia(REPLACE)`. Fix `updateMedia()` in `StudioRepositoryImpl` to read the existing entity and merge upload-related fields before re-inserting.

**Tech Stack:** Kotlin, Room (SQLite), WorkManager `CoroutineWorker`, JUnit 5 (MockK) for repository tests, JUnit 4 + Robolectric (in-memory Room) for DAO tests.

---

## File Map

| Action | File |
|--------|------|
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/StudioMediaDao.kt` |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/MediaCloudSyncWorker.kt` |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/StudioRepositoryImpl.kt` |
| Create | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/local/StudioMediaUploadDaoTest.kt` |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/StudioRepositoryImplTest.kt` |

---

## Task 1: Prove the DAO race condition with a failing test

**Files:**
- Create: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/local/StudioMediaUploadDaoTest.kt`

- [ ] **Step 1: Write the failing test**

Create the file with a Robolectric in-memory database. The test documents the existing bug (`insertMedia(REPLACE)` overwrites `albumId`) and the desired fix (`markUploaded()` preserves it). The first test proves the bug; the second will fail because `markUploaded` doesn't exist yet.

```kotlin
package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumEntity
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudioMediaUploadDaoTest {

    private lateinit var database: PocketCrewDatabase
    private lateinit var dao: StudioMediaDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.studioMediaDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun insertAlbum(albumId: String) {
        database.studioAlbumDao().insertAlbum(
            StudioAlbumEntity(id = albumId, name = "Test Album")
        )
    }

    private fun insertMedia(id: String, albumId: String? = null): StudioMediaEntity {
        val entity = StudioMediaEntity(
            id = id,
            prompt = "sunset",
            mediaUri = "file:///data/files/sunset.jpg",
            mediaType = "IMAGE",
            albumId = albumId,
            syncStatus = "PENDING_UPLOAD",
            pendingSync = 1,
            updatedAt = 1_000L,
            createdAt = 1_000L
        )
        dao.insertMedia(entity)
        return entity
    }

    // ──────────────────────────────────────────────────────────────
    // Bug proof: insertMedia(REPLACE) clobbers concurrent albumId
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `insertMedia REPLACE overwrites albumId set by concurrent updateMediaAlbum`() = runTest {
        // Given: entity inserted with albumId=null
        val staleEntity = insertMedia("m1")  // albumId=null at fetch time

        // Concurrent album assignment (happens while upload is in progress)
        insertAlbum("album-42")
        dao.updateMediaAlbum(listOf("m1"), "album-42")

        // Old worker behavior: inserts stale copy after upload completes
        dao.insertMedia(
            staleEntity.copy(
                remoteUrl = "https://cdn.example.com/m1.jpg",
                syncStatus = "UPLOADED",
                pendingSync = 1,
                updatedAt = 2_000L
            )
        )

        val result = dao.getMediaById("m1")
        // Bug: albumId is null because REPLACE overwrote the concurrent update
        assertNull("albumId should be null (proving the race condition bug)", result?.albumId)
    }

    // ──────────────────────────────────────────────────────────────
    // Fix proof: markUploaded preserves albumId from concurrent update
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `markUploaded preserves albumId set by concurrent updateMediaAlbum`() = runTest {
        // Given: entity inserted with albumId=null
        insertMedia("m1")

        // Concurrent album assignment (happens while upload is in progress)
        insertAlbum("album-42")
        dao.updateMediaAlbum(listOf("m1"), "album-42")

        // Fixed worker behavior: targeted UPDATE preserves other fields
        dao.markUploaded("m1", "https://cdn.example.com/m1.jpg")

        val result = dao.getMediaById("m1")
        assertEquals("album-42", result?.albumId)
        assertEquals("https://cdn.example.com/m1.jpg", result?.remoteUrl)
        assertEquals("UPLOADED", result?.syncStatus)
        assertEquals(1, result?.pendingSync)
    }

    @Test
    fun `markUploaded sets pendingSync to 1 for metadata sync`() = runTest {
        // pendingSync must be 1 so the sync system picks it up and sends remote_url to Firestore
        insertMedia("m1")

        dao.markUploaded("m1", "https://cdn.example.com/m1.jpg")

        val result = dao.getMediaById("m1")
        assertEquals(1, result?.pendingSync)
    }

    @Test
    fun `markUploadFailed preserves albumId and does not change pendingSync`() = runTest {
        insertAlbum("album-99")
        insertMedia("m1")
        dao.updateMediaAlbum(listOf("m1"), "album-99")
        // Simulate sync system claiming this entity
        dao.claimPending()  // pendingSync: 1 → 2

        dao.markUploadFailed("m1")

        val result = dao.getMediaById("m1")
        assertEquals("album-99", result?.albumId)
        assertEquals("FAILED", result?.syncStatus)
        // pendingSync untouched (still 2 so markSynced can still complete)
        assertEquals(2, result?.pendingSync)
    }
}
```

- [ ] **Step 2: Run to verify it fails on the `markUploaded` tests**

```bash
cd /home/sean/Code/pocket-crew
./gradlew :core:data:testDebugUnitTest --tests "*.StudioMediaUploadDaoTest" 2>&1 | tail -30
```

Expected: Two tests pass (the bug-proof and the insertMedia-REPLACE tests), two tests fail with `Unresolved reference: markUploaded` / `Unresolved reference: markUploadFailed`.

---

## Task 2: Add `markUploaded` and `markUploadFailed` to StudioMediaDao

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/StudioMediaDao.kt`

The column names come from `StudioMediaEntity`: `remoteUrl` is the Room column name (no `@ColumnInfo` override), `syncStatus` is the column name, `pending_sync` and `updated_at` are explicitly named via `@ColumnInfo`.

- [ ] **Step 1: Add the two methods**

Open `StudioMediaDao.kt`. After the `markCloudDeleted` method at the end of the interface, add:

```kotlin
    @Query("UPDATE studio_gallery SET remoteUrl = :remoteUrl, syncStatus = 'UPLOADED', pending_sync = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun markUploaded(id: String, remoteUrl: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE studio_gallery SET syncStatus = 'FAILED', updated_at = :updatedAt WHERE id = :id")
    suspend fun markUploadFailed(id: String, updatedAt: Long = System.currentTimeMillis())
```

The full file after edit:

```kotlin
package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioMediaDao {
    @Query("SELECT * FROM studio_gallery WHERE deleted = 0 ORDER BY createdAt DESC")
    fun getAllMedia(): Flow<List<StudioMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: StudioMediaEntity)

    @Query("UPDATE studio_gallery SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM studio_gallery WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<String>)

    @Query("SELECT * FROM studio_gallery WHERE id = :id AND deleted = 0")
    suspend fun getMediaById(id: String): StudioMediaEntity?

    @Query("UPDATE studio_gallery SET albumId = :albumId, pending_sync = 1, updated_at = :timestamp WHERE id IN (:mediaIds)")
    suspend fun updateMediaAlbum(mediaIds: List<String>, albumId: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE studio_gallery SET albumId = NULL, pending_sync = 1, updated_at = :timestamp WHERE albumId = :albumId")
    suspend fun reassignMediaToDefault(albumId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE studio_gallery SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM studio_gallery WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<StudioMediaEntity>

    @Query("UPDATE studio_gallery SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT * FROM studio_gallery WHERE syncStatus = 'PENDING_UPLOAD' AND deleted = 0")
    suspend fun getPendingUploads(): List<StudioMediaEntity>

    @Query("SELECT * FROM studio_gallery WHERE deleted = 1 AND syncStatus != 'DELETED'")
    suspend fun getPendingDeletions(): List<StudioMediaEntity>

    @Query("UPDATE studio_gallery SET syncStatus = 'DELETED' WHERE id = :id")
    suspend fun markCloudDeleted(id: String)

    @Query("UPDATE studio_gallery SET remoteUrl = :remoteUrl, syncStatus = 'UPLOADED', pending_sync = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun markUploaded(id: String, remoteUrl: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE studio_gallery SET syncStatus = 'FAILED', updated_at = :updatedAt WHERE id = :id")
    suspend fun markUploadFailed(id: String, updatedAt: Long = System.currentTimeMillis())
}
```

- [ ] **Step 2: Run the DAO tests again to verify all four pass**

```bash
./gradlew :core:data:testDebugUnitTest --tests "*.StudioMediaUploadDaoTest" 2>&1 | tail -30
```

Expected: All 4 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/StudioMediaDao.kt \
        core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/local/StudioMediaUploadDaoTest.kt
git commit -m "$(cat <<'EOF'
test(data): prove insertMedia REPLACE race condition and add markUploaded/markUploadFailed DAOs

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Fix MediaCloudSyncWorker to use targeted updates

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/MediaCloudSyncWorker.kt`

Replace both `insertMedia(media.copy(...))` calls with `markUploaded` / `markUploadFailed`. The `media` variable (a `StudioMediaEntity` fetched from DB at the start of the loop) is no longer used as a source for copy — we only need `media.id` and `media.mediaUri`.

- [ ] **Step 1: Apply the fix**

Replace the full `doWork()` body in `MediaCloudSyncWorker.kt`:

```kotlin
    override suspend fun doWork(): Result {
        val userId = userIdProvider() ?: return Result.failure()

        val pendingUploads = studioMediaDao.getPendingUploads()
        if (pendingUploads.isEmpty()) return Result.success()

        var hasFailure = false

        for (media in pendingUploads) {
            try {
                val localFile = File(media.mediaUri.replace("file://", ""))
                if (!localFile.exists()) {
                    loggingPort.error(TAG, "Local file not found for media ${media.id}: ${media.mediaUri}")
                    studioMediaDao.markUploadFailed(media.id)
                    continue
                }

                val storageRef = storage.reference.child("users/$userId/media/${media.id}")

                val fileBytes = localFile.readBytes()
                val encryptionResult = cryptoManager.encryptBytes(fileBytes)

                if (encryptionResult.isFailure) {
                    loggingPort.error(TAG, "Encryption failed for media ${media.id}", encryptionResult.exceptionOrNull())
                    hasFailure = true
                    continue
                }

                val encryptedBytes = encryptionResult.getOrThrow()
                storageRef.putBytes(encryptedBytes).await()

                val downloadUrl = storageRef.downloadUrl.await().toString()

                studioMediaDao.markUploaded(media.id, downloadUrl)
                syncTrigger.trigger()
                loggingPort.debug(TAG, "Uploaded media ${media.id} to $downloadUrl")
            } catch (e: Exception) {
                loggingPort.error(TAG, "Failed to upload media ${media.id}", e)
                hasFailure = true
            }
        }

        val pendingDeletions = studioMediaDao.getPendingDeletions()
        for (media in pendingDeletions) {
            try {
                loggingPort.debug(TAG, "Deleting cloud media for ${media.id}")
                val storageRef = storage.reference.child("users/$userId/media/${media.id}")
                try {
                    storageRef.delete().await()
                } catch (e: Exception) {
                    loggingPort.debug(TAG, "Cloud file already gone or error: ${e.message}")
                }
                studioMediaDao.markCloudDeleted(media.id)
            } catch (e: Exception) {
                loggingPort.error(TAG, "Failed to delete cloud media ${media.id}", e)
                hasFailure = true
            }
        }

        return if (hasFailure) Result.retry() else Result.success()
    }
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :core:data:assemble 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run full data module tests**

```bash
./gradlew :core:data:testDebugUnitTest 2>&1 | tail -30
```

Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/MediaCloudSyncWorker.kt
git commit -m "$(cat <<'EOF'
fix(data): replace insertMedia(REPLACE) with targeted markUploaded/markUploadFailed in MediaCloudSyncWorker

Eliminates race condition where a concurrent updateMediaAlbum() call could
be silently overwritten by the upload worker's REPLACE insert. Matches the
pattern already used by MessageFileCloudSyncWorker.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Fix updateMedia() data loss and add test

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/StudioRepositoryImplTest.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/StudioRepositoryImpl.kt`

`updateMedia()` currently constructs a fresh `StudioMediaEntity` without `remoteUrl`, `remoteThumbnailUrl`, or `syncStatus`, so calling it on an already-uploaded item silently resets those fields to `null` / `"PENDING_UPLOAD"`. The fix reads the existing entity first and merges the upload-related fields.

- [ ] **Step 1: Write the failing test**

Add this test to the existing `StudioRepositoryImplTest` class (inside the class body, after the last existing `@Test`):

```kotlin
    @Test
    fun `updateMedia preserves remoteUrl and syncStatus of an already-uploaded entity`() = runTest {
        val existingEntity = StudioMediaEntity(
            id = "media-uploaded",
            prompt = "original",
            mediaUri = "file:///data/files/photo.jpg",
            mediaType = "IMAGE",
            remoteUrl = "https://cdn.example.com/photo.jpg",
            remoteThumbnailUrl = null,
            syncStatus = "UPLOADED",
            createdAt = 1_000L,
            albumId = null
        )
        coEvery { studioMediaDao.getMediaById("media-uploaded") } returns existingEntity

        repository.updateMedia(
            com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset(
                id = "media-uploaded",
                prompt = "updated prompt",
                localUri = "file:///data/files/photo.jpg",
                mediaType = "IMAGE",
                createdAt = 1_000L,
                albumId = null
            )
        )

        coVerify {
            studioMediaDao.insertMedia(match { entity ->
                entity.remoteUrl == "https://cdn.example.com/photo.jpg" &&
                entity.syncStatus == "UPLOADED" &&
                entity.prompt == "updated prompt"
            })
        }
    }

    @Test
    fun `updateMedia uses PENDING_UPLOAD syncStatus when entity has never been uploaded`() = runTest {
        coEvery { studioMediaDao.getMediaById("media-new") } returns null

        repository.updateMedia(
            com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset(
                id = "media-new",
                prompt = "brand new",
                localUri = "file:///data/files/new.jpg",
                mediaType = "IMAGE",
                createdAt = 2_000L,
                albumId = null
            )
        )

        coVerify {
            studioMediaDao.insertMedia(match { entity ->
                entity.remoteUrl == null &&
                entity.syncStatus == "PENDING_UPLOAD"
            })
        }
    }
```

- [ ] **Step 2: Run the new tests to verify they fail**

```bash
./gradlew :core:data:testDebugUnitTest --tests "*.StudioRepositoryImplTest" 2>&1 | tail -30
```

Expected: The two new tests FAIL (the first because the current `updateMedia()` passes `remoteUrl=null` and `syncStatus="PENDING_UPLOAD"` to `insertMedia`).

- [ ] **Step 3: Fix updateMedia() in StudioRepositoryImpl**

Replace the current `updateMedia` method (lines ~127-140) with:

```kotlin
    override suspend fun updateMedia(asset: StudioMediaAsset) {
        val existing = studioMediaDao.getMediaById(asset.id)
        studioMediaDao.insertMedia(
            StudioMediaEntity(
                id = asset.id,
                prompt = asset.prompt,
                mediaUri = asset.localUri,
                mediaType = asset.mediaType,
                createdAt = asset.createdAt,
                albumId = asset.albumId,
                remoteUrl = existing?.remoteUrl,
                remoteThumbnailUrl = existing?.remoteThumbnailUrl,
                syncStatus = existing?.syncStatus ?: "PENDING_UPLOAD",
                pendingSync = 1
            )
        )
        triggerSync()
    }
```

- [ ] **Step 4: Run all repository tests to verify they pass**

```bash
./gradlew :core:data:testDebugUnitTest --tests "*.StudioRepositoryImplTest" 2>&1 | tail -30
```

Expected: All tests PASS.

- [ ] **Step 5: Run the full test suite**

```bash
./gradlew test 2>&1 | tail -40
```

Expected: All tests pass across all modules.

- [ ] **Step 6: Commit**

```bash
git add core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/StudioRepositoryImpl.kt \
        core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/StudioRepositoryImplTest.kt
git commit -m "$(cat <<'EOF'
fix(data): preserve remoteUrl and syncStatus in updateMedia()

Previously constructed a fresh StudioMediaEntity without upload-related
fields, silently resetting remoteUrl to null and syncStatus to
PENDING_UPLOAD on any call after upload completion.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review

**Spec coverage:**
- ✅ Race condition in `MediaCloudSyncWorker` — Tasks 1–3 address this with a DAO-level proof test and a targeted UPDATE fix
- ✅ `updateMedia()` data loss — Task 4 adds two tests and fixes the method
- ✅ TDD order: failing tests always written before implementation

**Placeholder scan:** No TBD, TODO, or vague requirements found.

**Type consistency:**
- `markUploaded(id: String, remoteUrl: String, updatedAt: Long)` — defined in Task 2, used in Task 3 ✅
- `markUploadFailed(id: String, updatedAt: Long)` — defined in Task 2, used in Task 3 ✅
- `StudioMediaAsset` fields (`id`, `prompt`, `localUri`, `mediaType`, `createdAt`, `albumId`) match domain model ✅
- `StudioMediaEntity` field names (`remoteUrl`, `syncStatus`, `pendingSync`, `updatedAt`) match entity definition ✅
