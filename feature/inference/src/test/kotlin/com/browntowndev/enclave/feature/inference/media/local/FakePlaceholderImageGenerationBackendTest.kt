package com.browntowndev.enclave.feature.inference.media.local

import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * UI-flow test helper: produces a tiny PNG without QNN/MNN runtimes.
 */
class FakePlaceholderImageGenerationBackendTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `placeholder backend writes png file`() =
        runTest {
            val backend = FakePlaceholderImageGenerationBackend()
            val prefix = File(tempDir, "out").absolutePath
            val result =
                backend
                    .generate(
                        LocalImageGenerationNativeRequest(
                            backend = LocalImageGenerationBackendKind.CPU_MNN,
                            modelDir = tempDir.absolutePath,
                            prompt = "test",
                            negativePrompt = null,
                            width = 64,
                            height = 64,
                            steps = 4,
                            cfgScale = 7.5f,
                            scheduler = "dpm",
                            seed = 42L,
                            outputPathPrefix = prefix,
                        ),
                    )
                    .getOrThrow()

            assertTrue(result.outputPaths.single().endsWith(".png"))
            assertTrue(File(result.outputPaths.single()).length() > 0L)
        }
}