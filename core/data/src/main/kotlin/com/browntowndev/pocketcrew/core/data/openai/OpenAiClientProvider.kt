package com.browntowndev.pocketcrew.core.data.openai

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiClientProvider @Inject constructor() {
    private val clientCache = ConcurrentHashMap<String, OpenAIClient>()

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
        val key = "${apiKey.hashCode()}_${baseUrl}_${organizationId}_${projectId}_$normalizedHeaders"

        return clientCache.getOrPut(key) {
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
            builder.build()
        }
    }
}
