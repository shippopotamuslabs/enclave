package com.browntowndev.pocketcrew.domain.usecase.media

import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.media.VideoGenerationSettings
import com.browntowndev.pocketcrew.domain.port.media.VideoGenerationPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenerateVideoUseCaseTest {
    private val defaultModelRepository = mockk<DefaultModelRepositoryPort>()
    private val mediaProviderRepository = mockk<MediaProviderRepositoryPort>()
    private val videoGenerationPort = mockk<VideoGenerationPort>()
    private lateinit var useCase: GenerateVideoUseCase

    @BeforeEach
    fun setup() {
        useCase = GenerateVideoUseCase(
            defaultModelRepository = defaultModelRepository,
            mediaProviderRepository = mediaProviderRepository,
            videoGenerationPort = videoGenerationPort,
        )
    }

    @Test
    fun invoke_noVideoAssignment_returnsFailure() = runTest {
        coEvery { defaultModelRepository.getDefault(ModelType.VIDEO_GENERATION) } returns null

        val result = useCase("prompt", VideoGenerationSettings())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun invoke_missingAssignedProvider_returnsFailure() = runTest {
        val providerId = MediaProviderId("provider-1")
        coEvery { defaultModelRepository.getDefault(ModelType.VIDEO_GENERATION) } returns DefaultModelAssignment(
            modelType = ModelType.VIDEO_GENERATION,
            mediaProviderId = providerId,
        )
        coEvery { mediaProviderRepository.getMediaProvider(providerId) } returns null

        val result = useCase("prompt", VideoGenerationSettings())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun invoke_videoAssignment_delegatesToVideoPort() = runTest {
        val providerId = MediaProviderId("provider-1")
        val provider = MediaProviderAsset(
            id = providerId,
            displayName = "OpenAI Video",
            provider = ApiProvider.OPENAI,
            capability = MediaCapability.VIDEO,
            credentialAlias = "openai",
        )
        val settings = VideoGenerationSettings(videoDuration = 8)
        val bytes = "video".toByteArray()
        coEvery { defaultModelRepository.getDefault(ModelType.VIDEO_GENERATION) } returns DefaultModelAssignment(
            modelType = ModelType.VIDEO_GENERATION,
            mediaProviderId = providerId,
        )
        coEvery { mediaProviderRepository.getMediaProvider(providerId) } returns provider
        coEvery { videoGenerationPort.generateVideo("prompt", provider, settings) } returns Result.success(bytes)

        val result = useCase("prompt", settings)

        assertTrue(result.isSuccess)
        assertArrayEquals(bytes, result.getOrThrow())
        coVerify {
            videoGenerationPort.generateVideo(
                prompt = "prompt",
                provider = provider,
                settings = settings,
            )
        }
    }
}
