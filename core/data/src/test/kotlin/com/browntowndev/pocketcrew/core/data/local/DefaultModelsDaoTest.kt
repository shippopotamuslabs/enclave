package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
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
import android.database.sqlite.SQLiteConstraintException

@RunWith(RobolectricTestRunner::class)
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

    @Test
    fun `assign a local config as default for a ModelType`() = runTest {
        val modelId = database.localModelsDao().upsert(LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF, huggingFaceModelName = "q", remoteFilename = "q", localFilename = "q", sha256 = "q", sizeInBytes = 1, displayName = "q", modelStatus = ModelStatus.CURRENT
        ))
        val configId = database.localModelConfigurationsDao().upsert(LocalModelConfigurationEntity(
            localModelId = modelId, displayName = "c"
        ))
        
        dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = configId, apiConfigId = null))
        val retrieved = dao.getDefault(ModelType.MAIN)
        assertEquals(configId, retrieved?.localConfigId)
        assertNull(retrieved?.apiConfigId)
    }

    @Test
    fun `assign an API config as default for a ModelType`() = runTest {
        val credId = database.apiCredentialsDao().upsert(ApiCredentialsEntity(
            provider = ApiProvider.OPENAI, modelId = "gpt", credentialAlias = "key", displayName = "gpt"
        ))
        val configId = database.apiModelConfigurationsDao().upsert(ApiModelConfigurationEntity(
            apiCredentialsId = credId, displayName = "def"
        ))

        dao.upsert(DefaultModelEntity(modelType = ModelType.VISION, localConfigId = null, apiConfigId = configId))
        val retrieved = dao.getDefault(ModelType.VISION)
        assertEquals(configId, retrieved?.apiConfigId)
        assertNull(retrieved?.localConfigId)
    }

    @Test
    fun `switch default from local to API`() = runTest {
        val modelId = database.localModelsDao().upsert(LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF, huggingFaceModelName = "q", remoteFilename = "q", localFilename = "q", sha256 = "q", sizeInBytes = 1, displayName = "q", modelStatus = ModelStatus.CURRENT
        ))
        val localConfigId = database.localModelConfigurationsDao().upsert(LocalModelConfigurationEntity(
            localModelId = modelId, displayName = "c"
        ))
        val credId = database.apiCredentialsDao().upsert(ApiCredentialsEntity(
            provider = ApiProvider.OPENAI, modelId = "gpt", credentialAlias = "key", displayName = "gpt"
        ))
        val apiConfigId = database.apiModelConfigurationsDao().upsert(ApiModelConfigurationEntity(
            apiCredentialsId = credId, displayName = "def"
        ))

        dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = localConfigId, apiConfigId = null))
        dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = null, apiConfigId = apiConfigId))
        
        val retrieved = dao.getDefault(ModelType.MAIN)
        assertEquals(apiConfigId, retrieved?.apiConfigId)
        assertNull(retrieved?.localConfigId)
    }

    @Test
    fun `observe all defaults`() = runTest {
        val modelId = database.localModelsDao().upsert(LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF, huggingFaceModelName = "q", remoteFilename = "q", localFilename = "q", sha256 = "q", sizeInBytes = 1, displayName = "q", modelStatus = ModelStatus.CURRENT
        ))
        val localConfigId = database.localModelConfigurationsDao().upsert(LocalModelConfigurationEntity(
            localModelId = modelId, displayName = "c"
        ))
        val credId = database.apiCredentialsDao().upsert(ApiCredentialsEntity(
            provider = ApiProvider.OPENAI, modelId = "gpt", credentialAlias = "key", displayName = "gpt"
        ))
        val apiConfigId = database.apiModelConfigurationsDao().upsert(ApiModelConfigurationEntity(
            apiCredentialsId = credId, displayName = "def"
        ))

        dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = localConfigId, apiConfigId = null))
        dao.upsert(DefaultModelEntity(modelType = ModelType.VISION, localConfigId = null, apiConfigId = apiConfigId))
        
        val list = dao.observeAll().first()
        assertEquals(2, list.size)
    }
    
    @Test(expected = SQLiteConstraintException::class)
    fun `DefaultModelEntity FK references a non-existent local config`() = runTest {
        dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = 999, apiConfigId = null))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `DefaultModelEntity FK references a non-existent API config`() = runTest {
        dao.upsert(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = null, apiConfigId = 999))
    }
}