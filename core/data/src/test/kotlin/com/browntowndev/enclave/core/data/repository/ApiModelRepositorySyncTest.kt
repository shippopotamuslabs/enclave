package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiCredentialsEntity
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ApiModelRepositorySyncTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private lateinit var apiCredentialsDao: ApiCredentialsDao
    private lateinit var apiModelConfigurationsDao: ApiModelConfigurationsDao
    private lateinit var apiKeyManager: ApiKeyManager
    private val cryptoManager = FakeCryptoManagerPort()

    private lateinit var repository: ApiModelRepositoryImpl

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        syncTrigger = RecordingCloudSyncTrigger()
        apiCredentialsDao = database.apiCredentialsDao()
        apiModelConfigurationsDao = database.apiModelConfigurationsDao()
        apiKeyManager =
            testApiKeyManager(
                context,
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
                "api_model_repository_sync_test_prefs",
            )
        repository =
            ApiModelRepositoryImpl(
                syncTrigger = syncTrigger,
                apiCredentialsDao = apiCredentialsDao,
                apiModelConfigurationsDao = apiModelConfigurationsDao,
                apiKeyManager = apiKeyManager,
                cryptoManager = cryptoManager,
                healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
                readRepairApplier = database.realReadRepairApplier(),
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saveCredentials sets pendingSync to 1 and triggers sync`() =
        runTest {
            val credentials =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "alias",
                )

            cryptoManager.hasCachedKeyValue = true
            cryptoManager.encryptResult = Result.success(byteArrayOf(1, 2, 3))

            val savedId = repository.saveCredentials(credentials, "key", null)

            val stored = apiCredentialsDao.getById(savedId)
            assertEquals(1, stored?.pendingSync)
            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `saveConfiguration sets pendingSync to 1 and triggers sync`() =
        runTest {
            // Parent credentials row required by the configuration table's foreign key.
            apiCredentialsDao.insert(
                ApiCredentialsEntity(
                    id = ApiCredentialsId("cred-id"),
                    displayName = "Creds",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "cred-alias",
                ),
            )
            val config =
                ApiModelConfiguration(
                    id = ApiModelConfigurationId(""),
                    apiCredentialsId = ApiCredentialsId("cred-id"),
                    modelId = "gpt-4o",
                    displayName = "Config",
                )

            val savedId = repository.saveConfiguration(config)

            val stored = apiModelConfigurationsDao.getById(savedId)
            assertEquals(1, stored?.pendingSync)
            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `deleteCredentials triggers sync`() =
        runTest {
            apiCredentialsDao.insert(
                ApiCredentialsEntity(
                    id = ApiCredentialsId("id"),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "alias",
                ),
            )

            repository.deleteCredentials(ApiCredentialsId("id"))

            assertEquals(1, syncTrigger.triggerCalls)
            assertNull(apiCredentialsDao.getById(ApiCredentialsId("id")))
        }
}
