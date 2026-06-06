package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import com.browntowndev.enclave.domain.port.download.ModelPackVerifierPort
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GetModelPacksForSettingsUseCaseTest {
    private lateinit var resolveModelCatalog: ResolveModelCatalogUseCase
    private lateinit var verifyModelPackCompleteness: VerifyModelPackCompletenessUseCase
    private lateinit var localModelRepository: LocalModelRepositoryPort
    private lateinit var utilityModelRepository: UtilityModelRepositoryPort
    private lateinit var modelPackVerifier: ModelPackVerifierPort
    private lateinit var capabilityPort: LocalImageGenerationCapabilityPort
    private lateinit var useCase: GetModelPacksForSettingsUseCase

    @BeforeEach
    fun setup() {
        resolveModelCatalog = mockk()
        verifyModelPackCompleteness = mockk()
        modelPackVerifier = mockk()
        localModelRepository = mockk(relaxed = true)
        utilityModelRepository = mockk(relaxed = true)
        capabilityPort = mockk(relaxed = true)
        coEvery { resolveModelCatalog() } returns Result.success(ModelPackTestFixtures.parsedCatalog())
        useCase =
            GetModelPacksForSettingsUseCase(
                resolveModelCatalog,
                verifyModelPackCompleteness,
                modelPackVerifier,
                localModelRepository,
                utilityModelRepository,
                capabilityPort,
            )
    }

    @Test
    fun returnsPackRowsFromCatalogWhenOnDeviceImageGenerationEnabled() =
        runTest {
            coEvery { localModelRepository.getSoftDeletedModels() } returns emptyList()
            coEvery { utilityModelRepository.getByUtilityType(any()) } returns null
            coEvery { verifyModelPackCompleteness(ModelPackTestFixtures.PACK_ID) } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.PACK_ID,
                        installedCount = 1,
                        totalCount = 1,
                        isComplete = true,
                        members = emptyList(),
                    ),
                )

            val rows = useCase()

            assertEquals(ModelPackTestFixtures.PACK_ID, rows.single().pack.packId)
        }

    @Test
    fun surfacesGen2DisabledReasonForBlockedSdxlPackRow() =
        runTest {
            every { capabilityPort.getCapabilities() } returns
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.YES,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.NO,
                    recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                    disabledReasonsByModelId =
                        mapOf(
                            LocalImageGenerationModelId.SDXL_QNN to
                                "Requires Snapdragon 8 Gen 3+ with QNN.",
                        ),
                )
            coEvery { resolveModelCatalog() } returns Result.success(ModelPackTestFixtures.parsedCatalogWithSdxl())
            coEvery { localModelRepository.getSoftDeletedModels() } returns emptyList()
            coEvery { utilityModelRepository.getByUtilityType(UtilityType.SD15_QNN_PACK) } returns mockk(relaxed = true)
            coEvery { utilityModelRepository.getByUtilityType(UtilityType.SDXL_QNN_PACK) } returns mockk(relaxed = true)
            coEvery { verifyModelPackCompleteness(ModelPackTestFixtures.PACK_ID) } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.PACK_ID,
                        installedCount = 2,
                        totalCount = 2,
                        isComplete = true,
                        members = emptyList(),
                    ),
                )
            coEvery { verifyModelPackCompleteness(ModelPackTestFixtures.SDXL_PACK_ID) } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.SDXL_PACK_ID,
                        installedCount = 2,
                        totalCount = 2,
                        isComplete = true,
                        members = emptyList(),
                    ),
                )

            val rows = useCase()
            val sdxlRow = rows.single { it.pack.packId == ModelPackTestFixtures.SDXL_PACK_ID }

            assertEquals("Requires Snapdragon 8 Gen 3+ with QNN.", sdxlRow.disabledReason)
            assertFalse(sdxlRow.isEligible)
        }

    @Test
    @DisplayName("SET-002 Gen2 blocked incomplete SDXL pack hides download action")
    fun set002_gen2BlockedIncompleteSdxlPackHidesDownloadAction() =
        runTest {
            every { capabilityPort.getCapabilities() } returns
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.YES,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.NO,
                    recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                    disabledReasonsByModelId =
                        mapOf(
                            LocalImageGenerationModelId.SDXL_QNN to
                                "Requires Snapdragon 8 Gen 3+ with QNN.",
                        ),
                )
            coEvery { resolveModelCatalog() } returns Result.success(ModelPackTestFixtures.parsedCatalogWithSdxl())
            coEvery { localModelRepository.getSoftDeletedModels() } returns emptyList()
            coEvery { utilityModelRepository.getByUtilityType(UtilityType.SD15_QNN_PACK) } returns mockk(relaxed = true)
            coEvery { utilityModelRepository.getByUtilityType(UtilityType.SDXL_QNN_PACK) } returns null
            coEvery { verifyModelPackCompleteness(ModelPackTestFixtures.PACK_ID) } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.PACK_ID,
                        installedCount = 2,
                        totalCount = 2,
                        isComplete = true,
                        members = emptyList(),
                    ),
                )
            coEvery { verifyModelPackCompleteness(ModelPackTestFixtures.SDXL_PACK_ID) } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.SDXL_PACK_ID,
                        installedCount = 0,
                        totalCount = 2,
                        isComplete = false,
                        members = emptyList(),
                    ),
                )

            val rows = useCase()
            val sdxlRow = rows.single { it.pack.packId == ModelPackTestFixtures.SDXL_PACK_ID }

            assertEquals("Requires Snapdragon 8 Gen 3+ with QNN.", sdxlRow.disabledReason)
            assertFalse(sdxlRow.isEligible)
            assertFalse(sdxlRow.showDownloadAction)
        }

    @Test
    fun surfacesRamBlockedReasonForIneligibleSdxlPackRow() =
        runTest {
            every { capabilityPort.getCapabilities() } returns
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.NO,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.NO,
                    recommendedBackend = LocalImageGenerationBackendKind.NONE,
                    recommendedModelId = null,
                    diagnostics = listOf("Insufficient RAM for local diffusion (need >= 8 GiB)."),
                )
            coEvery { resolveModelCatalog() } returns Result.success(ModelPackTestFixtures.parsedCatalogWithSdxl())
            coEvery { localModelRepository.getSoftDeletedModels() } returns emptyList()
            coEvery { utilityModelRepository.getByUtilityType(UtilityType.SD15_QNN_PACK) } returns null
            coEvery { utilityModelRepository.getByUtilityType(UtilityType.SDXL_QNN_PACK) } returns null
            coEvery { verifyModelPackCompleteness(ModelPackTestFixtures.PACK_ID) } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.PACK_ID,
                        installedCount = 0,
                        totalCount = 2,
                        isComplete = false,
                        members = emptyList(),
                    ),
                )
            coEvery { verifyModelPackCompleteness(ModelPackTestFixtures.SDXL_PACK_ID) } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.SDXL_PACK_ID,
                        installedCount = 0,
                        totalCount = 2,
                        isComplete = false,
                        members = emptyList(),
                    ),
                )

            val rows = useCase()
            val sdxlRow = rows.single { it.pack.packId == ModelPackTestFixtures.SDXL_PACK_ID }

            assertFalse(sdxlRow.isEligible)
            assertEquals("Insufficient RAM for local diffusion (need >= 8 GiB).", sdxlRow.disabledReason)
            assertFalse(sdxlRow.showDownloadAction)
        }

    @Test
    fun returnsEmptyWhenCatalogHasNoModelPacks() =
        runTest {
            coEvery { resolveModelCatalog() } returns
                Result.success(ModelPackTestFixtures.parsedCatalog(includePack = false))

            val rows = useCase()

            assertTrue(rows.isEmpty())
        }
}
