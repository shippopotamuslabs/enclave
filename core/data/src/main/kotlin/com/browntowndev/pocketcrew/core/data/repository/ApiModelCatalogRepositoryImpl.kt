package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.anthropic.AnthropicClientProvider
import com.browntowndev.pocketcrew.core.data.google.GoogleGenAiClientProvider
import com.browntowndev.pocketcrew.core.data.openai.OpenAiClientProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelCatalogPort
import com.google.genai.types.Model
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

@Singleton
class ApiModelCatalogRepositoryImpl(
    private val openAiClientProvider: OpenAiClientProvider,
    private val anthropicClientProvider: AnthropicClientProvider,
    private val googleGenAiClientProvider: GoogleGenAiClientProvider,
    private val httpClient: Call.Factory,
) : ApiModelCatalogPort {
    @Inject
    constructor(
        openAiClientProvider: OpenAiClientProvider,
        anthropicClientProvider: AnthropicClientProvider,
        googleGenAiClientProvider: GoogleGenAiClientProvider,
    ) : this(
        openAiClientProvider = openAiClientProvider,
        anthropicClientProvider = anthropicClientProvider,
        googleGenAiClientProvider = googleGenAiClientProvider,
        httpClient = OkHttpClient(),
    )

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
                        created = it.createdAt()?.toEpochSecond()
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
        val url = baseUrl
            .toHttpUrl()
            .newBuilder()
            .addPathSegments("models/user")
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", "PocketCrew")
            .build()

        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "OpenRouter model discovery failed with HTTP ${response.code}"
            }
            val body = response.body.string()
            return parseOpenRouterModels(body)
        }
    }

    internal fun parseOpenRouterModels(responseBody: String): List<DiscoveredApiModel> {
        val data = JSONObject(responseBody).optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until data.length()) {
                val model = data.optJSONObject(index) ?: continue
                val id = model.optString("id")
                if (id.isBlank()) continue
                
                val pricing = model.optJSONObject("pricing")
                
                add(
                    DiscoveredApiModel(
                        id = id,
                        name = model.optString("name").takeIf { it.isNotBlank() },
                        created = model.optLongOrNull("created"),
                        promptPrice = pricing?.optString("prompt")?.toDoubleOrNull(),
                        completionPrice = pricing?.optString("completion")?.toDoubleOrNull(),
                        contextWindowTokens = model.optIntOrNull("context_length"),
                        maxOutputTokens = model
                            .optJSONObject("top_provider")
                            ?.optIntOrNull("max_completion_tokens"),
                    )
                )
            }
        }
            .distinctBy(DiscoveredApiModel::id)
            .sortedBy(DiscoveredApiModel::id)
    }

    private fun fetchXaiModels(
        apiKey: String,
        baseUrl: String,
    ): List<DiscoveredApiModel> {
        val url = baseUrl
            .toHttpUrl()
            .newBuilder()
            // We use /v1/language-models or similar endpoint that returns pricing/modalities
            // If baseUrl is https://api.x.ai/v1, we need to replace the last segment or just append
            // But since baseUrl is usually configured to end with /v1, we'll replace the last segment "models" if it exists,
            // but the `baseUrl` here is just the base, it's not the full URL.
            // Wait, the resolvedBaseUrl is passed from defaultBaseUrl() which is "https://api.x.ai/v1"
            .addPathSegments("language-models")
            .build()
            
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", "PocketCrew")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // Fallback to /v1/models if /v1/language-models fails (e.g. they don't support it yet via this proxy)
                return fetchXaiFallbackModels(apiKey, baseUrl)
            }
            val body = response.body.string()
            return parseXaiModels(body)
        }
    }
    
    private fun fetchXaiFallbackModels(apiKey: String, baseUrl: String): List<DiscoveredApiModel> {
        val url = baseUrl
            .toHttpUrl()
            .newBuilder()
            .addPathSegments("models")
            .build()
            
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", "PocketCrew")
            .build()

        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "xAI model discovery failed with HTTP ${response.code}"
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
        val url = baseUrl
            .toHttpUrl()
            .newBuilder()
            .addPathSegments("language-models")
            .addPathSegment(modelId)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", "PocketCrew")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return null
            }
            return parseXaiModelDetail(
                responseBody = response.body.string(),
                requestedModelId = modelId,
            )
        }
    }

    internal fun parseXaiModels(responseBody: String): List<DiscoveredApiModel> {
        val payload = JSONObject(responseBody)
        val data = payload.optJSONArray("models") ?: payload.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until data.length()) {
                val model = data.optJSONObject(index) ?: continue
                val id = model.optString("id")
                if (id.isBlank()) continue
                
                val promptPrice = model.optDoubleOrNull("prompt_text_token_price")
                    ?: model.optDoubleOrNull("prompt_token_price")
                val completionPrice = model.optDoubleOrNull("completion_text_token_price")
                    ?: model.optDoubleOrNull("completion_token_price")

                add(
                    DiscoveredApiModel(
                        id = id,
                        name = model.optString("name").takeIf { it.isNotBlank() } ?: id,
                        created = model.optLongOrNull("created"),
                        promptPrice = promptPrice,
                        completionPrice = completionPrice,
                        contextWindowTokens = model.optIntOrNull("max_prompt_length"),
                    )
                )
            }
        }
            .distinctBy(DiscoveredApiModel::id)
            .sortedBy(DiscoveredApiModel::id)
    }

    internal fun parseXaiModelDetail(
        responseBody: String,
        requestedModelId: String,
    ): DiscoveredApiModel? {
        val model = JSONObject(responseBody)
        val canonicalId = model.optString("id").takeIf { it.isNotBlank() } ?: return null
        val aliases = model.optJSONArray("aliases")
        val matchedAlias = aliases
            ?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val alias = array.optString(index)
                        if (alias.isNotBlank()) {
                            add(alias)
                        }
                    }
                }
            }
            ?.firstOrNull { it == requestedModelId }

        val promptPrice = model.optDoubleOrNull("prompt_text_token_price")
            ?: model.optDoubleOrNull("prompt_token_price")
        val completionPrice = model.optDoubleOrNull("completion_text_token_price")
            ?: model.optDoubleOrNull("completion_token_price")

        return DiscoveredApiModel(
            id = matchedAlias ?: requestedModelId.takeIf { it.isNotBlank() } ?: canonicalId,
            name = model.optString("name").takeIf { it.isNotBlank() } ?: matchedAlias ?: canonicalId,
            created = model.optLongOrNull("created"),
            promptPrice = promptPrice,
            completionPrice = completionPrice,
            contextWindowTokens = model.optIntOrNull("max_prompt_length")
                ?: model.optIntOrNull("context_length"),
        )
    }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) {
            optInt(name)
        } else {
            null
        }

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (has(name) && !isNull(name)) {
            optLong(name)
        } else {
            null
        }

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (has(name) && !isNull(name)) {
            optDouble(name)
        } else {
            null
        }
}
