package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.database.sqlite.SQLiteConstraintException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudioMediaDaoTest {
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

    @Test(expected = SQLiteConstraintException::class)
    fun `inserting media with invalid albumId throws constraint exception`() = runTest {
        val media = StudioMediaEntity(
            id = "media-1",
            prompt = "prompt",
            mediaUri = "uri",
            mediaType = "IMAGE",
            albumId = "non-existent-album"
        )
        dao.insertMedia(media)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `inserting media with default albumId throws constraint exception`() = runTest {
        val media = StudioMediaEntity(
            id = "media-1",
            prompt = "prompt",
            mediaUri = "uri",
            mediaType = "IMAGE",
            albumId = "default"
        )
        dao.insertMedia(media)
    }

    @Test
    fun `inserting media with null albumId succeeds`() = runTest {
        val media = StudioMediaEntity(
            id = "media-1",
            prompt = "prompt",
            mediaUri = "uri",
            mediaType = "IMAGE",
            albumId = null
        )
        dao.insertMedia(media)
        
        val retrieved = dao.getMediaById("media-1")
        org.junit.Assert.assertNotNull(retrieved)
        org.junit.Assert.assertNull(retrieved?.albumId)
    }

    @Test
    fun `updating media album to null succeeds`() = runTest {
        val albumId = "album-1"
        database.studioAlbumDao().insertAlbum(StudioAlbumEntity(id = albumId, name = "Album 1"))
        
        val media = StudioMediaEntity(
            id = "media-1",
            prompt = "prompt",
            mediaUri = "uri",
            mediaType = "IMAGE",
            albumId = albumId
        )
        dao.insertMedia(media)
        
        dao.updateMediaAlbum(listOf("media-1"), null)
        
        val retrieved = dao.getMediaById("media-1")
        org.junit.Assert.assertNull(retrieved?.albumId)
    }
}
