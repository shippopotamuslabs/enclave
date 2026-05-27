package com.browntowndev.enclave.feature.inference.repository

import android.util.Log
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogPort
import com.browntowndev.enclave.feature.inference.anthropic.AnthropicSdkBaseUrl
import com.browntowndev.enclave.feature.inference.google.GoogleGenAiClientProvider
import com.browntowndev.enclave.feature.inference.openai.OpenAiClientProvider
import com.google.genai.types.Model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class OpenRouterModelsResponse(
    val data: List<OpenRouterModel> = emptyList(),
)

@Serializable
private data class OpenRouterModel(
    val id: String,
    val name: String? = null,
    val created: Long? = null,
    val pricing: OpenRouterPricing? = null,
    val context_length: Int? = null,
    val top_provider: OpenRouterTopProvider? = null,
    val architecture: OpenRouterArchitecture? = null,
)

@Serializable
private data class OpenRouterPricing(
    val prompt: String? = null,
    val completion: String? = null,
)

@Serializable
private data class OpenRouterTopProvider(
    val max_completion_tokens: Int? = null,
)

@Serializable
private data class OpenRouterArchitecture(
    val input_modalities: List<String> = emptyList(),
)

@Serializable
private data class AnthropicModelsResponse(
    val data: List<AnthropicModel> = emptyList(),
)

@Serializable
private data class AnthropicModel(
    val id: String,
    val name: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    val created: Long? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("context_window")
    val contextWindow: Int? = null,
    @SerialName("context_window_tokens")
    val contextWindowTokens: Int? = null,
    @SerialName("context_length")
    val contextLength: Int? = null,
    @SerialName("max_context_length")
    val maxContextLength: Int? = null,
    @SerialName("max_prompt_length")
    val maxPromptLength: Int? = null,
    @SerialName("max_output_tokens")
    val maxOutputTokens: Int? = null,
    val capabilities: AnthropicModelCapabilities? = null,
)

@Serializable
private data class AnthropicModelCapabilities(
    @SerialName("image_input")
    val imageInput: AnthropicCapabilitySupport? = null,
)

@Serializable
private data class AnthropicCapabilitySupport(
    val supported: Boolean? = null,
)

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

