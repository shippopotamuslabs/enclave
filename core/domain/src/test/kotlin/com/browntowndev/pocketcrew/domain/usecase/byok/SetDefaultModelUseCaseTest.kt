package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.FakeDefaultModelRepository
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class SetDefaultModelUseCaseTest {

    private lateinit var repository: FakeDefaultModelRepository
    private lateinit var localModelRepository: LocalModelRepositoryPort
    private lateinit var apiModelRepository: ApiModelRepositoryPort
    private lateinit var useCase: SetDefaultModelUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeDefaultModelRepository()
        localModelRepository = mockk(relaxed = true)
        apiModelRepository = mockk(relaxed = true)
        useCase = SetDefaultModelUseCaseImpl(repository, localModelRepository, apiModelRepository)
    }

    // ========================================================================
    // Happy Path
    // ========================================================================

    @Test
    fun `set FAST slot to API source`() = runTest {
        repository.seed(ModelType.entries.map { DefaultModelAssignment(it, localConfigId = LocalModelConfigurationId("1")) })

        useCase(ModelType.FAST, localConfigId = null, apiConfigId = ApiModelConfigurationId("5"))

        assertEquals(Triple(ModelType.FAST, null, ApiModelConfigurationId("5")), repository.lastSetCall)
    }

    @Test
    fun `set THINKING slot back to ON_DEVICE`() = runTest {
        repository.seed(listOf(DefaultModelAssignment(ModelType.THINKING, apiConfigId = ApiModelConfigurationId("5"))))

        useCase(ModelType.THINKING, localConfigId = LocalModelConfigurationId("1"), apiConfigId = null)

        assertEquals(Triple(ModelType.THINKING, LocalModelConfigurationId("1"), null), repository.lastSetCall)
    }

    @Test
    fun `set default for every ModelType variant`() = runTest {
        for (modelType in ModelType.entries) {
            if (modelType == ModelType.VISION) {
                val configId = ApiModelConfigurationId("vision-api-config")
                val credentialsId = ApiCredentialsId("vision-api-creds")
                coEvery { apiModelRepository.getConfigurationById(configId) } returns ApiModelConfiguration(
                    id = configId,
                    apiCredentialsId = credentialsId,
                    displayName = "Vision API",
                )
                coEvery { apiModelRepository.getCredentialsById(credentialsId) } returns ApiCredentials(
                    id = credentialsId,
                    displayName = "Vision API",
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4o",
                    isMultimodal = true,
                    credentialAlias = "vision-api",
                )
                useCase(modelType, localConfigId = null, apiConfigId = configId)
            } else {
                useCase(modelType, localConfigId = LocalModelConfigurationId("1"), apiConfigId = null)
            }
            assertEquals(modelType, repository.lastSetCall?.first)
        }
    }

    @Test
    fun `set VISION slot rejects local model assignments`() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            useCase(ModelType.VISION, localConfigId = LocalModelConfigurationId("vision-local"), apiConfigId = null)
        }

        assertEquals("Vision slot is API-only.", error.message)
    }

    @Test
    fun `set VISION slot rejects non vision capable API model`() = runTest {
        val configId = ApiModelConfigurationId("vision-api-config")
        val credentialsId = ApiCredentialsId("vision-api-creds")
        coEvery { apiModelRepository.getConfigurationById(configId) } returns ApiModelConfiguration(
            id = configId,
            apiCredentialsId = credentialsId,
            displayName = "Vision API",
        )
        coEvery { apiModelRepository.getCredentialsById(credentialsId) } returns ApiCredentials(
            id = credentialsId,
            displayName = "Vision API",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            isMultimodal = false,
            credentialAlias = "vision-api",
        )

        val error = assertFailsWith<IllegalArgumentException> {
            useCase(ModelType.VISION, localConfigId = null, apiConfigId = configId)
        }

        assertEquals("Vision slot requires a multimodal API model.", error.message)
    }
}
