package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.MediaProviderDao
import com.browntowndev.pocketcrew.core.data.local.MediaProviderEntity
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MediaProviderRepositoryImplTest {
    private val mediaProviderDao: MediaProviderDao = mockk()
    private val apiKeyManager: ApiKeyManager = mockk(relaxed = true)
    private val syncTrigger: CloudSyncTrigger = mockk(relaxed = true)
    private lateinit var repository: MediaProviderRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository = MediaProviderRepositoryImpl(syncTrigger, mediaProviderDao, apiKeyManager)
    }

    @Test
    fun `getMediaProviders returns mapped domain models`() =
        runTest {
            val entities =
                listOf(
                    MediaProviderEntity("1", "Imagen", ApiProvider.GOOGLE, MediaCapability.IMAGE, "imagen-3", null, "google-key"),
                )
            coEvery { mediaProviderDao.observeAllMediaProviders() } returns flowOf(entities)

            val result = repository.getMediaProviders().first()

            assertEquals(1, result.size)
            assertEquals("1", result[0].id.value)
            assertEquals("Imagen", result[0].displayName)
            assertEquals(ApiProvider.GOOGLE, result[0].provider)
            assertEquals(MediaCapability.IMAGE, result[0].capability)
        }

    @Test
    fun `saveMediaProvider inserts entity and saves API key`() =
        runTest {
            val asset =
                MediaProviderAsset(
                    MediaProviderId("1"),
                    "Imagen",
                    ApiProvider.GOOGLE,
                    MediaCapability.IMAGE,
                    "imagen-3",
                    null,
                    "google-key",
                )
            coEvery { mediaProviderDao.upsert(any()) } returns Unit

            val resultId = repository.saveMediaProvider(asset, "api-key")

            assertEquals("1", resultId.value)
            coVerify { mediaProviderDao.upsert(any()) }
            verify { apiKeyManager.save("google-key", "api-key") }
        }

    @Test
    fun `deleteMediaProvider deletes entity and removes API key`() =
        runTest {
            val entity = MediaProviderEntity("1", "Imagen", ApiProvider.GOOGLE, MediaCapability.IMAGE, null, null, "google-key")
            coEvery { mediaProviderDao.getMediaProvider("1") } returns entity
            coEvery { mediaProviderDao.softDeleteById("1", any()) } returns Unit

            repository.deleteMediaProvider(MediaProviderId("1"))

            coVerify { mediaProviderDao.softDeleteById("1", any()) }
            verify { apiKeyManager.delete("google-key") }
        }

    @Test
    fun `saveMediaProvider calls triggerSync once`() =
        runTest {
            val asset =
                MediaProviderAsset(MediaProviderId("1"), "Imagen", ApiProvider.GOOGLE, MediaCapability.IMAGE, null, null, "google-key")
            coEvery { mediaProviderDao.upsert(any()) } returns Unit

            repository.saveMediaProvider(asset, null)

            verify(exactly = 1) { syncTrigger.trigger() }
        }

    @Test
    fun `deleteMediaProvider calls triggerSync once`() =
        runTest {
            val entity = MediaProviderEntity("1", "Imagen", ApiProvider.GOOGLE, MediaCapability.IMAGE, null, null, "google-key")
            coEvery { mediaProviderDao.getMediaProvider("1") } returns entity
            coEvery { mediaProviderDao.softDeleteById("1", any()) } returns Unit

            repository.deleteMediaProvider(MediaProviderId("1"))

            verify(exactly = 1) { syncTrigger.trigger() }
        }

    @Test
    fun `getMediaProviders read-only does not call triggerSync`() =
        runTest {
            coEvery { mediaProviderDao.observeAllMediaProviders() } returns flowOf(emptyList())

            repository.getMediaProviders().first()

            verify(exactly = 0) { syncTrigger.trigger() }
        }

    @Test
    fun `saveMediaProvider upserts entity with pendingSync true`() =
        runTest {
            val asset =
                MediaProviderAsset(MediaProviderId("42"), "Kling", ApiProvider.OPENAI, MediaCapability.VIDEO, null, null, "kling-key")
            coEvery { mediaProviderDao.upsert(any()) } returns Unit

            repository.saveMediaProvider(asset, null)

            coVerify {
                mediaProviderDao.upsert(match { it.pendingSync == 1 })
            }
        }
}
