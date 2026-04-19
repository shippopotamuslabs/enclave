package com.browntowndev.pocketcrew.core.data.repository

import android.util.Log
import com.browntowndev.pocketcrew.core.data.anthropic.AnthropicClientProvider
import com.browntowndev.pocketcrew.core.data.google.GoogleGenAiClientProvider
import com.browntowndev.pocketcrew.core.data.openai.OpenAiClientProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelCatalogPort
import com.google.genai.types.Model
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class OpenRouterModelsResponse(
    val data: List<OpenRouterModel> = emptyList()
)

@Serializable
private data class OpenRouterModel(
    val id: String,
    val name: String? = null,
    val created: Long? = null,
    val pricing: OpenRouterPricing? = null,
    val context_length: Int? = null,
    val top_provider: OpenRouterTopProvider? = null,
    val architecture: OpenRouterArchitecture? = null
)

@Serializable
private data class OpenRouterPricing(
    val prompt: String? = null,
    val completion: String? = null
)

@Serializable
private data class OpenRouterTopProvider(
    val max_completion_tokens: Int? = null
)

@Serializable
private data class OpenRouterArchitecture(
    val input_modalities: List<String> = emptyList()
)

@Serializable
private data class XaiModelsResponse(
    val models: List<XaiModel>? = null,
    val data: List<XaiModel>? = null
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
    val max_prompt_length: Int? = null,
    val context_length: Int? = null,
    val input_modalities: List<String> = emptyList(),
    val aliases: List<String> = emptyList()
)

