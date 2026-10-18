package com.browntowndev.enclave.core.data.download

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ModelFileScannerTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var modelsDir: File
    private lateinit var scanner: ModelFileScanner

    @BeforeEach
    fun setup() {
        // Robolectric context whose external files dir is the @TempDir sandbox;
        // android.util.Log is shadowed by Robolectric (no static mocking needed).
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        context =
            object : ContextWrapper(appContext) {
                override fun getExternalFilesDir(type: String?): File = tempDir
            }

        modelsDir = File(tempDir, ModelConfig.MODELS_DIR)
        modelsDir.deleteRecursively()
        modelsDir.mkdirs()

        scanner =
            ModelFileScanner(
                context = context,
                localModelRepository = FakeLocalModelRepository(),
                activeModelProvider = FakeActiveModelProvider(),
            )
    }

    @AfterEach
    fun tearDown() {
        modelsDir.deleteRecursively()
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

    @Test
    fun `scan accepts both safety classifiers when stored at disambiguated paths`() =
        kotlinx.coroutines.test.runTest {
            val imageAsset = createSafetyClassifierAsset(
                localFileName = "image_safety_classifier_model_quantized.onnx",
                sizeBytes = 4L,
            )
            val promptAsset = createSafetyClassifierAsset(
                localFileName = "nsfw_text_classifier_model_quantized.onnx",
                sizeBytes = 8L,
                utilityType = UtilityType.PROMPT_SAFETY_CLASSIFIER,
            )
            File(modelsDir, imageAsset.metadata.localFileName).writeBytes(ByteArray(4))
            File(modelsDir, promptAsset.metadata.localFileName).writeBytes(ByteArray(8))

            val result =
                scanner.scanAndCreateDirIfNotExist(
                    expectedModels = emptyMap(),
                    utilityAssets = listOf(imageAsset, promptAsset),
                )

            assertTrue(result.missingModels.isEmpty())
            assertTrue(result.invalidModels.isEmpty())
            assertTrue(result.allValid)
        }

    @Test
    fun `scan removes orphaned legacy collision file before validating assets`() =
        kotlinx.coroutines.test.runTest {
            val imageAsset = createSafetyClassifierAsset(
                localFileName = "image_safety_classifier_model_quantized.onnx",
                sizeBytes = 4L,
            )
            val promptAsset = createSafetyClassifierAsset(
                localFileName = "nsfw_text_classifier_model_quantized.onnx",
                sizeBytes = 8L,
                utilityType = UtilityType.PROMPT_SAFETY_CLASSIFIER,
            )
            File(modelsDir, "model_quantized.onnx").writeBytes(ByteArray(16))
            File(modelsDir, imageAsset.metadata.localFileName).writeBytes(ByteArray(4))
            File(modelsDir, promptAsset.metadata.localFileName).writeBytes(ByteArray(8))

            val result =
                scanner.scanAndCreateDirIfNotExist(
                    expectedModels = emptyMap(),
                    utilityAssets = listOf(imageAsset, promptAsset),
                )

            assertFalse(File(modelsDir, "model_quantized.onnx").exists())
            assertTrue(result.missingModels.isEmpty())
            assertTrue(result.allValid)
        }

    private fun createSafetyClassifierAsset(
        localFileName: String,
        sizeBytes: Long,
        utilityType: UtilityType = UtilityType.IMAGE_SAFETY_CLASSIFIER,
    ): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("safety-$localFileName"),
                    huggingFaceModelName = "",
                    huggingFacePath =
                        when (utilityType) {
                            UtilityType.PROMPT_SAFETY_CLASSIFIER -> "nsfw_text_classifier"
                            else -> "image_safety_classifier"
                        },
                    remoteFileName = "model_quantized.onnx",
                    localFileName = localFileName,
                    sha256 = "sha-$localFileName",
                    sizeInBytes = sizeBytes,
                    modelFileFormat = ModelFileFormat.ONNX,
                    source = DownloadSource.CLOUDFLARE_R2,
                    utilityType = utilityType,
                ),
            configurations = emptyList(),
        )
}
