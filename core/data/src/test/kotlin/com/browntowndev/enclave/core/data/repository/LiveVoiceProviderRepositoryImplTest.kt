package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.LiveVoiceProviderDao
import com.browntowndev.enclave.core.data.local.LiveVoiceProviderEntity
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class LiveVoiceProviderRepositoryImplTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var liveVoiceProviderDao: LiveVoiceProviderDao
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var repository: LiveVoiceProviderRepositoryImpl

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        liveVoiceProviderDao = database.liveVoiceProviderDao()
        apiKeyManager =
            testApiKeyManager(
                context,
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
                "live_voice_provider_repository_test_prefs",
            )
        repository =
            LiveVoiceProviderRepositoryImpl(
                RecordingCloudSyncTrigger(),
                liveVoiceProviderDao,
                apiKeyManager,
                testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
                database.realReadRepairApplier(),
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
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
            entities.forEach { liveVoiceProviderDao.upsertLiveVoiceProvider(it) }

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

            val resultId = repository.saveLiveVoiceProvider(asset, "sk-test")

            assertEquals("1", resultId.value)
            assertEquals("alias-x", liveVoiceProviderDao.getLiveVoiceProvider("1")?.credentialAlias)
            assertEquals("sk-test", apiKeyManager.get("alias-x"))
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

            repository.saveLiveVoiceProvider(asset, "k")

            assertEquals("lv-abc", liveVoiceProviderDao.getLiveVoiceProvider("abc")?.credentialAlias)
            assertEquals("k", apiKeyManager.get("lv-abc"))
        }
}
