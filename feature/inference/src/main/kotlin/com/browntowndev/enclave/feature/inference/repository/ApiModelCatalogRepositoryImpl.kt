package com.browntowndev.enclave.feature.inference.repository

import android.util.Log
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.config.OpenRouterModelEndpoint
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.model.media.ImageInputRequirement
import com.browntowndev.enclave.domain.model.media.ModelGenerationInputSchema
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogPort
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogResult
import com.browntowndev.enclave.feature.inference.fal.FalOpenApiInputSchemaParser
import com.browntowndev.enclave.feature.inference.fal.FalOpenApiOutputSchemaParser
import com.browntowndev.enclave.feature.inference.google.GoogleGenAiClientProvider
import com.browntowndev.enclave.feature.inference.openai.OpenAiClientProvider
import com.google.genai.types.Model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

internal fun deriveFalMediaTasks(
    capability: MediaCapability,
    inputSchema: ModelGenerationInputSchema?,
    endpointId: String? = null,
    category: String? = null,
): Set<MediaTask> =
    deriveFalTasks(
        modelId = endpointId.orEmpty(),
        category = category,
        isImageGen = capability == MediaCapability.IMAGE,
        isVideoGen = capability == MediaCapability.VIDEO,
        imageInput = inputSchema?.imageInput,
    )

@Serializable
private data class FalModelsResponse(
    val models: List<FalModel> = emptyList(),
    val data: List<FalModel> = emptyList(),
    val cursor: String? = null,
    @kotlinx.serialization.SerialName("next_cursor")
    val nextCursor: String? = null,
)

@Serializable
private data class FalModelMetadata(
    @kotlinx.serialization.SerialName("display_name")
    val displayName: String? = null,
    val category: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
private data class FalModel(
    val id: String? = null,
    @kotlinx.serialization.SerialName("endpoint_id")
    val endpointId: String? = null,
    val name: String? = null,
    val category: String? = null,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val metadata: FalModelMetadata? = null,
    val openapi: JsonObject? = null,
)

private fun FalModel.resolvedId(): String? = endpointId?.takeIf { it.isNotBlank() } ?: id?.takeIf { it.isNotBlank() }

private const val FAL_MODELS_URL = "https://api.fal.ai/v1/models"
private const val FAL_MAX_PAGES = 50

private fun getFalModelsUrl(baseUrl: String?): String {
    val trimmed = baseUrl?.trim()?.removeSuffix("/").orEmpty()
    if (
        trimmed.isBlank() ||
        trimmed.equals("https://queue.fal.run", ignoreCase = true) ||
        trimmed.equals("https://fal.run", ignoreCase = true)
    ) {
        return FAL_MODELS_URL
    }
    return if (trimmed.endsWith("/v1/models") || trimmed.endsWith("/models")) {
        trimmed
    } else {
        "$trimmed/v1/models"
    }
}

private val featuredFalModelIds =
    listOf(
        DiscoveredApiModel(id = "fal-ai/flux-pro/kontext", name = "FLUX Pro Kontext"),
        DiscoveredApiModel(id = "fal-ai/recraft/v3", name = "Recraft V3"),
        DiscoveredApiModel(id = "fal-ai/kling-video/v2.1/master/image-to-video", name = "Kling Video 2.1"),
        DiscoveredApiModel(id = "fal-ai/minimax/video-01", name = "MiniMax Video 01"),
        DiscoveredApiModel(id = "fal-ai/minimax/speech-02-hd", name = "MiniMax Speech 02 HD"),
        DiscoveredApiModel(id = "fal-ai/stable-audio", name = "Stable Audio"),
        DiscoveredApiModel(id = "fal-ai/ace-step", name = "ACE-Step"),
    )

private fun fetchFalModels(
    httpClient: Call.Factory,
    apiKey: String,
    baseUrl: String?,
): Pair<List<DiscoveredApiModel>, Boolean> {
    val trimmedApiKey = apiKey.trim()
    val discovered = mutableListOf<DiscoveredApiModel>()
    val modelsUrl = getFalModelsUrl(baseUrl)
    var cursor: String? = null
    repeat(FAL_MAX_PAGES) { pageIndex ->
        val url =
            modelsUrl
                .toHttpUrl()
                .newBuilder()
                .apply {
                    cursor?.takeIf { it.isNotBlank() }?.let { addQueryParameter("cursor", it) }
                }.build()
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Authorization", "Key $trimmedApiKey")
                .header("Accept", "application/json")
                .build()

        val responseBody =
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body.string()
                    error("fal.ai model discovery failed with HTTP ${response.code}: $errorBody")
                }
                response.body.string()
            }
        val response = apiModelCatalogJson.decodeFromString<FalModelsResponse>(responseBody)
        discovered += parseFalModels(response)
        cursor = response.nextCursor ?: response.cursor
        if (cursor.isNullOrBlank()) {
            return Pair(mergeFalFeaturedModels(discovered), false)
        }
        if (pageIndex == FAL_MAX_PAGES - 1) {
            Log.w("ApiModelCatalog", "FAL model discovery truncated at $FAL_MAX_PAGES pages. Some models might be missing.")
        }
    }
    return Pair(mergeFalFeaturedModels(discovered), true)
}

