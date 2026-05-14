package com.browntowndev.pocketcrew.core.data.repository

import android.content.Context
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaEntity
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class StudioRepositorySyncTest {
    private val studioMediaDao = mockk<StudioMediaDao>(relaxed = true)
    private val studioAlbumDao = mockk<StudioAlbumDao>(relaxed = true)
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val transactionProvider = FakeTransactionProvider()
    private val context = mockk<Context>(relaxed = true)
    private val healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService())
    private val readRepairApplier = mockk<SensitiveRoomReadRepairApplier>(relaxed = true)

    private lateinit var repository: StudioRepositoryImpl

    @BeforeEach
    fun setup() {
        every { context.filesDir } returns File("/tmp")

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
    fun `saveMedia sets pendingSync to 1 and triggers sync`() =
        runTest {
            coEvery { studioMediaDao.getMediaById(any()) } returns
                StudioMediaEntity(
                    id = "id",
                    prompt = "PCENC:v1:aes-gcm:prompt",
                    mediaUri = "PCENC:v1:aes-gcm:uri",
                    mediaType = "IMAGE",
                    pendingSync = 1,
                )

            repository.saveMedia("uri", "prompt", "IMAGE", albumId = null)

            coVerify {
                studioMediaDao.insertMedia(match { it.pendingSync == 1 })
                syncTrigger.trigger()
            }
        }

    @Test
    fun `createAlbum sets pendingSync to 1 and triggers sync`() =
        runTest {
            repository.createAlbum("New Album")

            coVerify {
                studioAlbumDao.insertAlbum(match { it.pendingSync == 1 })
                syncTrigger.trigger()
            }
        }

    @Test
    fun `moveMediaToAlbum triggers sync`() =
        runTest {
            repository.moveMediaToAlbum(listOf("media-1"), "album-1")

            coVerify {
                studioMediaDao.updateMediaAlbum(any(), any(), any())
                syncTrigger.trigger()
            }
        }

    @Test
    fun `deleteMedia triggers sync`() =
        runTest {
            repository.deleteMedia("media-1")

            coVerify {
                studioMediaDao.softDeleteById("media-1", any())
                syncTrigger.trigger()
            }
        }

    @Test
    fun `deleteAlbum triggers sync`() =
        runTest {
            repository.deleteAlbum("album-1")

            coVerify {
                syncTrigger.trigger()
            }
        }

    private class FakeTransactionProvider : TransactionProvider {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
}
