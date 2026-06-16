package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.MediaGenerationOrigin
import com.browntowndev.enclave.domain.model.media.MediaGenerationRequest
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.util.Clock
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GenerateMediaUseCaseOverrideTest {
    private val generateImageUseCase = mockk<GenerateImageUseCase>()
    private val generateVideoUseCase = mockk<GenerateVideoUseCase>()
    private val generateMusicUseCase = mockk<GenerateMusicUseCase>()
    private val resolveMediaModelTypeUseCase = mockk<ResolveMediaModelTypeUseCase>()
    private val resolveAssignedImageGenerationTargetUseCase = mockk<ResolveAssignedImageGenerationTargetUseCase>()
    private val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
    private val clock = FakeClock()
    private val overrideSlot = slot<StudioModelOverride?>()

    private lateinit var useCase: GenerateMediaUseCase

    @BeforeEach
    fun setUp() {
        clock.now = 0L
        every { resolveMediaModelTypeUseCase(any(), any()) } returns ModelType.IMAGE_GENERATION
        coEvery { resolveAssignedImageGenerationTargetUseCase(any()) } returns
            Result.success(
                ImageGenerationTarget.Remote(
                    providerAssetId = MediaProviderId("remote"),
                    providerDisplayName = "Remote",
                ),
            )
        coEvery { studioRepository.cacheEphemeralMedia(any(), any()) } returns "file:///cache/a"
        useCase =
            GenerateMediaUseCase(
                generateImageUseCase = generateImageUseCase,
                generateVideoUseCase = generateVideoUseCase,
                generateMusicUseCase = generateMusicUseCase,
                resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                resolveAssignedImageGenerationTargetUseCase = resolveAssignedImageGenerationTargetUseCase,
                studioRepository = studioRepository,
                clock = clock,
            )
    }

    @Test
    @DisplayName("S04-5 — IMAGE request with override forwards override to GenerateImageUseCase")
    fun s04_imageRequestForwardsOverride() =
        runTest {
            val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")
            val settings = ImageGenerationSettings(generationCount = 1)
            coEvery {
                generateImageUseCase(
                    prompt = any(),
                    settings = any(),
                    onProgress = any(),
                    override = captureNullable(overrideSlot),
                )
            } returns Result.success(listOf(byteArrayOf(1)))

            useCase(
                MediaGenerationRequest(
                    prompt = "prompt",
                    capability = MediaCapability.IMAGE,
                    settings = settings,
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                    override = override,
                ),
            )

            assertEquals(override, overrideSlot.captured)
        }

    private class FakeClock : Clock {
        var now: Long = 0L

        override fun currentTimeMillis(): Long = now
    }
}