@Singleton
class ApiModelCatalogRepositoryImpl @Inject constructor(
    private val openAiClientProvider: OpenAiClientProvider,
    private val anthropicClientProvider: AnthropicClientProvider,
    private val googleGenAiClientProvider: GoogleGenAiClientProvider,
    private val httpClient: Call.Factory,
) : ApiModelCatalogPort {
    private companion object {
        private const val TAG = "ApiModelCatalog"
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun fetchModels(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String?
    ): List<DiscoveredApiModel> = withContext(Dispatchers.IO) {
        require(
            provider == ApiProvider.OPENAI ||
                provider == ApiProvider.ANTHROPIC ||
                provider == ApiProvider.GOOGLE ||
                provider == ApiProvider.OPENROUTER ||
                provider == ApiProvider.XAI
        ) {
            "Model discovery is currently supported for OpenAI, Anthropic, Google, OpenRouter, and xAI."
        }
        val resolvedBaseUrl = baseUrl?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl()

        when (provider) {
            ApiProvider.ANTHROPIC -> anthropicClientProvider.getClient(
                apiKey = apiKey,
                baseUrl = resolvedBaseUrl,
            ).models()
                .list()
                .autoPager()
                .map { 
                    DiscoveredApiModel(
                        id = it.id(),
                        name = it.displayName(),
                        created = it.createdAt().toEpochSecond(),
                        isMultimodal = it.capabilities()
                            .map { capabilities -> capabilities.imageInput().supported() }
                            .orElse(null),
                    )
                }
                .distinctBy(DiscoveredApiModel::id)
                .sortedBy(DiscoveredApiModel::id)

            ApiProvider.GOOGLE -> googleGenAiClientProvider.listModels(
                apiKey = apiKey,
                baseUrl = resolvedBaseUrl,
            )
                .asSequence()
                .map(::toGoogleDiscoveredModel)
                .filter { it.id.isNotBlank() }
                .distinctBy(DiscoveredApiModel::id)
                .sortedBy(DiscoveredApiModel::id)
                .toList()

            ApiProvider.OPENROUTER -> fetchOpenRouterModels(
                apiKey = apiKey,
                baseUrl = checkNotNull(resolvedBaseUrl),
            )
            
            ApiProvider.XAI -> fetchXaiModels(
                apiKey = apiKey,
                baseUrl = checkNotNull(resolvedBaseUrl)
            )

            else -> openAiClientProvider.getClient(
                apiKey = apiKey,
                baseUrl = resolvedBaseUrl,
            ).models()
                .list()
                .data()
                .map { 
                    DiscoveredApiModel(
                        id = it.id(),
                        created = it.created()
                    )
                }
                .distinctBy(DiscoveredApiModel::id)
                .sortedBy(DiscoveredApiModel::id)
        }
    }

    override suspend fun fetchModel(
        provider: ApiProvider,
        apiKey: String,
        modelId: String,
        baseUrl: String?
    ): DiscoveredApiModel? = withContext(Dispatchers.IO) {
        val resolvedBaseUrl = baseUrl?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl()
        when (provider) {
            ApiProvider.XAI -> fetchXaiModelDetail(
                apiKey = apiKey,
                baseUrl = checkNotNull(resolvedBaseUrl),
                modelId = modelId,
            )

            else -> null
        }
    }

    private fun toGoogleDiscoveredModel(model: Model): DiscoveredApiModel =
        DiscoveredApiModel(
            id = normalizeGoogleModelName(model),
            contextWindowTokens = model.inputTokenLimit().orElse(null),
            maxOutputTokens = model.outputTokenLimit().orElse(null),
        )

    private fun normalizeGoogleModelName(model: Model): String =
        model.name()
            .orElse("")
            .removePrefix("models/")
            .removePrefix("publishers/google/models/")
            .substringAfterLast('/')

    private fun fetchOpenRouterModels(
        apiKey: String,
        baseUrl: String,
    ): List<DiscoveredApiModel> {
        val trimmedApiKey = apiKey.trim()
        val httpUrl = baseUrl.trimEnd('/').toHttpUrl()
        val url = httpUrl.newBuilder()
            .apply {
                val segments = httpUrl.pathSegments
                if (segments.isNotEmpty() && segments.last() == "models") {
                    removePathSegment(segments.size - 1)
                }
            }
            .addPathSegments("models/user")
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $trimmedApiKey")
            .header("User-Agent", "PocketCrew")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body.string()
                error("OpenRouter model discovery failed with HTTP ${response.code}: $errorBody")
            }
            val body = response.body.string()
            return parseOpenRouterModels(body)
        }
    }

    internal fun parseOpenRouterModels(responseBody: String): List<DiscoveredApiModel> {
        val response = json.decodeFromString<OpenRouterModelsResponse>(responseBody)
        return response.data.map { model ->
            DiscoveredApiModel(
                id = model.id,
                name = model.name?.takeIf { it.isNotBlank() },
                created = model.created,
                promptPrice = model.pricing?.prompt?.toDoubleOrNull()?.asUsdPerMillionFromPerToken(),
                completionPrice = model.pricing?.completion?.toDoubleOrNull()?.asUsdPerMillionFromPerToken(),
                contextWindowTokens = model.context_length,
                maxOutputTokens = model.top_provider?.max_completion_tokens,
                isMultimodal = model.architecture?.input_modalities?.any { it.equals("image", ignoreCase = true) } ?: false,
            )
        }
            .distinctBy(DiscoveredApiModel::id)
            .sortedBy(DiscoveredApiModel::id)
    }

    private fun fetchXaiModels(
        apiKey: String,
        baseUrl: String,
    ): List<DiscoveredApiModel> {
        val trimmedApiKey = apiKey.trim()
        val httpUrl = baseUrl.trimEnd('/').toHttpUrl()
        val url = httpUrl.newBuilder()
            .apply {
                val segments = httpUrl.pathSegments
                if (segments.isNotEmpty() && (segments.last() == "models" || segments.last() == "language-models")) {
                    removePathSegment(segments.size - 1)
                }
            }
            .addPathSegments("language-models")
            .build()
            
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $trimmedApiKey")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body.string()
                Log.e(TAG, "xAI model discovery (/v1/language-models) failed with HTTP ${response.code}: $errorBody")
                // Fallback to /v1/models if /v1/language-models fails (e.g. they don't support it yet via this proxy)
                return fetchXaiFallbackModels(trimmedApiKey, baseUrl)
            }
            val body = response.body.string()
            return parseXaiModels(body)
        }
    }
    
    private fun fetchXaiFallbackModels(apiKey: String, baseUrl: String): List<DiscoveredApiModel> {
        val trimmedApiKey = apiKey.trim()
        val httpUrl = baseUrl.trimEnd('/').toHttpUrl()
        val url = httpUrl.newBuilder()
            .apply {
                val segments = httpUrl.pathSegments
                if (segments.isNotEmpty() && (segments.last() == "models" || segments.last() == "language-models")) {
                    removePathSegment(segments.size - 1)
                }
            }
            .addPathSegments("models")
            .build()
            
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $trimmedApiKey")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body.string()
                error("xAI model discovery failed with HTTP ${response.code}: $errorBody")
            }
            val body = response.body.string()
            return parseXaiModels(body)
        }
    }

    private fun fetchXaiModelDetail(
        apiKey: String,
        baseUrl: String,
        modelId: String,
    ): DiscoveredApiModel? {
        val trimmedApiKey = apiKey.trim()
        val httpUrl = baseUrl.trimEnd('/').toHttpUrl()
        val url = httpUrl.newBuilder()
            .apply {
                val segments = httpUrl.pathSegments
                if (segments.isNotEmpty() && (segments.last() == "models" || segments.last() == "language-models")) {
                    removePathSegment(segments.size - 1)
                }
            }
            .addPathSegments("language-models")
            .addPathSegment(modelId)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $trimmedApiKey")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body.string()
                Log.e(TAG, "xAI model detail fetch failed with HTTP ${response.code}: $errorBody")
                return null
            }
            return parseXaiModelDetail(
                responseBody = response.body.string(),
                requestedModelId = modelId,
            )
        }
    }

    internal fun parseXaiModels(responseBody: String): List<DiscoveredApiModel> {
        val response = json.decodeFromString<XaiModelsResponse>(responseBody)
        val data = response.models ?: response.data ?: return emptyList()

        return data.map { model ->
            val promptPrice = model.prompt_text_token_price ?: model.prompt_token_price
            val completionPrice = model.completion_text_token_price ?: model.completion_token_price

            DiscoveredApiModel(
                id = model.id,
                name = model.name?.takeIf { it.isNotBlank() } ?: model.id,
                created = model.created,
                promptPrice = promptPrice?.asUsdPerMillionFromXai(),
                completionPrice = completionPrice?.asUsdPerMillionFromXai(),
                contextWindowTokens = model.max_prompt_length,
                isMultimodal = model.input_modalities.any { it.equals("image", ignoreCase = true) },
            )
        }
            .distinctBy(DiscoveredApiModel::id)
            .sortedBy(DiscoveredApiModel::id)
    }

    internal fun parseXaiModelDetail(
        responseBody: String,
        requestedModelId: String,
    ): DiscoveredApiModel? {
        val model = json.decodeFromString<XaiModel>(responseBody)
        val canonicalId = model.id.takeIf { it.isNotBlank() } ?: return null
        val matchedAlias = model.aliases.firstOrNull { it == requestedModelId }

        val promptPrice = model.prompt_text_token_price ?: model.prompt_token_price
        val completionPrice = model.completion_text_token_price ?: model.completion_token_price

        return DiscoveredApiModel(
            id = matchedAlias ?: requestedModelId.takeIf { it.isNotBlank() } ?: canonicalId,
            name = model.name?.takeIf { it.isNotBlank() } ?: matchedAlias ?: canonicalId,
            created = model.created,
            promptPrice = promptPrice?.asUsdPerMillionFromXai(),
            completionPrice = completionPrice?.asUsdPerMillionFromXai(),
            contextWindowTokens = model.max_prompt_length ?: model.context_length,
            isMultimodal = model.input_modalities.any { it.equals("image", ignoreCase = true) },
        )
    }

    private fun Double.asUsdPerMillionFromPerToken(): Double? =
        takeIf { isFinite() && this >= 0.0 }
            ?.times(1_000_000.0)

    private fun Double.asUsdPerMillionFromXai(): Double? =
        takeIf { isFinite() && this >= 0.0 }
            ?.div(10_000.0)
}
