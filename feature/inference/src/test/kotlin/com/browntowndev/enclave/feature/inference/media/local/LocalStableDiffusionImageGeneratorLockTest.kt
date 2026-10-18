package com.browntowndev.enclave.feature.inference.media.local

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34], shadows = [ShadowFakeNativeLocalImageGenRuntime::class])
class LocalStableDiffusionImageGeneratorLockTest {
    @Test
    fun `busy shared on-device lock rejects image generation before native launch`() =
        runTest {
            val context: Context = ApplicationProvider.getApplicationContext()
            val runtime = NativeLocalImageGenRuntime(context)
            val lockManager = FakeInferenceLockManager()
            lockManager.acquireResult = false
            val engine = FakeLocalImageGenerationEngine(runtime)
            val generator =
                LocalStableDiffusionImageGenerator(
                    context = context,
                    engine = engine,
                    nativeRuntime = runtime,
                    inferenceLockManager = lockManager,
                    studioRepository = FakeStudioRepository(),
                    dispatcher = Dispatchers.Unconfined,
                )

            val result =
                generator.generate(
                    prompt = "sunset",
                    target = localTarget(),
                    settings = ImageGenerationSettings(imageResolution = "512x512"),
                )

            assertTrue(result.exceptionOrNull() is LocalImageGenerationError.Busy)
            assertTrue(engine.generateCalls.isEmpty())
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
