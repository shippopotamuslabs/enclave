package com.browntowndev.pocketcrew.app

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks if model files are ready using ModelDownloadOrchestrator.
 * This validates against both filesystem and ModelRegistry for full integrity checking.
 *
 * Checks for standardized model files: vision.litertlm, draft.litertlm, main.litertlm
 */
@Singleton
class ModelReadyChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val modelRegistry: ModelRegistryPort
) {
    companion object {
        private const val TAG = "ModelReadyChecker"
    }

    /**
     * Get the directory where models are stored.
     */
    private fun getModelsDirectory(): File {
        return File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
    }

    /**
     * Compute filename from modelType and modelFileFormat.
     */
    private fun computeFilename(modelType: ModelType, format: ModelFileFormat): String {
        return "${modelType.name.lowercase()}.${format.extension.removePrefix(".")}"
    }

    /**
     * Quick synchronous check - only checks filesystem without remote config.
     * Use this as a fast path when you need a quick check without network.
     */
    fun isReadyFastSync(): Boolean {
        val modelsDir = getModelsDirectory()
        if (!modelsDir.exists()) {
            Log.d(TAG, "Models directory does not exist")
            return false
        }

        // Get dynamic filenames from registry, or throw if not configured
        val allConfigs = modelRegistry.getRegisteredModelsSync()
        val configsByType = allConfigs.associateBy { it.modelType }

        val requiredFiles = listOfNotNull(
            configsByType[ModelType.VISION]?.let { computeFilename(ModelType.VISION, it.metadata.modelFileFormat) },
            configsByType[ModelType.DRAFT_ONE]?.let { computeFilename(ModelType.DRAFT_ONE, it.metadata.modelFileFormat) },
            configsByType[ModelType.DRAFT_TWO]?.let { computeFilename(ModelType.DRAFT_TWO, it.metadata.modelFileFormat) },
            configsByType[ModelType.MAIN]?.let { computeFilename(ModelType.MAIN, it.metadata.modelFileFormat) },
            configsByType[ModelType.FAST]?.let { computeFilename(ModelType.FAST, it.metadata.modelFileFormat) }
        )

        for (filename in requiredFiles) {
            val file = File(modelsDir, filename)
            if (!file.exists() || file.length() == 0L) {
                Log.d(TAG, "Model $filename not ready: exists=${file.exists()}, size=${file.length()}")
                return false
            }
        }

        Log.d(TAG, "All models are ready (fast check)")
        return true
    }
}
