package com.browntowndev.enclave.core.data.download

import android.content.Context
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.model.download.Sd15QnnPackCatalog
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Sd15QnnPackInstaller
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val logger: LoggingPort,
    ) {
        companion object {
            private const val TAG = "Sd15QnnPackInstaller"
        }

        suspend fun installIfNeeded(metadata: LocalModelMetadata): Boolean =
            withContext(Dispatchers.IO) {
                if (metadata.utilityType != UtilityType.SD15_QNN_PACK) {
                    return@withContext false
                }
                val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
                val packDir = File(modelsDir, LocalImageGenerationPaths.SD15_QNN_SUBDIR)
                if (LocalImageGenerationPaths.hasQnnArtifacts(packDir)) {
                    logger.debug(TAG, "QNN pack already present at ${packDir.absolutePath}")
                    deleteZipIfPresent(modelsDir, metadata.localFileName)
                    return@withContext true
                }
                val zipFile = File(modelsDir, metadata.localFileName)
                if (!zipFile.isFile) {
                    logger.warning(TAG, "QNN pack zip missing: ${zipFile.absolutePath}")
                    return@withContext false
                }
                val variant = Sd15QnnPackCatalog.findBySha256(metadata.sha256)
                if (variant == null) {
                    logger.warning(TAG, "Unknown SD15 QNN pack SHA: ${metadata.sha256}")
                    return@withContext false
                }
                val extracted =
                    runCatching {
                        extractZipToPackDir(zipFile, packDir)
                    }.onFailure { error ->
                        logger.error(TAG, "Failed to extract QNN pack: ${error.message}", error)
                    }.isSuccess
                if (!extracted) {
                    return@withContext false
                }
                val ready = LocalImageGenerationPaths.hasQnnArtifacts(packDir)
                if (ready) {
                    deleteZipIfPresent(modelsDir, metadata.localFileName)
                    logger.info(TAG, "Installed SD15 QNN pack to ${packDir.absolutePath}")
                } else {
                    logger.error(TAG, "QNN pack extract incomplete under ${packDir.absolutePath}")
                }
                ready
            }

        private fun extractZipToPackDir(
            zipFile: File,
            packDir: File,
        ) {
            val staging = File(packDir.parentFile, "${packDir.name}.extracting")
            if (staging.exists()) {
                staging.deleteRecursively()
            }
            staging.mkdirs()
            zipFile.inputStream().buffered().use { input ->
                unzipToDirectory(input, staging)
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

        private fun unzipToDirectory(
            input: InputStream,
            targetDir: File,
        ) {
            ZipInputStream(input).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val outFile = resolveZipEntry(targetDir, entry)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out ->
                            zip.copyTo(out)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

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
