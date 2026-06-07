package com.browntowndev.enclave.feature.inference.media.local

import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationError
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.localimagegen.NativeImageGenGenerateParams
import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MnnCpuStableDiffusionBackendTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `MNN probe one is ready and generation succeeds`() =
        runTest {
            val nativeRuntime = mockk<NativeLocalImageGenRuntime>()
            every { nativeRuntime.probe(LocalImageGenerationBackendKind.CPU_MNN) } returns 1
            every { nativeRuntime.generate(any()) } answers {
                File(firstArg<NativeImageGenGenerateParams>().outputPath).writeBytes(byteArrayOf(1))
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
            val nativeRuntime = mockk<NativeLocalImageGenRuntime>()
            every { nativeRuntime.probe(LocalImageGenerationBackendKind.CPU_MNN) } returns 0

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
