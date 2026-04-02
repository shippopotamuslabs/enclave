package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.database.sqlite.SQLiteConstraintException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CascadeBehaviorTest {
    private lateinit var database: PocketCrewDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `deleting a local model cascades to its configurations`() = runTest {
        val modelId = database.localModelsDao().upsert(LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF, huggingFaceModelName = "q", remoteFilename = "q", localFilename = "q", sha256 = "q", sizeInBytes = 1, modelStatus = ModelStatus.CURRENT
        ))
        database.localModelConfigurationsDao().upsert(LocalModelConfigurationEntity(localModelId = modelId, displayName = "c1"))
        database.localModelConfigurationsDao().upsert(LocalModelConfigurationEntity(localModelId = modelId, displayName = "c2"))
        
        database.localModelsDao().deleteById(modelId)
        
        val configs = database.localModelConfigurationsDao().getAllForAsset(modelId)
        assertTrue(configs.isEmpty())
    }

    @Test
    fun `deleting API credentials cascades to its configurations`() = runTest {
        val credId = database.apiCredentialsDao().upsert(ApiCredentialsEntity(
            provider = ApiProvider.OPENAI, modelId = "gpt", credentialAlias = "key", displayName = "gpt"
        ))
        database.apiModelConfigurationsDao().upsert(ApiModelConfigurationEntity(apiCredentialsId = credId, displayName = "c1"))
        database.apiModelConfigurationsDao().upsert(ApiModelConfigurationEntity(apiCredentialsId = credId, displayName = "c2"))
        
        database.apiCredentialsDao().deleteById(credId)
        
        val configs = database.apiModelConfigurationsDao().getAllForCredentials(credId)
        assertTrue(configs.isEmpty())
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `deleting a local config that is currently a default is blocked`() = runTest {
        val modelId = database.localModelsDao().upsert(LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF, huggingFaceModelName = "q", remoteFilename = "q", localFilename = "q", sha256 = "q", sizeInBytes = 1, modelStatus = ModelStatus.CURRENT
        ))
        val configId = database.localModelConfigurationsDao().upsert(LocalModelConfigurationEntity(localModelId = modelId, displayName = "c1"))
        database.defaultModelsDao().upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = configId, apiConfigId = null))
        
        database.localModelsDao().deleteById(modelId)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `deleting an API config that is currently a default is blocked`() = runTest {
        val credId = database.apiCredentialsDao().upsert(ApiCredentialsEntity(
            provider = ApiProvider.OPENAI, modelId = "gpt", credentialAlias = "key", displayName = "gpt"
        ))
        val configId = database.apiModelConfigurationsDao().upsert(ApiModelConfigurationEntity(apiCredentialsId = credId, displayName = "c1"))
        database.defaultModelsDao().upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = null, apiConfigId = configId))
        
        database.apiCredentialsDao().deleteById(credId)
    }
}
