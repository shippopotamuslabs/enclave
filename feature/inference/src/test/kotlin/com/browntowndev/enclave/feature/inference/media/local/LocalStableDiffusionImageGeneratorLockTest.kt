package com.browntowndev.enclave.feature.inference.media.local

import android.content.Context
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationError
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.inference.InferenceType
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalStableDiffusionImageGeneratorLockTest {
    @Test
    fun `busy shared on-device lock rejects image generation before native launch`() =
        runTest {
            val lockManager =
                mockk<InferenceLockManager> {
                    every { acquireLock(InferenceType.ON_DEVICE) } returns false
                }
            val engine = mockk<LocalImageGenerationEngine>()
            val context =
                mockk<Context>(relaxed = true) {
                    every { cacheDir } returns java.io.File.createTempFile("cache", "").parentFile!!
                }
            val generator =
                LocalStableDiffusionImageGenerator(
                    context = context,
                    engine = engine,
                    inferenceLockManager = lockManager,
                    studioRepository = mockk<StudioRepositoryPort>(relaxed = true),
                )

            val result =
                generator.generate(
                    prompt = "sunset",
                    target = localTarget(),
                    settings = ImageGenerationSettings(imageResolution = "512x512"),
                )

            assertTrue(result.exceptionOrNull() is LocalImageGenerationError.Busy)
            coVerify(exactly = 0) { engine.generate(any()) }
        }

    private fun localTarget(): ImageGenerationTarget.LocalStableDiffusion =
        ImageGenerationTarget.LocalStableDiffusion(
            localConfigId = LocalModelConfigurationId("sd15-local-config"),
            displayName = "Stable Diffusion 1.5",
            modelId = LocalImageGenerationModelId.SD15_QNN,
            modelDir = "/models",
            catalogSha256 = "c2f6e92f9d08d69cc673a1003528ac8199274b3c0eaec88d5fbefe5af67bd42b",
            selectedBackend = LocalImageGenerationBackendKind.QNN_NPU,
            capabilities =
                ProviderCapabilities(
                    supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
                    supportedImageQualities = listOf(GenerationQuality.SPEED),
                    supportedImageResolutions = listOf("512x512"),
                    supportedVideoQualities = emptyList(),
                    supportedVideoResolutions = emptyList(),
                    supportedVideoDurations = emptyList(),
                    supportsVideo = false,
                ),
            deviceCapabilities =
                LocalImageGenerationCapabilities(
                    npuAvailable = LocalImageGenTriState.YES,
                    sd15NpuSupported = LocalImageGenTriState.YES,
                    sd15CpuSupported = LocalImageGenTriState.NO,
                    sdxlNpuSupported = LocalImageGenTriState.NO,
                    recommendedBackend = LocalImageGenerationBackendKind.QNN_NPU,
                    recommendedModelId = LocalImageGenerationModelId.SD15_QNN,
                    diagnostics = emptyList(),
                ),
        )
}
