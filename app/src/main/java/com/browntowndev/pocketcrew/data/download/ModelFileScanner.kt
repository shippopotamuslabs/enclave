package com.browntowndev.pocketcrew.data.download

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.port.cache.ModelConfigCachePort
import com.browntowndev.pocketcrew.domain.model.ModelConfig
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.ModelFile
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.port.repository.RegisteredModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelFileScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val modelRegistry: ModelRegistryPort,
    private val modelConfigCache: ModelConfigCachePort
) {
    companion object {
        private const val TAG = "ModelFileScanner"
    }

    /**
     * Generate filename for a model based on its type and format.
     */
    private fun getFilenameForModel(modelType: ModelType, format: ModelFileFormat): String {
        val extension = when (format) {
            ModelFileFormat.LITERTLM -> "litertlm"
            ModelFileFormat.TASK -> "task"
        }
        return when (modelType) {
            ModelType.VISION -> "vision.$extension"
            ModelType.DRAFT -> "draft.$extension"
            ModelType.MAIN -> "main.$extension"
            ModelType.FAST -> "fast.$extension"
        }
    }

    /**
     * Scan the models directory and create it if it doesn't exist.
     * Validates against ModelRegistry for MD5 and format changes.
     * @param modelsToCheck List of ORIGINAL models from registry (before remote config update)
     * @param newModels List of NEW models from remote config (for reference to detect config changes)
     */
    suspend fun scanAndCreateDirIfNotExist(
        modelsToCheck: List<ModelFile> = emptyList(),
        newModels: List<ModelFile> = emptyList()
    ): ModelScanResult = withContext(Dispatchers.IO) {
        // Create a lookup for new models to detect config changes
        val newModelsByType = newModels.flatMap { model ->
            model.modelTypes.map { it to model }
        }.toMap()

        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)

        if (!modelsDir.exists()) {
            val created = modelsDir.mkdirs()
            if (!created && !modelsDir.exists()) {
                Log.e(TAG, "Failed to create models directory")
                return@withContext ModelScanResult(
                    missingModels = modelsToCheck,
                    partialDownloads = emptyMap(),
                    allValid = false,
                    directoryError = true
                )
            }
        }

        val missingModels = mutableListOf<ModelFile>()
        val partialDownloads = mutableMapOf<String, Long>()
        val invalidModels = mutableListOf<ModelFile>()

        for (model in modelsToCheck) {
            // Check all filenames this model serves (one file per modelType)
            // Note: When model types share a file, ModelDownloadWorker creates copies for each,
            // so we need to check all filenames to ensure all are present
            var allFilesValid = true
            
            for ((index, filename) in model.filenames.withIndex()) {
                val modelType = model.modelTypes[index]
                val file = File(modelsDir, filename)
                val tempFile = File(modelsDir, "${filename}${ModelConfig.TEMP_EXTENSION}")

                val fileExists = file.exists()
                // Skip size validation - rely 100% on MD5 verification for integrity
                // Physical devices may add metadata/buffer bytes that cause size mismatches

                val tempExists = tempFile.exists()
                val tempLength = if (tempExists) tempFile.length() else 0L

                // Check if model is registered and validate against registry
                val registeredModel = modelRegistry.getRegisteredModel(modelType)
                
                // Detect if config changed by comparing new model against registered
                // This is the key fix: detect when remote config differs from what was previously registered
                val configChanged = newModelsByType[modelType]?.let { newModel ->
                    registeredModel != null && (
                        registeredModel.md5 != newModel.md5 ||
                        registeredModel.modelFileFormat != newModel.modelFileFormat
                    )
                } ?: false

                // Use the configChanged flag to invalidate existing files when config changed
                val isValidByRegistry = !configChanged && (fileExists && registeredModel != null)

                when {
                    // File is valid: exists and config hasn't changed - trust MD5 validation
                    fileExists && isValidByRegistry && !configChanged -> {
                        Log.d(TAG, "Model $filename is valid (exists & config unchanged - MD5 will verify)")
                    }
                    // Partial download exists - check if we should trust it based on config
                    tempExists && tempLength > 0 -> {
                        val canTrustPartial = !configChanged && registeredModel != null &&
                            registeredModel.md5 == model.md5 &&
                            registeredModel.modelFileFormat == model.modelFileFormat
                        if (canTrustPartial) {
                            // Trust the partial file - it's from a previous download with matching MD5
                            // Track the partial download so UI can show resume progress
                            partialDownloads[filename] = tempLength
                            Log.d(TAG, "Model $filename has valid partial download (trusted by registry)")
                        } else {
                            // Not trusted - treat as missing, will re-download
                            allFilesValid = false
                            Log.d(TAG, "Model $filename has untrusted partial download (config changed), will re-download")
                        }
                    }
                    // File exists but config changed
                    fileExists && !isValidByRegistry -> {
                        // Handle format change - delete old format file
                        handleFormatChangeForType(modelType, registeredModel, model.modelFileFormat, modelsDir)
                        allFilesValid = false
                        invalidModels.add(model)
                        Log.d(TAG, "Model $filename is invalid (config changed), will re-download")
                    }
                    // File doesn't exist
                    else -> {
                        allFilesValid = false
                        Log.d(TAG, "Model $filename is missing (exists=$fileExists)")
                    }
                }
            }
            
            if (!allFilesValid && !missingModels.contains(model)) {
                missingModels.add(model)
            }
        }

        ModelScanResult(
            missingModels = missingModels,
            partialDownloads = partialDownloads,
            allValid = missingModels.isEmpty() && invalidModels.isEmpty() && partialDownloads.isEmpty(),
            invalidModels = invalidModels
        )
    }

    /**
     * Handle format change by deleting the old format file for a specific modelType.
     */
    private suspend fun handleFormatChangeForType(
        modelType: ModelType,
        registeredModel: RegisteredModel?,
        newFormat: ModelFileFormat,
        modelsDir: File
    ) {
        if (registeredModel == null) return

        // Get the old filename with the previous format
        val oldFormat = registeredModel.modelFileFormat
        val oldFilename = getFilenameForModel(modelType, oldFormat)
        val oldFile = File(modelsDir, oldFilename)

        if (oldFile.exists()) {
            val deleted = oldFile.delete()
            Log.d(TAG, "Deleted old format file $oldFilename: $deleted")
        }

        // Also check if there's a .tmp file with old format
        val oldTempFile = File(modelsDir, "$oldFilename${ModelConfig.TEMP_EXTENSION}")
        if (oldTempFile.exists()) {
            val deleted = oldTempFile.delete()
            Log.d(TAG, "Deleted old format temp file: $deleted")
        }
    }

    /**
     * Quick scan to check if all models are present.
     * Used for fast path checking without fetching remote config.
     */
    suspend fun quickCheckModelsReady(): Boolean = withContext(Dispatchers.IO) {
        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        
        // Get dynamic filenames from cache, or use fallback defaults
        val visionConfig = modelConfigCache.getVisionConfig()
        val draftConfig = modelConfigCache.getDraftConfig()
        val mainConfig = modelConfigCache.getMainConfig()
        val fastConfig = modelConfigCache.getFastConfig()
        
        // Check for standardized model files
        val requiredFiles = listOfNotNull(
            visionConfig?.let { getFilenameForModel(it.modelType, it.modelFileFormat) }
                ?: getFilenameForModel(ModelType.VISION, ModelFileFormat.LITERTLM),
            draftConfig?.let { getFilenameForModel(it.modelType, it.modelFileFormat) }
                ?: getFilenameForModel(ModelType.DRAFT, ModelFileFormat.LITERTLM),
            mainConfig?.let { getFilenameForModel(it.modelType, it.modelFileFormat) }
                ?: getFilenameForModel(ModelType.MAIN, ModelFileFormat.LITERTLM),
            fastConfig?.let { getFilenameForModel(it.modelType, it.modelFileFormat) }
                ?: getFilenameForModel(ModelType.FAST, ModelFileFormat.LITERTLM)
        )
        
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
