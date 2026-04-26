package com.browntowndev.pocketcrew.feature.inference

import android.util.LruCache
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.google.genai.Client
import com.google.genai.types.HttpOptions
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

interface OpenAiClientProviderPort {
    fun getClient(
        apiKey: String,
        baseUrl: String?,
        organizationId: String? = null,
        projectId: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): OpenAIClient
}

interface AnthropicClientProviderPort {
    fun getClient(
        apiKey: String,
        baseUrl: String?,
        headers: Map<String, String> = emptyMap(),
    ): AnthropicClient
}

interface GoogleGenAiClientProviderPort {
    fun getClient(
        apiKey: String,
        baseUrl: String?,
        headers: Map<String, String> = emptyMap(),
        apiVersion: String = GEMINI_API_VERSION,
    ): Client

    companion object {
        const val GEMINI_API_VERSION = "v1alpha"
    }
}

@Singleton
class OpenAiInferenceClientProvider @Inject constructor() : OpenAiClientProviderPort {
    private val clientCache = object : LruCache<String, OpenAIClient>(8) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: OpenAIClient,
            newValue: OpenAIClient?,
        ) {
            oldValue.close()
        }
    }

    override fun getClient(
        apiKey: String,
        baseUrl: String?,
        organizationId: String?,
        projectId: String?,
        headers: Map<String, String>,
    ): OpenAIClient {
        val normalizedHeaders = headers.normalizedHeaderCacheKey()
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
                        .forEach { (name, value) -> builder.putHeader(name, value) }
                    client = builder.build()
                    clientCache.put(key, client)
                }
            }
        }
        return requireNotNull(client) { "OpenAI client cache returned null after creation" }
    }
}

@Singleton
class AnthropicInferenceClientProvider @Inject constructor() : AnthropicClientProviderPort {
    private val clientCache = object : LruCache<String, AnthropicClient>(8) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: AnthropicClient,
            newValue: AnthropicClient?,
        ) {
            oldValue.close()
        }
    }

    override fun getClient(
        apiKey: String,
        baseUrl: String?,
        headers: Map<String, String>,
    ): AnthropicClient {
        val normalizedHeaders = headers.normalizedHeaderCacheKey()
        val key = "${apiKey.sha256Hex()}_${baseUrl}_$normalizedHeaders"

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
                        .forEach { (name, value) -> builder.putHeader(name, value) }
                    client = builder.build()
                    clientCache.put(key, client)
                }
            }
        }
        return requireNotNull(client) { "Anthropic client cache returned null after creation" }
    }
}

@Singleton
class GoogleGenAiInferenceClientProvider @Inject constructor() : GoogleGenAiClientProviderPort {
    private val clientCache = object : LruCache<String, Client>(8) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Client,
            newValue: Client?,
        ) {
            oldValue.close()
        }
    }

    override fun getClient(
        apiKey: String,
        baseUrl: String?,
        headers: Map<String, String>,
        apiVersion: String,
    ): Client {
        val normalizedHeaders = headers.normalizedHeaderCacheKey()
        val key = "${apiKey.sha256Hex()}_${baseUrl}_${apiVersion}_$normalizedHeaders"

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
        return requireNotNull(client) { "Google GenAI client cache returned null after creation" }
    }
}

private fun Map<String, String>.normalizedHeaderCacheKey(): String = filterValues { it.isNotBlank() }
    .toSortedMap()
    .entries
    .joinToString(separator = "|") { (name, value) -> "$name=$value" }

private fun String.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
