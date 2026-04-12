package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TavilySearchRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val apiKeyManager: ApiKeyManager,
) {
    companion object {
        private const val SEARCH_URL = "https://api.tavily.com/search"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    fun search(query: String): String {
        val apiKey = apiKeyManager.get(ApiKeyManager.TAVILY_SEARCH_ALIAS)
            ?: throw IllegalStateException("Tavily API key is required when search is enabled")

        val requestBody = JSONObject()
            .put("query", query)
            .put("api_key", apiKey)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
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

    internal fun mapResponse(query: String, body: String): String {
        val payload = JSONObject(body)
        val mappedResults = JSONArray()
        val results = payload.optJSONArray("results") ?: JSONArray()

        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            mappedResults.put(
                JSONObject()
                    .put("title", item.optString("title"))
                    .put("url", item.optString("url"))
                    .put("content", item.optString("content"))
                    .put("score", item.optDouble("score"))
            )
        }

        return JSONObject()
            .put("query", query)
            .put("results", mappedResults)
            .toString()
    }
}
