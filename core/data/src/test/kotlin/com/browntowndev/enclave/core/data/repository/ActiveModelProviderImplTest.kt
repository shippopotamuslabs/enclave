package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiCredentialsEntity
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationEntity
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelEntity
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

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
    fun `SCN-ACTIVE-001 active model provider uses asset displayName for local config`() =
        runTest {
            val configId = LocalModelConfigurationId("cfg-fast")
            val modelId = LocalModelId("model-1")
            coEvery { defaultModelRepository.getDefault(ModelType.FAST) } returns
                DefaultModelAssignment(
                    modelType = ModelType.FAST,
                    localConfigId = configId,
                )
            coEvery { localConfigsDao.getById(configId) } returns
                LocalModelConfigurationEntity(
                    id = configId,
                    localModelId = modelId,
                    displayName = localEncryption.encryptString("Fast"),
                )
            coEvery { localModelsDao.getById(modelId) } returns
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.LITERTLM,
                    displayName = "Gemma 4 E4B",
                    huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm",
                    remoteFilename = "gemma-4-E4B-it.litertlm",
                    localFilename = "gemma-4-E4B-it.litertlm",
                    sha256 = "gemma-sha",
                    sizeInBytes = 42L,
                )

            val result = repository.getActiveConfiguration(ModelType.FAST)

            assertEquals("Gemma 4 E4B", result?.name)
            assertEquals("gemma-4-E4B-it.litertlm", result?.modelId)
        }

    @Test
    fun `SCN-ACTIVE-001 Active Provider Model From Preset`() =
        runTest {
            val result = activeApiConfigurationFromPreset()

            assertEquals("gpt-4o", result.modelId)
            assertEquals(ApiProvider.OPENAI, result.apiProvider)
        }

    @Test
    fun `SCN-ACTIVE-002 Active Provider Multimodal From Preset`() =
        runTest {
            val result = activeApiConfigurationFromPreset()

            assertEquals(true, result.isMultimodal)
        }

    @Test
    fun `SCN-MUT-002 Active Provider Must Not Read Credential Model`() =
        runTest {
            val result = activeApiConfigurationFromPreset(modelType = ModelType.MAIN)

            assertEquals("gpt-4o", result.modelId)
            assertFalse(ApiCredentialsEntity::class.members.any { it.name == "modelId" })
        }

    @Test
    fun `SCN-MUT-002 source ActiveModelProviderImpl does not read credentials modelId`() {
        val sourcePath =
            Paths.get(
                "src/main/kotlin/com/browntowndev/enclave/core/data/repository/ActiveModelProviderImpl.kt",
            )
        val source = Files.readString(sourcePath)
        assertFalse(source.contains("credentials.modelId"))
    }

    private suspend fun activeApiConfigurationFromPreset(modelType: ModelType = ModelType.VISION): ActiveModelConfiguration {
        val configId = ApiModelConfigurationId("cfg-9")
        val credentialsId = ApiCredentialsId("creds-1")
        coEvery { defaultModelRepository.getDefault(modelType) } returns
            DefaultModelAssignment(
                modelType = modelType,
                apiConfigId = configId,
            )
        coEvery { apiConfigsDao.getById(configId) } returns
            ApiModelConfigurationEntity(
                id = configId,
                apiCredentialsId = credentialsId,
                displayName = localEncryption.encryptString("Default"),
                modelId = "gpt-4o",
                isMultimodal = true,
                systemPrompt = localEncryption.encryptString("Be helpful"),
            )
        coEvery { apiCredentialsDao.getById(credentialsId) } returns
            ApiCredentialsEntity(
                id = credentialsId,
                displayName = localEncryption.encryptString("OpenAI Primary"),
                provider = ApiProvider.OPENAI,
                baseUrl = localEncryption.encryptString("https://api.openai.com/v1"),
                credentialAlias = "openai-key",
            )

        return requireNotNull(repository.getActiveConfiguration(modelType))
    }

    @Test
    fun `getActiveConfiguration returns null when no default assignment`() =
        runTest {
            coEvery { defaultModelRepository.getDefault(ModelType.MAIN) } returns null

            val result = repository.getActiveConfiguration(ModelType.MAIN)

            assertNull(result)
        }

    @Test
    fun `getActiveConfiguration returns null system prompt when decrypt fails`() =
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
                    displayName = localEncryption.encryptString("Balanced"),
                    systemPrompt = localEncryption.encryptString("Use brief answers"),
                )
            coEvery { localModelsDao.getById(modelId) } returns
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.GGUF,
                    displayName = "Gemma 4 E4B",
                    huggingFaceModelName = "qwen",
                    remoteFilename = "qwen.gguf",
                    localFilename = "qwen.gguf",
                    sha256 = "abc123",
                    sizeInBytes = 42L,
                )

            val result = repository.getActiveConfiguration(ModelType.MAIN)

            assertEquals("Gemma 4 E4B", result?.name)
            assertNull(result?.systemPrompt)
        }
}
