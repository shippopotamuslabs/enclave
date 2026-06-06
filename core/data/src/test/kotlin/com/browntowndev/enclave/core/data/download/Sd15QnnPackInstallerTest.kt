package com.browntowndev.enclave.core.data.download

import android.content.Context
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.model.download.Sd15QnnPackCatalog
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Sd15QnnPackInstallerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun installIfNeeded_extractsZipIntoSd15QnnDir() =
        runTest {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val modelsDir = File(filesDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val variant = Sd15QnnPackCatalog.catalogTemplate
            val zipFile = File(modelsDir, variant.localZipFileName)
            writeMinimalPackZip(zipFile)

            val context = mockk<Context>(relaxed = true)
            every { context.getExternalFilesDir(null) } returns filesDir

            val installer =
                Sd15QnnPackInstaller(
                    context = context,
                    logger = mockk<LoggingPort>(relaxed = true),
                )
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("sd15-qnn-pack"),
                    huggingFaceModelName = Sd15QnnPackCatalog.HF_REPO,
                    remoteFileName = variant.zipFileName,
                    localFileName = variant.localZipFileName,
                    sha256 = variant.sha256,
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
    fun installIfNeeded_rejectsZipEntriesThatEscapePackDir() =
        runTest {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val modelsDir = File(filesDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            val variant = Sd15QnnPackCatalog.catalogTemplate
            val zipFile = File(modelsDir, variant.localZipFileName)
            writeEscapingPackZip(zipFile)

            val context = mockk<Context>(relaxed = true)
            every { context.getExternalFilesDir(null) } returns filesDir

            val installer =
                Sd15QnnPackInstaller(
                    context = context,
                    logger = mockk<LoggingPort>(relaxed = true),
                )
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("sd15-qnn-pack"),
                    huggingFaceModelName = Sd15QnnPackCatalog.HF_REPO,
                    remoteFileName = variant.zipFileName,
                    localFileName = variant.localZipFileName,
                    sha256 = variant.sha256,
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

            val context = mockk<Context>(relaxed = true)
            every { context.getExternalFilesDir(null) } returns filesDir

            val installer =
                Sd15QnnPackInstaller(
                    context = context,
                    logger = mockk<LoggingPort>(relaxed = true),
                )
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("sdxl-qnn-pack"),
                    huggingFaceModelName = "xororz/sdxl-qnn",
                    remoteFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                    localFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                    sha256 = "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83",
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

            val context = mockk<Context>(relaxed = true)
            every { context.getExternalFilesDir(null) } returns filesDir

            val installer =
                Sd15QnnPackInstaller(
                    context = context,
                    logger = mockk<LoggingPort>(relaxed = true),
                )
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId("sdxl-qnn-pack"),
                    huggingFaceModelName = "xororz/sdxl-qnn",
                    remoteFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                    localFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                    sha256 = "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83",
                    sizeInBytes = zipFile.length(),
                    modelFileFormat = ModelFileFormat.BIN,
                    utilityType = UtilityType.SDXL_QNN_PACK,
                )

            assertTrue(installer.installIfNeeded(metadata))
            assertFalse(zipFile.exists())
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
}
