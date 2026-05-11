package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ExaSearchRequest(
    val query: String,
    val type: String = "auto",
    val num_results: Int = 10,
    val contents: ExaContentsConfig
)

@Serializable
private data class ExaContentsConfig(
    val highlights: Boolean? = null,
    val text: ExaTextConfig? = null
)

@Serializable
private data class ExaTextConfig(
    val verbosity: String = "compact",
    val maxCharacters: Int? = null
)

@Serializable
private data class ExaSearchResponse(
    val results: List<ExaSearchResult> = emptyList()
)

@Serializable
private data class ExaSearchResult(
    val title: String = "",
    val url: String = "",
    val highlights: List<String> = emptyList(),
    val score: Double = 0.0
)

@Serializable
private data class ExaContentsRequest(
    val urls: List<String>,
    val text: ExaTextConfig? = null,
    val highlights: Boolean? = null
)

@Serializable
private data class ExaContentsResponse(
    val results: List<ExaContentResult> = emptyList()
)

@Serializable
private data class ExaContentResult(
    val url: String = "",
    val text: String = ""
)

@Singleton
class ExaSearchRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val apiKeyManager: ApiKeyManager,
) {
    companion object {
        private const val SEARCH_URL = "https://api.exa.ai/search"
        private const val CONTENTS_URL = "https://api.exa.ai/contents"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
    }

    fun search(query: String): String {
        val apiKey = apiKeyManager.get(ApiKeyManager.EXA_SEARCH_ALIAS)
            ?: throw IllegalStateException("Exa API key is required when search is enabled and Exa is selected")

        val requestBody = Json.encodeToString(
            ExaSearchRequest(
                query = query,
                contents = ExaContentsConfig(highlights = true)
            )
        ).toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(SEARCH_URL)
            .addHeader("x-api-key", apiKey)
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Exa search request failed with HTTP ${response.code}")
            }

            val body = response.body.string()
            val exaResponse = json.decodeFromString<ExaSearchResponse>(body)
            
            val mappedResults = exaResponse.results.map { result ->
                SearchResult(
                    title = result.title,
                    url = result.url,
                    content = result.highlights.joinToString("\n...\n"),
                    score = result.score
                )
            }
            
            return Json.encodeToString(MappedSearchResponse(query, mappedResults))
        }
    }

    fun extract(urls: List<String>): String {
        val apiKey = apiKeyManager.get(ApiKeyManager.EXA_SEARCH_ALIAS)
            ?: throw IllegalStateException("Exa API key is required when search is enabled and Exa is selected")

        val requestBody = Json.encodeToString(
            ExaContentsRequest(
                urls = urls,
                text = ExaTextConfig(verbosity = "full")
            )
        ).toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(CONTENTS_URL)
            .addHeader("x-api-key", apiKey)
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Exa contents request failed with HTTP ${response.code}")
            }

            val body = response.body.string()
            val exaResponse = json.decodeFromString<ExaContentsResponse>(body)
            
            val mappedResults = exaResponse.results.map { result ->
                ExtractResult(
                    url = result.url,
                    raw_content = result.text
                )
            }
            
            return Json.encodeToString(MappedExtractResponse(mappedResults))
        }
    }
}
