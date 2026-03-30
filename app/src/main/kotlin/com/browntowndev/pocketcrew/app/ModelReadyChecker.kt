package com.browntowndev.pocketcrew.app

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility to check if all required model files are present on disk.
 * Used during app startup and initialization to decide whether to show download screen.
 */
@Singleton
class ModelReadyChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRegistry: ModelRegistryPort
) {
    companion object {
        private const val TAG = "ModelReadyChecker"
    }

    private fun getModelsDirectory(): File {
        return File(context.filesDir, "models")
    }

    private fun computeFilename(modelType: ModelType, format: ModelFileFormat): String {
        return "${modelType.name.lowercase()}.${format.extension.removePrefix(".")}"
    }

    /**
     * Quick synchronous check - only checks filesystem without remote config.
     * Use this as a fast path when you need a quick check without network.
     */
    @Suppress("ReturnCount")
    fun isReadyFastSync(): Boolean {
        val modelsDir = getModelsDirectory()
        if (!modelsDir.exists()) {
            Log.d(TAG, "Models directory does not exist")
            return false
        }

        // Check if all primary slots have an asset registered and that file exists
        val slotsToCheck = listOf(
            ModelType.VISION,
            ModelType.DRAFT_ONE,
            ModelType.DRAFT_TWO,
            ModelType.MAIN,
            ModelType.FAST
        )

        for (slot in slotsToCheck) {
            val asset = modelRegistry.getRegisteredAssetSync(slot)
            if (asset == null) {
                Log.d(TAG, "No asset registered for slot $slot")
                return false
            }

            val filename = computeFilename(slot, asset.metadata.modelFileFormat)
            val file = File(modelsDir, filename)
            if (!file.exists() || file.length() == 0L) {
                Log.d(TAG, "Model $filename for slot $slot not ready: exists=${file.exists()}, size=${file.length()}")
                return false
            }
        }

        Log.d(TAG, "All required models are ready (fast check)")
        return true
    }
}