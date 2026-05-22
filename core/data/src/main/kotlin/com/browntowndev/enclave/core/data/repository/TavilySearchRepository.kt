package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.security.ApiKeyManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class SearchRequest(
    val query: String,
    val api_key: String,
)

@Serializable
private data class SearchResponse(
    val results: List<SearchResult> = emptyList(),
)

@Serializable
private data class ExtractRequest(
    val urls: List<String>,
    val api_key: String,
    val extract_depth: String,
    val format: String,
)

@Serializable
private data class ExtractResponse(
    val results: List<ExtractResult>,
)

@Singleton
class TavilySearchRepository
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val apiKeyManager: ApiKeyManager,
    ) {
        companion object {
            private const val SEARCH_URL = "https://api.tavily.com/search"
            private const val EXTRACT_URL = "https://api.tavily.com/extract"
            private val JSON_MEDIA_TYPE = "application/json".toMediaType()
            private val json = Json { ignoreUnknownKeys = true }
        }

        fun search(query: String): String {
            val apiKey =
                apiKeyManager.get(ApiKeyManager.TAVILY_SEARCH_ALIAS)
                    ?: throw IllegalStateException("Tavily API key is required when search is enabled")

            val requestBody =
                Json
                    .encodeToString(SearchRequest(query, apiKey))
                    .toRequestBody(JSON_MEDIA_TYPE)

            val request =
                Request
                    .Builder()
                    .url(SEARCH_URL)
                    .post(requestBody)
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Tavily request failed with HTTP ${response.code}")
                }

                val body = response.body.string()
                return mapResponse(query, body)
            }
        }

        internal fun mapResponse(
            query: String,
            body: String,
        ): String {
            val response = json.decodeFromString<SearchResponse>(body)
            return Json.encodeToString(MappedSearchResponse(query, response.results))
        }

        fun extract(
            urls: List<String>,
            extractDepth: String = "basic",
            format: String = "markdown",
        ): String {
            val apiKey =
                apiKeyManager.get(ApiKeyManager.TAVILY_SEARCH_ALIAS)
                    ?: throw IllegalStateException("Tavily API key is required when search is enabled")

            val requestBody =
                Json
                    .encodeToString(
                        ExtractRequest(urls, apiKey, extractDepth, format),
                    ).toRequestBody(JSON_MEDIA_TYPE)

            val request =
                Request
                    .Builder()
                    .url(EXTRACT_URL)
                    .post(requestBody)
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Tavily request failed with HTTP ${response.code}")
                }

                val body = response.body.string()
                return mapExtractResponse(body)
            }
        }

        internal fun mapExtractResponse(body: String): String {
            val response = json.decodeFromString<ExtractResponse>(body)
            return Json.encodeToString(MappedExtractResponse(response.results))
        }
    }
