package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MediaProviderEntity
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import org.robolectric.annotation.Config as RoboConfig

/**
 * Translated from MockK to hand-written fakes (ENC-28): a recording DAO
 * fake, a real [ApiKeyManager] over Robolectric prefs (key saves/deletes
 * are asserted as stored state), and a recording sync trigger. The
 * incidental read-repair applier is the real implementation over an
 * in-memory Room database.
 */
@ExtendWith(RobolectricExtension::class)
@RoboConfig(sdk = [34])
class MediaProviderRepositoryImplTest {
    private lateinit var database: EnclaveDatabase
    private val mediaProviderDao = FakeMediaProviderDao()
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private val healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService(), FakeLoggingPort())
    private lateinit var repository: MediaProviderRepositoryImpl

    @BeforeEach
    fun setUp() {
        syncTrigger = RecordingCloudSyncTrigger()
        database = createInMemoryEnclaveDatabase()
        val context = ApplicationProvider.getApplicationContext<Context>()
        apiKeyManager =
            testApiKeyManager(context, CoroutineScope(SupervisorJob() + Dispatchers.Default), "media_provider_test_prefs")
        repository =
            MediaProviderRepositoryImpl(
                syncTrigger,
                mediaProviderDao,
                apiKeyManager,
                healer,
                database.realReadRepairApplier(),
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getMediaProviders returns mapped domain models`() =
        runTest {
            val entities =
                listOf(
                    MediaProviderEntity("1", "Imagen", ApiProvider.GOOGLE, MediaCapability.IMAGE, "imagen-3", null, "google-key"),
                )
            mediaProviderDao.observedEntities = entities

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

            val resultId = repository.saveMediaProvider(asset, "api-key")

            assertEquals("1", resultId.value)
            assertEquals(1, mediaProviderDao.upsertCalls.size)
            assertEquals("api-key", apiKeyManager.get("google-key"))
        }

    @Test
    fun `saveMediaProvider generates credential alias when draft alias is blank`() =
        runTest {
            val asset =
                MediaProviderAsset(
                    MediaProviderId("42"),
                    "Imagen",
                    ApiProvider.GOOGLE,
                    MediaCapability.IMAGE,
                    "imagen-3",
                    null,
                    "",
                )

            repository.saveMediaProvider(asset, "api-key")

            assertEquals("media-42", mediaProviderDao.upsertCalls.single().credentialAlias)
            assertEquals("api-key", apiKeyManager.get("media-42"))
        }

    @Test
    fun `saveMediaProvider stores sensitive fields encrypted and reads them decrypted`() =
        runTest {
            val asset =
                MediaProviderAsset(
                    MediaProviderId("1"),
                    "Imagen",
                    ApiProvider.GOOGLE,
                    MediaCapability.IMAGE,
                    "imagen-3",
                    "https://api.google.com",
                    "google-key",
                )
            mediaProviderDao.entities["1"] =
                MediaProviderEntity(
                    "1",
                    "PCENC:v1:aes-gcm:Imagen",
                    ApiProvider.GOOGLE,
                    MediaCapability.IMAGE,
                    "PCENC:v1:aes-gcm:imagen-3",
                    "PCENC:v1:aes-gcm:https://api.google.com",
                    "google-key",
                )

            repository.saveMediaProvider(asset, null)
            val loaded = repository.getMediaProvider(MediaProviderId("1"))

            val upserted = mediaProviderDao.upsertCalls.single()
            assertEquals("PCENC:v1:aes-gcm:Imagen", upserted.displayName)
            assertEquals("PCENC:v1:aes-gcm:imagen-3", upserted.modelName)
            assertEquals("PCENC:v1:aes-gcm:https://api.google.com", upserted.baseUrl)
            assertEquals(asset.displayName, loaded?.displayName)
            assertEquals(asset.modelName, loaded?.modelName)
            assertEquals(asset.baseUrl, loaded?.baseUrl)
        }

    @Test
    fun `deleteMediaProvider deletes entity and removes API key`() =
        runTest {
            val entity = MediaProviderEntity("1", "Imagen", ApiProvider.GOOGLE, MediaCapability.IMAGE, null, null, "google-key")
            mediaProviderDao.entities["1"] = entity
            apiKeyManager.save("google-key", "api-key")

            repository.deleteMediaProvider(MediaProviderId("1"))

            assertEquals(listOf("1"), mediaProviderDao.softDeleteCalls.map { it.first })
            assertNull(apiKeyManager.get("google-key"))
        }

    @Test
    fun `saveMediaProvider calls triggerSync once`() =
        runTest {
            val asset =
                MediaProviderAsset(MediaProviderId("1"), "Imagen", ApiProvider.GOOGLE, MediaCapability.IMAGE, null, null, "google-key")

            repository.saveMediaProvider(asset, null)

            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `deleteMediaProvider calls triggerSync once`() =
        runTest {
            val entity = MediaProviderEntity("1", "Imagen", ApiProvider.GOOGLE, MediaCapability.IMAGE, null, null, "google-key")
            mediaProviderDao.entities["1"] = entity

            repository.deleteMediaProvider(MediaProviderId("1"))

            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `getMediaProviders read-only does not call triggerSync`() =
        runTest {
            mediaProviderDao.observedEntities = emptyList()

            repository.getMediaProviders().first()

            assertEquals(0, syncTrigger.triggerCalls)
        }

    @Test
    fun `saveMediaProvider upserts entity with pendingSync true`() =
        runTest {
            val asset =
                MediaProviderAsset(MediaProviderId("42"), "Kling", ApiProvider.OPENAI, MediaCapability.VIDEO, null, null, "kling-key")

            repository.saveMediaProvider(asset, null)

            assertEquals(1, mediaProviderDao.upsertCalls.single().pendingSync)
        }
}
