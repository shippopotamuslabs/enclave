package com.browntowndev.pocketcrew.data.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.ModelConfig
import com.browntowndev.pocketcrew.domain.model.ModelFile
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.model.RemoteModelConfig
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelConfigFetcherImpl @Inject constructor(
    private val okHttpClient: OkHttpClient
) : ModelConfigFetcherPort {
    companion object {
        private const val TAG = "ModelConfigFetcher"
        private const val CONFIG_URL = "${ModelConfig.R2_BUCKET_URL}/model_config.json"
    }

    /**
     * Fetches model_config.json from R2 and parses it into RemoteModelConfig list.
     */
    override suspend fun fetchRemoteConfig(): Result<List<RemoteModelConfig>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(CONFIG_URL)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: ${response.message}")
                    )
                }

                val json = response.body?.string()?.let { JSONObject(it) }
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val configs = mutableListOf<RemoteModelConfig>()

                // Parse vision model
                json.optJSONObject("vision")?.let { vision ->
                    configs.add(parseModelConfig(vision, ModelType.VISION))
                }

                // Parse draft model
                json.optJSONObject("draft")?.let { draft ->
                    configs.add(parseModelConfig(draft, ModelType.DRAFT))
                }

                // Parse main model
                json.optJSONObject("main")?.let { main ->
                    configs.add(parseModelConfig(main, ModelType.MAIN))
                }

                // Parse fast model
                json.optJSONObject("fast")?.let { fast ->
                    configs.add(parseModelConfig(fast, ModelType.FAST))
                }

                Log.i(TAG, "Fetched ${configs.size} model configs from server")
                Result.success(configs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch model config: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun parseModelConfig(json: JSONObject, modelType: ModelType): RemoteModelConfig {
        val fileName = json.getString("fileName")
        // Extract format from fileName extension if not explicitly provided in JSON
        val modelFileFormat = if (json.has("modelFileFormat")) {
            parseModelFileFormat(json.getString("modelFileFormat"))
        } else {
            parseModelFileFormatFromFileName(fileName)
        }
        
        return RemoteModelConfig(
            modelType = modelType,
            fileName = fileName,
            displayName = json.getString("displayName"),
            md5 = json.getString("md5"),
            sizeInBytes = json.getLong("sizeInBytes"),
            modelFileFormat = modelFileFormat,
            temperature = json.getDouble("temperature"),
            topK = json.getInt("topK"),
            topP = json.getDouble("topP"),
            maxTokens = json.getInt("maxTokens"),
            systemPrompt = json.getString("systemPrompt")
        )
    }

    private fun parseModelFileFormatFromFileName(fileName: String): ModelFileFormat {
        return when {
            fileName.endsWith(".task", ignoreCase = true) -> ModelFileFormat.TASK
            fileName.endsWith(".litertlm", ignoreCase = true) -> ModelFileFormat.LITERTLM
            else -> ModelFileFormat.LITERTLM // Default to LITERTLM for unknown extensions
        }
    }

    private fun parseModelFileFormat(format: String): ModelFileFormat {
        return when (format.uppercase()) {
            "TASK" -> ModelFileFormat.TASK
            else -> ModelFileFormat.LITERTLM
        }
    }

    /**
     * Converts RemoteModelConfig to list of ModelFile with standardized filenames.
     * Downloads from original URL but saves as standardized name.
     * The filename is computed from modelType + modelFileFormat.
     * Detects duplicate files (same fileName) and merges them into a single ModelFile
     * with multiple modelTypes. MD5 verification happens after download, not during grouping.
     */
    override fun toModelFiles(configs: List<RemoteModelConfig>): List<ModelFile> {
        // Group by md5 for true duplicates
        // - Same md5: deduplicated (one download, copies created)
        // - Same md5 but DIFFERENT fileName treats them as same file (one download, copies created)
        val grouped = configs.groupBy { it.md5 }

        return grouped.map { (_, groupedConfigs) ->
            val firstConfig = groupedConfigs.first()
            val modelTypesList = groupedConfigs.map { it.modelType }
            Log.d(TAG, "Model Config File: ${firstConfig.fileName} Grouped model types: $modelTypesList")

            ModelFile(
                sizeBytes = firstConfig.sizeInBytes,
                url = "${ModelConfig.R2_BUCKET_URL}/${firstConfig.fileName}",
                md5 = firstConfig.md5,
                modelTypes = modelTypesList,
                originalFileName = firstConfig.fileName,
                displayName = firstConfig.displayName,
                modelFileFormat = firstConfig.modelFileFormat,
                temperature = firstConfig.temperature,
                topK = firstConfig.topK,
                topP = firstConfig.topP,
                maxTokens = firstConfig.maxTokens,
                systemPrompt = firstConfig.systemPrompt
            )
        }
    }
}
