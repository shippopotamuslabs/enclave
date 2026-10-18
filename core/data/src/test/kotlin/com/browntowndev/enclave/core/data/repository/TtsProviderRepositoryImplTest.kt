package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.TtsProviderEntity
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
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
class TtsProviderRepositoryImplTest {
    private lateinit var database: EnclaveDatabase
    private val ttsProviderDao = FakeTtsProviderDao()
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private lateinit var repository: TtsProviderRepositoryImpl

    @BeforeEach
    fun setUp() {
        syncTrigger = RecordingCloudSyncTrigger()
        database = createInMemoryEnclaveDatabase()
        val context = ApplicationProvider.getApplicationContext<Context>()
        apiKeyManager =
            testApiKeyManager(context, CoroutineScope(SupervisorJob() + Dispatchers.Default), "tts_provider_test_prefs")
        repository =
            TtsProviderRepositoryImpl(
                syncTrigger,
                ttsProviderDao,
                apiKeyManager,
                testSensitiveSelfHealer(FakeLocalDataEncryptionService(), FakeLoggingPort()),
                database.realReadRepairApplier(),
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getTtsProviders returns mapped domain models`() =
        runTest {
            val entities =
                listOf(
                    TtsProviderEntity("1", "OpenAI TTS", ApiProvider.OPENAI, "alloy", null, "https://api.openai.com", "alias-1"),
                )
            ttsProviderDao.flowEntities = entities

            val result = repository.getTtsProviders().first()

            assertEquals(1, result.size)
            assertEquals("1", result[0].id.value)
            assertEquals("OpenAI TTS", result[0].displayName)
            assertEquals(ApiProvider.OPENAI, result[0].provider)
            assertEquals("alloy", result[0].voiceName)
            assertEquals("https://api.openai.com", result[0].baseUrl)
        }

    @Test
    fun `saveTtsProvider inserts entity and saves API key`() =
        runTest {
            val asset = TtsProviderAsset(TtsProviderId("1"), "xAI TTS", ApiProvider.XAI, "eve", null, "https://api.x.ai", "alias-x")
            val apiKey = "sk-test"

            val resultId = repository.saveTtsProvider(asset, apiKey)

            assertEquals("1", resultId.value)
            val upserted = ttsProviderDao.upsertCalls.single()
            assertEquals("1", upserted.id)
            assertEquals("PCENC:v1:aes-gcm:xAI TTS", upserted.displayName)
            assertEquals(ApiProvider.XAI, upserted.provider)
            assertEquals("PCENC:v1:aes-gcm:https://api.x.ai", upserted.baseUrl)
            assertEquals("alias-x", upserted.credentialAlias)
            assertEquals(apiKey, apiKeyManager.get("alias-x"))
        }

    @Test
    fun `saveTtsProvider generates credential alias when draft alias is blank`() =
        runTest {
            val asset = TtsProviderAsset(TtsProviderId("42"), "xAI TTS", ApiProvider.XAI, "eve", null, null, "")

            repository.saveTtsProvider(asset, "sk-test")

            assertEquals("tts-42", ttsProviderDao.upsertCalls.single().credentialAlias)
            assertEquals("sk-test", apiKeyManager.get("tts-42"))
        }

    @Test
    fun `saveTtsProvider stores sensitive fields encrypted and reads them decrypted`() =
        runTest {
            val asset =
                TtsProviderAsset(
                    TtsProviderId("1"),
                    "xAI TTS",
                    ApiProvider.XAI,
                    "eve",
                    "tts-1",
                    "https://api.x.ai",
                    "alias-x",
                )
            ttsProviderDao.entities["1"] =
                TtsProviderEntity(
                    "1",
                    "PCENC:v1:aes-gcm:xAI TTS",
                    ApiProvider.XAI,
                    "PCENC:v1:aes-gcm:eve",
                    "PCENC:v1:aes-gcm:tts-1",
                    "PCENC:v1:aes-gcm:https://api.x.ai",
                    "alias-x",
                )

            repository.saveTtsProvider(asset, null)
            val loaded = repository.getTtsProvider(TtsProviderId("1"))

            val upserted = ttsProviderDao.upsertCalls.single()
            assertEquals("PCENC:v1:aes-gcm:xAI TTS", upserted.displayName)
            assertEquals("PCENC:v1:aes-gcm:eve", upserted.voiceName)
            assertEquals("PCENC:v1:aes-gcm:tts-1", upserted.modelName)
            assertEquals("PCENC:v1:aes-gcm:https://api.x.ai", upserted.baseUrl)
            assertEquals(asset.displayName, loaded?.displayName)
            assertEquals(asset.voiceName, loaded?.voiceName)
            assertEquals(asset.modelName, loaded?.modelName)
            assertEquals(asset.baseUrl, loaded?.baseUrl)
        }

    @Test
    fun `deleteTtsProvider deletes entity and removes API key`() =
        runTest {
            val entity = TtsProviderEntity("1", "OpenAI TTS", ApiProvider.OPENAI, "alloy", null, "https://api.openai.com", "alias-1")
            ttsProviderDao.entities["1"] = entity
            apiKeyManager.save("alias-1", "sk-test")

            repository.deleteTtsProvider(TtsProviderId("1"))

            assertEquals(listOf("1"), ttsProviderDao.softDeleteCalls.map { it.first })
            assertNull(apiKeyManager.get("alias-1"))
        }

    @Test
    fun `saveTtsProvider calls triggerSync once`() =
        runTest {
            val asset = TtsProviderAsset(TtsProviderId("1"), "xAI TTS", ApiProvider.XAI, "eve", null, null, "alias-x")

            repository.saveTtsProvider(asset, null)

            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `deleteTtsProvider calls triggerSync once`() =
        runTest {
            val entity = TtsProviderEntity("1", "OpenAI TTS", ApiProvider.OPENAI, "alloy", null, null, "alias-1")
            ttsProviderDao.entities["1"] = entity

            repository.deleteTtsProvider(TtsProviderId("1"))

            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `getTtsProviders read-only does not call triggerSync`() =
        runTest {
            ttsProviderDao.flowEntities = emptyList()

            repository.getTtsProviders().first()

            assertEquals(0, syncTrigger.triggerCalls)
        }

    @Test
    fun `saveTtsProvider upserts entity with pendingSync true`() =
        runTest {
            val asset = TtsProviderAsset(TtsProviderId("42"), "My TTS", ApiProvider.OPENAI, "nova", null, null, "alias-42")

            repository.saveTtsProvider(asset, null)

            assertEquals(1, ttsProviderDao.upsertCalls.single().pendingSync)
        }
}