@Singleton
class ApiModelCatalogRepositoryImpl
    @Inject
    constructor(
        private val openAiClientProvider: OpenAiClientProvider,
        private val googleGenAiClientProvider: GoogleGenAiClientProvider,
        private val httpClient: Call.Factory,
    ) : ApiModelCatalogPort {
        private companion object {
            private const val TAG = "ApiModelCatalog"
            private const val PER_TOKEN_TO_PER_MILLION = 1_000_000.0
            private const val XAI_PER_TOKEN_TO_PER_MILLION = 10_000.0
            private const val XAI_LANGUAGE_MODELS_PATH = "language-models"
            private const val XAI_IMAGE_MODELS_PATH = "image-generation-models"
            private const val XAI_VIDEO_MODELS_PATH = "video-generation-models"
            private val json = Json { ignoreUnknownKeys = true }
        }

        override suspend fun fetchModels(
            provider: ApiProvider,
            apiKey: String,
            baseUrl: String?,
        ): List<DiscoveredApiModel> =
            withContext(Dispatchers.IO) {
                require(
                    provider == ApiProvider.OPENAI ||
                        provider == ApiProvider.ANTHROPIC ||
                        provider == ApiProvider.GOOGLE ||
                        provider == ApiProvider.OPENROUTER ||
                        provider == ApiProvider.XAI,
                ) {
                    "Model discovery is currently supported for OpenAI, Anthropic, Google, OpenRouter, and xAI."
                }
                val resolvedBaseUrl = baseUrl?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl()

                when (provider) {
                    ApiProvider.ANTHROPIC ->
                        fetchAnthropicModels(
                            apiKey = apiKey,
                            baseUrl = resolvedBaseUrl,
                        )

                    ApiProvider.GOOGLE ->
                        googleGenAiClientProvider
                            .listModels(
                                apiKey = apiKey,
                                baseUrl = resolvedBaseUrl,
                            ).asSequence()
                            .mapNotNull { toGoogleDiscoveredModel(it) }
                            .filter { it.id.isNotBlank() }
                            .distinctBy(DiscoveredApiModel::id)
                            .sortedBy(DiscoveredApiModel::id)
                            .toList()

                    ApiProvider.OPENROUTER ->
                        fetchOpenRouterModels(
                            apiKey = apiKey,
                            baseUrl = checkNotNull(resolvedBaseUrl),
                        )

                    ApiProvider.XAI ->
                        fetchXaiModels(
                            apiKey = apiKey,
                            baseUrl = checkNotNull(resolvedBaseUrl),
                        )

                    else ->
                        openAiClientProvider
                            .getClient(
                                apiKey = apiKey,
                                baseUrl = resolvedBaseUrl,
                            ).models()
                            .list()
                            .data()
                            .mapNotNull {
                                DiscoveredApiModel(
                                    id = it.id(),
                                    created = it.created(),
                                ).applyCapabilities(provider)
                            }.distinctBy(DiscoveredApiModel::id)
                            .sortedBy(DiscoveredApiModel::id)
                }
            }

        override suspend fun fetchModel(
            provider: ApiProvider,
            apiKey: String,
            modelId: String,
            baseUrl: String?,
        ): DiscoveredApiModel? =
            withContext(Dispatchers.IO) {
                val resolvedBaseUrl = baseUrl?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl()
                when (provider) {
                    ApiProvider.XAI ->
                        fetchXaiModelDetail(
                            apiKey = apiKey,
                            baseUrl = checkNotNull(resolvedBaseUrl),
                            modelId = modelId,
                        )?.applyCapabilities(provider)

                    else -> null
                }
            }

        private fun toGoogleDiscoveredModel(model: Model): DiscoveredApiModel? =
            DiscoveredApiModel(
                id = normalizeGoogleModelName(model),
                contextWindowTokens = model.inputTokenLimit().orElse(null),
                maxOutputTokens = model.outputTokenLimit().orElse(null),
            ).applyCapabilities(
                provider = ApiProvider.GOOGLE,
                supportedGenerationMethods = model.supportedActions().orElse(emptyList()),
            )

        private fun normalizeGoogleModelName(model: Model): String =
            model
                .name()
                .orElse("")
                .removePrefix("models/")
                .removePrefix("publishers/google/models/")
                .substringAfterLast('/')

        private fun fetchAnthropicModels(
            apiKey: String,
            baseUrl: String?,
        ): List<DiscoveredApiModel> {
            val trimmedApiKey = apiKey.trim()
            val url = buildAnthropicModelsUrl(baseUrl)
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .header("x-api-key", trimmedApiKey)
                    .header("Authorization", "Bearer $trimmedApiKey")
                    .header("anthropic-version", "2023-06-01")
                    .header("Accept", "application/json")
                    .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body.string()
                    error("Anthropic model discovery failed with HTTP ${response.code}: $errorBody")
                }
                return parseAnthropicModels(response.body.string())
            }
        }

        private fun buildAnthropicModelsUrl(baseUrl: String?): okhttp3.HttpUrl {
            val httpUrl = (AnthropicSdkBaseUrl.normalize(baseUrl) ?: "https://api.anthropic.com").toHttpUrl()
            val segments = httpUrl.pathSegments
            val lastSegment = segments.lastOrNull()
            val penultimateSegment = segments.dropLast(1).lastOrNull()
            val removeModelsSegment = lastSegment == "models"
            val removeVersionSegment = lastSegment == "v1" || (removeModelsSegment && penultimateSegment == "v1")
            return httpUrl
                .newBuilder()
                .apply {
                    if (removeModelsSegment) {
                        removePathSegment(segments.lastIndex)
                    }
                    if (removeVersionSegment) {
                        removePathSegment(if (removeModelsSegment) segments.lastIndex - 1 else segments.lastIndex)
                    }
                }.addPathSegments("v1/models")
                .build()
        }

        internal fun parseAnthropicModels(responseBody: String): List<DiscoveredApiModel> {
            val response = json.decodeFromString<AnthropicModelsResponse>(responseBody)
            return response.data
                .mapNotNull { model ->
                    val displayName =
                        model.displayName?.takeIf { it.isNotBlank() }
                            ?: model.name?.takeIf { it.isNotBlank() }
                            ?: model.id.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                    DiscoveredApiModel(
                        id = model.id,
                        name = displayName,
                        created = model.created ?: model.createdAt?.toEpochSecondOrNull(),
                        contextWindowTokens =
                            model.contextWindowTokens
                                ?: model.contextWindow
                                ?: model.contextLength
                                ?: model.maxContextLength
                                ?: model.maxPromptLength,
                        maxOutputTokens = model.maxOutputTokens,
                        isMultimodal = model.capabilities?.imageInput?.supported,
                    ).applyCapabilities(ApiProvider.ANTHROPIC)
                }.distinctBy(DiscoveredApiModel::id)
                .sortedBy(DiscoveredApiModel::id)
        }

        private fun fetchOpenRouterModels(
            apiKey: String,
            baseUrl: String,
        ): List<DiscoveredApiModel> {
            val trimmedApiKey = apiKey.trim()
            val httpUrl = baseUrl.trimEnd('/').toHttpUrl()
            val url =
                httpUrl
                    .newBuilder()
                    .apply {
                        val segments = httpUrl.pathSegments
                        if (segments.isNotEmpty() && segments.last() == "models") {
                            removePathSegment(segments.size - 1)
                        }
                    }.addPathSegments("models/user")
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $trimmedApiKey")
                    .header("User-Agent", "Enclave")
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
            return response.data
                .mapNotNull { model ->
                    DiscoveredApiModel(
                        id = model.id,
                        name = model.name?.takeIf { it.isNotBlank() },
                        created = model.created,
                        promptPrice =
                            model.pricing
                                ?.prompt
                                ?.toDoubleOrNull()
                                ?.asUsdPerMillionFromPerToken(),
                        completionPrice =
                            model.pricing
                                ?.completion
                                ?.toDoubleOrNull()
                                ?.asUsdPerMillionFromPerToken(),
                        contextWindowTokens = model.context_length,
                        maxOutputTokens = model.top_provider?.max_completion_tokens,
                        isMultimodal = model.architecture?.input_modalities?.any { it.equals("image", ignoreCase = true) } ?: false,
                    ).applyCapabilities(
                        provider = ApiProvider.OPENROUTER,
                        supportedGenerationMethods = model.architecture?.input_modalities.orEmpty(),
                    )
                }.distinctBy(DiscoveredApiModel::id)
                .sortedBy(DiscoveredApiModel::id)
        }

        private suspend fun fetchXaiModels(
            apiKey: String,
            baseUrl: String,
        ): List<DiscoveredApiModel> =
            coroutineScope {
                val trimmedApiKey = apiKey.trim()
                val languageModelsDeferred =
                    async {
                        fetchXaiModelsFromEndpoint(trimmedApiKey, baseUrl, XAI_LANGUAGE_MODELS_PATH, isChat = true)
                    }
                val imageModelsDeferred =
                    async {
                        fetchXaiModelsFromEndpoint(trimmedApiKey, baseUrl, XAI_IMAGE_MODELS_PATH, isImageGen = true)
                    }
                val videoModelsDeferred =
                    async {
                        fetchXaiModelsFromEndpoint(trimmedApiKey, baseUrl, XAI_VIDEO_MODELS_PATH, isVideoGen = true)
                    }

                val results =
                    listOf(
                        languageModelsDeferred.await(),
                        imageModelsDeferred.await(),
                        videoModelsDeferred.await(),
                    ).flatten()

                if (results.isEmpty()) {
                    fetchXaiFallbackModels(trimmedApiKey, baseUrl)
                } else {
                    results
                        .distinctBy(DiscoveredApiModel::id)
                        .sortedBy(DiscoveredApiModel::id)
                }
            }

        private fun fetchXaiModelsFromEndpoint(
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
                        val xaiEndpoints = listOf("models", XAI_LANGUAGE_MODELS_PATH, XAI_IMAGE_MODELS_PATH, XAI_VIDEO_MODELS_PATH)
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
                return parseXaiModels(body, isChat = isChat, isImageGen = isImageGen, isVideoGen = isVideoGen)
            }
        }

        private fun fetchXaiFallbackModels(
            apiKey: String,
            baseUrl: String,
        ): List<DiscoveredApiModel> {
            val trimmedApiKey = apiKey.trim()
            val httpUrl = baseUrl.trimEnd('/').toHttpUrl()
            val url =
                httpUrl
                    .newBuilder()
                    .apply {
                        val segments = httpUrl.pathSegments
                        val xaiEndpoints = listOf("models", XAI_LANGUAGE_MODELS_PATH, XAI_IMAGE_MODELS_PATH, XAI_VIDEO_MODELS_PATH)
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
            val endpoints = listOf(XAI_LANGUAGE_MODELS_PATH, XAI_IMAGE_MODELS_PATH, XAI_VIDEO_MODELS_PATH)

            for (endpoint in endpoints) {
                val httpUrl = baseUrl.trimEnd('/').toHttpUrl()
                val url =
                    httpUrl
                        .newBuilder()
                        .apply {
                            val segments = httpUrl.pathSegments
                            val xaiEndpoints = listOf("models", XAI_LANGUAGE_MODELS_PATH, XAI_IMAGE_MODELS_PATH, XAI_VIDEO_MODELS_PATH)
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
                        return parseXaiModelDetail(
                            responseBody = response.body.string(),
                            requestedModelId = modelId,
                            isChat = endpoint == XAI_LANGUAGE_MODELS_PATH,
                            isImageGen = endpoint == XAI_IMAGE_MODELS_PATH,
                            isVideoGen = endpoint == XAI_VIDEO_MODELS_PATH,
                        )
                    }
                }
            }

            Log.e(TAG, "xAI model detail fetch failed for $modelId across all endpoints")
            return null
        }

        internal fun parseXaiModels(
            responseBody: String,
            isChat: Boolean = false,
            isImageGen: Boolean = false,
            isVideoGen: Boolean = false,
        ): List<DiscoveredApiModel> {
            val response = json.decodeFromString<XaiModelsResponse>(responseBody)
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
                    ).applyCapabilities(
                        provider = ApiProvider.XAI,
                        supportedGenerationMethods = model.input_modalities,
                        isChatOverride = isChat,
                        isImageGenOverride = isImageGen,
                        isVideoGenOverride = isVideoGen,
                    )
                }.distinctBy(DiscoveredApiModel::id)
                .sortedBy(DiscoveredApiModel::id)
        }

        internal fun parseXaiModelDetail(
            responseBody: String,
            requestedModelId: String,
            isChat: Boolean = false,
            isImageGen: Boolean = false,
            isVideoGen: Boolean = false,
        ): DiscoveredApiModel? {
            val model = json.decodeFromString<XaiModel>(responseBody)
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
            ).applyCapabilities(
                provider = ApiProvider.XAI,
                supportedGenerationMethods = model.input_modalities,
                isChatOverride = isChat,
                isImageGenOverride = isImageGen,
                isVideoGenOverride = isVideoGen,
            )
        }

        private fun Double.asUsdPerMillionFromPerToken(): Double? =
            takeIf { isFinite() && this >= 0.0 }
                ?.times(PER_TOKEN_TO_PER_MILLION)

        private fun Double.asUsdPerMillionFromXai(): Double? =
            takeIf { isFinite() && this >= 0.0 }
                ?.div(XAI_PER_TOKEN_TO_PER_MILLION)

        private fun String.toEpochSecondOrNull(): Long? =
            try {
                OffsetDateTime.parse(this).toEpochSecond()
            } catch (_: DateTimeParseException) {
                null
            }

        @Suppress("CyclomaticComplexMethod")
        private fun DiscoveredApiModel.applyCapabilities(
            provider: ApiProvider,
            supportedGenerationMethods: List<String> = emptyList(),
            isChatOverride: Boolean? = null,
            isImageGenOverride: Boolean? = null,
            isVideoGenOverride: Boolean? = null,
        ): DiscoveredApiModel? {
            // Tier 2: Centralized Blacklist (Exclude legacy/unsupported models)
            val isBlacklisted =
                when (provider) {
                    ApiProvider.OPENAI -> {
                        id.contains("dall-e-2", ignoreCase = true) ||
                            id.startsWith("whisper", ignoreCase = true) ||
                            id.startsWith("tts-", ignoreCase = true) ||
                            id.contains("canary", ignoreCase = true)
                    }
                    ApiProvider.GOOGLE -> {
                        id.contains("imagen-1", ignoreCase = true) ||
                            id.contains("imagen-2", ignoreCase = true) ||
                            id.contains("aqa", ignoreCase = true)
                    }
                    else -> false
                }
            if (isBlacklisted) return null

            // Tier 1: Capability Detection (Identify image/video/music/chat)
            val isImageGen =
                when (provider) {
                    ApiProvider.OPENAI -> id.contains("dall-e-3", ignoreCase = true) || id.contains("gpt-image-", ignoreCase = true)
                    ApiProvider.GOOGLE -> {
                        id.contains("imagen-3", ignoreCase = true) ||
                            id.contains("nano-banana", ignoreCase = true) ||
                            (
                                supportedGenerationMethods.any { it.equals("predict", ignoreCase = true) } &&
                                    id.contains("imagen", ignoreCase = true)
                            )
                    }
                    ApiProvider.XAI -> isImageGenOverride ?: false
                    else -> false
                }

            val isVideoGen =
                when (provider) {
                    ApiProvider.GOOGLE -> {
                        id.contains("veo", ignoreCase = true) ||
                            supportedGenerationMethods.any { it.equals("predictLongRunning", ignoreCase = true) }
                    }
                    ApiProvider.XAI -> isVideoGenOverride ?: false
                    else -> false
                }

            val isMusicGen =
                when (provider) {
                    ApiProvider.GOOGLE -> id.contains("lyria", ignoreCase = true)
                    else -> false
                }

            // Chat capability detection
            val isMediaOnly =
                (isImageGen || isVideoGen || isMusicGen) &&
                    !id.contains("gemini", ignoreCase = true) &&
                    !id.contains("gpt-4", ignoreCase = true)

            val isChat =
                isChatOverride ?: (
                    !isMediaOnly &&
                        !id.startsWith("whisper", ignoreCase = true) &&
                        !id.startsWith("tts-", ignoreCase = true)
                )

            return copy(
                isChatCapable = isChat,
                isImageGenerationCapable = isImageGen,
                isVideoGenerationCapable = isVideoGen,
                isAudioGenerationCapable = isMusicGen,
            )
        }
    }
