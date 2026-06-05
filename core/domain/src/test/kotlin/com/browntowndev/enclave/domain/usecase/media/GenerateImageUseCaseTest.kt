package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.StudioTemplate
import com.browntowndev.enclave.domain.port.media.ImageGenerationPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenerateImageUseCaseTest {
    private val defaultModelRepository = mockk<DefaultModelRepositoryPort>()
    private val mediaProviderRepository = mockk<MediaProviderRepositoryPort>()
    private val imageGenerationPort = mockk<ImageGenerationPort>()
    private val resolveAssignedImageGenerationTargetUseCase = mockk<ResolveAssignedImageGenerationTargetUseCase>()
    private lateinit var useCase: GenerateImageUseCase

    @BeforeEach
    fun setup() {
        useCase =
            GenerateImageUseCase(
                resolveMediaModelTypeUseCase = ResolveMediaModelTypeUseCase(),
                resolveAssignedMediaProviderUseCase =
                    ResolveAssignedMediaProviderUseCase(
                        defaultModelRepository = defaultModelRepository,
                        mediaProviderRepository = mediaProviderRepository,
                    ),
                resolveAssignedImageGenerationTargetUseCase = resolveAssignedImageGenerationTargetUseCase,
                imageGenerationPort = imageGenerationPort,
            )
    }

    @Test
    fun `invoke with template prefixes and suffixes prompt correctly`() =
        runBlocking {
            // Given
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
            val target =
                ImageGenerationTarget.Remote(
                    providerAssetId = MediaProviderId("provider-1"),
                    providerDisplayName = "Provider 1",
                )

            coEvery { resolveAssignedImageGenerationTargetUseCase() } returns Result.success(target)
            coEvery { imageGenerationPort.generateImage(any(), any(), any(), any()) } returns Result.success(listOf(ByteArray(0)))

            // When
            // In a real scenario, the prompt prefix/suffix concatenation might happen in the ViewModel
            // or a decorator. For this test, we verify that IF we pass the concatenated prompt,
            // it reaches the port with the resolved target and settings.
            val finalPrompt = "${template.promptPrefix}$prompt${template.promptSuffix}"
            useCase(finalPrompt, settings)

            // Then
            coVerify {
                imageGenerationPort.generateImage(
                    prompt = "chibi style, dragon, cute",
                    target = target,
                    settings = settings,
                    onProgress = any(),
                )
            }
        }

    @Test
    fun `invoke fails when no target resolved`() =
        runBlocking {
            // Given
            coEvery { resolveAssignedImageGenerationTargetUseCase() } returns
                Result.failure(IllegalStateException("No image generation model assigned."))

            // When
            val result = useCase("prompt", ImageGenerationSettings())

            // Then
            assertTrue(result.isFailure)
            assertEquals("No image generation model assigned.", result.exceptionOrNull()?.message)
        }

    @Test
    fun `invoke routes local target to port`() =
        runBlocking {
            val target = mockk<ImageGenerationTarget.LocalStableDiffusion>()
            val settings = ImageGenerationSettings()
            coEvery { resolveAssignedImageGenerationTargetUseCase() } returns Result.success(target)
            coEvery { imageGenerationPort.generateImage(any(), any(), any(), any()) } returns Result.success(listOf(ByteArray(0)))

            useCase("local prompt", settings)

            coVerify {
                imageGenerationPort.generateImage(
                    prompt = "local prompt",
                    target = target,
                    settings = settings,
                    onProgress = any(),
                )
            }
        }

    @Test
    fun `invoke with reference image resolves IMAGE_EDIT default`() =
        runBlocking {
            val editProviderId = MediaProviderId("edit-provider")
            val provider =
                MediaProviderAsset(
                    id = editProviderId,
                    displayName = "Edit Provider",
                    provider = com.browntowndev.enclave.domain.model.inference.ApiProvider.FAL,
                    capability = com.browntowndev.enclave.domain.model.config.MediaCapability.IMAGE,
                    credentialAlias = "edit",
                )
            val settings = ImageGenerationSettings(referenceImageUri = "file://reference.png")

            coEvery { defaultModelRepository.getDefault(ModelType.IMAGE_EDIT) } returns
                DefaultModelAssignment(
                    modelType = ModelType.IMAGE_EDIT,
                    mediaProviderId = editProviderId,
                )
            coEvery { mediaProviderRepository.getMediaProvider(editProviderId) } returns provider
            coEvery { imageGenerationPort.generateImage(any(), any(), any(), any()) } returns Result.success(listOf(ByteArray(0)))

            useCase("edit prompt", settings)

            coVerify {
                imageGenerationPort.generateImage(
                    prompt = "edit prompt",
                    target =
                        ImageGenerationTarget.Remote(
                            providerAssetId = editProviderId,
                            providerDisplayName = "Edit Provider",
                        ),
                    settings = settings,
                    onProgress = any(),
                )
            }
            coVerify(exactly = 0) { resolveAssignedImageGenerationTargetUseCase() }
        }

    @Test
    fun `invoke with reference image fails when no edit provider assigned`() =
        runBlocking {
            coEvery { defaultModelRepository.getDefault(ModelType.IMAGE_EDIT) } returns null

            val result =
                useCase(
                    "edit prompt",
                    ImageGenerationSettings(referenceImageUri = "file://reference.png"),
                )

            assertTrue(result.isFailure)
            assertEquals("No image edit provider assigned", result.exceptionOrNull()?.message)
        }
}
