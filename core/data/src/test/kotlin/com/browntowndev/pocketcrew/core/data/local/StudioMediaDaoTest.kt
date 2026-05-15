package com.browntowndev.pocketcrew.core.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class StudioMediaDaoTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var dao: StudioMediaDao

    @BeforeEach
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

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `inserting media with invalid albumId throws constraint exception`() =
        runTest {
            assertThrows<SQLiteConstraintException> {
                val media =
                    StudioMediaEntity(
                        id = "media-1",
                        prompt = "prompt",
                        mediaUri = "uri",
                        mediaType = "IMAGE",
                        albumId = "non-existent-album",
                    )
                dao.insertMedia(media)
            }
        }

    @Test
    fun `inserting media with default albumId throws constraint exception`() =
        runTest {
            assertThrows<SQLiteConstraintException> {
                val media =
                    StudioMediaEntity(
                        id = "media-1",
                        prompt = "prompt",
                        mediaUri = "uri",
                        mediaType = "IMAGE",
                        albumId = "default",
                    )
                dao.insertMedia(media)
            }
        }

    @Test
    fun `inserting media with null albumId succeeds`() =
        runTest {
            val media =
                StudioMediaEntity(
                    id = "media-1",
                    prompt = "prompt",
                    mediaUri = "uri",
                    mediaType = "IMAGE",
                    albumId = null,
                )
            dao.insertMedia(media)

            val retrieved = dao.getMediaById("media-1")
            assertNotNull(retrieved)
            assertNull(retrieved?.albumId)
        }

    @Test
    fun `updating media album to null succeeds`() =
        runTest {
            val albumId = "album-1"
            database.studioAlbumDao().insertAlbum(StudioAlbumEntity(id = albumId, name = "Album 1"))

            val media =
                StudioMediaEntity(
                    id = "media-1",
                    prompt = "prompt",
                    mediaUri = "uri",
                    mediaType = "IMAGE",
                    albumId = albumId,
                )
            dao.insertMedia(media)

            dao.updateMediaAlbum(listOf("media-1"), null)

            val retrieved = dao.getMediaById("media-1")
            assertNull(retrieved?.albumId)
        }
}
