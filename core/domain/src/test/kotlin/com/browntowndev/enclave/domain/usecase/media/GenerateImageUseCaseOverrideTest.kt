package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.port.media.ImageGenerationPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.usecase.media.test.FakeDefaultModelRepositoryWithCalls
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GenerateImageUseCaseOverrideTest {
    private val defaultModelRepository = FakeDefaultModelRepositoryWithCalls()
    private val resolveMediaModelTypeUseCase = ResolveMediaModelTypeUseCase()
    private val mediaProviderRepository = mockk<MediaProviderRepositoryPort>(relaxed = true)
    private val localStableDiffusionTargetResolver = mockk<LocalStableDiffusionTargetResolver>(relaxed = true)
    private val imageGenerationPort = mockk<ImageGenerationPort>(relaxed = true)

    private lateinit var useCase: GenerateImageUseCase

    @BeforeEach
    fun setup() {
        // Wire the REAL resolver so that a lazy Green implementation that drops the override
        // falls through to defaultModelRepository.getDefault(modelType), populating
        // getDefaultCalls and tripping the MD01 assertion.
        val resolveAssignedImageGenerationTargetUseCase =
            ResolveAssignedImageGenerationTargetUseCase(
                defaultModelRepository = defaultModelRepository,
                mediaProviderRepository = mediaProviderRepository,
                localStableDiffusionTargetResolver = localStableDiffusionTargetResolver,
            )
        coEvery {
            imageGenerationPort.generateImage(any(), any(), any(), any())
        } returns Result.success(listOf(byteArrayOf(1)))
        useCase =
            GenerateImageUseCase(
                resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                resolveAssignedImageGenerationTargetUseCase = resolveAssignedImageGenerationTargetUseCase,
                imageGenerationPort = imageGenerationPort,
            )
    }

    @Nested
    @DisplayName("S05 — Leaf use cases resolve from override instead of default slot")
    inner class S05ImageOverride {
        @Test
        @DisplayName("S05a — Local override resolves via local target resolver; default slot untouched")
        fun s05a_localOverride() =
            runTest {
                val configId = LocalModelConfigurationId("sd-config")
                val override = StudioModelOverride.Local(configId, "My SD Pack")

                val result = useCase("prompt", ImageGenerationSettings(), override = override)

                assertTrue(result.isSuccess)
                assertFalse(defaultModelRepository.getDefaultCalls.contains(ModelType.IMAGE_GENERATION))
            }

        @Test
        @DisplayName("S05b — MediaProvider override produces Remote target; default slot untouched")
        fun s05b_mediaProviderOverride() =
            runTest {
                val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")

                val result = useCase("prompt", ImageGenerationSettings(), override = override)

                assertTrue(result.isSuccess)
                assertFalse(defaultModelRepository.getDefaultCalls.contains(ModelType.IMAGE_GENERATION))
            }

        @Test
        @DisplayName("S05c — Api override fails with specific IllegalArgumentException message")
        fun s05c_apiOverride() =
            runTest {
                val override = StudioModelOverride.Api(ApiModelConfigurationId("api-config"), "GPT-Image")

                val result = useCase("prompt", ImageGenerationSettings(), override = override)

                assertTrue(result.isFailure)
                val error = result.exceptionOrNull()
                assertEquals(IllegalArgumentException::class.java, error?.javaClass)
                assertEquals(
                    "API configuration overrides are not supported for image generation.",
                    error?.message,
                )
            }
    }

    @Nested
    @DisplayName("MD01 — Mutation defense: override short-circuits default-slot reads")
    inner class MD01Image {
        @Test
        @DisplayName("MD01-image — Override set: getDefault NOT called for IMAGE_GENERATION or IMAGE_EDIT")
        fun md01_image() =
            runTest {
                val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")

                useCase("prompt", ImageGenerationSettings(), override = override)

                assertFalse(defaultModelRepository.getDefaultCalls.contains(ModelType.IMAGE_GENERATION))
                assertFalse(defaultModelRepository.getDefaultCalls.contains(ModelType.IMAGE_EDIT))
            }
    }
}
