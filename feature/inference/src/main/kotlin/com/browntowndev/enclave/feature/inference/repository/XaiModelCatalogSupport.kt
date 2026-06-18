package com.browntowndev.enclave.feature.inference.repository

import android.util.Log
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

@Serializable
private data class XaiModelsResponse(
    val models: List<XaiModel>? = null,
    val data: List<XaiModel>? = null,
)

@Serializable
private data class XaiModel(
    val id: String,
    val name: String? = null,
    val created: Long? = null,
    val prompt_text_token_price: Double? = null,
    val prompt_token_price: Double? = null,
    val completion_text_token_price: Double? = null,
    val completion_token_price: Double? = null,
    val cached_prompt_text_token_price: Double? = null,
    val image_price: Double? = null,
    val max_prompt_length: Int? = null,
    val context_length: Int? = null,
    val input_modalities: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
)

internal object XaiModelCatalogSupport {
    private const val TAG = "ApiModelCatalog"
    private const val LANGUAGE_MODELS_PATH = "language-models"
    private const val IMAGE_MODELS_PATH = "image-generation-models"
    private const val VIDEO_MODELS_PATH = "video-generation-models"

    suspend fun fetchModels(
        httpClient: Call.Factory,
        apiKey: String,
        baseUrl: String,
    ): List<DiscoveredApiModel> =
        coroutineScope {
            val trimmedApiKey = apiKey.trim()
            val languageModelsDeferred =
                async {
                    fetchModelsFromEndpoint(
                        httpClient = httpClient,
                        apiKey = trimmedApiKey,
                        baseUrl = baseUrl,
                        endpointPath = LANGUAGE_MODELS_PATH,
                        isChat = true,
                    )
                }
            val imageModelsDeferred =
                async {
                    fetchModelsFromEndpoint(
                        httpClient = httpClient,
                        apiKey = trimmedApiKey,
                        baseUrl = baseUrl,
                        endpointPath = IMAGE_MODELS_PATH,
                        isImageGen = true,
                    )
                }
            val videoModelsDeferred =
                async {
                    fetchModelsFromEndpoint(
                        httpClient = httpClient,
                        apiKey = trimmedApiKey,
                        baseUrl = baseUrl,
                        endpointPath = VIDEO_MODELS_PATH,
                        isVideoGen = true,
                    )
                }

            val results =
                listOf(
                    languageModelsDeferred.await(),
                    imageModelsDeferred.await(),
                    videoModelsDeferred.await(),
                ).flatten()

            if (results.isEmpty()) {
                fetchFallbackModels(httpClient, trimmedApiKey, baseUrl)
            } else {
                results
                    .distinctBy(DiscoveredApiModel::id)
                    .sortedBy(DiscoveredApiModel::id)
            }
        }

