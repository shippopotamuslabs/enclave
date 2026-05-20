package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ActiveModelProviderImplTest {
    private val defaultModelRepository = mockk<DefaultModelRepositoryPort>()
    private val localModelsDao = mockk<LocalModelsDao>()
    private val localConfigsDao = mockk<LocalModelConfigurationsDao>()
    private val apiCredentialsDao = mockk<ApiCredentialsDao>()
    private val apiConfigsDao = mockk<ApiModelConfigurationsDao>()
    private val localEncryption = FakeLocalDataEncryptionService()

    private lateinit var repository: ActiveModelProviderImpl

    @BeforeEach
    fun setUp() {
        repository =
            ActiveModelProviderImpl(
                defaultModelRepository = defaultModelRepository,
                localModelsDao = localModelsDao,
                localConfigsDao = localConfigsDao,
                apiCredentialsDao = apiCredentialsDao,
                apiConfigsDao = apiConfigsDao,
                localEncryption = localEncryption,
            )
    }

    @Test
    fun `getActiveConfiguration decrypts local config fields`() =
        runTest {
            val configId = LocalModelConfigurationId("local-config-1")
            val modelId = LocalModelId("model-1")
            coEvery { defaultModelRepository.getDefault(ModelType.MAIN) } returns
                DefaultModelAssignment(
                    modelType = ModelType.MAIN,
                    localConfigId = configId,
                )
            coEvery { localConfigsDao.getById(configId) } returns
                LocalModelConfigurationEntity(
                    id = configId,
                    localModelId = modelId,
                    displayName = localEncryption.encryptString("Balanced"),
                    systemPrompt = localEncryption.encryptString("Use brief answers"),
                )
            coEvery { localModelsDao.getById(modelId) } returns
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.GGUF,
                    huggingFaceModelName = "qwen",
                    remoteFilename = "qwen.gguf",
                    localFilename = "qwen.gguf",
                    sha256 = "abc123",
                    sizeInBytes = 42L,
                    isMultimodal = true,
                )

            val result = repository.getActiveConfiguration(ModelType.MAIN)

            assertTrue(result?.isLocal == true)
            assertEquals("Balanced", result?.name)
            assertEquals("Use brief answers", result?.systemPrompt)
            assertEquals("qwen.gguf", result?.modelId)
            assertEquals(ModelFileFormat.GGUF, result?.localModelFormat)
            assertEquals(true, result?.isMultimodal)
        }

    @Test
    fun `getActiveConfiguration decrypts api config fields`() =
        runTest {
            val configId = ApiModelConfigurationId("api-config-1")
            val credentialsId = ApiCredentialsId("creds-1")
            coEvery { defaultModelRepository.getDefault(ModelType.VISION) } returns
                DefaultModelAssignment(
                    modelType = ModelType.VISION,
                    apiConfigId = configId,
                )
            coEvery { apiConfigsDao.getById(configId) } returns
                ApiModelConfigurationEntity(
                    id = configId,
                    apiCredentialsId = credentialsId,
                    displayName = localEncryption.encryptString("Default"),
                    systemPrompt = localEncryption.encryptString("Be helpful"),
                )
            coEvery { apiCredentialsDao.getById(credentialsId) } returns
                ApiCredentialsEntity(
                    id = credentialsId,
                    displayName = localEncryption.encryptString("OpenAI Primary"),
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4o",
                    baseUrl = localEncryption.encryptString("https://api.openai.com/v1"),
                    credentialAlias = "openai-key",
                    isMultimodal = true,
                )

            val result = repository.getActiveConfiguration(ModelType.VISION)

            assertTrue(result?.isLocal == false)
            assertEquals("Default", result?.name)
            assertEquals("Be helpful", result?.systemPrompt)
            assertEquals("gpt-4o", result?.modelId)
            assertEquals(ApiProvider.OPENAI, result?.apiProvider)
            assertEquals(true, result?.isMultimodal)
        }

    @Test
    fun `getActiveConfiguration returns null when no default assignment`() =
        runTest {
            coEvery { defaultModelRepository.getDefault(ModelType.MAIN) } returns null

            val result = repository.getActiveConfiguration(ModelType.MAIN)

            assertNull(result)
        }

    @Test
    fun `getActiveConfiguration throws when display name decrypt fails`() =
        runTest {
            localEncryption.forceDecryptFailure = true
            val configId = LocalModelConfigurationId("local-config-1")
            val modelId = LocalModelId("model-1")
            coEvery { defaultModelRepository.getDefault(ModelType.MAIN) } returns
                DefaultModelAssignment(
                    modelType = ModelType.MAIN,
                    localConfigId = configId,
                )
            coEvery { localConfigsDao.getById(configId) } returns
                LocalModelConfigurationEntity(
                    id = configId,
                    localModelId = modelId,
                    displayName = "ciphertext",
                )
            coEvery { localModelsDao.getById(modelId) } returns
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.GGUF,
                    huggingFaceModelName = "qwen",
                    remoteFilename = "qwen.gguf",
                    localFilename = "qwen.gguf",
                    sha256 = "abc123",
                    sizeInBytes = 42L,
                    isMultimodal = false,
                )

            val thrown =
                assertThrows<ActiveModelConfigurationDecryptException> {
                    repository.getActiveConfiguration(ModelType.MAIN)
                }

            assertEquals(ModelType.MAIN, thrown.modelType)
            assertEquals(ActiveModelConfigurationDecryptException.USER_MESSAGE, thrown.message)
        }
}
