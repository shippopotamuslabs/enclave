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
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.inference.InferenceType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalStableDiffusionImageGeneratorFailureTest {
    private val lockManager =
        mockk<InferenceLockManager> {
            every { acquireLock(InferenceType.ON_DEVICE) } returns true
            every { releaseLock(InferenceType.ON_DEVICE) } just runs
        }

    @Test
    fun `typed generation error surfaces backend detail`() =
        runTest {
            val engine =
                mockk<LocalImageGenerationEngine> {
                    coEvery { generate(any()) } returns
                        Result.failure(
                            LocalImageGenerationError.RuntimeNotPackaged(
                                LocalImageGenerationBackendKind.QNN_NPU,
                                "QNN runtime not packaged",
                            ),
                        )
                }
            val context = mockk<Context>(relaxed = true)
            every { context.cacheDir } returns java.io.File.createTempFile("cache", "").parentFile!!
            val generator = LocalStableDiffusionImageGenerator(context, engine, lockManager)

            val result =
                generator.generate(
                    prompt = "bonsai tree",
                    target = localTarget(),
                    settings = ImageGenerationSettings(imageResolution = "512x512"),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is LocalImageGenerationError.RuntimeNotPackaged)
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

    @Test
    fun `INF-003 stale SD15 1024 request fails before native launch with exact error`() =
        runTest {
            val engine = mockk<LocalImageGenerationEngine>()
            val context = mockk<Context>(relaxed = true)
            every { context.cacheDir } returns java.io.File.createTempFile("cache", "").parentFile!!
            val generator = LocalStableDiffusionImageGenerator(context, engine, lockManager)

            val result = generator.generate(
                prompt = "sunset",
                target = localTarget(),
                settings = ImageGenerationSettings(imageResolution = "1024x1024")
            )

            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertTrue(exception is IllegalArgumentException)
            assertEquals(
                "Unsupported local image generation resolution '1024x1024' for model SD15_QNN. Supported resolutions: [512x512].",
                exception?.message
            )
        }

    @Test
    fun `INF-004 stale SDXL step 13 clamps to 16 before native launch`() =
        runTest {
            val capturedRequest = io.mockk.slot<LocalImageGenerationNativeRequest>()
            val engine = mockk<LocalImageGenerationEngine> {
                coEvery { generate(capture(capturedRequest)) } returns Result.success(
                    LocalImageGenerationNativeResult(emptyList(), 1024, 1024, 0L)
                )
            }
            val context = mockk<Context>(relaxed = true)
            every { context.cacheDir } returns java.io.File.createTempFile("cache", "").parentFile!!
            val generator = LocalStableDiffusionImageGenerator(context, engine, lockManager)

            val sdxlTarget = localTarget().copy(
                modelId = LocalImageGenerationModelId.SDXL_QNN,
                capabilities = ProviderCapabilities(
                    supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
                    supportedImageQualities = listOf(GenerationQuality.SPEED),
                    supportedImageResolutions = listOf("1024x1024"),
                    supportedImageSteps = listOf(8, 12, 16, 20, 24, 30),
                    defaultImageStep = 16,
                    supportedVideoQualities = emptyList(),
                    supportedVideoResolutions = emptyList(),
                    supportedVideoDurations = emptyList(),
                    supportsVideo = false,
                )
            )

            val result = generator.generate(
                prompt = "sunset",
                target = sdxlTarget,
                settings = ImageGenerationSettings(imageResolution = "1024x1024", diffusionSteps = 13)
            )

            assertTrue(result.isSuccess)
            assertEquals(16, capturedRequest.captured.steps)
        }
}