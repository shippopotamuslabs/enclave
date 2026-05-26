package com.browntowndev.enclave.core.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.UUID

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class DefaultModelsDaoTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var dao: DefaultModelsDao

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = database.defaultModelsDao()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    private fun nextModelId() = LocalModelId(UUID.randomUUID().toString())

    private fun nextCredId() = ApiCredentialsId(UUID.randomUUID().toString())

    @Test
    fun `SCN-DAO-002 observeAllWithDetails returns asset display name`() =
        runTest {
            val encryption = FakeLocalDataEncryptionService()
            val modelId = nextModelId()
            val configId = LocalModelConfigurationId("cfg-fast")
            database.localModelsDao().upsert(
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.LITERTLM,
                    displayName = "Gemma 4 E4B",
                    huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm",
                    remoteFilename = "gemma-4-E4B-it.litertlm",
                    localFilename = "gemma-4-E4B-it.litertlm",
                    sha256 = "gemma-sha",
                    sizeInBytes = 100L,
                    isMultimodal = true,
                ),
            )
            database.localModelConfigurationsDao().upsert(
                LocalModelConfigurationEntity(
                    id = configId,
                    localModelId = modelId,
                    displayName = encryption.encryptString("Fast"),
                ),
            )
            dao.upsert(
                DefaultModelEntity(
                    modelType = ModelType.FAST,
                    localConfigId = configId,
                    apiConfigId = null,
                ),
            )

            val view = dao.observeAllWithDetails().first().single { it.modelType == ModelType.FAST }

            assertEquals("Gemma 4 E4B", view.localAssetName)
            assertEquals("Fast", encryption.decryptString(view.localPresetName!!))
        }

    @Test
    fun `observeAllWithDetails falls back to hugging face name when display name is blank`() =
        runTest {
            val encryption = FakeLocalDataEncryptionService()
            val modelId = nextModelId()
            val configId = LocalModelConfigurationId("cfg-draft")
            val huggingFaceName = "openbmb/MiniCPM-V-4.6-Thinking-gguf"
            database.localModelsDao().upsert(
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.GGUF,
                    displayName = "",
                    huggingFaceModelName = huggingFaceName,
                    remoteFilename = "minicpm.gguf",
                    localFilename = "minicpm.gguf",
                    sha256 = "minicpm-sha",
                    sizeInBytes = 100L,
                ),
            )
            database.localModelConfigurationsDao().upsert(
                LocalModelConfigurationEntity(
                    id = configId,
                    localModelId = modelId,
                    displayName = encryption.encryptString("Analytic Draft"),
                ),
            )
            dao.upsert(
                DefaultModelEntity(
                    modelType = ModelType.DRAFT_ONE,
                    localConfigId = configId,
                    apiConfigId = null,
                ),
            )

            val view = dao.observeAllWithDetails().first().single { it.modelType == ModelType.DRAFT_ONE }

            assertEquals(huggingFaceName, view.localAssetName)
        }

    @Test
    fun `assign a local config as default for a ModelType`() =
        runTest {
            val modelId = nextModelId()
            database.localModelsDao().upsert(
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.GGUF,
                    huggingFaceModelName = "q",
                    remoteFilename = "q",
                    localFilename = "q",
                    sha256 = "q",
                    sizeInBytes = 1,
                    isMultimodal = false,
                ),
            )
            val configId = LocalModelConfigurationId("test-local-config-1")
            database.localModelConfigurationsDao().upsert(
                LocalModelConfigurationEntity(
                    id = configId,
                    localModelId = modelId,
                    displayName = "c",
                ),
            )

            dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = configId, apiConfigId = null))
            val retrieved = dao.getDefault(ModelType.MAIN)
            assertEquals(configId, retrieved?.localConfigId)
            assertNull(retrieved?.apiConfigId)
        }

    @Test
    fun `assign an API config as default for a ModelType`() =
        runTest {
            val credId = nextCredId()
            database.apiCredentialsDao().upsert(
                ApiCredentialsEntity(
                    id = credId,
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "key",
                    displayName = "gpt",
                ),
            )
            val configId = ApiModelConfigurationId("test-api-config-1")
            database.apiModelConfigurationsDao().upsert(
                ApiModelConfigurationEntity(
                    id = configId,
                    apiCredentialsId = credId,
                    modelId = "",
                    displayName = "def",
                ),
            )

            dao.upsert(DefaultModelEntity(modelType = ModelType.VISION, localConfigId = null, apiConfigId = configId))
            val retrieved = dao.getDefault(ModelType.VISION)
            assertEquals(configId, retrieved?.apiConfigId)
            assertNull(retrieved?.localConfigId)
        }

    @Test
    fun `switch default from local to API`() =
        runTest {
            val modelId = nextModelId()
            val localId = LocalModelConfigurationId("test-local-config-1")
            val apiId = ApiModelConfigurationId("test-api-config-1")
            database.localModelsDao().upsert(
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.GGUF,
                    huggingFaceModelName = "q",
                    remoteFilename = "q",
                    localFilename = "q",
                    sha256 = "q",
                    sizeInBytes = 1,
                    isMultimodal = false,
                ),
            )
            database.localModelConfigurationsDao().upsert(
                LocalModelConfigurationEntity(
                    id = localId,
                    localModelId = modelId,
                    displayName = "c",
                ),
            )
            val credId = nextCredId()
            database.apiCredentialsDao().upsert(
                ApiCredentialsEntity(
                    id = credId,
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "key",
                    displayName = "gpt",
                ),
            )
            database.apiModelConfigurationsDao().upsert(
                ApiModelConfigurationEntity(
                    id = apiId,
                    apiCredentialsId = credId,
                    modelId = "",
                    displayName = "def",
                ),
            )

            dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = localId, apiConfigId = null))
            dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = null, apiConfigId = apiId))

            val retrieved = dao.getDefault(ModelType.MAIN)
            assertEquals(apiId, retrieved?.apiConfigId)
            assertNull(retrieved?.localConfigId)
        }

    @Test
    fun `observe all defaults`() =
        runTest {
            val modelId = nextModelId()
            val localId = LocalModelConfigurationId("test-local-config-1")
            val apiId = ApiModelConfigurationId("test-api-config-1")
            database.localModelsDao().upsert(
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.GGUF,
                    huggingFaceModelName = "q",
                    remoteFilename = "q",
                    localFilename = "q",
                    sha256 = "q",
                    sizeInBytes = 1,
                    isMultimodal = false,
                ),
            )
            database.localModelConfigurationsDao().upsert(
                LocalModelConfigurationEntity(
                    id = localId,
                    localModelId = modelId,
                    displayName = "c",
                ),
            )
            val credId = nextCredId()
            database.apiCredentialsDao().upsert(
                ApiCredentialsEntity(
                    id = credId,
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "key",
                    displayName = "gpt",
                ),
            )
            database.apiModelConfigurationsDao().upsert(
                ApiModelConfigurationEntity(
                    id = apiId,
                    apiCredentialsId = credId,
                    modelId = "",
                    displayName = "def",
                ),
            )

            dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = localId, apiConfigId = null))
            dao.upsert(DefaultModelEntity(modelType = ModelType.VISION, localConfigId = null, apiConfigId = apiId))

            val list = dao.observeAll().first()
            assertEquals(2, list.size)
        }

    @Test
    fun `DefaultModelEntity FK references a non-existent local config`() =
        runTest {
            assertThrows<SQLiteConstraintException> {
                dao.upsert(
                    DefaultModelEntity(
                        modelType = ModelType.MAIN,
                        localConfigId = LocalModelConfigurationId("non-existent-id"),
                        apiConfigId = null,
                    ),
                )
            }
        }

    @Test
    fun `DefaultModelEntity FK references a non-existent API config`() =
        runTest {
            assertThrows<SQLiteConstraintException> {
                dao.upsert(
                    DefaultModelEntity(
                        modelType = ModelType.MAIN,
                        localConfigId = null,
                        apiConfigId = ApiModelConfigurationId("non-existent-id"),
                    ),
                )
            }
        }
}
