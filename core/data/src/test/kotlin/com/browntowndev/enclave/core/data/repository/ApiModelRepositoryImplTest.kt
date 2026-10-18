package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiCredentialsEntity
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.buildApiCredentialsIdentitySignature
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.enclave.domain.model.config.OpenRouterProviderSort
import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(MainDispatcherRule::class, RobolectricExtension::class)
@Config(sdk = [34])
class ApiModelRepositoryImplTest {
    private lateinit var repo: ApiModelRepositoryImpl
    private lateinit var database: EnclaveDatabase
    private lateinit var credentialsDao: ApiCredentialsDao
    private lateinit var configurationsDao: ApiModelConfigurationsDao
    private lateinit var apiKeyManager: ApiKeyManager
    private val cryptoManager = FakeCryptoManagerPort()
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private val localEncryption = FakeLocalDataEncryptionService()
    private val healer: SensitivePersistenceSelfHealer = testSensitiveSelfHealer(localEncryption)

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        credentialsDao = database.apiCredentialsDao()
        configurationsDao = database.apiModelConfigurationsDao()
        apiKeyManager =
            testApiKeyManager(
                context,
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
                "api_model_repository_impl_test_prefs",
            )
        syncTrigger = RecordingCloudSyncTrigger()
        cryptoManager.hasCachedKeyValue = true
        cryptoManager.encryptResult = Result.success(ByteArray(0))

        repo =
            ApiModelRepositoryImpl(
                syncTrigger = syncTrigger,
                apiCredentialsDao = credentialsDao,
                apiModelConfigurationsDao = configurationsDao,
                apiKeyManager = apiKeyManager,
                cryptoManager = cryptoManager,
                healer = healer,
                readRepairApplier = database.realReadRepairApplier(),
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    /** Seeds an active credentials row so configuration foreign keys resolve. */
    private suspend fun seedCredentials(
        id: String,
        alias: String = "cred-$id",
    ) {
        credentialsDao.insert(
            ApiCredentialsEntity(
                id = ApiCredentialsId(id),
                displayName = "Seed",
                provider = ApiProvider.OPENAI,
                credentialAlias = alias,
            ),
        )
    }

    @Test
    fun `SCN-REPO-003 Two Presets One Credential Same Key`() =
        runTest {
            val baseUrl = "https://api.deepseek.com"
            val sharedSignature =
                buildApiCredentialsIdentitySignature(
                    provider = ApiProvider.OPENAI,
                    baseUrl = baseUrl,
                    apiKey = "shared",
                )

            val credId =
                repo.saveCredentials(
                    ApiCredentials(
                        id = ApiCredentialsId(""),
                        displayName = "DeepSeek Shared",
                        provider = ApiProvider.OPENAI,
                        baseUrl = baseUrl,
                        credentialAlias = "deepseek-main",
                    ),
                    apiKey = "shared",
                )

            repo.saveConfiguration(
                ApiModelConfiguration(
                    apiCredentialsId = credId,
                    modelId = "deepseek-chat",
                    displayName = "Chat",
                ),
            )

            val matched =
                repo.findMatchingCredentials(
                    provider = ApiProvider.OPENAI,
                    baseUrl = baseUrl,
                    apiKey = "shared",
                )
            assertEquals(credId, matched?.id)

            repo.saveConfiguration(
                ApiModelConfiguration(
                    apiCredentialsId = requireNotNull(matched).id,
                    modelId = "kimi-k2",
                    displayName = "Kimi",
                ),
            )

            val storedCredentials = credentialsDao.getAll()
            assertEquals(1, storedCredentials.size)
            assertEquals(sharedSignature, storedCredentials.single().apiKeySignature)
            val storedConfigs = configurationsDao.getAllForCredentials(credId)
            assertEquals(2, storedConfigs.size)
            assertTrue(storedConfigs.all { it.apiCredentialsId == credId })
            assertEquals(setOf("deepseek-chat", "kimi-k2"), storedConfigs.map { it.modelId }.toSet())
        }

    @Test
    fun `SCN-REPO-001 saveCredentials Without Model Fields`() =
        runTest {
            val creds =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    baseUrl = "https://api.openai.com/v1",
                    credentialAlias = "my_key",
                )

            val id = repo.saveCredentials(creds, "sk-test")

            assertTrue(id.value.isNotEmpty())
            val expectedSignature =
                buildApiCredentialsIdentitySignature(
                    provider = ApiProvider.OPENAI,
                    baseUrl = "https://api.openai.com/v1",
                    apiKey = "sk-test",
                )
            val stored = credentialsDao.getById(id)!!
            assertEquals("my_key", stored.credentialAlias)
            assertEquals("PCENC:v1:aes-gcm:Test", stored.displayName)
            assertEquals(ApiProvider.OPENAI, stored.provider)
            assertEquals("PCENC:v1:aes-gcm:https://api.openai.com/v1", stored.baseUrl)
            assertEquals(expectedSignature, stored.apiKeySignature)
            assertEquals("sk-test", apiKeyManager.get("my_key"))
        }

