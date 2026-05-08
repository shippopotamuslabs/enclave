package com.browntowndev.pocketcrew.core.data.repository

import android.content.Context
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaEntity
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
    private val transactionProvider = FakeTransactionProvider()
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
            transactionProvider = transactionProvider,
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

        coEvery { studioMediaDao.insertMedia(any()) } returns Unit
        coEvery { studioMediaDao.getMediaById(any()) } returns StudioMediaEntity(id = "uuid-1", prompt = "prompt", mediaUri = "file://${File(filesDir, "test.jpg").absolutePath}", mediaType = "IMAGE", createdAt = 1L, albumId = "123")

        val resultAsset = repository.saveMedia(cacheUri, "prompt", "IMAGE", albumId = "123")

        // Verify file was copied to filesDir
        val destinationFile = File(filesDir, "test.jpg")
        assertTrue(destinationFile.exists())
        assertEquals("image data", destinationFile.readText())

        // Verify source file was deleted (cleanup logic)
        assertFalse(sourceFile.exists())

        // Verify DAO insertion
        coVerify {
            studioMediaDao.insertMedia(match {
                it.prompt == "prompt" &&
                it.mediaUri == "file://${destinationFile.absolutePath}" &&
                it.albumId == "123"
            })
        }
        assertEquals("file://${destinationFile.absolutePath}", resultAsset.localUri)
        assertEquals("uuid-1", resultAsset.id)
    }

    @Test
    fun `saveMedia with bytes saves directly to files dir`() = runTest {
        val bytes = "new image".toByteArray()
        
        coEvery { studioMediaDao.insertMedia(any()) } returns Unit
        coEvery { studioMediaDao.getMediaById(any()) } returns StudioMediaEntity(id = "uuid-2", prompt = "new prompt", mediaUri = "file://${File(filesDir, "test.jpg").absolutePath}", mediaType = "IMAGE", createdAt = 1L, albumId = null)

        val resultAsset = repository.saveMedia(bytes, "new prompt", "IMAGE", albumId = null)

        val files = filesDir.listFiles()
        assertEquals(1, files?.size)
        assertEquals("new image", files!![0].readText())
        
        coVerify {
            studioMediaDao.insertMedia(match {
                it.prompt == "new prompt" &&
                it.albumId == null
            })
        }
        assertEquals("uuid-2", resultAsset.id)
    }

    @Test
    fun `renameAlbum trims name and updates album row`() = runTest {
        repository.renameAlbum("42", "  Sunrise  ")

        coVerify {
            studioAlbumDao.updateAlbumName("42", "Sunrise", any())
        }
    }

    @Test
    fun `renameAlbum ignores blank names`() = runTest {
        repository.renameAlbum("42", "   ")

        coVerify(exactly = 0) {
            studioAlbumDao.updateAlbumName(any(), any(), any())
        }
    }

    @Test
    fun `deleteAlbum reassigns media to null before deleting album`() = runTest {
        repository.deleteAlbum("7")

        coVerifyOrder {
            studioMediaDao.reassignMediaToDefault("7", any())
            studioAlbumDao.softDeleteById("7", any())
        }
    }

    @Test
    fun `deleteMedia with UUID calls DAO`() = runTest {
        val uuid = "5a8277e4-dcc0-45d4-81ab-8fb1324c96c9"
        repository.deleteMedia(uuid)

        coVerify { studioMediaDao.softDeleteById(uuid, any()) }
    }

    @Test
    fun `readMediaBytes reads content uri using content resolver`() = runTest {
        val uriString = "content://media/external/images/media/1"
        val mockResolver = mockk<android.content.ContentResolver>()
        every { context.contentResolver } returns mockResolver

        val bytes = "content data".toByteArray()
        val mockInputStream = java.io.ByteArrayInputStream(bytes)

        io.mockk.mockkStatic(android.net.Uri::class)
        val mockUri = mockk<android.net.Uri>()
        every { android.net.Uri.parse(uriString) } returns mockUri
        every { mockResolver.openInputStream(mockUri) } returns mockInputStream

        val result = repository.readMediaBytes(uriString)

        org.junit.jupiter.api.Assertions.assertNotNull(result)
        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, result)

        io.mockk.unmockkStatic(android.net.Uri::class)
    }
}

private class FakeTransactionProvider : TransactionProvider {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return block()
    }
}
