package com.browntowndev.enclave.feature.inference.repository

import com.browntowndev.enclave.domain.model.config.OpenRouterModelEndpoint
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

@Serializable
private data class OpenRouterModelsResponse(
    val data: List<OpenRouterModel> = emptyList(),
)

@Serializable
private data class OpenRouterModelEndpointsResponse(
    val data: OpenRouterModelEndpointsData? = null,
)

@Serializable
private data class OpenRouterModelEndpointsData(
    val endpoints: List<OpenRouterModelEndpointPayload> = emptyList(),
)

@Serializable
private data class OpenRouterLatencyStats(
    val p50: Double? = null,
)

@Serializable
private data class OpenRouterThroughputStats(
    val p50: Double? = null,
)

@Serializable
private data class OpenRouterModelEndpointPayload(
    @SerialName("provider_name")
    val providerName: String,
    @SerialName("provider_slug")
    val providerSlug: String? = null,
    @SerialName("context_length")
    val contextLength: Int? = null,
    val pricing: OpenRouterPricing? = null,
    val quantization: String? = null,
    val status: Int? = null,
    @SerialName("uptime_last_1d")
    val uptimeLast1d: Double? = null,
    @SerialName("latency_last_30m")
    val latencyLast30m: OpenRouterLatencyStats? = null,
    @SerialName("throughput_last_30m")
    val throughputLast30m: OpenRouterThroughputStats? = null,
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

internal object OpenRouterModelCatalogSupport {
    fun fetchModelEndpoints(
        httpClient: Call.Factory,
        apiKey: String,
        baseUrl: String,
        modelId: String,
    ): List<OpenRouterModelEndpoint> {
        val trimmedApiKey = apiKey.trim()
        val url =
            baseUrl
                .trimEnd('/')
                .toHttpUrl()
                .newBuilder()
                .addPathSegments("models/${modelId.trim()}/endpoints")
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
                error("OpenRouter endpoint discovery failed with HTTP ${response.code}: $errorBody")
            }
            return parseModelEndpoints(response.body.string())
        }
    }

    fun parseModelEndpoints(responseBody: String): List<OpenRouterModelEndpoint> {
        val response = apiModelCatalogJson.decodeFromString<OpenRouterModelEndpointsResponse>(responseBody)
        return response.data
            ?.endpoints
            .orEmpty()
            .map { endpoint ->
                OpenRouterModelEndpoint(
                    providerName = endpoint.providerName,
                    providerSlug =
                        endpoint.providerSlug?.takeIf { it.isNotBlank() }
                            ?: endpoint.providerName.lowercase(),
                    contextLength = endpoint.contextLength ?: 0,
                    promptPriceUsdPerMillion =
                        endpoint.pricing
                            ?.prompt
                            ?.toPerTokenUsdPerMillion()
                            ?: 0.0,
                    completionPriceUsdPerMillion =
                        endpoint.pricing
                            ?.completion
                            ?.toPerTokenUsdPerMillion()
                            ?: 0.0,
                    quantization = endpoint.quantization.orEmpty(),
                    isOperational = endpoint.status == 0,
                    uptime1d = endpoint.uptimeLast1d,
                    latency30m = endpoint.latencyLast30m?.p50?.let { it / 1000.0 },
                    throughput30m = endpoint.throughputLast30m?.p50,
                )
            }
    }

    fun fetchModels(
        httpClient: Call.Factory,
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
            return parseModels(response.body.string())
        }
    }

    fun parseModels(responseBody: String): List<DiscoveredApiModel> {
        val response = apiModelCatalogJson.decodeFromString<OpenRouterModelsResponse>(responseBody)
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
                ).applyCatalogCapabilities(
                    provider = ApiProvider.OPENROUTER,
                    supportedGenerationMethods = model.architecture?.input_modalities.orEmpty(),
                )
            }.distinctBy(DiscoveredApiModel::id)
            .sortedBy(DiscoveredApiModel::id)
    }
}
