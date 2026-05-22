package com.browntowndev.enclave.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.UUID

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class CleanupDaoTest {
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

    @Test
    fun `deleteAllDefaultModels allows deleting local model configurations`() =
        runTest {
            val modelId = LocalModelId(UUID.randomUUID().toString())
            val localConfigId = LocalModelConfigurationId(UUID.randomUUID().toString())
            database.localModelsDao().upsert(
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.GGUF,
                    huggingFaceModelName = "qwen",
                    remoteFilename = "q",
                    localFilename = "q",
                    sha256 = "q",
                    sizeInBytes = 1,
                    isMultimodal = false,
                ),
            )
            database.localModelConfigurationsDao().upsert(
                LocalModelConfigurationEntity(
                    id = localConfigId,
                    localModelId = modelId,
                    displayName = "Local preset",
                ),
            )
            database.defaultModelsDao().upsert(
                DefaultModelEntity(
                    modelType = ModelType.MAIN,
                    localConfigId = localConfigId,
                    apiConfigId = null,
                ),
            )

            database.cleanupDao().deleteAllDefaultModels()
            database.cleanupDao().deleteLocalModelConfigurations()

            assertTrue(database.defaultModelsDao().getAll().isEmpty())
            assertTrue(database.localModelConfigurationsDao().getAllForAsset(modelId).isEmpty())
        }

    @Test
    @DisplayName("SCN-CLEANUP-001")
    fun scnCleanup001_clearUserDataDeletesAllUtilityModelsRows() =
        runTest {
            val dao = database.utilityModelsDao()
            dao.upsert(
                UtilityModelEntity(
                    id = UtilityModelId("util-1"),
                    utilityType = UtilityType.WHISPER,
                    modelFileFormat = ModelFileFormat.BIN,
                    huggingFaceModelName = "whisper",
                    remoteFilename = "ggml-base.en.bin",
                    localFilename = "ggml-base.en.bin",
                    sha256 = "whisper-sha",
                    sizeInBytes = 4L,
                ),
            )
            dao.upsert(
                UtilityModelEntity(
                    id = UtilityModelId("util-2"),
                    utilityType = UtilityType.ONNX_VOCAB,
                    modelFileFormat = ModelFileFormat.TXT,
                    huggingFaceModelName = "vocab",
                    remoteFilename = "vocab.txt",
                    localFilename = "vocab.txt",
                    sha256 = "vocab-sha",
                    sizeInBytes = 100L,
                ),
            )

            runCatching { database.cleanupDao().clearUserData() }

            assertTrue(dao.getAll().isEmpty())
        }
}
