package com.browntowndev.enclave.feature.inference.repository

import android.util.Log
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.feature.inference.anthropic.AnthropicSdkBaseUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

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

internal object AnthropicModelCatalogSupport {
    fun fetchModels(
        httpClient: Call.Factory,
        apiKey: String,
        baseUrl: String?,
    ): List<DiscoveredApiModel> {
        val trimmedApiKey = apiKey.trim()
        val url = buildModelsUrl(baseUrl)
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
            return parseModels(response.body.string())
        }
    }

    fun parseModels(responseBody: String): List<DiscoveredApiModel> {
        val response = apiModelCatalogJson.decodeFromString<AnthropicModelsResponse>(responseBody)
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
                ).applyCatalogCapabilities(ApiProvider.ANTHROPIC)
            }.distinctBy(DiscoveredApiModel::id)
            .sortedBy(DiscoveredApiModel::id)
    }

    private fun buildModelsUrl(baseUrl: String?): HttpUrl {
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
}
