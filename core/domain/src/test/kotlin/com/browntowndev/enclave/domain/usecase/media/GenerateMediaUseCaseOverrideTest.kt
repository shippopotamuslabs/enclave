package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeGpuCapability
import com.browntowndev.enclave.core.testing.FakeImageGenerationPort
import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMusicGenerationPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeUtilityModelFile
import com.browntowndev.enclave.core.testing.FakeVideoGenerationPort
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.MediaGenerationOrigin
import com.browntowndev.enclave.domain.model.media.MediaGenerationRequest
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.util.Clock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GenerateMediaUseCaseOverrideTest {
    private val defaultModelRepository = FakeDefaultModelRepository()
    private val mediaProviderRepository = FakeMediaProviderRepository()
    private val imageGenerationPort = FakeImageGenerationPort()
    private val studioRepository = FakeStudioRepository()
    private val clock = FakeClock()

    private lateinit var useCase: GenerateMediaUseCase

    @BeforeEach
    fun setUp() {
        clock.now = 0L
        studioRepository.cacheEphemeralUri = "file:///cache/a"
        val resolveMediaModelTypeUseCase = ResolveMediaModelTypeUseCase()
        val resolveAssignedImageGenerationTargetUseCase =
            ResolveAssignedImageGenerationTargetUseCase(
                defaultModelRepository = defaultModelRepository,
                mediaProviderRepository = mediaProviderRepository,
                localStableDiffusionTargetResolver =
                    LocalStableDiffusionTargetResolver(
                        localModelRepository = FakeLocalModelRepository(),
                        utilityModelFilePort = FakeUtilityModelFile(),
                        gpuCapabilityPort = FakeGpuCapability(),
                        capabilityPort = FakeLocalImageGenerationCapability(),
                        selectBackend = SelectLocalImageGenerationBackendUseCase(),
                    ),
            )
        useCase =
            GenerateMediaUseCase(
                generateImageUseCase =
                    GenerateImageUseCase(
                        resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                        resolveAssignedImageGenerationTargetUseCase = resolveAssignedImageGenerationTargetUseCase,
                        imageGenerationPort = imageGenerationPort,
                    ),
                generateVideoUseCase =
                    GenerateVideoUseCase(
                        resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                        resolveAssignedMediaProviderUseCase =
                            ResolveAssignedMediaProviderUseCase(
                                defaultModelRepository = defaultModelRepository,
                                mediaProviderRepository = mediaProviderRepository,
                            ),
                        videoGenerationPort = FakeVideoGenerationPort(),
                    ),
                generateMusicUseCase =
                    GenerateMusicUseCase(
                        defaultModelRepository = defaultModelRepository,
                        mediaProviderRepository = mediaProviderRepository,
                        musicGenerationPort = FakeMusicGenerationPort(),
                    ),
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
            imageGenerationPort.result = Result.success(listOf(byteArrayOf(1)))

            useCase(
                MediaGenerationRequest(
                    prompt = "prompt",
                    capability = MediaCapability.IMAGE,
                    settings = settings,
                    origin = MediaGenerationOrigin.CHAT_TOOL,
                    override = override,
                ),
            )

            // The override surfaces at the port boundary as the Remote target built from it:
            // GenerateImageUseCase received the override and resolved through it.
            assertEquals(
                listOf(
                    ImageGenerationTarget.Remote(
                        providerAssetId = MediaProviderId("fal-1"),
                        providerDisplayName = "Fal Aura",
                    ),
                ),
                imageGenerationPort.targets,
            )
        }

    private class FakeClock : Clock {
        var now: Long = 0L

        override fun currentTimeMillis(): Long = now
    }
}
