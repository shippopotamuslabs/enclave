package com.browntowndev.enclave.feature.inference.repository

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

@Serializable
private data class OpenAiCompatibleModelsResponse(
    val data: List<OpenAiCompatibleModel> = emptyList(),
)

@Serializable
private data class OpenAiCompatibleModel(
    val id: String,
    val created: Long? = null,
)

/**
 * Lists models from OpenAI-compatible `/v1/models` (or `{base}/models`) endpoints using
 * kotlinx serialization so optional fields such as DeepSeek's missing `created` do not crash
 * the OpenAI Java SDK's strict [com.openai.models.models.Model] parser.
 */
internal object OpenAiCompatibleModelCatalogSupport {
    fun fetchModels(
        httpClient: Call.Factory,
        apiKey: String,
        baseUrl: String,
        provider: ApiProvider,
    ): List<DiscoveredApiModel> {
        val trimmedApiKey = apiKey.trim()
        val request =
            Request
                .Builder()
                .url(modelsListUrl(baseUrl))
                .get()
                .header("Authorization", "Bearer $trimmedApiKey")
                .header("Accept", "application/json")
                .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body.string()
                error(
                    "${provider.displayName} model discovery failed with HTTP ${response.code}: $errorBody",
                )
            }
            return parseModels(response.body.string(), provider)
        }
    }

    fun parseModels(
        responseBody: String,
        provider: ApiProvider,
    ): List<DiscoveredApiModel> {
        val response = apiModelCatalogJson.decodeFromString<OpenAiCompatibleModelsResponse>(responseBody)
        return response.data
            .mapNotNull { model ->
                val id = model.id.trim()
                if (id.isBlank()) return@mapNotNull null
                DiscoveredApiModel(
                    id = id,
                    created = model.created,
                ).applyCatalogCapabilities(provider)
            }.distinctBy(DiscoveredApiModel::id)
            .sortedBy(DiscoveredApiModel::id)
    }

    internal fun modelsListUrl(baseUrl: String): HttpUrl {
        val httpUrl = baseUrl.trimEnd('/').toHttpUrl()
        if (httpUrl.pathSegments.filter { it.isNotBlank() }.lastOrNull() == "models") {
            return httpUrl
        }
        val builder = httpUrl.newBuilder()
        val segments = httpUrl.pathSegments.filter { it.isNotBlank() }
        if (segments.isEmpty()) {
            builder.addPathSegments("v1/models")
        } else {
            builder.addPathSegment("models")
        }
        return builder.build()
    }
}
