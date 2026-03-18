package com.browntowndev.pocketcrew.data.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
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
     * Fetches model_config.json from the config URL and parses it into ModelConfiguration list.
     */
    override suspend fun fetchRemoteConfig(): Result<List<ModelConfiguration>> = withContext(Dispatchers.IO) {
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
                    configs.add(parseModelConfig(vision, ModelType.VISION))
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

                // Convert to ModelConfiguration
                val modelConfigs = toModelConfigurations(configs)
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
            thinkingEnabled = json.optBoolean("thinkingEnabled", false)
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
     * Converts RemoteModelConfig list to ModelConfiguration list.
     * Uses ModelUrlProvider to compute download URLs.
     *
     * Note: The download URL is computed on-the-fly by ModelUrlProviderPort when needed,
     * not stored in the model.
     * Grouping by SHA256 (for files shared across multiple model types) happens in
     * DownloadProgressTracker, not here.
     */
    override fun toModelConfigurations(configs: List<RemoteModelConfig>): List<ModelConfiguration> {
        return configs.map { config ->
            Log.d(TAG, "Model Config File: ${config.fileName}, modelType: ${config.modelType}")

            ModelConfiguration(
                modelType = config.modelType,
                metadata = ModelConfiguration.Metadata(
                    huggingFaceModelName = config.huggingFaceModelName,
                    remoteFileName = config.fileName,
                    localFileName = config.fileName,
                    displayName = config.displayName,
                    sha256 = config.sha256,
                    sizeInBytes = config.sizeInBytes,
                    modelFileFormat = config.modelFileFormat
                ),
                tunings = ModelConfiguration.Tunings(
                    temperature = config.temperature,
                    topK = config.topK,
                    topP = config.topP,
                    minP = config.minP,
                    repetitionPenalty = config.repetitionPenalty,
                    maxTokens = config.maxTokens,
                    contextWindow = config.contextWindow,
                    thinkingEnabled = config.thinkingEnabled
                ),
                persona = ModelConfiguration.Persona(
                    systemPrompt = config.systemPrompt
                )
            )
        }
    }
}
