package com.browntowndev.enclave.core.data.download

import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ModelFileScannerTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var modelsDir: File
    private lateinit var scanner: ModelFileScanner

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        context = mockk()
        every { context.getExternalFilesDir(null) } returns tempDir

        modelsDir = File(tempDir, ModelConfig.MODELS_DIR)
        modelsDir.deleteRecursively()
        modelsDir.mkdirs()

        scanner =
            ModelFileScanner(
                context = context,
                localModelRepository = mockk<LocalModelRepositoryPort>(relaxed = true),
                activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true),
            )
    }

    @AfterEach
    fun tearDown() {
        modelsDir.deleteRecursively()
        unmockkStatic(Log::class)
    }

    @Test
    fun `scan accepts matching sized final file`() =
        kotlinx.coroutines.test.runTest {
            val asset = createAsset(filename = "model.gguf", sizeBytes = 4L)
            File(modelsDir, asset.metadata.localFileName).writeBytes(byteArrayOf(1, 2, 3, 4))

            val result = scanner.scanAndCreateDirIfNotExist(mapOf(ModelType.MAIN to asset), utilityAssets = emptyList())

            assertTrue(result.missingModels.isEmpty())
            assertTrue(result.partialDownloads.isEmpty())
            assertTrue(result.invalidModels.isEmpty())
            assertTrue(result.allValid)
        }

    @Test
    fun `scan rejects final file with wrong size`() =
        kotlinx.coroutines.test.runTest {
            val asset = createAsset(filename = "model.gguf", sizeBytes = 4L)
            File(modelsDir, asset.metadata.localFileName).writeBytes(byteArrayOf(1, 2))

            val result = scanner.scanAndCreateDirIfNotExist(mapOf(ModelType.MAIN to asset), utilityAssets = emptyList())

            assertEquals(listOf(asset), result.invalidModels)
            assertFalse(result.allValid)
        }

    @Test
    fun `scan treats temp file as partial download when meta file is valid`() =
        kotlinx.coroutines.test.runTest {
            val asset = createAsset(filename = "model.gguf", sizeBytes = 4L, sha256 = "dummy-hash")
            File(modelsDir, "${asset.metadata.localFileName}${ModelConfig.TEMP_EXTENSION}")
                .writeBytes(byteArrayOf(1, 2))
            File(modelsDir, "${asset.metadata.localFileName}${ModelConfig.TEMP_META_EXTENSION}")
                .writeText("4\ndummy-hash")

            val result = scanner.scanAndCreateDirIfNotExist(mapOf(ModelType.MAIN to asset), utilityAssets = emptyList())

            assertEquals(2L, result.partialDownloads[asset.metadata.localFileName])
            assertTrue(result.missingModels.isEmpty())
            assertTrue(result.invalidModels.isEmpty())
            assertFalse(result.allValid)
        }

    @Test
    fun `scan rejects partial download when meta file is missing`() =
        kotlinx.coroutines.test.runTest {
            val asset = createAsset(filename = "model.gguf", sizeBytes = 4L)
            File(modelsDir, "${asset.metadata.localFileName}${ModelConfig.TEMP_EXTENSION}")
                .writeBytes(byteArrayOf(1, 2))

            val result = scanner.scanAndCreateDirIfNotExist(mapOf(ModelType.MAIN to asset), utilityAssets = emptyList())

            assertTrue(result.partialDownloads.isEmpty())
            assertEquals(1, result.missingModels.size)
            assertTrue(result.invalidModels.isEmpty())
            assertFalse(result.allValid)
        }

    @Test
    fun `scan rejects partial download when meta file is stale`() =
        kotlinx.coroutines.test.runTest {
            val asset = createAsset(filename = "model.gguf", sizeBytes = 4L, sha256 = "new-hash")
            File(modelsDir, "${asset.metadata.localFileName}${ModelConfig.TEMP_EXTENSION}")
                .writeBytes(byteArrayOf(1, 2))
            File(modelsDir, "${asset.metadata.localFileName}${ModelConfig.TEMP_META_EXTENSION}")
                .writeText("4\nold-hash")

            val result = scanner.scanAndCreateDirIfNotExist(mapOf(ModelType.MAIN to asset), utilityAssets = emptyList())

            assertTrue(result.partialDownloads.isEmpty())
            assertEquals(1, result.missingModels.size)
            assertTrue(result.invalidModels.isEmpty())
            assertFalse(result.allValid)
        }

    @Test
    fun `scan prefers temp file over final file`() =
        kotlinx.coroutines.test.runTest {
            val asset = createAsset(filename = "model.gguf", sizeBytes = 4L, sha256 = "dummy-hash")
            File(modelsDir, asset.metadata.localFileName).writeBytes(byteArrayOf(1, 2, 3, 4))
            File(modelsDir, "${asset.metadata.localFileName}${ModelConfig.TEMP_EXTENSION}")
                .writeBytes(byteArrayOf(1, 2))
            File(modelsDir, "${asset.metadata.localFileName}${ModelConfig.TEMP_META_EXTENSION}")
                .writeText("4\ndummy-hash")

            val result = scanner.scanAndCreateDirIfNotExist(mapOf(ModelType.MAIN to asset), utilityAssets = emptyList())

            assertEquals(2L, result.partialDownloads[asset.metadata.localFileName])
            assertTrue(result.invalidModels.isEmpty())
            assertFalse(result.allValid)
        }

    @Test
    fun `scan treats installed SDXL QNN pack directory as present`() =
        kotlinx.coroutines.test.runTest {
            val asset =
                createQnnPackAsset(
                    filename = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                    utilityType = UtilityType.SDXL_QNN_PACK,
                    packSubdir = LocalImageGenerationPaths.SDXL_QNN_SUBDIR,
                )

            val result =
                scanner.scanAndCreateDirIfNotExist(
                    expectedModels = emptyMap(),
                    utilityAssets = listOf(asset),
                )

            assertTrue(result.missingModels.isEmpty())
            assertTrue(result.allValid)
        }

    @Test
    fun `scan marks SDXL QNN pack missing when extracted artifacts are absent`() =
        kotlinx.coroutines.test.runTest {
            val asset =
                createQnnPackAsset(
                    filename = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                    utilityType = UtilityType.SDXL_QNN_PACK,
                    packSubdir = LocalImageGenerationPaths.SDXL_QNN_SUBDIR,
                    installArtifacts = false,
                )

            val result =
                scanner.scanAndCreateDirIfNotExist(
                    expectedModels = emptyMap(),
                    utilityAssets = listOf(asset),
                )

            assertEquals(listOf(asset), result.missingModels)
            assertFalse(result.allValid)
        }

    @Test
    fun `scan treats installed SD15 CPU MNN pack directory as present`() =
        kotlinx.coroutines.test.runTest {
            val asset = createSd15CpuPackAsset(installArtifacts = true)

            val result =
                scanner.scanAndCreateDirIfNotExist(
                    expectedModels = emptyMap(),
                    utilityAssets = listOf(asset),
                )

            assertTrue(result.missingModels.isEmpty())
            assertTrue(result.allValid)
        }

    @Test
    fun `scan marks SD15 CPU MNN pack missing when extracted artifacts are absent`() =
        kotlinx.coroutines.test.runTest {
            val asset = createSd15CpuPackAsset(installArtifacts = false)

            val result =
                scanner.scanAndCreateDirIfNotExist(
                    expectedModels = emptyMap(),
                    utilityAssets = listOf(asset),
                )

            assertEquals(listOf(asset), result.missingModels)
            assertFalse(result.allValid)
        }

    private fun createAsset(
        filename: String,
        sizeBytes: Long,
        sha256: String = "unused-for-startup",
    ): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("1"),
                    huggingFaceModelName = "test/model",
                    remoteFileName = filename,
                    localFileName = filename,
                    sha256 = sha256,
                    sizeInBytes = sizeBytes,
                    modelFileFormat = ModelFileFormat.GGUF,
                ),
            configurations =
                listOf(
                    LocalModelConfiguration(
                        localModelId = LocalModelId("1"),
                        displayName = "Test",
                        maxTokens = 2048,
                        contextWindow = 4096,
                        temperature = 0.7,
                        topP = 0.9,
                        topK = 40,
                        repetitionPenalty = 1.1,
                        systemPrompt = "Test prompt",
                    ),
                ),
        )

    private fun createQnnPackAsset(
        filename: String,
        utilityType: UtilityType,
        packSubdir: String,
        installArtifacts: Boolean = true,
    ): LocalModelAsset {
        if (installArtifacts) {
            val packDir = File(modelsDir, packSubdir).apply { mkdirs() }
            val requiredFiles =
                when (utilityType) {
                    UtilityType.SDXL_QNN_PACK,
                    UtilityType.JUGGERNAUT_XL_QNN_PACK,
                    -> LocalImageGenerationPaths.REQUIRED_SDXL_QNN_FILES
                    UtilityType.SD15_QNN_PACK -> LocalImageGenerationPaths.REQUIRED_SD15_QNN_FILES
                    else -> LocalImageGenerationPaths.REQUIRED_SD15_QNN_FILES
                }
            requiredFiles.forEach { name ->
                File(packDir, name).writeText("stub")
            }
        }
        return LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("qnn-pack"),
                    huggingFaceModelName = "xororz/sdxl-qnn",
                    remoteFileName = filename,
                    localFileName = filename,
                    sha256 = "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83",
                    sizeInBytes = 100L,
                    modelFileFormat = ModelFileFormat.BIN,
                    source = DownloadSource.HUGGING_FACE,
                    utilityType = utilityType,
                ),
            configurations = emptyList(),
        )
    }

    private fun createSd15CpuPackAsset(installArtifacts: Boolean): LocalModelAsset {
        if (installArtifacts) {
            val packDir = File(modelsDir, LocalImageGenerationPaths.SD15_CPU_SUBDIR).apply { mkdirs() }
            listOf("unet.mnn", "vae_decoder.mnn", "tokenizer.json", "clip.mnn").forEach { name ->
                File(packDir, name).writeText("stub")
            }
        }
        return LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("sd15-cpu-pack"),
                    huggingFaceModelName = "xororz/sd15-mnn",
                    remoteFileName = "AnythingV5.zip",
                    localFileName = "AnythingV5.zip",
                    sha256 = "6183835e6b3f438a743ee16d4f929cb8bd2f20e50dc56d1a8eab0185e0734bbf",
                    sizeInBytes = 100L,
                    modelFileFormat = ModelFileFormat.BIN,
                    source = DownloadSource.HUGGING_FACE,
                    utilityType = UtilityType.SD15_QNN_PACK,
                ),
            configurations = emptyList(),
        )
    }
}
