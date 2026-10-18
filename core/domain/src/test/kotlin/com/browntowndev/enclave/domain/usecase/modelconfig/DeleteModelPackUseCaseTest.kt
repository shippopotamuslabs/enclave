package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeModelConfigFetcher
import com.browntowndev.enclave.core.testing.FakeModelFileScanner
import com.browntowndev.enclave.core.testing.FakeUtilityModelRepository
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DeleteModelPackUseCaseTest {
    private lateinit var deleteLocalModel: RecordingDeleteLocalModelUseCase
    private lateinit var utilityModelRepository: FakeUtilityModelRepository
    private lateinit var modelFileScanner: FakeModelFileScanner
    private lateinit var useCase: DeleteModelPackUseCase

    @BeforeEach
    fun setup() {
        deleteLocalModel = RecordingDeleteLocalModelUseCase()
        utilityModelRepository = FakeUtilityModelRepository()
        modelFileScanner = FakeModelFileScanner()
        val modelConfigFetcher =
            FakeModelConfigFetcher().apply {
                persistedCatalogResult = Result.success(ModelPackTestFixtures.parsedCatalog())
            }
        useCase =
            DeleteModelPackUseCase(
                deleteLocalModel,
                utilityModelRepository,
                modelFileScanner,
                ResolveModelCatalogUseCase(modelConfigFetcher),
            )
    }

    @Test
    @DisplayName("MPK-013 Delete pack removes DB soft-delete and physical files for all members")
    fun mpk013_deletePackRemovesRegistryAndPhysicalFiles() =
        runTest {
            useCase(ModelPackTestFixtures.PACK_ID).getOrThrow()

            assertEquals(
                listOf(
                    RecordingDeleteLocalModelUseCase.Call(
                        modelId = ModelPackTestFixtures.diffusionAsset().metadata.id,
                        replacementLocalConfigId = null,
                        replacementApiConfigId = null,
                        replacementMediaProviderId = null,
                    ),
                ),
                deleteLocalModel.calls,
            )
            assertTrue(utilityModelRepository.softDeletedIds.isEmpty())
            assertTrue(modelFileScanner.deletedFileNames.contains(ModelPackTestFixtures.DIFFUSION_FILE))
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

    /** Records [DeleteLocalModelUseCase] invocations instead of executing the delete. */
    private class RecordingDeleteLocalModelUseCase :
        DeleteLocalModelUseCase(
            FakeLocalModelRepository(),
            FakeDefaultModelRepository(),
            FakeModelFileScanner(),
            FakeApiModelRepository(),
            FakeLoggingPort(),
        ) {
        data class Call(
            val modelId: LocalModelId,
            val replacementLocalConfigId: LocalModelConfigurationId?,
            val replacementApiConfigId: ApiModelConfigurationId?,
            val replacementMediaProviderId: MediaProviderId?,
        )

        val calls = mutableListOf<Call>()

        override suspend fun invoke(
            modelId: LocalModelId,
            replacementLocalConfigId: LocalModelConfigurationId?,
            replacementApiConfigId: ApiModelConfigurationId?,
            replacementMediaProviderId: MediaProviderId?,
        ): Result<Unit> {
            calls += Call(modelId, replacementLocalConfigId, replacementApiConfigId, replacementMediaProviderId)
            return Result.success(Unit)
        }
    }
}
