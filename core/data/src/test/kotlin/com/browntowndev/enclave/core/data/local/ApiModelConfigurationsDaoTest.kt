package com.browntowndev.enclave.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.UUID

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ApiModelConfigurationsDaoTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var credDao: ApiCredentialsDao
    private lateinit var configDao: ApiModelConfigurationsDao

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        credDao = database.apiCredentialsDao()
        configDao = database.apiModelConfigurationsDao()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    private fun nextCredId() = ApiCredentialsId(UUID.randomUUID().toString())

    @Test
    fun `SCN-DAO-002 ApiModelConfigurationsDao Round-Trip Model Fields`() =
        runTest {
            val credId = nextCredId()
            credDao.upsert(
                ApiCredentialsEntity(
                    id = credId,
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "key1",
                    displayName = "GPT-4o",
                ),
            )

            val configId = ApiModelConfigurationId("preset-1")
            configDao.upsert(
                ApiModelConfigurationEntity(
                    id = configId,
                    apiCredentialsId = credId,
                    modelId = "gpt-4o",
                    isMultimodal = true,
                    displayName = "Creative",
                ),
            )

            val retrieved = configDao.getById(configId)
            assertNotNull(retrieved)
            assertEquals("gpt-4o", retrieved?.modelId)
            assertEquals(true, retrieved?.isMultimodal)
        }

    @Test
    fun `insert and retrieve an API tuning preset`() =
        runTest {
            val credId = nextCredId()
            credDao.upsert(
                ApiCredentialsEntity(
                    id = credId,
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "key1",
                    displayName = "GPT-4o",
                ),
            )

            val configId = ApiModelConfigurationId("test-api-config-1")
            val config =
                ApiModelConfigurationEntity(
                    id = configId,
                    apiCredentialsId = credId,
                    modelId = "",
                    displayName = "Default",
                    temperature = 0.7,
                    maxTokens = 4096,
                )
            configDao.upsert(config)

            val retrieved = configDao.getById(configId)
            assertNotNull(retrieved)
            assertEquals("Default", retrieved?.displayName)
        }

    @Test
    fun `multiple presets per API credential`() =
        runTest {
            val credId = nextCredId()
            credDao.upsert(
                ApiCredentialsEntity(
                    id = credId,
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "key1",
                    displayName = "GPT-4o",
                ),
            )

            configDao.upsert(
                ApiModelConfigurationEntity(
                    id = ApiModelConfigurationId("test-api-config-1"),
                    apiCredentialsId = credId,
                    modelId = "gpt-4o",
                    displayName = "Fast",
                ),
            )
            configDao.upsert(
                ApiModelConfigurationEntity(
                    id = ApiModelConfigurationId("test-api-config-2"),
                    apiCredentialsId = credId,
                    modelId = "gpt-4o-mini",
                    displayName = "Thorough",
                ),
            )

            val allConfigs = configDao.getAllForCredentials(credId)
            assertEquals(2, allConfigs.size)
        }
}
