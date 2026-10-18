package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeModelConfigFetcher
import com.browntowndev.enclave.core.testing.FakeModelPackVerifierPort
import com.browntowndev.enclave.core.testing.FakeUtilityModelRepository
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GetModelPacksForSettingsUseCaseTest {
    private lateinit var modelConfigFetcher: FakeModelConfigFetcher
    private lateinit var modelPackVerifier: FakeModelPackVerifierPort
    private lateinit var localModelRepository: FakeLocalModelRepository
    private lateinit var utilityModelRepository: FakeUtilityModelRepository
    private lateinit var capabilityPort: FakeLocalImageGenerationCapability
    private lateinit var useCase: GetModelPacksForSettingsUseCase

    @BeforeEach
    fun setup() {
        modelConfigFetcher =
            FakeModelConfigFetcher().apply {
                persistedCatalogResult = Result.success(ModelPackTestFixtures.parsedCatalog())
            }
        modelPackVerifier = FakeModelPackVerifierPort()
        localModelRepository = FakeLocalModelRepository()
        utilityModelRepository = FakeUtilityModelRepository()
        capabilityPort = FakeLocalImageGenerationCapability()
        val resolveModelCatalog = ResolveModelCatalogUseCase(modelConfigFetcher)
        useCase =
            GetModelPacksForSettingsUseCase(
                resolveModelCatalog,
                VerifyModelPackCompletenessUseCase(modelPackVerifier, resolveModelCatalog),
                modelPackVerifier,
                localModelRepository,
                utilityModelRepository,
                capabilityPort,
            )
    }

    @Test
    fun returnsPackRowsFromCatalogWhenOnDeviceImageGenerationEnabled() =
        runTest {
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.PACK_ID,
                    installedCount = 1,
                    totalCount = 1,
                    isComplete = true,
                    members = emptyList(),
                )

            val rows = useCase()

            assertEquals(ModelPackTestFixtures.PACK_ID, rows.single().pack.packId)
        }

    @Test
    fun `SD15 pack remains eligible when only CPU MNN fallback is available`() =
        runTest {
            capabilityPort.capabilities =
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.NO,
                    sd15NpuSupported = LocalImageGenTriState.NO,
                    sd15CpuSupported = LocalImageGenTriState.YES,
                    sdxlNpuSupported = LocalImageGenTriState.NO,
                    recommendedBackend = LocalImageGenerationBackendKind.CPU_MNN,
                    recommendedModelId = LocalImageGenerationModelId.SD15_CPU,
                    diagnostics = emptyList(),
                )
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.PACK_ID,
                    installedCount = 0,
                    totalCount = 1,
                    isComplete = false,
                    members = emptyList(),
                )

            val rows = useCase()
            val sd15Row = rows.single { it.pack.packId == ModelPackTestFixtures.PACK_ID }

            assertTrue(sd15Row.isEligible)
            assertNull(sd15Row.disabledReason)
            assertTrue(sd15Row.sd15CpuFallbackActive)
            assertEquals("Stable Diffusion 1.5 (CPU)", sd15Row.pack.displayName)
        }

    @Test
    fun `SD15 pack keeps NPU label when the NPU pathway is available`() =
        runTest {
            capabilityPort.capabilities =
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.YES,
                    sd15CpuSupported = LocalImageGenTriState.YES,
                    sdxlNpuSupported = LocalImageGenTriState.NO,
                    recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                )
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.PACK_ID,
                    installedCount = 1,
                    totalCount = 1,
                    isComplete = true,
                    members = emptyList(),
                )

            val rows = useCase()
            val sd15Row = rows.single { it.pack.packId == ModelPackTestFixtures.PACK_ID }

            assertFalse(sd15Row.sd15CpuFallbackActive)
            assertEquals(ModelPackTestFixtures.PACK_DISPLAY, sd15Row.pack.displayName)
        }

    @Test
    fun surfacesGen2DisabledReasonForBlockedSdxlPackRow() =
        runTest {
            capabilityPort.capabilities =
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
            modelConfigFetcher.persistedCatalogResult =
                Result.success(ModelPackTestFixtures.parsedCatalogWithSdxl())
            utilityModelRepository.metadataByType[UtilityType.SD15_QNN_PACK] =
                utilityMetadata(UtilityType.SD15_QNN_PACK, "sd15-pack")
            utilityModelRepository.metadataByType[UtilityType.SDXL_QNN_PACK] =
                utilityMetadata(UtilityType.SDXL_QNN_PACK, "sdxl-pack")
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.PACK_ID,
                    installedCount = 2,
                    totalCount = 2,
                    isComplete = true,
                    members = emptyList(),
                )
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.SDXL_PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.SDXL_PACK_ID,
                    installedCount = 2,
                    totalCount = 2,
                    isComplete = true,
                    members = emptyList(),
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
            capabilityPort.capabilities =
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
            modelConfigFetcher.persistedCatalogResult =
                Result.success(ModelPackTestFixtures.parsedCatalogWithSdxl())
            utilityModelRepository.metadataByType[UtilityType.SD15_QNN_PACK] =
                utilityMetadata(UtilityType.SD15_QNN_PACK, "sd15-pack")
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.PACK_ID,
                    installedCount = 2,
                    totalCount = 2,
                    isComplete = true,
                    members = emptyList(),
                )
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.SDXL_PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.SDXL_PACK_ID,
                    installedCount = 0,
                    totalCount = 2,
                    isComplete = false,
                    members = emptyList(),
                )

            val rows = useCase()
            val sdxlRow = rows.single { it.pack.packId == ModelPackTestFixtures.SDXL_PACK_ID }

            assertEquals("Requires Snapdragon 8 Gen 3+ with QNN.", sdxlRow.disabledReason)
            assertFalse(sdxlRow.isEligible)
            assertFalse(sdxlRow.showDownloadAction)
        }

    @Test
    @DisplayName("SET-007 Gen2 hides Juggernaut from assignment and shows disabled pack reason")
    fun set007_gen2BlockedJuggernautPackShowsDisabledReason() =
        runTest {
            capabilityPort.capabilities =
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.YES,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.NO,
                    juggernautXlNpuSupported = LocalImageGenTriState.NO,
                    recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                    disabledReasonsByModelId =
                        mapOf(
                            LocalImageGenerationModelId.JUGGERNAUT_XL_QNN to
                                "Requires Snapdragon 8 Gen 3+ with QNN.",
                        ),
                )
            modelConfigFetcher.persistedCatalogResult =
                Result.success(ModelPackTestFixtures.parsedCatalogWithAllThreeImagePacks())
            utilityModelRepository.metadataByType[UtilityType.SD15_QNN_PACK] =
                utilityMetadata(UtilityType.SD15_QNN_PACK, "sd15-pack")
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.PACK_ID,
                    installedCount = 2,
                    totalCount = 2,
                    isComplete = true,
                    members = emptyList(),
                )
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.SDXL_PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.SDXL_PACK_ID,
                    installedCount = 0,
                    totalCount = 2,
                    isComplete = false,
                    members = emptyList(),
                )
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.JUGGERNAUT_PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.JUGGERNAUT_PACK_ID,
                    installedCount = 0,
                    totalCount = 2,
                    isComplete = false,
                    members = emptyList(),
                )

            val rows = useCase()
            val juggernautRow = rows.single { it.pack.packId == ModelPackTestFixtures.JUGGERNAUT_PACK_ID }

            assertEquals("Requires Snapdragon 8 Gen 3+ with QNN.", juggernautRow.disabledReason)
            assertFalse(juggernautRow.isEligible)
        }

    @Test
    fun surfacesRamBlockedReasonForIneligibleSdxlPackRow() =
        runTest {
            capabilityPort.capabilities =
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.NO,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.NO,
                    recommendedBackend = LocalImageGenerationBackendKind.NONE,
                    recommendedModelId = null,
                    diagnostics = listOf("Insufficient RAM for local diffusion (need >= 8 GiB)."),
                )
            modelConfigFetcher.persistedCatalogResult =
                Result.success(ModelPackTestFixtures.parsedCatalogWithSdxl())
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.PACK_ID,
                    installedCount = 0,
                    totalCount = 2,
                    isComplete = false,
                    members = emptyList(),
                )
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.SDXL_PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.SDXL_PACK_ID,
                    installedCount = 0,
                    totalCount = 2,
                    isComplete = false,
                    members = emptyList(),
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
            modelConfigFetcher.persistedCatalogResult =
                Result.success(ModelPackTestFixtures.parsedCatalog(includePack = false))

            val rows = useCase()

            assertTrue(rows.isEmpty())
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
