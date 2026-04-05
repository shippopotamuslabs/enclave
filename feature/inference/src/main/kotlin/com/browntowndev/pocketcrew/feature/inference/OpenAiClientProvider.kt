package com.browntowndev.pocketcrew.feature.inference

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
        projectId: String? = null
    ): OpenAIClient {
        // Create a stable cache key
        val key = "${apiKey.hashCode()}_${baseUrl}_${organizationId}_${projectId}"
        
        return clientCache.getOrPut(key) {
            OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .apply {
                    if (!baseUrl.isNullOrBlank()) baseUrl(baseUrl)
                    if (!organizationId.isNullOrBlank()) organization(organizationId)
                    if (!projectId.isNullOrBlank()) project(projectId)
                }
                .build()
        }
    }
}
