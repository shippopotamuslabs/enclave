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
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import io.mockk.coEvery
import io.mockk.mockk
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
        coEvery { defaultModelsDao.getDefault(ModelType.MAIN) } returns DefaultModelEntity(
            modelType = ModelType.MAIN,
            localConfigId = 5L,
            apiConfigId = null
        )
        coEvery { localConfigsDao.getById(5L) } returns LocalModelConfigurationEntity(
            id = 5L,
            localModelId = 10L,
            displayName = "Precise"
        )
        coEvery { localModelsDao.getById(10L) } returns LocalModelEntity(
            id = 10L,
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "abc",
            sizeInBytes = 100,
            displayName = "Qwen3-4B",
            modelStatus = ModelStatus.CURRENT
        )

        val result = repo.getDefault(ModelType.MAIN)

        assertEquals(5L, result?.localConfigId)
        assertNull(result?.apiConfigId)
        assertEquals("Precise", result?.displayName)
        assertEquals("Qwen3-4B", result?.providerName) // Using model name as provider for local
    }

    @Test
    fun `get default with resolved display data for API model`() = runTest {
        coEvery { defaultModelsDao.getDefault(ModelType.VISION) } returns DefaultModelEntity(
            modelType = ModelType.VISION,
            localConfigId = null,
            apiConfigId = 3L
        )
        coEvery { apiConfigsDao.getById(3L) } returns ApiModelConfigurationEntity(
            id = 3L,
            apiCredentialsId = 12L,
            displayName = "Default"
        )
        coEvery { apiCredentialsDao.getById(12L) } returns ApiCredentialsEntity(
            id = 12L,
            displayName = "GPT-4o",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            credentialAlias = "key1",
            isVision = false
        )

        val result = repo.getDefault(ModelType.VISION)

        assertEquals(3L, result?.apiConfigId)
        assertNull(result?.localConfigId)
        assertEquals("Default", result?.displayName)
        assertEquals("OpenAI", result?.providerName)
    }
}