package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.buildApiCredentialsIdentitySignature
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterProviderSort
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class ApiModelRepositoryImplTest {
    private lateinit var repo: ApiModelRepositoryImpl
    private val credentialsDao = mockk<ApiCredentialsDao>(relaxed = true)
    private val configurationsDao = mockk<ApiModelConfigurationsDao>(relaxed = true)
    private val apiKeyManager = mockk<ApiKeyManager>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val localEncryption = FakeLocalDataEncryptionService()
    private val healer: SensitivePersistenceSelfHealer = testSensitiveSelfHealer(localEncryption)
    private val readRepairApplier = mockk<SensitiveRoomReadRepairApplier>(relaxed = true)

    @BeforeEach
    fun setup() {
        every { cryptoManager.hasCachedKey() } returns true
        every { cryptoManager.encrypt(any<String>()) } returns Result.success(ByteArray(0))

        repo =
            ApiModelRepositoryImpl(
                syncTrigger = syncTrigger,
                apiCredentialsDao = credentialsDao,
                apiModelConfigurationsDao = configurationsDao,
                apiKeyManager = apiKeyManager,
                cryptoManager = cryptoManager,
                healer = healer,
                readRepairApplier = readRepairApplier,
            )
    }

    @Test
    fun `save credentials with API key stores key via credentialAlias and maps all fields`() =
        runTest {
            val creds =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4o",
                    baseUrl = "https://api.openai.com/v1",
                    isMultimodal = true,
                    credentialAlias = "my_key",
                )

            val generatedId = ApiCredentialsId("1")
            coEvery { credentialsDao.insert(any()) } returns 1L
            coEvery { credentialsDao.getById(any()) } returns
                ApiCredentialsEntity(
                    id = generatedId,
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4o",
                    baseUrl = "https://api.openai.com/v1",
                    isMultimodal = true,
                    credentialAlias = "my_key",
                )

            val id = repo.saveCredentials(creds, "sk-test")

            assertTrue(id.value.isNotEmpty())
            val expectedSignature =
                buildApiCredentialsIdentitySignature(
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4o",
                    baseUrl = "https://api.openai.com/v1",
                    apiKey = "sk-test",
                )
            coVerify {
                credentialsDao.insert(
                    match {
                        it.credentialAlias == "my_key" &&
                            it.displayName == "PCENC:v1:aes-gcm:Test" &&
                            it.provider == ApiProvider.OPENAI &&
                            it.modelId == "gpt-4o" &&
                            it.baseUrl == "PCENC:v1:aes-gcm:https://api.openai.com/v1" &&
                            it.isMultimodal == true &&
                            it.apiKeySignature == expectedSignature
                    },
                )
            }
            verify { apiKeyManager.save("my_key", "sk-test") }
        }

    @Test
    fun `save credentials with API key fails closed when encryption is locked`() =
        runTest {
            val lockedRepository =
                ApiModelRepositoryImpl(
                    syncTrigger = syncTrigger,
                    apiCredentialsDao = credentialsDao,
                    apiModelConfigurationsDao = configurationsDao,
                    apiKeyManager = apiKeyManager,
                    cryptoManager = cryptoManager,
                    healer = testSensitiveSelfHealer(LockedLocalDataEncryptionService()),
                    readRepairApplier = readRepairApplier,
                )
            val creds =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4o",
                    credentialAlias = "my_key",
                )

            assertThrows<IllegalStateException> {
                lockedRepository.saveCredentials(creds, "sk-test", null)
            }

            coVerify(exactly = 0) { credentialsDao.insert(any()) }
            verify(exactly = 0) { apiKeyManager.save(any(), any()) }
        }

    @Test
    fun `delete credentials removes both entity and key`() =
        runTest {
            val credId = ApiCredentialsId("1")
            coEvery { credentialsDao.getById(credId) } returns
                ApiCredentialsEntity(
                    id = credId,
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4o",
                    credentialAlias = "my_key",
                    isMultimodal = false,
                )

            repo.deleteCredentials(credId)

            coVerify {
                configurationsDao.deleteAllForCredentials(credId, any())
                credentialsDao.softDeleteById(credId, any())
            }
            verify { apiKeyManager.delete("my_key") }
        }

    @Test
    fun `credential save with blank API key skips ApiKeyManager`() =
        runTest {
            val creds =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4o",
                    credentialAlias = "my_key",
                )

            val generatedId = ApiCredentialsId("1")
            coEvery { credentialsDao.insert(any()) } returns 1L
            coEvery { credentialsDao.getById(any()) } returns
                ApiCredentialsEntity(
                    id = generatedId,
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4o",
                    credentialAlias = "my_key",
                    isMultimodal = false,
                )

            val id = repo.saveCredentials(creds, "", null)

            assertTrue(id.value.isNotEmpty())
            coVerify { credentialsDao.insert(any()) }
            verify(exactly = 0) { apiKeyManager.save(any(), any()) }
        }

    @Test
    fun `save credentials can clone an existing stored key into a new alias`() =
        runTest {
            val creds =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Cloned",
                    provider = ApiProvider.XAI,
                    modelId = "grok-4.20",
                    credentialAlias = "new_alias",
                )

            val generatedId = ApiCredentialsId("5")
            coEvery { credentialsDao.insert(any()) } returns 5L
            coEvery { credentialsDao.getById(any()) } returns
                ApiCredentialsEntity(
                    id = generatedId,
                    displayName = "Cloned",
                    provider = ApiProvider.XAI,
                    modelId = "grok-4.20",
                    credentialAlias = "new_alias",
                    isMultimodal = false,
                )
            io.mockk.every { apiKeyManager.get("existing_alias") } returns "stored-key"

            val id = repo.saveCredentials(creds, "", "existing_alias")

            assertTrue(id.value.isNotEmpty())
            verify(exactly = 1) { apiKeyManager.get("existing_alias") }
            verify(exactly = 1) { apiKeyManager.save("new_alias", "stored-key") }
        }

    @Test
    fun `find matching credentials resolves stored duplicate signature`() =
        runTest {
            val credId = ApiCredentialsId("11")
            val expectedSignature =
                buildApiCredentialsIdentitySignature(
                    provider = ApiProvider.XAI,
                    modelId = "grok-4.20",
                    baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                    apiKey = "xai-key",
                )
            coEvery { credentialsDao.getByApiKeySignature(expectedSignature) } returns
                ApiCredentialsEntity(
                    id = credId,
                    displayName = "Existing xAI",
                    provider = ApiProvider.XAI,
                    modelId = "grok-4.20",
                    baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                    credentialAlias = "existing-xai",
                    apiKeySignature = expectedSignature,
                )

            val result =
                repo.findMatchingCredentials(
                    provider = ApiProvider.XAI,
                    modelId = "grok-4.20",
                    baseUrl = null,
                    apiKey = "xai-key",
                )

            assertEquals(credId, result?.id)
            assertEquals("existing-xai", result?.credentialAlias)
            coVerify(exactly = 1) { credentialsDao.getByApiKeySignature(expectedSignature) }
        }

    @Test
    fun `find matching credentials normalizes blank baseUrl to provider default`() =
        runTest {
            val credId = ApiCredentialsId("22")
            val provider = ApiProvider.XAI
            val defaultBaseUrl = provider.defaultBaseUrl()
            val expectedSignature =
                buildApiCredentialsIdentitySignature(
                    provider = provider,
                    modelId = "grok-4.20",
                    baseUrl = defaultBaseUrl,
                    apiKey = "xai-key",
                )
            coEvery { credentialsDao.getByApiKeySignature(expectedSignature) } returns
                ApiCredentialsEntity(
                    id = credId,
                    displayName = "Existing xAI blank baseUrl",
                    provider = provider,
                    modelId = "grok-4.20",
                    baseUrl = defaultBaseUrl,
                    credentialAlias = "existing-xai-blank",
                    apiKeySignature = expectedSignature,
                )

            val result =
                repo.findMatchingCredentials(
                    provider = provider,
                    modelId = "grok-4.20",
                    baseUrl = "  ",
                    apiKey = "xai-key",
                )

            assertEquals(credId, result?.id)
            coVerify(exactly = 1) { credentialsDao.getByApiKeySignature(expectedSignature) }
        }

    @Test
    fun `save credentials inserts a new row when id is empty`() =
        runTest {
            val creds =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "xAI Existing",
                    provider = ApiProvider.XAI,
                    modelId = "grok-4-fast-reasoning",
                    baseUrl = "https://api.x.ai/v1",
                    credentialAlias = "xai-existing-2",
                )

            val generatedId = ApiCredentialsId("77")
            coEvery { credentialsDao.insert(any()) } returns 77L
            coEvery { credentialsDao.getById(any()) } returns
                ApiCredentialsEntity(
                    id = generatedId,
                    displayName = "xAI Existing",
                    provider = ApiProvider.XAI,
                    modelId = "grok-4-fast-reasoning",
                    baseUrl = "https://api.x.ai/v1",
                    credentialAlias = "xai-existing-2",
                )

            val id = repo.saveCredentials(creds, "xai-key")

            assertEquals(generatedId, id)
            coVerify(exactly = 1) { credentialsDao.insert(any()) }
            coVerify(exactly = 0) { credentialsDao.update(any()) }
            verify { apiKeyManager.save("xai-existing-2", "xai-key") }
        }

    @Test
    fun `save credentials with existing id fails fast when row is missing`() =
        runTest {
            val credId = ApiCredentialsId("5")
            val creds =
                ApiCredentials(
                    id = credId,
                    displayName = "Edited",
                    provider = ApiProvider.XAI,
                    modelId = "grok-4-fast-reasoning",
                    baseUrl = "https://api.x.ai/v1",
                    credentialAlias = "xai-edited",
                )

            coEvery { credentialsDao.getById(credId) } returns null

            assertThrows<IllegalArgumentException> {
                repo.saveCredentials(creds, "", null)
            }

            coVerify(exactly = 0) { credentialsDao.insert(any()) }
            coVerify(exactly = 0) { credentialsDao.update(any()) }
        }

    @Test
    fun `save configuration persists openrouter routing settings`() =
        runTest {
            val config =
                ApiModelConfiguration(
                    apiCredentialsId = ApiCredentialsId("9"),
                    displayName = "Router preset",
                    openRouterRouting =
                        OpenRouterRoutingConfiguration(
                            providerSort = OpenRouterProviderSort.PRICE,
                            allowFallbacks = false,
                            requireParameters = true,
                            dataCollectionPolicy = OpenRouterDataCollectionPolicy.ALLOW,
                            zeroDataRetention = true,
                        ),
                )

            coEvery { configurationsDao.upsert(any()) } returns Unit

            val id = repo.saveConfiguration(config)

            // Verify the id is a non-empty string (UUID)
            assertTrue(id.value.isNotEmpty())
            coVerify {
                configurationsDao.upsert(
                    match {
                        it.openRouterProviderSort == "price" &&
                            it.openRouterAllowFallbacks == false &&
                            it.openRouterRequireParameters == true &&
                            it.openRouterDataCollectionPolicy == "allow" &&
                            it.openRouterZeroDataRetention == true
                    },
                )
            }
        }

    @Test
    fun `saveCredentials calls triggerSync once`() =
        runTest {
            val creds =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4o",
                    credentialAlias = "key1",
                )
            coEvery { credentialsDao.insert(any()) } returns 1L
            coEvery { credentialsDao.getById(any()) } returns
                ApiCredentialsEntity(
                    id = ApiCredentialsId("1"),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4o",
                    credentialAlias = "key1",
                )

            repo.saveCredentials(creds, "sk-test")

            verify(exactly = 1) { syncTrigger.trigger() }
        }

    @Test
    fun `saveConfiguration calls triggerSync once`() =
        runTest {
            val config =
                ApiModelConfiguration(
                    apiCredentialsId = ApiCredentialsId("9"),
                    displayName = "Fast",
                )
            coEvery { configurationsDao.upsert(any()) } returns Unit

            repo.saveConfiguration(config)

            verify(exactly = 1) { syncTrigger.trigger() }
        }

    @Test
    fun `deleteCredentials calls triggerSync once`() =
        runTest {
            val credId = ApiCredentialsId("1")
            coEvery { credentialsDao.getById(credId) } returns
                ApiCredentialsEntity(
                    id = credId,
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4o",
                    credentialAlias = "my_key",
                )

            repo.deleteCredentials(credId)

            verify(exactly = 1) { syncTrigger.trigger() }
        }

    @Test
    fun `deleteConfiguration calls triggerSync once`() =
        runTest {
            val configId = ApiModelConfigurationId("cfg-1")
            coEvery { configurationsDao.softDeleteById(configId, any()) } returns Unit

            repo.deleteConfiguration(configId)

            coVerify { configurationsDao.softDeleteById(configId, any()) }
            verify(exactly = 1) { syncTrigger.trigger() }
        }

    @Test
    fun `saveConfiguration upserts entity with pendingSync true`() =
        runTest {
            val config =
                ApiModelConfiguration(
                    apiCredentialsId = ApiCredentialsId("9"),
                    displayName = "Fast",
                )
            coEvery { configurationsDao.upsert(any()) } returns Unit

            repo.saveConfiguration(config)

            coVerify {
                configurationsDao.upsert(match { it.pendingSync == 1 })
            }
        }

    private class LockedLocalDataEncryptionService : LocalDataEncryptionService {
        override fun encryptString(plaintext: String): String = throw IllegalStateException("Encryption session locked")

        override fun decryptString(stored: String): String = throw IllegalStateException("Encryption session locked")

        override fun isEncrypted(stored: String): Boolean = false

        override fun encryptString(
            plaintext: String,
            aad: ByteArray,
        ): String = throw IllegalStateException("Encryption session locked")

        override fun decryptString(
            stored: String,
            aad: ByteArray,
        ): String = throw IllegalStateException("Encryption session locked")
    }
}
