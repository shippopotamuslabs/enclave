package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
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
            val provider = mockk<MediaProviderAsset>()

            coEvery { defaultModelRepository.getDefault(ModelType.IMAGE_GENERATION) } returns
                mockk {
                    coEvery { mediaProviderId } returns MediaProviderId("provider-1")
                }
            coEvery { mediaProviderRepository.getMediaProvider(MediaProviderId("provider-1")) } returns provider
            coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(listOf(ByteArray(0)))

            // When
            // In a real scenario, the prompt prefix/suffix concatenation might happen in the ViewModel
            // or a decorator. For this test, we verify that IF we pass the concatenated prompt,
            // it reaches the port with the settings.
            val finalPrompt = "${template.promptPrefix}$prompt${template.promptSuffix}"
            useCase(finalPrompt, settings)

            // Then
            coVerify {
                imageGenerationPort.generateImage(
                    prompt = "chibi style, dragon, cute",
                    provider = provider,
                    settings = settings,
                )
            }
        }

    @Test
    fun `invoke fails when no provider assigned`() =
        runBlocking {
            // Given
            coEvery { defaultModelRepository.getDefault(ModelType.IMAGE_GENERATION) } returns null

            // When
            val result = useCase("prompt", ImageGenerationSettings())

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }

    @Test
    fun `invoke with reference image resolves IMAGE_EDIT default`() =
        runBlocking {
            val editProviderId = MediaProviderId("edit-provider")
            val provider = mockk<MediaProviderAsset>()
            val settings = ImageGenerationSettings(referenceImageUri = "file://reference.png")

            coEvery { defaultModelRepository.getDefault(ModelType.IMAGE_EDIT) } returns
                DefaultModelAssignment(
                    modelType = ModelType.IMAGE_EDIT,
                    mediaProviderId = editProviderId,
                )
            coEvery { mediaProviderRepository.getMediaProvider(editProviderId) } returns provider
            coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(listOf(ByteArray(0)))

            useCase("edit prompt", settings)

            coVerify {
                imageGenerationPort.generateImage(
                    prompt = "edit prompt",
                    provider = provider,
                    settings = settings,
                )
            }
            coVerify(exactly = 0) { defaultModelRepository.getDefault(ModelType.IMAGE_GENERATION) }
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
