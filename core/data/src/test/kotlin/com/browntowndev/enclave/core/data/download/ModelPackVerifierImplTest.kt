package com.browntowndev.enclave.core.data.download

import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.model.download.ModelPack
import com.browntowndev.enclave.domain.model.download.ModelPackMember
import com.browntowndev.enclave.domain.model.download.ModelPackRole
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.download.ModelPackVerificationDepth
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
import com.browntowndev.enclave.domain.port.download.HashingPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ModelPackVerifierImplTest {
    private lateinit var hashingPort: HashingPort
    private lateinit var verifier: ModelPackVerifierImpl
    private lateinit var appContext: android.content.Context

    @BeforeEach
    fun setup() {
        appContext = ApplicationProvider.getApplicationContext()
        hashingPort = mockk(relaxed = true)
        verifier = ModelPackVerifierImpl(appContext, hashingPort)
    }

    @Test
    @DisplayName("MPK-029 SIZE_ON_DISK verification does not compute SHA-256")
    fun mpk029_sizeOnlyVerificationSkipsSha256() =
        runTest {
            val fileName = "pack-member-test.gguf"
            val expectedSize = 16L
            val modelsDir = File(appContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
            modelsDir.mkdirs()
            val modelFile = File(modelsDir, fileName)
            modelFile.writeBytes(ByteArray(expectedSize.toInt()) { 0xAB.toByte() })

            every { hashingPort.calculateSha256(any()) } returns "definitely-not-matching"

            val pack = testPack(fileName, expectedSize)
            val result = verifier.verifyPack(pack, ModelPackVerificationDepth.SIZE_ON_DISK)

            assertTrue(result.isComplete)
            verify(exactly = 0) { hashingPort.calculateSha256(any()) }
        }

    @Test
    @DisplayName("MPK-030 SHA256_INTEGRITY rejects wrong hash when size matches")
    fun mpk030_sha256IntegrityRejectsWrongHash() =
        runTest {
            val fileName = "pack-member-integrity.gguf"
            val expectedSize = 8L
            val modelsDir = File(appContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
            modelsDir.mkdirs()
            File(modelsDir, fileName).writeBytes(ByteArray(expectedSize.toInt()))

            every { hashingPort.calculateSha256(any()) } returns "wrong-hash"

            val pack = testPack(fileName, expectedSize, sha256 = "expected-hash")
            val result = verifier.verifyPack(pack, ModelPackVerificationDepth.SHA256_INTEGRITY)

            assertFalse(result.isComplete)
            verify(exactly = 1) { hashingPort.calculateSha256(any()) }
        }

    @Test
    @DisplayName("SD15 QNN pack verifies installed artifact directory after zip deletion")
    fun sd15QnnPackVerificationUsesInstalledArtifactDirectory() =
        runTest {
            val modelsDir = File(appContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
            val packDir = File(modelsDir, LocalImageGenerationPaths.SD15_QNN_SUBDIR).apply { mkdirs() }
            listOf("clip_v2.mnn", "token_emb.bin", "pos_emb.bin", "unet.bin", "vae_decoder.bin", "tokenizer.json").forEach { name ->
                File(packDir, name).writeBytes(byteArrayOf(1))
            }

            val result = verifier.verifyPack(sd15QnnPack(), ModelPackVerificationDepth.SHA256_INTEGRITY)

            assertTrue(result.isComplete)
            verify(exactly = 0) { hashingPort.calculateSha256(any()) }
        }

    @Test
    fun sdxlQnnPackVerificationUsesInstalledArtifactDirectory() =
        runTest {
            val modelsDir = File(appContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
            val packDir = File(modelsDir, LocalImageGenerationPaths.SDXL_QNN_SUBDIR).apply { mkdirs() }
            listOf(
                "clip.mnn",
                "clip_2.mnn",
                "token_emb.bin",
                "token_emb_2.bin",
                "pos_emb.bin",
                "pos_emb_2.bin",
                "unet.bin",
                "vae_decoder.bin",
                "tokenizer.json",
            ).forEach { name ->
                File(packDir, name).writeBytes(byteArrayOf(1))
            }

            val result = verifier.verifyPack(sdxlQnnPack(), ModelPackVerificationDepth.SHA256_INTEGRITY)

            assertTrue(result.isComplete)
            verify(exactly = 0) { hashingPort.calculateSha256(any()) }
        }

    @Test
    fun sdxlQnnPackVerificationFailsIfIncomplete() =
        runTest {
            val modelsDir = File(appContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
            val packDir = File(modelsDir, LocalImageGenerationPaths.SDXL_QNN_SUBDIR).apply { mkdirs() }
            // Missing tokenizer.json
            listOf("clip.mnn", "clip_2.mnn", "token_emb.bin", "token_emb_2.bin", "pos_emb.bin", "pos_emb_2.bin", "unet.bin", "vae_decoder.bin").forEach { name ->
                File(packDir, name).writeBytes(byteArrayOf(1))
            }

            val result = verifier.verifyPack(sdxlQnnPack(), ModelPackVerificationDepth.SHA256_INTEGRITY)

            assertFalse(result.isComplete)
            assertFalse(LocalImageGenerationPaths.hasSdxlQnnArtifacts(packDir))
        }

    @Test
    fun sdxlFullPackZipOnlyDoesNotCountAsInstalled() =
        runTest {
            val modelsDir = File(appContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
            modelsDir.mkdirs()
            val zipFile = File(modelsDir, ModelPackTestFixtures.SDXL_QNN_PACK_FILE)
            zipFile.writeBytes(ByteArray(1024))

            val result = verifier.verifyPack(ModelPackTestFixtures.sdxlPack(), ModelPackVerificationDepth.SIZE_ON_DISK)

            assertFalse(result.isComplete)
            assertEquals(0, result.installedCount)
            assertEquals(2, result.totalCount)
        }

    @Test
    fun sdxlFullPackCountsBothMembersWhenArtifactsInstalled() =
        runTest {
            val modelsDir = File(appContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
            val packDir = File(modelsDir, LocalImageGenerationPaths.SDXL_QNN_SUBDIR).apply { mkdirs() }
            LocalImageGenerationPaths.REQUIRED_SDXL_QNN_FILES.forEach { name ->
                File(packDir, name).writeBytes(byteArrayOf(1))
            }

            val result = verifier.verifyPack(ModelPackTestFixtures.sdxlPack(), ModelPackVerificationDepth.SHA256_INTEGRITY)

            assertTrue(result.isComplete)
            assertEquals(2, result.installedCount)
        }

    private fun testPack(
        fileName: String,
        sizeInBytes: Long,
        sha256: String = "abc123",
    ): ModelPack {
        val asset =
            LocalModelAsset(
                metadata =
                    LocalModelMetadata(
                        id = LocalModelId("test"),
                        displayName = "Test",
                        huggingFaceModelName = "test/model",
                        remoteFileName = fileName,
                        localFileName = fileName,
                        sha256 = sha256,
                        sizeInBytes = sizeInBytes,
                        modelFileFormat = ModelFileFormat.GGUF,
                        source = DownloadSource.HUGGING_FACE,
                    ),
                configurations =
                    listOf(
                        LocalModelConfiguration(
                            id = com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId("cfg"),
                            localModelId = LocalModelId("test"),
                            displayName = "Test",
                            maxTokens = 0,
                            contextWindow = 0,
                            temperature = 0.0,
                            topP = 1.0,
                            topK = 1,
                            repetitionPenalty = 1.0,
                            systemPrompt = "",
                            defaultAssignments = listOf(ModelType.IMAGE_GENERATION),
                        ),
                    ),
            )
        return ModelPack(
            packId = "test-pack",
            displayName = "Test Pack",
            slot = ModelType.IMAGE_GENERATION,
            members = listOf(ModelPackMember(ModelPackRole.DIFFUSION, asset)),
        )
    }

    private fun sd15QnnPack(): ModelPack {
        val asset =
            LocalModelAsset(
                metadata =
                    LocalModelMetadata(
                        id = LocalModelId("sd15-qnn-pack"),
                        displayName = "SD 1.5 NPU weights",
                        huggingFaceModelName = "xororz/sd-qnn",
                        remoteFileName = "AnythingV5_qnn2.28_8gen2.zip",
                        localFileName = "AnythingV5_qnn2.28_8gen2.zip",
                        sha256 = "unused-after-install",
                        sizeInBytes = 1_057_820_237L,
                        modelFileFormat = ModelFileFormat.BIN,
                        source = DownloadSource.HUGGING_FACE,
                        utilityType = UtilityType.SD15_QNN_PACK,
                    ),
                configurations = emptyList(),
            )
        return ModelPack(
            packId = "sd15-t2i",
            displayName = "Stable Diffusion 1.5",
            slot = ModelType.IMAGE_GENERATION,
            members = listOf(ModelPackMember(ModelPackRole.TEXT_ENCODER, asset)),
        )
    }

    @Test
    @DisplayName("DL-008 ASG-001 SD15 SDXL and Juggernaut packs verify complete simultaneously")
    fun dl008_asg001_allThreeQnnPacksVerifyComplete() =
        runTest {
            val modelsDir = File(appContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
            val sd15Dir = File(modelsDir, LocalImageGenerationPaths.SD15_QNN_SUBDIR).apply { mkdirs() }
            listOf("clip_v2.mnn", "token_emb.bin", "pos_emb.bin", "unet.bin", "vae_decoder.bin", "tokenizer.json").forEach { name ->
                File(sd15Dir, name).writeBytes(byteArrayOf(1))
            }
            val sdxlDir = File(modelsDir, LocalImageGenerationPaths.SDXL_QNN_SUBDIR).apply { mkdirs() }
            LocalImageGenerationPaths.REQUIRED_SDXL_QNN_FILES.forEach { name ->
                File(sdxlDir, name).writeBytes(byteArrayOf(1))
            }
            val juggernautDir = File(modelsDir, LocalImageGenerationPaths.JUGGERNAUT_XL_QNN_SUBDIR).apply { mkdirs() }
            LocalImageGenerationPaths.REQUIRED_SDXL_QNN_FILES.forEach { name ->
                File(juggernautDir, name).writeBytes(byteArrayOf(1))
            }
            File(juggernautDir, "vae_encoder.bin").writeBytes(byteArrayOf(1))

            assertTrue(verifier.verifyPack(sd15QnnPack(), ModelPackVerificationDepth.SHA256_INTEGRITY).isComplete)
            assertTrue(verifier.verifyPack(sdxlQnnPack(), ModelPackVerificationDepth.SHA256_INTEGRITY).isComplete)
            assertTrue(verifier.verifyPack(juggernautQnnPack(), ModelPackVerificationDepth.SHA256_INTEGRITY).isComplete)
        }

    @Test
    @DisplayName("DL-017 Juggernaut IMAGE_EDIT pack requires vae_encoder.bin for completeness")
    fun dl017_juggernautImageEditPackRequiresVaeEncoder() =
        runTest {
            val modelsDir = File(appContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
            val juggernautDir = File(modelsDir, LocalImageGenerationPaths.JUGGERNAUT_XL_QNN_SUBDIR).apply { mkdirs() }
            LocalImageGenerationPaths.REQUIRED_SDXL_QNN_FILES.forEach { name ->
                File(juggernautDir, name).writeBytes(byteArrayOf(1))
            }

            assertFalse(verifier.verifyPack(juggernautQnnPack(), ModelPackVerificationDepth.SHA256_INTEGRITY).isComplete)
        }

    @Test
    @DisplayName("DL-018 Juggernaut completes when artifacts incl vae_encoder live in a nested subdir")
    fun dl018_juggernautCompletesWithNestedArtifacts() =
        runTest {
            val modelsDir = File(appContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
            // QNN packs may extract into a nested artifact folder; vae_encoder.bin sits there too.
            val nestedDir =
                File(
                    File(modelsDir, LocalImageGenerationPaths.JUGGERNAUT_XL_QNN_SUBDIR),
                    "juggernaut_qnn2.28_8gen3",
                ).apply { mkdirs() }
            LocalImageGenerationPaths.REQUIRED_SDXL_QNN_FILES.forEach { name ->
                File(nestedDir, name).writeBytes(byteArrayOf(1))
            }
            File(nestedDir, "vae_encoder.bin").writeBytes(byteArrayOf(1))

            assertTrue(verifier.verifyPack(juggernautQnnPack(), ModelPackVerificationDepth.SHA256_INTEGRITY).isComplete)
        }

    private fun juggernautQnnPack(): ModelPack {
        val asset =
            LocalModelAsset(
                metadata =
                    LocalModelMetadata(
                        id = LocalModelId("juggernaut-qnn-pack"),
                        displayName = "Juggernaut XL NPU weights",
                        huggingFaceModelName = "xororz/sdxl-qnn",
                        remoteFileName = "juggernaut_qnn2.28_8gen3.zip",
                        localFileName = "juggernaut_qnn2.28_8gen3.zip",
                        sha256 = "unused-after-install",
                        sizeInBytes = 3_747_687_306L,
                        modelFileFormat = ModelFileFormat.BIN,
                        source = DownloadSource.HUGGING_FACE,
                        utilityType = UtilityType.JUGGERNAUT_XL_QNN_PACK,
                    ),
                configurations = emptyList(),
            )
        return ModelPack(
            packId = ModelPackTestFixtures.JUGGERNAUT_PACK_ID,
            displayName = ModelPackTestFixtures.JUGGERNAUT_PACK_DISPLAY,
            slot = ModelType.IMAGE_EDIT,
            members = listOf(ModelPackMember(ModelPackRole.TEXT_ENCODER, asset)),
        )
    }

    private fun sdxlQnnPack(): ModelPack {
        val asset =
            LocalModelAsset(
                metadata =
                    LocalModelMetadata(
                        id = LocalModelId("sdxl-qnn-pack"),
                        displayName = "SDXL NPU weights",
                        huggingFaceModelName = "xororz/sdxl-qnn",
                        remoteFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                        localFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                        sha256 = "unused-after-install",
                        sizeInBytes = 3_504_485_672L,
                        modelFileFormat = ModelFileFormat.BIN,
                        source = DownloadSource.HUGGING_FACE,
                        utilityType = UtilityType.SDXL_QNN_PACK,
                    ),
                configurations = emptyList(),
            )
        return ModelPack(
            packId = "sdxl-t2i",
            displayName = "Stable Diffusion XL",
            slot = ModelType.IMAGE_GENERATION,
            members = listOf(ModelPackMember(ModelPackRole.TEXT_ENCODER, asset)),
        )
    }
}
