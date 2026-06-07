package com.browntowndev.enclave.core.data.download

import android.content.Context
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.model.download.JuggernautXlQnnPackCatalog
import com.browntowndev.enclave.domain.model.download.Sd15QnnPackCatalog
import com.browntowndev.enclave.domain.model.download.SdxlQnnPackCatalog
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
import com.browntowndev.enclave.domain.port.download.QnnPackInstallerPort
import com.browntowndev.enclave.domain.port.download.QnnPackInstallProgressListener
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Sd15QnnPackInstaller
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val logger: LoggingPort,
    ) : QnnPackInstallerPort {
        companion object {
            private const val TAG = "Sd15QnnPackInstaller"
            private const val PROGRESS_REPORT_INTERVAL_BYTES = 8 * 1024 * 1024L
            private const val COPY_BUFFER_BYTES = 1 * 1024 * 1024
        }

        override suspend fun installIfNeeded(
            metadata: LocalModelMetadata,
            progressListener: QnnPackInstallProgressListener?,
        ): Boolean =
            withContext(Dispatchers.IO) {
                when (metadata.utilityType) {
                    UtilityType.SD15_QNN_PACK ->
                        installQnnPack(
                            metadata = metadata,
                            packSubdir = LocalImageGenerationPaths.SD15_QNN_SUBDIR,
                            variantLookup = Sd15QnnPackCatalog::findBySha256,
                            label = "SD15",
                            progressListener = progressListener,
                            artifactsPresent = LocalImageGenerationPaths::hasSd15QnnArtifacts,
                        )
                    UtilityType.SDXL_QNN_PACK ->
                        installQnnPack(
                            metadata = metadata,
                            packSubdir = LocalImageGenerationPaths.SDXL_QNN_SUBDIR,
                            variantLookup = SdxlQnnPackCatalog::findBySha256,
                            label = "SDXL",
                            progressListener = progressListener,
                            artifactsPresent = LocalImageGenerationPaths::hasSdxlQnnArtifacts,
                        )
                    UtilityType.JUGGERNAUT_XL_QNN_PACK ->
                        installQnnPack(
                            metadata = metadata,
                            packSubdir = LocalImageGenerationPaths.JUGGERNAUT_XL_QNN_SUBDIR,
                            variantLookup = JuggernautXlQnnPackCatalog::findBySha256,
                            label = "Juggernaut XL",
                            progressListener = progressListener,
                            artifactsPresent = LocalImageGenerationPaths::hasJuggernautXlQnnArtifacts,
                        )
                    else -> false
                }
            }

        private suspend fun installQnnPack(
            metadata: LocalModelMetadata,
            packSubdir: String,
            variantLookup: (String) -> Any?,
            label: String,
            progressListener: QnnPackInstallProgressListener?,
            artifactsPresent: (File) -> Boolean,
        ): Boolean {
            val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
            val packDir = File(modelsDir, packSubdir)
            if (artifactsPresent(packDir)) {
                logger.debug(TAG, "$label QNN pack already present at ${packDir.absolutePath}")
                deleteZipIfPresent(modelsDir, metadata.localFileName)
                progressListener?.onProgress(metadata.sizeInBytes, metadata.sizeInBytes)
                return true
            }
            val zipFile = File(modelsDir, metadata.localFileName)
            if (!zipFile.isFile) {
                logger.warning(TAG, "$label QNN pack zip missing: ${zipFile.absolutePath}")
                return false
            }
            if (variantLookup(metadata.sha256) == null) {
                logger.warning(TAG, "Unknown $label QNN pack SHA: ${metadata.sha256}")
                return false
            }
            val totalBytes = estimateUncompressedZipBytes(zipFile).coerceAtLeast(zipFile.length())
            val extracted =
                runCatching {
                    extractZipToPackDir(
                        zipFile = zipFile,
                        packDir = packDir,
                        totalBytes = totalBytes,
                        progressListener = progressListener,
                    )
                }.onFailure { error ->
                    logger.error(TAG, "Failed to extract $label QNN pack: ${error.message}", error)
                }.isSuccess
            if (!extracted) {
                return false
            }
            val ready = artifactsPresent(packDir)
            if (ready) {
                deleteZipIfPresent(modelsDir, metadata.localFileName)
                logger.info(TAG, "Installed $label QNN pack to ${packDir.absolutePath}")
                progressListener?.onProgress(totalBytes, totalBytes)
            } else {
                logger.error(TAG, "$label QNN pack extract incomplete under ${packDir.absolutePath}")
            }
            return ready
        }

        private suspend fun extractZipToPackDir(
            zipFile: File,
            packDir: File,
            totalBytes: Long,
            progressListener: QnnPackInstallProgressListener?,
        ) {
            val staging = File(packDir.parentFile, "${packDir.name}.extracting")
            if (staging.exists()) {
                staging.deleteRecursively()
            }
            staging.mkdirs()
            zipFile.inputStream().buffered().use { input ->
                unzipToDirectory(
                    input = input,
                    targetDir = staging,
                    totalBytes = totalBytes,
                    progressListener = progressListener,
                )
            }
            val source = flattenSingleRootDirectory(staging)
            if (packDir.exists()) {
                packDir.deleteRecursively()
            }
            packDir.parentFile?.mkdirs()
            if (!source.renameTo(packDir)) {
                source.copyRecursively(packDir, overwrite = true)
                source.deleteRecursively()
            }
            staging.deleteRecursively()
        }

        private fun flattenSingleRootDirectory(dir: File): File {
            val children = dir.listFiles()?.filter { it.name != "__MACOSX" }.orEmpty()
            if (children.size == 1 && children[0].isDirectory) {
                return children[0]
            }
            return dir
        }

        private suspend fun unzipToDirectory(
            input: InputStream,
            targetDir: File,
            totalBytes: Long,
            progressListener: QnnPackInstallProgressListener?,
        ) {
            val progress = ZipExtractionProgress()
            ZipInputStream(input).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    writeZipEntry(zip, targetDir, entry, totalBytes, progressListener, progress)
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            progressListener?.onProgress(progress.extractedBytes.coerceAtMost(totalBytes), totalBytes)
        }

        private suspend fun writeZipEntry(
            zip: ZipInputStream,
            targetDir: File,
            entry: ZipEntry,
            totalBytes: Long,
            progressListener: QnnPackInstallProgressListener?,
            progress: ZipExtractionProgress,
        ) {
            val outFile = resolveZipEntry(targetDir, entry)
            if (entry.isDirectory) {
                outFile.mkdirs()
                return
            }
            outFile.parentFile?.mkdirs()
            outFile.outputStream().buffered().use { out ->
                copyZipEntryBytes(zip, out, totalBytes, progressListener, progress)
            }
        }

        private suspend fun copyZipEntryBytes(
            zip: ZipInputStream,
            out: OutputStream,
            totalBytes: Long,
            progressListener: QnnPackInstallProgressListener?,
            progress: ZipExtractionProgress,
        ) {
            // Copy in chunks so a single large entry (e.g. multi-GB unet.bin)
            // reports incremental progress instead of jumping at entry boundaries.
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                progress.extractedBytes += read
                reportExtractionProgress(progressListener, progress, totalBytes)
            }
        }

        private suspend fun reportExtractionProgress(
            progressListener: QnnPackInstallProgressListener?,
            progress: ZipExtractionProgress,
            totalBytes: Long,
        ) {
            if (progressListener == null) return
            if (progress.extractedBytes - progress.lastReportedBytes < PROGRESS_REPORT_INTERVAL_BYTES) return
            progressListener.onProgress(progress.extractedBytes.coerceAtMost(totalBytes), totalBytes)
            progress.lastReportedBytes = progress.extractedBytes
        }

        private class ZipExtractionProgress(
            var extractedBytes: Long = 0L,
            var lastReportedBytes: Long = 0L,
        )

        private fun estimateUncompressedZipBytes(zipFile: File): Long =
            runCatching {
                ZipFile(zipFile).use { zip ->
                    zip.entries().asSequence().sumOf { entry -> entry.size.coerceAtLeast(0L) }
                }
            }.getOrDefault(zipFile.length())

        private fun resolveZipEntry(
            targetDir: File,
            entry: ZipEntry,
        ): File {
            val outFile = File(targetDir, entry.name)
            val targetCanonical = targetDir.canonicalPath + File.separator
            val outCanonical = outFile.canonicalPath
            require(outCanonical.startsWith(targetCanonical)) {
                "Zip entry escapes target directory: ${entry.name}"
            }
            return outFile
        }

        private fun deleteZipIfPresent(
            modelsDir: File,
            localZipFileName: String,
        ) {
            val zip = File(modelsDir, localZipFileName)
            if (zip.isFile) {
                zip.delete()
            }
        }
    }
