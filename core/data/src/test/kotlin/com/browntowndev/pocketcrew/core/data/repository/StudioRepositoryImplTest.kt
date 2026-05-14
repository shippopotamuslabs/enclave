package com.browntowndev.pocketcrew.core.data.repository

import android.content.Context
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumDao
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumEntity
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaEntity
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.port.repository.DEFAULT_GALLERY_ALBUM_ID
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
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val transactionProvider = FakeTransactionProvider()
    private val context = mockk<Context>()

    @TempDir
    lateinit var tempDir: File

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private val healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService())
    private val readRepairApplier = mockk<SensitiveRoomReadRepairApplier>(relaxed = true)
    private lateinit var repository: StudioRepositoryImpl

    @BeforeEach
    fun setup() {
        cacheDir = File(tempDir, "cache")
        filesDir = File(tempDir, "files")
        cacheDir.mkdirs()
        filesDir.mkdirs()

        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir

        repository =
            StudioRepositoryImpl(
                studioMediaDao = studioMediaDao,
                studioAlbumDao = studioAlbumDao,
                transactionProvider = transactionProvider,
                syncTrigger = syncTrigger,
                healer = healer,
                readRepairApplier = readRepairApplier,
                context = context,
            )
    }

    @Test
    fun `cacheEphemeralMedia saves bytes to cache subfolder`() =
        runTest {
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
    fun `clearEphemeralCache deletes cache subfolder`() =
        runTest {
            val ephemeralDir = File(cacheDir, "studio_ephemeral")
            ephemeralDir.mkdirs()
            File(ephemeralDir, "test.jpg").writeText("data")

            repository.clearEphemeralCache()

            assertFalse(ephemeralDir.exists())
        }

    @Test
    fun `saveMedia moves file from cache to files dir when uri is in cache`() =
        runTest {
            val ephemeralDir = File(cacheDir, "studio_ephemeral")
            ephemeralDir.mkdirs()
            val sourceFile = File(ephemeralDir, "test.jpg")
            sourceFile.writeText("image data")
            val cacheUri = "file://${sourceFile.absolutePath}"

            coEvery { studioMediaDao.insertMedia(any()) } returns Unit
            coEvery { studioMediaDao.getMediaById(any()) } returns
                StudioMediaEntity(
                    id = "uuid-1",
                    prompt = "PCENC:v1:aes-gcm:prompt",
                    mediaUri = "PCENC:v1:aes-gcm:file://${File(filesDir, "test.jpg").absolutePath}",
                    mediaType = "IMAGE",
                    createdAt = 1L,
                    albumId = "123",
                )

            val resultAsset = repository.saveMedia(cacheUri, "prompt", "IMAGE", albumId = "123")

            // Verify file was copied to filesDir
            val destinationFile = File(filesDir, "test.jpg")
            assertTrue(destinationFile.exists())
            assertEquals("image data", destinationFile.readText())

            // Verify source file was deleted (cleanup logic)
            assertFalse(sourceFile.exists())

            // Verify DAO insertion
            coVerify {
                studioMediaDao.insertMedia(
                    match {
                        it.prompt == "PCENC:v1:aes-gcm:prompt" &&
                            it.mediaUri == "PCENC:v1:aes-gcm:file://${destinationFile.absolutePath}" &&
                            it.albumId == "123"
                    },
                )
            }
            assertEquals("file://${destinationFile.absolutePath}", resultAsset.localUri)
            assertEquals("uuid-1", resultAsset.id)
        }

    @Test
    fun `saveMedia with bytes saves directly to files dir`() =
        runTest {
            val bytes = "new image".toByteArray()

            coEvery { studioMediaDao.insertMedia(any()) } returns Unit
            coEvery { studioMediaDao.getMediaById(any()) } returns
                StudioMediaEntity(
                    id = "uuid-2",
                    prompt = "PCENC:v1:aes-gcm:new prompt",
                    mediaUri = "PCENC:v1:aes-gcm:file://${File(filesDir, "test.jpg").absolutePath}",
                    mediaType = "IMAGE",
                    createdAt = 1L,
                    albumId = null,
                )

            val resultAsset = repository.saveMedia(bytes, "new prompt", "IMAGE", albumId = null)

            val files = filesDir.listFiles()
            assertEquals(1, files?.size)
            assertEquals("new image", files!![0].readText())

            coVerify {
                studioMediaDao.insertMedia(
                    match {
                        it.prompt == "PCENC:v1:aes-gcm:new prompt" &&
                            it.albumId == null
                    },
                )
            }
            assertEquals("uuid-2", resultAsset.id)
        }

    @Test
    fun `saveMedia stores prompt encrypted and returns plaintext domain model`() =
        runTest {
            val encryptedRepository =
                StudioRepositoryImpl(
                    studioMediaDao = studioMediaDao,
                    studioAlbumDao = studioAlbumDao,
                    transactionProvider = transactionProvider,
                    syncTrigger = syncTrigger,
                    healer = healer,
                    readRepairApplier = readRepairApplier,
                    context = context,
                )
            val sourceFile = File(filesDir, "encrypted-test.jpg")
            sourceFile.writeText("image data")
            val localUri = "file://${sourceFile.absolutePath}"

            coEvery { studioMediaDao.insertMedia(any()) } returns Unit
            coEvery { studioMediaDao.getMediaById(any()) } returns
                StudioMediaEntity(
                    id = "uuid-3",
                    prompt = "PCENC:v1:aes-gcm:private prompt",
                    mediaUri = localUri,
                    mediaType = "IMAGE",
                    createdAt = 1L,
                    albumId = null,
                )

            val resultAsset = encryptedRepository.saveMedia(localUri, "private prompt", "IMAGE", albumId = null)

            coVerify {
                studioMediaDao.insertMedia(
                    match {
                        it.prompt == "PCENC:v1:aes-gcm:private prompt" &&
                            it.mediaUri == "PCENC:v1:aes-gcm:$localUri"
                    },
                )
            }
            assertEquals("private prompt", resultAsset.prompt)
        }

    @Test
    fun `renameAlbum trims name and updates album row`() =
        runTest {
            coEvery { studioAlbumDao.getAlbumById("42") } returns
                StudioAlbumEntity(id = "42", name = "PCENC:v1:aes-gcm:Old", pendingSync = 0)
            repository.renameAlbum("42", "  Sunrise  ")

            coVerify {
                studioAlbumDao.insertAlbum(
                    match {
                        it.id == "42" && it.name == "PCENC:v1:aes-gcm:Sunrise"
                    },
                )
            }
        }

    @Test
    fun `renameAlbum ignores blank names`() =
        runTest {
            repository.renameAlbum("42", "   ")

            coVerify(exactly = 0) {
                studioAlbumDao.insertAlbum(any())
            }
        }

    @Test
    fun `deleteAlbum reassigns media to null before deleting album`() =
        runTest {
            repository.deleteAlbum("7")

            coVerifyOrder {
                studioMediaDao.reassignMediaToDefault("7", any())
                studioAlbumDao.softDeleteById("7", any())
            }
        }

    @Test
    fun `deleteMedia with UUID calls DAO`() =
        runTest {
            val uuid = "5a8277e4-dcc0-45d4-81ab-8fb1324c96c9"
            repository.deleteMedia(uuid)

            coVerify { studioMediaDao.softDeleteById(uuid, any()) }
        }

    @Test
    fun `saveMedia maps 'default' albumId to null`() =
        runTest {
            coEvery { studioMediaDao.insertMedia(any()) } returns Unit
            coEvery { studioMediaDao.getMediaById(any()) } returns
                StudioMediaEntity(id = "uuid-1", prompt = "PCENC:v1:aes-gcm:prompt", mediaUri = "PCENC:v1:aes-gcm:uri", mediaType = "IMAGE", createdAt = 1L, albumId = null)

            repository.saveMedia("uri", "prompt", "IMAGE", albumId = DEFAULT_GALLERY_ALBUM_ID)

            coVerify {
                studioMediaDao.insertMedia(match { it.albumId == null })
            }
        }

    @Test
    fun `moveMediaToAlbum maps 'default' albumId to null`() =
        runTest {
            repository.moveMediaToAlbum(listOf("media-1"), DEFAULT_GALLERY_ALBUM_ID)

            coVerify {
                studioMediaDao.updateMediaAlbum(listOf("media-1"), null, any())
            }
        }

    @Test
    fun `readMediaBytes reads content uri using content resolver`() =
        runTest {
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

            org.junit.jupiter.api.Assertions
                .assertNotNull(result)
            org.junit.jupiter.api.Assertions
                .assertArrayEquals(bytes, result)

            io.mockk.unmockkStatic(android.net.Uri::class)
        }

    @Test
    fun `updateMedia preserves remoteUrl and syncStatus of an already-uploaded entity`() =
        runTest {
            val existingEntity =
                StudioMediaEntity(
                    id = "media-uploaded",
                    prompt = "original",
                    mediaUri = "file:///data/files/photo.jpg",
                    mediaType = "IMAGE",
                    remoteUrl = "https://cdn.example.com/photo.jpg",
                    remoteThumbnailUrl = null,
                    syncStatus = "UPLOADED",
                    createdAt = 1_000L,
                    albumId = null,
                )
            coEvery { studioMediaDao.getMediaById("media-uploaded") } returns existingEntity

            repository.updateMedia(
                com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset(
                    id = "media-uploaded",
                    prompt = "updated prompt",
                    localUri = "file:///data/files/photo.jpg",
                    mediaType = "IMAGE",
                    createdAt = 1_000L,
                    albumId = null,
                ),
            )

            coVerify {
                studioMediaDao.insertMedia(
                    match { entity ->
                        entity.remoteUrl == "PCENC:v1:aes-gcm:https://cdn.example.com/photo.jpg" &&
                            entity.syncStatus == "UPLOADED" &&
                            entity.prompt == "PCENC:v1:aes-gcm:updated prompt" &&
                            entity.mediaUri == "PCENC:v1:aes-gcm:file:///data/files/photo.jpg"
                    },
                )
            }
        }

    @Test
    fun `updateMedia uses PENDING_UPLOAD syncStatus when entity has never been uploaded`() =
        runTest {
            coEvery { studioMediaDao.getMediaById("media-new") } returns null

            repository.updateMedia(
                com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset(
                    id = "media-new",
                    prompt = "brand new",
                    localUri = "file:///data/files/new.jpg",
                    mediaType = "IMAGE",
                    createdAt = 2_000L,
                    albumId = null,
                ),
            )

            coVerify {
                studioMediaDao.insertMedia(
                    match { entity ->
                        entity.remoteUrl == null &&
                            entity.syncStatus == "PENDING_UPLOAD"
                    },
                )
            }
        }

    @Test
    fun `updateMedia maps DEFAULT_GALLERY_ALBUM_ID to null albumId`() =
        runTest {
            coEvery { studioMediaDao.getMediaById("media-1") } returns null

            repository.updateMedia(
                com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset(
                    id = "media-1",
                    prompt = "test",
                    localUri = "file:///data/files/test.jpg",
                    mediaType = "IMAGE",
                    createdAt = 1_000L,
                    albumId = DEFAULT_GALLERY_ALBUM_ID,
                ),
            )

            coVerify {
                studioMediaDao.insertMedia(match { it.albumId == null })
            }
        }
}

private class FakeTransactionProvider : TransactionProvider {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
}
