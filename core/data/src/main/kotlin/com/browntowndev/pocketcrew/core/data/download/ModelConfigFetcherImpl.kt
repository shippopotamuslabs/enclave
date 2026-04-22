package com.browntowndev.pocketcrew.core.data.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.RemoteModelAsset
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class RemoteConfigResponse(
    val assets: List<RemoteModelAsset>
)

@Singleton
class ModelConfigFetcherImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val modelUrlProvider: ModelUrlProviderPort
) : ModelConfigFetcherPort {

    companion object {
        private const val TAG = "ModelConfigFetcher"
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    /**
     * Fetches model_config.json from the config URL and parses it into LocalModelAsset list.
     */
    override suspend fun fetchRemoteConfig(): Result<List<LocalModelAsset>> = withContext(Dispatchers.IO) {
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

                val bodyString = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val configResponse = json.decodeFromString<RemoteConfigResponse>(bodyString)
                val assets = configResponse.assets

                Log.i(TAG, "Fetched ${assets.size} model assets from server")

                val modelAssets = toLocalModelAssets(assets)
                Result.success(modelAssets)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch model config: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Converts RemoteModelAsset list to a list of LocalModelAsset.
     * Each asset naturally contains its configurations; no deduplication needed.
     */
    override fun toLocalModelAssets(assets: List<RemoteModelAsset>): List<LocalModelAsset> {
        return assets.map { asset ->
            Log.d(TAG, "Model Asset File: ${asset.fileName}, configs: ${asset.configurations.size}")

            val metadata = LocalModelMetadata(
                id = LocalModelId(""),
                huggingFaceModelName = asset.huggingFaceModelName,
                remoteFileName = asset.fileName,
                localFileName = asset.fileName,
                sha256 = asset.sha256,
                sizeInBytes = asset.sizeInBytes,
                modelFileFormat = asset.modelFileFormat,
                source = asset.source,
                utilityType = asset.utilityType,
                isMultimodal = asset.isMultimodal,
                mmprojRemoteFileName = asset.mmprojFileName,
                mmprojLocalFileName = asset.mmprojFileName,
                mmprojSha256 = asset.mmprojSha256,
                mmprojSizeInBytes = asset.mmprojSizeInBytes,
            )

            validateAsset(asset)

            val configurations = asset.configurations.map { config ->
                LocalModelConfiguration(
                    id = config.configId,
                    localModelId = LocalModelId(""),
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
                    isSystemPreset = true,
                    defaultAssignments = config.defaultAssignments
                )
            }

            LocalModelAsset(
                metadata = metadata,
                configurations = configurations
            )
        }
    }

    private fun validateAsset(asset: RemoteModelAsset) {
        if (asset.utilityType != null) {
            require(asset.configurations.isEmpty()) {
                "Utility asset ${asset.fileName} must not declare LLM configurations."
            }
            return
        }

        require(asset.configurations.isNotEmpty()) {
            "Non-utility asset ${asset.fileName} must declare at least one configuration."
        }
    }
}
