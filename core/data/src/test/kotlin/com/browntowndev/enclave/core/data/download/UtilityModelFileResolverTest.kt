package com.browntowndev.enclave.core.data.download

import android.content.Context
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class UtilityModelFileResolverTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    @DisplayName("SCN-RESOLVE-001")
    fun scnResolve001_returnsPathWhenRoomRowAndFileMatchSize() =
        runTest {
            val modelsDir = File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val modelFile =
                File(modelsDir, "ggml-base.en.bin").apply {
                    writeBytes(ByteArray(4))
                }
            val repository = repositoryReturning(whisperMetadata(sizeInBytes = 4L))
            val resolver = UtilityModelFileResolver(fakeContext(), repository)

            val path = resolver.resolveUtilityModelPath(UtilityType.WHISPER)

            assertEquals(modelFile.absolutePath, path)
        }

    @Test
    @DisplayName("SCN-RESOLVE-002")
    fun scnResolve002_returnsNullWhenRoomRowMissing() =
        runTest {
            val repository =
                mockk<UtilityModelRepositoryPort> {
                    coEvery { getByUtilityType(UtilityType.WHISPER) } returns null
                }
            val resolver = UtilityModelFileResolver(fakeContext(), repository)

            val path = resolver.resolveUtilityModelPath(UtilityType.WHISPER)

            assertNull(path)
        }

    @Test
    @DisplayName("SCN-RESOLVE-003")
    fun scnResolve003_returnsNullWhenFileSizeMismatchesMetadata() =
        runTest {
            File(tempDir, ModelConfig.MODELS_DIR)
                .apply { mkdirs() }
                .resolve("ggml-base.en.bin")
                .writeBytes(ByteArray(2))
            val repository = repositoryReturning(whisperMetadata(sizeInBytes = 4L))
            val resolver = UtilityModelFileResolver(fakeContext(), repository)

            val path = resolver.resolveUtilityModelPath(UtilityType.WHISPER)

            assertNull(path)
        }

    @Test
    @DisplayName("SCN-MUT-001")
    fun scnMut001_resolverDoesNotFetchRemoteConfig() =
        runTest {
            val modelsDir = File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            File(modelsDir, "ggml-base.en.bin").writeBytes(ByteArray(4))
            val repository = repositoryReturning(whisperMetadata(sizeInBytes = 4L))
            val resolver = UtilityModelFileResolver(fakeContext(), repository)

            val path = resolver.resolveUtilityModelPath(UtilityType.WHISPER)

            assertEquals(File(modelsDir, "ggml-base.en.bin").absolutePath, path)
        }

    @Test
    @DisplayName("SCN-MUT-003")
    fun scnMut003_resolverReturnsPathForRequestedUtilityTypeOnly() =
        runTest {
            val modelsDir = File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            File(modelsDir, "model.onnx").writeBytes(ByteArray(100))
            File(modelsDir, "ggml-base.en.bin").writeBytes(ByteArray(4))
            val repository =
                mockk<UtilityModelRepositoryPort> {
                    coEvery { getByUtilityType(UtilityType.ONNX_MODEL) } returns onnxMetadata()
                    coEvery { getByUtilityType(UtilityType.WHISPER) } returns whisperMetadata(sizeInBytes = 4L)
                }
            val resolver = UtilityModelFileResolver(fakeContext(), repository)

            val path = resolver.resolveUtilityModelPath(UtilityType.WHISPER)

            assertTrue(path!!.endsWith("/ggml-base.en.bin"))
            assertTrue(!path.endsWith("/model.onnx"))
        }

    private fun fakeContext(): Context =
        mockk {
            every { getExternalFilesDir(null) } returns tempDir
        }

    private fun repositoryReturning(metadata: UtilityModelMetadata): UtilityModelRepositoryPort =
        mockk {
            coEvery { getByUtilityType(metadata.utilityType) } returns metadata
        }

    private fun whisperMetadata(sizeInBytes: Long) =
        UtilityModelMetadata(
            id = UtilityModelId("whisper-id"),
            utilityType = UtilityType.WHISPER,
            huggingFaceModelName = "ggerganov/whisper.cpp",
            remoteFileName = "ggml-base.en.bin",
            localFileName = "ggml-base.en.bin",
            sha256 = "whisper-sha",
            sizeInBytes = sizeInBytes,
            modelFileFormat = ModelFileFormat.BIN,
            source = DownloadSource.HUGGING_FACE,
        )

    private fun onnxMetadata() =
        UtilityModelMetadata(
            id = UtilityModelId("onnx-id"),
            utilityType = UtilityType.ONNX_MODEL,
            huggingFaceModelName = "onnx",
            remoteFileName = "model.onnx",
            localFileName = "model.onnx",
            sha256 = "onnx-sha",
            sizeInBytes = 100L,
            modelFileFormat = ModelFileFormat.ONNX,
            source = DownloadSource.HUGGING_FACE,
        )
}
