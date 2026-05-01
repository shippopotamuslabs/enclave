package com.browntowndev.pocketcrew.domain.usecase.media

import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.media.MusicGenerationSettings
import com.browntowndev.pocketcrew.domain.port.media.MusicGenerationPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenerateMusicUseCaseTest {
    private val defaultModelRepository = mockk<DefaultModelRepositoryPort>()
    private val mediaProviderRepository = mockk<MediaProviderRepositoryPort>()
    private val musicGenerationPort = mockk<MusicGenerationPort>()
    private lateinit var useCase: GenerateMusicUseCase

    @BeforeEach
    fun setup() {
        useCase = GenerateMusicUseCase(
            defaultModelRepository,
            mediaProviderRepository,
            musicGenerationPort
        )
    }

    @Test
    fun `invoke resolves MUSIC_GENERATION model type and delegates to port`() = runBlocking {
        // Given
        val settings = MusicGenerationSettings()
        val provider = mockk<MediaProviderAsset>()

        coEvery { defaultModelRepository.getDefault(ModelType.MUSIC_GENERATION) } returns mockk {
            coEvery { mediaProviderId } returns MediaProviderId("music-provider-1")
        }
        coEvery { mediaProviderRepository.getMediaProvider(MediaProviderId("music-provider-1")) } returns provider
        coEvery { musicGenerationPort.generateMusic(any(), any(), any()) } returns Result.success(ByteArray(0))

        // When
        val result = useCase("ambient soundscape", settings)

        // Then
        assertTrue(result.isSuccess)
        coVerify {
            musicGenerationPort.generateMusic(
                prompt = "ambient soundscape",
                provider = provider,
                settings = settings
            )
        }
    }

    @Test
    fun `invoke fails when no default music model assigned`() = runBlocking {
        // Given
        coEvery { defaultModelRepository.getDefault(ModelType.MUSIC_GENERATION) } returns null

        // When
        val result = useCase("prompt", MusicGenerationSettings())

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `invoke fails when no media provider assigned`() = runBlocking {
        // Given
        coEvery { defaultModelRepository.getDefault(ModelType.MUSIC_GENERATION) } returns mockk {
            coEvery { mediaProviderId } returns null
        }

        // When
        val result = useCase("prompt", MusicGenerationSettings())

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `invoke fails when provider not found`() = runBlocking {
        // Given
        coEvery { defaultModelRepository.getDefault(ModelType.MUSIC_GENERATION) } returns mockk {
            coEvery { mediaProviderId } returns MediaProviderId("missing-provider")
        }
        coEvery { mediaProviderRepository.getMediaProvider(MediaProviderId("missing-provider")) } returns null

        // When
        val result = useCase("prompt", MusicGenerationSettings())

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}