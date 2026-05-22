package com.browntowndev.enclave.core.data.download

import android.content.Context
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.ModelConfig
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
    ) : UtilityModelFilePort {
        override suspend fun resolveUtilityModelPath(utilityType: UtilityType): String? =
            withContext(Dispatchers.IO) {
                val metadata = utilityModelRepository.getByUtilityType(utilityType) ?: return@withContext null
                val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
                val file = safeFile(modelsDir, metadata.localFileName)
                if (file.exists() && file.length() == metadata.sizeInBytes) {
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
    }
