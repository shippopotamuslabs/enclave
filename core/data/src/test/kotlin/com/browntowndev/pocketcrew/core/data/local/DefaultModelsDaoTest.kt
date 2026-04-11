package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.database.sqlite.SQLiteConstraintException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultModelsDaoTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var dao: DefaultModelsDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.defaultModelsDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun nextModelId() = LocalModelId(UUID.randomUUID().toString())
    private fun nextCredId() = ApiCredentialsId(UUID.randomUUID().toString())

    @Test
    fun `assign a local config as default for a ModelType`() = runTest {
        val modelId = nextModelId()
        database.localModelsDao().upsert(LocalModelEntity(
            id = modelId,
            modelFileFormat = ModelFileFormat.GGUF, huggingFaceModelName = "q", remoteFilename = "q", localFilename = "q", sha256 = "q", sizeInBytes = 1, visionCapable = false, thinkingEnabled = false, isVision = false
        ))
        val configId = LocalModelConfigurationId("test-local-config-1")
        database.localModelConfigurationsDao().upsert(LocalModelConfigurationEntity(
            id = configId,
            localModelId = modelId, displayName = "c"
        ))
        
        dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = configId, apiConfigId = null))
        val retrieved = dao.getDefault(ModelType.MAIN)
        assertEquals(configId, retrieved?.localConfigId)
        assertNull(retrieved?.apiConfigId)
    }

    @Test
    fun `assign an API config as default for a ModelType`() = runTest {
        val credId = nextCredId()
        database.apiCredentialsDao().upsert(ApiCredentialsEntity(
            id = credId,
            provider = ApiProvider.OPENAI, modelId = "gpt", credentialAlias = "key", displayName = "gpt"
        ))
        val configId = ApiModelConfigurationId("test-api-config-1")
        database.apiModelConfigurationsDao().upsert(ApiModelConfigurationEntity(
            id = configId,
            apiCredentialsId = credId, displayName = "def"
        ))

        dao.upsert(DefaultModelEntity(modelType = ModelType.VISION, localConfigId = null, apiConfigId = configId))
        val retrieved = dao.getDefault(ModelType.VISION)
        assertEquals(configId, retrieved?.apiConfigId)
        assertNull(retrieved?.localConfigId)
    }

    @Test
    fun `switch default from local to API`() = runTest {
        val modelId = nextModelId()
        val localId = LocalModelConfigurationId("test-local-config-1")
        val apiId = ApiModelConfigurationId("test-api-config-1")
        database.localModelsDao().upsert(LocalModelEntity(
            id = modelId,
            modelFileFormat = ModelFileFormat.GGUF, huggingFaceModelName = "q", remoteFilename = "q", localFilename = "q", sha256 = "q", sizeInBytes = 1, visionCapable = false, thinkingEnabled = false, isVision = false
        ))
        database.localModelConfigurationsDao().upsert(LocalModelConfigurationEntity(
            id = localId,
            localModelId = modelId, displayName = "c"
        ))
        val credId = nextCredId()
        database.apiCredentialsDao().upsert(ApiCredentialsEntity(
            id = credId,
            provider = ApiProvider.OPENAI, modelId = "gpt", credentialAlias = "key", displayName = "gpt"
        ))
        database.apiModelConfigurationsDao().upsert(ApiModelConfigurationEntity(
            id = apiId,
            apiCredentialsId = credId, displayName = "def"
        ))

        dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = localId, apiConfigId = null))
        dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = null, apiConfigId = apiId))
        
        val retrieved = dao.getDefault(ModelType.MAIN)
        assertEquals(apiId, retrieved?.apiConfigId)
        assertNull(retrieved?.localConfigId)
    }

    @Test
    fun `observe all defaults`() = runTest {
        val modelId = nextModelId()
        val localId = LocalModelConfigurationId("test-local-config-1")
        val apiId = ApiModelConfigurationId("test-api-config-1")
        database.localModelsDao().upsert(LocalModelEntity(
            id = modelId,
            modelFileFormat = ModelFileFormat.GGUF, huggingFaceModelName = "q", remoteFilename = "q", localFilename = "q", sha256 = "q", sizeInBytes = 1, visionCapable = false, thinkingEnabled = false, isVision = false
        ))
        database.localModelConfigurationsDao().upsert(LocalModelConfigurationEntity(
            id = localId,
            localModelId = modelId, displayName = "c"
        ))
        val credId = nextCredId()
        database.apiCredentialsDao().upsert(ApiCredentialsEntity(
            id = credId,
            provider = ApiProvider.OPENAI, modelId = "gpt", credentialAlias = "key", displayName = "gpt"
        ))
        database.apiModelConfigurationsDao().upsert(ApiModelConfigurationEntity(
            id = apiId,
            apiCredentialsId = credId, displayName = "def"
        ))

        dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = localId, apiConfigId = null))
        dao.upsert(DefaultModelEntity(modelType = ModelType.VISION, localConfigId = null, apiConfigId = apiId))
        
        val list = dao.observeAll().first()
        assertEquals(2, list.size)
    }
    
    @Test(expected = SQLiteConstraintException::class)
    fun `DefaultModelEntity FK references a non-existent local config`() = runTest {
        dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = LocalModelConfigurationId("non-existent-id"), apiConfigId = null))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `DefaultModelEntity FK references a non-existent API config`() = runTest {
        dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = null, apiConfigId = ApiModelConfigurationId("non-existent-id")))
    }
}
