package com.browntowndev.enclave.core.data.download

import android.content.Context
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.model.download.SdxlQnnPackCatalog
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
import com.browntowndev.enclave.domain.port.download.HashingPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UtilityModelFileResolver
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val utilityModelRepository: UtilityModelRepositoryPort,
        private val hashingPort: HashingPort,
    ) : UtilityModelFilePort {
        override suspend fun resolveUtilityModelPath(utilityType: UtilityType): String? =
            withContext(Dispatchers.IO) {
                val metadata = utilityModelRepository.getByUtilityType(utilityType) ?: return@withContext null
                val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
                if (utilityType == UtilityType.SD15_QNN_PACK) {
                    val packDir = File(modelsDir, LocalImageGenerationPaths.SD15_QNN_SUBDIR)
                    return@withContext packDir.takeIf(LocalImageGenerationPaths::hasSd15QnnArtifacts)?.absolutePath
                }
                if (utilityType == UtilityType.SDXL_QNN_PACK) {
                    val packDir = File(modelsDir, LocalImageGenerationPaths.SDXL_QNN_SUBDIR)
                    return@withContext packDir.takeIf(LocalImageGenerationPaths::hasSdxlQnnArtifacts)?.absolutePath
                }
                val file = safeFile(modelsDir, metadata.localFileName)
                if (file.matches(metadata.sizeInBytes, metadata.sha256)) {
                    file.absolutePath
                } else {
                    null
                }
            }

        override suspend fun resolveLocalModelPath(metadata: LocalModelMetadata): String? =
            withContext(Dispatchers.IO) {
                if (metadata.localFileName.isBlank()) return@withContext null
                val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
                SdxlQnnPackCatalog.findBySha256(metadata.sha256)?.let {
                    val packDir = File(modelsDir, LocalImageGenerationPaths.SDXL_QNN_SUBDIR)
                    packDir
                        .takeIf(LocalImageGenerationPaths::hasSdxlQnnArtifacts)
                        ?.absolutePath
                        ?.let { return@withContext it }
                }
                val file = safeFile(modelsDir, metadata.localFileName)
                if (file.matches(metadata.sizeInBytes, metadata.sha256)) {
                    file.absolutePath
                } else {
                    null
                }
            }

        private fun isSafeChildOf(
            child: File,
            parent: File,
        ): Boolean {
            val childCanonical = child.canonicalPath
            val parentCanonical = parent.canonicalPath + File.separator
            return childCanonical.startsWith(parentCanonical)
        }

        private fun safeFile(
            parentDir: File,
            filename: String,
        ): File {
            require(filename.isNotBlank()) { "Utility model filename must not be blank." }

            val safeName = File(filename).name
            val resolvedFile = File(parentDir, safeName)
            require(isSafeChildOf(resolvedFile, parentDir)) {
                "Resolved utility model path escapes model directory."
            }
            return resolvedFile
        }

        private fun File.matches(
            expectedSizeInBytes: Long,
            expectedSha256: String,
        ): Boolean =
            try {
                exists() &&
                    length() == expectedSizeInBytes &&
                    hashingPort.calculateSha256(absolutePath).equals(expectedSha256, ignoreCase = true)
            } catch (_: Exception) {
                false
            }
    }
