package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMusicGenerationPort
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenerateMusicUseCaseTest {
    private val defaultModelRepository = FakeDefaultModelRepository()
    private val mediaProviderRepository = FakeMediaProviderRepository()
    private val musicGenerationPort = FakeMusicGenerationPort()
    private lateinit var useCase: GenerateMusicUseCase

    @BeforeEach
    fun setup() {
        useCase =
            GenerateMusicUseCase(
                defaultModelRepository,
                mediaProviderRepository,
                musicGenerationPort,
            )
    }

    @Test
    fun `invoke resolves MUSIC_GENERATION model type and delegates to port`() =
        runBlocking {
            // Given
            val settings = MusicGenerationSettings()
            val provider =
                MediaProviderAsset(
                    id = MediaProviderId("music-provider-1"),
                    displayName = "Music Provider",
                    provider = ApiProvider.FAL,
                    capability = MediaCapability.MUSIC,
                    credentialAlias = "fal",
                )

            defaultModelRepository.seed(
                DefaultModelAssignment(
                    modelType = ModelType.MUSIC_GENERATION,
                    mediaProviderId = MediaProviderId("music-provider-1"),
                ),
            )
            mediaProviderRepository.emitProviders(listOf(provider))
            musicGenerationPort.result = Result.success(ByteArray(0))

            // When
            val result = useCase("ambient soundscape", settings)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(listOf("ambient soundscape"), musicGenerationPort.calls)
            assertEquals(listOf(provider), musicGenerationPort.providerCalls)
            assertEquals(listOf(settings), musicGenerationPort.settingsCalls)
        }

    @Test
    fun `invoke fails when no default music model assigned`() =
        runBlocking {
            // When
            val result = useCase("prompt", MusicGenerationSettings())

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }

    @Test
    fun `invoke fails when no media provider assigned`() =
        runBlocking {
            // Given
            defaultModelRepository.seed(
                DefaultModelAssignment(
                    modelType = ModelType.MUSIC_GENERATION,
                    mediaProviderId = null,
                ),
            )

            // When
            val result = useCase("prompt", MusicGenerationSettings())

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }

    @Test
    fun `invoke fails when provider not found`() =
        runBlocking {
            // Given
            defaultModelRepository.seed(
                DefaultModelAssignment(
                    modelType = ModelType.MUSIC_GENERATION,
                    mediaProviderId = MediaProviderId("missing-provider"),
                ),
            )

            // When
            val result = useCase("prompt", MusicGenerationSettings())

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }
}