    fun fetchModelDetail(
        httpClient: Call.Factory,
        apiKey: String,
        baseUrl: String,
        modelId: String,
    ): DiscoveredApiModel? {
        val trimmedApiKey = apiKey.trim()
        val endpoints = listOf(LANGUAGE_MODELS_PATH, IMAGE_MODELS_PATH, VIDEO_MODELS_PATH)

        for (endpoint in endpoints) {
            val httpUrl = baseUrl.trimEnd('/').toHttpUrl()
            val url =
                httpUrl
                    .newBuilder()
                    .apply {
                        val segments = httpUrl.pathSegments
                        val xaiEndpoints = listOf("models", LANGUAGE_MODELS_PATH, IMAGE_MODELS_PATH, VIDEO_MODELS_PATH)
                        if (segments.isNotEmpty() && segments.last() in xaiEndpoints) {
                            removePathSegment(segments.size - 1)
                        }
                    }.addPathSegments(endpoint)
                    .addPathSegment(modelId)
                    .build()

            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $trimmedApiKey")
                    .header("Accept", "application/json")
                    .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return parseModelDetail(
                        responseBody = response.body.string(),
                        requestedModelId = modelId,
                        isChat = endpoint == LANGUAGE_MODELS_PATH,
                        isImageGen = endpoint == IMAGE_MODELS_PATH,
                        isVideoGen = endpoint == VIDEO_MODELS_PATH,
                    )
                }
            }
        }

        Log.e(TAG, "xAI model detail fetch failed for $modelId across all endpoints")
        return null
    }

    fun parseModels(
        responseBody: String,
        isChat: Boolean = false,
        isImageGen: Boolean = false,
        isVideoGen: Boolean = false,
    ): List<DiscoveredApiModel> {
        val response = apiModelCatalogJson.decodeFromString<XaiModelsResponse>(responseBody)
        val data = response.models ?: response.data ?: return emptyList()

        return data
            .mapNotNull { model ->
                val promptPrice = model.prompt_text_token_price ?: model.prompt_token_price
                val completionPrice = model.completion_text_token_price ?: model.completion_token_price
                val cachedPromptPrice = model.cached_prompt_text_token_price

                DiscoveredApiModel(
                    id = model.id,
                    name = model.name?.takeIf { it.isNotBlank() } ?: model.id,
                    created = model.created,
                    promptPrice = promptPrice?.asUsdPerMillionFromXai(),
                    completionPrice = completionPrice?.asUsdPerMillionFromXai(),
                    cachedPromptPrice = cachedPromptPrice?.asUsdPerMillionFromXai(),
                    contextWindowTokens = model.max_prompt_length,
                    isMultimodal = model.input_modalities.any { it.equals("image", ignoreCase = true) },
                ).applyCatalogCapabilities(
                    provider = ApiProvider.XAI,
                    supportedGenerationMethods = model.input_modalities,
                    isChatOverride = isChat,
                    isImageGenOverride = isImageGen,
                    isVideoGenOverride = isVideoGen,
                )
            }.distinctBy(DiscoveredApiModel::id)
            .sortedBy(DiscoveredApiModel::id)
    }

    fun parseModelDetail(
        responseBody: String,
        requestedModelId: String,
        isChat: Boolean = false,
        isImageGen: Boolean = false,
        isVideoGen: Boolean = false,
    ): DiscoveredApiModel? {
        val model = apiModelCatalogJson.decodeFromString<XaiModel>(responseBody)
        val canonicalId = model.id.takeIf { it.isNotBlank() } ?: return null
        val matchedAlias = model.aliases.firstOrNull { it == requestedModelId }

        val promptPrice = model.prompt_text_token_price ?: model.prompt_token_price
        val completionPrice = model.completion_text_token_price ?: model.completion_token_price
        val cachedPromptPrice = model.cached_prompt_text_token_price

        return DiscoveredApiModel(
            id = matchedAlias ?: requestedModelId.takeIf { it.isNotBlank() } ?: canonicalId,
            name = model.name?.takeIf { it.isNotBlank() } ?: matchedAlias ?: canonicalId,
            created = model.created,
            promptPrice = promptPrice?.asUsdPerMillionFromXai(),
            completionPrice = completionPrice?.asUsdPerMillionFromXai(),
            cachedPromptPrice = cachedPromptPrice?.asUsdPerMillionFromXai(),
            contextWindowTokens = model.max_prompt_length ?: model.context_length,
            isMultimodal = model.input_modalities.any { it.equals("image", ignoreCase = true) },
        ).applyCatalogCapabilities(
            provider = ApiProvider.XAI,
            supportedGenerationMethods = model.input_modalities,
            isChatOverride = isChat,
            isImageGenOverride = isImageGen,
            isVideoGenOverride = isVideoGen,
        )
    }

    private fun fetchModelsFromEndpoint(
        httpClient: Call.Factory,
        apiKey: String,
        baseUrl: String,
        endpointPath: String,
        isChat: Boolean = false,
        isImageGen: Boolean = false,
        isVideoGen: Boolean = false,
    ): List<DiscoveredApiModel> {
        val httpUrl = baseUrl.trimEnd('/').toHttpUrl()
        val url =
            httpUrl
                .newBuilder()
                .apply {
                    val segments = httpUrl.pathSegments
                    val xaiEndpoints = listOf("models", LANGUAGE_MODELS_PATH, IMAGE_MODELS_PATH, VIDEO_MODELS_PATH)
                    if (segments.isNotEmpty() && segments.last() in xaiEndpoints) {
                        removePathSegment(segments.size - 1)
                    }
                }.addPathSegments(endpointPath)
                .build()

        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body.string()
                Log.e(TAG, "xAI model discovery ($endpointPath) failed with HTTP ${response.code}: $errorBody")
                return emptyList()
            }
            val body = response.body.string()
            return parseModels(body, isChat = isChat, isImageGen = isImageGen, isVideoGen = isVideoGen)
        }
    }

    private fun fetchFallbackModels(
        httpClient: Call.Factory,
        apiKey: String,
        baseUrl: String,
    ): List<DiscoveredApiModel> {
        val httpUrl = baseUrl.trimEnd('/').toHttpUrl()
        val url =
            httpUrl
                .newBuilder()
                .apply {
                    val segments = httpUrl.pathSegments
                    val xaiEndpoints = listOf("models", LANGUAGE_MODELS_PATH, IMAGE_MODELS_PATH, VIDEO_MODELS_PATH)
                    if (segments.isNotEmpty() && segments.last() in xaiEndpoints) {
                        removePathSegment(segments.size - 1)
                    }
                }.addPathSegments("models")
                .build()

        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body.string()
                error("xAI model discovery failed with HTTP ${response.code}: $errorBody")
            }
            val body = response.body.string()
            return parseModels(body)
        }
    }
}
