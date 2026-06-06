package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry
import com.browntowndev.enclave.domain.port.media.GpuCapabilityPort
import com.browntowndev.enclave.domain.port.media.LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import com.browntowndev.enclave.domain.usecase.FakeDefaultModelRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResolveAssignedImageGenerationTargetUseCaseTest {
    @Test
    fun `SD15 assignment resolves model directory and QNN backend`() =
        runTest {
            val modelFile = "/abs/models/stable-diffusion-v1-5-Q4_0.gguf"
            val filePort =
                mockk<com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort> {
                    coEvery { resolveLocalModelPath(any()) } returns modelFile
                }
            val useCase = useCaseWith(filePort)

            val target = useCase().getOrThrow() as ImageGenerationTarget.LocalStableDiffusion

            assertEquals("/abs/models/sd15-qnn", target.modelDir)
            assertEquals(LocalImageGenerationModelId.SD15_QNN, target.modelId)
            assertEquals(LocalImageGenerationBackendKind.QNN_NPU, target.selectedBackend)
        }

    @Test
    fun `SDXL assignment resolves sdxl-qnn model directory and QNN backend`() =
        runTest {
            val modelFile = "/abs/models/${ModelPackTestFixtures.SDXL_DIFFUSION_FILE}"
            val filePort =
                mockk<com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort> {
                    coEvery { resolveLocalModelPath(any()) } returns modelFile
                }
            val sdxlConfigId = LocalModelConfigurationId(ModelPackTestFixtures.SDXL_CONFIG_ID)
            val useCase =
                useCaseWith(
                    filePort = filePort,
                    asset = ModelPackTestFixtures.sdxlDiffusionAsset(),
                    localConfigId = sdxlConfigId,
                    capabilities = sdxlSupportedCapabilities(),
                )

            val target = useCase().getOrThrow() as ImageGenerationTarget.LocalStableDiffusion

            assertEquals("/abs/models/sdxl-qnn", target.modelDir)
            assertEquals(LocalImageGenerationModelId.SDXL_QNN, target.modelId)
            assertEquals(LocalImageGenerationBackendKind.QNN_NPU, target.selectedBackend)
            assertEquals(sdxlConfigId, target.localConfigId)
        }

    @Test
    fun `IMAGE_EDIT slot resolves local stable diffusion assignment`() =
        runTest {
            val modelFile = "/abs/models/${ModelPackTestFixtures.SDXL_DIFFUSION_FILE}"
            val filePort =
                mockk<com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort> {
                    coEvery { resolveLocalModelPath(any()) } returns modelFile
                }
            val sdxlConfigId = LocalModelConfigurationId(ModelPackTestFixtures.SDXL_CONFIG_ID)
            val useCase =
                useCaseWith(
                    filePort = filePort,
                    asset = ModelPackTestFixtures.sdxlDiffusionAsset(),
                    localConfigId = sdxlConfigId,
                    slot = ModelType.IMAGE_EDIT,
                    capabilities = sdxlSupportedCapabilities(),
                )

            val target = useCase(ModelType.IMAGE_EDIT).getOrThrow() as ImageGenerationTarget.LocalStableDiffusion

            assertEquals("/abs/models/sdxl-qnn", target.modelDir)
            assertEquals(sdxlConfigId, target.localConfigId)
        }

    @Test
    fun `missing diffusion file on disk surfaces not-downloaded even when repo row exists`() =
        runTest {
            val filePort =
                mockk<com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort> {
                    coEvery { resolveLocalModelPath(any()) } returns null
                }
            val useCase = useCaseWith(filePort)

            val result = useCase()

            assertTrue(result.isFailure)
            assertEquals("Image generation model is not fully downloaded.", result.exceptionOrNull()?.message)
        }

    @Test
    fun `IMAGE_EDIT missing assignment surfaces edit-specific message`() =
        runTest {
            val useCase =
                ResolveAssignedImageGenerationTargetUseCase(
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = mockk(relaxed = true),
                    localStableDiffusionTargetResolver = mockk(relaxed = true),
                )

            val result = useCase(ModelType.IMAGE_EDIT)

            assertTrue(result.isFailure)
            assertEquals("No image edit model assigned.", result.exceptionOrNull()?.message)
        }

    @Test
    fun `ordinary local chat model is rejected before native artifact resolution`() =
        runTest {
            val useCase =
                useCaseWith(
                    filePort = mockk(relaxed = true),
                    asset = sd15Asset(sha256 = "ordinary-chat-gguf-sha"),
                )

            val result = useCase()

            assertTrue(result.isFailure)
            assertEquals(
                "Assigned local model is not compatible with image generation.",
                result.exceptionOrNull()?.message,
            )
        }

    @Test
    fun `unsupported device rejects local target before artifact resolution`() =
        runTest {
            val filePort = mockk<com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort>(relaxed = true)
            val useCase = useCaseWith(filePort, localImageGenerationSupported = false)

            val result = useCase()

            assertTrue(result.isFailure)
            assertEquals(LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE, result.exceptionOrNull()?.message)
            coVerify(exactly = 0) { filePort.resolveLocalModelPath(any()) }
        }

    private fun useCaseWith(
        filePort: com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort,
        asset: LocalModelAsset = sd15Asset(),
        localConfigId: LocalModelConfigurationId = LocalModelConfigurationId("sd15-local-config"),
        slot: ModelType = ModelType.IMAGE_GENERATION,
        capabilities: LocalImageGenerationCapabilities = supportedCapabilities(),
        localImageGenerationSupported: Boolean = true,
    ): ResolveAssignedImageGenerationTargetUseCase {
        val effectiveCapabilities =
            if (localImageGenerationSupported) {
                capabilities
            } else {
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.NO,
                    sd15NpuSupported = LocalImageGenTriState.NO,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.NO,
                    recommendedBackend = LocalImageGenerationBackendKind.NONE,
                    recommendedModelId = null,
                    diagnostics = listOf("Disabled for test."),
                )
            }
        val localModelRepository =
            mockk<com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort> {
                coEvery { getAssetByConfigId(localConfigId) } returns asset
            }
        val resolver =
            LocalStableDiffusionTargetResolver(
                localModelRepository = localModelRepository,
                utilityModelFilePort = filePort,
                gpuCapabilityPort =
                    mockk {
                        every { isLocalImageGenerationSupported() } returns localImageGenerationSupported
                    },
                capabilityPort =
                    mockk {
                        every { getCapabilities() } returns effectiveCapabilities
                    },
                selectBackend = SelectLocalImageGenerationBackendUseCase(),
            )
        return ResolveAssignedImageGenerationTargetUseCase(
            defaultModelRepository =
                FakeDefaultModelRepository().apply {
                    seed(
                        listOf(
                            DefaultModelAssignment(
                                modelType = slot,
                                localConfigId = localConfigId,
                            ),
                        ),
                    )
                },
            mediaProviderRepository = mockk(relaxed = true),
            localStableDiffusionTargetResolver = resolver,
        )
    }

    private fun supportedCapabilities(): LocalImageGenerationCapabilities =
        LocalImageGenerationCapabilities(
            npuAvailable = LocalImageGenTriState.YES,
            sd15NpuSupported = LocalImageGenTriState.YES,
            sd15CpuSupported = LocalImageGenTriState.NO,
            sdxlNpuSupported = LocalImageGenTriState.NO,
            recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
            recommendedModelId = LocalImageGenerationModelId.SD15_QNN,
            diagnostics = emptyList(),
        )

    private fun sdxlSupportedCapabilities(): LocalImageGenerationCapabilities =
        LocalImageGenerationCapabilities(
            npuAvailable = LocalImageGenTriState.YES,
            sd15NpuSupported = LocalImageGenTriState.YES,
            sd15CpuSupported = LocalImageGenTriState.NO,
            sdxlNpuSupported = LocalImageGenTriState.YES,
            recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
            recommendedModelId = LocalImageGenerationModelId.SD15_QNN,
            diagnostics = emptyList(),
        )

    private fun sd15Asset(
        sha256: String = LocalImageGenerationModelRegistry.SD15_QNN_SHA256,
    ): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("sd15-model"),
                    displayName = "Stable Diffusion 1.5",
                    huggingFaceModelName = "gpustack/stable-diffusion-v1-5-GGUF",
                    remoteFileName = "stable-diffusion-v1-5-Q4_0.gguf",
                    localFileName = "stable-diffusion-v1-5-Q4_0.gguf",
                    sha256 = sha256,
                    sizeInBytes = 1_747_190_784L,
                    modelFileFormat = ModelFileFormat.GGUF,
                ),
            configurations =
                listOf(
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId("sd15-local-config"),
                        localModelId = LocalModelId("sd15-model"),
                        displayName = "Stable Diffusion 1.5",
                        maxTokens = 0,
                        contextWindow = 0,
                        temperature = 0.0,
                        topP = 1.0,
                        topK = 1,
                        repetitionPenalty = 1.0,
                        systemPrompt = "",
                        defaultAssignments = listOf(ModelType.IMAGE_GENERATION),
                    ),
                ),
        )
}
