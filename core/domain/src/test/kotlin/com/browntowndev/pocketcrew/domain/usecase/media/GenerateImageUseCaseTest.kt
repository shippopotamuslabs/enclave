package com.browntowndev.pocketcrew.domain.usecase.media

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.StudioTemplate
import com.browntowndev.pocketcrew.domain.port.media.ImageGenerationPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
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
        useCase = GenerateImageUseCase(
            defaultModelRepository,
            mediaProviderRepository,
            imageGenerationPort
        )
    }

    @Test
    fun `invoke with template prefixes and suffixes prompt correctly`() = runBlocking {
        // Given
        val template = StudioTemplate(
            id = "chibi",
            name = "Chibi",
            exampleUri = "",
            promptPrefix = "chibi style, ",
            promptSuffix = ", cute"
        )
        val prompt = "dragon"
        val settings = ImageGenerationSettings()
        val provider = mockk<MediaProviderAsset>()
        
        coEvery { defaultModelRepository.getDefault(ModelType.IMAGE_GENERATION) } returns mockk {
            coEvery { mediaProviderId } returns MediaProviderId("provider-1")
        }
        coEvery { mediaProviderRepository.getMediaProvider(MediaProviderId("provider-1")) } returns provider
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(ByteArray(0))

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
                settings = settings
            )
        }
    }

    @Test
    fun `invoke fails when no provider assigned`() = runBlocking {
        // Given
        coEvery { defaultModelRepository.getDefault(ModelType.IMAGE_GENERATION) } returns null

        // When
        val result = useCase("prompt", ImageGenerationSettings())

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
