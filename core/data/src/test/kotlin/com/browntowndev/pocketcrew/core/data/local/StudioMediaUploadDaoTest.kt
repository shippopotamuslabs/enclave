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
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PocketCrewDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = database.studioMediaDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private suspend fun insertAlbum(albumId: String) {
        database.studioAlbumDao().insertAlbum(
            StudioAlbumEntity(id = albumId, name = "Test Album"),
        )
    }

    private suspend fun insertMedia(
        id: String,
        albumId: String? = null,
    ): StudioMediaEntity {
        val entity =
            StudioMediaEntity(
                id = id,
                prompt = "sunset",
                mediaUri = "file:///data/files/sunset.jpg",
                mediaType = "IMAGE",
                albumId = albumId,
                syncStatus = "PENDING_UPLOAD",
                pendingSync = 1,
                updatedAt = 1_000L,
                createdAt = 1_000L,
            )
        dao.insertMedia(entity)
        return entity
    }

    // ──────────────────────────────────────────────────────────────
    // Bug proof: insertMedia(REPLACE) clobbers concurrent albumId
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `insertMedia REPLACE overwrites albumId set by concurrent updateMediaAlbum`() =
        runTest {
            // Given: entity inserted with albumId=null
            val staleEntity = insertMedia("m1") // albumId=null at fetch time

            // Concurrent album assignment (happens while upload is in progress)
            insertAlbum("album-42")
            dao.updateMediaAlbum(listOf("m1"), "album-42")

            // Old worker behavior: inserts stale copy after upload completes
            dao.insertMedia(
                staleEntity.copy(
                    remoteUrl = "https://cdn.example.com/m1.jpg",
                    syncStatus = "UPLOADED",
                    pendingSync = 1,
                    updatedAt = 2_000L,
                ),
            )

            val result = dao.getMediaById("m1")
            // Bug: albumId is null because REPLACE overwrote the concurrent update
            assertNull("albumId should be null (proving the race condition bug)", result?.albumId)
        }

    // ──────────────────────────────────────────────────────────────
    // Fix proof: markUploaded preserves albumId from concurrent update
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `markUploaded preserves albumId set by concurrent updateMediaAlbum`() =
        runTest {
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
    fun `markUploaded sets pendingSync to 1 for metadata sync`() =
        runTest {
            // pendingSync must be 1 so the sync system picks it up and sends remote_url to Firestore
            insertMedia("m1")

            dao.markUploaded("m1", "https://cdn.example.com/m1.jpg")

            val result = dao.getMediaById("m1")
            assertEquals(1, result?.pendingSync)
        }

    @Test
    fun `markUploadFailed preserves albumId and does not change pendingSync`() =
        runTest {
            insertAlbum("album-99")
            insertMedia("m1")
            dao.updateMediaAlbum(listOf("m1"), "album-99")
            // Simulate sync system claiming this entity
            dao.claimPending() // pendingSync: 1 → 2

            dao.markUploadFailed("m1")

            val result = dao.getMediaById("m1")
            assertEquals("album-99", result?.albumId)
            assertEquals("FAILED", result?.syncStatus)
            // pendingSync untouched (still 2 so markSynced can still complete)
            assertEquals(2, result?.pendingSync)
        }
}
