package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
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
class CleanupDaoTest {
    private lateinit var database: PocketCrewDatabase

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PocketCrewDatabase::class.java,
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
}
