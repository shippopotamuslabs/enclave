package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
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
import java.util.UUID

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

    private fun nextModelId() = LocalModelId(UUID.randomUUID().toString())
    private fun nextCredId() = ApiCredentialsId(UUID.randomUUID().toString())

    @Test
    fun `deleting a local model cascades to its configurations`() = runTest {
        val modelId = nextModelId()
        val model = LocalModelEntity(
            id = modelId,
            modelFileFormat = ModelFileFormat.GGUF, huggingFaceModelName = "q", remoteFilename = "q",
            localFilename = "q", sha256 = "q", sizeInBytes = 1, visionCapable = false,
            thinkingEnabled = false, isVision = false
        )
        database.localModelsDao().upsert(model)
        database.localModelConfigurationsDao().upsert(LocalModelConfigurationEntity(id = LocalModelConfigurationId("test-config-1"), localModelId = modelId, displayName = "c1"))
        database.localModelConfigurationsDao().upsert(LocalModelConfigurationEntity(id = LocalModelConfigurationId("test-config-2"), localModelId = modelId, displayName = "c2"))
        
        database.localModelsDao().deleteById(modelId)
        
        val configs = database.localModelConfigurationsDao().getAllForAsset(modelId)
        assertTrue(configs.isEmpty())
    }

    @Test
    fun `deleting API credentials cascades to its configurations`() = runTest {
        val credId = nextCredId()
        database.apiCredentialsDao().upsert(ApiCredentialsEntity(
            id = credId,
            provider = ApiProvider.OPENAI, modelId = "gpt", credentialAlias = "key", displayName = "gpt"
        ))
        database.apiModelConfigurationsDao().upsert(ApiModelConfigurationEntity(id = ApiModelConfigurationId("test-api-config-1"), apiCredentialsId = credId, displayName = "c1"))
        database.apiModelConfigurationsDao().upsert(ApiModelConfigurationEntity(id = ApiModelConfigurationId("test-api-config-2"), apiCredentialsId = credId, displayName = "c2"))
        
        database.apiCredentialsDao().deleteById(credId)
        
        val configs = database.apiModelConfigurationsDao().getAllForCredentials(credId)
        assertTrue(configs.isEmpty())
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `deleting a local config that is currently a default is blocked`() = runTest {
        val modelId = nextModelId()
        val model = LocalModelEntity(
            id = modelId,
            modelFileFormat = ModelFileFormat.GGUF, huggingFaceModelName = "q", remoteFilename = "q",
            localFilename = "q", sha256 = "q", sizeInBytes = 1, visionCapable = false,
            thinkingEnabled = false, isVision = false
        )
        database.localModelsDao().upsert(model)
        database.localModelConfigurationsDao().upsert(LocalModelConfigurationEntity(id = LocalModelConfigurationId("test-config-1"), localModelId = modelId, displayName = "c1"))
        database.defaultModelsDao().upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = LocalModelConfigurationId("test-config-1"), apiConfigId = null))
        
        database.localModelsDao().deleteById(modelId)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `deleting an API config that is currently a default is blocked`() = runTest {
        val credId = nextCredId()
        database.apiCredentialsDao().upsert(ApiCredentialsEntity(
            id = credId,
            provider = ApiProvider.OPENAI, modelId = "gpt", credentialAlias = "key", displayName = "gpt"
        ))
        database.apiModelConfigurationsDao().upsert(ApiModelConfigurationEntity(id = ApiModelConfigurationId("test-api-config-1"), apiCredentialsId = credId, displayName = "c1"))
        database.defaultModelsDao().upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = null, apiConfigId = ApiModelConfigurationId("test-api-config-1")))
        
        database.apiCredentialsDao().deleteById(credId)
    }
}
