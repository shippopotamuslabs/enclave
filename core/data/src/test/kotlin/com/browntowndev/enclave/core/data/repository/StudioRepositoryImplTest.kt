package com.browntowndev.enclave.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.StudioAlbumEntity
import com.browntowndev.enclave.core.data.local.StudioMediaEntity
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.port.repository.ActiveStudioGeneration
import com.browntowndev.enclave.domain.port.repository.DEFAULT_GALLERY_ALBUM_ID
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Shadows.shadowOf
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.ByteArrayInputStream
import java.io.File
import org.robolectric.annotation.Config as RoboConfig

/**
 * Translated from MockK to hand-written fakes (ENC-28): recording DAO
 * fakes, a recording sync trigger, the real [ActiveStudioGenerationPersistence]
 * over an in-memory DataStore, and the Robolectric application context (its
 * cache/files dirs are real writable directories). Incidental cleanup DAO /
 * read-repair applier are the real implementations over an in-memory Room
 * database.
 */
@ExtendWith(RobolectricExtension::class)
@RoboConfig(sdk = [34])
class StudioRepositoryImplTest {
    private lateinit var database: EnclaveDatabase
    private val callLog = mutableListOf<String>()
    private val studioMediaDao = FakeStudioMediaDao(callLog)
    private val studioAlbumDao = FakeStudioAlbumDao(callLog)
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private val transactionProvider = FakeTransactionProvider()
    private lateinit var context: Context

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private val healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService(), FakeLoggingPort())
    private lateinit var activeGenerationPersistence: ActiveStudioGenerationPersistence
    private lateinit var repository: StudioRepositoryImpl

    @BeforeEach
    fun setup() {
        syncTrigger = RecordingCloudSyncTrigger()
        database = createInMemoryEnclaveDatabase()
        context = ApplicationProvider.getApplicationContext()
        cacheDir = context.cacheDir
        filesDir = context.filesDir
        activeGenerationPersistence = ActiveStudioGenerationPersistence(InMemoryPreferencesDataStore())

        repository =
            StudioRepositoryImpl(
                studioMediaDao = studioMediaDao,
                studioAlbumDao = studioAlbumDao,
                transactionProvider = transactionProvider,
                cleanupDao = database.cleanupDao(),
                syncTrigger = syncTrigger,
                healer = healer,
                readRepairApplier = database.realReadRepairApplier(),
                activeGenerationPersistence = activeGenerationPersistence,
                context = context,
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `cacheEphemeralMedia saves bytes to cache subfolder`() =
        runTest {
            val bytes = "test data".toByteArray()
            // Robolectric sandbox dirs are shared across this class's tests, so
            // assert on the one new file rather than the folder's total contents.
            val ephemeralDir = File(cacheDir, "studio_ephemeral")
            val before = ephemeralDir.listFiles()?.toSet() ?: emptySet()
            val uriString = repository.cacheEphemeralMedia(bytes, "IMAGE")

            assertTrue(ephemeralDir.exists())
            val newFiles = (ephemeralDir.listFiles()?.toSet() ?: emptySet()) - before
            assertEquals(1, newFiles.size)
            val written = newFiles.single()
            assertTrue(written.name.endsWith(".png"))
            assertEquals("test data", written.readText())
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
    fun `active generation is observable until cleared`() =
        runTest {
            val activeGeneration =
                ActiveStudioGeneration(
                    id = "gen-1",
                    prompt = "in progress",
                    mediaType = MediaCapability.IMAGE,
                    settings = ImageGenerationSettings(),
                )

            repository.observeActiveGeneration().test {
                assertNull(awaitItem())

                assertTrue(repository.reportActiveGeneration(activeGeneration))
                assertEquals(activeGeneration, awaitItem())

                assertFalse(repository.reportActiveGeneration(activeGeneration.copy(prompt = "second")))
                expectNoEvents()

                repository.clearActiveGeneration()
                assertNull(awaitItem())
            }
        }

    @Test
    fun `clearActiveGenerationIfOwned only clears matching generation`() =
        runTest {
            val first =
                ActiveStudioGeneration(
                    id = "gen-1",
                    prompt = "first",
                    mediaType = MediaCapability.IMAGE,
                    settings = ImageGenerationSettings(),
                )
            val second =
                ActiveStudioGeneration(
                    id = "gen-2",
                    prompt = "second",
                    mediaType = MediaCapability.IMAGE,
                    settings = ImageGenerationSettings(),
                )

            assertTrue(repository.reportActiveGeneration(first))
            assertFalse(repository.clearActiveGenerationIfOwned("gen-2"))
            assertTrue(repository.hasActiveGeneration())
            assertTrue(repository.clearActiveGenerationIfOwned("gen-1"))
            assertFalse(repository.hasActiveGeneration())

            assertTrue(repository.reportActiveGeneration(second))
            assertTrue(repository.clearActiveGenerationIfOwned("gen-2"))
        }

    @Test
    fun `reconcilePersistedActiveGeneration clears stale claim without surfacing error`() =
        runTest {
            val staleGeneration =
                ActiveStudioGeneration(
                    id = "gen-stale",
                    prompt = "orphaned",
                    mediaType = MediaCapability.IMAGE,
                    settings = ImageGenerationSettings(),
                )
            activeGenerationPersistence.persist(staleGeneration)

            repository.reconcilePersistedActiveGeneration()

            assertNull(activeGenerationPersistence.load())
            repository.observeGenerationError().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `clearEphemeralCache refuses while generation active`() =
        runTest {
            repository.reportActiveGeneration(
                ActiveStudioGeneration(
                    id = "gen-1",
                    prompt = "busy",
                    mediaType = MediaCapability.IMAGE,
                    settings = ImageGenerationSettings(),
                ),
            )

            val error =
                runCatching {
                    repository.clearEphemeralCache()
                }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
        }

    @Test
    fun `saveMedia moves file from cache to files dir when uri is in cache`() =
        runTest {
            val ephemeralDir = File(cacheDir, "studio_ephemeral")
            ephemeralDir.mkdirs()
            val sourceFile = File(ephemeralDir, "test.jpg")
            sourceFile.writeText("image data")
            val cacheUri = "file://${sourceFile.absolutePath}"

            studioMediaDao.getMediaByIdOverride =
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
            val inserted = studioMediaDao.insertCalls.single()
            assertEquals("PCENC:v1:aes-gcm:prompt", inserted.prompt)
            assertEquals("PCENC:v1:aes-gcm:file://${destinationFile.absolutePath}", inserted.mediaUri)
            assertEquals("123", inserted.albumId)
            assertEquals("file://${destinationFile.absolutePath}", resultAsset.localUri)
            assertEquals("uuid-1", resultAsset.id)
        }

    @Test
    fun `saveMedia with bytes saves directly to files dir`() =
        runTest {
            val bytes = "new image".toByteArray()

            studioMediaDao.getMediaByIdOverride =
                StudioMediaEntity(
                    id = "uuid-2",
                    prompt = "PCENC:v1:aes-gcm:new prompt",
                    mediaUri = "PCENC:v1:aes-gcm:file://${File(filesDir, "test.jpg").absolutePath}",
                    mediaType = "IMAGE",
                    createdAt = 1L,
                    albumId = null,
                )

            val before = filesDir.listFiles()?.toSet() ?: emptySet()
            val resultAsset = repository.saveMedia(bytes, "new prompt", "IMAGE", albumId = null)

            // Exactly one new file appears in filesDir (sandbox dirs are shared across tests).
            val newFiles = (filesDir.listFiles()?.toSet() ?: emptySet()) - before
            assertEquals(1, newFiles.size)
            assertEquals("new image", newFiles.single().readText())

            val inserted = studioMediaDao.insertCalls.single()
            assertEquals("PCENC:v1:aes-gcm:new prompt", inserted.prompt)
            assertEquals(null, inserted.albumId)
            assertEquals("uuid-2", resultAsset.id)
        }

    @Test
    fun `saveMedia stores prompt encrypted and returns plaintext domain model`() =
        runTest {
            val sourceFile = File(filesDir, "encrypted-test.jpg")
            sourceFile.writeText("image data")
            val localUri = "file://${sourceFile.absolutePath}"

            studioMediaDao.getMediaByIdOverride =
                StudioMediaEntity(
                    id = "uuid-3",
                    prompt = "PCENC:v1:aes-gcm:private prompt",
                    mediaUri = localUri,
                    mediaType = "IMAGE",
                    createdAt = 1L,
                    albumId = null,
                )

            val resultAsset = repository.saveMedia(localUri, "private prompt", "IMAGE", albumId = null)

            val inserted = studioMediaDao.insertCalls.single()
            assertEquals("PCENC:v1:aes-gcm:private prompt", inserted.prompt)
            assertEquals("PCENC:v1:aes-gcm:$localUri", inserted.mediaUri)
            assertEquals("private prompt", resultAsset.prompt)
        }

    @Test
    fun `getMediaPendingDownload returns only uploaded rows missing a local file`() =
        runTest {
            val localFile = File(filesDir, "present.jpg")
            localFile.writeText("bytes")

            studioMediaDao.uploadedEntities =
                listOf(
                    // Synced down on a fresh device: mediaUri points at the remote URL, no local file.
                    StudioMediaEntity(
                        id = "remote-only",
                        prompt = "PCENC:v1:aes-gcm:p",
                        mediaUri = "PCENC:v1:aes-gcm:https://storage/users/u/media/remote-only",
                        mediaType = "IMAGE",
                        remoteUrl = "PCENC:v1:aes-gcm:https://storage/users/u/media/remote-only",
                        syncStatus = "UPLOADED",
                    ),
                    // Source device: bytes already present locally, must be skipped.
                    StudioMediaEntity(
                        id = "has-local",
                        prompt = "PCENC:v1:aes-gcm:p",
                        mediaUri = "PCENC:v1:aes-gcm:file://${localFile.absolutePath}",
                        mediaType = "IMAGE",
                        remoteUrl = "PCENC:v1:aes-gcm:https://storage/users/u/media/has-local",
                        syncStatus = "UPLOADED",
                    ),
                    // Never uploaded (no remote URL): nothing to download.
                    StudioMediaEntity(
                        id = "no-remote",
                        prompt = "PCENC:v1:aes-gcm:p",
                        mediaUri = "PCENC:v1:aes-gcm:https://storage/users/u/media/no-remote",
                        mediaType = "IMAGE",
                        remoteUrl = null,
                        syncStatus = "UPLOADED",
                    ),
                )

            val pending = repository.getMediaPendingDownload()

            assertEquals(listOf("remote-only"), pending.map { it.id })
        }

    @Test
    fun `materializeDownloadedMedia writes bytes locally and repoints the row without re-uploading`() =
        runTest {
            val bytes = "decrypted image".toByteArray()
            studioMediaDao.getMediaByIdOverride =
                StudioMediaEntity(
                    id = "media-1",
                    prompt = "PCENC:v1:aes-gcm:p",
                    mediaUri = "PCENC:v1:aes-gcm:https://storage/users/u/media/media-1",
                    mediaType = "IMAGE",
                    remoteUrl = "PCENC:v1:aes-gcm:https://storage/users/u/media/media-1",
                    syncStatus = "UPLOADED",
                    pendingSync = 0,
                )

            repository.materializeDownloadedMedia("media-1", bytes)

            val written = File(filesDir, "studio_synced_media-1.png")
            assertTrue(written.exists())
            assertEquals("decrypted image", written.readText())

            val inserted = studioMediaDao.insertCalls.single()
            assertEquals("media-1", inserted.id)
            assertEquals("PCENC:v1:aes-gcm:file://${written.absolutePath}", inserted.mediaUri)
            assertEquals("UPLOADED", inserted.syncStatus)
            assertEquals(0, inserted.pendingSync)
        }

    @Test
    fun `renameAlbum trims name and updates album row`() =
        runTest {
            studioAlbumDao.entities["42"] =
                StudioAlbumEntity(id = "42", name = "PCENC:v1:aes-gcm:Old", pendingSync = 0)
            repository.renameAlbum("42", "  Sunrise  ")

            val inserted = studioAlbumDao.insertCalls.single()
            assertEquals("42", inserted.id)
            assertEquals("PCENC:v1:aes-gcm:Sunrise", inserted.name)
        }

    @Test
    fun `renameAlbum ignores blank names`() =
        runTest {
            repository.renameAlbum("42", "   ")

            assertTrue(studioAlbumDao.insertCalls.isEmpty())
        }

    @Test
    fun `deleteAlbum reassigns media to null before deleting album`() =
        runTest {
            repository.deleteAlbum("7")

            assertEquals(listOf("7"), studioMediaDao.reassignCalls.map { it.first })
            assertEquals(listOf("7"), studioAlbumDao.softDeleteCalls.map { it.first })
            assertTrue(
                callLog.indexOf("reassignMediaToDefault") < callLog.indexOf("albumSoftDeleteById"),
                "media must be reassigned before the album row is deleted: $callLog",
            )
        }

    @Test
    fun `deleteMedia with UUID calls DAO`() =
        runTest {
            val uuid = "5a8277e4-dcc0-45d4-81ab-8fb1324c96c9"
            repository.deleteMedia(uuid)

            assertEquals(listOf(uuid), studioMediaDao.softDeleteCalls.map { it.first })
        }

    @Test
    fun `saveMedia maps 'default' albumId to null`() =
        runTest {
            studioMediaDao.getMediaByIdOverride =
                StudioMediaEntity(
                    id = "uuid-1",
                    prompt = "PCENC:v1:aes-gcm:prompt",
                    mediaUri = "PCENC:v1:aes-gcm:uri",
                    mediaType = "IMAGE",
                    createdAt = 1L,
                    albumId = null,
                )

            repository.saveMedia("uri", "prompt", "IMAGE", albumId = DEFAULT_GALLERY_ALBUM_ID)

            assertEquals(null, studioMediaDao.insertCalls.single().albumId)
        }

    @Test
    fun `moveMediaToAlbum maps 'default' albumId to null`() =
        runTest {
            repository.moveMediaToAlbum(listOf("media-1"), DEFAULT_GALLERY_ALBUM_ID)

            val call = studioMediaDao.updateMediaAlbumCalls.single()
            assertEquals(listOf("media-1"), call.first)
            assertEquals(null, call.second)
        }

    @Test
    fun `readMediaBytes reads content uri using content resolver`() =
        runTest {
            val uriString = "content://media/external/images/media/1"
            val bytes = "content data".toByteArray()
            val uri = Uri.parse(uriString)
            shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))

            val result = repository.readMediaBytes(uriString)

            assertNotNull(result)
            assertArrayEquals(bytes, result)
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
            studioMediaDao.entities[existingEntity.id] = existingEntity

            repository.updateMedia(
                com.browntowndev.enclave.domain.port.repository.StudioMediaAsset(
                    id = "media-uploaded",
                    prompt = "updated prompt",
                    localUri = "file:///data/files/photo.jpg",
                    mediaType = "IMAGE",
                    createdAt = 1_000L,
                    albumId = null,
                ),
            )

            val inserted = studioMediaDao.insertCalls.single()
            assertEquals("PCENC:v1:aes-gcm:https://cdn.example.com/photo.jpg", inserted.remoteUrl)
            assertEquals("UPLOADED", inserted.syncStatus)
            assertEquals("PCENC:v1:aes-gcm:updated prompt", inserted.prompt)
            assertEquals("PCENC:v1:aes-gcm:file:///data/files/photo.jpg", inserted.mediaUri)
        }

    @Test
    fun `updateMedia uses PENDING_UPLOAD syncStatus when entity has never been uploaded`() =
        runTest {
            repository.updateMedia(
                com.browntowndev.enclave.domain.port.repository.StudioMediaAsset(
                    id = "media-new",
                    prompt = "brand new",
                    localUri = "file:///data/files/new.jpg",
                    mediaType = "IMAGE",
                    createdAt = 2_000L,
                    albumId = null,
                ),
            )

            val inserted = studioMediaDao.insertCalls.single()
            assertEquals(null, inserted.remoteUrl)
            assertEquals("PENDING_UPLOAD", inserted.syncStatus)
        }

    @Test
    fun `updateMedia maps DEFAULT_GALLERY_ALBUM_ID to null albumId`() =
        runTest {
            repository.updateMedia(
                com.browntowndev.enclave.domain.port.repository.StudioMediaAsset(
                    id = "media-1",
                    prompt = "test",
                    localUri = "file:///data/files/test.jpg",
                    mediaType = "IMAGE",
                    createdAt = 1_000L,
                    albumId = DEFAULT_GALLERY_ALBUM_ID,
                ),
            )

            assertEquals(null, studioMediaDao.insertCalls.single().albumId)
        }
}

private class FakeTransactionProvider : TransactionProvider {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
}
