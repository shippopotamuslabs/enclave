package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.StudioMediaEntity
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import org.robolectric.annotation.Config as RoboConfig

/**
 * Translated from MockK to hand-written fakes (ENC-28): recording DAO
 * fakes capture the rows the repository writes, the recording sync trigger
 * counts its calls, and Context is the Robolectric application context.
 * Incidental cleanup DAO / read-repair applier are the real implementations
 * over an in-memory Room database.
 */
@ExtendWith(RobolectricExtension::class)
@RoboConfig(sdk = [34])
class StudioRepositorySyncTest {
    private lateinit var database: EnclaveDatabase
    private val studioMediaDao = FakeStudioMediaDao()
    private val studioAlbumDao = FakeStudioAlbumDao()
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private val transactionProvider = FakeTransactionProvider()
    private lateinit var context: Context
    private val healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService(), FakeLoggingPort())
    private lateinit var activeGenerationPersistence: ActiveStudioGenerationPersistence

    private lateinit var repository: StudioRepositoryImpl

    @BeforeEach
    fun setup() {
        syncTrigger = RecordingCloudSyncTrigger()
        database = createInMemoryEnclaveDatabase()
        context = ApplicationProvider.getApplicationContext()
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
    fun `saveMedia sets pendingSync to 1 and triggers sync`() =
        runTest {
            studioMediaDao.getMediaByIdOverride =
                StudioMediaEntity(
                    id = "id",
                    prompt = "PCENC:v1:aes-gcm:prompt",
                    mediaUri = "PCENC:v1:aes-gcm:uri",
                    mediaType = "IMAGE",
                    pendingSync = 1,
                )

            repository.saveMedia("uri", "prompt", "IMAGE", albumId = null)

            assertEquals(1, studioMediaDao.insertCalls.single().pendingSync)
            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `createAlbum sets pendingSync to 1 and triggers sync`() =
        runTest {
            repository.createAlbum("New Album")

            assertEquals(1, studioAlbumDao.insertCalls.single().pendingSync)
            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `moveMediaToAlbum triggers sync`() =
        runTest {
            repository.moveMediaToAlbum(listOf("media-1"), "album-1")

            assertEquals(1, studioMediaDao.updateMediaAlbumCalls.size)
            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `deleteMedia triggers sync`() =
        runTest {
            repository.deleteMedia("media-1")

            assertEquals(listOf("media-1"), studioMediaDao.softDeleteCalls.map { it.first })
            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `deleteAlbum triggers sync`() =
        runTest {
            repository.deleteAlbum("album-1")

            assertEquals(1, syncTrigger.triggerCalls)
        }

    private class FakeTransactionProvider : TransactionProvider {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
}
