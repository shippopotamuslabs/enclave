package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.TtsProviderEntity
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
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

class TtsProviderRepositoryImplTest {
    private val ttsProviderDao: TtsProviderDao = mockk()
    private val apiKeyManager: ApiKeyManager = mockk(relaxed = true)
    private val syncTrigger: CloudSyncTrigger = mockk(relaxed = true)
    private val readRepairApplier = mockk<SensitiveRoomReadRepairApplier>(relaxed = true)
    private lateinit var repository: TtsProviderRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository =
            TtsProviderRepositoryImpl(
                syncTrigger,
                ttsProviderDao,
                apiKeyManager,
                testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
                readRepairApplier,
            )
    }

    @Test
    fun `getTtsProviders returns mapped domain models`() =
        runTest {
            val entities =
                listOf(
                    TtsProviderEntity("1", "OpenAI TTS", ApiProvider.OPENAI, "alloy", null, "https://api.openai.com", "alias-1"),
                )
            coEvery { ttsProviderDao.getTtsProviders() } returns flowOf(entities)

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
            coEvery { ttsProviderDao.upsertTtsProvider(any()) } returns Unit

            val resultId = repository.saveTtsProvider(asset, apiKey)

            assertEquals("1", resultId.value)
            coVerify {
                ttsProviderDao.upsertTtsProvider(
                    match {
                        it.id == "1" &&
                            it.displayName == "PCENC:v1:aes-gcm:xAI TTS" &&
                            it.provider == ApiProvider.XAI &&
                            it.baseUrl == "PCENC:v1:aes-gcm:https://api.x.ai" &&
                            it.credentialAlias == "alias-x"
                    },
                )
                apiKeyManager.save("alias-x", apiKey)
            }
        }

    @Test
    fun `saveTtsProvider generates credential alias when draft alias is blank`() =
        runTest {
            val asset = TtsProviderAsset(TtsProviderId("42"), "xAI TTS", ApiProvider.XAI, "eve", null, null, "")
            coEvery { ttsProviderDao.upsertTtsProvider(any()) } returns Unit

            repository.saveTtsProvider(asset, "sk-test")

            coVerify {
                ttsProviderDao.upsertTtsProvider(match { it.credentialAlias == "tts-42" })
            }
            verify { apiKeyManager.save("tts-42", "sk-test") }
        }

    @Test
    fun `saveTtsProvider stores sensitive fields encrypted and reads them decrypted`() =
        runTest {
            val encryptedRepository =
                TtsProviderRepositoryImpl(
                    syncTrigger,
                    ttsProviderDao,
                    apiKeyManager,
                    testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
                    readRepairApplier,
                )
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
            coEvery { ttsProviderDao.upsertTtsProvider(any()) } returns Unit
            coEvery { ttsProviderDao.getTtsProvider("1") } returns
                TtsProviderEntity(
                    "1",
                    "PCENC:v1:aes-gcm:xAI TTS",
                    ApiProvider.XAI,
                    "PCENC:v1:aes-gcm:eve",
                    "PCENC:v1:aes-gcm:tts-1",
                    "PCENC:v1:aes-gcm:https://api.x.ai",
                    "alias-x",
                )

            encryptedRepository.saveTtsProvider(asset, null)
            val loaded = encryptedRepository.getTtsProvider(TtsProviderId("1"))

            coVerify {
                ttsProviderDao.upsertTtsProvider(
                    match {
                        it.displayName == "PCENC:v1:aes-gcm:xAI TTS" &&
                            it.voiceName == "PCENC:v1:aes-gcm:eve" &&
                            it.modelName == "PCENC:v1:aes-gcm:tts-1" &&
                            it.baseUrl == "PCENC:v1:aes-gcm:https://api.x.ai"
                    },
                )
            }
            assertEquals(asset.displayName, loaded?.displayName)
            assertEquals(asset.voiceName, loaded?.voiceName)
            assertEquals(asset.modelName, loaded?.modelName)
            assertEquals(asset.baseUrl, loaded?.baseUrl)
        }

    @Test
    fun `deleteTtsProvider deletes entity and removes API key`() =
        runTest {
            val entity = TtsProviderEntity("1", "OpenAI TTS", ApiProvider.OPENAI, "alloy", null, "https://api.openai.com", "alias-1")
            coEvery { ttsProviderDao.getTtsProvider("1") } returns entity
            coEvery { ttsProviderDao.softDeleteById("1", any()) } returns Unit

            repository.deleteTtsProvider(TtsProviderId("1"))

            coVerify {
                ttsProviderDao.softDeleteById("1", any())
                apiKeyManager.delete("alias-1")
            }
        }

    @Test
    fun `saveTtsProvider calls triggerSync once`() =
        runTest {
            val asset = TtsProviderAsset(TtsProviderId("1"), "xAI TTS", ApiProvider.XAI, "eve", null, null, "alias-x")
            coEvery { ttsProviderDao.upsertTtsProvider(any()) } returns Unit

            repository.saveTtsProvider(asset, null)

            verify(exactly = 1) { syncTrigger.trigger() }
        }

    @Test
    fun `deleteTtsProvider calls triggerSync once`() =
        runTest {
            val entity = TtsProviderEntity("1", "OpenAI TTS", ApiProvider.OPENAI, "alloy", null, null, "alias-1")
            coEvery { ttsProviderDao.getTtsProvider("1") } returns entity
            coEvery { ttsProviderDao.softDeleteById("1", any()) } returns Unit

            repository.deleteTtsProvider(TtsProviderId("1"))

            verify(exactly = 1) { syncTrigger.trigger() }
        }

    @Test
    fun `getTtsProviders read-only does not call triggerSync`() =
        runTest {
            coEvery { ttsProviderDao.getTtsProviders() } returns flowOf(emptyList())

            repository.getTtsProviders().first()

            verify(exactly = 0) { syncTrigger.trigger() }
        }

    @Test
    fun `saveTtsProvider upserts entity with pendingSync true`() =
        runTest {
            val asset = TtsProviderAsset(TtsProviderId("42"), "My TTS", ApiProvider.OPENAI, "nova", null, null, "alias-42")
            coEvery { ttsProviderDao.upsertTtsProvider(any()) } returns Unit

            repository.saveTtsProvider(asset, null)

            coVerify {
                ttsProviderDao.upsertTtsProvider(match { it.pendingSync == 1 })
            }
        }
}
