package com.browntowndev.enclave.feature.inference.media.local

import android.app.ActivityManager
import android.content.Context
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
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
    private val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)

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
            val generator = createGenerator(context, engine)

            val result =
                generator.generate(
                    prompt = "bonsai tree",
                    target = localTarget(),
                    settings = ImageGenerationSettings(imageResolution = "512x512"),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is LocalImageGenerationError.RuntimeNotPackaged)
        }

    private fun createGenerator(
        context: Context,
        engine: LocalImageGenerationEngine,
    ): LocalStableDiffusionImageGenerator {
        val activityManager = mockk<ActivityManager> { every { memoryClass } returns 512 }
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        return LocalStableDiffusionImageGenerator(context, engine, lockManager, studioRepository)
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
    fun `INF-001 SD15 native request uses policy-normalized 512 square 20 and 7_5`() =
        runTest {
            val capturedRequest = io.mockk.slot<LocalImageGenerationNativeRequest>()
            val engine =
                mockk<LocalImageGenerationEngine> {
                    coEvery { generate(capture(capturedRequest)) } returns
                        Result.success(
                            LocalImageGenerationNativeResult(emptyList(), 512, 512, 0L),
                        )
                }
            val context = mockk<Context>(relaxed = true)
            every { context.cacheDir } returns java.io.File.createTempFile("cache", "").parentFile!!
            val generator = createGenerator(context, engine)

            val result =
                generator.generate(
                    prompt = "sunset",
                    target = localTarget(),
                    settings =
                        ImageGenerationSettings(
                            imageResolution = "512x512",
                            aspectRatio = AspectRatio.SIXTEEN_NINE,
                            diffusionSteps = 24,
                        ),
                )

            assertTrue(result.isSuccess)
            // Legacy hardcoded path derives 512x288 from 16:9; policy must force SD15 square 512x512.
            assertEquals(512, capturedRequest.captured.width)
            assertEquals(512, capturedRequest.captured.height)
            assertEquals(20, capturedRequest.captured.steps)
            assertEquals(7.5f, capturedRequest.captured.cfgScale)
        }

    @Test
    fun `INF-002 SDXL native request uses 1024 selected steps CFG and lcm`() =
        runTest {
            val capturedRequest = io.mockk.slot<LocalImageGenerationNativeRequest>()
            val engine =
                mockk<LocalImageGenerationEngine> {
                    coEvery { generate(capture(capturedRequest)) } returns
                        Result.success(
                            LocalImageGenerationNativeResult(emptyList(), 1024, 1024, 0L),
                        )
                }
            val context = mockk<Context>(relaxed = true)
            every { context.cacheDir } returns java.io.File.createTempFile("cache", "").parentFile!!
            val generator = createGenerator(context, engine)

            val sdxlTarget =
                localTarget().copy(
                    modelId = LocalImageGenerationModelId.SDXL_QNN,
                    capabilities =
                        ProviderCapabilities(
                            supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
                            supportedImageQualities = listOf(GenerationQuality.SPEED),
                            supportedImageResolutions = listOf("1024x1024"),
                            supportedImageSteps = listOf(8, 12, 16, 20, 24, 30),
                            defaultImageStep = 16,
                            supportedVideoQualities = emptyList(),
                            supportedVideoResolutions = emptyList(),
                            supportedVideoDurations = emptyList(),
                            supportsVideo = false,
                        ),
                )

            val result =
                generator.generate(
                    prompt = "sunset",
                    target = sdxlTarget,
                    settings = ImageGenerationSettings(imageResolution = "1024x1024", diffusionSteps = 8),
                )

            assertTrue(result.isSuccess)
            assertEquals(1024, capturedRequest.captured.width)
            assertEquals(1024, capturedRequest.captured.height)
            assertEquals(8, capturedRequest.captured.steps)
            assertEquals(1.5f, capturedRequest.captured.cfgScale)
            assertEquals("lcm", capturedRequest.captured.scheduler)
        }

    @Test
    fun `INF-003 stale SD15 1024 request fails before native launch with exact error`() =
        runTest {
            val engine = mockk<LocalImageGenerationEngine>()
            val context = mockk<Context>(relaxed = true)
            every { context.cacheDir } returns java.io.File.createTempFile("cache", "").parentFile!!
            val generator = createGenerator(context, engine)

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
            val generator = createGenerator(context, engine)

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

    @Test
    fun `MUT-003 lazy any-step implementation fails because step 13 must clamp to 16`() =
        runTest {
            val capturedRequest = io.mockk.slot<LocalImageGenerationNativeRequest>()
            val engine = mockk<LocalImageGenerationEngine> {
                coEvery { generate(capture(capturedRequest)) } returns Result.success(
                    LocalImageGenerationNativeResult(emptyList(), 1024, 1024, 0L)
                )
            }
            val context = mockk<Context>(relaxed = true)
            every { context.cacheDir } returns java.io.File.createTempFile("cache", "").parentFile!!
            val generator = createGenerator(context, engine)

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
            // A lazy forwarder would emit 13 unchanged; the unsupported step must clamp to 16.
            assertEquals(16, capturedRequest.captured.steps)
        }
}