package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.LiveVoiceProviderDao
import com.browntowndev.enclave.core.data.local.LiveVoiceProviderEntity
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiveVoiceProviderRepositoryImplTest {
    private val liveVoiceProviderDao: LiveVoiceProviderDao = mockk()
    private val apiKeyManager: ApiKeyManager = mockk(relaxed = true)
    private val syncTrigger: CloudSyncTrigger = mockk(relaxed = true)
    private val readRepairApplier = mockk<SensitiveRoomReadRepairApplier>(relaxed = true)
    private lateinit var repository: LiveVoiceProviderRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository =
            LiveVoiceProviderRepositoryImpl(
                syncTrigger,
                liveVoiceProviderDao,
                apiKeyManager,
                testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
                readRepairApplier,
            )
    }

    @Test
    @DisplayName("LV-REPO-001")
    fun lvRepo001_getLiveVoiceProvidersMapsEntityToDomain() =
        runTest {
            val entities =
                listOf(
                    LiveVoiceProviderEntity(
                        id = "1",
                        displayName = "OpenAI RT",
                        provider = ApiProvider.OPENAI,
                        voiceName = "alloy",
                        modelName = null,
                        baseUrl = "https://api.openai.com",
                        credentialAlias = "alias-1",
                    ),
                )
            coEvery { liveVoiceProviderDao.getLiveVoiceProviders() } returns flowOf(entities)

            val result = repository.getLiveVoiceProviders().first()

            assertEquals(1, result.size)
            assertEquals("1", result[0].id.value)
            assertEquals("OpenAI RT", result[0].displayName)
            assertEquals(ApiProvider.OPENAI, result[0].provider)
            assertEquals("alloy", result[0].voiceName)
            assertEquals("https://api.openai.com", result[0].baseUrl)
        }

    @Test
    @DisplayName("LV-REPO-002")
    fun lvRepo002_saveInsertsEntityAndStoresApiKey() =
        runTest {
            val asset =
                LiveVoiceProviderAsset(
                    id = LiveVoiceProviderId("1"),
                    displayName = "xAI RT",
                    provider = ApiProvider.XAI,
                    voiceName = "eve",
                    modelName = null,
                    baseUrl = "https://api.x.ai/v1",
                    credentialAlias = "alias-x",
                )
            coEvery { liveVoiceProviderDao.upsertLiveVoiceProvider(any()) } returns Unit

            val resultId = repository.saveLiveVoiceProvider(asset, "sk-test")

            assertEquals("1", resultId.value)
            coVerify {
                liveVoiceProviderDao.upsertLiveVoiceProvider(any())
                apiKeyManager.save("alias-x", "sk-test")
            }
        }

    @Test
    @DisplayName("LV-REPO-003")
    fun lvRepo003_saveDefaultsBlankCredentialAliasToLvId() =
        runTest {
            val asset =
                LiveVoiceProviderAsset(
                    id = LiveVoiceProviderId("abc"),
                    displayName = "xAI RT",
                    provider = ApiProvider.XAI,
                    voiceName = "eve",
                    modelName = null,
                    baseUrl = null,
                    credentialAlias = "  ",
                )
            coEvery { liveVoiceProviderDao.upsertLiveVoiceProvider(any()) } returns Unit

            repository.saveLiveVoiceProvider(asset, "k")

            coVerify {
                liveVoiceProviderDao.upsertLiveVoiceProvider(match { it.credentialAlias == "lv-abc" })
            }
            verify { apiKeyManager.save("lv-abc", "k") }
        }
}
