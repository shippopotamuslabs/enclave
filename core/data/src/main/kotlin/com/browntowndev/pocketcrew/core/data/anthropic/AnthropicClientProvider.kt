package com.browntowndev.pocketcrew.core.data.anthropic

import android.util.LruCache
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnthropicClientProvider @Inject constructor() {
    private val clientCache = object : LruCache<String, AnthropicClient>(8) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: AnthropicClient,
            newValue: AnthropicClient?
        ) {
            oldValue.close()
        }
    }

    fun getClient(
        apiKey: String,
        baseUrl: String?,
        headers: Map<String, String> = emptyMap()
    ): AnthropicClient {
        val normalizedHeaders = headers
            .filterValues { it.isNotBlank() }
            .toSortedMap()
            .entries
            .joinToString(separator = "|") { (name, value) -> "$name=$value" }
        val key = "${apiKey.sha256Hex()}_${baseUrl}_${normalizedHeaders}"

        var client = clientCache.get(key)
        if (client == null) {
            synchronized(this) {
                client = clientCache.get(key)
                if (client == null) {
                    val builder = AnthropicOkHttpClient.builder()
                        .apiKey(apiKey)
                    if (!baseUrl.isNullOrBlank()) {
                        builder.baseUrl(baseUrl)
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
        return digest.digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
