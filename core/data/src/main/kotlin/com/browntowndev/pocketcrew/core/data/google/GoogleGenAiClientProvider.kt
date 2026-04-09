package com.browntowndev.pocketcrew.core.data.google

import android.util.LruCache
import com.google.genai.Client
import com.google.genai.types.ListModelsConfig
import com.google.genai.types.Model
import com.google.genai.types.HttpOptions
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleGenAiClientProvider @Inject constructor() {
    companion object {
        const val GEMINI_API_VERSION = "v1alpha"
    }

    private val clientCache = object : LruCache<String, Client>(8) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Client,
            newValue: Client?
        ) {
            oldValue.close()
        }
    }

    fun getClient(
        apiKey: String,
        baseUrl: String?,
        headers: Map<String, String> = emptyMap(),
        apiVersion: String = GEMINI_API_VERSION,
    ): Client {
        val normalizedHeaders = headers
            .filterValues { it.isNotBlank() }
            .toSortedMap()
            .entries
            .joinToString(separator = "|") { (name, value) -> "$name=$value" }
        val key = "${apiKey.sha256Hex()}_${baseUrl}_${apiVersion}_${normalizedHeaders}"

        var client = clientCache.get(key)
        if (client == null) {
            synchronized(this) {
                client = clientCache.get(key)
                if (client == null) {
                    val httpOptions = HttpOptions.builder()
                        .apiVersion(apiVersion)
                        .headers(headers.filterValues { it.isNotBlank() })
                        .apply {
                            if (!baseUrl.isNullOrBlank()) {
                                baseUrl(baseUrl)
                            }
                        }
                        .build()

                    client = Client.builder()
                        .apiKey(apiKey)
                        .httpOptions(httpOptions)
                        .build()
                    clientCache.put(key, client)
                }
            }
        }
        return client!!
    }

    fun listModels(
        apiKey: String,
        baseUrl: String?,
        headers: Map<String, String> = emptyMap(),
        apiVersion: String = GEMINI_API_VERSION,
    ): List<Model> = getClient(
        apiKey = apiKey,
        baseUrl = baseUrl,
        headers = headers,
        apiVersion = apiVersion,
    ).models
        .list(ListModelsConfig.builder().build())
        .asSequence()
        .toList()

    private fun String.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
