package com.browntowndev.enclave.feature.inference.media.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationError
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.feature.inference.ShadowFakeNativeLocalImageGenRuntime
import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34], shadows = [ShadowFakeNativeLocalImageGenRuntime::class])
class MnnCpuStableDiffusionBackendTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var nativeRuntime: NativeLocalImageGenRuntime

    @BeforeEach
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        nativeRuntime = NativeLocalImageGenRuntime(context)
    }

    @Test
    fun `MNN probe one is ready and generation succeeds`() =
        runTest {
            ShadowFakeNativeLocalImageGenRuntime.probeResults[LocalImageGenerationBackendKind.CPU_MNN] = 1
            ShadowFakeNativeLocalImageGenRuntime.generateHandler = { params ->
                File(params.outputPath).writeBytes(byteArrayOf(1))
                0
            }
            val backend = MnnCpuStableDiffusionBackend(nativeRuntime)

            val result = backend.generate(request())

            assertTrue(result.isSuccess)
            assertTrue(File(result.getOrThrow().outputPaths.single()).isFile)
        }

    @Test
    fun `MNN probe zero reports runtime not packaged`() =
        runTest {
            ShadowFakeNativeLocalImageGenRuntime.probeResults[LocalImageGenerationBackendKind.CPU_MNN] = 0

            val result = MnnCpuStableDiffusionBackend(nativeRuntime).generate(request())

            assertTrue(result.exceptionOrNull() is LocalImageGenerationError.RuntimeNotPackaged)
        }

    private fun request(): LocalImageGenerationNativeRequest =
        LocalImageGenerationNativeRequest(
            backend = LocalImageGenerationBackendKind.CPU_MNN,
            modelId = LocalImageGenerationModelId.SD15_CPU,
            modelDir = tempDir.absolutePath,
            prompt = "test",
            negativePrompt = null,
            width = 512,
            height = 512,
            steps = 20,
            cfgScale = 7.5f,
            scheduler = "dpm",
            seed = 42L,
            outputPathPrefix = File(tempDir, "output").absolutePath,
        )
}
