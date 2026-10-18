package com.browntowndev.enclave.feature.inference.media.local

import android.app.ActivityManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeStudioRepository
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
import com.browntowndev.enclave.feature.inference.FakeInferenceLockManager
import com.browntowndev.enclave.feature.inference.FakeLocalImageGenerationEngine
import com.browntowndev.enclave.feature.inference.ShadowFakeNativeLocalImageGenRuntime
import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34], shadows = [ShadowFakeNativeLocalImageGenRuntime::class])
class LocalStableDiffusionImageGeneratorFailureTest {
    private val studioRepository = FakeStudioRepository()

    private val lockManager = FakeInferenceLockManager()

    private lateinit var context: Context
    private lateinit var runtime: NativeLocalImageGenRuntime

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runtime = NativeLocalImageGenRuntime(context)
        val activityManager = context.getSystemService(ActivityManager::class.java)
        shadowOf(activityManager).setMemoryClass(512)
    }

    @Test
    fun `typed generation error surfaces backend detail`() =
        runTest {
            val engine = FakeLocalImageGenerationEngine(runtime)
            engine.result =
                Result.failure(
                    LocalImageGenerationError.RuntimeNotPackaged(
                        LocalImageGenerationBackendKind.QNN_NPU,
                        "QNN runtime not packaged",
                    ),
                )
            val generator = createGenerator(engine)

            val result =
                generator.generate(
                    prompt = "bonsai tree",
                    target = localTarget(),
                    settings = ImageGenerationSettings(imageResolution = "512x512"),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is LocalImageGenerationError.RuntimeNotPackaged)
        }

    private fun createGenerator(engine: LocalImageGenerationEngine): LocalStableDiffusionImageGenerator =
        LocalStableDiffusionImageGenerator(
            context = context,
            engine = engine,
            nativeRuntime = runtime,
            inferenceLockManager = lockManager,
            studioRepository = studioRepository,
            dispatcher = Dispatchers.Unconfined,
        )

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
            val engine = FakeLocalImageGenerationEngine(runtime)
            engine.result = Result.success(LocalImageGenerationNativeResult(emptyList(), 512, 512, 0L))
            val generator = createGenerator(engine)

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
            val capturedRequest = engine.generateCalls.single()
            assertEquals(512, capturedRequest.width)
            assertEquals(512, capturedRequest.height)
            assertEquals(20, capturedRequest.steps)
            assertEquals(7.5f, capturedRequest.cfgScale)
        }

    @Test
    fun `INF-002 SDXL native request uses 1024 selected steps CFG and lcm`() =
        runTest {
            val engine = FakeLocalImageGenerationEngine(runtime)
            engine.result = Result.success(LocalImageGenerationNativeResult(emptyList(), 1024, 1024, 0L))
            val generator = createGenerator(engine)

            val sdxlTarget =
                localTarget().copy(
                    modelId = LocalImageGenerationModelId.SDXL_QNN,
                    capabilities =
                        ProviderCapabilities(
                            supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
                            supportedImageQualities = listOf(GenerationQuality.SPEED),
                            supportedImageResolutions = listOf("1024x1024"),
                            supportedImageSteps = listOf(8, 12, 16, 20, 24, 30),
                            defaultImageStep = 8,
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
            val capturedRequest = engine.generateCalls.single()
            assertEquals(1024, capturedRequest.width)
            assertEquals(1024, capturedRequest.height)
            assertEquals(8, capturedRequest.steps)
            assertEquals(1.5f, capturedRequest.cfgScale)
            assertEquals("lcm", capturedRequest.scheduler)
        }

    @Test
    fun `INF-003 stale SD15 1024 request fails before native launch with exact error`() =
        runTest {
            val engine = FakeLocalImageGenerationEngine(runtime)
            val generator = createGenerator(engine)

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
            val engine = FakeLocalImageGenerationEngine(runtime)
            engine.result = Result.success(LocalImageGenerationNativeResult(emptyList(), 1024, 1024, 0L))
            val generator = createGenerator(engine)

            val sdxlTarget = localTarget().copy(
                modelId = LocalImageGenerationModelId.SDXL_QNN,
                capabilities = ProviderCapabilities(
                    supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
                    supportedImageQualities = listOf(GenerationQuality.SPEED),
                    supportedImageResolutions = listOf("1024x1024"),
                    supportedImageSteps = listOf(8, 12, 16, 20, 24, 30),
                    defaultImageStep = 8,
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
            assertEquals(16, engine.generateCalls.single().steps)
        }

    @Test
    fun `MUT-003 lazy any-step implementation fails because step 13 must clamp to 16`() =
        runTest {
            val engine = FakeLocalImageGenerationEngine(runtime)
            engine.result = Result.success(LocalImageGenerationNativeResult(emptyList(), 1024, 1024, 0L))
            val generator = createGenerator(engine)

            val sdxlTarget = localTarget().copy(
                modelId = LocalImageGenerationModelId.SDXL_QNN,
                capabilities = ProviderCapabilities(
                    supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
                    supportedImageQualities = listOf(GenerationQuality.SPEED),
                    supportedImageResolutions = listOf("1024x1024"),
                    supportedImageSteps = listOf(8, 12, 16, 20, 24, 30),
                    defaultImageStep = 8,
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
            assertEquals(16, engine.generateCalls.single().steps)
        }

    @Test
    fun `img2img failure deletes init image from cache`() =
        runTest {
            val cacheRoot = java.nio.file.Files.createTempDirectory("local-image-gen-cache").toFile()
            val cacheContext =
                object : android.content.ContextWrapper(context) {
                    override fun getCacheDir(): java.io.File = cacheRoot
                }
            val outputDir = cacheRoot.resolve("local-image-gen").apply { mkdirs() }
            val modelDir = java.io.File.createTempFile("model", "").parentFile!!.resolve("img2img-model")
            modelDir.mkdirs()
            java.io.File(modelDir, "vae_encoder.bin").writeBytes(byteArrayOf(1))
            val engine = FakeLocalImageGenerationEngine(runtime)
            engine.result =
                Result.failure(
                    LocalImageGenerationError.GenerationFailed(
                        LocalImageGenerationBackendKind.QNN_NPU,
                        "Generation failed",
                    ),
                )
            studioRepository.mediaBytes["cache://reference.png"] = byteArrayOf(1, 2, 3)
            val generator =
                LocalStableDiffusionImageGenerator(
                    context = cacheContext,
                    engine = engine,
                    nativeRuntime = runtime,
                    inferenceLockManager = lockManager,
                    studioRepository = studioRepository,
                    dispatcher = Dispatchers.Unconfined,
                )

            val result =
                generator.generate(
                    prompt = "sunset",
                    target = localTarget().copy(modelDir = modelDir.absolutePath),
                    settings =
                        ImageGenerationSettings(
                            imageResolution = "512x512",
                            referenceImageUri = "cache://reference.png",
                        ),
                )

            assertTrue(result.isFailure)
            assertTrue(outputDir.listFiles { file -> file.name.startsWith("init-") }.isNullOrEmpty())
        }

    @Test
    fun `INF-005 img2img native request forwards clamped denoise strength`() =
        runTest {
            val engine = FakeLocalImageGenerationEngine(runtime)
            engine.result = Result.success(LocalImageGenerationNativeResult(emptyList(), 512, 512, 0L))
            val modelDir = java.io.File.createTempFile("model", "").parentFile!!.resolve("img2img-model")
            modelDir.mkdirs()
            java.io.File(modelDir, "vae_encoder.bin").writeBytes(byteArrayOf(1))
            studioRepository.mediaBytes["cache://reference.png"] = byteArrayOf(1, 2, 3)
            val generator = createGenerator(engine)

            val result =
                generator.generate(
                    prompt = "sunset",
                    target = localTarget().copy(modelDir = modelDir.absolutePath),
                    settings =
                        ImageGenerationSettings(
                            imageResolution = "512x512",
                            referenceImageUri = "cache://reference.png",
                            img2ImgDenoiseStrength = 0.4f,
                        ),
                )

            assertTrue(result.isSuccess)
            val capturedRequest = engine.generateCalls.single()
            assertEquals(LocalImageGenerationMode.IMG2IMG, capturedRequest.mode)
            assertEquals(0.4f, capturedRequest.denoiseStrength)
        }

    @Test
    fun `INF-006 Juggernaut native request uses 1024 24 CFG 4_0 and scheduler dpm`() =
        runTest {
            val engine = FakeLocalImageGenerationEngine(runtime)
            engine.result = Result.success(LocalImageGenerationNativeResult(emptyList(), 1024, 1024, 0L))
            val generator = createGenerator(engine)

            val juggernautTarget =
                localTarget().copy(
                    modelId = LocalImageGenerationModelId.JUGGERNAUT_XL_QNN,
                    capabilities =
                        ProviderCapabilities(
                            supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
                            supportedImageQualities = emptyList(),
                            supportedImageResolutions = listOf("1024x1024"),
                            supportedImageSteps = listOf(20, 22, 24, 26, 28, 30),
                            defaultImageStep = 24,
                            supportedVideoQualities = emptyList(),
                            supportedVideoResolutions = emptyList(),
                            supportedVideoDurations = emptyList(),
                            supportsVideo = false,
                        ),
                )

            val result =
                generator.generate(
                    prompt = "sunset",
                    target = juggernautTarget,
                    settings = ImageGenerationSettings(imageResolution = "1024x1024", diffusionSteps = 24),
                )

            assertTrue(result.isSuccess)
            val capturedRequest = engine.generateCalls.single()
            assertEquals(1024, capturedRequest.width)
            assertEquals(1024, capturedRequest.height)
            assertEquals(24, capturedRequest.steps)
            assertEquals(4.0f, capturedRequest.cfgScale)
            assertEquals("dpm", capturedRequest.scheduler)
        }

    @Test
    fun `INF-007 stale Juggernaut step 23 clamps to 24 before native launch`() =
        runTest {
            val engine = FakeLocalImageGenerationEngine(runtime)
            engine.result = Result.success(LocalImageGenerationNativeResult(emptyList(), 1024, 1024, 0L))
            val generator = createGenerator(engine)

            val juggernautTarget =
                localTarget().copy(
                    modelId = LocalImageGenerationModelId.JUGGERNAUT_XL_QNN,
                    capabilities =
                        ProviderCapabilities(
                            supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
                            supportedImageQualities = emptyList(),
                            supportedImageResolutions = listOf("1024x1024"),
                            supportedImageSteps = listOf(20, 22, 24, 26, 28, 30),
                            defaultImageStep = 24,
                            supportedVideoQualities = emptyList(),
                            supportedVideoResolutions = emptyList(),
                            supportedVideoDurations = emptyList(),
                            supportsVideo = false,
                        ),
                )

            val result =
                generator.generate(
                    prompt = "sunset",
                    target = juggernautTarget,
                    settings = ImageGenerationSettings(imageResolution = "1024x1024", diffusionSteps = 23),
                )

            assertTrue(result.isSuccess)
            assertEquals(24, engine.generateCalls.single().steps)
        }

    @Test
    fun `MUT-005 reusing SDXL lcm policy for Juggernaut fails scheduler and CFG checks`() =
        runTest {
            val engine = FakeLocalImageGenerationEngine(runtime)
            engine.result = Result.success(LocalImageGenerationNativeResult(emptyList(), 1024, 1024, 0L))
            val generator = createGenerator(engine)

            val juggernautTarget =
                localTarget().copy(
                    modelId = LocalImageGenerationModelId.JUGGERNAUT_XL_QNN,
                    capabilities =
                        ProviderCapabilities(
                            supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
                            supportedImageQualities = emptyList(),
                            supportedImageResolutions = listOf("1024x1024"),
                            supportedImageSteps = listOf(20, 22, 24, 26, 28, 30),
                            defaultImageStep = 24,
                            supportedVideoQualities = emptyList(),
                            supportedVideoResolutions = emptyList(),
                            supportedVideoDurations = emptyList(),
                            supportsVideo = false,
                        ),
                )

            val result =
                generator.generate(
                    prompt = "sunset",
                    target = juggernautTarget,
                    settings = ImageGenerationSettings(imageResolution = "1024x1024", diffusionSteps = 24),
                )

            assertTrue(result.isSuccess)
            val capturedRequest = engine.generateCalls.single()
            assertEquals("dpm", capturedRequest.scheduler)
            assertEquals(4.0f, capturedRequest.cfgScale)
            assertEquals(24, capturedRequest.steps)
        }
}
