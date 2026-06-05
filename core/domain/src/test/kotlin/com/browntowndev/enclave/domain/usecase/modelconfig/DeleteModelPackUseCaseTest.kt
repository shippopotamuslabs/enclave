package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.port.download.ModelFileScannerPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DeleteModelPackUseCaseTest {
    private lateinit var deleteLocalModel: DeleteLocalModelUseCase
    private lateinit var utilityModelRepository: UtilityModelRepositoryPort
    private lateinit var modelFileScanner: ModelFileScannerPort
    private lateinit var resolveModelCatalog: ResolveModelCatalogUseCase
    private lateinit var useCase: DeleteModelPackUseCase

    @BeforeEach
    fun setup() {
        deleteLocalModel = mockk(relaxed = true)
        utilityModelRepository = mockk(relaxed = true)
        modelFileScanner = mockk(relaxed = true)
        resolveModelCatalog = mockk(relaxed = true)
        coEvery { resolveModelCatalog() } returns Result.success(ModelPackTestFixtures.parsedCatalog())
        useCase =
            DeleteModelPackUseCase(
                deleteLocalModel,
                utilityModelRepository,
                modelFileScanner,
                resolveModelCatalog,
            )
    }

    @Test
    @DisplayName("MPK-013 Delete pack removes DB soft-delete and physical files for all members")
    fun mpk013_deletePackRemovesRegistryAndPhysicalFiles() =
        runTest {
            useCase(ModelPackTestFixtures.PACK_ID).getOrThrow()

            coVerify {
                deleteLocalModel(
                    ModelPackTestFixtures.diffusionAsset().metadata.id,
                    any(),
                    any(),
                    any(),
                )
            }
            coVerify(exactly = 0) { utilityModelRepository.softDelete(any()) }
            coVerify { modelFileScanner.deleteModelFileByLocalFileName(ModelPackTestFixtures.DIFFUSION_FILE) }
        }

    private fun utilityMetadata(
        type: UtilityType,
        id: String,
    ) = UtilityModelMetadata(
        id = UtilityModelId(id),
        utilityType = type,
        sha256 = "sha",
        sizeInBytes = 1L,
        localFileName = "file",
        remoteFileName = "file",
        huggingFaceModelName = "hf/model",
        modelFileFormat = ModelFileFormat.GGUF,
        source = DownloadSource.HUGGING_FACE,
    )
}
