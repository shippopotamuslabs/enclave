package com.browntowndev.enclave.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.UUID

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class CascadeBehaviorTest {
    private lateinit var database: EnclaveDatabase

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    private fun nextModelId() = LocalModelId(UUID.randomUUID().toString())

    private fun nextCredId() = ApiCredentialsId(UUID.randomUUID().toString())

    @Test
    fun `deleting a local model cascades to its configurations`() =
        runTest {
            val modelId = nextModelId()
            val model =
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.GGUF,
                    huggingFaceModelName = "q",
                    remoteFilename = "q",
                    localFilename = "q",
                    sha256 = "q",
                    sizeInBytes = 1,
                    isMultimodal = false,
                )
            database.localModelsDao().upsert(model)
            database.localModelConfigurationsDao().upsert(
                LocalModelConfigurationEntity(id = LocalModelConfigurationId("test-config-1"), localModelId = modelId, displayName = "c1"),
            )
            database.localModelConfigurationsDao().upsert(
                LocalModelConfigurationEntity(id = LocalModelConfigurationId("test-config-2"), localModelId = modelId, displayName = "c2"),
            )

            database.localModelsDao().softDeleteById(modelId)
            database.localModelConfigurationsDao().deleteAllForAsset(modelId)

            val configs = database.localModelConfigurationsDao().getAllForAsset(modelId)
            assertTrue(configs.isEmpty())
        }

    @Test
    fun `deleting API credentials cascades to its configurations`() =
        runTest {
            val credId = nextCredId()
            database.apiCredentialsDao().upsert(
                ApiCredentialsEntity(
                    id = credId,
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt",
                    credentialAlias = "key",
                    displayName = "gpt",
                ),
            )
            database.apiModelConfigurationsDao().upsert(
                ApiModelConfigurationEntity(
                    id = ApiModelConfigurationId("test-api-config-1"),
                    apiCredentialsId = credId,
                    displayName = "c1",
                ),
            )
            database.apiModelConfigurationsDao().upsert(
                ApiModelConfigurationEntity(
                    id = ApiModelConfigurationId("test-api-config-2"),
                    apiCredentialsId = credId,
                    displayName = "c2",
                ),
            )

            database.apiCredentialsDao().softDeleteById(credId)
            database.apiModelConfigurationsDao().deleteAllForCredentials(credId)

            val configs = database.apiModelConfigurationsDao().getAllForCredentials(credId)
            assertTrue(configs.isEmpty())
        }

    @Test
    fun `soft-deleting a local model that is a default is allowed`() =
        runTest {
            val modelId = nextModelId()
            val model =
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.GGUF,
                    huggingFaceModelName = "q",
                    remoteFilename = "q",
                    localFilename = "q",
                    sha256 = "q",
                    sizeInBytes = 1,
                    isMultimodal = false,
                )
            database.localModelsDao().upsert(model)
            database.localModelConfigurationsDao().upsert(
                LocalModelConfigurationEntity(id = LocalModelConfigurationId("test-config-1"), localModelId = modelId, displayName = "c1"),
            )
            database.defaultModelsDao().upsert(
                DefaultModelEntity(
                    modelType = ModelType.MAIN,
                    localConfigId = LocalModelConfigurationId("test-config-1"),
                    apiConfigId = null,
                ),
            )

            database.localModelsDao().softDeleteById(modelId)
            val retrieved = database.localModelsDao().getById(modelId)
            org.junit.Assert.assertNull(retrieved)
        }

    @Test
    fun `soft-deleting an API config that is currently a default is allowed`() =
        runTest {
            val credId = nextCredId()
            database.apiCredentialsDao().upsert(
                ApiCredentialsEntity(
                    id = credId,
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt",
                    credentialAlias = "key",
                    displayName = "gpt",
                ),
            )
            database.apiModelConfigurationsDao().upsert(
                ApiModelConfigurationEntity(
                    id = ApiModelConfigurationId("test-api-config-1"),
                    apiCredentialsId = credId,
                    displayName = "c1",
                ),
            )
            database.defaultModelsDao().upsert(
                DefaultModelEntity(
                    modelType = ModelType.MAIN,
                    localConfigId = null,
                    apiConfigId = ApiModelConfigurationId("test-api-config-1"),
                ),
            )

            database.apiCredentialsDao().softDeleteById(credId)
            val retrieved = database.apiCredentialsDao().getById(credId)
            org.junit.Assert.assertNull(retrieved)
        }
}
