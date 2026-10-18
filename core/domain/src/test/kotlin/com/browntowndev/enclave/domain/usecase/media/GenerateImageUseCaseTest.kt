package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeGpuCapability
import com.browntowndev.enclave.core.testing.FakeImageGenerationPort
import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeUtilityModelFile
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelPolicy
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry
import com.browntowndev.enclave.domain.model.media.StudioTemplate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenerateImageUseCaseTest {
    private val imageGenerationPort = FakeImageGenerationPort()
    private val defaultModelRepository = FakeDefaultModelRepository()
    private val mediaProviderRepository = FakeMediaProviderRepository()
    private val localModelRepository = FakeLocalModelRepository()
    private val utilityModelFile = FakeUtilityModelFile()
    private val gpuCapability = FakeGpuCapability()
    private val capabilityPort = FakeLocalImageGenerationCapability()
    private lateinit var useCase: GenerateImageUseCase

    @BeforeEach
    fun setup() {
        val resolveAssignedImageGenerationTargetUseCase =
            ResolveAssignedImageGenerationTargetUseCase(
                defaultModelRepository = defaultModelRepository,
                mediaProviderRepository = mediaProviderRepository,
                localStableDiffusionTargetResolver =
                    LocalStableDiffusionTargetResolver(
                        localModelRepository = localModelRepository,
                        utilityModelFilePort = utilityModelFile,
                        gpuCapabilityPort = gpuCapability,
                        capabilityPort = capabilityPort,
                        selectBackend = SelectLocalImageGenerationBackendUseCase(),
                    ),
            )
        useCase =
            GenerateImageUseCase(
                resolveMediaModelTypeUseCase = ResolveMediaModelTypeUseCase(),
                resolveAssignedImageGenerationTargetUseCase = resolveAssignedImageGenerationTargetUseCase,
                imageGenerationPort = imageGenerationPort,
            )
    }

    private fun seedRemoteAssignment(
        modelType: ModelType,
        providerId: String,
        displayName: String,
    ): ImageGenerationTarget.Remote {
        defaultModelRepository.seed(
            DefaultModelAssignment(
                modelType = modelType,
                mediaProviderId = MediaProviderId(providerId),
            ),
        )
        mediaProviderRepository.emitProviders(
            listOf(
                MediaProviderAsset(
                    id = MediaProviderId(providerId),
                    displayName = displayName,
                    provider = ApiProvider.FAL,
                    capability = MediaCapability.IMAGE,
                    credentialAlias = "fal",
                ),
            ),
        )
        return ImageGenerationTarget.Remote(
            providerAssetId = MediaProviderId(providerId),
            providerDisplayName = displayName,
        )
    }

    private fun seedLocalAssignment(modelType: ModelType): ImageGenerationTarget.LocalStableDiffusion {
        val configId = LocalModelConfigurationId("sd15-local-config")
        defaultModelRepository.seed(
            DefaultModelAssignment(
                modelType = modelType,
                localConfigId = configId,
            ),
        )
        localModelRepository.seedAssetForConfig(configId, sd15Asset())
        utilityModelFile.localModelPath = "/abs/models/stable-diffusion-v1-5-Q4_0.gguf"
        gpuCapability.localImageGenerationSupported = true
        capabilityPort.capabilities = supportedCapabilities()
        return ImageGenerationTarget.LocalStableDiffusion(
            localConfigId = configId,
            displayName = "Stable Diffusion 1.5",
            modelId = LocalImageGenerationModelId.SD15_QNN,
            modelDir = "/abs/models/sd15-qnn",
            catalogSha256 = LocalImageGenerationModelRegistry.SD15_QNN_SHA256,
            selectedBackend = LocalImageGenerationBackendKind.QNN_NPU,
            capabilities = LocalImageGenerationModelPolicy.providerCapabilitiesFor(LocalImageGenerationModelId.SD15_QNN),
            deviceCapabilities = supportedCapabilities(),
        )
    }

    @Test
    fun `invoke with template prefixes and suffixes prompt correctly`() =
        runBlocking {
            val template =
                StudioTemplate(
                    id = "chibi",
                    name = "Chibi",
                    exampleUri = "",
                    promptPrefix = "chibi style, ",
                    promptSuffix = ", cute",
                )
            val prompt = "dragon"
            val settings = ImageGenerationSettings()
            val target = seedRemoteAssignment(ModelType.IMAGE_GENERATION, "provider-1", "Provider 1")
            imageGenerationPort.result = Result.success(listOf(ByteArray(0)))

            val finalPrompt = "${template.promptPrefix}$prompt${template.promptSuffix}"
            useCase(finalPrompt, settings)

            assertEquals(listOf("chibi style, dragon, cute"), imageGenerationPort.calls)
            assertEquals(listOf(target), imageGenerationPort.targets)
            assertEquals(listOf(settings), imageGenerationPort.settingsCalls)
        }

    @Test
    fun `invoke fails when no target resolved`() =
        runBlocking {
            val result = useCase("prompt", ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertEquals("No image generation model assigned.", result.exceptionOrNull()?.message)
        }

    @Test
    fun `invoke routes local target to port`() =
        runBlocking {
            val target = seedLocalAssignment(ModelType.IMAGE_GENERATION)
            val settings = ImageGenerationSettings()
            imageGenerationPort.result = Result.success(listOf(ByteArray(0)))

            useCase("local prompt", settings)

            assertEquals(listOf("local prompt"), imageGenerationPort.calls)
            assertEquals(listOf(target), imageGenerationPort.targets)
            assertEquals(listOf(settings), imageGenerationPort.settingsCalls)
        }

    @Test
    fun `invoke with reference image resolves IMAGE_EDIT slot`() =
        runBlocking {
            val target = seedRemoteAssignment(ModelType.IMAGE_EDIT, "edit-provider", "Edit Provider")
            val settings = ImageGenerationSettings(referenceImageUri = "file://reference.png")
            imageGenerationPort.result = Result.success(listOf(ByteArray(0)))

            useCase("edit prompt", settings)

            assertEquals(listOf("edit prompt"), imageGenerationPort.calls)
            assertEquals(listOf(target), imageGenerationPort.targets)
            assertEquals(listOf(settings), imageGenerationPort.settingsCalls)
            assertFalse(defaultModelRepository.getDefaultCalls.contains(ModelType.IMAGE_GENERATION))
        }

    @Test
    fun `invoke with reference image routes local IMAGE_EDIT target to port`() =
        runBlocking {
            val target = seedLocalAssignment(ModelType.IMAGE_EDIT)
            val settings = ImageGenerationSettings(referenceImageUri = "file://reference.png")
            imageGenerationPort.result = Result.success(listOf(ByteArray(0)))

            useCase("edit prompt", settings)

            assertEquals(listOf("edit prompt"), imageGenerationPort.calls)
            assertEquals(listOf(target), imageGenerationPort.targets)
            assertEquals(listOf(settings), imageGenerationPort.settingsCalls)
        }

    @Test
    fun `invoke with reference image fails when no edit model assigned`() =
        runBlocking {
            val result =
                useCase(
                    "edit prompt",
                    ImageGenerationSettings(referenceImageUri = "file://reference.png"),
                )

            assertTrue(result.isFailure)
            assertEquals("No image edit model assigned.", result.exceptionOrNull()?.message)
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

    private fun sd15Asset(): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("sd15-model"),
                    displayName = "Stable Diffusion 1.5",
                    huggingFaceModelName = "gpustack/stable-diffusion-v1-5-GGUF",
                    remoteFileName = "stable-diffusion-v1-5-Q4_0.gguf",
                    localFileName = "stable-diffusion-v1-5-Q4_0.gguf",
                    sha256 = LocalImageGenerationModelRegistry.SD15_QNN_SHA256,
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
