package com.browntowndev.pocketcrew.data.download

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.port.download.HashingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelFileScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val modelRegistry: ModelRegistryPort,
    private val hashingPort: HashingPort
) {
    companion object {
        private const val TAG = "ModelFileScanner"
    }

    /**
     * Scan the models directory and create it if it doesn't exist.
     * Validates against cache (expected models) for MD5 and format changes.
     *
     * @param downloadedModels List of models actually downloaded (from registry)
     * @param expectedModels List of models expected from remote config (from cache)
     */
    suspend fun scanAndCreateDirIfNotExist(
        downloadedModels: List<ModelConfiguration> = emptyList(),
        expectedModels: List<ModelConfiguration> = emptyList()
    ): ModelScanResult = withContext(Dispatchers.IO) {
        // Create lookup map for downloaded models
        val downloadedModelsByType = downloadedModels.associateBy { it.modelType }

        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)

        if (!modelsDir.exists()) {
            val created = modelsDir.mkdirs()
            if (!created && !modelsDir.exists()) {
                Log.e(TAG, "Failed to create models directory")
                return@withContext ModelScanResult(
                    missingModels = expectedModels,
                    partialDownloads = emptyMap(),
                    allValid = false,
                    directoryError = true
                )
            }
        }

        val missingModels = mutableListOf<ModelConfiguration>()
        val partialDownloads = mutableMapOf<String, Long>()
        val invalidModels = mutableListOf<ModelConfiguration>()

        // Collect files that need SHA256 verification for concurrent processing
        val filesToVerify = mutableListOf<Triple<ModelConfiguration, File, String>>()

        // Iterate over expected models (what we want to have)
        for (expectedModel in expectedModels) {
            val filename = expectedModel.metadata.localFileName
            val modelType = expectedModel.modelType

            val file = File(modelsDir, filename)
            val tempFile = File(modelsDir, "${filename}${ModelConfig.TEMP_EXTENSION}")

            val fileExists = file.exists()
            // Skip size validation - rely 100% on MD5 verification for integrity
            // Physical devices may add metadata/buffer bytes that cause size mismatches

            val tempExists = tempFile.exists()
            val tempLength = if (tempExists) tempFile.length() else 0L

            // Get what's actually downloaded from registry
            val downloadedModel = downloadedModelsByType[modelType]

            // Detect if config changed by comparing expected (remote) vs downloaded (registry)
            val configChanged = downloadedModel != null && (
                downloadedModel.metadata.sha256 != expectedModel.metadata.sha256 ||
                downloadedModel.metadata.modelFileFormat != expectedModel.metadata.modelFileFormat
            )

            when {
                // File exists and config hasn't changed - verify SHA256 to ensure file integrity
                fileExists && !configChanged -> {
                    // Add to list for concurrent SHA256 verification
                    filesToVerify.add(Triple(expectedModel, file, expectedModel.metadata.sha256))
                }
                // Partial download exists - check against expected model from cache
                tempExists && tempLength > 0 -> {
                    // Trust partial if config hasn't changed
                    if (!configChanged) {
                        // Track the partial download so UI can show resume progress
                        partialDownloads[filename] = tempLength
                        Log.d(TAG, "Model $filename has valid partial download (config unchanged)")
                    } else {
                        // Not trusted - treat as missing, will re-download
                        missingModels.add(expectedModel)
                        Log.d(TAG, "Model $filename has untrusted partial download (config changed), will re-download")
                    }
                }
                // File exists but config changed - need to re-download
                fileExists && configChanged -> {
                    // Handle format change - delete old format file
                    handleFormatChangeForType(modelType, downloadedModel, modelsDir)
                    invalidModels.add(expectedModel)
                    Log.d(TAG, "Model $filename is invalid (config changed), will re-download")
                }
                // File doesn't exist
                else -> {
                    missingModels.add(expectedModel)
                    Log.d(TAG, "Model $filename is missing")
                }
            }
        }

        // Process SHA256 verifications concurrently
        if (filesToVerify.isNotEmpty()) {
            filesToVerify.chunked(5).forEach { batch ->
                // Launch concurrent async tasks for each file in batch
                val deferredResults = batch.map { (model, file, expectedSha256) ->
                    CoroutineScope(Dispatchers.IO).async {
                        val actualSha256 = hashingPort.calculateSha256(file)
                        Triple(model, expectedSha256, actualSha256)
                    }
                }

                // Await all results in this batch
                val results = deferredResults.awaitAll()

                // Process results
                results.forEach { (model, expectedSha256, actualSha256) ->
                    val filename = model.metadata.localFileName
                    if (actualSha256 == expectedSha256) {
                        Log.d(TAG, "Model $filename is valid (SHA256 matches)")
                    } else {
                        Log.w(TAG, "Model $filename has SHA256 mismatch! Expected: $expectedSha256, Actual: $actualSha256. Will re-download.")
                        invalidModels.add(model)
                    }
                }
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
        registeredModel: ModelConfiguration?,
        modelsDir: File
    ) {
        if (registeredModel == null) return

        // Get the old filename with the previous format
        val oldFormat = registeredModel.metadata.modelFileFormat
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
     * Generate filename for a model based on its type and format.
     * Note: This is only used for fallback when cache doesn't have the info.
     * The actual filename comes from the config's localFileName.
     */
    private fun getFilenameForModel(modelType: ModelType, format: ModelFileFormat): String {
        val extension = when (format) {
            ModelFileFormat.LITERTLM -> "litertlm"
            ModelFileFormat.TASK -> "task"
            ModelFileFormat.GGUF -> "gguf"
        }
        return when (modelType) {
            ModelType.VISION -> "vision.$extension"
            ModelType.DRAFT_ONE -> "draft_one.$extension"
            ModelType.DRAFT_TWO -> "draft_two.$extension"
            ModelType.MAIN -> "main.$extension"
            ModelType.FAST -> "fast.$extension"
            ModelType.THINKING -> "thinking.$extension"
            ModelType.FINAL_SYNTHESIS -> "final_synthesis.$extension"
        }
    }

    /**
     * Quick scan to check if all models are present.
     * Used for fast path checking without fetching remote config.
     */
    suspend fun quickCheckModelsReady(): Boolean = withContext(Dispatchers.IO) {
        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)

        // Get dynamic filenames from registry
        val allConfigs = modelRegistry.getRegisteredModels()
        val configsByType = allConfigs.associateBy { it.modelType }

        // Check for model files - use localFileName from config
        val requiredFiles = listOfNotNull(
            configsByType[ModelType.VISION]?.metadata?.localFileName,
            configsByType[ModelType.DRAFT_ONE]?.metadata?.localFileName,
            configsByType[ModelType.DRAFT_TWO]?.metadata?.localFileName,
            configsByType[ModelType.MAIN]?.metadata?.localFileName,
            configsByType[ModelType.FAST]?.metadata?.localFileName,
            configsByType[ModelType.THINKING]?.metadata?.localFileName
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
