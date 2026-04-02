package com.browntowndev.pocketcrew.core.data.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.config.RemoteModelConfig
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
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
    private val okHttpClient: OkHttpClient,
    private val modelUrlProvider: ModelUrlProviderPort
) : ModelConfigFetcherPort {

    companion object {
        private const val TAG = "ModelConfigFetcher"
    }

    /**
     * Fetches model_config.json from the config URL and parses it into LocalModelAsset map.
     */
    override suspend fun fetchRemoteConfig(): Result<Map<ModelType, LocalModelAsset>> = withContext(Dispatchers.IO) {
        try {
            val configUrl = modelUrlProvider.getConfigUrl()
            Log.i(TAG, "Fetching config from URL: $configUrl")
            val request = Request.Builder()
                .url(configUrl)
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
                    val config = parseModelConfig(vision, ModelType.VISION)
                    if (!config.visionCapable) {
                        return@withContext Result.failure(
                            Exception("Model configured for 'vision' slot must have 'visionCapable' set to true")
                        )
                    }
                    configs.add(config)
                }

                // Parse draft_one model
                json.optJSONObject("draft")?.let { draftOne ->
                    configs.add(parseModelConfig(draftOne, ModelType.DRAFT_ONE))
                }

                // Parse draft_two model
                json.optJSONObject("draftTwo")?.let { draftTwo ->
                    configs.add(parseModelConfig(draftTwo, ModelType.DRAFT_TWO))
                }

                // Parse main model
                json.optJSONObject("main")?.let { main ->
                    configs.add(parseModelConfig(main, ModelType.MAIN))
                }

                // Parse fast model
                json.optJSONObject("fast")?.let { fast ->
                    configs.add(parseModelConfig(fast, ModelType.FAST))
                }

                // Parse thinking model
                json.optJSONObject("thinking")?.let { thinking ->
                    configs.add(parseModelConfig(thinking, ModelType.THINKING))
                }

                // Parse finalSynthesis model
                json.optJSONObject("finalSynthesis")?.let { finalSynthesis ->
                    configs.add(parseModelConfig(finalSynthesis, ModelType.FINAL_SYNTHESIS))
                }

                Log.i(TAG, "Fetched ${configs.size} model configs from server")

                // Convert to LocalModelAsset
                val modelConfigs = toLocalModelAssets(configs)
                Result.success(modelConfigs)
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

        // Get HuggingFace model name - defaults to empty if not present (for backward compatibility)
        val huggingFaceModelName = json.optString("huggingFaceModelName", "")

        return RemoteModelConfig(
            modelType = modelType,
            fileName = fileName,
            huggingFaceModelName = huggingFaceModelName,
            displayName = json.getString("displayName"),
            sha256 = json.getString("sha256"),
            sizeInBytes = json.getLong("sizeInBytes"),
            modelFileFormat = modelFileFormat,
            temperature = json.getDouble("temperature"),
            topK = json.getInt("topK"),
            topP = json.getDouble("topP"),
            minP = json.getDouble("minP"),
            repetitionPenalty = json.getDouble("repetitionPenalty"),
            maxTokens = json.getInt("maxTokens"),
            contextWindow = json.getInt("contextWindow"),
            systemPrompt = json.getString("systemPrompt"),
            thinkingEnabled = json.optBoolean("thinkingEnabled", false),
            visionCapable = json.optBoolean("visionCapable", false)
        )
    }

    private fun parseModelFileFormatFromFileName(fileName: String): ModelFileFormat {
        return when {
            fileName.endsWith(".task", ignoreCase = true) -> ModelFileFormat.TASK
            fileName.endsWith(".litertlm", ignoreCase = true) -> ModelFileFormat.LITERTLM
            fileName.endsWith(".gguf", ignoreCase = true) -> ModelFileFormat.GGUF
            else -> ModelFileFormat.LITERTLM
        }
    }

    private fun parseModelFileFormat(format: String): ModelFileFormat {
        return when (format.uppercase()) {
            "TASK" -> ModelFileFormat.TASK
            "GGUF" -> ModelFileFormat.GGUF
            else -> ModelFileFormat.LITERTLM
        }
    }

    /**
     * Converts RemoteModelConfig list to a map of ModelType to LocalModelAsset.
     * Uses ModelUrlProvider to compute download URLs.
     *
     * Note: The download URL is computed on-the-fly by ModelUrlProviderPort when needed,
     * not stored in the model.
     * Grouping by SHA256 (for files shared across multiple model types) happens in
     * DownloadProgressTracker, not here.
     */
    override fun toLocalModelAssets(configs: List<RemoteModelConfig>): Map<ModelType, LocalModelAsset> {
        return configs.associate { config ->
            Log.d(TAG, "Model Config File: ${config.fileName}, modelType: ${config.modelType}")

            val metadata = LocalModelMetadata(
                huggingFaceModelName = config.huggingFaceModelName,
                remoteFileName = config.fileName,
                localFileName = config.fileName,
                sha256 = config.sha256,
                sizeInBytes = config.sizeInBytes,
                modelFileFormat = config.modelFileFormat,
                visionCapable = config.visionCapable
            )

            val configuration = LocalModelConfiguration(
                localModelId = 0, // Will be set by Room
                displayName = config.displayName,
                maxTokens = config.maxTokens,
                contextWindow = config.contextWindow,
                temperature = config.temperature,
                topP = config.topP,
                topK = config.topK,
                minP = config.minP,
                repetitionPenalty = config.repetitionPenalty,
                thinkingEnabled = config.thinkingEnabled,
                systemPrompt = config.systemPrompt,
                isSystemPreset = true
            )

            config.modelType to LocalModelAsset(
                metadata = metadata,
                configurations = listOf(configuration)
            )
        }
    }
}
