package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.StudioTemplate
import com.browntowndev.enclave.domain.port.media.ImageGenerationPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenerateImageUseCaseTest {
    private val imageGenerationPort = mockk<ImageGenerationPort>()
    private val resolveAssignedImageGenerationTargetUseCase = mockk<ResolveAssignedImageGenerationTargetUseCase>()
    private lateinit var useCase: GenerateImageUseCase

    @BeforeEach
    fun setup() {
        useCase =
            GenerateImageUseCase(
                resolveMediaModelTypeUseCase = ResolveMediaModelTypeUseCase(),
                resolveAssignedImageGenerationTargetUseCase = resolveAssignedImageGenerationTargetUseCase,
                imageGenerationPort = imageGenerationPort,
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
            val target =
                ImageGenerationTarget.Remote(
                    providerAssetId = com.browntowndev.enclave.domain.model.config.MediaProviderId("provider-1"),
                    providerDisplayName = "Provider 1",
                )

            coEvery { resolveAssignedImageGenerationTargetUseCase(ModelType.IMAGE_GENERATION) } returns Result.success(target)
            coEvery { imageGenerationPort.generateImage(any(), any(), any(), any()) } returns Result.success(listOf(ByteArray(0)))

            val finalPrompt = "${template.promptPrefix}$prompt${template.promptSuffix}"
            useCase(finalPrompt, settings)

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
            coEvery { resolveAssignedImageGenerationTargetUseCase(ModelType.IMAGE_GENERATION) } returns
                Result.failure(IllegalStateException("No image generation model assigned."))

            val result = useCase("prompt", ImageGenerationSettings())

            assertTrue(result.isFailure)
            assertEquals("No image generation model assigned.", result.exceptionOrNull()?.message)
        }

    @Test
    fun `invoke routes local target to port`() =
        runBlocking {
            val target = mockk<ImageGenerationTarget.LocalStableDiffusion>()
            val settings = ImageGenerationSettings()
            coEvery { resolveAssignedImageGenerationTargetUseCase(ModelType.IMAGE_GENERATION) } returns Result.success(target)
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
    fun `invoke with reference image resolves IMAGE_EDIT slot`() =
        runBlocking {
            val target =
                ImageGenerationTarget.Remote(
                    providerAssetId = com.browntowndev.enclave.domain.model.config.MediaProviderId("edit-provider"),
                    providerDisplayName = "Edit Provider",
                )
            val settings = ImageGenerationSettings(referenceImageUri = "file://reference.png")

            coEvery { resolveAssignedImageGenerationTargetUseCase(ModelType.IMAGE_EDIT) } returns Result.success(target)
            coEvery { imageGenerationPort.generateImage(any(), any(), any(), any()) } returns Result.success(listOf(ByteArray(0)))

            useCase("edit prompt", settings)

            coVerify {
                imageGenerationPort.generateImage(
                    prompt = "edit prompt",
                    target = target,
                    settings = settings,
                    onProgress = any(),
                )
            }
            coVerify(exactly = 0) { resolveAssignedImageGenerationTargetUseCase(ModelType.IMAGE_GENERATION) }
        }

    @Test
    fun `invoke with reference image routes local IMAGE_EDIT target to port`() =
        runBlocking {
            val target = mockk<ImageGenerationTarget.LocalStableDiffusion>()
            val settings = ImageGenerationSettings(referenceImageUri = "file://reference.png")

            coEvery { resolveAssignedImageGenerationTargetUseCase(ModelType.IMAGE_EDIT) } returns Result.success(target)
            coEvery { imageGenerationPort.generateImage(any(), any(), any(), any()) } returns Result.success(listOf(ByteArray(0)))

            useCase("edit prompt", settings)

            coVerify {
                imageGenerationPort.generateImage(
                    prompt = "edit prompt",
                    target = target,
                    settings = settings,
                    onProgress = any(),
                )
            }
        }

    @Test
    fun `invoke with reference image fails when no edit model assigned`() =
        runBlocking {
            coEvery { resolveAssignedImageGenerationTargetUseCase(ModelType.IMAGE_EDIT) } returns
                Result.failure(IllegalStateException("No image edit model assigned."))

            val result =
                useCase(
                    "edit prompt",
                    ImageGenerationSettings(referenceImageUri = "file://reference.png"),
                )

            assertTrue(result.isFailure)
            assertEquals("No image edit model assigned.", result.exceptionOrNull()?.message)
        }
}