private fun parseFalModels(response: FalModelsResponse): List<DiscoveredApiModel> {
    val models = response.models.ifEmpty { response.data }
    return models
        .mapNotNull(FalModel::toDiscoveredApiModel)
        .distinctBy(DiscoveredApiModel::id)
        .sortedBy(DiscoveredApiModel::id)
}

@Suppress("ReturnCount")
private fun deriveFalTasks(
    modelId: String,
    category: String?,
    isImageGen: Boolean,
    isVideoGen: Boolean,
    imageInput: ImageInputRequirement?,
): Set<MediaTask> {
    if (category != null) {
        when (category) {
            "text-to-image" -> return setOf(MediaTask.TEXT_TO_IMAGE)
            "image-to-image" -> return setOf(MediaTask.EDIT_IMAGE)
            "text-to-video" -> return setOf(MediaTask.TEXT_TO_VIDEO)
            "image-to-video" -> return setOf(MediaTask.IMAGE_TO_VIDEO)
        }
    }

    val cleanId = modelId.lowercase()
    val isImageToVideo =
        listOf("/image-to-video", "/i2v", "img2vid", "img-to-video", "image2video")
            .any { cleanId.contains(it) }
    if (isImageToVideo) {
        return setOf(MediaTask.IMAGE_TO_VIDEO)
    }
    if (cleanId.contains("/text-to-video")) {
        return setOf(MediaTask.TEXT_TO_VIDEO)
    }
    if (cleanId.contains("/text-to-image")) {
        return setOf(MediaTask.TEXT_TO_IMAGE)
    }

    if (imageInput != null) {
        if (isImageGen) {
            when (imageInput) {
                ImageInputRequirement.NONE -> return setOf(MediaTask.TEXT_TO_IMAGE)
                ImageInputRequirement.REQUIRED -> return setOf(MediaTask.EDIT_IMAGE)
                ImageInputRequirement.OPTIONAL -> return setOf(MediaTask.TEXT_TO_IMAGE, MediaTask.EDIT_IMAGE)
            }
        }
        if (isVideoGen) {
            when (imageInput) {
                ImageInputRequirement.NONE -> return setOf(MediaTask.TEXT_TO_VIDEO)
                ImageInputRequirement.REQUIRED -> return setOf(MediaTask.IMAGE_TO_VIDEO)
                ImageInputRequirement.OPTIONAL -> return setOf(MediaTask.TEXT_TO_VIDEO, MediaTask.IMAGE_TO_VIDEO)
            }
        }
    }

    val cleanModelId = modelId.trim().removeSuffix("/")
    when (cleanModelId) {
        "fal-ai/flux-pro/kontext" -> return setOf(MediaTask.TEXT_TO_IMAGE, MediaTask.EDIT_IMAGE)
        "fal-ai/recraft/v3" -> return setOf(MediaTask.TEXT_TO_IMAGE)
        "fal-ai/kling-video/v2.1/master/image-to-video" -> return setOf(MediaTask.IMAGE_TO_VIDEO)
        "fal-ai/minimax/video-01" -> return setOf(MediaTask.TEXT_TO_VIDEO)
    }

    return emptySet()
}

private val falTaskCategories =
    setOf(
        "text-to-image",
        "image-to-image",
        "text-to-video",
        "image-to-video",
    )

private fun String?.normalizedFalTaskCategory(): String? {
    val normalized = this?.trim()?.lowercase() ?: return null
    return normalized.takeIf { it in falTaskCategories }
}

private fun FalModel.resolvedTaskCategory(): String? =
    category.normalizedFalTaskCategory()
        ?: metadata?.category.normalizedFalTaskCategory()
        ?: categories.firstNotNullOfOrNull { it.normalizedFalTaskCategory() }

