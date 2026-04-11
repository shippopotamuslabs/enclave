package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.FakeDefaultModelRepository
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
    private lateinit var useCase: SetDefaultModelUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeDefaultModelRepository()
        localModelRepository = mockk(relaxed = true)
        useCase = SetDefaultModelUseCaseImpl(repository, localModelRepository)
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
                coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("1")) } returns LocalModelAsset(
                    metadata = LocalModelMetadata(
                        id = LocalModelId("asset-vision"),
                        huggingFaceModelName = "vision-model",
                        remoteFileName = "vision.litertlm",
                        localFileName = "vision.litertlm",
                        sha256 = "vision-sha",
                        sizeInBytes = 1_024,
                        modelFileFormat = ModelFileFormat.LITERTLM,
                        source = DownloadSource.HUGGING_FACE,
                        visionCapable = true,
                    ),
                    configurations = emptyList(),
                )
            }
            useCase(modelType, localConfigId = LocalModelConfigurationId("1"), apiConfigId = null)
            assertEquals(modelType, repository.lastSetCall?.first)
        }
    }

    @Test
    fun `set VISION slot rejects non vision capable local model`() = runTest {
        val configId = LocalModelConfigurationId("vision-local")
        coEvery { localModelRepository.getAssetByConfigId(configId) } returns LocalModelAsset(
            metadata = LocalModelMetadata(
                id = LocalModelId("asset-1"),
                huggingFaceModelName = "text-only",
                remoteFileName = "model.litertlm",
                localFileName = "model.litertlm",
                sha256 = "sha",
                sizeInBytes = 1_024,
                modelFileFormat = ModelFileFormat.LITERTLM,
                source = DownloadSource.HUGGING_FACE,
                visionCapable = false,
            ),
            configurations = emptyList(),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            useCase(ModelType.VISION, localConfigId = configId, apiConfigId = null)
        }

        assertEquals("Vision slot requires a vision-capable local model.", error.message)
    }
}
