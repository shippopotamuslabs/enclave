package com.browntowndev.pocketcrew.domain.service

import com.browntowndev.pocketcrew.domain.port.HashingPort
import com.browntowndev.pocketcrew.domain.port.cache.ModelConfigCachePort
import com.browntowndev.pocketcrew.domain.model.WorkParserModelFile
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigProvider
import com.browntowndev.pocketcrew.domain.port.repository.RegisteredModel
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class FileIntegrityValidator @Inject constructor(
    private val modelConfigProvider: ModelConfigProvider,
    private val modelConfigCache: ModelConfigCachePort,
    private val hashingPort: HashingPort,
    private val logger: LoggingPort
) {
    companion object {
        private const val TAG = "FileIntegrityValidator"
    }
    /**
     * Verify that all required model files exist and have valid MD5.
     * This is the single source of truth for model file validation.
     *
     * @param requiredFiles List of files to verify. If empty, verifies all model configs from cache.
     *                      If non-empty, verifies only those specific files (requires md5 to be set).
     */
    suspend fun verifyModelsExist(
        requiredFiles: List<WorkParserModelFile> = emptyList()
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val modelsDir = modelConfigProvider.modelsDirectory

            if (!modelsDir.exists()) {
                return@withContext Result.failure(
                    IllegalStateException("Models directory does not exist: ${modelsDir.absolutePath}")
                )
            }

            // If no specific files provided, get all model configs from cache
            val filesToVerify = requiredFiles.ifEmpty {
                getAllModelFilesFromCache()
            }

            for (model in filesToVerify) {
                val file = File(modelsDir, model.localFileName)

                // DIAGNOSTIC: Log what we're checking
                logger.info(TAG, "[DIAGNOSTIC] verifyModelsExist: Checking file=${model.localFileName}, expectedMd5=${model.md5}, fileExists=${file.exists()}")

                // Check file exists and has content
                if (!file.exists() || file.length() == 0L) {
                    logger.error(TAG, "[DIAGNOSTIC] verifyModelsExist: File missing or empty: ${model.localFileName}, exists=${file.exists()}, length=${file.length()}")
                    return@withContext Result.failure(
                        IllegalStateException("Model file missing or empty: ${model.localFileName}")
                    )
                }

                // MD5 verification - required for full validation
                // Skip if md5 not provided (for backward compatibility)
                if (model.md5 != null) {
                    val actualMd5Base64 = hashingPort.calculateMd5(file)
                    if (actualMd5Base64 != model.md5) {
                        return@withContext Result.failure(
                            IllegalStateException(
                                "MD5 mismatch for ${model.localFileName}. " +
                                        "Expected (B64): ${model.md5}, Found (B64): $actualMd5Base64"
                            )
                        )
                    }
                }
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all model files from cache with their expected MD5 checksums.
     */
    private fun getAllModelFilesFromCache(): List<WorkParserModelFile> {
        if (!modelConfigCache.isInitialized()) {
            throw IllegalStateException("Model config cache not initialized")
        }

        val visionConfig = modelConfigCache.getVisionConfig()
        val draftConfig = modelConfigCache.getDraftConfig()
        val mainConfig = modelConfigCache.getMainConfig()
        val fastConfig = modelConfigCache.getFastConfig()

        val configs = listOfNotNull(visionConfig, draftConfig, mainConfig, fastConfig)

        val missingConfigs = mapOf(
            "Vision" to visionConfig,
            "Draft" to draftConfig,
            "Main" to mainConfig,
            "Fast" to fastConfig
        ).filterValues { it == null }.keys

        if (missingConfigs.isNotEmpty()) {
            throw IllegalStateException("The following model configs are missing: ${missingConfigs.joinToString(", ")}")
        }

        return configs.map { config ->
            val filename = getFilenameForConfig(config)
            WorkParserModelFile(
                localFileName = filename,
                sizeBytes = 0, // Not used in validation
                modelTypes = listOf(config.modelType),
                modelFileFormat = config.modelFileFormat,
                md5 = config.md5
            )
        }
    }

    /**
     * Get the expected filename for a registered model config.
     */
    private fun getFilenameForConfig(config: RegisteredModel): String {
        return "${config.modelType.name.lowercase()}.${config.modelFileFormat.extension.removePrefix(".")}"
    }
}
