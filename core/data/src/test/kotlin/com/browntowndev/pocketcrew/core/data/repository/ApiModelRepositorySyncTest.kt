package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApiModelRepositorySyncTest {
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val apiCredentialsDao = mockk<ApiCredentialsDao>(relaxed = true)
    private val apiModelConfigurationsDao = mockk<ApiModelConfigurationsDao>(relaxed = true)
    private val apiKeyManager = mockk<ApiKeyManager>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)

    private lateinit var repository: ApiModelRepositoryImpl

    @BeforeEach
    fun setup() {
        repository =
            ApiModelRepositoryImpl(
                syncTrigger = syncTrigger,
                apiCredentialsDao = apiCredentialsDao,
                apiModelConfigurationsDao = apiModelConfigurationsDao,
                apiKeyManager = apiKeyManager,
                cryptoManager = cryptoManager,
                localEncryption = FakeLocalDataEncryptionService(),
            )
    }

    @Test
    fun `saveCredentials sets pendingSync to 1 and triggers sync`() =
        runTest {
            val credentials =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4",
                    credentialAlias = "alias",
                )

            every { cryptoManager.hasCachedKey() } returns true
            every { cryptoManager.encrypt("key") } returns Result.success(byteArrayOf(1, 2, 3))
            coEvery { apiCredentialsDao.getById(any()) } returns
                ApiCredentialsEntity(
                    id = ApiCredentialsId("saved"),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4",
                    credentialAlias = "alias",
                )

            repository.saveCredentials(credentials, "key", null)

            coVerify {
                apiCredentialsDao.insert(match { it.pendingSync == 1 })
                syncTrigger.trigger()
            }
        }

    @Test
    fun `saveConfiguration sets pendingSync to 1 and triggers sync`() =
        runTest {
            val config =
                ApiModelConfiguration(
                    id = ApiModelConfigurationId(""),
                    apiCredentialsId = ApiCredentialsId("cred-id"),
                    displayName = "Config",
                )

            repository.saveConfiguration(config)

            coVerify {
                apiModelConfigurationsDao.upsert(match { it.pendingSync == 1 })
                syncTrigger.trigger()
            }
        }

    @Test
    fun `deleteCredentials triggers sync`() =
        runTest {
            coEvery { apiCredentialsDao.getById(any()) } returns mockk(relaxed = true)

            repository.deleteCredentials(ApiCredentialsId("id"))

            coVerify {
                syncTrigger.trigger()
            }
        }
}
