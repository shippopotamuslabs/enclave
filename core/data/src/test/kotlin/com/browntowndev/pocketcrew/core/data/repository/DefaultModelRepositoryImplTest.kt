package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.DefaultModelEntity
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsEntity
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefaultModelRepositoryImplTest {

    private lateinit var repo: DefaultModelRepositoryImpl
    private val defaultModelsDao = mockk<DefaultModelsDao>()
    private val localConfigsDao = mockk<LocalModelConfigurationsDao>()
    private val localModelsDao = mockk<LocalModelsDao>()
    private val apiConfigsDao = mockk<ApiModelConfigurationsDao>()
    private val apiCredentialsDao = mockk<ApiCredentialsDao>()

    @BeforeEach
    fun setup() {
        repo = DefaultModelRepositoryImpl(
            defaultModelsDao = defaultModelsDao,
            localModelConfigurationsDao = localConfigsDao,
            localModelsDao = localModelsDao,
            apiModelConfigurationsDao = apiConfigsDao,
            apiCredentialsDao = apiCredentialsDao
        )
    }

    @Test
    fun `get default with resolved display data for local model`() = runTest {
        val configId = LocalModelConfigurationId("test-local-config-1")
        val modelId = LocalModelId("10")
        coEvery { defaultModelsDao.getDefault(ModelType.MAIN) } returns DefaultModelEntity(
            modelType = ModelType.MAIN,
            localConfigId = configId,
            apiConfigId = null
        )
        coEvery { localConfigsDao.getById(configId) } returns LocalModelConfigurationEntity(
            id = configId,
            localModelId = modelId,
            displayName = "Precise"
        )
        coEvery { localModelsDao.getById(modelId) } returns LocalModelEntity(
            id = modelId,
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "abc",
            sizeInBytes = 100
        )

        val result = repo.getDefault(ModelType.MAIN)

        assertEquals(configId, result?.localConfigId)
        assertNull(result?.apiConfigId)
        assertEquals("Precise", result?.displayName)
        assertEquals("qwen", result?.providerName) // Using huggingFaceModelName as provider for local models
    }

    @Test
    fun `get default with resolved display data for API model`() = runTest {
        val configId = ApiModelConfigurationId("test-api-config-1")
        val credId = ApiCredentialsId("12")
        coEvery { defaultModelsDao.getDefault(ModelType.VISION) } returns DefaultModelEntity(
            modelType = ModelType.VISION,
            localConfigId = null,
            apiConfigId = configId
        )
        coEvery { apiConfigsDao.getById(configId) } returns ApiModelConfigurationEntity(
            id = configId,
            apiCredentialsId = credId,
            displayName = "Default"
        )
        coEvery { apiCredentialsDao.getById(credId) } returns ApiCredentialsEntity(
            id = credId,
            displayName = "GPT-4o",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            credentialAlias = "key1",
            isMultimodal = false
        )

        val result = repo.getDefault(ModelType.VISION)

        assertEquals(configId, result?.apiConfigId)
        assertNull(result?.localConfigId)
        assertEquals("Default", result?.displayName)
        assertEquals("OpenAI", result?.providerName)
    }
}
