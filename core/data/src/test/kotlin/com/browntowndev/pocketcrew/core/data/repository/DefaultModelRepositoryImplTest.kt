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
import com.browntowndev.pocketcrew.core.data.local.TtsProviderDao
import com.browntowndev.pocketcrew.core.data.local.TtsProviderEntity
import com.browntowndev.pocketcrew.core.data.local.MediaProviderDao
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
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
    private val ttsProviderDao = mockk<TtsProviderDao>()
    private val mediaProviderDao = mockk<MediaProviderDao>()

    @BeforeEach
    fun setup() {
        repo = DefaultModelRepositoryImpl(
            defaultModelsDao = defaultModelsDao,
            localModelConfigurationsDao = localConfigsDao,
            localModelsDao = localModelsDao,
            apiModelConfigurationsDao = apiConfigsDao,
            apiCredentialsDao = apiCredentialsDao,
            ttsProviderDao = ttsProviderDao,
            mediaProviderDao = mediaProviderDao
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
        assertEquals("qwen", result?.displayName)
        assertEquals("Local", result?.providerName)
        assertEquals("Precise", result?.presetName)
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
            displayName = "OpenAI Primary",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            credentialAlias = "key1",
            isMultimodal = false
        )

        val result = repo.getDefault(ModelType.VISION)

        assertEquals(configId, result?.apiConfigId)
        assertNull(result?.localConfigId)
        assertEquals("OpenAI Primary", result?.displayName)
        assertEquals("OpenAI", result?.providerName)
        assertEquals("Default", result?.presetName)
    }

    @Test
    fun `get default with resolved display data for TTS model`() = runTest {
        val ttsId = TtsProviderId("tts-1")
        coEvery { defaultModelsDao.getDefault(ModelType.TTS) } returns DefaultModelEntity(
            modelType = ModelType.TTS,
            ttsProviderId = ttsId
        )
        coEvery { ttsProviderDao.getTtsProvider(ttsId.value) } returns TtsProviderEntity(
            id = ttsId.value,
            displayName = "My Grok TTS",
            provider = ApiProvider.XAI,
            voiceName = "eve",
            credentialAlias = "xai-key"
        )

        val result = repo.getDefault(ModelType.TTS)

        assertEquals(ttsId, result?.ttsProviderId)
        assertEquals("My Grok TTS", result?.displayName)
        assertEquals("xAI", result?.providerName)
        assertEquals("eve", result?.presetName)
    }
}