private fun FalModel.toDiscoveredApiModel(): DiscoveredApiModel? {
    val modelId = resolvedId() ?: return null
    val hints = capabilityHints()
    val isImageGen =
        hints.any { it.mediaHintMatches("image") } ||
            modelId.mediaHintMatchesAny("flux", "imagen", "seedream", "recraft", "ideogram", "stable-diffusion")
    val isVideoGen =
        hints.any { it.mediaHintMatches("video") } ||
            modelId.mediaHintMatchesAny("video", "kling", "seedance", "wan", "hailuo")
    val isAudioGen =
        hints.any { it.mediaHintMatches("audio") || it.mediaHintMatches("music") } ||
            modelId.mediaHintMatchesAny("audio", "music", "stable-audio", "ace-step", "tts", "speech", "elevenlabs")
    val isUnclassified = !isImageGen && !isVideoGen && !isAudioGen
    val displayName =
        name?.takeIf { it.isNotBlank() }
            ?: metadata?.displayName?.takeIf { it.isNotBlank() }
            ?: modelId
    val inputSchema = openapi?.let(FalOpenApiInputSchemaParser::parse)
    val outputSchema = openapi?.let(FalOpenApiOutputSchemaParser::parse)
    val derivedTasks =
        deriveFalTasks(
            modelId = modelId,
            category = resolvedTaskCategory(),
            isImageGen = isImageGen || isUnclassified,
            isVideoGen = isVideoGen,
            imageInput = inputSchema?.imageInput,
        )
    return DiscoveredApiModel(
        id = modelId,
        name = displayName,
        isChatCapable = false,
        isImageGenerationCapable = isImageGen || isUnclassified,
        isVideoGenerationCapable = isVideoGen,
        isAudioGenerationCapable = isAudioGen,
        tasks = derivedTasks,
        inputSchema = inputSchema,
        outputSchema = outputSchema,
    )
}

private fun mergeFalFeaturedModels(discovered: List<DiscoveredApiModel>): List<DiscoveredApiModel> =
    (
        discovered +
            featuredFalModelIds.mapNotNull {
                FalModel(id = it.id, name = it.name, category = it.name).toDiscoveredApiModel()
            }
    ).distinctBy(DiscoveredApiModel::id)
        .sortedBy(DiscoveredApiModel::id)

private fun FalModel.capabilityHints(): List<String> =
    buildList {
        category?.let(::add)
        metadata?.category?.let(::add)
        addAll(categories)
        addAll(tags)
        metadata?.tags?.let { addAll(it) }
        description?.let(::add)
        metadata?.description?.let(::add)
    }

