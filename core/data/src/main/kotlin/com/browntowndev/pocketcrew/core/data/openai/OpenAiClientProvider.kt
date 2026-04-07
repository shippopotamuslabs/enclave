package com.browntowndev.pocketcrew.core.data.openai

import android.util.LruCache
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiClientProvider @Inject constructor() {
    private val clientCache = object : LruCache<String, OpenAIClient>(8) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: OpenAIClient,
            newValue: OpenAIClient?
        ) {
            // Explicitly close the evicted client to free wrapper resources,
            // which also shuts down its internal OkHttpClient dispatcher and connection pool.
            oldValue.close()
        }
    }

    fun getClient(
        apiKey: String,
        baseUrl: String?,
        organizationId: String? = null,
        projectId: String? = null,
        headers: Map<String, String> = emptyMap()
    ): OpenAIClient {
        val normalizedHeaders = headers
            .filterValues { it.isNotBlank() }
            .toSortedMap()
            .entries
            .joinToString(separator = "|") { (name, value) -> "$name=$value" }
        val key = "${apiKey.sha256Hex()}_${baseUrl}_${organizationId}_${projectId}_$normalizedHeaders"

        var client = clientCache.get(key)
        if (client == null) {
            synchronized(this) {
                client = clientCache.get(key)
                if (client == null) {
                    val builder = OpenAIOkHttpClient.builder()
                        .apiKey(apiKey)
                        .apply {
                            if (!baseUrl.isNullOrBlank()) {
                                baseUrl(baseUrl)
                            }
                            if (!organizationId.isNullOrBlank()) {
                                organization(organizationId)
                            }
                            if (!projectId.isNullOrBlank()) {
                                project(projectId)
                            }
                        }
                    headers
                        .filterValues { it.isNotBlank() }
                        .forEach { (name, value) ->
                            builder.putHeader(name, value)
                        }
                    client = builder.build()
                    clientCache.put(key, client)
                }
            }
        }
        return client!!
    }

    private fun String.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(this.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
