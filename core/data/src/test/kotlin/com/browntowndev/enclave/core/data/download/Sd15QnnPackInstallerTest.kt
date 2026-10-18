package com.browntowndev.enclave.core.data.download

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.model.download.Sd15QnnPackCatalog
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
import com.browntowndev.enclave.domain.port.download.QnnPackInstallProgressListener
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class Sd15QnnPackInstallerTest {
    @TempDir
    lateinit var tempDir: File

    /** Robolectric context whose external files dir is the given @TempDir folder (ENC-28). */
    private fun fakeContext(filesDir: File): Context {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        return object : ContextWrapper(appContext) {
            override fun getExternalFilesDir(type: String?): File = filesDir
        }
    }

    private fun installer(filesDir: File): Sd15QnnPackInstaller =
        Sd15QnnPackInstaller(
            context = fakeContext(filesDir),
            logger = FakeLoggingPort(),
        )

    @Test
    fun installIfNeeded_extractsZipIntoSd15QnnDir() =
        runTest {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val modelsDir = File(filesDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val variant = Sd15QnnPackCatalog.catalogTemplate
            val zipFile = File(modelsDir, variant.localZipFileName)
            writeMinimalPackZip(zipFile)

            val installer = installer(filesDir)
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("sd15-qnn-pack"),
                    huggingFaceModelName = Sd15QnnPackCatalog.HF_REPO,
                    remoteFileName = variant.zipFileName,
                    localFileName = variant.localZipFileName,
                    sha256 = sha256Hex(zipFile),
                    sizeInBytes = zipFile.length(),
                    modelFileFormat = ModelFileFormat.BIN,
                    utilityType = UtilityType.SD15_QNN_PACK,
                )

            assertTrue(installer.installIfNeeded(metadata))
            val packDir = File(modelsDir, LocalImageGenerationPaths.SD15_QNN_SUBDIR)
            assertTrue(LocalImageGenerationPaths.hasSd15QnnArtifacts(packDir))
            assertTrue(!zipFile.exists())
        }

    @Test
    fun installIfNeeded_extractsMnnCpuZipIntoSd15CpuDir() =
        runTest {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val modelsDir = File(filesDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val zipFile = File(modelsDir, "AnythingV5.zip")
            // xororz CPU pack layout: single root folder + clip_v2 embedding variant.
            writePackZip(
                zipFile,
                listOf(
                    "AnythingV5/clip_v2.mnn",
                    "AnythingV5/token_emb.bin",
                    "AnythingV5/pos_emb.bin",
                    "AnythingV5/unet.mnn",
                    "AnythingV5/vae_decoder.mnn",
                    "AnythingV5/tokenizer.json",
                ),
            )

            val installer = installer(filesDir)
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("sd15-qnn-pack"),
                    huggingFaceModelName = Sd15QnnPackCatalog.HF_CPU_REPO,
                    remoteFileName = "AnythingV5.zip",
                    localFileName = "AnythingV5.zip",
                    sha256 = sha256Hex(zipFile),
                    sizeInBytes = zipFile.length(),
                    modelFileFormat = ModelFileFormat.BIN,
                    utilityType = UtilityType.SD15_QNN_PACK,
                )

            assertTrue(installer.installIfNeeded(metadata))
            val cpuDir = File(modelsDir, LocalImageGenerationPaths.SD15_CPU_SUBDIR)
            assertTrue(LocalImageGenerationPaths.hasMnnArtifacts(cpuDir))
            assertFalse(File(modelsDir, LocalImageGenerationPaths.SD15_QNN_SUBDIR).exists())
            assertFalse(zipFile.exists())
        }

    @Test
    fun installIfNeeded_rejectsZipEntriesThatEscapePackDir() =
        runTest {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val modelsDir = File(filesDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val variant = Sd15QnnPackCatalog.catalogTemplate
            val zipFile = File(modelsDir, variant.localZipFileName)
            writeEscapingPackZip(zipFile)

            val installer = installer(filesDir)
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("sd15-qnn-pack"),
                    huggingFaceModelName = Sd15QnnPackCatalog.HF_REPO,
                    remoteFileName = variant.zipFileName,
                    localFileName = variant.localZipFileName,
                    sha256 = sha256Hex(zipFile),
                    sizeInBytes = zipFile.length(),
                    modelFileFormat = ModelFileFormat.BIN,
                    utilityType = UtilityType.SD15_QNN_PACK,
                )

            assertFalse(installer.installIfNeeded(metadata))
            assertFalse(File(modelsDir, "evil.txt").exists())
        }

    @Test
    fun installIfNeeded_extractsSdxlZipIntoSdxlQnnDir() =
        runTest {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val modelsDir = File(filesDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val zipFile = File(modelsDir, "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip")
            writeMinimalSdxlPackZip(zipFile)

            val installer = installer(filesDir)
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("sdxl-qnn-pack"),
                    huggingFaceModelName = "xororz/sdxl-qnn",
                    remoteFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                    localFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                    sha256 = sha256Hex(zipFile),
                    sizeInBytes = zipFile.length(),
                    modelFileFormat = ModelFileFormat.BIN,
                    utilityType = UtilityType.SDXL_QNN_PACK,
                )

            assertTrue(installer.installIfNeeded(metadata))
            val packDir = File(modelsDir, "sdxl-qnn")
            assertTrue(LocalImageGenerationPaths.hasSdxlQnnArtifacts(packDir))
        }

    @Test
    fun installIfNeeded_deletesSdxlZipAfterVerification() =
        runTest {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val modelsDir = File(filesDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val zipFile = File(modelsDir, "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip")
            writeMinimalSdxlPackZip(zipFile)

            val installer = installer(filesDir)
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("sdxl-qnn-pack"),
                    huggingFaceModelName = "xororz/sdxl-qnn",
                    remoteFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                    localFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                    sha256 = sha256Hex(zipFile),
                    sizeInBytes = zipFile.length(),
                    modelFileFormat = ModelFileFormat.BIN,
                    utilityType = UtilityType.SDXL_QNN_PACK,
                )

            assertTrue(installer.installIfNeeded(metadata))
            assertFalse(zipFile.exists())
        }

    @Test
    fun `DL-010 Juggernaut extraction installs under juggernaut-qnn`() =
        runTest {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val modelsDir = File(filesDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val zipFile = File(modelsDir, "juggernaut_qnn2.28_8gen3.zip")
            writeMinimalSdxlPackZip(zipFile)
            val sdxlDirBefore = File(modelsDir, LocalImageGenerationPaths.SDXL_QNN_SUBDIR)

            val installer = installer(filesDir)
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("juggernaut-qnn-pack"),
                    huggingFaceModelName = "xororz/sdxl-qnn",
                    remoteFileName = "juggernaut_qnn2.28_8gen3.zip",
                    localFileName = "juggernaut_qnn2.28_8gen3.zip",
                    sha256 = sha256Hex(zipFile),
                    sizeInBytes = zipFile.length(),
                    modelFileFormat = ModelFileFormat.BIN,
                    utilityType = UtilityType.JUGGERNAUT_XL_QNN_PACK,
                )

            assertTrue(installer.installIfNeeded(metadata))
            val packDir = File(modelsDir, LocalImageGenerationPaths.JUGGERNAUT_XL_QNN_SUBDIR)
            assertTrue(LocalImageGenerationPaths.hasJuggernautXlQnnArtifacts(packDir))
            assertFalse(sdxlDirBefore.exists())
        }

    @Test
    fun `DL-011 Juggernaut zip deletes only after required artifacts verify`() =
        runTest {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val modelsDir = File(filesDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val zipFile = File(modelsDir, "juggernaut_qnn2.28_8gen3.zip")
            writeMinimalSdxlPackZip(zipFile)

            val installer = installer(filesDir)
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("juggernaut-qnn-pack"),
                    huggingFaceModelName = "xororz/sdxl-qnn",
                    remoteFileName = "juggernaut_qnn2.28_8gen3.zip",
                    localFileName = "juggernaut_qnn2.28_8gen3.zip",
                    sha256 = sha256Hex(zipFile),
                    sizeInBytes = zipFile.length(),
                    modelFileFormat = ModelFileFormat.BIN,
                    utilityType = UtilityType.JUGGERNAUT_XL_QNN_PACK,
                )

            assertTrue(installer.installIfNeeded(metadata))
            assertFalse(zipFile.exists())
            val packDir = File(modelsDir, LocalImageGenerationPaths.JUGGERNAUT_XL_QNN_SUBDIR)
            assertTrue(LocalImageGenerationPaths.hasJuggernautXlQnnArtifacts(packDir))
        }

    @Test
    fun `DL-012 incomplete Juggernaut extraction stays incomplete`() =
        runTest {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val modelsDir = File(filesDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val zipFile = File(modelsDir, "juggernaut_qnn2.28_8gen3.zip")
            val incompleteFiles =
                LocalImageGenerationPaths.REQUIRED_SDXL_QNN_FILES.filter { it != "tokenizer.json" }
            writePackZip(zipFile, incompleteFiles)

            val installer = installer(filesDir)
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("juggernaut-qnn-pack"),
                    huggingFaceModelName = "xororz/sdxl-qnn",
                    remoteFileName = "juggernaut_qnn2.28_8gen3.zip",
                    localFileName = "juggernaut_qnn2.28_8gen3.zip",
                    sha256 = sha256Hex(zipFile),
                    sizeInBytes = zipFile.length(),
                    modelFileFormat = ModelFileFormat.BIN,
                    utilityType = UtilityType.JUGGERNAUT_XL_QNN_PACK,
                )

            assertFalse(installer.installIfNeeded(metadata))
            val packDir = File(modelsDir, LocalImageGenerationPaths.JUGGERNAUT_XL_QNN_SUBDIR)
            assertFalse(LocalImageGenerationPaths.hasJuggernautXlQnnArtifacts(packDir))
        }

    @Test
    fun `MUT-006 Juggernaut install must not extract into sdxl-qnn`() =
        runTest {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val modelsDir = File(filesDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val zipFile = File(modelsDir, "juggernaut_qnn2.28_8gen3.zip")
            writeMinimalSdxlPackZip(zipFile)

            val installer = installer(filesDir)
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("juggernaut-qnn-pack"),
                    huggingFaceModelName = "xororz/sdxl-qnn",
                    remoteFileName = "juggernaut_qnn2.28_8gen3.zip",
                    localFileName = "juggernaut_qnn2.28_8gen3.zip",
                    sha256 = sha256Hex(zipFile),
                    sizeInBytes = zipFile.length(),
                    modelFileFormat = ModelFileFormat.BIN,
                    utilityType = UtilityType.JUGGERNAUT_XL_QNN_PACK,
                )

            installer.installIfNeeded(metadata)

            val juggernautDir = File(modelsDir, LocalImageGenerationPaths.JUGGERNAUT_XL_QNN_SUBDIR)
            assertEquals(juggernautDir.absolutePath, juggernautDir.absolutePath)
            assertTrue(LocalImageGenerationPaths.hasJuggernautXlQnnArtifacts(juggernautDir))
            val sdxlDir = File(modelsDir, LocalImageGenerationPaths.SDXL_QNN_SUBDIR)
            assertFalse(LocalImageGenerationPaths.hasSdxlQnnArtifacts(sdxlDir))
        }

    @Test
    fun installIfNeeded_reportsIncrementalProgressDuringLargeEntry() =
        runTest {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val modelsDir = File(filesDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val variant = Sd15QnnPackCatalog.catalogTemplate
            val zipFile = File(modelsDir, variant.localZipFileName)
            // unet.bin is multi-megabyte so a single entry spans several progress intervals.
            val largeEntryBytes = 20 * 1024 * 1024
            writePackZipWithLargeUnet(zipFile, largeEntryBytes)

            val installer = installer(filesDir)
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("sd15-qnn-pack"),
                    huggingFaceModelName = Sd15QnnPackCatalog.HF_REPO,
                    remoteFileName = variant.zipFileName,
                    localFileName = variant.localZipFileName,
                    sha256 = sha256Hex(zipFile),
                    sizeInBytes = zipFile.length(),
                    modelFileFormat = ModelFileFormat.BIN,
                    utilityType = UtilityType.SD15_QNN_PACK,
                )

            val progressBytes = mutableListOf<Long>()
            val listener = QnnPackInstallProgressListener { extracted, _ -> progressBytes.add(extracted) }

            assertTrue(installer.installIfNeeded(metadata, listener))

            val total = progressBytes.last()
            // Pre-fix, a single entry reported once (at its boundary); now it reports mid-extraction.
            val midReports = progressBytes.filter { it < total }.distinct()
            assertTrue(midReports.size >= 2, "expected >=2 mid-extraction reports, got $progressBytes")
            assertEquals(progressBytes.sorted(), progressBytes, "progress must be monotonically increasing")
        }

    private fun writePackZipWithLargeUnet(
        zipFile: File,
        unetBytes: Int,
    ) {
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            LocalImageGenerationPaths.REQUIRED_SD15_QNN_FILES.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                if (name == "unet.bin") {
                    zip.write(ByteArray(unetBytes))
                } else {
                    zip.write(byteArrayOf(1, 2, 3))
                }
                zip.closeEntry()
            }
        }
    }

    private fun writeMinimalSdxlPackZip(zipFile: File) {
        writePackZip(zipFile, LocalImageGenerationPaths.REQUIRED_SDXL_QNN_FILES)
    }

    private fun writeMinimalPackZip(zipFile: File) {
        writePackZip(zipFile, LocalImageGenerationPaths.REQUIRED_SD15_QNN_FILES)
    }

    private fun writePackZip(
        zipFile: File,
        artifactNames: List<String>,
    ) {
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            artifactNames.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
        }
    }

    private fun writeEscapingPackZip(zipFile: File) {
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../evil.txt"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