@Singleton
class ApiModelCatalogRepositoryImpl
    @Inject
    constructor(
        private val openAiClientProvider: OpenAiClientProvider,
        private val googleGenAiClientProvider: GoogleGenAiClientProvider,
        private val httpClient: Call.Factory,
    ) : ApiModelCatalogPort {
        override suspend fun fetchModels(
            provider: ApiProvider,
            apiKey: String,
            baseUrl: String?,
        ): ApiModelCatalogResult =
            withContext(Dispatchers.IO) {
                require(ApiProviderModelPolicy.supportsModelDiscovery(provider)) {
                    "Model discovery is not supported for provider $provider."
                }
                val resolvedBaseUrl = baseUrl?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl()

                when (provider) {
                    ApiProvider.ANTHROPIC ->
                        ApiModelCatalogResult(
                            AnthropicModelCatalogSupport.fetchModels(
                                httpClient = httpClient,
                                apiKey = apiKey,
                                baseUrl = resolvedBaseUrl,
                            ),
                        )

                    ApiProvider.GOOGLE ->
                        ApiModelCatalogResult(
                            googleGenAiClientProvider
                                .listModels(apiKey = apiKey, baseUrl = resolvedBaseUrl)
                                .asSequence()
                                .mapNotNull { toGoogleDiscoveredModel(it) }
                                .filter { it.id.isNotBlank() }
                                .distinctBy(DiscoveredApiModel::id)
                                .sortedBy(DiscoveredApiModel::id)
                                .toList(),
                        )

                    ApiProvider.OPENROUTER ->
                        ApiModelCatalogResult(
                            OpenRouterModelCatalogSupport.fetchModels(
                                httpClient = httpClient,
                                apiKey = apiKey,
                                baseUrl = checkNotNull(resolvedBaseUrl),
                            ),
                        )

                    ApiProvider.XAI ->
                        ApiModelCatalogResult(
                            XaiModelCatalogSupport.fetchModels(
                                httpClient = httpClient,
                                apiKey = apiKey,
                                baseUrl = checkNotNull(resolvedBaseUrl),
                            ),
                        )

                    ApiProvider.FAL -> {
                        val (models, isTruncated) =
                            fetchFalModels(
                                httpClient = httpClient,
                                apiKey = apiKey,
                                baseUrl = resolvedBaseUrl,
                            )
                        ApiModelCatalogResult(models = models, isTruncated = isTruncated)
                    }

                    ApiProvider.DEEPSEEK, ApiProvider.ZAI ->
                        ApiModelCatalogResult(
                            OpenAiCompatibleModelCatalogSupport.fetchModels(
                                httpClient = httpClient,
                                apiKey = apiKey,
                                baseUrl = checkNotNull(resolvedBaseUrl),
                                provider = provider,
                            ),
                        )

                    else ->
                        ApiModelCatalogResult(
                            openAiClientProvider
                                .getClient(apiKey = apiKey, baseUrl = resolvedBaseUrl)
                                .models()
                                .list()
                                .data()
                                .mapNotNull {
                                    DiscoveredApiModel(
                                        id = it.id(),
                                        created = it.created(),
                                    ).applyCatalogCapabilities(provider)
                                }.distinctBy(DiscoveredApiModel::id)
                                .sortedBy(DiscoveredApiModel::id),
                        )
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
                    ApiProvider.FAL ->
                        fetchFalModelDetail(
                            apiKey = apiKey,
                            modelId = modelId,
                            baseUrl = resolvedBaseUrl,
                        )

                    ApiProvider.XAI ->
                        XaiModelCatalogSupport
                            .fetchModelDetail(
                                httpClient = httpClient,
                                apiKey = apiKey,
                                baseUrl = checkNotNull(resolvedBaseUrl),
                                modelId = modelId,
                            )?.applyCatalogCapabilities(provider)

                    else -> null
                }
            }

        override suspend fun fetchModelEndpoints(
            modelId: String,
            apiKey: String,
            baseUrl: String?,
        ): List<OpenRouterModelEndpoint> =
            withContext(Dispatchers.IO) {
                val resolvedBaseUrl =
                    baseUrl?.takeIf { it.isNotBlank() } ?: "https://openrouter.ai/api/v1"
                OpenRouterModelCatalogSupport.fetchModelEndpoints(
                    httpClient = httpClient,
                    apiKey = apiKey,
                    baseUrl = resolvedBaseUrl,
                    modelId = modelId,
                )
            }

        internal fun parseOpenRouterModelEndpoints(responseBody: String): List<OpenRouterModelEndpoint> =
            OpenRouterModelCatalogSupport.parseModelEndpoints(responseBody)

        private fun fetchFalModelDetail(
            apiKey: String,
            modelId: String,
            baseUrl: String?,
        ): DiscoveredApiModel? {
            val trimmedApiKey = apiKey.trim()
            val url =
                getFalModelsUrl(baseUrl)
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("endpoint_id", modelId.trim())
                    .addQueryParameter("expand", "openapi-3.0")
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Key $trimmedApiKey")
                    .header("Accept", "application/json")
                    .build()
            val responseBody =
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body.string()
                        error("fal.ai model detail failed with HTTP ${response.code}: $errorBody")
                    }
                    response.body.string()
                }
            val response = apiModelCatalogJson.decodeFromString<FalModelsResponse>(responseBody)
            return parseFalModels(response).firstOrNull { it.id == modelId.trim() }
        }

        private fun toGoogleDiscoveredModel(model: Model): DiscoveredApiModel? =
            DiscoveredApiModel(
                id = normalizeGoogleModelName(model),
                contextWindowTokens = model.inputTokenLimit().orElse(null),
                maxOutputTokens = model.outputTokenLimit().orElse(null),
            ).applyCatalogCapabilities(
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

        internal fun parseAnthropicModels(responseBody: String): List<DiscoveredApiModel> =
            AnthropicModelCatalogSupport.parseModels(responseBody)

        internal fun parseOpenRouterModels(responseBody: String): List<DiscoveredApiModel> =
            OpenRouterModelCatalogSupport.parseModels(responseBody)

        internal fun parseOpenAiCompatibleModels(
            responseBody: String,
            provider: ApiProvider,
        ): List<DiscoveredApiModel> = OpenAiCompatibleModelCatalogSupport.parseModels(responseBody, provider)

        internal fun parseFalModels(responseBody: String): List<DiscoveredApiModel> =
            parseFalModels(apiModelCatalogJson.decodeFromString<FalModelsResponse>(responseBody))

        internal fun parseXaiModels(
            responseBody: String,
            isChat: Boolean = false,
            isImageGen: Boolean = false,
            isVideoGen: Boolean = false,
        ): List<DiscoveredApiModel> =
            XaiModelCatalogSupport.parseModels(
                responseBody = responseBody,
                isChat = isChat,
                isImageGen = isImageGen,
                isVideoGen = isVideoGen,
            )

        internal fun parseXaiModelDetail(
            responseBody: String,
            requestedModelId: String,
            isChat: Boolean = false,
            isImageGen: Boolean = false,
            isVideoGen: Boolean = false,
        ): DiscoveredApiModel? =
            XaiModelCatalogSupport.parseModelDetail(
                responseBody = responseBody,
                requestedModelId = requestedModelId,
                isChat = isChat,
                isImageGen = isImageGen,
                isVideoGen = isVideoGen,
            )
    }
