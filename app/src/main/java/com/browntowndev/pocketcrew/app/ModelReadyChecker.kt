package com.browntowndev.pocketcrew.app

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.port.cache.ModelConfigCachePort
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.ModelConfig
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.model.ModelType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
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
    private val modelConfigCache: ModelConfigCachePort
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
        
        // Get dynamic filenames from cache, or use fallback defaults
        val visionConfig = modelConfigCache.getVisionConfig()
        val draftConfig = modelConfigCache.getDraftConfig()
        val mainConfig = modelConfigCache.getMainConfig()
        val fastConfig = modelConfigCache.getFastConfig()
        
        val requiredFiles = listOfNotNull(
            visionConfig?.let { computeFilename(ModelType.VISION, it.modelFileFormat) } 
                ?: "${ModelType.VISION.name.lowercase()}.litertlm",
            draftConfig?.let { computeFilename(ModelType.DRAFT, it.modelFileFormat) }
                ?: "${ModelType.DRAFT.name.lowercase()}.litertlm",
            mainConfig?.let { computeFilename(ModelType.MAIN, it.modelFileFormat) }
                ?: "${ModelType.MAIN.name.lowercase()}.litertlm",
            fastConfig?.let { computeFilename(ModelType.FAST, it.modelFileFormat) }
                ?: "${ModelType.FAST.name.lowercase()}.litertlm"
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
