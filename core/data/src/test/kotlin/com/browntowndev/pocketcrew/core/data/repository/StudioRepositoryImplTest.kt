package com.browntowndev.pocketcrew.core.data.repository

import android.content.Context
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class StudioRepositoryImplTest {

    private val studioMediaDao = mockk<StudioMediaDao>(relaxed = true)
    private val studioAlbumDao = mockk<StudioAlbumDao>(relaxed = true)
    private val context = mockk<Context>()

    @TempDir
    lateinit var tempDir: File

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var repository: StudioRepositoryImpl

    @BeforeEach
    fun setup() {
        cacheDir = File(tempDir, "cache")
        filesDir = File(tempDir, "files")
        cacheDir.mkdirs()
        filesDir.mkdirs()

        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir

        repository = StudioRepositoryImpl(
            studioMediaDao = studioMediaDao,
            studioAlbumDao = studioAlbumDao,
            context = context
        )
    }

    @Test
    fun `cacheEphemeralMedia saves bytes to cache subfolder`() = runTest {
        val bytes = "test data".toByteArray()
        val uriString = repository.cacheEphemeralMedia(bytes, "IMAGE")

        val ephemeralDir = File(cacheDir, "studio_ephemeral")
        assertTrue(ephemeralDir.exists())
        val files = ephemeralDir.listFiles()
        assertEquals(1, files?.size)
        assertTrue(files!![0].name.endsWith(".jpg"))
        assertEquals("test data", files[0].readText())
        assertTrue(uriString.contains("studio_ephemeral"))
    }

    @Test
    fun `clearEphemeralCache deletes cache subfolder`() = runTest {
        val ephemeralDir = File(cacheDir, "studio_ephemeral")
        ephemeralDir.mkdirs()
        File(ephemeralDir, "test.jpg").writeText("data")

        repository.clearEphemeralCache()

        assertFalse(ephemeralDir.exists())
    }

    @Test
    fun `saveMedia moves file from cache to files dir when uri is in cache`() = runTest {
        val ephemeralDir = File(cacheDir, "studio_ephemeral")
        ephemeralDir.mkdirs()
        val sourceFile = File(ephemeralDir, "test.jpg")
        sourceFile.writeText("image data")
        val cacheUri = "file://${sourceFile.absolutePath}"

        val resultUri = repository.saveMedia(cacheUri, "prompt", "IMAGE", albumId = "123")

        // Verify file was copied to filesDir
        val destinationFile = File(filesDir, "test.jpg")
        assertTrue(destinationFile.exists())
        assertEquals("image data", destinationFile.readText())

        // Verify DAO insertion
        coVerify {
            studioMediaDao.insertMedia(match {
                it.prompt == "prompt" &&
                it.mediaUri == "file://${destinationFile.absolutePath}" &&
                it.albumId == 123L
            })
        }
        assertEquals("file://${destinationFile.absolutePath}", resultUri)
    }

    @Test
    fun `saveMedia with bytes saves directly to files dir`() = runTest {
        val bytes = "new image".toByteArray()
        
        val resultUri = repository.saveMedia(bytes, "new prompt", "IMAGE", albumId = null)

        val files = filesDir.listFiles()
        assertEquals(1, files?.size)
        assertEquals("new image", files!![0].readText())
        
        coVerify {
            studioMediaDao.insertMedia(match {
                it.prompt == "new prompt" &&
                it.albumId == null
            })
        }
        assertEquals("file://${files[0].absolutePath}", resultUri)
    }
}
