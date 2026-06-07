package com.browntowndev.enclave.core.data.download

import android.content.Context
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
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
    fun scnResolve001_returnsPathWhenRoomRowAndFileMatchSizeAndHash() =
        runTest {
            val modelsDir = File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val modelFile =
                File(modelsDir, "ggml-base.en.bin").apply {
                    writeBytes(FOUR_ZERO_BYTES)
                }
            val repository = repositoryReturning(whisperMetadata(sizeInBytes = 4L, sha256 = FOUR_ZERO_SHA256))
            val resolver = resolver(repository)

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
            val resolver = resolver(repository)

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
            val repository = repositoryReturning(whisperMetadata(sizeInBytes = 4L, sha256 = FOUR_ZERO_SHA256))
            val resolver = resolver(repository)

            val path = resolver.resolveUtilityModelPath(UtilityType.WHISPER)

            assertNull(path)
        }

    @Test
    @DisplayName("SCN-RESOLVE-003b")
    fun scnResolve003b_returnsNullWhenFileHashMismatchesMetadata() =
        runTest {
            File(tempDir, ModelConfig.MODELS_DIR)
                .apply { mkdirs() }
                .resolve("ggml-base.en.bin")
                .writeBytes(ByteArray(4) { 0xFF.toByte() }) // correct size, wrong content
            val repository = repositoryReturning(whisperMetadata(sizeInBytes = 4L, sha256 = FOUR_ZERO_SHA256))
            val resolver = resolver(repository)

            val path = resolver.resolveUtilityModelPath(UtilityType.WHISPER)

            assertNull(path)
        }

    @Test
    @DisplayName("SCN-MUT-001")
    fun scnMut001_resolverDoesNotFetchRemoteConfig() =
        runTest {
            val modelsDir = File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            File(modelsDir, "ggml-base.en.bin").writeBytes(FOUR_ZERO_BYTES)
            val repository = repositoryReturning(whisperMetadata(sizeInBytes = 4L, sha256 = FOUR_ZERO_SHA256))
            val resolver = resolver(repository)

            val path = resolver.resolveUtilityModelPath(UtilityType.WHISPER)

            assertEquals(File(modelsDir, "ggml-base.en.bin").absolutePath, path)
        }

    @Test
    @DisplayName("SCN-MUT-003")
    fun scnMut003_resolverReturnsPathForRequestedUtilityTypeOnly() =
        runTest {
            val modelsDir = File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            File(modelsDir, "model.onnx").writeBytes(ByteArray(100))
            File(modelsDir, "ggml-base.en.bin").writeBytes(FOUR_ZERO_BYTES)
            val repository =
                mockk<UtilityModelRepositoryPort> {
                    coEvery { getByUtilityType(UtilityType.ONNX_MODEL) } returns onnxMetadata()
                    coEvery { getByUtilityType(UtilityType.WHISPER) } returns whisperMetadata(sizeInBytes = 4L, sha256 = FOUR_ZERO_SHA256)
                }
            val resolver = resolver(repository)

            val path = resolver.resolveUtilityModelPath(UtilityType.WHISPER)

            assertTrue(path!!.endsWith("/ggml-base.en.bin"))
            assertTrue(!path.endsWith("/model.onnx"))
        }

    @Test
    @DisplayName("SCN-RESOLVE-SD15-QNN-001")
    fun scnResolveSd15Qnn001_returnsInstalledPackDirectoryAfterZipDeletion() =
        runTest {
            val modelsDir = File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val packDir = File(modelsDir, LocalImageGenerationPaths.SD15_QNN_SUBDIR).apply { mkdirs() }
            listOf("clip_v2.mnn", "token_emb.bin", "pos_emb.bin", "unet.bin", "vae_decoder.bin", "tokenizer.json").forEach { name ->
                File(packDir, name).writeBytes(byteArrayOf(1))
            }
            val repository = repositoryReturning(sd15QnnPackMetadata())
            val resolver = resolver(repository)

            val path = resolver.resolveUtilityModelPath(UtilityType.SD15_QNN_PACK)

            assertEquals(packDir.absolutePath, path)
        }

    @Test
    @DisplayName("DL-005 resolves installed SDXL QNN pack directory")
    fun dl005_resolvesInstalledSdxlQnnPackDirectory() =
        runTest {
            val modelsDir = File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val packDir = File(modelsDir, LocalImageGenerationPaths.SDXL_QNN_SUBDIR).apply { mkdirs() }
            LocalImageGenerationPaths.REQUIRED_SDXL_QNN_FILES.forEach { name ->
                File(packDir, name).writeBytes(byteArrayOf(1))
            }
            val repository = repositoryReturning(sdxlQnnPackMetadata())
            val resolver = resolver(repository)

            val path = resolver.resolveUtilityModelPath(UtilityType.SDXL_QNN_PACK)

            assertEquals(packDir.absolutePath, path)
        }

    @Test
    @DisplayName("DL-010 resolves installed Juggernaut QNN pack directory")
    fun dl010_resolvesInstalledJuggernautQnnPackDirectory() =
        runTest {
            val modelsDir = File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val packDir = File(modelsDir, LocalImageGenerationPaths.JUGGERNAUT_XL_QNN_SUBDIR).apply { mkdirs() }
            LocalImageGenerationPaths.REQUIRED_SDXL_QNN_FILES.forEach { name ->
                File(packDir, name).writeBytes(byteArrayOf(1))
            }
            val repository = repositoryReturning(juggernautQnnPackMetadata())
            val resolver = resolver(repository)

            val path = resolver.resolveUtilityModelPath(UtilityType.JUGGERNAUT_XL_QNN_PACK)

            assertEquals(packDir.absolutePath, path)
        }

    @Test
    fun resolveLocalModelPath_returnsSdxlQnnDirWhenCatalogAssetInstalled() =
        runTest {
            val modelsDir = File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val packDir = File(modelsDir, LocalImageGenerationPaths.SDXL_QNN_SUBDIR).apply { mkdirs() }
            LocalImageGenerationPaths.REQUIRED_SDXL_QNN_FILES.forEach { name ->
                File(packDir, name).writeBytes(byteArrayOf(1))
            }
            val resolver = resolver()

            val path =
                resolver.resolveLocalModelPath(
                    LocalModelMetadata(
                        id = LocalModelId("sdxl-diffusion"),
                        displayName = "Stable Diffusion XL (NPU)",
                        huggingFaceModelName = "xororz/sdxl-qnn",
                        remoteFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                        localFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                        sha256 = "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83",
                        sizeInBytes = 3_504_485_672L,
                        modelFileFormat = ModelFileFormat.BIN,
                    ),
                )

            assertEquals(packDir.absolutePath, path)
        }

    @Test
    fun scnResolveLocal001_returnsAbsolutePathWhenLocalModelPresentAndSizeAndHashMatch() =
        runTest {
            val modelsDir = File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val diffusionFile =
                File(modelsDir, "stable-diffusion-v1-5-Q4_0.gguf").apply { writeBytes(FOUR_ZERO_BYTES) }
            val resolver = resolver()

            val path = resolver.resolveLocalModelPath(localMetadata(sizeInBytes = 4L, sha256 = FOUR_ZERO_SHA256))

            assertEquals(diffusionFile.absolutePath, path)
        }

    @Test
    @DisplayName("SCN-RESOLVE-LOCAL-001b")
    fun scnResolveLocal001b_returnsNullWhenLocalModelHashMismatch() =
        runTest {
            val modelsDir = File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            File(modelsDir, "stable-diffusion-v1-5-Q4_0.gguf").apply { writeBytes(ByteArray(4) { 0xFF.toByte() }) }
            val resolver = resolver()

            // File size matches but content (and therefore SHA-256) differs
            val path = resolver.resolveLocalModelPath(localMetadata(sizeInBytes = 4L, sha256 = FOUR_ZERO_SHA256))

            assertNull(path)
        }

    @Test
    @DisplayName("SCN-RESOLVE-LOCAL-002")
    fun scnResolveLocal002_returnsNullWhenLocalModelMissing() =
        runTest {
            File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val resolver = resolver()

            val path = resolver.resolveLocalModelPath(localMetadata(sizeInBytes = 8L))

            assertNull(path)
        }

    @Test
    @DisplayName("SCN-RESOLVE-LOCAL-003")
    fun scnResolveLocal003_returnsNullWhenLocalModelSizeMismatches() =
        runTest {
            val modelsDir = File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            File(modelsDir, "stable-diffusion-v1-5-Q4_0.gguf").writeBytes(ByteArray(2))
            val resolver = resolver()

            val path = resolver.resolveLocalModelPath(localMetadata(sizeInBytes = 8L, sha256 = FOUR_ZERO_SHA256))

            assertNull(path)
        }

    companion object {
        /** SHA-256 of ByteArray(4) — 4 zero bytes. */
        const val FOUR_ZERO_SHA256 = "df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119"
        val FOUR_ZERO_BYTES = ByteArray(4)
    }

    private fun localMetadata(
        sizeInBytes: Long,
        sha256: String = "placeholder-sha256-that-will-never-match",
    ) = LocalModelMetadata(
        id = LocalModelId("bonsai-model"),
        displayName = "Stable Diffusion 1.5",
        huggingFaceModelName = "",
        remoteFileName = "stable-diffusion-v1-5-Q4_0.gguf",
        localFileName = "stable-diffusion-v1-5-Q4_0.gguf",
        sha256 = sha256,
        sizeInBytes = sizeInBytes,
        modelFileFormat = ModelFileFormat.GGUF,
    )

    private fun fakeContext(): Context =
        mockk {
            every { getExternalFilesDir(null) } returns tempDir
        }

    private fun resolver(repository: UtilityModelRepositoryPort = mockk(relaxed = true)): UtilityModelFileResolver =
        UtilityModelFileResolver(
            context = fakeContext(),
            utilityModelRepository = repository,
            hashingPort = HashingService(),
        )

    private fun repositoryReturning(metadata: UtilityModelMetadata): UtilityModelRepositoryPort =
        mockk {
            coEvery { getByUtilityType(metadata.utilityType) } returns metadata
        }

    private fun whisperMetadata(
        sizeInBytes: Long,
        sha256: String = "placeholder-sha256-that-will-never-match",
    ) = UtilityModelMetadata(
        id = UtilityModelId("whisper-id"),
        utilityType = UtilityType.WHISPER,
        huggingFaceModelName = "ggerganov/whisper.cpp",
        remoteFileName = "ggml-base.en.bin",
        localFileName = "ggml-base.en.bin",
        sha256 = sha256,
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

    private fun sd15QnnPackMetadata() =
        UtilityModelMetadata(
            id = UtilityModelId("sd15-qnn-id"),
            utilityType = UtilityType.SD15_QNN_PACK,
            huggingFaceModelName = "xororz/sd-qnn",
            remoteFileName = "AnythingV5_qnn2.28_8gen2.zip",
            localFileName = "AnythingV5_qnn2.28_8gen2.zip",
            sha256 = "unused-after-install",
            sizeInBytes = 1_057_820_237L,
            modelFileFormat = ModelFileFormat.BIN,
            source = DownloadSource.HUGGING_FACE,
        )

    private fun sdxlQnnPackMetadata() =
        UtilityModelMetadata(
            id = UtilityModelId("sdxl-qnn-id"),
            utilityType = UtilityType.SDXL_QNN_PACK,
            huggingFaceModelName = "xororz/sdxl-qnn",
            remoteFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
            localFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
            sha256 = "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83",
            sizeInBytes = 3_504_485_672L,
            modelFileFormat = ModelFileFormat.BIN,
            source = DownloadSource.HUGGING_FACE,
        )

    private fun juggernautQnnPackMetadata() =
        UtilityModelMetadata(
            id = UtilityModelId("juggernaut-qnn-id"),
            utilityType = UtilityType.JUGGERNAUT_XL_QNN_PACK,
            huggingFaceModelName = "xororz/sdxl-qnn",
            remoteFileName = "juggernaut_qnn2.28_8gen3.zip",
            localFileName = "juggernaut_qnn2.28_8gen3.zip",
            sha256 = "b27231823434c4e4184a61f77401227264a5308c6846c6fc628c37583e76413c",
            sizeInBytes = 3_747_687_306L,
            modelFileFormat = ModelFileFormat.BIN,
            source = DownloadSource.HUGGING_FACE,
        )
}