    @Test
    fun `saveCredentials generates credential alias when draft alias is blank`() =
        runTest {
            val id =
                repo.saveCredentials(
                    ApiCredentials(
                        id = ApiCredentialsId(""),
                        displayName = "Test",
                        provider = ApiProvider.OPENAI,
                        baseUrl = "https://api.openai.com/v1",
                        credentialAlias = "",
                    ),
                    apiKey = "sk-test",
                )

            val expectedAlias = "api-${id.value}"
            assertEquals(expectedAlias, credentialsDao.getById(id)?.credentialAlias)
            assertEquals("sk-test", apiKeyManager.get(expectedAlias))
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
                    readRepairApplier = database.realReadRepairApplier(),
                )
            val creds =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "my_key",
                )

            assertThrows<IllegalStateException> {
                lockedRepository.saveCredentials(creds, "sk-test", null)
            }

            assertTrue(credentialsDao.getAll().isEmpty())
            assertNull(apiKeyManager.get("my_key"))
        }

    @Test
    fun `delete credentials removes both entity and key`() =
        runTest {
            val credId = ApiCredentialsId("1")
            credentialsDao.insert(
                ApiCredentialsEntity(
                    id = credId,
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "my_key",
                ),
            )
            repo.saveConfiguration(
                ApiModelConfiguration(
                    apiCredentialsId = credId,
                    modelId = "gpt-4o",
                    displayName = "Cfg",
                ),
            )
            apiKeyManager.save("my_key", "sk-test")

            repo.deleteCredentials(credId)

            assertNull(credentialsDao.getById(credId))
            assertTrue(configurationsDao.getAllForCredentials(credId).isEmpty())
            assertNull(apiKeyManager.get("my_key"))
        }

    @Test
    fun `credential save with blank API key skips ApiKeyManager`() =
        runTest {
            val creds =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "my_key",
                )

            val id = repo.saveCredentials(creds, "", null)

            assertTrue(id.value.isNotEmpty())
            assertEquals(1, credentialsDao.getAll().size)
            assertNull(apiKeyManager.get("my_key"))
        }

    @Test
    fun `save credentials can clone an existing stored key into a new alias`() =
        runTest {
            val creds =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Cloned",
                    provider = ApiProvider.XAI,
                    credentialAlias = "new_alias",
                )
            apiKeyManager.save("existing_alias", "stored-key")

            val id = repo.saveCredentials(creds, "", "existing_alias")

            assertTrue(id.value.isNotEmpty())
            assertEquals("stored-key", apiKeyManager.get("new_alias"))
        }

    @Test
    fun `find matching credentials resolves stored duplicate signature`() =
        runTest {
            val credId = ApiCredentialsId("11")
            val expectedSignature =
                buildApiCredentialsIdentitySignature(
                    provider = ApiProvider.XAI,
                    baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                    apiKey = "xai-key",
                )
            credentialsDao.insert(
                ApiCredentialsEntity(
                    id = credId,
                    displayName = "Existing xAI",
                    provider = ApiProvider.XAI,
                    baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                    credentialAlias = "existing-xai",
                    apiKeySignature = expectedSignature,
                ),
            )

            val result =
                repo.findMatchingCredentials(
                    provider = ApiProvider.XAI,
                    baseUrl = null,
                    apiKey = "xai-key",
                )

            assertEquals(credId, result?.id)
            assertEquals("existing-xai", result?.credentialAlias)
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
                    baseUrl = defaultBaseUrl,
                    apiKey = "xai-key",
                )
            credentialsDao.insert(
                ApiCredentialsEntity(
                    id = credId,
                    displayName = "Existing xAI blank baseUrl",
                    provider = provider,
                    baseUrl = defaultBaseUrl,
                    credentialAlias = "existing-xai-blank",
                    apiKeySignature = expectedSignature,
                ),
            )

            val result =
                repo.findMatchingCredentials(
                    provider = provider,
                    baseUrl = "  ",
                    apiKey = "xai-key",
                )

            assertEquals(credId, result?.id)
        }

    @Test
    fun `save credentials inserts a new row when id is empty`() =
        runTest {
            val creds =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "xAI Existing",
                    provider = ApiProvider.XAI,
                    baseUrl = "https://api.x.ai/v1",
                    credentialAlias = "xai-existing-2",
                )

            val id = repo.saveCredentials(creds, "xai-key")

            val stored = credentialsDao.getAll()
            assertEquals(1, stored.size)
            assertEquals(id, stored.single().id)
            assertEquals("xai-key", apiKeyManager.get("xai-existing-2"))
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
                    baseUrl = "https://api.x.ai/v1",
                    credentialAlias = "xai-edited",
                )

            assertThrows<IllegalArgumentException> {
                repo.saveCredentials(creds, "", null)
            }

            assertTrue(credentialsDao.getAll().isEmpty())
        }

    @Test
    fun `save configuration persists openrouter routing settings`() =
        runTest {
            seedCredentials("9")
            val config =
                ApiModelConfiguration(
                    apiCredentialsId = ApiCredentialsId("9"),
                    modelId = "openai/gpt-5.2",
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

            val id = repo.saveConfiguration(config)

            // Verify the id is a non-empty string (UUID)
            assertTrue(id.value.isNotEmpty())
            val stored = configurationsDao.getById(id)!!
            assertEquals("price", stored.openRouterProviderSort)
            assertEquals(false, stored.openRouterAllowFallbacks)
            assertEquals(true, stored.openRouterRequireParameters)
            assertEquals("allow", stored.openRouterDataCollectionPolicy)
            assertEquals(true, stored.openRouterZeroDataRetention)
        }

    @Test
    fun `saveCredentials calls triggerSync once`() =
        runTest {
            val creds =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "key1",
                )

            repo.saveCredentials(creds, "sk-test")

            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `saveConfiguration calls triggerSync once`() =
        runTest {
            seedCredentials("9")
            val config =
                ApiModelConfiguration(
                    apiCredentialsId = ApiCredentialsId("9"),
                    modelId = "gpt-4o",
                    displayName = "Fast",
                )

            repo.saveConfiguration(config)

            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `deleteCredentials calls triggerSync once`() =
        runTest {
            val credId = ApiCredentialsId("1")
            credentialsDao.insert(
                ApiCredentialsEntity(
                    id = credId,
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "my_key",
                ),
            )

            repo.deleteCredentials(credId)

            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `saveCredentials releases deleted identity locks before insert`() =
        runTest {
            // A soft-deleted row still holds the unique credential_alias / api_key_signature
            // indexes; saving a fresh row with the same alias must release that lock first.
            val deletedId = ApiCredentialsId("old-1")
            val signature =
                buildApiCredentialsIdentitySignature(
                    provider = ApiProvider.OPENAI,
                    baseUrl = ApiProvider.OPENAI.defaultBaseUrl(),
                    apiKey = "sk-openai",
                )
            credentialsDao.insert(
                ApiCredentialsEntity(
                    id = deletedId,
                    displayName = "Old",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "openai",
                    apiKeySignature = signature,
                ),
            )
            credentialsDao.softDeleteById(deletedId)

            val creds =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "My OpenAI",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "openai",
                )

            val id = repo.saveCredentials(creds, "sk-openai")

            assertEquals("openai", credentialsDao.getById(id)?.credentialAlias)
            val allAliases = credentialsDao.getAllCredentialAliases()
            assertTrue(allAliases.contains("deleted-old-1-openai"))
            assertTrue(allAliases.contains("openai"))
        }

    @Test
    fun `deleteConfiguration calls triggerSync once`() =
        runTest {
            seedCredentials("9")
            val configId =
                repo.saveConfiguration(
                    ApiModelConfiguration(
                        apiCredentialsId = ApiCredentialsId("9"),
                        modelId = "gpt-4o",
                        displayName = "Fast",
                    ),
                )

            val triggersBefore = syncTrigger.triggerCalls
            repo.deleteConfiguration(configId)

            assertNull(configurationsDao.getById(configId))
            assertEquals(triggersBefore + 1, syncTrigger.triggerCalls)
        }

    @Test
    fun `SCN-REPO-002 saveConfiguration With Model Fields`() =
        runTest {
            seedCredentials("9")
            val config =
                ApiModelConfiguration(
                    apiCredentialsId = ApiCredentialsId("9"),
                    modelId = "gpt-4o",
                    isMultimodal = true,
                    displayName = "Fast",
                    maxTokens = 4096,
                    contextWindow = 128000,
                )

            val id = repo.saveConfiguration(config)

            val stored = configurationsDao.getById(id)!!
            assertEquals("gpt-4o", stored.modelId)
            assertTrue(stored.isMultimodal)
            assertEquals(1, stored.pendingSync)
        }

    @Test
    fun `SCN-REPO-004 apiKeySignature Excludes ModelId`() =
        runTest {
            val creds =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    baseUrl = "https://api.openai.com/v1",
                    credentialAlias = "my_key",
                )

            val id = repo.saveCredentials(creds, "sk-test")

            val expectedSignature =
                buildApiCredentialsIdentitySignature(
                    provider = ApiProvider.OPENAI,
                    baseUrl = "https://api.openai.com/v1",
                    apiKey = "sk-test",
                )
            assertEquals(expectedSignature, credentialsDao.getById(id)?.apiKeySignature)
        }

    @Test
    @org.junit.jupiter.api.DisplayName("SCN-PERSIST-001")
    fun scnPersist001_apiModelRepositoryMapsPreferredProviderToAndFromDatabase() =
        runTest {
            seedCredentials("9")
            val config =
                ApiModelConfiguration(
                    apiCredentialsId = ApiCredentialsId("9"),
                    modelId = "openai/gpt-5.2",
                    displayName = "Router preset",
                    openRouterRouting =
                        OpenRouterRoutingConfiguration(
                            preferredProvider = "together",
                        ),
                )

            val configId = repo.saveConfiguration(config)

            val storedConfigs = configurationsDao.getAllForCredentials(ApiCredentialsId("9"))
            assertEquals(1, storedConfigs.size)
            assertEquals("together", storedConfigs.first().openRouterPreferredProvider)

            val retrieved = repo.getConfigurationById(configId)
            assertEquals("together", retrieved?.openRouterRouting?.preferredProvider)
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
