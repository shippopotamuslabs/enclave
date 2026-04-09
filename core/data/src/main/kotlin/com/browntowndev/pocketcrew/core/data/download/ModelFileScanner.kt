package com.browntowndev.pocketcrew.core.data.download

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.ModelFileScannerPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelFileScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val localModelRepository: com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort,
    private val activeModelProvider: ActiveModelProviderPort
) : ModelFileScannerPort {
    companion object {
        private const val TAG = "ModelFileScanner"
    }

    /**
     * Scan the models directory and create it if it doesn't exist.
     * Validates expected models against physical files on disk (existence and size).
     *
     * @param expectedModels Map of model types to assets expected from remote config (from cache)
     */
    override suspend fun scanAndCreateDirIfNotExist(
        expectedModels: Map<ModelType, LocalModelAsset>
    ): ModelScanResult = withContext(Dispatchers.IO) {
        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)

        if (!modelsDir.exists()) {
            val created = modelsDir.mkdirs()
            if (!created && !modelsDir.exists()) {
                Log.e(TAG, "Failed to create models directory")
                return@withContext ModelScanResult(
                    missingModels = expectedModels.values.toList(),
                    partialDownloads = emptyMap(),
                    allValid = false,
                    directoryError = true
                )
            }
        }

        val missingModels = mutableListOf<LocalModelAsset>()
        val partialDownloads = mutableMapOf<String, Long>()
        val invalidModels = mutableListOf<LocalModelAsset>()

        // Iterate over expected models (what we want to have)
        for ((_, expectedAsset) in expectedModels) {
            val filename = expectedAsset.metadata.localFileName

            val file = File(modelsDir, filename)
            val tempFile = File(modelsDir, "${filename}${ModelConfig.TEMP_EXTENSION}")
            val metaFile = File(modelsDir, "${filename}${ModelConfig.TEMP_META_EXTENSION}")

            val fileExists = file.exists()
            val tempExists = tempFile.exists()
            val tempLength = if (tempExists) tempFile.length() else 0L

            when {
                // A temp file means a previous download did not complete cleanly.
                tempExists && tempLength > 0 -> {
                    var isValidPartial = false
                    if (metaFile.exists()) {
                        try {
                            val metaLines = metaFile.readLines()
                            if (metaLines.size >= 2) {
                                val expectedSize = metaLines[0].toLongOrNull()
                                val expectedSha256 = metaLines[1]
                                
                                if (expectedSize == expectedAsset.metadata.sizeInBytes &&
                                    expectedSha256 == expectedAsset.metadata.sha256 &&
                                    tempLength < expectedSize) {
                                    isValidPartial = true
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to read meta file for $filename", e)
                        }
                    }

                    if (isValidPartial) {
                        partialDownloads[filename] = tempLength
                        Log.d(TAG, "Model $filename has valid partial download")
                    } else {
                        Log.w(TAG, "Model $filename has invalid/stale partial download. Deleting.")
                        tempFile.delete()
                        metaFile.delete()
                        missingModels.add(expectedAsset)
                    }
                }
                fileExists && file.length() == expectedAsset.metadata.sizeInBytes -> {
                    Log.d(TAG, "Model $filename is valid (size matches)")
                }
                fileExists -> {
                    invalidModels.add(expectedAsset)
                    Log.w(
                        TAG,
                        "Model $filename has size mismatch! Expected: ${expectedAsset.metadata.sizeInBytes}, Actual: ${file.length()}. Will re-download."
                    )
                }
                else -> {
                    missingModels.add(expectedAsset)
                    Log.d(TAG, "Model $filename is missing")
                    if (tempExists) {
                        tempFile.delete()
                    }
                    if (metaFile.exists()) {
                        metaFile.delete()
                    }
                }
            }
        }

        ModelScanResult(
            // Preserve per-slot assets for shared files so downstream activation can
            // apply every configuration (VISION / FAST / THINKING, etc.) after one download.
            missingModels = missingModels.distinct(),
            partialDownloads = partialDownloads,
            allValid = missingModels.isEmpty() && invalidModels.isEmpty() && partialDownloads.isEmpty(),
            invalidModels = invalidModels.distinct()
        )
    }

    /**
     * Deletes the physical model file from disk for the given local model ID.
     * Called during soft-delete of a local model.
     */
    override suspend fun deleteModelFile(localModelId: Long) {
        withContext(Dispatchers.IO) {
            val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)

            // Look up the model to get its filename
            val asset = localModelRepository.getAssetById(localModelId)
            val filename = asset?.metadata?.localFileName

            if (filename != null && filename.isNotBlank()) {
                val file = File(modelsDir, filename)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (!deleted) {
                        Log.w(TAG, "Failed to delete model file: ${file.absolutePath}")
                    }
                }

                // Also clean up any partial download temp file for this model
                val tempFile = File(modelsDir, "$filename${ModelConfig.TEMP_EXTENSION}")
                if (tempFile.exists()) {
                    val tempDeleted = tempFile.delete()
                    if (!tempDeleted) {
                        Log.w(TAG, "Failed to delete temp file: ${tempFile.absolutePath}")
                    }
                }
                val metaFile = File(modelsDir, "$filename${ModelConfig.TEMP_META_EXTENSION}")
                if (metaFile.exists()) {
                    metaFile.delete()
                }
            } else {
                Log.w(TAG, "Could not find model with ID $localModelId to delete file")
            }
        }
    }

    /**
     * Quick scan to check if all models are present.
     * Used for fast path checking without fetching remote config.
     */
    suspend fun quickCheckModelsReady(): Boolean = withContext(Dispatchers.IO) {
        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)

        // Get dynamic filenames from registry
        val assetsByType = ModelType.entries.associateWith { modelType -> 
            val config = activeModelProvider.getActiveConfiguration(modelType)
            if (config != null && config.isLocal) {
                localModelRepository.getAssetByConfigId(config.id as LocalModelConfigurationId)
            } else null
        }

        // Check for model files - use localFileName from config
        val requiredFiles = listOfNotNull(
            assetsByType[ModelType.VISION]?.metadata?.localFileName,
            assetsByType[ModelType.DRAFT_ONE]?.metadata?.localFileName,
            assetsByType[ModelType.DRAFT_TWO]?.metadata?.localFileName,
            assetsByType[ModelType.MAIN]?.metadata?.localFileName,
            assetsByType[ModelType.FAST]?.metadata?.localFileName,
            assetsByType[ModelType.THINKING]?.metadata?.localFileName
        )

        if (requiredFiles.isEmpty()) {
            throw IllegalStateException("No models configured. This is a bug - models must be configured before quickCheckModelsReady is called.")
        }

        // First check that all required files exist with content
        val allFilesExist = requiredFiles.all { filename ->
            val file = File(modelsDir, filename)
            file.exists() && file.length() > 0
        }

        // Also verify no partial .tmp files exist (from failed downloads)
        val noPartialDownloads = requiredFiles.all { filename ->
            val tempFile = File(modelsDir, "$filename${ModelConfig.TEMP_EXTENSION}")
            !tempFile.exists()
        }

        allFilesExist && noPartialDownloads
    }
}
