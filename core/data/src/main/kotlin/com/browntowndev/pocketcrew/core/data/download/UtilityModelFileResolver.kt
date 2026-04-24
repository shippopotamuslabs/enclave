package com.browntowndev.pocketcrew.core.data.download

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.config.UtilityType
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.pocketcrew.domain.port.repository.UtilityModelFilePort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class UtilityModelFileResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val modelConfigFetcher: ModelConfigFetcherPort,
) : UtilityModelFilePort {

    override suspend fun resolveUtilityModelPath(utilityType: UtilityType): String? = withContext(Dispatchers.IO) {
        val assets = modelConfigFetcher.fetchRemoteConfig().getOrThrow()
        val asset = assets.firstOrNull { candidate ->
            candidate.metadata.utilityType == utilityType
        } ?: return@withContext null

        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        val modelFile = safeFile(modelsDir, asset.metadata.localFileName)

        if (modelFile.exists() && modelFile.length() == asset.metadata.sizeInBytes) {
            modelFile.absolutePath
        } else {
            null
        }
    }

    private fun isSafeChildOf(child: File, parent: File): Boolean {
        val childCanonical = child.canonicalPath
        val parentCanonical = parent.canonicalPath + File.separator
        return childCanonical.startsWith(parentCanonical)
    }

    private fun safeFile(parentDir: File, filename: String): File {
        require(filename.isNotBlank()) { "Utility model filename must not be blank." }

        val safeName = File(filename).name
        val resolvedFile = File(parentDir, safeName)
        require(isSafeChildOf(resolvedFile, parentDir)) {
            "Resolved utility model path escapes model directory."
        }
        return resolvedFile
    }
}
